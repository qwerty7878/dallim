package com.dallim.auth

/**
 * Pure validation functions for docs/02-api-spec.md 1장 signup rules. Kept free of any
 * DB/HTTP dependency so qa-engineer can unit test them directly.
 */
object AuthValidation {
    private const val MAX_EMAIL_LENGTH = 254
    private const val MIN_PASSWORD_LENGTH = 8

    // Deliberately simple (not RFC 5322 exhaustive) — matches the "이메일 형식" requirement
    // without rejecting common real-world addresses.
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean = email.length <= MAX_EMAIL_LENGTH && EMAIL_REGEX.matches(email)

    /** 8자 이상 + 영문/숫자 조합 필수 (docs/01-feature-spec.md 2.2.A). */
    fun isValidPassword(password: String): Boolean =
        password.length >= MIN_PASSWORD_LENGTH &&
            password.any { it.isLetter() } &&
            password.any { it.isDigit() }
}
