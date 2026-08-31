package com.dallim.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * [Configuration.Provider]로 WorkManager에 [HiltWorkerFactory]를 꽂아줘서
 * [com.dallim.app.location.GpsBatchUploadWorker]가 `@HiltWorker`로 의존성을 주입받을 수 있게 한다
 * (S-24 GPS 배치 업로드 — docs/01-feature-spec.md §1.3). 기본 WorkManagerInitializer는
 * AndroidManifest.xml에서 꺼뒀다.
 */
@HiltAndroidApp
class DallimApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
