package com.dallim.race

import com.dallim.common.ErrorCodes
import com.dallim.common.GeoMath.toPercentInt
import com.dallim.common.NotFoundException
import com.dallim.route.RouteRepository
import com.dallim.run.RunRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 대회 캘린더 비즈니스 로직 — docs/02-api-spec.md 16장. RaceRoutes.kt는 얇은 HTTP 어댑터로
 * 유지하고, 접수 상태 계산/필터링/정렬은 전부 여기에 둔다.
 *
 * 접수 상태([RaceStatus])는 저장하지 않고 [computeStatus]로 매 조회 시 계산한다 — 배치/
 * 스케줄러가 필요 없다(작업 브리핑 지시).
 *
 * S-85(대회 코스 미리 달리기, 2026-09-12, docs/02-api-spec.md 16.6) 이후로는
 * `com.dallim.route.RouteRepository`/`com.dallim.run.RunRepository`에도 의존한다 — 공식 코스
 * 요약(geoJson/distanceKm/elevationGainM)은 route 도메인 데이터고, 구간 완주 여부는 run
 * 도메인 데이터라서 그렇다(완주 판정 알고리즘 자체는 손대지 않고 RunRepository.
 * findCompletedRouteIds를 그대로 재사용).
 */
class RaceService(
    private val raceRepository: RaceRepository,
    private val routeRepository: RouteRepository,
    private val runRepository: RunRepository,
) {

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
            // 2026-10-05 정렬 재정의. 예전에는 (마감여부, registrationEnd 오름차순) 두 키뿐이라 두 가지가
            // 이상했다: ① 마감된 대회끼리 "가장 오래전에 마감된 것"이 먼저 와 목록이 과거로 거슬러 올라갔고,
            // ② 정렬 기준(접수 마감일)이 화면에 보이는 값(D-day = 대회일)과 달라 순서가 무작위로 보였다.
            //
            // 지금은 "지금 신청할 수 있는 것 먼저" + "화면에 보이는 D-day와 같은 방향"으로 맞춘다:
            //   1. 접수중(OPEN) → 2. 접수 예정(UPCOMING) → 3. 접수 마감(CLOSED)
            //   그 안에서 아직 안 열린 대회가 먼저(대회일 임박순), 이미 열린 대회는 맨 뒤(최근 대회부터).
            .sortedWith(
                compareBy<EnrichedRace> {
                    when (it.status) {
                        RaceStatus.OPEN -> 0
                        RaceStatus.UPCOMING -> 1
                        RaceStatus.CLOSED -> 2
                    }
                }
                    .thenBy { it.dDay < 0 } // 이미 열린 대회는 각 그룹 뒤로
                    .thenBy { if (it.dDay < 0) -it.race.raceDate.epochSecond else it.race.raceDate.epochSecond },
            )

        val totalCount = enriched.size
        val pageItems = enriched.drop(safePage * safeSize).take(safeSize)

        val savedCounts = raceRepository.countSavedByRaces(pageItems.map { it.race.id })
        val savedRaceIds = if (userId != null) raceRepository.findSavedRaceIds(userId, pageItems.map { it.race.id }) else emptySet()
        val previewProgressPercents = computePreviewProgressPercents(pageItems.map { it.race }, userId)

        return RaceListResponse(
            items = pageItems.map {
                it.toSummary(
                    savedCount = savedCounts[it.race.id] ?: 0,
                    isSaved = it.race.id in savedRaceIds,
                    previewProgressPercent = previewProgressPercents[it.race.id],
                )
            },
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
            previewProgressPercent = computePreviewProgressPercents(listOf(race), userId)[race.id],
        )
    }

    /**
     * GET /races/{raceId}/course — S-85. 이 대회에 공식 코스가 없으면 `hasCourse=false`뿐인
     * 응답을 돌려준다(에러가 아니다 — "코스 없음"은 정상 상태). 구간 완주 여부/진행률은
     * [computePreviewProgressPercents]와 같은 계산이지만, 여기선 구간별 `isCompleted`까지
     * 함께 내려줘야 해서 별도로 계산한다.
     */
    fun getCourse(raceId: String, userId: String?): RaceCourseResponse {
        val race = raceRepository.findById(raceId)
            ?: throw NotFoundException(ErrorCodes.RACE_NOT_FOUND, "대회를 찾을 수 없습니다.")
        val courseRouteId = race.courseRouteId ?: return RaceCourseResponse(hasCourse = false)

        // FK가 가리키는 route가 사라졌을 리 없지만(참조 무결성), 방어적으로 코스 없음과 동일하게 처리.
        val courseRoute = routeRepository.findDetail(courseRouteId) ?: return RaceCourseResponse(hasCourse = false)

        val segments = raceRepository.findSegmentsForRace(raceId)
        val completedRouteIds = if (userId != null) {
            runRepository.findCompletedRouteIds(userId, segments.map { it.routeId })
        } else {
            emptySet()
        }

        val completedDistanceKm = segments.filter { it.routeId in completedRouteIds }.sumOf { it.distanceKm }
        val progressPercent = if (courseRoute.distanceKm > 0.0) {
            (completedDistanceKm / courseRoute.distanceKm * 100.0).toPercentInt()
        } else {
            0
        }

        return RaceCourseResponse(
            hasCourse = true,
            geoJson = courseRoute.geoJson,
            distanceKm = courseRoute.distanceKm,
            elevationGainM = courseRoute.elevationGainM,
            segments = segments.map {
                RaceCourseSegmentItem(
                    segmentId = it.segmentId,
                    label = it.label,
                    routeId = it.routeId,
                    distanceKm = it.distanceKm,
                    estimatedMinutes = it.estimatedMinutes,
                    elevationGainM = it.elevationGainM,
                    orderIndex = it.orderIndex,
                    isCompleted = it.routeId in completedRouteIds,
                )
            },
            previewProgressPercent = progressPercent,
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
        val previewProgressPercents = computePreviewProgressPercents(races, userId)
        val now = Instant.now()

        val items = races.map { race ->
            race.toEnriched(categoriesByRace[race.id] ?: emptyList(), now)
                .toSummary(
                    savedCount = savedCounts[race.id] ?: 0,
                    isSaved = true,
                    previewProgressPercent = previewProgressPercents[race.id],
                )
        }
        return MyRacesResponse(items = items)
    }

    // --- 내부 계산 로직 ---

    /**
     * `previewProgressPercent`(S-85) — [races] 중 `courseRouteId`가 있는 대회만 계산하고, 없는
     * 대회는 `null`을 매핑한다. 완주한 구간들의 `distanceKm` 합 / 전체 코스 `distanceKm` * 100을
     * 정수로 반올림한다. [userId]가 null(비로그인)이면 항상 0(구간을 하나도 완주하지 않은
     * 것과 동일하게 계산되므로 별도 분기가 필요 없다).
     *
     * findCategoriesByRaces/countSavedByRaces와 동일한 배치 조회 관례 — 대회마다 쿼리하지
     * 않고 [races] 전체에 대해 한 번씩만 조회한다.
     */
    private fun computePreviewProgressPercents(races: List<Race>, userId: String?): Map<String, Int?> {
        val racesWithCourse = races.filter { it.courseRouteId != null }
        if (racesWithCourse.isEmpty()) return races.associate { it.id to null }

        val courseRouteIds = racesWithCourse.mapNotNull { it.courseRouteId }
        val courseDistanceKmByRouteId = routeRepository.findDistanceKmByIds(courseRouteIds)

        val segmentsByRace = raceRepository.findSegmentsByRaces(racesWithCourse.map { it.id })
        val allSegmentRouteIds = segmentsByRace.values.flatten().map { it.routeId }
        val completedRouteIds = if (userId != null) {
            runRepository.findCompletedRouteIds(userId, allSegmentRouteIds)
        } else {
            emptySet()
        }

        return races.associate { race ->
            val courseRouteId = race.courseRouteId
            val totalDistanceKm = courseRouteId?.let { courseDistanceKmByRouteId[it] }
            if (courseRouteId == null || totalDistanceKm == null || totalDistanceKm <= 0.0) {
                race.id to (if (courseRouteId == null) null else 0)
            } else {
                val segments = segmentsByRace[race.id] ?: emptyList()
                val completedDistanceKm = segments.filter { it.routeId in completedRouteIds }.sumOf { it.distanceKm }
                race.id to (completedDistanceKm / totalDistanceKm * 100.0).toPercentInt()
            }
        }
    }

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

    private fun EnrichedRace.toSummary(savedCount: Int, isSaved: Boolean, previewProgressPercent: Int?) = RaceSummaryResponse(
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
        previewProgressPercent = previewProgressPercent,
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
