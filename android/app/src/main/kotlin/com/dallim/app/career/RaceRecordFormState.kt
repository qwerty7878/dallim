package com.dallim.app.career

import com.dallim.network.racerecord.CreateRaceRecordRequest
import com.dallim.network.racerecord.RaceRecordItem
import com.dallim.network.racerecord.UpdateRaceRecordRequest
import java.time.Year

/** 15.3: `year`는 1990 ~ 현재연도+1. */
private val MIN_YEAR = 1990
private val MAX_YEAR = Year.now().value + 1

/**
 * S-04b(온보딩 러닝 커리어 입력)/S-90(완주 이력 등록·편집) 공용 입력 폼 상태. 여기서 계산하는
 * 에러들은 전부 **UX 프리뷰 전용 최소 검증**이다(CLAUDE.md 공통 규칙 3과 동일한 원칙) — 최종
 * 성공/실패는 항상 서버 응답(`POST`/`PATCH /users/me/race-records`)이 결정한다. 규칙은
 * docs/02-api-spec.md 15.3을 그대로 옮겼다.
 */
data class RaceRecordFormState(
    val raceName: String = "",
    val category: RaceCategoryOption? = null,
    val otherDistanceKmInput: String = "",
    val yearInput: String = Year.now().value.toString(),
    val hoursInput: String = "",
    val minutesInput: String = "",
    val secondsInput: String = "",
    val recordType: RecordTypeOption? = null,
    val bibNumber: String = "",
    val memo: String = "",
) {
    /** OTHER는 필수(양수), 그 외는 카테고리 기준 고정값을 그대로 신뢰(15.3). */
    val distanceKmError: String?
        get() = when {
            category?.requiresDistanceInput != true -> null
            otherDistanceKmInput.isEmpty() -> null
            otherDistanceKmInput.toDoubleOrNull()?.let { it > 0 } != true -> "거리는 0보다 커야 해요."
            else -> null
        }

    private val yearOrNull: Int? get() = yearInput.toIntOrNull()

    val yearError: String?
        get() = when {
            yearInput.isEmpty() -> null
            yearOrNull == null -> "연도를 숫자로 입력해주세요."
            yearOrNull !in MIN_YEAR..MAX_YEAR -> "${MIN_YEAR}~${MAX_YEAR}년 사이로 입력해주세요."
            else -> null
        }

    private val hasAnyRecordInput: Boolean
        get() = hoursInput.isNotEmpty() || minutesInput.isNotEmpty() || secondsInput.isNotEmpty()

    /** 시:분:초 중 하나라도 입력되면 나머지는 0으로 취급해 초 단위로 환산한다. 전부 비어있으면 null(선택 입력). */
    val recordSecondsOrNull: Int?
        get() {
            if (!hasAnyRecordInput) return null
            val h = hoursInput.toIntOrNull() ?: 0
            val m = minutesInput.toIntOrNull() ?: 0
            val s = secondsInput.toIntOrNull() ?: 0
            val total = h * 3600 + m * 60 + s
            return if (total > 0) total else null
        }

    val recordTimeError: String?
        get() = when {
            !hasAnyRecordInput -> null
            (minutesInput.toIntOrNull() ?: 0) !in 0..59 || (secondsInput.toIntOrNull() ?: 0) !in 0..59 ->
                "분·초는 0~59 사이로 입력해주세요."
            recordSecondsOrNull == null -> "기록을 정확히 입력해주세요."
            else -> null
        }

    /** OTHER면 사용자 입력값, 그 외는 종목 고정 거리(ULTRA/TRAIL은 고정값이 없어 null). */
    private val effectiveDistanceKm: Double?
        get() = if (category?.requiresDistanceInput == true) otherDistanceKmInput.toDoubleOrNull() else category?.fixedDistanceKm

    val isValid: Boolean
        get() = raceName.isNotBlank() &&
            category != null &&
            distanceKmError == null &&
            (category.requiresDistanceInput != true || effectiveDistanceKm != null) &&
            yearOrNull != null && yearError == null &&
            recordTimeError == null

    fun toCreateRequest(): CreateRaceRecordRequest {
        check(isValid) { "폼이 유효하지 않은 상태에서 요청을 만들 수 없습니다." }
        return CreateRaceRecordRequest(
            raceName = raceName.trim(),
            category = category!!.apiValue,
            distanceKm = effectiveDistanceKm,
            year = yearOrNull!!,
            recordSeconds = recordSecondsOrNull,
            recordType = recordType?.apiValue,
            bibNumber = bibNumber.trim().ifEmpty { null },
            memo = memo.trim().ifEmpty { null },
        )
    }

    fun toUpdateRequest(): UpdateRaceRecordRequest {
        check(isValid) { "폼이 유효하지 않은 상태에서 요청을 만들 수 없습니다." }
        return UpdateRaceRecordRequest(
            raceName = raceName.trim(),
            category = category!!.apiValue,
            distanceKm = effectiveDistanceKm,
            year = yearOrNull!!,
            recordSeconds = recordSecondsOrNull,
            recordType = recordType?.apiValue,
            bibNumber = bibNumber.trim().ifEmpty { null },
            memo = memo.trim().ifEmpty { null },
        )
    }

    companion object {
        /** S-90 수정 모드 진입 시 기존 [RaceRecordItem]으로 폼을 프리필한다. */
        fun fromItem(item: RaceRecordItem): RaceRecordFormState {
            val category = RaceCategoryOption.entries.firstOrNull { it.apiValue == item.category }
            val totalSeconds = item.recordSeconds
            return RaceRecordFormState(
                raceName = item.raceName,
                category = category,
                otherDistanceKmInput = if (category?.requiresDistanceInput == true) {
                    item.distanceKm?.let { formatDistanceInput(it) } ?: ""
                } else {
                    ""
                },
                yearInput = item.year.toString(),
                hoursInput = totalSeconds?.let { (it / 3600).toString() } ?: "",
                minutesInput = totalSeconds?.let { ((it % 3600) / 60).toString() } ?: "",
                secondsInput = totalSeconds?.let { (it % 60).toString() } ?: "",
                recordType = RecordTypeOption.entries.firstOrNull { it.apiValue == item.recordType },
                bibNumber = item.bibNumber ?: "",
                memo = item.memo ?: "",
            )
        }

        private fun formatDistanceInput(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}
