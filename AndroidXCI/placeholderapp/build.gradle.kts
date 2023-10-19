/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.androidApplication)
}

android {
    compileSdk = 33

    defaultConfig {
        namespace = "dev.androidx.ci.placeholderapp"
        minSdk = 14
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        // Makes APK smaller
        buildConfig = false
    }
    signingConfigs {
        getByName("debug") {
            // Make sure that we sign with the same checked in keystore, so that this APK is always identical.
            storeFile = File(projectDir, "debug.keystore")
        }
    }
}
