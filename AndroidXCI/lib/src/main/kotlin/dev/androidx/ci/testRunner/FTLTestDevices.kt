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

/**
 * Common devices that are known to exist in FTL
 */
object FTLTestDevices {
    val REDFIN_API_30 = AndroidDevice(
        id = "redfin",
        sdk = "30"
    )
    val PIXEL6_31 = AndroidDevice(
        id = "oriole",
        sdk = "31"
    )
    val PIXEL_3 = AndroidDevice(
        id = "Pixel3",
        sdk = "30"
    )
    val SAILFISH = AndroidDevice(
        id = "sailfish",
        sdk = "25"
    )
    val WALLEYE = AndroidDevice(
        id = "walleye",
        sdk = "27"
    )

    val NEXUS5_19 = AndroidDevice(
        id = "Nexus5",
        sdk = "19"
    )



    private fun AndroidDevice(id:String, sdk:String) = AndroidDevice(
        locale = "en",
        androidModelId = id,
        androidVersionId = sdk,
        orientation = "portrait"
    )


    //    All Google Devices:
    //    AmatiTvEmulator / [29]
    //    GoogleTvEmulator / [30]
    //    Nexus6P / [23, 24, 25, 26, 27]
    //    Pixel2 / [26, 27, 28, 29, 30]
    //    Pixel2_Q / null
    //    Pixel3 / [30]
    //    blueline / [28]
    //    flame / null
    //    oriole / [31]
    //    redfin / [30]
    //    sailfish / [25]
    //    sargo / null
    //    taimen / null
    //    taimen_kddi / null
    //    walleye / [27]
    //    Nexus10 / [19, 21, 22]
    //    Nexus4 / [19, 21, 22]
    //    Nexus5 / [19, 21, 22, 23]
    //    Nexus7 / [19, 21, 22]
    //    flo / [19]
    //    lt02wifi / [19]


}