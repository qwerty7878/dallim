package com.dallim.app.location

import com.dallim.ui.components.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S-21/S-22/S-23 상태 머신의 현재 phase. destinations 문서 주석대로 S-22(일시정지/종료)/
 * S-23(코스이탈)/S-24(완주판정)는 별도 화면이 아니라 이 상태들에 대응하는 S-21 화면 내부 상태다.
 */
enum class RunPhase {
    IDLE,
    RUNNING,
    PAUSED,
    /** 종료 버튼을 눌러 GPS 배치 업로드 + 서버 완주판정을 기다리는 중 (S-24). */
    FINISHING,
    /** [POST /runs/{id}/finish] 성공 — 화면단이 S-25로 내비게이션할 차례. */
    FINISHED,
    /** 업로드/판정 실패 — 재시도 UI를 보여줘야 하는 상태. */
    FINISH_FAILED,
}

/**
 * [LocationTrackingService]가 쓰고 S-21 ViewModel이 구독하는 러닝 중 상태 스냅샷.
 * `Service`와 `Activity`/`ViewModel`을 바인딩 없이 느슨하게 연결하는 싱글턴 허브 —
 * Service는 위치가 올 때마다 이걸 갱신하고, Compose 쪽은 StateFlow로만 관찰한다.
 */
data class RunTrackingSnapshot(
    val phase: RunPhase = RunPhase.IDLE,
    val runId: String? = null,
    val distanceMeters: Double = 0.0,
    /** 일시정지 구간을 제외한 순수 러닝 경과시간. */
    val elapsedMillis: Long = 0L,
    val currentPaceSecPerKm: Int? = null,
    val actualPath: List<GeoPoint> = emptyList(),
    val currentLocation: GeoPoint? = null,
    val gpsAccuracyM: Float? = null,
    /** S-23: 계획 경로 대비 60m 이상 이격이 15초 이상 지속된 상태인지. */
    val isOffRoute: Boolean = false,
    /** 진행률 링에 쓰는 로컬 프리체크 커버리지(%) — UX 프리뷰 전용, 서버 판정과 다를 수 있다. */
    val coveragePercent: Int = 0,
    val finishError: String? = null,
    /** [StepCounterTracker.stopAndConsume]이 채워줌 — 저장 전용, 판정에는 쓰이지 않는다. */
    val stepCount: Int? = null,
)

@Singleton
class RunTrackingRepository @Inject constructor() {
    private val _state = MutableStateFlow(RunTrackingSnapshot())
    val state: StateFlow<RunTrackingSnapshot> = _state.asStateFlow()

    fun update(transform: (RunTrackingSnapshot) -> RunTrackingSnapshot) {
        _state.value = transform(_state.value)
    }

    fun reset() {
        _state.value = RunTrackingSnapshot()
    }
}
