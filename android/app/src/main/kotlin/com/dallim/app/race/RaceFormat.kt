package com.dallim.app.race

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * S-80/S-81 공통 표시 변환 — 서버는 날짜를 UTC ISO-8601 `Instant` 문자열로 준다
 * (docs/02-api-spec.md 16장, 예: `"2026-11-01T08:00:00Z"`). `com.dallim.app.meetup.MeetupFormat`과
 * 동일한 패턴(기기 로컬 타임존 기준 표시, 실패 시 원문 그대로 반환).
 */
object RaceFormat {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.M.d (E)", Locale.KOREAN)

    /** `"2026-11-01T08:00:00Z"` -> `"2026.11.1 (일)"`(KST 기준 예시). */
    fun displayDate(isoInstant: String): String = runCatching {
        Instant.parse(isoInstant).atZone(ZoneId.systemDefault()).format(dateFormatter)
    }.getOrDefault(isoInstant)

    /** `dDay`가 지난 대회면 음수도 올 수 있으나(16.2), 큐레이션 데이터는 항상 미래 일자다. */
    fun dDayLabel(dDay: Int): String = when {
        dDay > 0 -> "D-$dDay"
        dDay == 0 -> "D-DAY"
        else -> "D+${-dDay}"
    }

    /** 참가비 범위 — 종목이 하나도 참가비를 갖지 않으면 둘 다 null(16.1). */
    fun feeRange(minFeeKrw: Int?, maxFeeKrw: Int?): String = when {
        minFeeKrw == null || maxFeeKrw == null -> "참가비 정보 없음"
        minFeeKrw == maxFeeKrw -> "${format(minFeeKrw)}원"
        else -> "${format(minFeeKrw)}~${format(maxFeeKrw)}원"
    }

    fun statusLabel(status: String): String = when (status) {
        "UPCOMING" -> "접수 예정"
        "OPEN" -> "접수중"
        "CLOSED" -> "접수 마감"
        else -> status
    }

    /** 컷오프(분) -> "4시간" / "4시간 30분". ULTRA/TRAIL처럼 컷오프가 없으면 null 그대로 처리한다. */
    fun cutoffLabel(cutoffMinutes: Int?): String {
        if (cutoffMinutes == null) return "-"
        val hours = cutoffMinutes / 60
        val minutes = cutoffMinutes % 60
        return if (minutes == 0) "${hours}시간" else "${hours}시간 ${minutes}분"
    }

    /** 종목마다 거리가 제각각인(ULTRA/TRAIL) 경우 null이 올 수 있다(16.1). */
    fun distanceLabel(distanceKm: Double?): String = if (distanceKm == null) "-" else "${formatDistance(distanceKm)}km"

    fun feeLabel(feeKrw: Int?): String = if (feeKrw == null) "-" else "${format(feeKrw)}원"

    fun capacityLabel(capacity: Int?): String = if (capacity == null) "-" else "${capacity}명"

    private fun formatDistance(km: Double): String =
        if (km == km.toLong().toDouble()) km.toLong().toString() else km.toString()

    /** 5K/10K/HALF/FULL/ULTRA/TRAIL — 와이어 표현을 그대로 배지 라벨로 쓴다(16.1). */
    fun categoryLabel(category: String): String = when (category) {
        "5K" -> "5K"
        "10K" -> "10K"
        "HALF" -> "하프"
        "FULL" -> "풀"
        "ULTRA" -> "울트라"
        "TRAIL" -> "트레일"
        else -> category
    }

    /** S-85 코스/구간 고도 표시 — "+52m". */
    fun elevationLabel(elevationGainM: Int): String = "+${elevationGainM}m"

    /** S-85 구간 예상 소요 시간 — "약 18분". */
    fun estimatedMinutesLabel(estimatedMinutes: Int): String = "약 ${estimatedMinutes}분"

    /** S-85 코스 답사 진행률 — "62% 답사 완료". */
    fun previewProgressLabel(previewProgressPercent: Int): String = "${previewProgressPercent}% 답사 완료"

    private fun format(krw: Int): String = "%,d".format(krw)
}
