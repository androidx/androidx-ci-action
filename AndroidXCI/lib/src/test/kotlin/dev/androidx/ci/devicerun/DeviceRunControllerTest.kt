/*
 * Copyright 2024 The Android Open Source Project
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
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.testRunner.vo.DeviceSetup
import dev.androidx.ci.testRunner.vo.UploadedApk
import dev.androidx.ci.gcloud.GcsPath
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class DeviceRunControllerTest {
    private val fakeApi = FakeDeviceRunApi()
    private val controller = DeviceRunController(fakeApi, "test-project")

    @Test
    fun testSubmitTests_localeTranslationAndDeviceSlug() = runBlocking<Unit> {
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
            devices = devices,
            sharding = null,
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            flakyTestAttempts = 0,
            testTimeoutSeconds = 900,
            gcsResultPath = "gs://results"
        )

        assertThat(session.sessionConfig?.jobConfigs).hasSize(2)
        
        val job1 = session.sessionConfig?.jobConfigs?.get(0)
        val job2 = session.sessionConfig?.jobConfigs?.get(1)
        
        // Assert Device Slugging
        val deviceConfig1 = job1?.allocationConfig?.deviceConfigs?.first()
        assertThat(deviceConfig1?.requirement?.deviceId).isEqualTo("mediumphone-arm-33")
        
        val deviceConfig2 = job2?.allocationConfig?.deviceConfigs?.first()
        assertThat(deviceConfig2?.requirement?.deviceId).isEqualTo("tokay-34")

        // Assert Locale Mapping and BCP47
        val switchLocale1 = deviceConfig1?.actions?.mapNotNull { it.androidSwitchLocale }?.firstOrNull()
        assertThat(switchLocale1?.localeCode).isEqualTo("en-US")
        
        val switchLocale2 = deviceConfig2?.actions?.mapNotNull { it.androidSwitchLocale }?.firstOrNull()
        assertThat(switchLocale2?.localeCode).isEqualTo("zh-CN")

        // Assert Orientation
        val orient1 = deviceConfig1?.actions?.mapNotNull { it.androidOrientation }?.firstOrNull()
        assertThat(orient1?.orientation).isEqualTo("portrait")
        val orient2 = deviceConfig2?.actions?.mapNotNull { it.androidOrientation }?.firstOrNull()
        assertThat(orient2?.orientation).isEqualTo("landscape")

        // Assert logcat and bugreport actions present
        assertThat(deviceConfig1?.actions?.mapNotNull { it.androidLogcat }).isNotEmpty()
        assertThat(deviceConfig1?.actions?.mapNotNull { it.androidBugreport }).isNotEmpty()
    }

    @Test
    fun testSubmitTests_extraOptionsMovedToTestTargets() = runBlocking<Unit> {
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
            devices = devices,
            sharding = null,
            deviceSetup = deviceSetup,
            pullScreenshots = false,
            testTargets = listOf("package com.example.foo"),
            flakyTestAttempts = 0,
            testTimeoutSeconds = 900,
            gcsResultPath = "gs://results"
        )

        val job = session.sessionConfig?.jobConfigs?.first()
        val instrumentationTest = job?.action?.androidInstrumentationTest

        // Verify test targets received the filtered options
        val testTargets = instrumentationTest?.testTargets ?: emptyList<String>()
        val targetsSet = testTargets.toSet()
        assertThat(targetsSet.contains("package com.example.foo")).isTrue()
        assertThat(targetsSet.contains("class com.example.TestClass")).isTrue()
        assertThat(targetsSet.contains("notAnnotation androidx.test.filters.FlakyTest")).isTrue()
        assertThat(testTargets.size).isEqualTo(3)

        // Verify non-filter keys remained in extra options along with implicit defaults
        val additionalTestOptions = instrumentationTest?.additionalTestOptions ?: emptyList<String>()
        val optionsSet = additionalTestOptions as List<String>
        assertThat(optionsSet.contains("disable_window_animations=true")).isTrue()
        assertThat(optionsSet.contains("firebase.test.lab=true")).isTrue()
        assertThat(optionsSet.contains("customKey=customValue")).isTrue()
        assertThat(additionalTestOptions.size).isEqualTo(3)
    }

    @Test
    fun testSubmitTests_retryMapping() = runBlocking<Unit> {
        val appApk = createUploadedApk("app.apk")
        val testApk = createUploadedApk("test.apk")
        val devices = listOf(AndroidDevice("tokay", "34", "en_US", "portrait"))

        val session = controller.submitTests(
            appApk = appApk,
            testApk = testApk,
            devices = devices,
            sharding = null,
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            flakyTestAttempts = 2,
            testTimeoutSeconds = 900,
            gcsResultPath = "gs://results"
        )

        val job = session.sessionConfig?.jobConfigs?.first()
        val flakyAttempts = job?.settings?.retrySettings?.flakyTestRetryStrategy?.flakyTestAttempts
        
        // FTL 2 retries -> DDP 3 total attempts
        assertThat(flakyAttempts).isEqualTo(3)
    }

    @Test
    fun testSubmitTests_jobLabels() = runBlocking<Unit> {
        val appApk = createUploadedApk("app.apk")
        val testApk = createUploadedApk("test.apk")
        val devices = listOf(
            AndroidDevice("tokay", "34", "en_US", "portrait"),
            AndroidDevice("oriole", "31", "en_US", "portrait")
        )

        val session = controller.submitTests(
            appApk = appApk,
            testApk = testApk,
            devices = devices,
            sharding = null,
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            flakyTestAttempts = 0,
            testTimeoutSeconds = 900,
            gcsResultPath = "gs://results",
            jobLabels = { device ->
                if (device.androidModelId == "tokay") {
                    mapOf("ftl_matrix_id" to "tokay-matrix")
                } else {
                    emptyMap()
                }
            }
        )

        val jobConfigs = session.sessionConfig.jobConfigs
        assertThat(jobConfigs).hasSize(2)
        assertThat(jobConfigs[0].labels).isEqualTo(mapOf("ftl_matrix_id" to "tokay-matrix"))
        // No labels rather than an empty map
        assertThat(jobConfigs[1].labels).isNull()
    }

    @Test
    fun testSubmitTests_noJobLabelsByDefault() = runBlocking<Unit> {
        val session = controller.submitTests(
            appApk = createUploadedApk("app.apk"),
            testApk = createUploadedApk("test.apk"),
            devices = listOf(AndroidDevice("tokay", "34", "en_US", "portrait")),
            sharding = null,
            deviceSetup = null,
            gcsResultPath = "gs://results"
        )

        assertThat(session.sessionConfig.jobConfigs.single().labels).isNull()
    }

    private fun createUploadedApk(name: String) = UploadedApk(
        gcsPath = GcsPath("gs://apk/$name"),
        apkInfo = ApkInfo(filePath = "files/$name", idHash = name)
    )
}