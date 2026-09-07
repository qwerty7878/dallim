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

    const val ARG_COMFORTABLE_PACE = "comfortablePace"

    /**
     * S-04b 러닝 커리어 입력 (2026-09-06 신규, docs/02-api-spec.md 15장 근거) — S-04에서
     * `runningExperience == OVER_1_YEAR`를 선택했을 때만 진입(그 외에는 S-04에서 바로 S-05로).
     * S-04에서 고른 [com.dallim.app.onboarding.profile.ComfortablePace.apiValue]를 경로 인자로
     * 받아, 하프/풀 기록 입력 시 서버 페이스 제안과 비교하는 데 쓴다(실제 프로필 반영 API는
     * 없어 표시만 한다 — CareerEntryViewModel 참고).
     */
    private const val CAREER_ENTRY_BASE = "s04b_career_entry"
    const val CAREER_ENTRY = "$CAREER_ENTRY_BASE/{$ARG_COMFORTABLE_PACE}"
    fun careerEntry(comfortablePace: String) = "$CAREER_ENTRY_BASE/$comfortablePace"

    const val HOME = "s10_home" // S-10
    const val EXPLORE = "s11_explore" // S-11

    const val ARG_ROUTE_ID = "routeId"
    const val ARG_RUN_ID = "runId"
    const val ARG_MEETUP_ID = "meetupId"

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

    /** S-46 알림 목록 — 홈(S-10) 종 아이콘에서 진입 (docs/01-feature-spec.md §1.7). */
    const val NOTIFICATIONS = "s46_notifications" // S-46

    /**
     * S-47/48/49 같이 달리기 모집 (2026-09-05 신규, docs/01-feature-spec.md §1.8). Route 상세
     * (S-16)에 종속된 플로우라 새 탭바 항목은 없다 — 진입은 항상 Route 상세를 거친다(§1.8.1).
     */
    private const val MEETUP_LIST_BASE = "s47_meetup_list" // S-47
    const val MEETUP_LIST = "$MEETUP_LIST_BASE/{$ARG_ROUTE_ID}"
    fun meetupList(routeId: String) = "$MEETUP_LIST_BASE/$routeId"

    private const val MEETUP_CREATE_BASE = "s48_meetup_create" // S-48
    const val MEETUP_CREATE = "$MEETUP_CREATE_BASE/{$ARG_ROUTE_ID}"
    fun meetupCreate(routeId: String) = "$MEETUP_CREATE_BASE/$routeId"

    private const val MEETUP_DETAIL_BASE = "s49_meetup_detail" // S-49
    const val MEETUP_DETAIL = "$MEETUP_DETAIL_BASE/{$ARG_MEETUP_ID}"
    fun meetupDetail(meetupId: String) = "$MEETUP_DETAIL_BASE/$meetupId"

    const val ARG_RACE_RECORD_ID = "raceRecordId"

    /**
     * S-90 완주 이력 등록/편집 (2026-09-06 신규). `raceRecordId`가 없으면 등록 모드, 있으면
     * 수정 모드 — `GET /users/me/race-records/{id}` 단건 조회 API가 SPEC에 없어(15장 참고),
     * 수정 모드는 목록(`GET /users/me/race-records`)을 다시 불러와 id로 찾는다
     * (RaceRecordEditViewModel 참고). S-04b 온보딩과 입력 폼(RaceRecordFormFields)을 공유한다.
     */
    private const val RACE_RECORD_EDIT_BASE = "s90_race_record_edit"
    const val RACE_RECORD_EDIT = "$RACE_RECORD_EDIT_BASE?$ARG_RACE_RECORD_ID={$ARG_RACE_RECORD_ID}"
    fun raceRecordCreate() = RACE_RECORD_EDIT_BASE
    fun raceRecordEdit(raceRecordId: String) = "$RACE_RECORD_EDIT_BASE?$ARG_RACE_RECORD_ID=$raceRecordId"

    /**
     * S-91 완주 메달 선반 (2026-09-06 신규, docs/달림_화면별_상세기획서_v1.3.md PART 3-H
     * "S-82 내 대회" 중 메달 선반 부분만 이번 라운드 범위). 마이(S-42)에서만 진입 — 새 탭은
     * 만들지 않는다(4탭 구조 유지 원칙).
     */
    const val MEDAL_SHELF = "s91_medal_shelf" // S-91

    const val ARG_RACE_ID = "raceId"

    /**
     * S-80 대회 캘린더 (2026-09-07 신규, docs/달림_화면별_상세기획서_v1.3.md PART 3-H 664~692행,
     * docs/02-api-spec.md 16장). 15장 러닝 커리어(`com.dallim.app.career`, 내가 과거에 뛴 대회의
     * 자기신고 완주 이력)와 완전히 별개 도메인이니 혼동 금지 — 이건 "앞으로 열릴 대회"다.
     * 탐색(S-11)의 세그먼트 탭에서 진입한다(DiscoverScreen 참고).
     */
    const val RACE_LIST = "s80_race_list" // S-80

    private const val RACE_DETAIL_BASE = "s81_race_detail" // S-81
    const val RACE_DETAIL = "$RACE_DETAIL_BASE/{$ARG_RACE_ID}"
    fun raceDetail(raceId: String) = "$RACE_DETAIL_BASE/$raceId"
}
