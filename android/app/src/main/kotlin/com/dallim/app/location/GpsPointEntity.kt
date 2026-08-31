package com.dallim.app.location

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room-로컬 GPS 포인트 (docs/01-feature-spec.md §1.3 파이프라인의 "Room DB (로컬 우선 저장)" 단계).
 * `ForegroundService`가 위치를 1초 간격으로 요청하지만, 실제로 이 테이블에 INSERT하는 건 직전
 * 기록 지점에서 5~10m 이상 이동했을 때뿐이다(배터리/저장공간 절약 — 서비스 쪽 필터링).
 *
 * `uploaded` 플래그로 [GpsBatchUploadWorker]가 아직 서버로 못 보낸 포인트만 골라 배치 전송한다
 * (부분 업로드 재시도 지원 — docs/02-api-spec.md 5장 "여러 배치로 나눠 보낼 수 있음").
 */
@Entity(tableName = "gps_points")
data class GpsPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val lat: Double,
    val lng: Double,
    /** epoch millis — 서버 전송 시 ISO-8601 문자열로 변환한다. */
    val timestampMillis: Long,
    val accuracyM: Float,
    val uploaded: Boolean = false,
)
