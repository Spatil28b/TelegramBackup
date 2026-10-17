plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.telegrambackup"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.telegrambackup"
        minSdk = 24
        targetSdk = 36
        versionCode = 154
        versionName = "2.7.6"
    }

    signingConfigs {
        create("customDebug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH") ?: "release.keystore"
        val releaseKeystoreFile = file(releaseKeystorePath)
        if (releaseKeystoreFile.exists()) {
            val ksPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: ""
            val kAlias = System.getenv("RELEASE_KEY_ALIAS") ?: ""
            val kPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: ""
            if (ksPassword.isBlank() || kAlias.isBlank() || kPassword.isBlank()) {
                throw GradleException(
                    "Release keystore found at '$releaseKeystorePath' but signing env vars are missing.\n" +
                    "Set RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS, and RELEASE_KEY_PASSWORD."
                )
            }
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = ksPassword
                keyAlias = kAlias
                keyPassword = kPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("customDebug")
        }
        release {
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig != null) {
                signingConfig = releaseConfig
            } else {
                tasks.matching { it.name.startsWith("assembleRelease") || it.name.startsWith("bundleRelease") }.configureEach {
                    doFirst {
                        throw GradleException(
                            "No release signing config found. Place release.keystore in app/ or set RELEASE_KEYSTORE_PATH, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS, and RELEASE_KEY_PASSWORD."
                        )
                    }
                }
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}



dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-extended")
  // Coil Image & Video Loader for Compose
  implementation("io.coil-kt:coil-compose:2.6.0")
  implementation("io.coil-kt:coil-video:2.6.0")

  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // WorkManager
  implementation(libs.androidx.work.runtime.ktx)

  // OkHttp
  implementation(libs.okhttp)

  // Biometrics
  implementation("androidx.biometric:biometric:1.1.0")

  // Room
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // Offline QR Code Generation
  implementation("com.google.zxing:core:3.5.3")

  // On-Device Google ML Kit OCR (Ultra-Fast Text Recognition)
  implementation("com.google.mlkit:text-recognition:16.0.1")
}

