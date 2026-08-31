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
import com.dallim.app.running.navigation.RunNavigationRoute
import com.dallim.app.running.prepare.RunPrepareRoute
import com.dallim.app.running.result.RunResultRoute
import com.dallim.app.running.share.ShareCardRoute

/**
 * Root NavHost. S-00~S-06 온보딩, S-10/S-11/S-16/S-17 홈&탐색, S-20~S-26 러닝
 * (destinations 문서 주석대로 S-22/23/24는 S-21 화면 내부 상태이지 별도 route가 아니다)이
 * 여기 배선돼 있다. S-00(SPLASH)는 back stack의 최하단에서 한 번만 지나가는 목적지이므로,
 * 온보딩/로그인 어디서 진입했든 "-> 홈" 전이는 전부
 * `popUpTo(DallimDestinations.SPLASH) { inclusive = true }`로 스플래시까지 통째로 걷어내
 * 온보딩 스택으로 뒤로가기가 되지 않게 한다.
 *
 * S-21(러닝 중) -> S-25(결과) 전이는 `popUpTo(RUN_PREPARE) { inclusive = true }`로 준비/러닝
 * 화면 전체를 백스택에서 걷어낸다 — 결과 화면에서 뒤로가기를 누르면 다시 러닝 중 화면으로
 * 돌아가는 사고를 막기 위함(러닝은 이미 서버에 종료 처리됐다). 달림북(S-40/S-41)은 다음
 * 라운드 범위라 아직 배선하지 않는다.
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
                onStartRunClick = { routeId -> navController.navigate(DallimDestinations.runPrepare(routeId)) },
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
                onStartRunClick = { routeId -> navController.navigate(DallimDestinations.runPrepare(routeId)) },
            )
        }

        composable(DallimDestinations.SAVED_ROUTES) {
            SavedRoutesRoute(
                onBackClick = { navController.popBackStack() },
                onRouteClick = { routeId -> navController.navigate(DallimDestinations.routeDetail(routeId)) },
                onExploreClick = { navController.navigate(DallimDestinations.EXPLORE) },
            )
        }

        composable(
            route = DallimDestinations.RUN_PREPARE,
            arguments = listOf(navArgument(DallimDestinations.ARG_ROUTE_ID) { type = NavType.StringType }),
        ) {
            RunPrepareRoute(
                onStarted = { runId, routeId ->
                    navController.navigate(DallimDestinations.runNavigation(runId, routeId)) {
                        popUpTo(DallimDestinations.RUN_PREPARE) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(
            route = DallimDestinations.RUN_NAVIGATION,
            arguments = listOf(
                navArgument(DallimDestinations.ARG_RUN_ID) { type = NavType.StringType },
                navArgument(DallimDestinations.ARG_ROUTE_ID) { type = NavType.StringType },
            ),
        ) {
            RunNavigationRoute(
                onFinished = { runId ->
                    navController.navigate(DallimDestinations.runResult(runId)) {
                        // 러닝은 이미 서버에 종료 처리됐다 — 결과 화면에서 뒤로가기를 눌러도 다시
                        // 준비/러닝 화면으로 돌아가지 않도록 그 두 화면을 백스택에서 걷어낸다.
                        popUpTo(DallimDestinations.RUN_PREPARE) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = DallimDestinations.RUN_RESULT,
            arguments = listOf(navArgument(DallimDestinations.ARG_RUN_ID) { type = NavType.StringType }),
        ) {
            RunResultRoute(
                onShareClick = { runId -> navController.navigate(DallimDestinations.shareCard(runId)) },
                onDoneClick = {
                    // S-40 달림북은 다음 라운드 범위라 아직 배선하지 않았다 — 지금은 홈으로 복귀한다.
                    navController.navigate(DallimDestinations.HOME) {
                        popUpTo(DallimDestinations.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = DallimDestinations.SHARE_CARD,
            arguments = listOf(navArgument(DallimDestinations.ARG_RUN_ID) { type = NavType.StringType }),
        ) {
            ShareCardRoute(onBackClick = { navController.popBackStack() })
        }
    }
}
