package com.dallim.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dallim.app.career.edit.RaceRecordEditRoute
import com.dallim.app.career.shelf.MedalShelfRoute
import com.dallim.app.dallimbook.detail.DallimbookDetailRoute
import com.dallim.app.dallimbook.grid.DallimbookGridRoute
import com.dallim.app.discover.DiscoverRoute
import com.dallim.app.home.HomeRoute
import com.dallim.app.meetup.create.MeetupCreateRoute
import com.dallim.app.meetup.detail.MeetupDetailRoute
import com.dallim.app.meetup.list.MeetupListRoute
import com.dallim.app.meetup.list.MeetupListViewModel
import com.dallim.app.my.MyRoute
import com.dallim.app.notification.NotificationListRoute
import com.dallim.app.onboarding.career.CareerEntryRoute
import com.dallim.app.onboarding.carousel.OnboardingCarouselScreen
import com.dallim.app.onboarding.firstroute.FirstRouteSuggestionRoute
import com.dallim.app.onboarding.login.LoginRoute
import com.dallim.app.onboarding.permission.PermissionRoute
import com.dallim.app.onboarding.profile.ProfileSetupRoute
import com.dallim.app.onboarding.signup.SignupRoute
import com.dallim.app.onboarding.splash.SplashRoute
import com.dallim.app.onboarding.terms.TermsRoute
import com.dallim.app.race.course.RaceCoursePreviewRoute
import com.dallim.app.race.detail.RaceDetailRoute
import com.dallim.app.race.list.RaceListRoute
import com.dallim.app.route.create.CourseCreateEntryRoute
import com.dallim.app.route.create.ai.AiRouteRoute
import com.dallim.app.route.create.draw.DrawRouteRoute
import com.dallim.app.route.detail.RouteDetailRoute
import com.dallim.app.route.saved.SavedRoutesRoute
import com.dallim.app.running.navigation.RunNavigationRoute
import com.dallim.app.running.prepare.RunPrepareRoute
import com.dallim.app.running.result.RunResultRoute
import com.dallim.app.running.share.ShareCardRoute
import com.dallim.app.social.chat.SocialSessionChatRoute
import com.dallim.app.social.checkin.SocialSessionCheckinRoute
import com.dallim.app.social.create.SocialSessionCreateRoute
import com.dallim.app.social.detail.SocialSessionApplicantsRoute
import com.dallim.app.social.detail.SocialSessionDetailRoute
import com.dallim.app.social.detail.SocialSessionDetailViewModel
import com.dallim.app.social.feedback.SocialSessionFeedbackRoute
import com.dallim.app.social.list.SocialSessionListRoute
import com.dallim.app.social.list.SocialSessionListViewModel
import com.dallim.app.social.runningmate.RunningMateListRoute
import com.dallim.ui.components.DallimTab

/**
 * Root NavHost. S-00~S-06 온보딩, S-10/S-11/S-16/S-17 홈&탐색, S-20~S-26 러닝, S-42 마이
 * (destinations 문서 주석대로 S-22/23/24는 S-21 화면 내부 상태이지 별도 route가 아니다)이
 * 여기 배선돼 있다. S-00(SPLASH)는 back stack의 최하단에서 한 번만 지나가는 목적지이므로,
 * 온보딩/로그인 어디서 진입했든 "-> 홈" 전이는 전부
 * `popUpTo(DallimDestinations.SPLASH) { inclusive = true }`로 스플래시까지 통째로 걷어내
 * 온보딩 스택으로 뒤로가기가 되지 않게 한다.
 *
 * S-21(러닝 중) -> S-25(결과) 전이는 `popUpTo(RUN_PREPARE) { inclusive = true }`로 준비/러닝
 * 화면 전체를 백스택에서 걷어낸다 — 결과 화면에서 뒤로가기를 누르면 다시 러닝 중 화면으로
 * 돌아가는 사고를 막기 위함(러닝은 이미 서버에 종료 처리됐다).
 *
 * S-42(마이) -> S-02(로그인) 로그아웃 전이는 반대로 `popUpTo(navController.graph.id)
 * { inclusive = true }`로 그래프 전체(홈/탭 포함)를 비운다 — 로그인 화면에서 뒤로가기를 눌러도
 * 다시 홈으로 돌아가지 않게 하기 위함.
 *
 * 세션 만료(리프레시 토큰까지 무효) -> S-02(로그인) 전이도 동일한 popUpTo 패턴을 쓴다:
 * `TokenAuthenticator`(core-network, OkHttp 백그라운드 스레드)가 `SessionEventBus`로 쏘는
 * 이벤트를 [SessionEventViewModel]을 통해 여기서 구독해, 사용자가 어느 화면에 있든 그래프
 * 전체를 비우고 로그인으로 보낸다.
 *
 * 홈(S-10)/탐색(S-11)/달림북 그리드(S-40)/마이(S-42) 4개 최상위 화면은 하단 탭바로 서로
 * 전환된다 (01-feature-spec.md §1.0) — [navigateToTab] 참고.
 */
@Composable
fun DallimNavHost(
    navController: NavHostController,
    sessionEventViewModel: SessionEventViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        sessionEventViewModel.sessionExpired.collect {
            // 리프레시 토큰까지 무효 — 사용자가 어느 화면에 있든 그래프 전체(홈/탭 포함)를
            // 비우고 로그인으로 보낸다. MyViewModel의 로그아웃 전이와 동일한 popUpTo 패턴
            // (위 클래스 주석 참고).
            navController.navigate(DallimDestinations.LOGIN) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

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
                onNavigateCareerEntry = { comfortablePace ->
                    navController.navigate(DallimDestinations.careerEntry(comfortablePace))
                },
            )
        }

        composable(
            route = DallimDestinations.CAREER_ENTRY,
            arguments = listOf(navArgument(DallimDestinations.ARG_COMFORTABLE_PACE) { type = NavType.StringType }),
        ) {
            CareerEntryRoute(
                onFinished = { navController.navigate(DallimDestinations.PERMISSION) },
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
                onSeeAllSavedRoutesClick = { navController.navigate(DallimDestinations.SAVED_ROUTES) },
                onNotificationClick = { navController.navigate(DallimDestinations.NOTIFICATIONS) },
                onTabSelected = { tab -> navController.navigateToTab(tab) },
            )
        }

        composable(DallimDestinations.NOTIFICATIONS) {
            NotificationListRoute(
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(DallimDestinations.DALLIMBOOK_GRID) {
            DallimbookGridRoute(
                onBackClick = { navController.popBackStack() },
                onArtworkClick = { runId -> navController.navigate(DallimDestinations.dallimbookDetail(runId)) },
                onEmptySlotClick = { routeId -> navController.navigate(DallimDestinations.routeDetail(routeId)) },
                onTabSelected = { tab -> navController.navigateToTab(tab) },
            )
        }

        composable(
            route = DallimDestinations.DALLIMBOOK_DETAIL,
            arguments = listOf(navArgument(DallimDestinations.ARG_RUN_ID) { type = NavType.StringType }),
        ) {
            DallimbookDetailRoute(
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(DallimDestinations.EXPLORE) {
            DiscoverRoute(
                onBackClick = { navController.popBackStack() },
                onRouteClick = { routeId -> navController.navigate(DallimDestinations.routeDetail(routeId)) },
                onTabSelected = { tab -> navController.navigateToTab(tab) },
                onCreateCourseClick = { navController.navigate(DallimDestinations.COURSE_CREATE_ENTRY) },
                onRaceTabClick = { navController.navigate(DallimDestinations.RACE_LIST) },
                onSocialTabClick = { navController.navigate(DallimDestinations.SOCIAL_SESSION_LIST) },
            )
        }

        composable(DallimDestinations.RACE_LIST) {
            RaceListRoute(
                onBackClick = { navController.popBackStack() },
                // 탐색(S-11)이 이미 백스택에 있는 왕복 구조이므로 새로 navigate하지 않고
                // 뒤로가기만으로 돌아간다 (RaceListScreen.kt 상단 주석).
                onExploreCoursesClick = { navController.popBackStack() },
                onRaceClick = { raceId -> navController.navigate(DallimDestinations.raceDetail(raceId)) },
            )
        }

        composable(
            route = DallimDestinations.RACE_DETAIL,
            arguments = listOf(navArgument(DallimDestinations.ARG_RACE_ID) { type = NavType.StringType }),
        ) {
            RaceDetailRoute(
                onBackClick = { navController.popBackStack() },
                onCoursePreviewClick = { raceId -> navController.navigate(DallimDestinations.raceCoursePreview(raceId)) },
            )
        }

        composable(
            route = DallimDestinations.RACE_COURSE_PREVIEW,
            arguments = listOf(navArgument(DallimDestinations.ARG_RACE_ID) { type = NavType.StringType }),
        ) {
            RaceCoursePreviewRoute(
                onBackClick = { navController.popBackStack() },
                // S-20 러닝 준비로 그대로 진입 — RouteDetailRoute.onStartRunClick과 동일한 패턴.
                onRunSegmentClick = { routeId -> navController.navigate(DallimDestinations.runPrepare(routeId)) },
            )
        }

        composable(DallimDestinations.SOCIAL_SESSION_LIST) {
            SocialSessionListRoute(
                onBackClick = { navController.popBackStack() },
                onSessionClick = { sessionId -> navController.navigate(DallimDestinations.socialSessionDetail(sessionId)) },
                onCreateClick = { navController.navigate(DallimDestinations.SOCIAL_SESSION_CREATE) },
            )
        }

        composable(DallimDestinations.SOCIAL_SESSION_CREATE) {
            SocialSessionCreateRoute(
                onBackClick = { navController.popBackStack() },
                onCreated = {
                    // S-30(세션 탐색) back stack entry의 SavedStateHandle에 결과 플래그를 심어
                    // 돌아갔을 때 목록이 자동으로 새로고침되게 한다
                    // (com.dallim.app.meetup.list.MeetupListViewModel.RESULT_MEETUP_CREATED와
                    // 동일 패턴).
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(SocialSessionListViewModel.RESULT_SESSION_CREATED, true)
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = DallimDestinations.SOCIAL_SESSION_DETAIL,
            arguments = listOf(navArgument(DallimDestinations.ARG_SOCIAL_SESSION_ID) { type = NavType.StringType }),
        ) {
            SocialSessionDetailRoute(
                onBackClick = { navController.popBackStack() },
                onApplicantsClick = { sessionId ->
                    navController.navigate(DallimDestinations.socialSessionApplicants(sessionId))
                },
                onChatClick = { sessionId -> navController.navigate(DallimDestinations.socialSessionChat(sessionId)) },
                onCheckinClick = { sessionId -> navController.navigate(DallimDestinations.socialSessionCheckin(sessionId)) },
            )
        }

        composable(
            route = DallimDestinations.SOCIAL_SESSION_CHAT,
            arguments = listOf(navArgument(DallimDestinations.ARG_SOCIAL_SESSION_ID) { type = NavType.StringType }),
        ) {
            SocialSessionChatRoute(onBackClick = { navController.popBackStack() })
        }

        composable(
            route = DallimDestinations.SOCIAL_SESSION_CHECKIN,
            arguments = listOf(navArgument(DallimDestinations.ARG_SOCIAL_SESSION_ID) { type = NavType.StringType }),
        ) {
            SocialSessionCheckinRoute(
                onBackClick = { navController.popBackStack() },
                onFeedbackClick = { sessionId -> navController.navigate(DallimDestinations.socialSessionFeedback(sessionId)) },
            )
        }

        composable(
            route = DallimDestinations.SOCIAL_SESSION_FEEDBACK,
            arguments = listOf(navArgument(DallimDestinations.ARG_SOCIAL_SESSION_ID) { type = NavType.StringType }),
        ) {
            // 평가 완료/건너뛰기 후 돌아갈 곳 — S-32(세션 상세)까지 한 번에 pop한다(체크인 화면
            // 까지 포함해 세 화면 전부 스택에서 걷어낸다, 평가를 다시 볼 이유가 없음).
            SocialSessionFeedbackRoute(
                onDoneClick = {
                    navController.popBackStack(route = DallimDestinations.SOCIAL_SESSION_DETAIL, inclusive = false)
                },
            )
        }

        composable(DallimDestinations.RUNNING_MATE_LIST) {
            RunningMateListRoute(onBackClick = { navController.popBackStack() })
        }

        composable(
            route = DallimDestinations.SOCIAL_SESSION_APPLICANTS,
            arguments = listOf(navArgument(DallimDestinations.ARG_SOCIAL_SESSION_ID) { type = NavType.StringType }),
        ) {
            SocialSessionApplicantsRoute(
                onBackClick = {
                    // 승인으로 approvedCount가 바뀌었을 수 있으니 S-32(세션 상세)로 돌아갈 때
                    // 항상 새로고침 플래그를 심는다 — 승인이 없었으면 SocialSessionDetailViewModel
                    // 이 다시 같은 값을 받아올 뿐 무해하다.
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(SocialSessionDetailViewModel.RESULT_SOCIAL_SESSION_UPDATED, true)
                    navController.popBackStack()
                },
            )
        }

        composable(DallimDestinations.COURSE_CREATE_ENTRY) {
            CourseCreateEntryRoute(
                onBackClick = { navController.popBackStack() },
                onDrawClick = { navController.navigate(DallimDestinations.COURSE_DRAW) },
                onAiGenerateClick = { navController.navigate(DallimDestinations.COURSE_AI_GENERATE) },
            )
        }

        composable(DallimDestinations.COURSE_DRAW) {
            DrawRouteRoute(onBackClick = { navController.popBackStack() })
        }

        composable(DallimDestinations.COURSE_AI_GENERATE) {
            AiRouteRoute(onBackClick = { navController.popBackStack() })
        }

        composable(DallimDestinations.MY) {
            MyRoute(
                onTabSelected = { tab -> navController.navigateToTab(tab) },
                onMedalShelfClick = { navController.navigate(DallimDestinations.MEDAL_SHELF) },
                onRunningMatesClick = { navController.navigate(DallimDestinations.RUNNING_MATE_LIST) },
                onLoggedOut = {
                    // 로그아웃 — S-02(로그인) 아래 전체 백스택(홈/탭 포함)을 비운다
                    // (docs/01-feature-spec.md §1.5). SplashViewModel의 로그인 성공 시
                    // popUpTo(SPLASH){inclusive=true}와 반대 방향: 여기서는 그래프 전체를 비운다.
                    navController.navigate(DallimDestinations.LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }

        composable(DallimDestinations.MEDAL_SHELF) {
            MedalShelfRoute(
                onBackClick = { navController.popBackStack() },
                onAddClick = { navController.navigate(DallimDestinations.raceRecordCreate()) },
                onItemClick = { raceRecordId -> navController.navigate(DallimDestinations.raceRecordEdit(raceRecordId)) },
            )
        }

        composable(
            route = DallimDestinations.RACE_RECORD_EDIT,
            arguments = listOf(
                navArgument(DallimDestinations.ARG_RACE_RECORD_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            // S-91(메달 선반)로 돌아가면 그 destination의 content 람다가 다시 컴포지션에
            // 들어오면서 `LaunchedEffect(Unit)`이 목록을 다시 불러온다(MedalShelfRoute 참고) —
            // 별도의 SavedStateHandle 결과 플래그 relay가 필요 없다.
            RaceRecordEditRoute(
                onBackClick = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
            )
        }

        composable(
            route = DallimDestinations.ROUTE_DETAIL,
            arguments = listOf(navArgument(DallimDestinations.ARG_ROUTE_ID) { type = NavType.StringType }),
        ) {
            RouteDetailRoute(
                onBackClick = { navController.popBackStack() },
                onStartRunClick = { routeId -> navController.navigate(DallimDestinations.runPrepare(routeId)) },
                onMeetupsClick = { routeId -> navController.navigate(DallimDestinations.meetupList(routeId)) },
            )
        }

        composable(
            route = DallimDestinations.MEETUP_LIST,
            arguments = listOf(navArgument(DallimDestinations.ARG_ROUTE_ID) { type = NavType.StringType }),
        ) {
            MeetupListRoute(
                onBackClick = { navController.popBackStack() },
                onMeetupClick = { meetupId -> navController.navigate(DallimDestinations.meetupDetail(meetupId)) },
                onCreateClick = { routeId -> navController.navigate(DallimDestinations.meetupCreate(routeId)) },
            )
        }

        composable(
            route = DallimDestinations.MEETUP_CREATE,
            arguments = listOf(navArgument(DallimDestinations.ARG_ROUTE_ID) { type = NavType.StringType }),
        ) {
            MeetupCreateRoute(
                onBackClick = { navController.popBackStack() },
                onCreated = {
                    // S-47(모집 목록) back stack entry의 SavedStateHandle에 결과 플래그를 심어
                    // 돌아갔을 때 목록이 자동으로 새로고침되게 한다(MeetupListViewModel.init 참고).
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(MeetupListViewModel.RESULT_MEETUP_CREATED, true)
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = DallimDestinations.MEETUP_DETAIL,
            arguments = listOf(navArgument(DallimDestinations.ARG_MEETUP_ID) { type = NavType.StringType }),
        ) {
            // S-49에서 참가/나가기로 상태가 바뀌었을 수 있으니, 뒤로가기든 모집 취소 성공이든
            // 항상 S-47(모집 목록) back stack entry에 새로고침 플래그를 심는다 — S-48의 onCreated와
            // 같은 결과 전달 패턴(MeetupListViewModel.RESULT_MEETUP_CREATED)을 재사용한다.
            fun goBackAndRefreshList() {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(MeetupListViewModel.RESULT_MEETUP_CREATED, true)
                navController.popBackStack()
            }

            MeetupDetailRoute(
                onBackClick = { goBackAndRefreshList() },
                onCancelled = { goBackAndRefreshList() },
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
                    // "달림북에 저장하고 닫기" — 방금 완성한 작품이 바로 보이는 달림북 그리드로
                    // 이동한다. HOME까지는 백스택에 남기고(뒤로가기 시 홈으로) 그 위의
                    // 코스상세/러닝/결과 화면들만 걷어낸다.
                    navController.navigate(DallimDestinations.DALLIMBOOK_GRID) {
                        popUpTo(DallimDestinations.HOME)
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

/**
 * 하단 탭바(01-feature-spec.md §1.0)의 표준 전환 패턴 — `popUpTo`+`launchSingleTop`+
 * `restoreState`로 탭을 오가도 백스택이 계속 쌓이지 않고 각 탭의 상태(스크롤 위치 등)를
 * 보존한다.
 *
 * 문서(§1.0)가 예시로 든 `navController.graph.findStartDestination().id`를 그대로 anchor로
 * 쓰지 않는다: 이 NavHost의 실제 `startDestination`은 SPLASH이고, 로그인 완료 시점에
 * `popUpTo(SPLASH) { inclusive = true }`로 이미 백스택에서 걷어낸 상태라 SPLASH는 로그인
 * 이후 백스택에 존재하지 않는다. `popUpTo`의 대상이 현재 백스택에 없으면 아무것도 pop되지
 * 않아(anchor를 못 찾음) 탭을 오갈 때마다 백스택이 무한히 쌓이는 버그가 난다. 대신 탭
 * 플로우의 실제 루트인 HOME을 anchor로 써서 back stack이 항상
 * "HOME (+ 현재 탭이 HOME이 아니면 그 탭 1개)"로 유지되게 한다 — RunResultRoute의
 * `onDoneClick`이 이미 같은 이유로 HOME을 anchor로 쓰고 있다(위 참고).
 */
private fun NavHostController.navigateToTab(tab: DallimTab) {
    val route = when (tab) {
        DallimTab.HOME -> DallimDestinations.HOME
        DallimTab.EXPLORE -> DallimDestinations.EXPLORE
        DallimTab.DALLIMBOOK -> DallimDestinations.DALLIMBOOK_GRID
        DallimTab.MY -> DallimDestinations.MY
    }
    navigate(route) {
        popUpTo(DallimDestinations.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
