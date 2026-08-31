package com.dallim.app.location

import com.dallim.ui.components.GeoPoint
import com.dallim.network.run.FinishRunRequest
import com.dallim.network.run.RunApi
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S-24 완주 판정 처리 — GPS 파이프라인의 마지막 두 단계를 순서대로 조율한다
 * (docs/01-feature-spec.md §1.3):
 * `WorkManager 배치 업로드(실패 시 지수 백오프 재시도) -> POST /runs/{runId}/finish`.
 *
 * [LocationTrackingService]는 Finish 시점에 위치 수집만 멈추고 바로 종료하므로, 실제 업로드
 * 대기·재시도·서버 판정 호출은 서비스 생명주기와 분리된 이 클래스가 맡는다 — 화면(ViewModel)이
 * 백그라운드로 가거나 재구성돼도 [WorkManager]의 unique work 자체는 계속 진행된다.
 *
 * **완주 판정은 서버가 유일한 신뢰 소스** (CLAUDE.md rule 3) — [LocalPrecheckCalculator] 결과는
 * `clientPrecheckStatus`로 참고용 전송될 뿐, [RunTrackingRepository]에는 서버 `finishRun` 응답이
 * 성공했다는 사실(FINISHED phase)만 반영하고 판정값 자체는 담지 않는다. 실제 판정값은 S-25가
 * `GET /runs/{runId}`로 다시 조회한다.
 */
@Singleton
class RunFinishCoordinator @Inject constructor(
    private val workManager: WorkManager,
    private val gpsPointDao: GpsPointDao,
    private val runApi: RunApi,
    private val repository: RunTrackingRepository,
) {
    suspend fun finish(runId: String, plannedRoute: List<GeoPoint>) {
        repository.update { it.copy(phase = RunPhase.FINISHING, finishError = null) }

        val localPoints = gpsPointDao.getPointsForRun(runId)
        val precheckStatus = runCatching {
            val timed = localPoints.map { TimedGeoPoint(GeoPoint(lng = it.lng, lat = it.lat), it.timestampMillis) }
            LocalPrecheckCalculator.calculate(timed, plannedRoute).status
        }.getOrDefault("PARTIAL")

        val uniqueWorkName = GpsBatchUploadWorker.uniqueWorkName(runId)
        workManager.enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.KEEP, GpsBatchUploadWorker.buildRequest(runId))

        val finishedWorkInfo = workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName)
            .mapNotNull { infos -> infos.firstOrNull { it.state.isFinished } }
            .first()

        if (finishedWorkInfo.state != WorkInfo.State.SUCCEEDED) {
            repository.update {
                it.copy(phase = RunPhase.FINISH_FAILED, finishError = "GPS 기록 업로드에 실패했어요. 네트워크를 확인하고 다시 시도해주세요.")
            }
            return
        }

        val response = runCatching {
            runApi.finishRun(
                runId = runId,
                request = FinishRunRequest(finishedAt = Instant.now().toString(), clientPrecheckStatus = precheckStatus),
            )
        }.getOrNull()

        if (response != null && response.isSuccessful && response.body()?.success == true) {
            repository.update { it.copy(phase = RunPhase.FINISHED) }
        } else {
            val message = response?.body()?.error?.message ?: "완주 판정을 받아오지 못했어요. 다시 시도해주세요."
            repository.update { it.copy(phase = RunPhase.FINISH_FAILED, finishError = message) }
        }
    }
}
