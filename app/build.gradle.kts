import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.chaquo.python")
}

fun gitCommitCount(): Int {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val count = process.inputStream.bufferedReader().readText().trim().toIntOrNull()
    process.waitFor()
    return count ?: 1
}

val commitCount = gitCommitCount()
val versionDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))

android {
    namespace = "me.wasddestroy.avbtoolandroid"
    compileSdk {
        version = release(37)
    }
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "me.wasddestroy.avbtoolandroid"
        minSdk = 27
        targetSdk = 36
        versionCode = commitCount
        versionName = "$versionDate-c$commitCount"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // arm64-v8a only, per project decision.
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("test") {
            storeFile = rootProject.file("keystore/testkey.jks")
            storePassword = "avbtool123"
            keyAlias = "avbtool"
            keyPassword = "avbtool123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
        release {
            signingConfig = signingConfigs.getByName("test")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        buildPython("C:/Users/USER/AppData/Local/Programs/Python/Python313/python.exe")
        pip {
            install("cryptography==42.0.8")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}