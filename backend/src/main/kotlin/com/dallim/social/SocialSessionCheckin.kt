package com.dallim.social

import com.dallim.user.UserTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Duration
import java.time.Instant

enum class SocialSessionCheckinStatus {
    WAITING,
    CHECKED_IN,
    LATE,
    NO_SHOW,
}

/**
 * GPS 체크인(S-36)/Ready Check(S-37) — docs/02-api-spec.md 17장 이어서. 대상은 APPROVED 참가자
 * + 호스트 본인(호스트는 social_session_applicants에 없으므로 이 테이블엔 별도 행으로 들어간다).
 *
 * **LATE의 실제 의미** (작업 브리핑이 "네가 더 단순한 쪽으로 골라라"라고 한 부분의 선택):
 * 체크인 윈도우([SocialSessionCheckinWindow], scheduledAt-30분~+15분) + 반경(150m) 내 자가
 * 체크인은 항상 성공한다 — 윈도우/반경을 벗어난 시도는 그 자체로 400(거부)이지 LATE로 저장되는
 * 게 아니다. LATE는 "체크인은 성공했지만 scheduledAt *이후*에 도착"을 뜻한다
 * ([SocialSessionCheckinWindow.resolveStatus]). 이렇게 하면 배치/조회 시점 계산이 전혀 필요
 * 없다 — WAITING인 채로 시간이 흐른 사람은 그냥 WAITING으로 남아 있다가 /start(S-37)에서
 * NO_SHOW로 일괄 전환된다.
 */
object SocialSessionCheckinTable : Table("social_session_checkins") {
    val sessionId = varchar("session_id", 32).references(SocialSessionTable.id)
    val userId = varchar("user_id", 32).references(UserTable.id)

    val status = enumerationByName("status", 16, SocialSessionCheckinStatus::class)
        .default(SocialSessionCheckinStatus.WAITING)
    val checkedInAt = timestamp("checked_in_at").nullable()
    val distanceErrorM = double("distance_error_m").nullable()
    val manualByHost = bool("manual_by_host").default(false)

    override val primaryKey = PrimaryKey(sessionId, userId)
}

/** S-36 체크인 윈도우/반경 판정 -- 순수 함수라 GeoMath/RouteFeedbackTags처럼 별도 유닛 테스트가
 * 가능하다. 기본값은 SPEC(v1.3 문서 469행)에 그대로 있는 수치를 옮긴 것. */
object SocialSessionCheckinWindow {
    val OPENS_BEFORE: Duration = Duration.ofMinutes(30)
    val CLOSES_AFTER: Duration = Duration.ofMinutes(15)
    const val RADIUS_METERS = 150.0

    fun isWithinWindow(scheduledAt: Instant, now: Instant): Boolean =
        !now.isBefore(scheduledAt.minus(OPENS_BEFORE)) && !now.isAfter(scheduledAt.plus(CLOSES_AFTER))

    fun isWithinRadius(distanceMeters: Double): Boolean = distanceMeters <= RADIUS_METERS

    /** 체크인 윈도우+반경을 통과한 시도의 최종 상태 -- scheduledAt 이후 도착이면 LATE. */
    fun resolveStatus(scheduledAt: Instant, checkedInAt: Instant): SocialSessionCheckinStatus =
        if (checkedInAt.isAfter(scheduledAt)) SocialSessionCheckinStatus.LATE else SocialSessionCheckinStatus.CHECKED_IN
}
