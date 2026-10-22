plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.countdown.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.tqnl.countdown"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.2"
    }
    val releaseStore = providers.environmentVariable("COUNTDOWN_KEYSTORE").orNull
    if (releaseStore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.environmentVariable("COUNTDOWN_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("COUNTDOWN_KEY_ALIAS").orElse("countdown").get()
                keyPassword = providers.environmentVariable("COUNTDOWN_KEY_PASSWORD")
                    .orElse(providers.environmentVariable("COUNTDOWN_STORE_PASSWORD")).get()
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    testImplementation("junit:junit:4.13.2")
}
