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

import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.testResults.Step

internal class TestExecutionStore(
    private val toolsResultApi: ToolsResultApi
) {
    private suspend fun getTestExecutionSteps(
        projectId: String,
        historyId: String,
        executionId: String
    ): List<Step> {
        val steps = mutableListOf<Step>()
        var nextPageToken: String? = null
        do {
            val response = toolsResultApi.listSteps(
                projectId = projectId,
                historyId = historyId,
                executionId = executionId,
                pageToken = nextPageToken
            )
            response.steps?.let { steps.addAll(it) }
            nextPageToken = response.nextPageToken
        } while (nextPageToken != null)
        return steps
    }

    suspend fun getTestExecutionSteps(
        testMatrix: TestMatrix
    ): List<Step> {
        return testMatrix.projectId?.let { projectId ->
            testMatrix.resultStorage.toolResultsExecution?.historyId?.let { historyId ->
                testMatrix.resultStorage.toolResultsExecution.executionId?.let { executionId ->
                    getTestExecutionSteps(
                        projectId = projectId,
                        historyId = historyId,
                        executionId = executionId
                    )
                }
            }
        } ?: emptyList()
    }
}
