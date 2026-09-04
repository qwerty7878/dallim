// Root build file — declares plugin versions once via the version catalog (gradle/libs.versions.toml)
// so every module (app, core-network, core-ui) applies them without redeclaring versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    // FCM 폰 시스템 푸시 (docs/02-api-spec.md 10장) — app/google-services.json을 읽어 BuildConfig에
    // Firebase 프로젝트 설정을 심어준다. apply false: 실제 적용은 app/build.gradle.kts에서만.
    alias(libs.plugins.google.services) apply false
}
