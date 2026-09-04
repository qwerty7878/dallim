package com.dallim.run

import com.dallim.common.BadRequestException
import com.dallim.common.ConflictException
import com.dallim.common.ErrorCodes
import com.dallim.common.GeoJsonLineString
import com.dallim.common.NotFoundException
import com.dallim.notification.NotificationService
import java.time.Instant

/**
 * Run domain business logic — docs/02-api-spec.md 5장, docs/01-feature-spec.md 2.2.D.
 * RunRoutes.kt stays a thin HTTP adapter; every status-transition/validation decision lives here.
 *
 * `clientPrecheckStatus` (FinishRunRequest) is UX-preview data only and is never read here —
 * [RunJudgementService] is the sole source of truth for a run's final status (CLAUDE.md rule 3).
 */
class RunService(
    private val runRepository: RunRepository,
    private val judgementService: RunJudgementService,
    private val finisherCountSync: FinisherCountSync,
    private val notificationService: NotificationService,
) {
    private val finishedStatuses = setOf(RunStatus.COMPLETED, RunStatus.PARTIAL, RunStatus.ABORTED, RunStatus.UNDER_REVIEW)

    /** POST /runs. */
    fun startRun(userId: String, request: StartRunRequest): RunStartResponse {
        // Also doubles as an existence check for routeId — there's no dedicated error code for
        // this in docs/02-api-spec.md 5장, so ROUTE_NOT_FOUND (already defined for the route
        // domain) is reused rather than letting an unknown routeId fall through to a raw FK
        // violation / 500.
        runRepository.findPlannedPath(request.routeId)
            ?: throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")

        val startedAt = parseInstant(request.startedAt)
        val runId = runRepository.create(
            userId = userId,
            routeId = request.routeId,
            mode = request.mode,
            startedAt = startedAt,
        )
        return RunStartResponse(runId = runId, status = RunStatus.IN_PROGRESS)
    }

    /** PATCH /runs/{runId}/status — RUNNING/PAUSED only (docs/02-api-spec.md 5장). */
    fun updateStatus(userId: String, runId: String, statusRaw: String): RunStatusResponse {
        val run = findOwnedRun(userId, runId)
        if (run.status in finishedStatuses) {
            throw ConflictException(ErrorCodes.RUN_ALREADY_FINISHED, "이미 종료된 러닝입니다.")
        }

        val newStatus = when (statusRaw) {
            "RUNNING" -> RunStatus.RUNNING
            "PAUSED" -> RunStatus.PAUSED
            else -> throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "status는 RUNNING 또는 PAUSED만 허용됩니다.")
        }

        runRepository.updateStatus(runId, newStatus)
        return RunStatusResponse(runId = runId, status = newStatus)
    }

    /**
     * POST /runs/{runId}/gps-batch — accumulates points; may be called multiple times for the
     * same run (CLAUDE.md rule 4). `receivedCount` in the response is the run's cumulative total.
     */
    fun uploadGpsBatch(userId: String, runId: String, request: GpsBatchRequest): GpsBatchResponse {
        val run = findOwnedRun(userId, runId)
        if (run.status in finishedStatuses) {
            throw ConflictException(ErrorCodes.RUN_ALREADY_FINISHED, "이미 종료된 러닝입니다.")
        }

        request.points.forEach { parseInstant(it.timestamp) } // fail fast with a proper 400 before any writes

        val total = runRepository.appendGpsPoints(runId, request.points)
        return GpsBatchResponse(receivedCount = total)
    }

    /**
     * POST /runs/{runId}/finish — runs [RunJudgementService.judge] against every GPS point
     * uploaded so far for this run and persists the result. This is the only place a run's final
     * status is decided; the client's `clientPrecheckStatus` is accepted but never consulted.
     */
    fun finishRun(userId: String, runId: String, request: FinishRunRequest): RunFinishResponse {
        val run = findOwnedRun(userId, runId)
        if (run.status in finishedStatuses) {
            throw ConflictException(ErrorCodes.RUN_ALREADY_FINISHED, "이미 종료된 러닝입니다.")
        }

        val rawPoints = runRepository.fetchGpsPoints(runId)
        if (rawPoints.size < RunJudgementService.MIN_POINTS_FOR_JUDGEMENT) {
            throw BadRequestException(ErrorCodes.GPS_DATA_INSUFFICIENT, "완주 판정을 위한 GPS 데이터가 부족합니다.")
        }

        // routeId is a foreign key on run_records, so the planned path is guaranteed to exist.
        val plannedPath = runRepository.findPlannedPath(run.routeId)
            ?: throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")

        val finishedAt = parseInstant(request.finishedAt)
        val result = judgementService.judge(plannedPath, rawPoints)
        val averagePace = judgementService.averagePaceSecPerKm(result.distanceMeters, result.durationSeconds)

        val isFirstDiscoverer = result.status == RunStatus.COMPLETED &&
            !runRepository.hasPriorCompletedRun(run.routeId, runId)

        // No ink/badge reward formula exists anywhere in docs/01-feature-spec.md or
        // docs/02-api-spec.md beyond this one response example — MVP1's reward economy isn't
        // specified (monetization/rewards are explicitly out of MVP1 scope per CLAUDE.md), so
        // these are held at 0/empty rather than inventing a formula.
        val earnedInk = 0
        val earnedBadges = emptyList<String>()

        runRepository.saveJudgement(
            runId = runId,
            finishedAt = finishedAt,
            result = result,
            averagePaceSecPerKm = averagePace,
            isFirstDiscoverer = isFirstDiscoverer,
            earnedInk = earnedInk,
        )

        if (result.status == RunStatus.COMPLETED) {
            finisherCountSync.recordFinisher(run.routeId)
            // docs/02-api-spec.md 9.4 — RUN_COMPLETED in-app notification, fired at the same point
            // the finisher count is recorded (COMPLETED is the only trigger in this phase).
            val routeName = runRepository.findRouteName(run.routeId) ?: ""
            notificationService.notifyRunCompleted(
                userId = userId,
                runId = runId,
                routeName = routeName,
                distanceKm = result.distanceMeters / 1000.0,
            )
        }

        return RunFinishResponse(
            runId = runId,
            status = result.status,
            distanceKm = result.distanceMeters / 1000.0,
            durationSeconds = result.durationSeconds,
            averagePaceSecPerKm = averagePace,
            sketchMatchPercent = result.sketchMatchPercent,
            routeCompletionPercent = result.routeCompletionPercent,
            isFirstDiscoverer = isFirstDiscoverer,
            earnedInk = earnedInk,
            earnedBadges = earnedBadges,
        )
    }

    /** GET /runs/{runId} — result re-entry/share. */
    fun getRunDetail(userId: String, runId: String): RunDetailResponse {
        val run = findOwnedRun(userId, runId)
        val routeName = runRepository.findRouteName(run.routeId) ?: ""
        val plannedGeoJson = runRepository.findPlannedGeoJson(run.routeId) ?: GeoJsonLineString(coordinates = emptyList())
        // Only populated once finish-judgement has run; empty (not an error) for an in-progress run.
        val actualGeoJson = runRepository.findActualGeoJson(runId) ?: GeoJsonLineString(coordinates = emptyList())

        val distanceKm = run.distanceKm ?: 0.0
        val durationSeconds = run.durationSeconds ?: 0
        val averagePace = run.averagePaceSecPerKm
            ?: judgementService.averagePaceSecPerKm(distanceKm * 1000.0, durationSeconds)

        return RunDetailResponse(
            runId = run.id,
            routeId = run.routeId,
            routeName = routeName,
            status = run.status,
            actualGeoJson = actualGeoJson,
            plannedGeoJson = plannedGeoJson,
            distanceKm = distanceKm,
            durationSeconds = durationSeconds,
            averagePaceSecPerKm = averagePace,
            sketchMatchPercent = run.sketchMatchPercent ?: 0,
            routeCompletionPercent = run.routeCompletionPercent ?: 0,
            completedAt = run.finishedAt?.toString() ?: "",
        )
    }

    /** Looks up a run and enforces ownership, hiding existence (404 RUN_NOT_FOUND either way)
     * rather than leaking a distinct "forbidden" signal for another user's run id. */
    private fun findOwnedRun(userId: String, runId: String): RunRepository.RunRow {
        val run = runRepository.findById(runId) ?: throw NotFoundException(ErrorCodes.RUN_NOT_FOUND, "러닝 기록을 찾을 수 없습니다.")
        if (run.userId != userId) {
            throw NotFoundException(ErrorCodes.RUN_NOT_FOUND, "러닝 기록을 찾을 수 없습니다.")
        }
        return run
    }

    private fun parseInstant(raw: String): Instant = try {
        Instant.parse(raw)
    } catch (e: Exception) {
        throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "시간 형식이 올바르지 않습니다.")
    }
}
