package com.dallim.auth

import com.dallim.common.BadRequestException
import com.dallim.common.ConflictException
import com.dallim.common.ErrorCodes
import com.dallim.common.UnauthorizedException
import com.dallim.user.AuthProvider

/**
 * Orchestrates the four login/signup paths + refresh/logout described in
 * docs/02-api-spec.md 1장 and docs/01-feature-spec.md 2.2.A. All account-linking / error-code
 * decisions live here so the route layer (AuthRoutes.kt) stays a thin HTTP adapter.
 */
class AuthService(
    private val userAccountRepository: UserAccountRepository,
    private val googleAuthClient: GoogleAuthClient,
    private val kakaoAuthClient: KakaoAuthClient,
    private val jwtService: JwtService,
    private val refreshTokenStore: RefreshTokenStore,
) {

    suspend fun loginWithGoogle(idToken: String): AuthTokenResponse {
        val info = googleAuthClient.verify(idToken)
        return loginOrSignupSocial(AuthProvider.GOOGLE, info.sub, info.email)
    }

    suspend fun loginWithKakao(kakaoAccessToken: String): AuthTokenResponse {
        val user = kakaoAuthClient.getUser(kakaoAccessToken)
        return loginOrSignupSocial(AuthProvider.KAKAO, user.id.toString(), user.kakaoAccount?.email)
    }

    /**
     * Shared Google/Kakao flow: existing (provider, providerId) -> login; otherwise check the
     * account-linking guard (409 ACCOUNT_EXISTS_DIFFERENT_PROVIDER) when an email is available,
     * then create a new account. Per docs/02-api-spec.md 계정 통합 정책, MVP1 does not auto-merge
     * accounts and Kakao accounts without email consent skip the guard entirely (별도 계정으로 생성).
     */
    private fun loginOrSignupSocial(provider: AuthProvider, providerId: String, email: String?): AuthTokenResponse {
        val existing = userAccountRepository.findByProviderAndProviderId(provider, providerId)
        if (existing != null) {
            return issueTokens(existing.id, provider, isNewUser = false)
        }

        if (email != null) {
            val conflict = userAccountRepository.findByEmailAnyProvider(email)
            if (conflict != null && conflict.provider != provider) {
                throw ConflictException(
                    ErrorCodes.ACCOUNT_EXISTS_DIFFERENT_PROVIDER,
                    "이미 ${conflict.provider}로 가입된 이메일이에요.",
                )
            }
        }

        val created = userAccountRepository.createSocialAccount(provider, providerId, email)
        return issueTokens(created.id, provider, isNewUser = true)
    }

    fun signup(email: String, password: String): AuthTokenResponse {
        if (!AuthValidation.isValidEmail(email)) {
            throw BadRequestException(ErrorCodes.INVALID_EMAIL_FORMAT, "이메일 형식이 올바르지 않습니다.")
        }
        if (!AuthValidation.isValidPassword(password)) {
            throw BadRequestException(
                ErrorCodes.INVALID_PASSWORD_FORMAT,
                "비밀번호는 8자 이상, 영문/숫자 조합이어야 합니다.",
            )
        }

        val existing = userAccountRepository.findByEmailAnyProvider(email)
        if (existing != null) {
            if (existing.provider == AuthProvider.EMAIL) {
                throw ConflictException(ErrorCodes.EMAIL_ALREADY_EXISTS, "이미 가입된 이메일입니다.")
            }
            throw ConflictException(
                ErrorCodes.ACCOUNT_EXISTS_DIFFERENT_PROVIDER,
                "이미 ${existing.provider}로 가입된 이메일이에요.",
            )
        }

        val passwordHash = PasswordHasher.hash(password)
        val created = userAccountRepository.createEmailAccount(email, passwordHash)
        return issueTokens(created.id, AuthProvider.EMAIL, isNewUser = true)
    }

    /**
     * "이메일 없음"과 "비밀번호 틀림"을 구분하지 않고 동일하게 401 INVALID_CREDENTIALS —
     * both the missing-account and wrong-password paths throw the exact same exception with
     * the exact same message, so no observable timing/response difference leaks account
     * existence (docs/02-api-spec.md 1장 POST /auth/login).
     */
    fun login(email: String, password: String): AuthTokenResponse {
        val invalidCredentials = {
            UnauthorizedException(ErrorCodes.INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다.")
        }

        val (userId, passwordHash) = userAccountRepository.findPasswordHashByEmail(email) ?: throw invalidCredentials()

        if (!PasswordHasher.matches(password, passwordHash)) {
            throw invalidCredentials()
        }

        return issueTokens(userId, AuthProvider.EMAIL, isNewUser = false)
    }

    fun refresh(refreshToken: String): RefreshResponse {
        val userId = refreshTokenStore.consume(refreshToken)
            ?: throw UnauthorizedException(
                ErrorCodes.REFRESH_TOKEN_EXPIRED_OR_INVALID,
                "리프레시 토큰이 유효하지 않습니다.",
            )

        val newAccessToken = jwtService.issueAccessToken(userId)
        val newRefreshToken = refreshTokenStore.issue(userId)
        return RefreshResponse(accessToken = newAccessToken, refreshToken = newRefreshToken)
    }

    fun logout(refreshToken: String) {
        refreshTokenStore.revoke(refreshToken)
    }

    private fun issueTokens(userId: String, provider: AuthProvider, isNewUser: Boolean): AuthTokenResponse =
        AuthTokenResponse(
            accessToken = jwtService.issueAccessToken(userId),
            refreshToken = refreshTokenStore.issue(userId),
            isNewUser = isNewUser,
            userId = userId,
            provider = provider,
        )
}
