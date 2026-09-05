package com.dallim.app.meetup

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * S-47/48/49 공통 일시 변환 — 서버는 `scheduledAt`을 UTC ISO-8601 `Instant` 문자열로 주고받는다
 * (docs/02-api-spec.md 14장, 예: `"2026-09-13T22:00:00Z"`). 화면에는 기기 로컬 타임존(한국은
 * 항상 KST) 기준으로 표시하고, 서버로 보낼 때는 다시 Instant 문자열로 변환한다.
 */
object MeetupFormat {
    private val displayFormatter = DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREAN)

    /** `"2026-09-13T22:00:00Z"` -> `"9월 14일 (월) 오전 7:00"`(KST 기준 예시). */
    fun displayDateTime(isoInstant: String): String = runCatching {
        Instant.parse(isoInstant).atZone(ZoneId.systemDefault()).format(displayFormatter)
    }.getOrDefault(isoInstant)

    /** 로컬 타임존 기준 (년/월/일/시/분) -> 서버 전송용 UTC Instant 문자열. */
    fun toIsoInstant(year: Int, month: Int, day: Int, hour: Int, minute: Int): String =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toString()
}
