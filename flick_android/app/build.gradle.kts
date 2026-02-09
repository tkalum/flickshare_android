import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.flickshare"
    compileSdk = 36 // Latest SDK

    defaultConfig {
        applicationId = "com.example.flickshare"
        minSdk = 24     // Supports Android 7.0 and 7.1+
        targetSdk = 36  // Optimized for latest Android
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    // --- ABI SPLITS CONFIGURATION ---
    splits {
        abi {
            isEnable = true
            reset()
            // Includes ARM 32-bit (v7a), ARM 64-bit (v8a), and x86 64-bit
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
}

// --- VERSION CODE AUTOMATION ---
// This ensures Google Play accepts the APKs by giving them unique IDs
androidComponents {
    val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)

    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier

            if (abi != null) {
                val baseAbiVersionCode = abiCodes[abi] ?: 0
                // For versionCode 1:
                // v7a becomes 1001, v8a becomes 2001, etc.
                output.versionCode.set(baseAbiVersionCode * 1000 + (android.defaultConfig.versionCode ?: 1))
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    implementation("androidx.compose.material3:material3:1.2.0") // Required for new One UI containers
    implementation("androidx.compose.ui:ui:1.6.0")

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.compose.material:material-icons-extended")

}