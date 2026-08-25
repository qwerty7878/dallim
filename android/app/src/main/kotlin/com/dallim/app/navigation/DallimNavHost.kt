package com.dallim.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dallim.app.discover.DiscoverRoute
import com.dallim.app.home.HomeRoute
import com.dallim.app.route.detail.RouteDetailRoute
import com.dallim.app.route.saved.SavedRoutesRoute

/**
 * Root NavHost. Only the splash placeholder (S-00) plus the S-10/S-11/S-16/S-17 홈&탐색 screens
 * are wired up here — this round's scope. The onboarding screens (S-01~S-06) are implemented
 * under onboarding/ but intentionally NOT wired into this NavHost yet (out of scope for this
 * round, left untouched); android-dev adds one `composable(DallimDestinations.X) { ... }` per
 * remaining screen as S-01~S-06/S-20~S-41 land (docs/01-feature-spec.md 1장).
 */
@Composable
fun DallimNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = DallimDestinations.SPLASH,
    ) {
        composable(DallimDestinations.SPLASH) {
            ScaffoldPlaceholder(label = "S-00 스플래시 (android-dev 구현 예정)")
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

@Composable
private fun ScaffoldPlaceholder(label: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
