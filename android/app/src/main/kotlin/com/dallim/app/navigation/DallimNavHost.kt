package com.dallim.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dallim.app.discover.DiscoverRoute
import com.dallim.app.home.HomeRoute
import com.dallim.app.onboarding.carousel.OnboardingCarouselScreen
import com.dallim.app.onboarding.firstroute.FirstRouteSuggestionRoute
import com.dallim.app.onboarding.login.LoginRoute
import com.dallim.app.onboarding.permission.PermissionRoute
import com.dallim.app.onboarding.profile.ProfileSetupRoute
import com.dallim.app.onboarding.signup.SignupRoute
import com.dallim.app.onboarding.splash.SplashRoute
import com.dallim.app.onboarding.terms.TermsRoute
import com.dallim.app.route.detail.RouteDetailRoute
import com.dallim.app.route.saved.SavedRoutesRoute

/**
 * Root NavHost. S-00~S-06 온보딩과 S-10/S-11/S-16/S-17 홈&탐색 screens are wired up here.
 * S-00(SPLASH)는 back stack의 최하단에서 한 번만 지나가는 목적지이므로, 온보딩/로그인 어디서
 * 진입했든 "-> 홈" 전이는 전부 `popUpTo(DallimDestinations.SPLASH) { inclusive = true }`로
 * 스플래시까지 통째로 걷어내 온보딩 스택으로 뒤로가기가 되지 않게 한다. 러닝/달림북
 * (S-20~S-41)은 아직 화면 구현체가 없어 이번 라운드에도 배선하지 않는다 — android-dev가 해당
 * 화면을 구현할 때 `composable(DallimDestinations.X) { ... }`를 추가한다
 * (docs/01-feature-spec.md 1장).
 */
@Composable
fun DallimNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = DallimDestinations.SPLASH,
    ) {
        composable(DallimDestinations.SPLASH) {
            SplashRoute(
                onNavigateHome = {
                    navController.navigate(DallimDestinations.HOME) {
                        popUpTo(DallimDestinations.SPLASH) { inclusive = true }
                    }
                },
                onNavigateCarousel = {
                    navController.navigate(DallimDestinations.ONBOARDING_CAROUSEL) {
                        popUpTo(DallimDestinations.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(DallimDestinations.ONBOARDING_CAROUSEL) {
            OnboardingCarouselScreen(
                onFinished = { navController.navigate(DallimDestinations.LOGIN) },
            )
        }

        composable(DallimDestinations.LOGIN) {
            LoginRoute(
                onNavigateTerms = { navController.navigate(DallimDestinations.TERMS) },
                onNavigateHome = {
                    navController.navigate(DallimDestinations.HOME) {
                        popUpTo(DallimDestinations.SPLASH) { inclusive = true }
                    }
                },
                onNavigateSignup = { navController.navigate(DallimDestinations.SIGNUP) },
            )
        }

        composable(DallimDestinations.SIGNUP) {
            SignupRoute(
                onNavigateTerms = { navController.navigate(DallimDestinations.TERMS) },
                onNavigateHome = {
                    navController.navigate(DallimDestinations.HOME) {
                        popUpTo(DallimDestinations.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(DallimDestinations.TERMS) {
            TermsRoute(
                onAgreed = { navController.navigate(DallimDestinations.PROFILE_SETUP) },
            )
        }

        composable(DallimDestinations.PROFILE_SETUP) {
            ProfileSetupRoute(
                onNavigatePermission = { navController.navigate(DallimDestinations.PERMISSION) },
            )
        }

        composable(DallimDestinations.PERMISSION) {
            PermissionRoute(
                onContinue = { navController.navigate(DallimDestinations.FIRST_ROUTE_SUGGESTION) },
            )
        }

        composable(DallimDestinations.FIRST_ROUTE_SUGGESTION) {
            FirstRouteSuggestionRoute(
                // S-20(러닝 준비)은 아직 NavHost에 등록되지 않았다 — android-dev가 S-20을
                // 구현할 때 이 콜백을 `navController.navigate(DallimDestinations.runPrepare(routeId))`로
                // 교체한다 (S-16의 onStartRunClick과 동일한 패턴).
                onStartRunClick = { },
                onLaterClick = {
                    navController.navigate(DallimDestinations.HOME) {
                        popUpTo(DallimDestinations.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(DallimDestinations.HOME) {
            HomeRoute(
                onRouteClick = { routeId -> navController.navigate(DallimDestinations.routeDetail(routeId)) },
                onExploreClick = { navController.navigate(DallimDestinations.EXPLORE) },
                onSeeAllSavedRoutesClick = { navController.navigate(DallimDestinations.SAVED_ROUTES) },
            )
        }

        composable(DallimDestinations.EXPLORE) {
            DiscoverRoute(
                onBackClick = { navController.popBackStack() },
                onRouteClick = { routeId -> navController.navigate(DallimDestinations.routeDetail(routeId)) },
            )
        }

        composable(
            route = DallimDestinations.ROUTE_DETAIL,
            arguments = listOf(navArgument(DallimDestinations.ARG_ROUTE_ID) { type = NavType.StringType }),
        ) {
            RouteDetailRoute(
                onBackClick = { navController.popBackStack() },
                // S-20(러닝 준비)은 다음 라운드 범위라 아직 NavHost에 등록되지 않았다 — 지금
                // 연결하면 미등록 목적지로 내비게이션이 실패한다. android-dev가 S-20을 구현할 때
                // 이 콜백을 `navController.navigate(DallimDestinations.runPrepare(routeId))`로
                // 교체한다.
                onStartRunClick = { },
            )
        }

        composable(DallimDestinations.SAVED_ROUTES) {
            SavedRoutesRoute(
                onBackClick = { navController.popBackStack() },
                onRouteClick = { routeId -> navController.navigate(DallimDestinations.routeDetail(routeId)) },
                onExploreClick = { navController.navigate(DallimDestinations.EXPLORE) },
            )
        }
    }
}
