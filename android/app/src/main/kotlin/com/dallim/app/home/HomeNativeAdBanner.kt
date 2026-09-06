package com.dallim.app.home

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dallim.app.BuildConfig
import com.dallim.ui.components.DallimCard
import com.dallim.ui.theme.DallimColors
import com.dallim.ui.theme.DallimTypography
import com.dallim.ui.theme.Spacing
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * S-56 홈 네이티브 광고 배너 (docs/01-feature-spec.md 홈 모듈,
 * docs/달림_화면별_상세기획서_v1.3.md 236행 "5번째 블록 아래에만"). 결제/굿즈 없이 광고 수익만으로
 * 시작하기로 한 첫 조각.
 *
 * AdMob 네이티브 광고는 View 시스템 전용이다 — [NativeAdView]가 실제 광고 에셋 View들을
 * (`headlineView`/`bodyView`/`mediaView`/`callToActionView`/`iconView`) 등록해야만 SDK가
 * 노출/클릭을 계측하므로 Compose로 직접 그릴 수 없다. 이 프로젝트에 View를 감싸 쓴 선례
 * ([com.dallim.ui.components.NaverDrawableMapView]의 `AndroidView(factory = ...)`)와 동일한
 * 패턴으로 감싸되, "광고" 배지와 카드 껍데기([DallimCard])는 토큰을 그대로 쓸 수 있는 Compose
 * 쪽에 둔다.
 *
 * 로드 실패 시 이 컴포저블은 아무것도 그리지 않는다(별도 에러 UI/재시도 로직 없음 — 오케스트레이터
 * 지시). 로딩 중에는 레이아웃이 갑자기 튀지 않도록 빈 자리만 유지한다.
 */
@Composable
fun HomeNativeAdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val adLoader = AdLoader.Builder(context, BuildConfig.ADMOB_NATIVE_HOME_AD_UNIT_ID)
            .forNativeAd { ad -> nativeAd = ad }
            .withAdListener(
                object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadFailed = true
                    }
                },
            )
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
        onDispose { nativeAd?.destroy() }
    }

    when {
        loadFailed -> Unit // 조용히 숨긴다 — 에러 UI/재시도 없음.
        nativeAd == null -> Box(modifier = modifier.fillMaxWidth().height(140.dp))
        else -> DallimCard(modifier = modifier) {
            Text(
                text = "광고",
                style = DallimTypography.Caption,
                color = DallimColors.TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DallimColors.Border)
                    .padding(horizontal = Spacing.xs, vertical = 2.dp),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            AndroidView(
                factory = { ctx -> createNativeAdContentView(ctx) },
                update = { view -> bindNativeAd(view, nativeAd!!) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** [NativeAdView]에 등록한 에셋 View들을 [bindNativeAd]에서 다시 찾기 위한 보관용. */
private class NativeAdAssetViews(
    val iconView: ImageView,
    val headlineView: TextView,
    val bodyView: TextView,
    val mediaView: MediaView,
    val ctaView: TextView,
)

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/**
 * [NativeAdView]와 그 안의 아이콘/헤드라인/본문/미디어/CTA View를 한 번만 조립한다. 색상은
 * `DallimColors` 토큰을 `toArgb()`로 변환해 쓴다 — 이 View 서브트리는 Compose가 아니라서 토큰을
 * 직접 참조할 수 없기 때문이다(04-ui-guide.md의 "하드코딩 금지"는 값 자체가 아니라 토큰 밖에서
 * 임의 색상/치수를 짓는 것을 금지하는 것이라 이 변환은 규칙 위반이 아니다).
 */
private fun createNativeAdContentView(context: Context): NativeAdView {
    val iconView = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(context.dp(40), context.dp(40))
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    val headlineView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = context.dp(12)
        }
        setTextColor(DallimColors.TextPrimary.toArgb())
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 2
    }
    val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(iconView)
        addView(headlineView)
    }

    val bodyView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(8)
        }
        setTextColor(DallimColors.TextSecondary.toArgb())
        textSize = 13f
        maxLines = 2
    }

    val mediaView = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(160)).apply {
            topMargin = context.dp(12)
        }
    }

    val ctaView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(44)).apply {
            topMargin = context.dp(12)
        }
        background = GradientDrawable().apply {
            cornerRadius = context.dp(16).toFloat()
            setColor(DallimColors.Primary.toArgb())
        }
        setTextColor(AndroidColor.WHITE)
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
    }

    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(headerRow)
        addView(bodyView)
        addView(mediaView)
        addView(ctaView)
    }

    return NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(column)
        this.iconView = iconView
        this.headlineView = headlineView
        this.bodyView = bodyView
        this.mediaView = mediaView
        this.callToActionView = ctaView
        tag = NativeAdAssetViews(iconView, headlineView, bodyView, mediaView, ctaView)
    }
}

private fun bindNativeAd(nativeAdView: NativeAdView, ad: NativeAd) {
    val assets = nativeAdView.tag as? NativeAdAssetViews ?: return

    assets.headlineView.text = ad.headline

    val body = ad.body
    assets.bodyView.text = body
    assets.bodyView.visibility = if (body.isNullOrBlank()) View.GONE else View.VISIBLE

    val icon = ad.icon
    if (icon != null) {
        assets.iconView.setImageDrawable(icon.drawable)
        assets.iconView.visibility = View.VISIBLE
    } else {
        assets.iconView.visibility = View.GONE
    }

    val callToAction = ad.callToAction
    assets.ctaView.text = callToAction
    assets.ctaView.visibility = if (callToAction.isNullOrBlank()) View.GONE else View.VISIBLE

    assets.mediaView.mediaContent = ad.mediaContent
    assets.mediaView.visibility = if (ad.mediaContent != null) View.VISIBLE else View.GONE

    nativeAdView.setNativeAd(ad)
}
