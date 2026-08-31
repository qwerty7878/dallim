package com.dallim.app.location

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GpsPointDao {
    @Insert
    suspend fun insert(point: GpsPointEntity): Long

    /** 그리기/애니메이션·로컬 프리체크용 — 러닝 전체 궤적을 시간순으로. */
    @Query("SELECT * FROM gps_points WHERE runId = :runId ORDER BY timestampMillis ASC")
    suspend fun getPointsForRun(runId: String): List<GpsPointEntity>

    /** [GpsBatchUploadWorker]가 다음 배치로 보낼 미업로드 포인트. */
    @Query("SELECT * FROM gps_points WHERE runId = :runId AND uploaded = 0 ORDER BY timestampMillis ASC LIMIT :limit")
    suspend fun getUnuploadedPoints(runId: String, limit: Int): List<GpsPointEntity>

    @Query("SELECT COUNT(*) FROM gps_points WHERE runId = :runId AND uploaded = 0")
    suspend fun countUnuploaded(runId: String): Int

    @Query("UPDATE gps_points SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)

    @Query("DELETE FROM gps_points WHERE runId = :runId")
    suspend fun deleteForRun(runId: String)
}
