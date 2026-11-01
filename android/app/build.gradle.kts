plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// engine.js is generated from extension/engine/engine.js on every build
// (docs/android/ANDROID-APP.md); never committed here, never hand-edited.
val engineDir = layout.buildDirectory.dir("generated/engine/assets")
val bundleEngine by tasks.registering(Exec::class) {
    val out = engineDir.get().file("engine.js").asFile
    inputs.dir(rootProject.file("../extension/engine"))
    inputs.dir(rootProject.file("../extension/reader"))
    inputs.dir(rootProject.file("../extension/overlays"))
    outputs.file(out)
    commandLine(rootProject.file("scripts/build-engine.sh").absolutePath, out.absolutePath)
}

android {
    namespace = "com.owenleung.dewey"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.owenleung.dewey"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sideloaded builds: sign with the debug key so `assembleRelease` installs too.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets["main"].assets.srcDir(engineDir)
    testOptions { unitTests.isReturnDefaultValues = true }
}

tasks.named("preBuild") { dependsOn(bundleEngine) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-process:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    // Scan my ID card: Google's code scanner (Play services draws the camera; the app needs no camera permission).
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
