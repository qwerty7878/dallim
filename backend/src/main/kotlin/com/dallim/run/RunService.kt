package com.dallim.run

import com.dallim.common.BadRequestException
import com.dallim.common.ConflictException
import com.dallim.common.ErrorCodes
import com.dallim.common.GeoJsonLineString
import com.dallim.common.IdGenerator
import com.dallim.common.NotFoundException
import com.dallim.notification.NotificationService
import com.dallim.route.RouteRepository
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Run domain business logic — docs/02-api-spec.md 5장, docs/01-feature-spec.md 2.2.D.
 * RunRoutes.kt stays a thin HTTP adapter; every status-transition/validation decision lives here.
 *
 * `clientPrecheckStatus` (FinishRunRequest) is UX-preview data only and is never read here —
 * [RunJudgementService] is the sole source of truth for a run's final status (CLAUDE.md rule 3).
 */
class RunService(
    private val runRepository: RunRepository,
    private val routeRepository: RouteRepository,
    private val judgementService: RunJudgementService,
    private val finisherCountSync: FinisherCountSync,
    private val notificationService: NotificationService,
) {
    private val finishedStatuses = setOf(RunStatus.COMPLETED, RunStatus.PARTIAL, RunStatus.ABORTED, RunStatus.UNDER_REVIEW)

    /**
     * POST /runs. `request.routeId == null`이면 자유 러닝(2026-09-26, 사용자 요청)이라 코스
     * 존재 확인을 건너뛴다.
     */
    fun startRun(userId: String, request: StartRunRequest): RunStartResponse {
        if (request.routeId != null) {
            // Also doubles as an existence check for routeId — there's no dedicated error code
            // for this in docs/02-api-spec.md 5장, so ROUTE_NOT_FOUND (already defined for the
            // route domain) is reused rather than letting an unknown routeId fall through to a
            // raw FK violation / 500.
            runRepository.findPlannedPath(request.routeId)
                ?: throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")
        }

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

        // stepCount is storage-only (see FinishRunRequest.stepCount) — the only check here is
        // rejecting a nonsensical negative value; it never feeds RunJudgementService.
        if ((request.stepCount ?: 0) < 0) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "stepCount는 0 이상이어야 합니다.")
        }

        val rawPoints = runRepository.fetchGpsPoints(runId)
        if (rawPoints.size < RunJudgementService.MIN_POINTS_FOR_JUDGEMENT) {
            throw BadRequestException(ErrorCodes.GPS_DATA_INSUFFICIENT, "완주 판정을 위한 GPS 데이터가 부족합니다.")
        }

        val routeId = run.routeId
        val finishedAt = parseInstant(request.finishedAt)
        // 자유 러닝(routeId == null, 2026-09-26 사용자 요청)은 목표 코스가 없어 judgeFreeform으로
        // 판정한다 — 구간 커버리지/Sketch Match 없이 거리·시간·페이스·비정상속도만.
        val result = if (routeId != null) {
            // routeId is a foreign key on run_records, so the planned path is guaranteed to exist.
            val plannedPath = runRepository.findPlannedPath(routeId)
                ?: throw NotFoundException(ErrorCodes.ROUTE_NOT_FOUND, "코스를 찾을 수 없습니다.")
            judgementService.judge(plannedPath, rawPoints)
        } else {
            judgementService.judgeFreeform(rawPoints)
        }
        val averagePace = judgementService.averagePaceSecPerKm(result.distanceMeters, result.durationSeconds)

        // "First Discoverer"는 코스를 처음 완주한 사람이라는 개념 — 목표 코스가 없는 자유 러닝엔
        // 적용되지 않는다.
        val isFirstDiscoverer = routeId != null && result.status == RunStatus.COMPLETED &&
            !runRepository.hasPriorCompletedRun(routeId, runId)

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
            stepCount = request.stepCount,
        )

        if (result.status == RunStatus.COMPLETED) {
            // finisherCount는 "이 코스를 완주한 사람 수"라는 개념이라 목표 코스가 없는 자유
            // 러닝에는 적용되지 않는다.
            if (routeId != null) finisherCountSync.recordFinisher(routeId)
            // docs/02-api-spec.md 9.4 — RUN_COMPLETED in-app notification, fired at the same point
            // the finisher count is recorded (COMPLETED is the only trigger in this phase).
            val routeName = routeId?.let { runRepository.findRouteName(it) } ?: "자유 러닝"
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

    /**
     * GET /runs/{runId} — result re-entry/share. 자유 러닝(routeId == null)이면
     * routeId/routeName/plannedGeoJson은 전부 null — 목표 코스 자체가 없다.
     */
    fun getRunDetail(userId: String, runId: String): RunDetailResponse {
        val run = findOwnedRun(userId, runId)
        val routeId = run.routeId
        val routeName = routeId?.let { runRepository.findRouteName(it) }
        val plannedGeoJson = routeId?.let { runRepository.findPlannedGeoJson(it) }
        // Only populated once finish-judgement has run; empty (not an error) for an in-progress run.
        val actualGeoJson = runRepository.findActualGeoJson(runId) ?: GeoJsonLineString(coordinates = emptyList())

        val distanceKm = run.distanceKm ?: 0.0
        val durationSeconds = run.durationSeconds ?: 0
        val averagePace = run.averagePaceSecPerKm
            ?: judgementService.averagePaceSecPerKm(distanceKm * 1000.0, durationSeconds)

        return RunDetailResponse(
            runId = run.id,
            routeId = routeId,
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
            stepCount = run.stepCount,
            registeredRouteId = if (routeId == null) routeRepository.findRouteIdBySourceRunId(runId) else null,
        )
    }

    /**
     * POST /runs/{runId}/register-as-route (2026-09-26, 사용자 요청) — 자유 러닝을 완주한 뒤 그
     * 실제 궤적을 새 코스로 공개 등록한다. UGC 모더레이션(금칙어/도로 안전 필터, 완주 전까지
     * 비공개)은 이번 라운드에 없다 — 사용자 결정으로 필터 없이 즉시 공개.
     */
    fun registerAsRoute(userId: String, runId: String, request: RegisterRouteRequest): RegisterRouteResponse {
        val run = findOwnedRun(userId, runId)
        if (run.routeId != null) {
            throw BadRequestException(ErrorCodes.RUN_NOT_FREEFORM, "코스를 목표로 뛴 러닝은 코스로 등록할 수 없습니다.")
        }
        if (run.status != RunStatus.COMPLETED) {
            throw BadRequestException(ErrorCodes.RUN_NOT_COMPLETED, "완주한 러닝만 코스로 등록할 수 있습니다.")
        }
        if (routeRepository.findRouteIdBySourceRunId(runId) != null) {
            throw ConflictException(ErrorCodes.ROUTE_ALREADY_REGISTERED, "이미 코스로 등록된 러닝입니다.")
        }
        val name = request.name.trim()
        val emoji = request.emoji.trim()
        if (name.isEmpty() || name.length > MAX_ROUTE_NAME_LENGTH) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "코스 이름은 1~${MAX_ROUTE_NAME_LENGTH}자여야 합니다.")
        }
        if (emoji.isEmpty()) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "이모지를 선택해주세요.")
        }

        val distanceKm = run.distanceKm ?: 0.0
        val durationSeconds = run.durationSeconds ?: 0
        val newRouteId = IdGenerator.route()
        routeRepository.createFromRun(
            newRouteId = newRouteId,
            runId = runId,
            userId = userId,
            name = name,
            emoji = emoji,
            distanceKm = distanceKm,
            // 이 러너가 실제로 걸린 시간을 그대로 예상 소요시간으로 쓴다 — discovery(AI 자동
            // 생성)의 가정치(분/km 상수)보다 정확하다(이미 한 번 뛰어본 실측값이므로).
            estimatedMinutes = (durationSeconds / 60.0).roundToInt().coerceAtLeast(1),
        )
        return RegisterRouteResponse(routeId = newRouteId)
    }

    /**
     * POST /runs/{runId}/feedback-tags — 0~3 tags from [RouteFeedbackTags.ALLOWED]. Does not
     * require the run to be COMPLETED (SPEC places no such constraint — this is a low-friction
     * "3초 컷" prompt, not gated on judgement outcome) and is unrelated to RunJudgementService.
     * A resubmission for a run that already has stored tags is a no-op (see
     * RunRepository.submitFeedbackTagsIfAbsent) rather than an error or an overwrite.
     */
    fun submitFeedbackTags(userId: String, runId: String, request: FeedbackTagsRequest): FeedbackTagsResponse {
        val run = findOwnedRun(userId, runId)
        val routeId = run.routeId
            ?: throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "자유 러닝에는 코스 평가를 남길 수 없습니다.")

        if (request.tags.size > RouteFeedbackTags.MAX_TAGS_PER_SUBMISSION) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "태그는 최대 ${RouteFeedbackTags.MAX_TAGS_PER_SUBMISSION}개까지 선택할 수 있습니다.")
        }
        val invalidTags = request.tags.filterNot { it in RouteFeedbackTags.ALLOWED }
        if (invalidTags.isNotEmpty()) {
            throw BadRequestException(ErrorCodes.VALIDATION_ERROR, "허용되지 않은 태그입니다: ${invalidTags.joinToString()}")
        }

        val storedTags = runRepository.submitFeedbackTagsIfAbsent(
            runId = runId,
            routeId = routeId,
            userId = userId,
            tags = request.tags.distinct(),
        )
        return FeedbackTagsResponse(runId = runId, tags = storedTags)
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

    private companion object {
        // com.dallim.route.SketchRouteTable.name is varchar(50).
        const val MAX_ROUTE_NAME_LENGTH = 50
    }
}
