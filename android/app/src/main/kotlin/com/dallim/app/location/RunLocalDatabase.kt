package com.dallim.app.location

import androidx.room.Database
import androidx.room.RoomDatabase

/** 러닝 GPS 로컬 저장소 — docs/01-feature-spec.md §1.3 파이프라인의 Room DB 단계. */
@Database(entities = [GpsPointEntity::class], version = 1, exportSchema = false)
abstract class RunLocalDatabase : RoomDatabase() {
    abstract fun gpsPointDao(): GpsPointDao
}
