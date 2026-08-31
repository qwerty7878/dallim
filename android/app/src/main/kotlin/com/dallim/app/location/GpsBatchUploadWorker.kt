package com.dallim.app.location

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dallim.network.run.GpsBatchRequest
import com.dallim.network.run.GpsPointDto
import com.dallim.network.run.RunApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * S-24 완주 판정 처리 — GPS 파이프라인의 마지막 단계 (docs/01-feature-spec.md §1.3):
 * `Room DB -> WorkManager 배치 업로드 (러닝 종료 시, 실패 시 지수 백오프 재시도) -> POST
 * /runs/{runId}/gps-batch`.
 *
 * Room에 쌓인 미업로드 포인트를 [BATCH_SIZE]개씩 잘라 여러 번 순차 업로드한다
 * (docs/02-api-spec.md 5장: "클라이언트가 여러 배치로 나눠 보낼 수 있음"). 배치 하나라도
 * 실패하면 `Result.retry()`를 반환해 WorkManager의 지수 백오프에 맡긴다 — 이미 성공한 배치는
 * `uploaded = true`로 표시돼 있으므로 재시도 시 중복 전송되지 않는다.
 */
@HiltWorker
class GpsBatchUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gpsPointDao: GpsPointDao,
    private val runApi: RunApi,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val runId = inputData.getString(KEY_RUN_ID) ?: return Result.failure()

        return try {
            while (true) {
                val batch = gpsPointDao.getUnuploadedPoints(runId, BATCH_SIZE)
                if (batch.isEmpty()) break

                val response = runApi.uploadGpsBatch(
                    runId = runId,
                    request = GpsBatchRequest(points = batch.map { it.toDto() }),
                )
                if (!response.isSuccessful || response.body()?.success != true) {
                    return Result.retry()
                }
                gpsPointDao.markUploaded(batch.map { it.id })
            }
            Result.success()
        } catch (e: java.io.IOException) {
            // 네트워크 오류 — 지수 백오프 재시도 대상.
            Result.retry()
        }
    }

    private fun GpsPointEntity.toDto(): GpsPointDto = GpsPointDto(
        lat = lat,
        lng = lng,
        timestamp = Instant.ofEpochMilli(timestampMillis).toString(),
        accuracyM = accuracyM,
    )

    companion object {
        const val KEY_RUN_ID = "key_run_id"
        private const val BATCH_SIZE = 500
        const val UNIQUE_WORK_PREFIX = "gps_batch_upload_"

        fun buildRequest(runId: String) = OneTimeWorkRequestBuilder<GpsBatchUploadWorker>()
            .setInputData(workDataOf(KEY_RUN_ID to runId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        fun uniqueWorkName(runId: String) = "$UNIQUE_WORK_PREFIX$runId"
    }
}
