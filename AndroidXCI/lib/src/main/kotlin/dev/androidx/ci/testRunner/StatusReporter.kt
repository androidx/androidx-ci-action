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

import dev.androidx.ci.github.GithubApi
import dev.androidx.ci.github.dto.CommitInfo
import dev.androidx.ci.github.dto.RunInfo
import dev.androidx.ci.testRunner.vo.TestResult
import dev.androidx.ci.util.LazyComputedValue

/**
 * Helper class to report status of a run to the backend, github etc.
 */
internal class StatusReporter(
    val githubApi: GithubApi,
    /**
     * The Github workflow run which is hosting this TestRun
     */
    val hostRunId: String,
    /**
     * The Github workflow whose artifacts are being tested
     */
    val targetRunId: String
) {
    private val targetRunInfo = LazyComputedValue<RunInfo> {
        githubApi.runInfo(targetRunId)
    }

    private val hostRunInfo = LazyComputedValue<RunInfo> {
        githubApi.runInfo(hostRunId)
    }

    suspend fun reportStart() {
        updateState(CommitInfo.State.PENDING)
    }

    suspend fun reportEnd(testResult: TestResult) {
        val newState = when {
            testResult is TestResult.IncompleteRun -> CommitInfo.State.ERROR
            testResult.allTestsPassed -> CommitInfo.State.SUCCESS
            else -> CommitInfo.State.FAILURE
        }
        updateState(state = newState)
    }

    private suspend fun updateState(
        state: CommitInfo.State
    ) {
        val targetRunInfo = targetRunInfo.get()
        val hostRunInfo = hostRunInfo.get()
        githubApi.updateCommitStatus(
            sha = targetRunInfo.headSha,
            update = CommitInfo.Update(
                state = state,
                // the url for results is the url of the host run
                targetUrl = hostRunInfo.url,
                description = DESCRIPTION,
                context = CONTEXT
            )
        )
    }

    companion object {
        const val DESCRIPTION = "Integration Tests"
        const val CONTEXT = "AndroidX-FTL"
    }
}
