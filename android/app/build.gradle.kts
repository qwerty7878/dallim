import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    // FCM 폰 시스템 푸시 (docs/02-api-spec.md 10장). app/google-services.json(gitignore 처리,
    // project_id: dallim-765b5)이 없으면 이 플러그인이 빌드를 실패시키므로, 파일이 로컬에
    // 존재해야만 정상 빌드된다 — 저장소를 새로 클론한 환경은 Firebase 콘솔에서 직접 받아야 한다.
    alias(libs.plugins.google.services)
}

// 네이버맵 SDK Client ID — local.properties(gitignore 대상)에서만 읽는다. 값이 없으면 빈 문자열로
// fallback해 빌드가 절대 실패하지 않도록 한다(NCP 키가 아직 발급되지 않은 상태에서도 정상 빌드).
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { load(it) }
    }
}
val naverMapClientId: String = localProperties.getProperty("NAVER_MAP_CLIENT_ID", "").trim()

// Google 소셜 로그인 키 — 위 네이버맵과 동일한 패턴: local.properties에 없으면 빈 문자열로
// fallback해 키가 아직 발급되지 않은 상태에서도 빌드/설치가 절대 실패하지 않도록 한다.
val googleWebClientId: String = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "").trim()

// AdMob (S-56 홈 네이티브 광고 배너) — 아직 실제 AdMob 계정/앱이 없으므로 local.properties에
// 값이 없으면 구글이 공식 문서에 공개한 "테스트 전용" ID로 폴백한다. 이 폴백값들은 구글이 배포
// 안전하다고 명시한 테스트 ID라 하드코딩해도 무방하지만, 실제 프로덕션 배포 전에는 반드시
// local.properties의 ADMOB_APP_ID / ADMOB_NATIVE_HOME_AD_UNIT_ID를 진짜 값으로 채워야 한다 —
// 테스트 ID로 배포하면 심사 반려/수익 미발생 사유가 된다.
val admobAppIdFallback = "ca-app-pub-3940256099942544~3347511713" // Google 공식 테스트 App ID
val admobNativeHomeAdUnitIdFallback = "ca-app-pub-3940256099942544/2247696110" // Google 공식 Native Advanced 테스트 유닛 ID
// S-56 2단계 — AI Discovery 일일 무료 횟수 소진 시 "광고 보고 1회 더"(docs/02-api-spec.md 8.4)가
// 띄우는 리워드 광고 유닛. 위와 동일한 폴백 패턴.
val admobRewardedDiscoveryAdUnitIdFallback = "ca-app-pub-3940256099942544/5224354917" // Google 공식 Rewarded 테스트 유닛 ID
val admobAppId: String = localProperties.getProperty("ADMOB_APP_ID", "").trim().ifEmpty { admobAppIdFallback }
val admobNativeHomeAdUnitId: String =
    localProperties.getProperty("ADMOB_NATIVE_HOME_AD_UNIT_ID", "").trim().ifEmpty { admobNativeHomeAdUnitIdFallback }
val admobRewardedDiscoveryAdUnitId: String =
    localProperties.getProperty("ADMOB_REWARDED_DISCOVERY_AD_UNIT_ID", "").trim()
        .ifEmpty { admobRewardedDiscoveryAdUnitIdFallback }

android {
    namespace = "com.dallim.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dallim.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AndroidManifest.xml의 com.naver.maps.map.NCP_KEY_ID meta-data에 연결된다.
        manifestPlaceholders["naverMapClientId"] = naverMapClientId
        // 지도 화면들이 실제 SDK 컴포넌트 / 기존 Canvas 폴백 중 무엇을 그릴지 런타임에 분기할 때 쓴다.
        buildConfigField("boolean", "NAVER_MAP_CLIENT_ID_CONFIGURED", naverMapClientId.isNotEmpty().toString())

        // Google Sign-In(Credential Manager)의 serverClientId. RealSocialLoginLauncher가
        // 비어있으면 즉시 Result.failure로 가드한다.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")

        // AndroidManifest.xml의 com.google.android.gms.ads.APPLICATION_ID meta-data에 연결된다.
        manifestPlaceholders["admobAppId"] = admobAppId
        // HomeNativeAdBanner(S-56)가 로드할 네이티브 광고 유닛 ID.
        buildConfigField("String", "ADMOB_NATIVE_HOME_AD_UNIT_ID", "\"$admobNativeHomeAdUnitId\"")
        // DiscoveryRewardedAdController(S-56 2단계)가 로드할 리워드 광고 유닛 ID.
        buildConfigField("String", "ADMOB_REWARDED_DISCOVERY_AD_UNIT_ID", "\"$admobRewardedDiscoveryAdUnitId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-network"))
    implementation(project(":core-ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    // S-02 Google 소셜 로그인 (docs/01-feature-spec.md §1.1). 키 미설정 시에도 이 모듈은
    // 문제없이 컴파일/링크되며, RealSocialLoginLauncher가 BuildConfig 플래그로 가드한다.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    // S-20~S-24 GPS pipeline: FusedLocationProviderClient -> ForegroundService -> Room -> WorkManager
    // (docs/01-feature-spec.md §1.3). Kept inside `app` (location package) rather than a new
    // `core-location` module per CLAUDE.md's "app 내 location 패키지" fallback.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.lifecycle.service)

    // FCM 폰 시스템 푸시 (docs/01-feature-spec.md §1.7 2단계, docs/02-api-spec.md 10장).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    // S-56 홈 네이티브 광고 배너 (docs/01-feature-spec.md 홈 모듈). 결제/굿즈 없이 광고 수익만으로
    // 시작하기로 한 첫 조각 — AdMob SDK.
    implementation(libs.play.services.ads)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
}
