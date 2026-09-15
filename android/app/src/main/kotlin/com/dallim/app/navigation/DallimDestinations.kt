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

    /**
     * 대회 탭 (2026-09-16 재편) — 2026-09-15에 도입했던 "소셜" 통합 허브 탭을 사용자 지시로
     * 폐기하고, 대회(S-80)를 다시 독립 최상위 탭으로 되돌렸다. v1.3 SPEC(S-00~S-86) 번호
     * 체계에 이 화면 자체의 번호가 없다 — RaceListBody를 그대로 재사용한 순수 IA 변경이라
     * 가짜 S-번호를 붙이지 않고 설명적 id를 쓴다.
     */
    const val RACE_TAB = "race_tab"

    /**
     * 채팅 탭 (2026-09-16 신규, docs/02-api-spec.md 18장 — v1.3 SPEC 밖). 내가 호스트/`APPROVED`
     * 참가자인 소셜 세션들의 채팅 인박스. v1.3 번호 체계에 없어 설명적 id를 쓴다.
     */
    const val CHAT_TAB = "chat_tab"

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

    /**
     * 차단 관리 (2026-09-16 신규, docs/02-api-spec.md 18.2 — v1.3 SPEC 밖). 마이(S-42)에서만
     * 진입한다(새 탭을 만들지 않는다는 원칙 유지, MEDAL_SHELF/RUNNING_MATE_LIST와 동일 패턴).
     */
    const val BLOCKED_USER_LIST = "blocked_user_list"

    const val ARG_RACE_ID = "raceId"

    /**
     * S-80 대회 캘린더 — 15장 러닝 커리어(`com.dallim.app.career`, 내가 과거에 뛴 대회의
     * 자기신고 완주 이력)와 완전히 별개 도메인이니 혼동 금지 — 이건 "앞으로 열릴 대회"다.
     * 2026-09-16부터 [RACE_TAB](독립 최상위 탭)에서 `RaceListBody`로 렌더링된다(2026-09-15에
     * 잠깐 있었던 소셜 허브 세그먼트 통합은 폐기됨).
     */
    private const val RACE_DETAIL_BASE = "s81_race_detail" // S-81
    const val RACE_DETAIL = "$RACE_DETAIL_BASE/{$ARG_RACE_ID}"
    fun raceDetail(raceId: String) = "$RACE_DETAIL_BASE/$raceId"

    /**
     * S-85 대회 코스 미리 달리기 (2026-09-12 신규, docs/02-api-spec.md 16.6). 대회 상세(S-81)에서
     * 공식 코스(`hasCourse == true`)가 있을 때만 진입한다. "이 구간 달리기"는 별도 route가 아니라
     * 구간의 `routeId`로 [RUN_PREPARE](S-20)를 그대로 재사용한다.
     */
    private const val RACE_COURSE_PREVIEW_BASE = "s85_race_course_preview"
    const val RACE_COURSE_PREVIEW = "$RACE_COURSE_PREVIEW_BASE/{$ARG_RACE_ID}"
    fun raceCoursePreview(raceId: String) = "$RACE_COURSE_PREVIEW_BASE/$raceId"

    const val ARG_SOCIAL_SESSION_ID = "sessionId"

    /**
     * S-30~S-34 소셜 세션 1단계 (docs/달림_화면별_상세기획서_v1.3.md PART 3-D,
     * docs/02-api-spec.md 17장). S-30(세션 탐색)은 2026-09-16부터 탐색([EXPLORE])의
     * `[그림 코스]/[소셜]` 세그먼트 안에 `SocialSessionListBody`로 직접 렌더링된다(2026-09-15에
     * 잠깐 있었던 소셜 허브 통합 탭은 폐기됨 — 과거 SOCIAL_SESSION_LIST 독립 라우트도 그 전에
     * 이미 삭제됨). 팀채팅/체크인/Ready Check/평가/Running Mate(S-35~S-39)는 2단계라 여기 없다.
     */
    private const val SOCIAL_SESSION_CREATE_BASE = "s31_social_session_create" // S-31

    /**
     * `routeId`는 optional query-string 인자다 — 코스 상세(S-16)의 "같이 뛸 사람 모으기"에서
     * 진입하면 그 코스가 이미 선택된 채로 폼이 열리고(2026-09-16 신규,
     * [com.dallim.app.social.create.SocialSessionCreateViewModel] 참고), 기존 "세션 열기" FAB
     * (탐색의 소셜 세그먼트)에서 진입하면 이 인자 없이 코스 선택 스텝부터 시작한다 —
     * `RACE_RECORD_EDIT`와 동일한 optional query-string nav-arg 패턴.
     */
    const val SOCIAL_SESSION_CREATE = "$SOCIAL_SESSION_CREATE_BASE?$ARG_ROUTE_ID={$ARG_ROUTE_ID}"
    fun socialSessionCreate(routeId: String? = null) =
        if (routeId != null) "$SOCIAL_SESSION_CREATE_BASE?$ARG_ROUTE_ID=$routeId" else SOCIAL_SESSION_CREATE_BASE

    private const val SOCIAL_SESSION_DETAIL_BASE = "s32_social_session_detail" // S-32
    const val SOCIAL_SESSION_DETAIL = "$SOCIAL_SESSION_DETAIL_BASE/{$ARG_SOCIAL_SESSION_ID}"
    fun socialSessionDetail(sessionId: String) = "$SOCIAL_SESSION_DETAIL_BASE/$sessionId"

    private const val SOCIAL_SESSION_APPLICANTS_BASE = "s34_social_session_applicants" // S-34
    const val SOCIAL_SESSION_APPLICANTS = "$SOCIAL_SESSION_APPLICANTS_BASE/{$ARG_SOCIAL_SESSION_ID}"
    fun socialSessionApplicants(sessionId: String) = "$SOCIAL_SESSION_APPLICANTS_BASE/$sessionId"

    /**
     * S-35~S-39 소셜 세션 2단계 (2026-09-15 신규, docs/달림_화면별_상세기획서_v1.3.md PART 3-D,
     * docs/02-api-spec.md 17.11 이하). S-32(세션 상세)에서 호스트/`APPROVED` 참가자에게만 채팅·
     * 체크인 진입 버튼이 보인다. S-36(GPS 체크인)과 S-37(Ready Check)은 작업 브리핑 지시대로
     * 한 화면(하나의 자연스러운 동선)으로 합쳐 [SOCIAL_SESSION_CHECKIN] 하나로 둔다. S-38(평가)
     * 진입점은 그 체크인 화면이 `started == true`를 확인했을 때 보여주는 CTA 하나뿐이다(별도
     * "세션 종료" 액션이 SPEC에 없어 이 값으로 근사). S-39(Running Mate)는 세션에 종속되지
     * 않는 "내 소유물" 목록이라 인자가 없다 — 마이(S-42)에서 진입한다.
     */
    private const val SOCIAL_SESSION_CHAT_BASE = "s35_social_session_chat"
    const val SOCIAL_SESSION_CHAT = "$SOCIAL_SESSION_CHAT_BASE/{$ARG_SOCIAL_SESSION_ID}"
    fun socialSessionChat(sessionId: String) = "$SOCIAL_SESSION_CHAT_BASE/$sessionId"

    private const val SOCIAL_SESSION_CHECKIN_BASE = "s36_social_session_checkin" // S-36 + S-37
    const val SOCIAL_SESSION_CHECKIN = "$SOCIAL_SESSION_CHECKIN_BASE/{$ARG_SOCIAL_SESSION_ID}"
    fun socialSessionCheckin(sessionId: String) = "$SOCIAL_SESSION_CHECKIN_BASE/$sessionId"

    private const val SOCIAL_SESSION_FEEDBACK_BASE = "s38_social_session_feedback"
    const val SOCIAL_SESSION_FEEDBACK = "$SOCIAL_SESSION_FEEDBACK_BASE/{$ARG_SOCIAL_SESSION_ID}"
    fun socialSessionFeedback(sessionId: String) = "$SOCIAL_SESSION_FEEDBACK_BASE/$sessionId"

    const val RUNNING_MATE_LIST = "s39_running_mate_list" // S-39
}
