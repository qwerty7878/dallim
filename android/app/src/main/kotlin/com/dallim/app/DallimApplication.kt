package com.dallim.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.gms.ads.MobileAds
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

    override fun onCreate() {
        super.onCreate()
        // S-56 홈 네이티브 광고 배너. 초기화는 앱 프로세스당 한 번만 필요하고 결과를 기다릴
        // 필요가 없어(HomeNativeAdBanner의 AdLoader가 알아서 초기화를 기다린 뒤 로드) 콜백 없이
        // fire-and-forget으로 호출한다.
        MobileAds.initialize(this)
    }
}
