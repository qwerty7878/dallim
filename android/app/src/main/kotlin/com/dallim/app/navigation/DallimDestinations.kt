package com.dallim.app.navigation

/**
 * Navigation route constants, named after the screen IDs in docs/01-feature-spec.md 1장
 * (S-00 ~ S-42) so it's easy to cross-reference the spec while wiring screens.
 *
 * S-22(일시정지/종료), S-23(코스이탈안내), S-24(완주판정처리) are NOT separate destinations —
 * per spec they're a Service-internal state machine / a banner / a processing overlay that all
 * live inside the S-21 screen and its ViewModel, not their own nav routes.
 */
object DallimDestinations {
    const val SPLASH = "s00_splash" // S-00
    const val ONBOARDING_CAROUSEL = "s01_onboarding_carousel" // S-01
    const val LOGIN = "s02_login" // S-02
    const val SIGNUP = "s02b_signup" // S-02b
    const val TERMS = "s03_terms" // S-03
    const val PROFILE_SETUP = "s04_profile_setup" // S-04
    const val PERMISSION = "s05_permission" // S-05
    const val FIRST_ROUTE_SUGGESTION = "s06_first_route_suggestion" // S-06

    const val HOME = "s10_home" // S-10
    const val EXPLORE = "s11_explore" // S-11

    const val ARG_ROUTE_ID = "routeId"
    const val ARG_RUN_ID = "runId"

    private const val ROUTE_DETAIL_BASE = "s16_route_detail" // S-16
    const val ROUTE_DETAIL = "$ROUTE_DETAIL_BASE/{$ARG_ROUTE_ID}"
    fun routeDetail(routeId: String) = "$ROUTE_DETAIL_BASE/$routeId"

    const val SAVED_ROUTES = "s17_saved_routes" // S-17

    /**
     * S-43/44/45 코스 만들기(직접 그리기/AI 자동 생성) — docs/01-feature-spec.md에는 없던 화면.
     * docs/02-api-spec.md 8장(2026-09-03 조기 착수) API에 맞춰 신설, 오케스트레이터 지시로
     * 기존 S-42(마이) 다음 번호를 잇지 않고 코스 관련 화면군(S-1x/4x)과 구분되는 새 접두로 부여.
     */
    const val COURSE_CREATE_ENTRY = "s43_course_create_entry" // S-43
    const val COURSE_DRAW = "s44_course_draw" // S-44
    const val COURSE_AI_GENERATE = "s45_course_ai_generate" // S-45

    private const val RUN_PREPARE_BASE = "s20_run_prepare" // S-20
    const val RUN_PREPARE = "$RUN_PREPARE_BASE/{$ARG_ROUTE_ID}"
    fun runPrepare(routeId: String) = "$RUN_PREPARE_BASE/$routeId"

    private const val RUN_NAVIGATION_BASE = "s21_run_navigation" // S-21 (also covers S-22/S-23/S-24 states)
    const val RUN_NAVIGATION = "$RUN_NAVIGATION_BASE/{$ARG_RUN_ID}/{$ARG_ROUTE_ID}"
    fun runNavigation(runId: String, routeId: String) = "$RUN_NAVIGATION_BASE/$runId/$routeId"

    private const val RUN_RESULT_BASE = "s25_run_result" // S-25
    const val RUN_RESULT = "$RUN_RESULT_BASE/{$ARG_RUN_ID}"
    fun runResult(runId: String) = "$RUN_RESULT_BASE/$runId"

    private const val SHARE_CARD_BASE = "s26_share_card" // S-26
    const val SHARE_CARD = "$SHARE_CARD_BASE/{$ARG_RUN_ID}"
    fun shareCard(runId: String) = "$SHARE_CARD_BASE/$runId"

    const val DALLIMBOOK_GRID = "s40_dallimbook_grid" // S-40

    private const val DALLIMBOOK_DETAIL_BASE = "s41_dallimbook_detail" // S-41
    const val DALLIMBOOK_DETAIL = "$DALLIMBOOK_DETAIL_BASE/{$ARG_RUN_ID}"
    fun dallimbookDetail(runId: String) = "$DALLIMBOOK_DETAIL_BASE/$runId"

    const val MY = "s42_my" // S-42
}
