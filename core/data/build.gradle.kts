plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.vimal.utl.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    // Room (for history)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // FFmpeg-Kit — Phase 3 TranscodeStrategy only (not needed for MVP stream-copy)
    // Add when Phase 3 begins: implementation("com.arthenica:ffmpeg-kit-full:6.0-2")
    // Repo to add in settings.gradle.kts: maven { url = uri("https://packagecloud.io/arthenica/maven") }

    testImplementation(libs.bundles.testing.unit)
}
