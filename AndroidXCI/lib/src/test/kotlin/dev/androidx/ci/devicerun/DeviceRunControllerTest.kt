/*
 * Copyright 2026 The Android Open Source Project
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

package dev.androidx.ci.devicerun

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeDeviceRunApi
import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.generated.ddp.CatalogAndroidDeviceDetails
import dev.androidx.ci.generated.ddp.CatalogDevice
import dev.androidx.ci.generated.ddp.CatalogListDevicesResponse
import dev.androidx.ci.generated.ddp.CatalogScreenMetrics
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.AndroidModel
import dev.androidx.ci.testRunner.DevicePicker
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.testRunner.vo.DeviceSetup
import dev.androidx.ci.testRunner.vo.UploadedApk
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class DeviceRunControllerTest {
    private val fakeApi = FakeDeviceRunApi()
    private val controller = DeviceRunController(fakeApi, "test-project")

    @Test
    fun testSubmitTests_localeTranslationAndDeviceSlug() = runBlocking {
        val appApk = createUploadedApk("app.apk")
        val testApk = createUploadedApk("test.apk")
        val devices = listOf(
            AndroidDevice(
                androidModelId = "MediumPhone.arm",
                androidVersionId = "33",
                locale = "en_US",
                orientation = "portrait"
            ),
            AndroidDevice(
                androidModelId = "tokay",
                androidVersionId = "34",
                locale = "zh_CN",
                orientation = "landscape"
            )
        )

        val session = controller.submitTests(
            appApk = appApk,
            testApk = testApk,
            devicePicker = { devices },
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            flakyTestAttempts = 0,
            testTimeoutSeconds = 900,
            gcsResultPath = "gs://results",
            gcsSmartShardingFilePath = "gs://smartShardingFile"
        )

        assertThat(session.sessionConfig.jobConfigs).hasSize(2)

        val job1 = session.sessionConfig.jobConfigs[0]
        val job2 = session.sessionConfig.jobConfigs[1]

        // Assert Device Slugging
        val deviceConfig1 = job1.allocationConfig.deviceConfigs.first()
        assertThat(deviceConfig1.requirement.deviceId).isEqualTo("mediumphone-arm-33")

        val deviceConfig2 = job2.allocationConfig.deviceConfigs.first()
        assertThat(deviceConfig2.requirement.deviceId).isEqualTo("tokay-34")

        // Assert Locale Mapping and BCP47
        val switchLocale1 = deviceConfig1.actions?.firstNotNullOfOrNull { it.androidSwitchLocale }
        assertThat(switchLocale1?.localeCode).isEqualTo("en-US")

        val switchLocale2 = deviceConfig2.actions?.firstNotNullOfOrNull { it.androidSwitchLocale }
        assertThat(switchLocale2?.localeCode).isEqualTo("zh-CN")

        // Assert Orientation
        val orient1 = deviceConfig1.actions?.firstNotNullOfOrNull { it.androidOrientation }
        assertThat(orient1?.orientation).isEqualTo("portrait")
        val orient2 = deviceConfig2.actions?.firstNotNullOfOrNull { it.androidOrientation }
        assertThat(orient2?.orientation).isEqualTo("landscape")

        // Assert logcat and bugreport actions present
        assertThat(deviceConfig1.actions?.mapNotNull { it.androidLogcat }).isNotEmpty()
        assertThat(deviceConfig1.actions?.mapNotNull { it.androidBugreport }).isNotEmpty()
    }

    @Test
    fun testSubmitTests_extraOptionsMovedToTestTargets() = runBlocking {
        val appApk = createUploadedApk("app.apk")
        val testApk = createUploadedApk("test.apk")
        val devices = listOf(AndroidDevice("tokay", "34", "en_US", "portrait"))

        val deviceSetup = DeviceSetup(
            instrumentationArguments = listOf(
                DeviceSetup.InstrumentationArgument("class", "com.example.TestClass"),
                DeviceSetup.InstrumentationArgument("notAnnotation", "androidx.test.filters.FlakyTest"),
                DeviceSetup.InstrumentationArgument("customKey", "customValue")
            )
        )

        val session = controller.submitTests(
            appApk = appApk,
            testApk = testApk,
            devicePicker = { devices },
            deviceSetup = deviceSetup,
            pullScreenshots = false,
            testTargets = listOf("package com.example.foo"),
            flakyTestAttempts = 0,
            testTimeoutSeconds = 900,
            gcsResultPath = "gs://results",
            gcsSmartShardingFilePath = "gs://smartShardingFile"
        )

        val job = session.sessionConfig.jobConfigs.first()
        val instrumentationTest = job.action.androidInstrumentationTest

        // Verify test targets received the filtered options
        val testTargets = instrumentationTest?.testTargets ?: emptyList()
        val targetsSet = testTargets.toSet()
        assertThat(targetsSet.contains("package com.example.foo")).isTrue()
        assertThat(targetsSet.contains("class com.example.TestClass")).isTrue()
        assertThat(targetsSet.contains("notAnnotation androidx.test.filters.FlakyTest")).isTrue()
        assertThat(testTargets.size).isEqualTo(3)

        // Verify non-filter keys remained in extra options along with implicit defaults
        val additionalTestOptions = instrumentationTest?.additionalTestOptions ?: emptyList<String>()
        val optionsSet = additionalTestOptions as List<*>
        assertThat(optionsSet.contains("disable_window_animations=true")).isTrue()
        assertThat(optionsSet.contains("firebase.test.lab=true")).isTrue()
        assertThat(optionsSet.contains("customKey=customValue")).isTrue()
        assertThat(additionalTestOptions.size).isEqualTo(3)
    }

    @Test
    fun testSubmitTests_retryMapping() = runBlocking {
        val appApk = createUploadedApk("app.apk")
        val testApk = createUploadedApk("test.apk")
        val devices = listOf(AndroidDevice("tokay", "34", "en_US", "portrait"))

        val session = controller.submitTests(
            appApk = appApk,
            testApk = testApk,
            devicePicker = { devices },
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            flakyTestAttempts = 2,
            testTimeoutSeconds = 900,
            gcsResultPath = "gs://results",
            gcsSmartShardingFilePath = "gs://smartShardingFile"
        )

        val job = session.sessionConfig.jobConfigs.first()
        val flakyAttempts = job.settings?.retrySettings?.flakyTestRetryStrategy?.flakyTestAttempts

        // FTL 2 retries -> DDP 3 total attempts
        assertThat(flakyAttempts).isEqualTo(3)
    }

    @Test
    fun testSubmitTests_withDevicePicker() = runBlocking {
        val fakeCatalog = CatalogListDevicesResponse(
            devices = listOf(
                CatalogDevice(
                    modelCode = "MediumPhone.arm",
                    osVersion = "33",
                    displayName = "Medium Phone",
                    platform = CatalogDevice.Platform.ANDROID,
                    labels = listOf("default")
                ),
                CatalogDevice(
                    modelCode = "tokay",
                    osVersion = "34",
                    displayName = "Pixel 9",
                    platform = CatalogDevice.Platform.ANDROID
                )
            )
        )
        fakeApi.setDevicesResponse(fakeCatalog)

        val appApk = createUploadedApk("app.apk")
        val testApk = createUploadedApk("test.apk")
        val picker: DevicePicker = { catalog ->
            val model = catalog.androidDeviceCatalog?.models?.first { it.id == "tokay" }!!
            listOf(
                AndroidDevice(
                    androidModelId = model.id,
                    androidVersionId = model.supportedVersionIds!!.first(),
                    locale = "en_US",
                    orientation = "portrait"
                )
            )
        }

        val session = controller.submitTests(
            appApk = appApk,
            testApk = testApk,
            devicePicker = picker,
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            flakyTestAttempts = 0,
            testTimeoutSeconds = 900,
            gcsResultPath = "gs://results",
            gcsSmartShardingFilePath = "gs://smartShardingFile"
        )

        assertThat(session.sessionConfig.jobConfigs).hasSize(1)
        val job = session.sessionConfig.jobConfigs.first()
        val deviceConfig = job.allocationConfig.deviceConfigs.first()
        assertThat(deviceConfig.requirement.deviceId).isEqualTo("tokay-34")
    }

    @Test
    fun testDefaultEnvironment() = runBlocking {
        val fakeCatalog = CatalogListDevicesResponse(
            devices = listOf(
                CatalogDevice(
                    modelCode = "MediumPhone.arm",
                    osVersion = "33",
                    displayName = "Medium Phone",
                    platform = CatalogDevice.Platform.ANDROID,
                    labels = listOf("default")
                )
            )
        )
        fakeApi.setDevicesResponse(fakeCatalog)

        val defaultEnv = controller.getDefaultEnvironmentMatrix()
        val defaultDevice = defaultEnv.androidDeviceList?.androidDevices?.first()
        assertThat(defaultDevice?.androidModelId).isEqualTo("MediumPhone.arm")
        assertThat(defaultDevice?.androidVersionId).isEqualTo("33")
    }

    @Test
    fun testCatalogConversion_mapsDdpDevicesToFtlCatalog() = runBlocking {
        val ddpResponse = CatalogListDevicesResponse(
            devices = listOf(
                CatalogDevice(
                    modelCode = "MediumPhone.arm",
                    osVersion = "33",
                    displayName = "Medium Phone 33",
                    manufacturer = "Google",
                    platform = CatalogDevice.Platform.ANDROID,
                    hardwareType = CatalogDevice.HardwareType.VIRTUAL,
                    formFactor = CatalogDevice.FormFactor.PHONE,
                    androidDetails = CatalogAndroidDeviceDetails(supportedAbis = listOf("arm64-v8a")),
                    primaryScreen = CatalogScreenMetrics(densityDpi = 420, widthPx = 1080, heightPx = 2400),
                    labels = listOf("default")
                ),
                CatalogDevice(
                    modelCode = "MediumPhone.arm",
                    osVersion = "34",
                    displayName = "Medium Phone 34",
                    manufacturer = "Google",
                    platform = CatalogDevice.Platform.ANDROID,
                    hardwareType = CatalogDevice.HardwareType.VIRTUAL,
                    formFactor = CatalogDevice.FormFactor.PHONE
                ),
                CatalogDevice(
                    modelCode = "iphone14pro",
                    osVersion = "17.4",
                    platform = CatalogDevice.Platform.IOS
                )
            )
        )

        with(controller) {
            val catalog = ddpResponse.toTestEnvironmentCatalog()
            val models = catalog.androidDeviceCatalog?.models
            assertThat(models).hasSize(1)

            val model = models!!.first()
            assertThat(model.id).isEqualTo("MediumPhone.arm")
            assertThat(model.supportedVersionIds).containsExactly("33", "34")
            assertThat(model.manufacturer).isEqualTo("Google")
            assertThat(model.form).isEqualTo(AndroidModel.Form.VIRTUAL)
            assertThat(model.formFactor).isEqualTo(AndroidModel.FormFactor.PHONE)
            assertThat(model.tags).contains("default")
            assertThat(model.supportedAbis).containsExactly("arm64-v8a")
            assertThat(model.screenDensity).isEqualTo(420)
            assertThat(model.screenX).isEqualTo(1080)
            assertThat(model.screenY).isEqualTo(2400)
        }
    }

    private fun createUploadedApk(name: String) = UploadedApk(
        gcsPath = GcsPath("gs://apk/$name"),
        apkInfo = ApkInfo(filePath = "files/$name", idHash = name)
    )
}
