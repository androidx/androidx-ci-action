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

package dev.androidx.ci.fake

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.generated.testResults.History
import dev.androidx.ci.generated.testResults.ListHistoriesResponse
import dev.androidx.ci.generated.testResults.ListStepsResponse
import dev.androidx.ci.generated.testResults.Step
import java.util.UUID

internal class FakeToolsResultApi : ToolsResultApi {
    private val histories = mutableListOf<History>()
    private val steps = mutableMapOf<ExecutionStepIdentifier, MutableList<Step>>()
    override suspend fun getHistories(projectId: String, name: String?, pageSize: Int): ListHistoriesResponse {
        return ListHistoriesResponse(
            nextPageToken = null,
            histories = histories.filter {
                name == null || it.name == name
            }
        )
    }

    override suspend fun create(projectId: String, requestId: String?, history: History): History {
        assertThat(
            getHistories(
                projectId = "",
                name = history.name
            ).histories
        ).isEmpty()
        val created = history.copy(
            historyId = UUID.randomUUID().toString()
        )
        histories.add(
            created
        )
        return created
    }

    override suspend fun listSteps(
        projectId: String,
        historyId: String,
        executionId: String,
        pageToken: String?,
        pageSize: Int
    ): ListStepsResponse {
        return ListStepsResponse(
            steps = steps[
                ExecutionStepIdentifier(
                    projectId = projectId,
                    historyId = historyId,
                    executionId = executionId
                )
            ]
        )
    }
    fun addStep(
        projectId: String,
        historyId: String,
        executionId: String,
        step: Step = Step(
            stepId = UUID.randomUUID().toString()
        )
    ) {
        val executionStepIdentifier = ExecutionStepIdentifier(
            projectId,
            historyId,
            executionId
        )
        steps.getOrPut(executionStepIdentifier) {
            mutableListOf()
        }.add(step)
    }

    private data class ExecutionStepIdentifier(
        val projectId: String,
        val historyId: String,
        val executionId: String
    )
}
