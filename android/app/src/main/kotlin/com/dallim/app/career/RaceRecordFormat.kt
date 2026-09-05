package com.dallim.app.career

import com.dallim.app.onboarding.profile.ComfortablePace
import com.dallim.app.running.RunFormat

/** S-04b/S-90/S-91 공용 표시 포맷터. */
object RaceRecordFormat {
    /** `recordSeconds`(초 단위)를 `hh:mm:ss`(또는 `mm:ss`)로. [RunFormat.duration]을 그대로 재사용한다. */
    fun recordTime(recordSeconds: Int?): String? = recordSeconds?.let { RunFormat.duration(it.toLong()) }

    /** 종목 라벨 — 알 수 없는 값이 오면 원본 문자열을 그대로 보여준다(방어적 폴백). */
    fun categoryLabel(apiValue: String): String =
        RaceCategoryOption.entries.firstOrNull { it.apiValue == apiValue }?.label ?: apiValue

    /** `paceSuggestion`/`comfortablePace` 값(`PACE_6_7` 등)을 S-04 한글 라벨로. */
    fun paceLabel(apiValue: String): String =
        ComfortablePace.entries.firstOrNull { it.apiValue == apiValue }?.label ?: apiValue
}
