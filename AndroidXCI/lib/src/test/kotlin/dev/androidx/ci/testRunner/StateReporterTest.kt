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
import dev.androidx.ci.github.dto.CommitInfo
import dev.androidx.ci.github.dto.CommitInfo.State.PENDING
import dev.androidx.ci.github.dto.CommitInfo.State.SUCCESS
import dev.androidx.ci.github.dto.RunInfo
import dev.androidx.ci.testRunner.vo.TestResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class StateReporterTest {
    private val fakeBackend = FakeBackend()
    private val reporter = StatusReporter(
        githubApi = fakeBackend.fakeGithubApi,
        hostRunId = HOST_RUN_ID,
        targetRunId = TARGET_RUN_ID
    )
    private val targetRunInfo: RunInfo = fakeBackend.createRun(TARGET_RUN_ID, emptyList())
    private val hostRunInfo: RunInfo = fakeBackend.createRun(HOST_RUN_ID, emptyList())

    @Test
    fun reportStart() = runBlocking<Unit> {
        reporter.reportStart()
        val status = fakeBackend.fakeGithubApi.commitStatus(targetRunInfo.headSha)
        assertThat(
            status.statuses
        ).hasSize(1)
        assertThat(
            status.state
        ).isEqualTo(PENDING)
        status.statuses.first().let {
            assertThat(it.context).isEqualTo(StatusReporter.CONTEXT)
            assertThat(it.description).isEqualTo(StatusReporter.DESCRIPTION)
            assertThat(it.state).isEqualTo(PENDING)
            assertThat(it.targetUrl).isEqualTo(hostRunInfo.url)
        }
    }

    @Test
    fun reportEnd_success() = runBlocking<Unit> {
        reporter.reportEnd(
            TestResult.CompleteRun(matrices = emptyList())
        )
        val status = fakeBackend.fakeGithubApi.commitStatus(targetRunInfo.headSha).statuses.first()
        assertThat(
            status.state
        ).isEqualTo(SUCCESS)
    }

    @Test
    fun reportEnd_fail() = runBlocking<Unit> {
        val matrix = TestMatrix(
            resultStorage = ResultStorage(
                googleCloudStorage = GoogleCloudStorage("gs://fake-path")
            ),
            projectId = "myProject",
            environmentMatrix = EnvironmentMatrix(),
            testSpecification = TestSpecification()
        )
        val failedTestMatrix = fakeBackend.fakeFirebaseTestLabApi.createTestMatrix(
            projectId = "myProject",
            requestId = "requestId",
            testMatrix = matrix
        )
        reporter.reportEnd(
            TestResult.CompleteRun(matrices = listOf(failedTestMatrix))
        )
        val status = fakeBackend.fakeGithubApi.commitStatus(targetRunInfo.headSha).statuses.first()
        assertThat(
            status.state
        ).isEqualTo(CommitInfo.State.FAILURE)
    }

    @Test
    fun reportEnd_error() = runBlocking<Unit> {
        reporter.reportEnd(
            TestResult.IncompleteRun("some error")
        )
        val status = fakeBackend.fakeGithubApi.commitStatus(targetRunInfo.headSha).statuses.first()
        assertThat(
            status.state
        ).isEqualTo(CommitInfo.State.ERROR)
    }

    companion object {
        const val HOST_RUN_ID = "1"
        const val TARGET_RUN_ID = "2"
    }
}
