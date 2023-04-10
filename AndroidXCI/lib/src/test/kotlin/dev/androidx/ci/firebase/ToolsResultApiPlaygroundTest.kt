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

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.config.Config
import dev.androidx.ci.testRunner.ToolsResultStore
import dev.androidx.ci.util.GoogleCloudCredentialsRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

internal class ToolsResultApiPlaygroundTest {
    private val projectId = "androidx-dev-prod"
    @get:Rule
    val playgroundCredentialsRule = GoogleCloudCredentialsRule()

    private val api by lazy {
        ToolsResultApi.build(
            config = Config.ToolsResult(
                gcp = playgroundCredentialsRule.gcpConfig
            )
        )
    }

    @Test
    fun getHistories() = runBlocking<Unit> {
        val histories = api.getHistories(projectId = projectId, name = "androidx.room.integration.noappcompat")
        println(histories)
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

    @Test
    fun getSteps() = runBlocking<Unit> {
        // this testrun has 8 steps
        val steps = api.listSteps(projectId, "bh.3d8b75bbc1050bf7", "6570812128705264798", null, 5)
        assertThat(steps.steps?.size).isEqualTo(5)
        val nextSteps = api.listSteps(projectId, "bh.3d8b75bbc1050bf7", "6570812128705264798", steps.nextPageToken, 5)
        assertThat(nextSteps.steps?.size).isEqualTo(3)
    }
}
