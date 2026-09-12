package com.dallim.dallimbook

import com.dallim.run.RunStatus

/**
 * GET /users/me/runs business logic — docs/02-api-spec.md 6장 (S-40 달림북 그리드).
 *
 * The `status` query param is part of the API contract, but this screen only ever shows
 * COMPLETED runs (see DallimbookRepository's class doc for why). Requesting a different, valid
 * RunStatus therefore yields an empty page rather than a 400 — it's a legal value, just one this
 * list can never contain. An unparseable value is treated the same as "no filter" so a stray
 * client string can't fail the request; either way COMPLETED runs are still returned.
 */
class DallimbookService(private val dallimbookRepository: DallimbookRepository) {

    fun listMyRuns(userId: String, statusRaw: String?, page: Int, size: Int): DallimbookResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val statusFilter = statusRaw?.let { raw -> runCatching { RunStatus.valueOf(raw.uppercase()) }.getOrNull() }

        val (rows, totalCount) = dallimbookRepository.findCompletedRuns(userId, statusFilter, safePage, safeSize)
        val totalDistanceKm = dallimbookRepository.sumCompletedDistanceKm(userId)
        val paceStats = dallimbookRepository.paceStats(userId)

        val items = rows.map {
            DallimbookRunItem(
                runId = it.runId,
                routeName = it.routeName,
                distanceKm = it.distanceKm,
                completedAt = it.completedAt.toString(),
                thumbnailGeoJson = it.thumbnailGeoJson,
            )
        }

        return DallimbookResponse(
            items = items,
            totalCount = totalCount,
            totalDistanceKm = totalDistanceKm,
            bestPaceSecPerKm = paceStats.bestPaceSecPerKm,
            averagePaceSecPerKm = paceStats.averagePaceSecPerKm,
        )
    }
}
