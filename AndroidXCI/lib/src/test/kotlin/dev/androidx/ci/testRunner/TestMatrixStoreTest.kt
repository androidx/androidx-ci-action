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

package dev.androidx.ci.testRunner

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeDatastore
import dev.androidx.ci.fake.FakeFirebaseTestLabApi
import dev.androidx.ci.fake.FakeToolsResultApi
import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.AndroidDeviceList
import dev.androidx.ci.generated.ftl.ClientInfo
import dev.androidx.ci.generated.ftl.ClientInfoDetail
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.FileReference
import dev.androidx.ci.generated.ftl.ShardingOption
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.ftl.UniformSharding
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.testRunner.vo.DeviceSetup
import dev.androidx.ci.testRunner.vo.UploadedApk
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class TestMatrixStoreTest {
    val firebaseTestLabApi = FakeFirebaseTestLabApi()
    val datastoreApi = FakeDatastore()
    val toolsResultApi = FakeToolsResultApi()
    private val store = TestMatrixStore(
        firebaseProjectId = "p1",
        firebaseTestLabApi = firebaseTestLabApi,
        datastoreApi = datastoreApi,
        toolsResultApi = toolsResultApi,
        resultsGcsPrefix = GcsPath("gs://test")
    )

    private val envMatrix1 = EnvironmentMatrix(
        androidDeviceList = AndroidDeviceList(
            androidDevices = listOf(
                AndroidDevice(
                    orientation = "land",
                    androidVersionId = "27",
                    locale = "us",
                    androidModelId = "model1"
                )
            )
        )
    )
    private val envMatrix2 = EnvironmentMatrix(
        androidDeviceList = AndroidDeviceList(
            androidDevices = listOf(
                AndroidDevice(
                    orientation = "port",
                    androidVersionId = "27",
                    locale = "us",
                    androidModelId = "model1"
                )
            )
        )
    )

    @Test
    fun create() = runBlocking<Unit> {
        val appApk = createFakeApk("app.pak")
        val testApk = createFakeApk("test.apk")
        val extraApk = createFakeApk("extra.apk")
        val clientInfo = ClientInfo(
            name = "test",
            clientInfoDetails = listOf(
                ClientInfoDetail(
                    "key1", "value1"
                )
            )
        )
        val sharding = ShardingOption(
            uniformSharding = UniformSharding(3)
        )
        val deviceSetup = DeviceSetup(
            additionalApks = listOf(extraApk),
            directoriesToPull = mutableListOf("/sdcard/foo/bar"),
            instrumentationArguments = listOf(
                DeviceSetup.InstrumentationArgument(
                    key = "foo",
                    value = "bar"
                )
            )
        )
        val testMatrix = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1,
            clientInfo = clientInfo,
            deviceSetup = deviceSetup,
            sharding = sharding
        )

        assertThat(firebaseTestLabApi.getTestMatrices()).hasSize(1)
        val matrix = firebaseTestLabApi.getTestMatrices().first()
        matrix.testSpecification.androidInstrumentationTest!!.let {
            assertThat(it.appApk).isEqualTo(
                FileReference(gcsPath = appApk.gcsPath.path)
            )
            assertThat(it.testApk).isEqualTo(
                FileReference(gcsPath = testApk.gcsPath.path)
            )
            assertThat(it.shardingOption).isEqualTo(
                ShardingOption(uniformSharding = UniformSharding(3))
            )
        }
        matrix.testSpecification.let {
            assertThat(
                it.testSetup?.additionalApks?.singleOrNull()?.location
            ).isEqualTo(
                FileReference(
                    gcsPath = extraApk.gcsPath.path
                )
            )
            assertThat(
                it.testSetup?.directoriesToPull
            ).containsExactlyElementsIn(deviceSetup.directoriesToPull)
            assertThat(
                it.testSetup?.environmentVariables
            ).containsExactlyElementsIn(
                deviceSetup.instrumentationArguments?.map {
                    it.toEnvironmentVariable()
                }
            )
        }
        assertThat(matrix.environmentMatrix).isEqualTo(envMatrix1)
        assertThat(matrix.clientInfo).isEqualTo(clientInfo)

        // upload again, should not upload but instead return the same matrix
        val reUploaded = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1,
            clientInfo = clientInfo,
            deviceSetup = deviceSetup,
            sharding = sharding
        )
        assertThat(reUploaded).isEqualTo(testMatrix)

        val withoutClientInfo = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1,
            clientInfo = null,
            deviceSetup = deviceSetup,
            sharding = sharding
        )
        // client info change should be considered as a new test
        assertThat(
            withoutClientInfo.testMatrixId
        ).isNotEqualTo(testMatrix.testMatrixId)

        val withoutSharding = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1,
            clientInfo = clientInfo,
            deviceSetup = deviceSetup,
            sharding = null
        )
        // new sharding option should be a new test matrix
        assertThat(
            withoutSharding.testMatrixId
        ).isNotEqualTo(testMatrix.testMatrixId)

        // upload w/ a new environment, should create a new test matrix
        val newEnvironment = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix2,
            clientInfo = clientInfo,
            deviceSetup = deviceSetup,
            sharding = sharding
        )
        assertThat(newEnvironment.testMatrixId).isNotEqualTo(testMatrix.testMatrixId)
        val app2 = createFakeApk("app2.apk")
        val newApp = store.getOrCreateTestMatrix(
            appApk = app2,
            testApk = testApk,
            environmentMatrix = envMatrix1,
            clientInfo = clientInfo,
            deviceSetup = deviceSetup,
            sharding = sharding
        )
        // should be a new one since app apk changed
        assertThat(newApp.testMatrixId).isNotEqualTo(testMatrix.testMatrixId)

        // make sure each submission got a unique result path
        val resultPaths = listOf(testMatrix, newApp, newEnvironment).map {
            it.resultStorage.googleCloudStorage.gcsPath
        }
        assertThat(resultPaths.distinct()).hasSize(3)
        firebaseTestLabApi.deleteTestMatrix(testMatrix.testMatrixId!!)
        // now re-upload it, it is in FireStore but not on FTL so we should create a new one
        // upload again, should not upload but instead return the same matrix
        val reUploadedAfterDeletion = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1,
            clientInfo = clientInfo,
            deviceSetup = deviceSetup,
            sharding = sharding
        )
        assertThat(reUploadedAfterDeletion.testMatrixId).isNotEqualTo(testMatrix.testMatrixId)
    }

    @Test
    fun dontReuseTestMatricesWithInfraFailures() = runBlocking<Unit> {
        val appApk = createFakeApk("app.pak")
        val testApk = createFakeApk("test.apk")
        val extraApk = createFakeApk("extra.apk")
        val testMatrix = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1,
            clientInfo = null,
            deviceSetup = null,
            sharding = null
        )
        val reload = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1,
            clientInfo = null,
            deviceSetup = null,
            sharding = null
        )
        assertThat(testMatrix.testMatrixId).isEqualTo(reload.testMatrixId)
        // set it to failed.
        firebaseTestLabApi.setTestMatrix(
            testMatrix.copy(
                state = TestMatrix.State.ERROR
            )
        )
        val reloadAfterError = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1,
            clientInfo = null,
            deviceSetup = null,
            sharding = null
        )
        assertThat(reloadAfterError.testMatrixId).isNotEqualTo(testMatrix.testMatrixId)
    }

    @Test
    fun pullScreenshots() = runBlocking<Unit> {
        val appApk = createFakeApk("app.pak")
        val testApk = createFakeApk("test.apk")
        val testPackageName = firebaseTestLabApi.getApkDetails(
            FileReference(testApk.gcsPath.path)
        ).apkDetail?.apkManifest?.packageName
        val deviceSetup = DeviceSetup(
            directoriesToPull = mutableListOf("/sdcard/foo1/bar1")
        )
        store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1,
            clientInfo = null,
            deviceSetup = deviceSetup,
            sharding = null,
            pullScreenshots = true
        )

        assertThat(firebaseTestLabApi.getTestMatrices()).hasSize(1)
        val matrix = firebaseTestLabApi.getTestMatrices().first()
        matrix.testSpecification.let {
            assertThat(
                it.testSetup?.directoriesToPull
            ).containsAtLeastElementsIn(deviceSetup.directoriesToPull)
            assertThat(
                it.testSetup?.directoriesToPull
            ).contains("/sdcard/Android/data/$testPackageName/cache/androidx_screenshots")
        }
    }

    @Test
    fun pullScreenshotsWithoutDeviceSetup() = runBlocking<Unit> {
        val appApk = createFakeApk("app.pak")
        val testApk = createFakeApk("test.apk")
        val testPackageName = firebaseTestLabApi.getApkDetails(
            FileReference(testApk.gcsPath.path)
        ).apkDetail?.apkManifest?.packageName
        store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1,
            clientInfo = null,
            deviceSetup = null,
            sharding = null,
            pullScreenshots = true
        )

        assertThat(firebaseTestLabApi.getTestMatrices()).hasSize(1)
        val matrix = firebaseTestLabApi.getTestMatrices().first()
        matrix.testSpecification.let {
            assertThat(
                it.testSetup?.directoriesToPull
            ).containsExactly("/sdcard/Android/data/$testPackageName/cache/androidx_screenshots")
        }
    }
    private fun createFakeApk(name: String) = UploadedApk(
        gcsPath = GcsPath("gs://foo/bar/$name"),
        apkInfo = ApkInfo(
            filePath = "foo/bar/$name",
            idHash = name
        )
    )
}
