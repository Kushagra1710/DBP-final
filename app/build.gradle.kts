plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.dbpsecurity.dbpcheckin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dbpsecurity.attendify"
        minSdk = 24
        targetSdk = 36
        versionCode = 20
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Exclude x86/x86_64 (Emulators) to remove their unaligned libraries from the APK
        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }
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
    aaptOptions {
        noCompress += "tflite"
    }
    packaging {
        jniLibs {
            excludes += "**/libimage_processing_util_jni.so"
        }
    }
}

dependencies {
    // Global Exclude for graphics-path to strictly prevent 16KB error
    modules {
        module("androidx.graphics:graphics-path") {
            replacedBy("androidx.graphics:graphics-path-custom", "Removed to fix 16KB alignment error")
        }
    }
    configurations.all {
         exclude(group = "androidx.graphics", module = "graphics-path")
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    
    // Explicit exclude not needed with global exclude, but keeping clean
    implementation(libs.androidx.compose.ui.graphics)
    
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.mlkit.face.detection) // Thin Client
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.guava)

    implementation(libs.play.services.tflite.java) // Thin Client (Fixes 16KB)
    implementation(libs.tensorflow.lite.api) // Required for InterpreterApi definition

    implementation(libs.play.services.location)

    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.cio)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.gson)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}