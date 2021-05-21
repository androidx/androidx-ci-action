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
import dev.androidx.ci.github.dto.IssueComment
import dev.androidx.ci.github.tryDeletingLabel
import dev.androidx.ci.testRunner.vo.TestResult
import dev.androidx.ci.util.LazyComputedValue

/**
 * Helper class to report status of test runs.
 */
class StatusReporter(
    val githubApi: GithubApi,
    val targetRunId: String,
    val hostRunId: String?,
) {
    private val runInfo = LazyComputedValue {
        githubApi.runInfo(targetRunId)
    }

    suspend fun reportStart() {
        setGithubLabel(StatusLabel.RUNNING)
        addCommentForResults()
    }

    private suspend fun addCommentForResults() {
        if (hostRunId == null) return
        val runInfo = runInfo.get()
        val hostRunInfo = githubApi.runInfo(hostRunId)
        runInfo.pullRequests.forEach { pullRequest ->
            githubApi.comment(
                issueNumber = pullRequest.number,
                comment = IssueComment(
                    body = """
                        Started running integration tests. You can find the results here:
                        ${hostRunInfo.url}
                        May the flake gods be with you!
                    """.trimIndent()
                )
            )
        }
    }

    suspend fun reportFinish(result: TestResult) {
        val label = if (result.allTestsPassed) {
            StatusLabel.PASSED
        } else {
            StatusLabel.FAILED
        }
        setGithubLabel(label)
    }

    private suspend fun setGithubLabel(label: StatusLabel) {
        runInfo.get().pullRequests.forEach { pullRequest ->
            val existingLabels = githubApi.getLabels(pullRequest.number)
            label.otherLabels().filter { otherLabel ->
                existingLabels.any { existingLabel ->
                    existingLabel.name == otherLabel.githubName
                }
            }.forEach { label ->
                githubApi.tryDeletingLabel(
                    issueNumber = pullRequest.number,
                    label = label.githubName
                )
            }
            githubApi.addLabels(
                issueNumber = pullRequest.number,
                labels = listOf(label.githubName)
            )
        }
    }

    enum class StatusLabel(
        val githubName: String
    ) {
        RUNNING("integration tests: running"),
        PASSED("integration tests: passed"),
        FAILED("integration tests: failed");

        fun otherLabels() = values().toList() - this
    }
}
