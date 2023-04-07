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
import dev.androidx.ci.generated.testResults.Step
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.testRunner.vo.UploadedApk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID

@RunWith(JUnit4::class)
internal class TestExecutionStoreTest {
    private val firebaseTestLabApi = FakeFirebaseTestLabApi()
    private val datastoreApi = FakeDatastore()
    private val toolsResultApi = FakeToolsResultApi()

    private val store = TestMatrixStore(
        firebaseProjectId = "p1",
        firebaseTestLabApi = firebaseTestLabApi,
        datastoreApi = datastoreApi,
        toolsResultApi = toolsResultApi,
        resultsGcsPrefix = GcsPath("gs://test")
    )

    private val testExecutionStore = TestExecutionStore(
        toolsResultApi = toolsResultApi
    )

    @Test
    fun getExecutionSteps() = runBlocking<Unit> {
        val envMatrix1 = EnvironmentMatrix(
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
        val testMatrix = store.getOrCreateTestMatrix(
            appApk = createFakeApk("app.pak"),
            testApk = createFakeApk("test.apk"),
            environmentMatrix = envMatrix1,
            clientInfo = null,
            deviceSetup = null,
            sharding = null
        )

        val inputSteps = mutableListOf<Step>(
            Step(stepId = UUID.randomUUID().toString()),
            Step(stepId = UUID.randomUUID().toString())
        )

        val projectId = testMatrix.projectId!!
        val historyId = testMatrix.resultStorage.toolResultsExecution?.historyId!!
        val executionId = testMatrix.resultStorage.toolResultsExecution?.executionId!!

        // Add 2 steps for this execution
        inputSteps.forEach() {
            toolsResultApi.createSteps(
                projectId = projectId,
                historyId = historyId,
                executionId = executionId,
                step = it,
            )
        }
        val outputSteps = testExecutionStore.getTestExecutionSteps(testMatrix)
        assertThat(outputSteps).hasSize(2)
        assertThat(outputSteps.first().stepId).isEqualTo(inputSteps.first().stepId)
        assertThat(outputSteps.last().stepId).isEqualTo(inputSteps.last().stepId)
    }

    private fun createFakeApk(name: String) = UploadedApk(
        gcsPath = GcsPath("gs://foo/bar/$name"),
        apkInfo = ApkInfo(
            filePath = "foo/bar/$name",
            idHash = name
        )
    )
}
