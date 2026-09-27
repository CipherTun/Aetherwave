plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ciphertun.aetherwave"
    // Bump compileSdk to whatever Android Studio currently offers as stable —
    // 34 is used here as a safe, long-established baseline.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ciphertun.aetherwave"
        // 26, not 24: RssFeedParser uses java.time (Instant/DateTimeFormatter)
        // to parse podcast pubDates, which needs API 26+ without adding core
        // library desugaring. Your own device is API 29, so this costs you
        // nothing — it just excludes Android 7.0/7.1 (API 24-25) users.
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"

        // Free, instant sign-up at https://developer.jamendo.com — no cost, no approval wait.
        // This is the only credential the app needs — Internet Archive and Apple's
        // podcast endpoints (search/lookup/charts + RSS) are all keyless.
        buildConfigField("String", "JAMENDO_CLIENT_ID", "\"${project.findProperty("JAMENDO_CLIENT_ID") ?: "REPLACE_ME"}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
}
