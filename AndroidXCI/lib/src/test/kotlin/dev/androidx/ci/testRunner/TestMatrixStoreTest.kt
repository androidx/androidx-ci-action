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
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.FileReference
import dev.androidx.ci.testRunner.vo.ApkInfo
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
        val testMatrix = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1
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
        }
        assertThat(matrix.environmentMatrix).isEqualTo(envMatrix1)

        // upload again, should not upload but instead return the same matrix
        val reUploaded = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix1
        )
        assertThat(reUploaded).isEqualTo(testMatrix)
        // upload w/ a new environment, should create a new test matrix
        val newEnvironment = store.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = envMatrix2
        )
        assertThat(newEnvironment.testMatrixId).isNotEqualTo(testMatrix.testMatrixId)
        val app2 = createFakeApk("app2.apk")
        val newApp = store.getOrCreateTestMatrix(
            appApk = app2,
            testApk = testApk,
            environmentMatrix = envMatrix1
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
            environmentMatrix = envMatrix1
        )
        assertThat(reUploadedAfterDeletion.testMatrixId).isNotEqualTo(testMatrix.testMatrixId)
    }

    private fun createFakeApk(name: String) = UploadedApk(
        gcsPath = GcsPath("gs://foo/bar/$name"),
        apkInfo = ApkInfo(
            filePath = "foo/bar/$name",
            idHash = name
        )
    )
}
