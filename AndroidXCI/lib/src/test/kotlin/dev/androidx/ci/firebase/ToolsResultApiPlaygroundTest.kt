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

package dev.androidx.ci.firebase

import com.google.auth.oauth2.GoogleCredentials
import dev.androidx.ci.config.Config
import dev.androidx.ci.testRunner.ToolsResultStore
import dev.androidx.ci.util.GoogleCloudCredentialsRule
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

internal class ToolsResultApiPlaygroundTest {
    private val projectId = "androidx-dev-prod"
    @get:Rule
    val playgroundCredentialsRule = GoogleCloudCredentialsRule {
        "androidx-dev-prod" to GoogleCredentials
            .getApplicationDefault()
    }

    private val api by lazy {
        ToolsResultApi.build(
            config = Config.ToolsResult(
                gcp = playgroundCredentialsRule.gcpConfig
            )
        )
    }

    private val ftlApi by lazy {
        FirebaseTestLabApi.build(
            Config.FirebaseTestLab(
                gcp = playgroundCredentialsRule.gcpConfig
            )
        )
    }

    @Test
    fun getHistories() = runBlocking<Unit> {
        val histories = api.getHistories(projectId = projectId, name = "androidx.recyclerview.test")
        println(histories)
    }

    @Test
    fun shardedRuns() = runBlocking<Unit> {
        val testMatrix = ftlApi.getTestMatrix(
            projectId = projectId,
            testMatrixId = "matrix-87zgcban3oosa"
        )
        val execution = testMatrix.testExecutions!!.first() // to this for all
        val testCases = execution.toolResultsStep?.let {
            // TODO get all pages
            api.getAllTestCases(
                projectId = it.projectId!!,
                historyId = it.historyId!!,
                executionId = it.executionId!!,
                stepId = it.stepId!!
            ).toList()
        }

        println(execution)
        println("---")
        println(testCases)
        println("---")
        println("total test cases :${testCases?.size}")
        testCases?.forEach {
            println("${it.testCaseReference} / ${it.status} / files (logs): ${it.toolOutputs?.firstOrNull()?.output?.fileUri}")
        }



//        val response = api.getExecution(projectId = projectId, historyId = testMatrix.testExecutions, executionId = "")
//        println(response)
    }

    @Test
    fun getOrCreateHistoryId() = runBlocking<Unit> {
        val store = ToolsResultStore(
            firebaseProjectId = projectId,
            toolsResultApi = api
        )
        val historyId = store.getHistoryId("androidx.compose.testutils.test")
        println(historyId)
    }
}
