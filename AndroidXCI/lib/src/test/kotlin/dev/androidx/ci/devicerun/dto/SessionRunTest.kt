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

package dev.androidx.ci.devicerun.dto

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeDatastore
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.AndroidDeviceList
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.testRunner.vo.DeviceSetup
import org.junit.Test

internal class SessionRunTest {
    private val datastoreApi = FakeDatastore()
    private val baseEnvironment = EnvironmentMatrix(
        androidDeviceList = AndroidDeviceList(
            androidDevices = listOf(AndroidDevice("MediumPhone.arm", "33", "en_US", "portrait"))
        )
    )
    private val baseAppApk = ApkInfo(
        filePath = "app.apk",
        idHash = "hash1"
    )
    private val baseTestApk = ApkInfo(
        filePath = "test.apk",
        idHash = "hash2"
    )

    @Test
    fun testCreateId_stability() {
        val id1 = SessionRun.createId(
            datastoreApi = datastoreApi,
            environment = baseEnvironment,
            clientInfo = null,
            appApk = baseAppApk,
            testApk = baseTestApk,
            deviceSetup = null,
            sharding = null,
            orchestratorOption = null
        )

        val id2 = SessionRun.createId(
            datastoreApi = datastoreApi,
            environment = baseEnvironment,
            clientInfo = null,
            appApk = baseAppApk,
            testApk = baseTestApk,
            deviceSetup = null,
            sharding = null,
            orchestratorOption = null
        )

        assertThat(id1.key.name).isEqualTo(id2.key.name)
    }

    @Test
    fun testCreateId_differentEnvironment() {
        val id1 = SessionRun.createId(
            datastoreApi = datastoreApi,
            environment = baseEnvironment,
            clientInfo = null,
            appApk = baseAppApk,
            testApk = baseTestApk,
            deviceSetup = null,
            sharding = null,
            orchestratorOption = null
        )

        val diffEnvironment = EnvironmentMatrix(
            androidDeviceList = AndroidDeviceList(
                androidDevices = listOf(AndroidDevice("SmallPhone.arm", "34", "en_US", "portrait"))
            )
        )
        val id2 = SessionRun.createId(
            datastoreApi = datastoreApi,
            environment = diffEnvironment,
            clientInfo = null,
            appApk = baseAppApk,
            testApk = baseTestApk,
            deviceSetup = null,
            sharding = null,
            orchestratorOption = null
        )

        assertThat(id1.key.name).isNotEqualTo(id2.key.name)
    }

    @Test
    fun testCreateId_differentApk() {
        val id1 = SessionRun.createId(
            datastoreApi = datastoreApi,
            environment = baseEnvironment,
            clientInfo = null,
            appApk = baseAppApk,
            testApk = baseTestApk,
            deviceSetup = null,
            sharding = null,
            orchestratorOption = null
        )

        val diffAppApk = ApkInfo(
            filePath = "app2.apk",
            idHash = "hash3"
        )
        val id2 = SessionRun.createId(
            datastoreApi = datastoreApi,
            environment = baseEnvironment,
            clientInfo = null,
            appApk = diffAppApk,
            testApk = baseTestApk,
            deviceSetup = null,
            sharding = null,
            orchestratorOption = null
        )

        assertThat(id1.key.name).isNotEqualTo(id2.key.name)
    }

    @Test
    fun testCreateId_differentSetup() {
        val id1 = SessionRun.createId(
            datastoreApi = datastoreApi,
            environment = baseEnvironment,
            clientInfo = null,
            appApk = baseAppApk,
            testApk = baseTestApk,
            deviceSetup = null,
            sharding = null,
            orchestratorOption = null
        )

        val deviceSetup = DeviceSetup(
            instrumentationArguments = listOf(DeviceSetup.InstrumentationArgument("key", "val"))
        )
        val id2 = SessionRun.createId(
            datastoreApi = datastoreApi,
            environment = baseEnvironment,
            clientInfo = null,
            appApk = baseAppApk,
            testApk = baseTestApk,
            deviceSetup = deviceSetup,
            sharding = null,
            orchestratorOption = null
        )

        assertThat(id1.key.name).isNotEqualTo(id2.key.name)
    }

    @Test
    fun testEntityConversion() {
        val id = SessionRun.createId(
            datastoreApi = datastoreApi,
            environment = baseEnvironment,
            clientInfo = null,
            appApk = baseAppApk,
            testApk = baseTestApk,
            deviceSetup = null,
            sharding = null,
            orchestratorOption = null
        )
        val originalSession = SessionRun(
            id = id,
            sessionId = "session-12345"
        )

        val entity = originalSession.toEntity()
        val convertedSession = entity.toSessionRun()

        assertThat(convertedSession).isNotNull()
        assertThat(convertedSession!!.id.key.name).isEqualTo(originalSession.id.key.name)
        assertThat(convertedSession.sessionId).isEqualTo(originalSession.sessionId)
    }
}
