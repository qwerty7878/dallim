package com.dallim.app.social

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * S-30~S-34 공통 일시 변환 — 서버는 `scheduledAt`을 UTC ISO-8601 `Instant` 문자열로 주고받는다
 * (docs/02-api-spec.md 17장). [com.dallim.app.meetup.MeetupFormat]과 동일한 관례이지만 이
 * 도메인은 meetup과 의도적으로 완전히 분리돼 있어(SocialSession.kt 문서 참고) 별도 파일로 둔다.
 */
object SocialSessionFormat {
    private val displayFormatter = DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREAN)

    /** `"2026-09-13T22:00:00Z"` -> `"9월 14일 (월) 오전 7:00"`(기기 로컬 타임존 기준). */
    fun displayDateTime(isoInstant: String): String = runCatching {
        Instant.parse(isoInstant).atZone(ZoneId.systemDefault()).format(displayFormatter)
    }.getOrDefault(isoInstant)

    /** 로컬 타임존 기준 (년/월/일/시/분) -> 서버 전송용 UTC Instant 문자열. */
    fun toIsoInstant(year: Int, month: Int, day: Int, hour: Int, minute: Int): String =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toString()

    /** S-34 신청일 표시 — 상세 일시와 달리 요일 없이 짧게. */
    private val appliedAtFormatter = DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN)
    fun displayAppliedAt(isoInstant: String): String = runCatching {
        Instant.parse(isoInstant).atZone(ZoneId.systemDefault()).format(appliedAtFormatter)
    }.getOrDefault(isoInstant)

    /** 당근마켓 매너온도 컨셉의 신뢰도 점수 표시 — docs/02-api-spec.md 17.1 `users.running_temperature`. */
    fun displayTemperature(temperature: Double): String = "%.1f℃".format(temperature)

    /** [com.dallim.network.social.SocialSessionGenderCondition] 값 -> 한국어 라벨(S-31 선택 칩/S-32 표시 공용). */
    fun genderConditionLabel(value: String): String = when (value) {
        "SAME_AS_HOST" -> "호스트와 동성만"
        "FEMALE_ONLY" -> "여성만"
        "MALE_ONLY" -> "남성만"
        else -> "누구나"
    }

    /** [com.dallim.network.social.SocialSessionRainPolicy] 값 -> 한국어 라벨. */
    fun rainPolicyLabel(value: String): String = when (value) {
        "PROCEED" -> "무조건 진행"
        "CANCEL" -> "무조건 취소"
        else -> "당일 판단"
    }
}
