package com.dallim.social

import com.dallim.route.SketchRouteTable
import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

/**
 * 소셜 세션 (S-30~S-39, docs/달림_화면별_상세기획서_v1.3.md 421~491행) — 1단계 범위: 탐색/생성/
 * 상세/참가신청/호스트 승인(S-30~S-34)만. 팀 채팅(S-35)/GPS 체크인(S-36)/Ready Check(S-37)/
 * 종료 후 평가(S-38)/Running Mate(S-39)는 전부 2단계다 — 이 파일에는 그 흔적을 두지 않는다.
 *
 * com.dallim.meetup(같이 달리기 모집, S-47/48/49)과는 의도적으로 완전히 분리된 패키지다: meetup은
 * 승인 없이 즉시 참가하는 1회성 게시판이고, 이 도메인은 호스트 승인 워크플로우 + 참가 조건
 * (성별/온도) + (2단계에서) 채팅/체크인까지 있는 별개의 무거운 기능이라 재사용/확장하지 않는다.
 *
 * status 컬럼 철학은 MeetupTable과 동일: RECRUITING/CANCELLED만 저장하고, 화면에 보이는
 * NEAR_CONFIRMATION/CONFIRMED는 SocialSessionService가 매 조회 시 승인된 참가자 수 vs
 * min/maxParticipants로 계산한다(배치 잡 없음) — SocialSessionDisplayStatus 참고.
 */
enum class SocialSessionStatus {
    RECRUITING,
    CANCELLED,
}

/**
 * 참가 성별 조건 — SPEC(v1.3 S-31)이 "성별 조건 4종"이라고만 하고 정확한 값 목록을 주지 않아
 * 이번에 4개로 확정한다: 전체 허용(ANY), 호스트와 동성만(SAME_AS_HOST), 여성전용(FEMALE_ONLY),
 * 남성전용(MALE_ONLY). 실제 러닝 크루 앱들이 흔히 제공하는 조건 조합과 맞아떨어지고, "성별 조건은
 * 사유를 노출하지 않음"(S-32)이라는 요구사항을 지키기에도 충분한 표현력이다.
 */
enum class SocialSessionGenderCondition {
    ANY,
    SAME_AS_HOST,
    FEMALE_ONLY,
    MALE_ONLY,
}

/** 우천 시 정책 — S-31 원문의 세 가지 선택지를 그대로 옮김. */
enum class SocialSessionRainPolicy {
    PROCEED,
    CANCEL,
    DECIDE_LATER,
}

/** 참가 신청 상태 — S-33/34. 호스트는 이 테이블에 들어가지 않는다(주최자는 참가자가 아님). */
enum class SocialSessionApplicantStatus {
    PENDING,
    APPROVED,
    EXPIRED,
    CANCELLED,
}

object SocialSessionTable : Table("social_sessions") {
    val id = varchar("id", 32)
    val hostUserId = varchar("host_user_id", 32).references(UserTable.id)
    val routeId = varchar("route_id", 32).references(SketchRouteTable.id)

    val title = varchar("title", 60)
    val scheduledAt = timestamp("scheduled_at")

    val minParticipants = integer("min_participants")
    val maxParticipants = integer("max_participants")

    // 콤마 구분 자유 텍스트 (S-31 "러닝 스타일 4종") -- SPEC이 정확한 값 목록을 주지 않아 닫힌
    // enum으로 만들지 않는다. 합리적인 예시 4개(코드 수준 참고용, 강제 X):
    // "대화하면서", "페이스러닝", "땀나게", "관광하며" 정도. SocialSessionDtos.kt의
    // runningStyles: List<String> <-> 이 콤마 구분 varchar 사이의 변환은 Repository가 담당.
    val runningStyles = varchar("running_styles", 200).nullable()

    val beginnerFriendly = bool("beginner_friendly").default(false)

    // 있으면 이 온도 미만인 유저는 참가 신청이 막힌다(SocialSessionService 참고). 없으면 온도
    // 조건 없음.
    val minRunningTemperature = double("min_running_temperature").nullable()

    val genderCondition = enumerationByName("gender_condition", 16, SocialSessionGenderCondition::class)
        .default(SocialSessionGenderCondition.ANY)

    val description = text("description").nullable()

    val meetingPointLat = double("meeting_point_lat")
    val meetingPointLng = double("meeting_point_lng")
    val meetingPointDescription = text("meeting_point_description").nullable()

    val rainPolicy = enumerationByName("rain_policy", 16, SocialSessionRainPolicy::class)
        .default(SocialSessionRainPolicy.DECIDE_LATER)

    val status = enumerationByName("status", 16, SocialSessionStatus::class).default(SocialSessionStatus.RECRUITING)

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

data class SocialSession(
    val id: String,
    val hostUserId: String,
    val routeId: String,
    val title: String,
    val scheduledAt: Instant,
    val minParticipants: Int,
    val maxParticipants: Int,
    val runningStyles: List<String>,
    val beginnerFriendly: Boolean,
    val minRunningTemperature: Double?,
    val genderCondition: SocialSessionGenderCondition,
    val description: String?,
    val meetingPointLat: Double,
    val meetingPointLng: Double,
    val meetingPointDescription: String?,
    val rainPolicy: SocialSessionRainPolicy,
    val status: SocialSessionStatus,
    val createdAt: Instant,
)

/**
 * 참가 신청자 — 호스트는 여기 없다(SocialSession.hostUserId로 이미 식별됨, 자동 참가자 등록도
 * 하지 않음 — com.dallim.meetup.MeetupParticipantTable과 다른 지점). 복합 유니크로 동일 세션
 * 중복 신청을 막는다(재신청도 막는다 — 이번 1단계는 상태 전환당 새 행을 만들지 않고 딱 한 번만
 * 신청 가능하게 단순하게 둔다. 과설계 금지).
 */
object SocialSessionApplicantTable : Table("social_session_applicants") {
    val sessionId = varchar("session_id", 32).references(SocialSessionTable.id)
    val userId = varchar("user_id", 32).references(UserTable.id)

    val status = enumerationByName("status", 16, SocialSessionApplicantStatus::class)
        .default(SocialSessionApplicantStatus.PENDING)
    val message = text("message").nullable()
    val appliedAt = timestamp("applied_at").clientDefault { Instant.now() }
    val respondedAt = timestamp("responded_at").nullable()

    // 복합 PK (sessionId, userId) 자체가 "동일 세션 중복 신청 방지" 유니크 제약이다 (별도
    // uniqueIndex 불필요 -- com.dallim.meetup.MeetupParticipantTable과 동일 컨벤션).
    override val primaryKey = PrimaryKey(sessionId, userId)
}

data class SocialSessionApplicant(
    val sessionId: String,
    val userId: String,
    val status: SocialSessionApplicantStatus,
    val message: String?,
    val appliedAt: Instant,
    val respondedAt: Instant?,
)

/**
 * 화면에 보이는 계산된 상태 — SocialSessionStatus(저장값)와 별개, SocialSessionService에서만
 * 만들어진다. RECRUITING/NEAR_CONFIRMATION/CONFIRMED는 APPROVED 참가자 수 vs
 * min/maxParticipants로, CANCELLED는 저장된 status를 그대로 반영한다.
 */
enum class SocialSessionDisplayStatus {
    RECRUITING,
    NEAR_CONFIRMATION,
    CONFIRMED,
    CANCELLED,
}
