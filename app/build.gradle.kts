plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Optional release signing with your own key. Nothing secret is stored in the repo: CI decodes
// the keystore from the SIGNING_KEYSTORE_BASE64 secret into a temp file and passes its path and
// password via environment variables. Without them, release builds use the debug key.
val signingKeystore: String? = System.getenv("SIGNING_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.gamedeals.watchlist"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gamedeals.watchlist"
        minSdk = 26
        targetSdk = 36 // Google Play requires API 36 for new apps and updates
        // Every CI build gets a higher version code, as Play requires for each upload.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100) ?: 3
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // "play": the Google Play build (uploaded as an .aab; ad blocking starts off).
    // "sideload": the APK you install yourself (ad blocking starts on).
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "DEFAULT_BLOCK_ADS", "false")
        }
        create("sideload") {
            dimension = "distribution"
            buildConfigField("boolean", "DEFAULT_BLOCK_ADS", "true")
        }
    }

    signingConfigs {
        if (signingKeystore != null && signingPassword != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storeType = "pkcs12"
                storePassword = signingPassword
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "gamedeals"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            // R8: strips unused code/resources and optimizes -> smaller, faster APK
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // Origin-restricted JavaScript bridge (WebViewCompat.addWebMessageListener)
    implementation("androidx.webkit:webkit:1.12.1")
    // Installs the bundled baseline profiles (ours + Compose's) on sideloaded installs for faster startup/scrolling
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")

    testImplementation("junit:junit:4.13.2")

    // On-device end-to-end tests (run on an emulator in CI)
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.json:json:20240303")
}
