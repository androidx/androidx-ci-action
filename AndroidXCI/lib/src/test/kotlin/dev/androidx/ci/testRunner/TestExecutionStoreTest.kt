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
import dev.androidx.ci.fake.FakeBackend
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.GoogleCloudStorage
import dev.androidx.ci.generated.ftl.ResultStorage
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.ftl.TestSpecification
import dev.androidx.ci.generated.ftl.ToolResultsExecution
import dev.androidx.ci.generated.testResults.Step
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID

@RunWith(JUnit4::class)
internal class TestExecutionStoreTest {
    private val fakeBackend = FakeBackend()
    private val fakeToolsResultApi = fakeBackend.fakeToolsResultApi
    private val testExecutionStore = TestExecutionStore(
        toolsResultApi = fakeToolsResultApi
    )

    @Test
    fun getExecutionSteps() = runBlocking<Unit> {
        val resultPath = "${fakeBackend.fakeGoogleCloudApi.rootGcsPath}/my-test-matrix-results"
        val testMatrix = fakeBackend.fakeFirebaseTestLabApi.createTestMatrix(
            projectId = fakeBackend.firebaseProjectId,
            requestId = "requestId",
            testMatrix = TestMatrix(
                resultStorage = ResultStorage(
                    googleCloudStorage = GoogleCloudStorage(resultPath),
                    toolResultsExecution = ToolResultsExecution(
                        executionId = "test_executionId",
                        historyId = "test_historyId"
                    )
                ),
                projectId = fakeBackend.firebaseProjectId,
                environmentMatrix = EnvironmentMatrix(),
                testSpecification = TestSpecification()
            )
        )

        val inputSteps = mutableListOf<Step>(
            Step(stepId = UUID.randomUUID().toString()),
            Step(stepId = UUID.randomUUID().toString())
        )

        val projectId = testMatrix.projectId!!
        val historyId = testMatrix.resultStorage.toolResultsExecution?.historyId!!
        val executionId = testMatrix.resultStorage.toolResultsExecution?.executionId!!

        // Add 2 steps for this execution
        inputSteps.forEach {
            fakeToolsResultApi.addStep(
                projectId = projectId,
                historyId = historyId,
                executionId = executionId,
                step = it,
            )
        }
        val outputSteps = testExecutionStore.getTestExecutionSteps(testMatrix)
        assertThat(outputSteps).hasSize(2)
        assertThat(outputSteps).containsExactlyElementsIn(inputSteps)
    }

    @Test
    fun getExecutionStepsNullValuesForExecutionIdHistoryId() = runBlocking<Unit> {
        val resultPath = "${fakeBackend.fakeGoogleCloudApi.rootGcsPath}/my-test-matrix-results"
        val testMatrix = fakeBackend.fakeFirebaseTestLabApi.createTestMatrix(
            projectId = fakeBackend.firebaseProjectId,
            requestId = "requestId",
            testMatrix = TestMatrix(
                resultStorage = ResultStorage(
                    googleCloudStorage = GoogleCloudStorage(resultPath)
                ),
                projectId = fakeBackend.firebaseProjectId,
                environmentMatrix = EnvironmentMatrix(),
                testSpecification = TestSpecification()
            )
        )

        val outputSteps = testExecutionStore.getTestExecutionSteps(testMatrix)
        assertThat(outputSteps).isEmpty()
    }
}
