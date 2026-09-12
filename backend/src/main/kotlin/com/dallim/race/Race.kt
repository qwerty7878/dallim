package com.dallim.race

import kotlinx.serialization.SerialName
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * 대회 캘린더(Race Calendar) 도메인 — docs/02-api-spec.md 16장,
 * docs/달림_화면별_상세기획서_v1.3.md S-80(대회 캘린더)/S-81(대회 상세) 근거
 * (2026-09-07, `docs/01-feature-spec.md` 1.9/15.7이 유보해둔 부분을 채우는 라운드).
 *
 * **주의**: 이 도메인은 `com.dallim.racerecord`(내가 과거에 뛴 대회의 완주 이력/메달 선반)와
 * 완전히 별개다 — 이쪽은 "앞으로 열릴 대회 정보를 찾아보고 담아두는" 기능이다. 패키지도
 * 다르고(`race` vs `racerecord`), id 프리픽스도 다르다(`rce_*`/`rcc_*`/`rcs_*` vs `race_*`,
 * com.dallim.common.IdGenerator 참고). 헷갈리지 말 것.
 *
 * 접수 상태([RaceStatus])는 저장하지 않고 매 조회 시 현재 시각과 registrationStart/End를
 * 비교해 계산한다(RaceService.computeStatus) — 배치/스케줄러 불필요.
 */
enum class RaceStatus {
    UPCOMING, // 접수 시작 전
    OPEN, // 접수중
    CLOSED, // 접수 마감
}

/**
 * 대회 종목(5K/10K/하프/풀/울트라/트레일). 자기신고 완주 이력용
 * `com.dallim.racerecord.RaceCategory`와는 다른 enum이다 — 이쪽엔 자유 입력 대체용 `OTHER`가
 * 없다(대회 캘린더는 운영자가 수동 큐레이션하는 데이터라 종목이 항상 명확하다).
 */
enum class RaceCategory {
    @SerialName("5K") FIVE_K,
    @SerialName("10K") TEN_K,
    HALF,
    FULL,
    ULTRA,
    TRAIL,
}

object RaceTable : Table("races") {
    val id = varchar("id", 32)
    val name = varchar("name", 100)
    val region = varchar("region", 50) // 자유 문자열, 예: "서울", "성남" — GET /races?region= 매칭 기준
    val location = varchar("location", 200) // 집결 장소
    val raceDate = timestamp("race_date") // 대회 일시
    val registrationStart = timestamp("registration_start")
    val registrationEnd = timestamp("registration_end")
    val organizer = varchar("organizer", 100)
    val souvenir = varchar("souvenir", 200).nullable() // 기념품, 선택

    // 대회 코스 미리 달리기(S-85, 2026-09-12, docs/02-api-spec.md 16.6) — 공식 코스로 등록된
    // SketchRoute. 코스가 없는 대회가 대다수라 nullable.
    val courseRouteId = varchar("course_route_id", 32).references(com.dallim.route.SketchRouteTable.id).nullable()

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

data class Race(
    val id: String,
    val name: String,
    val region: String,
    val location: String,
    val raceDate: Instant,
    val registrationStart: Instant,
    val registrationEnd: Instant,
    val organizer: String,
    val souvenir: String?,
    val courseRouteId: String?,
    val createdAt: Instant,
)

/**
 * 대회 공식 코스(`Race.courseRouteId`)를 잘라낸 구간들 — S-85, docs/02-api-spec.md 16.6.
 * [routeId]는 그 구간만 담은 별도의 SketchRoute(항상 `is_preview_segment = true`)를 가리킨다
 * — "이 구간 달리기"는 새 엔드포인트 없이 이 routeId로 기존 POST /runs를 그대로 태우는
 * 방식이라, 완주 판정(RunJudgementService)도 구간을 특별 취급하지 않고 평범한 SketchRoute
 * 처럼 그대로 통과시킨다.
 */
object RaceCourseSegmentTable : Table("race_course_segments") {
    val id = varchar("id", 32)
    val raceId = varchar("race_id", 32).references(RaceTable.id)
    val routeId = varchar("route_id", 32).references(com.dallim.route.SketchRouteTable.id)
    val label = varchar("label", 50) // 예: "출발~1.4km"
    val orderIndex = integer("order_index")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

data class RaceCourseSegment(
    val id: String,
    val raceId: String,
    val routeId: String,
    val label: String,
    val orderIndex: Int,
)

/**
 * 대회 하나에 여러 종목이 있을 수 있고, 종목마다 참가비/정원/컷오프가 다를 수 있어 자식
 * 테이블로 모델링한다(작업 브리핑 지시). `distanceKm`은 ULTRA/TRAIL처럼 대회마다 거리가
 * 제각각인 경우를 위해 nullable로 둔다(`com.dallim.racerecord`의 동일 관례 참고).
 */
object RaceCategoryOptionTable : Table("race_category_options") {
    val id = varchar("id", 32)
    val raceId = varchar("race_id", 32).references(RaceTable.id)
    val category = enumerationByName("category", 16, RaceCategory::class)
    val distanceKm = double("distance_km").nullable()
    val feeKrw = integer("fee_krw").nullable()
    val capacity = integer("capacity").nullable()
    val cutoffMinutes = integer("cutoff_minutes").nullable()

    override val primaryKey = PrimaryKey(id)
}

data class RaceCategoryOption(
    val id: String,
    val raceId: String,
    val category: RaceCategory,
    val distanceKm: Double?,
    val feeKrw: Int?,
    val capacity: Int?,
    val cutoffMinutes: Int?,
)

/**
 * User <-> Race 담기(bookmark) — `com.dallim.user.SavedRouteTable`(코스 저장)과 완전히 같은
 * 패턴이지만 대회용 별도 테이블이다. `savedCount`("달림 러너 N명 참가 예정")는 이 테이블의
 * row 수로 대체한다 — 실제 참가 여부 검증은 하지 않는다(작업 브리핑 지시, 과설계 금지).
 */
object RaceSaveTable : Table("race_saves") {
    val id = varchar("id", 32)
    val userId = varchar("user_id", 32).references(com.dallim.user.UserTable.id)
    val raceId = varchar("race_id", 32).references(RaceTable.id)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_race_saves_user_race", userId, raceId)
    }
}
