/*
 * Copyright 2022 The Android Open Source Project
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

package dev.androidx.ci.testRunner

import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.TestEnvironmentCatalog

/**
 * Helper function that can pick [AndroidDevice]s based on a [TestEnvironmentCatalog].
 * Often times, you can use the [FTLTestDevices] but if you want to have a more generic selection
 * logic (e.g. pick certain sdk levels), you can implement this interface.
 */
typealias DevicePicker = (TestEnvironmentCatalog) -> List<AndroidDevice>
