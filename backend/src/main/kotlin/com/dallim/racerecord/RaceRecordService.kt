package com.dallim.racerecord

import com.dallim.common.BadRequestException
import com.dallim.common.ErrorCodes
import com.dallim.common.NotFoundException
import java.time.Year

/**
 * 완주 이력(러닝 커리어) 비즈니스 로직 — docs/02-api-spec.md 15장,
 * docs/달림_화면별_상세기획서_v1.3.md S-04b/S-82/S-83. RaceRecordRoutes.kt는 얇은 HTTP
 * 어댑터로 유지하고, 검증/PB계산/페이스제안은 전부 여기에 둔다.
 *
 * 다른 유저의 이력을 PATCH/DELETE하려는 시도는 404 RACE_RECORD_NOT_FOUND로 응답한다
 * (com.dallim.run.RunService.findOwnedRun과 동일한 관례 — 남의 개인 리소스 존재 여부를
 * 굳이 403으로 알려주지 않는다).
 */
class RaceRecordService(private val raceRecordRepository: RaceRecordRepository) {

    /** GET /users/me/race-records — 15.1. */
    fun list(userId: String): RaceRecordListResponse {
        val rows = raceRecordRepository.findAllByUser(userId)
        val bestByCategory = personalBestByCategory(rows)
        return RaceRecordListResponse(items = rows.map { it.toItem(bestByCategory) })
    }

    /** POST /users/me/race-records — 15.2. */
    fun create(userId: String, request: CreateRaceRecordRequest): RaceRecordItem {
        val normalized = normalize(
            raceNameRaw = request.raceName,
            category = parseCategory(request.category),
            distanceKmRaw = request.distanceKm,
            year = request.year,
            recordSecondsRaw = request.recordSeconds,
            recordType = request.recordType?.let { parseRecordType(it) },
        )

        val id = raceRecordRepository.create(
            userId = userId,
            raceName = normalized.raceName,
            category = normalized.category,
            distanceKm = normalized.distanceKm,
            year = normalized.year,
            recordSeconds = normalized.recordSeconds,
            recordType = normalized.recordType,
            bibNumber = request.bibNumber?.trim()?.ifBlank { null },
            memo = request.memo?.trim()?.ifBlank { null },
        )

        return buildItem(userId, id)
    }

    /** PATCH /users/me/race-records/{id} — 15.3. 보내지 않은 필드는 기존 값 유지. */
    fun update(userId: String, id: String, request: UpdateRaceRecordRequest): RaceRecordItem {
        val existing = findOwnedOr404(userId, id)

        val category = request.category?.let { parseCategory(it) } ?: existing.category
        // 카테고리가 바뀌는데 distanceKm을 새로 안 보냈으면 이전 카테고리(예: OTHER)의 거리를
        // 그대로 물려받지 않는다 — normalizeDistanceKm이 새 카테고리 기준으로 다시 판단하도록
        // null로 넘긴다(예: OTHER "15km 산길" -> HALF로 바꾸면 21.0975km로 재계산).
        val distanceKmRaw = request.distanceKm ?: existing.distanceKm.takeIf { category == existing.category }

        val normalized = normalize(
            raceNameRaw = request.raceName ?: existing.raceName,
            category = category,
            distanceKmRaw = distanceKmRaw,
            year = request.year ?: existing.year,
            recordSecondsRaw = if (request.recordSeconds != null) request.recordSeconds else existing.recordSeconds,
            recordType = request.recordType?.let { parseRecordType(it) } ?: existing.recordType,
        )

        raceRecordRepository.update(
            id = id,
            raceName = normalized.raceName,
            category = normalized.category,
            distanceKm = normalized.distanceKm,
            year = normalized.year,
            recordSeconds = normalized.recordSeconds,
            recordType = normalized.recordType,
            bibNumber = (request.bibNumber ?: existing.bibNumber)?.trim()?.ifBlank { null },
            memo = (request.memo ?: existing.memo)?.trim()?.ifBlank { null },
        )

        return buildItem(userId, id)
    }

    /** DELETE /users/me/race-records/{id} — 15.4. */
    fun delete(userId: String, id: String) {
        findOwnedOr404(userId, id)
        raceRecordRepository.delete(id)
    }

    /** POST /users/me/race-records/pace-suggestion — 15.5, S-04b/S-83 "예상 페이스 환산". */
    fun suggestPace(request: PaceSuggestionRequest): PaceSuggestionResponse {
        val category = parseCategory(request.category)
        if (category != RaceCategory.HALF && category != RaceCategory.FULL) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "페이스 제안은 하프 또는 풀 기록에서만 가능합니다.")
        }
        if (request.recordSeconds <= 0) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "기록(recordSeconds)은 0보다 커야 합니다.")
        }
        val bucket = PaceSuggestionCalculator.suggest(category, request.recordSeconds)
            ?: throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "페이스 제안은 하프 또는 풀 기록에서만 가능합니다.")
        return PaceSuggestionResponse(suggestedPace = bucket.apiValue)
    }

    /** 새로 만들거나 수정한 레코드를 [RaceRecordItem]으로 되돌려주기 위해 유저 전체를 다시
     * 훑는다 — isPb는 같은 카테고리의 다른 이력과 비교해야 하므로 단건만 봐서는 계산할 수 없다.
     * 이력 수가 적어 성능 문제는 없다(작업 브리핑 지시). */
    private fun buildItem(userId: String, id: String): RaceRecordItem {
        val rows = raceRecordRepository.findAllByUser(userId)
        val bestByCategory = personalBestByCategory(rows)
        val row = rows.firstOrNull { it.id == id }
            ?: throw NotFoundException(ErrorCodes.RACE_RECORD_NOT_FOUND, "완주 이력을 찾을 수 없습니다.")
        return row.toItem(bestByCategory)
    }

    private fun findOwnedOr404(userId: String, id: String): RaceRecord {
        val row = raceRecordRepository.findById(id)
            ?: throw NotFoundException(ErrorCodes.RACE_RECORD_NOT_FOUND, "완주 이력을 찾을 수 없습니다.")
        if (row.userId != userId) {
            throw NotFoundException(ErrorCodes.RACE_RECORD_NOT_FOUND, "완주 이력을 찾을 수 없습니다.")
        }
        return row
    }

    /** 같은 카테고리 내 recordSeconds가 있는 이력 중 최솟값 — PB 여부 판단 기준(15.1). */
    private fun personalBestByCategory(rows: List<RaceRecord>): Map<RaceCategory, Int> =
        rows.filter { it.recordSeconds != null }
            .groupBy { it.category }
            .mapValues { (_, records) -> records.minOf { it.recordSeconds!! } }

    private fun RaceRecord.toItem(bestByCategory: Map<RaceCategory, Int>): RaceRecordItem {
        val isPb = recordSeconds != null && bestByCategory[category] == recordSeconds
        val paceSuggestion = recordSeconds?.let { PaceSuggestionCalculator.suggest(category, it)?.apiValue }
        return RaceRecordItem(
            id = id,
            raceName = raceName,
            category = category,
            distanceKm = distanceKm,
            year = year,
            recordSeconds = recordSeconds,
            recordType = recordType,
            bibNumber = bibNumber,
            memo = memo,
            verified = verified,
            isPb = isPb,
            paceSuggestion = paceSuggestion,
            createdAt = createdAt.toString(),
        )
    }

    private data class NormalizedInput(
        val raceName: String,
        val category: RaceCategory,
        val distanceKm: Double?,
        val year: Int,
        val recordSeconds: Int?,
        val recordType: RecordType?,
    )

    private fun normalize(
        raceNameRaw: String,
        category: RaceCategory,
        distanceKmRaw: Double?,
        year: Int,
        recordSecondsRaw: Int?,
        recordType: RecordType?,
    ): NormalizedInput {
        val raceName = raceNameRaw.trim()
        if (raceName.isBlank()) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "대회명을 입력해주세요.")
        }

        val minYear = 1990
        val maxYear = Year.now().value + 1
        if (year !in minYear..maxYear) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "연도는 ${minYear}년부터 ${maxYear}년 사이여야 합니다.")
        }

        val distanceKm = normalizeDistanceKm(category, distanceKmRaw)

        if (recordSecondsRaw != null && recordSecondsRaw <= 0) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "기록(recordSeconds)은 0보다 커야 합니다.")
        }

        return NormalizedInput(
            raceName = raceName,
            category = category,
            distanceKm = distanceKm,
            year = year,
            recordSeconds = recordSecondsRaw,
            recordType = recordType,
        )
    }

    /**
     * OTHER는 거리 직접 입력 필수(양수). 5K/10K/HALF/FULL은 카테고리로부터 유추한 고정값을
     * 기본으로 쓰되, 클라이언트가 값을 보내면 그 값을 신뢰한다(공인 기록이 표기 거리와 정확히
     * 일치하지 않는 대회 대응). ULTRA/TRAIL은 대회마다 거리가 제각각이라 고정값이 없으므로
     * 입력된 값이 있을 때만 저장한다 — SPEC에 없어 임의로 판단한 부분(작업 브리핑 참고).
     */
    private fun normalizeDistanceKm(category: RaceCategory, distanceKmRaw: Double?): Double? {
        if (category == RaceCategory.OTHER) {
            if (distanceKmRaw == null || distanceKmRaw <= 0.0) {
                throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "기타 종목은 거리(distanceKm, 양수)를 입력해야 합니다.")
            }
            return distanceKmRaw
        }

        if (distanceKmRaw != null) {
            if (distanceKmRaw <= 0.0) {
                throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "거리(distanceKm)는 0보다 커야 합니다.")
            }
            return distanceKmRaw
        }

        return when (category) {
            RaceCategory.FIVE_K -> 5.0
            RaceCategory.TEN_K -> 10.0
            RaceCategory.HALF -> 21.0975
            RaceCategory.FULL -> 42.195
            RaceCategory.ULTRA, RaceCategory.TRAIL -> null
            RaceCategory.OTHER -> null // unreachable, handled above
        }
    }

    private fun parseCategory(raw: String): RaceCategory = when (raw) {
        "5K" -> RaceCategory.FIVE_K
        "10K" -> RaceCategory.TEN_K
        "HALF" -> RaceCategory.HALF
        "FULL" -> RaceCategory.FULL
        "ULTRA" -> RaceCategory.ULTRA
        "TRAIL" -> RaceCategory.TRAIL
        "OTHER" -> RaceCategory.OTHER
        else -> throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "종목(category) 값이 올바르지 않습니다.")
    }

    private fun parseRecordType(raw: String): RecordType = try {
        RecordType.valueOf(raw)
    } catch (e: IllegalArgumentException) {
        throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "기록 종류(recordType)는 NET 또는 GROSS만 허용됩니다.")
    }
}
