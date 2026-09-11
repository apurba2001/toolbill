plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Room writes the schema of every version here. Migration tests read them back to build a
// real old database and run the real migration over it -- the only way to catch a migration
// that compiles, runs, and quietly produces the wrong shape.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.toolbill.android"
    // Current AndroidX (core-ktx 1.19, compose-ui 1.12) requires compiling against API 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.toolbill.android"
        // API 26 makes java.time native, so no core library desugaring is needed.
        minSdk = 26
        // Now level with compileSdk. The Android 17 behaviour changes this app can actually
        // touch are edge-to-edge enforcement and predictive back, both of which it already opts
        // into (enableEdgeToEdge, enableOnBackInvokedCallback) -- and it runs no foreground
        // service, starts no activity from a notification, and holds no exact-alarm permission,
        // which is where the rest of the tightening landed. Verified on an API 37 device.
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // Room DAO behaviour is tested on a device: an in-memory Room database still needs a
        // real SQLite and a real Context, which the JVM unit-test source set does not have.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // The exported schemas ship into the test APK so MigrationTestHelper can find them.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    buildFeatures {
        compose = true
        // The About row states the running version. Reading it from anywhere else is how it
        // came to claim 1.4.0 against a versionName of 0.1.0.
        buildConfig = true
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

// room-testing reads the exported schema JSON through kotlinx-serialization. A platform
// constraint pins serialization-core to 1.7.3 while Room pulls json 1.8.1, and the mismatched
// pair throws AbstractMethodError out of a generated serializer the moment a migration test
// opens a schema. Align the two on the instrumented classpath, which is the only place Room's
// migration bundle is ever loaded.
configurations.configureEach {
    if (name.contains("AndroidTest")) {
        resolutionStrategy.force(
            "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
        )
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Declared rather than inherited. Everything here already uses Flow and viewModelScope, and
    // leaving the version to whichever transitive dependency wins is how the app ended up on
    // 1.9.0 while the catalog said 1.11.0 -- which the instrumented tests then linked against.
    implementation(libs.kotlinx.coroutines.android)

    // The app lock. DEVICE_CREDENTIAL support is why this is the AndroidX prompt rather than
    // the platform one: a device with no enrolled fingerprint still has a PIN to fall back to.
    implementation(libs.androidx.biometric)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // The reminder safety net. AlarmManager is the primary path; this is what notices when
    // a device dropped an alarm and fires it late rather than never.
    implementation(libs.work.runtime.ktx)

    // Live exchange rates from Frankfurter: ECB-sourced, free, no key, no account. The only
    // network call this app makes, and it carries nothing about the user -- just currency codes.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    testImplementation(libs.junit.jupiter)
    // Suspend functions are testable on the JVM too; this was only on the instrumented path.
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // GrantPermissionRule: the reminder tests need POST_NOTIFICATIONS to actually deliver.
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
