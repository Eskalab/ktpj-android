import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "co.eskala.bktpj_notification"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.eskala.bktpj_notification"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "NEON_HOST", "\"${localProps["neon.host"] ?: ""}\"")
        buildConfigField("String", "NEON_USER", "\"${localProps["neon.user"] ?: ""}\"")
        buildConfigField("String", "NEON_PASS", "\"${localProps["neon.pass"] ?: ""}\"")
        buildConfigField("String", "NEON_DB",   "\"${localProps["neon.db"]   ?: "neondb"}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Lifecycle (lifecycleScope en Activity)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
}
