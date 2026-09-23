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
        consumerProguardFiles("consumer-rules.pro")
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

    // FFmpeg-Kit Video (H.264, AAC, scale, volume for WhatsApp Story compression)
    implementation(libs.ffmpeg.kit.video)

    testImplementation(libs.bundles.testing.unit)
}
