plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.toolbill.android"
    // Current AndroidX (core-ktx 1.19, compose-ui 1.12) requires compiling against API 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.toolbill.android"
        // API 26 makes java.time native, so no core library desugaring is needed.
        minSdk = 26
        // Deliberately one behind compileSdk: compile against the newest APIs without
        // inheriting Android 17 runtime behaviour changes we have not validated. Bump to 37
        // as a considered step before launch.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// core/domain is pure JVM, so unit tests need no emulator. JUnit 5 runs natively
// through the Gradle test task; no third-party Android JUnit 5 plugin required.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
