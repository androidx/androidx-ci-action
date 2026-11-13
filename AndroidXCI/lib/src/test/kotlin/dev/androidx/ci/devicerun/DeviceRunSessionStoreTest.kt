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
import dev.androidx.ci.fake.FakeDatastore
import dev.androidx.ci.fake.FakeDeviceRunApi
import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.testRunner.vo.UploadedApk
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class DeviceRunSessionStoreTest {
    private val fakeApi = FakeDeviceRunApi()
    private val datastoreApi = FakeDatastore()
    private val controller = DeviceRunController(fakeApi, "test-project")
    private val store = DeviceRunSessionStore(
        datastoreApi = datastoreApi,
        deviceRunController = controller,
        resultsGcsPrefix = GcsPath("gs://results/prefix")
    )

    @Test
    fun testGetOrCreateSession_createsNewWhenEmpty() = runBlocking {
        val appApk = createUploadedApk("app.apk")
        val testApk = createUploadedApk("test.apk")
        val devices = listOf(AndroidDevice("MediumPhone.arm", "33", "en_US", "portrait"))

        val session = store.getOrCreateSession(
            appApk = appApk,
            testApk = testApk,
            devicePicker = { devices },
            clientInfo = null,
            sharding = null,
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            gcsSmartShardingFilePath = "gs://smartShardingFile"
        )

        assertThat(session.name).isNotNull()
        assertThat(fakeApi.getSessions()).hasSize(1)
    }

    @Test
    fun testGetOrCreateSession_reusesExistingWhenSameConfig() = runBlocking {
        val appApk = createUploadedApk("app.apk")
        val testApk = createUploadedApk("test.apk")
        val devices = listOf(AndroidDevice("MediumPhone.arm", "33", "en_US", "portrait"))

        val session1 = store.getOrCreateSession(
            appApk = appApk,
            testApk = testApk,
            devicePicker = { devices },
            clientInfo = null,
            sharding = null,
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            gcsSmartShardingFilePath = "gs://smartShardingFile"
        )

        val session2 = store.getOrCreateSession(
            appApk = appApk,
            testApk = testApk,
            devicePicker = { devices },
            clientInfo = null,
            sharding = null,
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            gcsSmartShardingFilePath = "gs://smartShardingFile"
        )

        assertThat(session1.name).isEqualTo(session2.name)
        // Should only have 1 session created in the fake backend
        assertThat(fakeApi.getSessions()).hasSize(1)
    }

    @Test
    fun testGetOrCreateSession_createsNewWhenConfigChanges() = runBlocking {
        val appApk = createUploadedApk("app.apk")
        val testApk = createUploadedApk("test.apk")
        val devices1 = listOf(AndroidDevice("MediumPhone.arm", "33", "en_US", "portrait"))
        val devices2 = listOf(AndroidDevice("tokay", "34", "en_US", "portrait")) // different device

        val session1 = store.getOrCreateSession(
            appApk = appApk,
            testApk = testApk,
            devicePicker = { devices1 },
            clientInfo = null,
            sharding = null,
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            gcsSmartShardingFilePath = "gs://smartShardingFile"
        )

        val session2 = store.getOrCreateSession(
            appApk = appApk,
            testApk = testApk,
            devicePicker = { devices2 },
            clientInfo = null,
            sharding = null,
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            gcsSmartShardingFilePath = "gs://smartShardingFile"
        )

        assertThat(session1.name).isNotEqualTo(session2.name)
        assertThat(fakeApi.getSessions()).hasSize(2)
    }
    @Test
    fun testGetOrCreateSession_createsNewWhenFilterRejects() = runBlocking {
        val appApk = createUploadedApk("app.apk")
        val testApk = createUploadedApk("test.apk")
        val devices = listOf(AndroidDevice("MediumPhone.arm", "33", "en_US", "portrait"))

        val session1 = store.getOrCreateSession(
            appApk = appApk,
            testApk = testApk,
            devicePicker = { devices },
            clientInfo = null,
            sharding = null,
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            gcsSmartShardingFilePath = "gs://smartShardingFile"
        )

        val session2 = store.getOrCreateSession(
            appApk = appApk,
            testApk = testApk,
            devicePicker = { devices },
            clientInfo = null,
            sharding = null,
            deviceSetup = null,
            pullScreenshots = false,
            testTargets = null,
            cachedSessionFilter = { false }, // Reject cache
            gcsSmartShardingFilePath = "gs://smartShardingFile"
        )

        assertThat(session1.name).isNotEqualTo(session2.name)
        assertThat(fakeApi.getSessions()).hasSize(2)
    }

    private fun createUploadedApk(name: String) = UploadedApk(
        gcsPath = GcsPath("gs://apk/$name"),
        apkInfo = ApkInfo(filePath = "files/$name", idHash = name)
    )
}
