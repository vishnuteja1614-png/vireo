plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // TFLite models must stay uncompressed or the interpreter cannot mmap them.
    androidResources { noCompress += listOf("tflite") }
    namespace = "com.vireo.editor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vireo.editor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // debug signing so CI can produce an installable release APK without secrets
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

android.testOptions {
    unitTests.isReturnDefaultValues = true
    // Surface the scorecard printed by FeatureMatrixTest in the CI log.
    unitTests.all {
        it.testLogging {
            showStandardStreams = true
            events("passed", "failed", "skipped")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Unit tests cannot instantiate android.net.Uri; mock it instead.
    testImplementation("org.mockito:mockito-core:5.12.0")

    // FFmpeg. Media3 has no reverse-playback path at all and cannot remux to
    // arbitrary containers, so these features need a real FFmpeg build.
    // The original com.arthenica artifacts were retired in Jan 2025 and pulled
    // from Maven Central in April 2025; this is the maintained drop-in fork
    // (same package, same API, SDK 35 and 16 KB page ready).
    // "min" is the smallest variant and still carries the reverse, areverse,
    // setpts and atempo filters we need, which keeps the APK down.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-min:8.1.7")

    // MediaPipe Tasks Vision: on-device person segmentation for AI background
    // removal. Runs entirely offline - no API key, no account, no upload.
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // Vosk: offline speech recognition with word-level timestamps, used for
    // auto-captions. Chosen over Whisper/ONNX because Whisper on ONNX Runtime
    // requires hand-written encoder/decoder loops and a tokenizer, whereas
    // Vosk ships a real Android artifact and emits the per-word start/end
    // times captions actually need. Runs offline - no API key, no upload.
    implementation("com.alphacephei:vosk-android:0.3.47")
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Open-source media engine: ExoPlayer + Transformer (AndroidX Media3, Apache-2.0)
    val media3 = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-common:$media3")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WebSocket client for the free Microsoft neural TTS endpoint
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Run the JVM unit tests as part of every debug build.
// The CI workflow file cannot be edited by the current token, so the test
// task is attached here instead: assembleDebug is finalized by the tests, and
// a test failure fails the build. No circular dependency, because the tests
// run after assembly rather than before it.
tasks.whenTaskAdded {
    if (name == "assembleDebug") {
        finalizedBy("testDebugUnitTest")
    }
}
