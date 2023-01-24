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

package dev.androidx.ci.testRunner.vo

import dev.androidx.ci.gcloud.GcsPath

/**
 * Wrapper for an APK that we upload to our GCS bucket.
 */
data class UploadedApk(
    override val gcsPath: GcsPath,
    val apkInfo: ApkInfo
): CloudApk

/**
 * Wrapper for an APK that comes from another GCS bucket.
 */
data class RemoteApk(
    override val gcsPath: GcsPath
): CloudApk
