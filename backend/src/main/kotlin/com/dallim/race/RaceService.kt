package com.dallim.race

import com.dallim.common.ErrorCodes
import com.dallim.common.NotFoundException
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 대회 캘린더 비즈니스 로직 — docs/02-api-spec.md 16장. RaceRoutes.kt는 얇은 HTTP 어댑터로
 * 유지하고, 접수 상태 계산/필터링/정렬은 전부 여기에 둔다.
 *
 * 접수 상태([RaceStatus])는 저장하지 않고 [computeStatus]로 매 조회 시 계산한다 — 배치/
 * 스케줄러가 필요 없다(작업 브리핑 지시).
 */
class RaceService(private val raceRepository: RaceRepository) {

    /** GET /races — 16.1. region(문자열 매칭)/category/status 필터 + 접수 마감 임박순 정렬. */
    fun listRaces(
        region: String?,
        category: String?,
        status: String?,
        page: Int,
        size: Int,
        userId: String?,
    ): RaceListResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val categoryFilter = category?.let { parseCategoryOrNull(it) }
        val statusFilter = status?.let { parseStatusOrNull(it) }

        val races = raceRepository.findAll(region)
        val categoriesByRace = raceRepository.findCategoriesByRaces(races.map { it.id })
        val now = Instant.now()

        val enriched = races
            .map { race -> race.toEnriched(categoriesByRace[race.id] ?: emptyList(), now) }
            .filter { categoryFilter == null || categoryFilter in it.categoryOptions.map { c -> c.category } }
            .filter { statusFilter == null || it.status == statusFilter }
            // 접수 마감 임박순: 아직 안 마감된 것 먼저(registrationEnd 오름차순), 이미 마감된
            // 건 뒤로(그 안에서도 registrationEnd 오름차순 — 작업 브리핑 지시).
            .sortedWith(compareBy({ it.status == RaceStatus.CLOSED }, { it.race.registrationEnd }))

        val totalCount = enriched.size
        val pageItems = enriched.drop(safePage * safeSize).take(safeSize)

        val savedCounts = raceRepository.countSavedByRaces(pageItems.map { it.race.id })
        val savedRaceIds = if (userId != null) raceRepository.findSavedRaceIds(userId, pageItems.map { it.race.id }) else emptySet()

        return RaceListResponse(
            items = pageItems.map { it.toSummary(savedCount = savedCounts[it.race.id] ?: 0, isSaved = it.race.id in savedRaceIds) },
            totalCount = totalCount,
            page = safePage,
            size = safeSize,
        )
    }

    /** GET /races/{raceId} — 16.2. `isSaved`는 com.dallim.route.RouteService.getDetail과 같은
     * 옵셔널 인증 패턴(비로그인이면 false). */
    fun getDetail(raceId: String, userId: String?): RaceDetailResponse {
        val race = raceRepository.findById(raceId)
            ?: throw NotFoundException(ErrorCodes.RACE_NOT_FOUND, "대회를 찾을 수 없습니다.")
        val categories = raceRepository.findCategoriesByRace(raceId)
        val enriched = race.toEnriched(categories, Instant.now())
        val isSaved = userId != null && raceRepository.isSaved(userId, raceId)

        return RaceDetailResponse(
            raceId = race.id,
            name = race.name,
            region = race.region,
            location = race.location,
            raceDate = race.raceDate.toString(),
            dDay = enriched.dDay,
            registrationStart = race.registrationStart.toString(),
            registrationEnd = race.registrationEnd.toString(),
            status = enriched.status,
            organizer = race.organizer,
            souvenir = race.souvenir,
            categories = categories.map {
                RaceCategoryItem(
                    category = it.category,
                    distanceKm = it.distanceKm,
                    feeKrw = it.feeKrw,
                    capacity = it.capacity,
                    cutoffMinutes = it.cutoffMinutes,
                )
            },
            minFeeKrw = enriched.minFeeKrw,
            maxFeeKrw = enriched.maxFeeKrw,
            savedCount = raceRepository.countSaved(raceId),
            isSaved = isSaved,
        )
    }

    /** POST /races/{raceId}/save — 16.3. com.dallim.user.SavedRouteService.save와 동일한 패턴. */
    fun save(userId: String, raceId: String) {
        if (!raceRepository.exists(raceId)) {
            throw NotFoundException(ErrorCodes.RACE_NOT_FOUND, "대회를 찾을 수 없습니다.")
        }
        raceRepository.save(userId, raceId)
    }

    /** DELETE /races/{raceId}/save — 16.3. 담지 않은 상태에서 호출해도 idempotent 200. */
    fun unsave(userId: String, raceId: String) {
        raceRepository.unsave(userId, raceId)
    }

    /** GET /users/me/races — 16.4. 대회 날짜(raceDate) 임박순(RaceRepository.findSavedRacesForUser에서 정렬). */
    fun listMyRaces(userId: String): MyRacesResponse {
        val races = raceRepository.findSavedRacesForUser(userId)
        val categoriesByRace = raceRepository.findCategoriesByRaces(races.map { it.id })
        val savedCounts = raceRepository.countSavedByRaces(races.map { it.id })
        val now = Instant.now()

        val items = races.map { race ->
            race.toEnriched(categoriesByRace[race.id] ?: emptyList(), now)
                .toSummary(savedCount = savedCounts[race.id] ?: 0, isSaved = true)
        }
        return MyRacesResponse(items = items)
    }

    // --- 내부 계산 로직 ---

    /** 저장하지 않고 매 조회 시 계산 — UPCOMING(접수 시작 전)/OPEN(접수중)/CLOSED(마감). */
    private fun computeStatus(race: Race, now: Instant): RaceStatus = when {
        now.isBefore(race.registrationStart) -> RaceStatus.UPCOMING
        now.isAfter(race.registrationEnd) -> RaceStatus.CLOSED
        else -> RaceStatus.OPEN
    }

    private data class EnrichedRace(
        val race: Race,
        val categoryOptions: List<RaceCategoryOption>,
        val status: RaceStatus,
        val dDay: Int,
        val minFeeKrw: Int?,
        val maxFeeKrw: Int?,
    )

    private fun Race.toEnriched(categoryOptions: List<RaceCategoryOption>, now: Instant): EnrichedRace {
        val fees = categoryOptions.mapNotNull { it.feeKrw }
        return EnrichedRace(
            race = this,
            categoryOptions = categoryOptions,
            status = computeStatus(this, now),
            dDay = ChronoUnit.DAYS.between(now, raceDate).toInt(),
            minFeeKrw = fees.minOrNull(),
            maxFeeKrw = fees.maxOrNull(),
        )
    }

    private fun EnrichedRace.toSummary(savedCount: Int, isSaved: Boolean) = RaceSummaryResponse(
        raceId = race.id,
        name = race.name,
        region = race.region,
        location = race.location,
        raceDate = race.raceDate.toString(),
        dDay = dDay,
        registrationStart = race.registrationStart.toString(),
        registrationEnd = race.registrationEnd.toString(),
        status = status,
        categories = categoryOptions.map { it.category },
        minFeeKrw = minFeeKrw,
        maxFeeKrw = maxFeeKrw,
        savedCount = savedCount,
        isSaved = isSaved,
    )

    private fun parseCategoryOrNull(raw: String): RaceCategory? = when (raw) {
        "5K" -> RaceCategory.FIVE_K
        "10K" -> RaceCategory.TEN_K
        "HALF" -> RaceCategory.HALF
        "FULL" -> RaceCategory.FULL
        "ULTRA" -> RaceCategory.ULTRA
        "TRAIL" -> RaceCategory.TRAIL
        else -> null // 잘못된 값은 필터 없음으로 취급(GET /routes의 status 쿼리 파라미터 관례와 동일)
    }

    private fun parseStatusOrNull(raw: String): RaceStatus? = runCatching { RaceStatus.valueOf(raw) }.getOrNull()
}
