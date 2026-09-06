package com.dallim.app.route.create.ai

import android.app.Activity
import android.content.Context
import com.dallim.app.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * S-56 2단계 — AI Discovery(S-45) 일일 무료 횟수 소진 시 뜨는 "일 무료 횟수 소진" 모달의
 * `[광고 보고 1회 더]` 버튼이 쓰는 리워드 광고 로드/표시 헬퍼 (docs/02-api-spec.md 8.4).
 *
 * `RewardedAd.load()`는 Application Context로도 되지만 `RewardedAd.show()`는 SDK 제약상 반드시
 * [Activity]가 있어야 한다 — 그렇다고 [AiRouteViewModel]이 Activity를 직접 들고 있으면
 * 화면 회전/재구성 사이 Activity 참조가 새는 문제가 생기므로, 이 컨트롤러는 Activity를 저장하지
 * 않고 [showAd] 호출 시점에 Composable이 넘겨준 것만 그 자리에서 쓴다.
 *
 * 과설계 금지 — 사전 로드/캐싱/재시도 전략 없음: 버튼 탭마다 새로 로드하고 로드가 끝나는 즉시
 * 보여준다. 로드 실패/표시 실패/리워드 없이 닫힘은 전부 [onFailedOrDismissed] 한 콜백으로
 * 뭉뚱그린다 — 호출부(ViewModel)가 조용히 모달을 유지하거나 스낵바만 띄우면 되는 수준이라
 * 실패 사유를 세분화할 필요가 없다.
 */
class DiscoveryRewardedAdController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun showAd(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onFailedOrDismissed: () -> Unit,
    ) {
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_DISCOVERY_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    onFailedOrDismissed()
                }

                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    var rewardEarned = false
                    rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            if (!rewardEarned) onFailedOrDismissed()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            onFailedOrDismissed()
                        }
                    }
                    rewardedAd.show(activity) {
                        rewardEarned = true
                        onRewardEarned()
                    }
                }
            },
        )
    }
}
