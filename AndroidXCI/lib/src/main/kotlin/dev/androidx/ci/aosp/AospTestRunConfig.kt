/*
 * Copyright 2023 The Android Open Source Project
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

package dev.androidx.ci.aosp

data class AospTestRunConfig(
    val name: String,
    val testApk: String,
    val testApkSha256: String,
    val appApk: String?,
    val appApkSha256: String?,
    val minSdkVersion: Int,
    val instrumentationArgs: List<AospInstrumentationArg>,
    val testSuiteTags: List<String>,
    val additionalApkKeys: List<String>
)

data class AospInstrumentationArg(
    val key: String,
    val value: String
)
