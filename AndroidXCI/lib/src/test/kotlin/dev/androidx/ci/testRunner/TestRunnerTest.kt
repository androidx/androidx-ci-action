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
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.ftl.TestMatrix.OutcomeSummary.FAILURE
import dev.androidx.ci.generated.ftl.TestMatrix.OutcomeSummary.SUCCESS
import dev.androidx.ci.testRunner.vo.TestResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class TestRunnerTest {
    private val testScope = TestCoroutineScope()
    private val fakeBackend = FakeBackend()
    private val testRunner = TestRunner(
        googleCloudApi = fakeBackend.fakeGoogleCloudApi,
        githubApi = fakeBackend.fakeGithubApi,
        datastoreApi = fakeBackend.datastoreApi,
        firebaseTestLabApi = fakeBackend.fakeFirebaseTestLabApi,
        firebaseProjectId = PROJECT_ID,
        targetRunId = RUN_ID,
        hostRunId = null
    )

    @Test
    fun badRunId() = testScope.runBlockingTest {
        val result = testRunner.runTests()
        assertThat(result.type).isEqualTo(TestResult.Type.INCOMPLETE_RUN)
        assertThat(result.allTestsPassed).isFalse()
    }

    @Test
    fun emptyArtifacts() = testScope.runBlockingTest {
        fakeBackend.createRun(runId = "1", artifacts = emptyList())
        val result = testRunner.runTests()
        assertThat(result.allTestsPassed).isTrue()
        check(result is TestResult.CompleteRun)
        assertThat(result.matrices).isEmpty()
    }

    @Test
    fun noApks() = testScope.runBlockingTest {
        val artifact1 = fakeBackend.createArchive(
            "foo.txt", "bar.txt"
        )
        fakeBackend.createRun(
            RUN_ID,
            listOf(
                artifact1
            )
        )
        val result = testRunner.runTests()
        assertThat(result.allTestsPassed).isTrue()
        check(result is TestResult.CompleteRun)
        assertThat(result.matrices).isEmpty()
    }

    @Test
    fun singlePassingTest() = singleTest(succeed = true)

    @Test
    fun singleFailingTest() = singleTest(succeed = false)

    private fun singleTest(
        succeed: Boolean
    ) = testScope.runBlockingTest {
        val artifact1 = fakeBackend.createArchive(
            "biometric-integration-tests-testapp_testapp-debug-androidTest.apk",
            "biometric-integration-tests-testapp_testapp-debug.apk",
            "biometric-integration-tests-testapp_testapp-release.apk"
        )
        fakeBackend.createRun(
            RUN_ID,
            listOf(
                artifact1
            )
        )
        val runTests = async {
            testRunner.runTests()
        }
        runCurrent()
        assertThat(runTests.isActive).isTrue()
        val testMatrices = fakeBackend.fakeFirebaseTestLabApi.getTestMatrices()
        assertThat(testMatrices).hasSize(1)
        val outcome = if (succeed) {
            SUCCESS
        } else {
            FAILURE
        }
        fakeBackend.finishAllTests(outcome)
        advanceUntilIdle()
        val result = runTests.await()
        assertThat(result.allTestsPassed).isEqualTo(succeed)
        check(result is TestResult.CompleteRun)
        assertThat(result.matrices).hasSize(1)
        result.matrices.first().let {
            // make sure it returns updated test matrices
            assertThat(it.state).isEqualTo(TestMatrix.State.FINISHED)
            assertThat(it.outcomeSummary).isEqualTo(outcome)
        }
    }

    @Test
    fun multipleTestsOnMultipleArtifacts_oneFailure() = testScope.runBlockingTest {
        val artifact1 = fakeBackend.createArchive(
            "biometric-integration-tests-testapp_testapp-debug-androidTest.apk",
            "biometric-integration-tests-testapp_testapp-debug.apk",
            "biometric-integration-tests-testapp_testapp-release.apk"
        )
        val artifact2 = fakeBackend.createArchive(
            "nanometrics-integration-tests-testapp_testapp-debug-androidTest.apk",
            "nanometrics-integration-tests-testapp_testapp-debug.apk",
            "nanometrics-integration-tests-testapp_testapp-release.apk"
        )
        fakeBackend.createRun(
            RUN_ID,
            listOf(
                artifact1,
                artifact2
            )
        )
        val runTests = async {
            testRunner.runTests()
        }
        runCurrent()
        assertThat(runTests.isActive).isTrue()
        val testMatrices = fakeBackend.fakeFirebaseTestLabApi.getTestMatrices()
        assertThat(testMatrices).hasSize(2)
        fakeBackend.finishTest(
            testMatrixId = testMatrices[0].testMatrixId!!,
            outcome = FAILURE
        )
        fakeBackend.finishTest(
            testMatrixId = testMatrices[1].testMatrixId!!,
            outcome = SUCCESS
        )
        advanceUntilIdle()
        val result = runTests.await()
        assertThat(result.allTestsPassed).isFalse()
        check(result is TestResult.CompleteRun)
        assertThat(result.matrices).hasSize(2)
        val outcomes = result.matrices.map {
            it.outcomeSummary
        }
        assertThat(outcomes).containsExactly(SUCCESS, FAILURE)
    }

    @Test
    fun multipleTestsOnSingleArtifact() = testScope.runBlockingTest {
        val artifact1 = fakeBackend.createArchive(
            "biometric-integration-tests-testapp_testapp-debug-androidTest.apk",
            "biometric-integration-tests-testapp_testapp-debug.apk",
            "biometric-integration-tests-testapp_testapp-release.apk",
            "nanometrics-integration-tests-testapp_testapp-debug-androidTest.apk",
            "nanometrics-integration-tests-testapp_testapp-debug.apk",
            "nanometrics-integration-tests-testapp_testapp-release.apk"
        )
        fakeBackend.createRun(
            RUN_ID,
            listOf(
                artifact1
            )
        )
        val runTests = async {
            testRunner.runTests()
        }
        runCurrent()
        assertThat(runTests.isActive).isTrue()
        val testMatrices = fakeBackend.fakeFirebaseTestLabApi.getTestMatrices()
        assertThat(testMatrices).hasSize(2)
        fakeBackend.finishTest(
            testMatrixId = testMatrices[0].testMatrixId!!,
            outcome = FAILURE
        )
        fakeBackend.finishTest(
            testMatrixId = testMatrices[1].testMatrixId!!,
            outcome = SUCCESS
        )
        advanceUntilIdle()
        val result = runTests.await()
        assertThat(result.allTestsPassed).isFalse()
        check(result is TestResult.CompleteRun)
        assertThat(result.matrices).hasSize(2)
        val outcomes = result.matrices.map {
            it.outcomeSummary
        }
        assertThat(outcomes).containsExactly(SUCCESS, FAILURE)
    }

    private companion object {
        private const val PROJECT_ID = "testProject"
        private const val RUN_ID = "1"
    }
}
