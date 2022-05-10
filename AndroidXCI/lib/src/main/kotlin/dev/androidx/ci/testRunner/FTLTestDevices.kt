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

@file:Suppress("unused")

package dev.androidx.ci.testRunner

import dev.androidx.ci.generated.ftl.AndroidDevice

/**
 * Common devices that are known to exist in FTL
 */
// to regenerate, run [TestRunnerServicePlayground.buildCommonDevices]
object FTLTestDevices {
    val AMATITVEMULATOR_API_29_EMULATOR = AndroidDevice(
        id = "AmatiTvEmulator",
        sdk = "29"
    )
    val GOOGLETVEMULATOR_API_30_EMULATOR = AndroidDevice(
        id = "GoogleTvEmulator",
        sdk = "30"
    )
    val NEXUS10_API_19_VIRTUAL = AndroidDevice(
        id = "Nexus10",
        sdk = "19"
    )
    val NEXUS10_API_21_VIRTUAL = AndroidDevice(
        id = "Nexus10",
        sdk = "21"
    )
    val NEXUS10_API_22_VIRTUAL = AndroidDevice(
        id = "Nexus10",
        sdk = "22"
    )
    val NEXUS4_API_19_VIRTUAL = AndroidDevice(
        id = "Nexus4",
        sdk = "19"
    )
    val NEXUS4_API_21_VIRTUAL = AndroidDevice(
        id = "Nexus4",
        sdk = "21"
    )
    val NEXUS4_API_22_VIRTUAL = AndroidDevice(
        id = "Nexus4",
        sdk = "22"
    )
    val NEXUS5_API_19_VIRTUAL = AndroidDevice(
        id = "Nexus5",
        sdk = "19"
    )
    val NEXUS5_API_21_VIRTUAL = AndroidDevice(
        id = "Nexus5",
        sdk = "21"
    )
    val NEXUS5_API_22_VIRTUAL = AndroidDevice(
        id = "Nexus5",
        sdk = "22"
    )
    val NEXUS5_API_23_VIRTUAL = AndroidDevice(
        id = "Nexus5",
        sdk = "23"
    )
    val NEXUS5X_API_23_VIRTUAL = AndroidDevice(
        id = "Nexus5X",
        sdk = "23"
    )
    val NEXUS5X_API_24_VIRTUAL = AndroidDevice(
        id = "Nexus5X",
        sdk = "24"
    )
    val NEXUS5X_API_25_VIRTUAL = AndroidDevice(
        id = "Nexus5X",
        sdk = "25"
    )
    val NEXUS5X_API_26_VIRTUAL = AndroidDevice(
        id = "Nexus5X",
        sdk = "26"
    )
    val NEXUS6_API_21_VIRTUAL = AndroidDevice(
        id = "Nexus6",
        sdk = "21"
    )
    val NEXUS6_API_22_VIRTUAL = AndroidDevice(
        id = "Nexus6",
        sdk = "22"
    )
    val NEXUS6_API_23_VIRTUAL = AndroidDevice(
        id = "Nexus6",
        sdk = "23"
    )
    val NEXUS6_API_24_VIRTUAL = AndroidDevice(
        id = "Nexus6",
        sdk = "24"
    )
    val NEXUS6_API_25_VIRTUAL = AndroidDevice(
        id = "Nexus6",
        sdk = "25"
    )
    val NEXUS6P_API_23_VIRTUAL = AndroidDevice(
        id = "Nexus6P",
        sdk = "23"
    )
    val NEXUS6P_API_24_VIRTUAL = AndroidDevice(
        id = "Nexus6P",
        sdk = "24"
    )
    val NEXUS6P_API_25_VIRTUAL = AndroidDevice(
        id = "Nexus6P",
        sdk = "25"
    )
    val NEXUS6P_API_26_VIRTUAL = AndroidDevice(
        id = "Nexus6P",
        sdk = "26"
    )
    val NEXUS6P_API_27_VIRTUAL = AndroidDevice(
        id = "Nexus6P",
        sdk = "27"
    )
    val NEXUS7_API_19_VIRTUAL = AndroidDevice(
        id = "Nexus7",
        sdk = "19"
    )
    val NEXUS7_API_21_VIRTUAL = AndroidDevice(
        id = "Nexus7",
        sdk = "21"
    )
    val NEXUS7_API_22_VIRTUAL = AndroidDevice(
        id = "Nexus7",
        sdk = "22"
    )
    val NEXUS7_CLONE_16_9_API_23_VIRTUAL = AndroidDevice(
        id = "Nexus7_clone_16_9",
        sdk = "23"
    )
    val NEXUS7_CLONE_16_9_API_24_VIRTUAL = AndroidDevice(
        id = "Nexus7_clone_16_9",
        sdk = "24"
    )
    val NEXUS7_CLONE_16_9_API_25_VIRTUAL = AndroidDevice(
        id = "Nexus7_clone_16_9",
        sdk = "25"
    )
    val NEXUS7_CLONE_16_9_API_26_VIRTUAL = AndroidDevice(
        id = "Nexus7_clone_16_9",
        sdk = "26"
    )
    val NEXUS9_API_21_VIRTUAL = AndroidDevice(
        id = "Nexus9",
        sdk = "21"
    )
    val NEXUS9_API_22_VIRTUAL = AndroidDevice(
        id = "Nexus9",
        sdk = "22"
    )
    val NEXUS9_API_23_VIRTUAL = AndroidDevice(
        id = "Nexus9",
        sdk = "23"
    )
    val NEXUS9_API_24_VIRTUAL = AndroidDevice(
        id = "Nexus9",
        sdk = "24"
    )
    val NEXUS9_API_25_VIRTUAL = AndroidDevice(
        id = "Nexus9",
        sdk = "25"
    )
    val NEXUSLOWRES_API_23_VIRTUAL = AndroidDevice(
        id = "NexusLowRes",
        sdk = "23"
    )
    val NEXUSLOWRES_API_24_VIRTUAL = AndroidDevice(
        id = "NexusLowRes",
        sdk = "24"
    )
    val NEXUSLOWRES_API_25_VIRTUAL = AndroidDevice(
        id = "NexusLowRes",
        sdk = "25"
    )
    val NEXUSLOWRES_API_26_VIRTUAL = AndroidDevice(
        id = "NexusLowRes",
        sdk = "26"
    )
    val NEXUSLOWRES_API_27_VIRTUAL = AndroidDevice(
        id = "NexusLowRes",
        sdk = "27"
    )
    val NEXUSLOWRES_API_28_VIRTUAL = AndroidDevice(
        id = "NexusLowRes",
        sdk = "28"
    )
    val NEXUSLOWRES_API_29_VIRTUAL = AndroidDevice(
        id = "NexusLowRes",
        sdk = "29"
    )
    val NEXUSLOWRES_API_30_VIRTUAL = AndroidDevice(
        id = "NexusLowRes",
        sdk = "30"
    )
    val PIXEL2_API_26_VIRTUAL = AndroidDevice(
        id = "Pixel2",
        sdk = "26"
    )
    val PIXEL2_API_27_VIRTUAL = AndroidDevice(
        id = "Pixel2",
        sdk = "27"
    )
    val PIXEL2_API_28_VIRTUAL = AndroidDevice(
        id = "Pixel2",
        sdk = "28"
    )
    val PIXEL2_API_29_VIRTUAL = AndroidDevice(
        id = "Pixel2",
        sdk = "29"
    )
    val PIXEL2_API_30_VIRTUAL = AndroidDevice(
        id = "Pixel2",
        sdk = "30"
    )
    val PIXEL3_API_30_VIRTUAL = AndroidDevice(
        id = "Pixel3",
        sdk = "30"
    )
    val BLUELINE_API_28_PHYSICAL = AndroidDevice(
        id = "blueline",
        sdk = "28"
    )
    val ORIOLE_API_31_PHYSICAL = AndroidDevice(
        id = "oriole",
        sdk = "31"
    )
    val REDFIN_API_30_PHYSICAL = AndroidDevice(
        id = "redfin",
        sdk = "30"
    )
    val SAILFISH_API_25_PHYSICAL = AndroidDevice(
        id = "sailfish",
        sdk = "25"
    )
    val WALLEYE_API_27_PHYSICAL = AndroidDevice(
        id = "walleye",
        sdk = "27"
    )

    private fun AndroidDevice(id: String, sdk: String) = AndroidDevice(
        locale = "en",
        androidModelId = id,
        androidVersionId = sdk,
        orientation = "portrait"
    )
}
