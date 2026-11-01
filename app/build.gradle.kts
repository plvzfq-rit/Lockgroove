plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.plvzfqrit.lockgroove"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.plvzfqrit.lockgroove"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Two builds of the same app. "standard" has no internet permission at all. "withLyrics" adds
    // it, and the lyrics feature that needs it. (Android Studio picks the alphabetically first
    // flavor by default, which is why this one isn't called "lyrics".)
    flavorDimensions += "edition"
    productFlavors {
        create("standard") {
            dimension = "edition"
        }
        create("withLyrics") {
            dimension = "edition"
            applicationIdSuffix = ".lyrics"
            versionNameSuffix = "-lyrics"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            // Kuromoji's two jars both ship the same notes files: keep one copy of each.
            pickFirsts += listOf("META-INF/CONTRIBUTORS.md", "META-INF/LICENSE.md", "META-INF/NOTICE.md")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.profileinstaller)
    // The Japanese dictionary (it reads kanji in context). Only the lyrics build has it.
    "withLyricsImplementation"(libs.kuromoji.ipadic)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}