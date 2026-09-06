package com.dallim.run

import com.dallim.common.GeoJsonLineString
import kotlinx.serialization.Serializable

// Request/response DTOs — docs/02-api-spec.md 5장 (runs). Field names/types must match
// android/core-network/src/main/kotlin/com/dallim/network/run/RunApi.kt exactly.
//
// Timestamps (startedAt/finishedAt/timestamp) travel as ISO-8601 strings, parsed to
// java.time.Instant in RunService — matching the rest of this codebase's DTOs, none of which use
// a custom Instant serializer.

@Serializable
data class ClientDeviceInfoRequest(val gpsAccuracyM: Int? = null)

@Serializable
data class StartRunRequest(
    val routeId: String,
    val mode: String = "SOLO",
    val startedAt: String,
    val clientDeviceInfo: ClientDeviceInfoRequest? = null,
)

@Serializable
data class RunStartResponse(val runId: String, val status: RunStatus)

/** PATCH /runs/{runId}/status — `status`: PAUSED | RUNNING only (docs/02-api-spec.md 5장). */
@Serializable
data class UpdateRunStatusRequest(val status: String)

@Serializable
data class RunStatusResponse(val runId: String, val status: RunStatus)

@Serializable
data class GpsPointRequest(
    val lat: Double,
    val lng: Double,
    val timestamp: String,
    val accuracyM: Double? = null,
)

@Serializable
data class GpsBatchRequest(val points: List<GpsPointRequest>)

/** `receivedCount` is the run's cumulative stored point count, not just this batch's size — see
 * docs/02-api-spec.md 5장 example (1240) and CLAUDE.md rule 4 (partial/repeated batch uploads). */
@Serializable
data class GpsBatchResponse(val receivedCount: Int)

/**
 * `clientPrecheckStatus` is intentionally typed as a raw, unvalidated String: it is UX-preview
 * data only (CLAUDE.md rule 3) and RunService must never branch on it when computing the real
 * judgement — only log/ignore it.
 *
 * `stepCount` (2026-09-06, docs/달림_화면별_상세기획서_v1.3.md PART 4.1) is collected now so
 * historical data exists once a "step count vs. distance" anti-cheat signal is built later — it is
 * pure storage in this round and RunJudgementService must never branch on it (same rule as
 * clientPrecheckStatus). Optional since not every device/session can supply a step sensor reading.
 */
@Serializable
data class FinishRunRequest(
    val finishedAt: String,
    val clientPrecheckStatus: String? = null,
    val stepCount: Int? = null,
)

@Serializable
data class RunFinishResponse(
    val runId: String,
    val status: RunStatus,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averagePaceSecPerKm: Int,
    val sketchMatchPercent: Int,
    val routeCompletionPercent: Int,
    val isFirstDiscoverer: Boolean,
    val earnedInk: Int,
    val earnedBadges: List<String> = emptyList(),
)

@Serializable
data class RunDetailResponse(
    val runId: String,
    val routeId: String,
    val routeName: String,
    val status: RunStatus,
    val actualGeoJson: GeoJsonLineString,
    val plannedGeoJson: GeoJsonLineString,
    val distanceKm: Double,
    val durationSeconds: Int,
    val averagePaceSecPerKm: Int,
    val sketchMatchPercent: Int,
    val routeCompletionPercent: Int,
    val completedAt: String,
    // Storage/debugging only for now — never consulted by RunJudgementService. See
    // FinishRunRequest.stepCount.
    val stepCount: Int? = null,
)

/**
 * POST /runs/{runId}/feedback-tags — 0~3 tags from the fixed [RouteFeedbackTags.ALLOWED]
 * vocabulary. Empty list is valid (skip == no-op, per docs/02-api-spec.md 5장). Anything outside
 * the allowed vocabulary or more than [RouteFeedbackTags.MAX_TAGS_PER_SUBMISSION] entries is a
 * 400 VALIDATION_ERROR — see RunService.submitFeedbackTags.
 */
@Serializable
data class FeedbackTagsRequest(val tags: List<String> = emptyList())

/** `tags` echoes the run's final stored set — on a resubmission this is whatever was stored the
 * first time (idempotent no-op), not necessarily this request's own `tags`. */
@Serializable
data class FeedbackTagsResponse(val runId: String, val tags: List<String>)
