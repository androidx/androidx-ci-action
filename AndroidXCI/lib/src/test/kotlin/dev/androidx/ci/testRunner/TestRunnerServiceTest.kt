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

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeBackend
import dev.androidx.ci.generated.ftl.TestMatrix.OutcomeSummary.FAILURE
import dev.androidx.ci.generated.ftl.TestMatrix.OutcomeSummary.SUCCESS
import dev.androidx.ci.testRunner.FTLTestDevices.NEXUS10_API_19_VIRTUAL
import dev.androidx.ci.testRunner.FTLTestDevices.PIXEL2_API_26_VIRTUAL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
internal class TestRunnerServiceTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()
    private val testScope = TestScope()
    private val fakeBackend = FakeBackend()

    private val testRunnerService by lazy {
        TestRunnerService(
            googleCloudApi = fakeBackend.fakeGoogleCloudApi,
            firebaseTestLabApi = fakeBackend.fakeFirebaseTestLabApi,
            toolsResultApi = fakeBackend.fakeToolsResultApi,
            firebaseProjectId = PROJECT_ID,
            datastoreApi = fakeBackend.datastoreApi,
            gcsResultPath = "testRunnerServiceTest"
        )
    }

    @Test
    fun runLibraryTest() {
        val libraryTest = tmpFolder.newFile("library-test.apk").also {
            it.writeText("library-test")
        }
        testScope.runTest {
            val testRun = async {
                testRunnerService.runTest(
                    testApk = libraryTest,
                    localDownloadFolder = tmpFolder.newFolder()
                )
            }
            testScope.runCurrent()
            assertThat(
                testRun.isActive
            ).isTrue()
            val testMatrices = fakeBackend.fakeFirebaseTestLabApi.getTestMatrices()
            assertThat(testMatrices).hasSize(1)
            fakeBackend.finishAllTests(SUCCESS)
            advanceUntilIdle()
            assertThat(testRun.isCompleted).isTrue()
            val (testResult, downloads) = testRun.await()
            assertThat(downloads).hasSize(1)
            assertThat(testResult.allTestsPassed).isTrue()
        }
    }

    @Test
    fun runIntegrationTestApp() {
        val testApp = tmpFolder.newFile("test.apk").also {
            it.writeText("tests")
        }
        val appUnderTest = tmpFolder.newFile("appUnderTest.apk").also {
            it.writeText("app")
        }
        testScope.runTest {
            val testRun = async {
                testRunnerService.runTest(
                    testApk = testApp,
                    appApk = appUnderTest,
                    localDownloadFolder = tmpFolder.newFolder()
                )
            }
            testScope.runCurrent()
            assertThat(
                testRun.isActive
            ).isTrue()
            val testMatrices = fakeBackend.fakeFirebaseTestLabApi.getTestMatrices()
            assertThat(testMatrices).hasSize(1)
            fakeBackend.finishAllTests(SUCCESS)
            advanceUntilIdle()
            assertThat(testRun.isCompleted).isTrue()
            val (testResult, downloads) = testRun.await()
            assertThat(downloads).hasSize(1)
            assertThat(testResult.allTestsPassed).isTrue()
        }
    }

    @Test
    fun runOnTwoDevices() {
        val libraryTest = tmpFolder.newFile("library-test.apk").also {
            it.writeText("library-test")
        }
        val targetDevices = listOf(
            PIXEL2_API_26_VIRTUAL, NEXUS10_API_19_VIRTUAL
        )
        testScope.runTest {
            val testRun = async {
                testRunnerService.runTest(
                    testApk = libraryTest,
                    localDownloadFolder = tmpFolder.newFolder(),
                    devicePicker = {
                        targetDevices
                    }
                )
            }
            testScope.runCurrent()
            assertThat(
                testRun.isActive
            ).isTrue()
            val testMatrices = fakeBackend.fakeFirebaseTestLabApi.getTestMatrices()
            assertThat(testMatrices).hasSize(2)
            fakeBackend.finishTest(testMatrices[0].testMatrixId!!, FAILURE)
            fakeBackend.finishTest(testMatrices[1].testMatrixId!!, SUCCESS)
            advanceUntilIdle()
            assertThat(testRun.isCompleted).isTrue()
            val (testResult, downloads) = testRun.await()
            assertThat(downloads).hasSize(2)
            assertThat(testResult.allTestsPassed).isFalse()
            val devices = testMatrices.flatMap {
                (it.environmentMatrix.androidDeviceList?.androidDevices ?: emptyList()).also {
                    // make sure each invocation has only 1 device
                    assertThat(it).hasSize(1)
                }
            }
            assertThat(devices).containsExactlyElementsIn(targetDevices)
        }
    }

    @Test
    fun scheduleAndCollect() {
        val testApp = tmpFolder.newFile("test.apk").also {
            it.writeText("tests")
        }
        val appUnderTest = tmpFolder.newFile("appUnderTest.apk").also {
            it.writeText("app")
        }
        testScope.runTest {
            val scheduled = testRunnerService.scheduleTests(
                testApk = testApp,
                appApk = appUnderTest,
                devices = listOf(PIXEL2_API_26_VIRTUAL)
            )
            assertThat(fakeBackend.fakeFirebaseTestLabApi.getTestMatrices()).hasSize(1)
            assertThat(scheduled.cachedTests).isEqualTo(0)
            assertThat(scheduled.newTests).isEqualTo(1)
            val pixelTestMatrixId = scheduled.testMatrixIds.single()

            // finish it
            val result = async {
                testRunnerService.getTestResults(
                    scheduledTests = listOf(scheduled),
                    localDownloadFolder = tmpFolder.newFolder()
                )
            }
            runCurrent()
            assertThat(result.isActive).isTrue()
            // finish the test
            fakeBackend.finishTest(pixelTestMatrixId, SUCCESS)
            advanceUntilIdle()
            assertThat(result.await().testResult.allTestsPassed).isTrue()

            // now re-submit 2 tests. 1 should be new, 1 should be re-used
            val schedule2 = testRunnerService.scheduleTests(
                testApk = testApp,
                appApk = appUnderTest,
                devices = listOf(PIXEL2_API_26_VIRTUAL, NEXUS10_API_19_VIRTUAL)
            )
            // only 1 new test matrix
            assertThat(fakeBackend.fakeFirebaseTestLabApi.getTestMatrices()).hasSize(2)
            assertThat(
                schedule2.newTests
            ).isEqualTo(1)
            assertThat(
                schedule2.cachedTests
            ).isEqualTo(1)
            val nexus5TestMatrixId = (schedule2.testMatrixIds - scheduled.testMatrixIds).single()
            val result2 = async {
                testRunnerService.getTestResults(
                    scheduledTests = listOf(schedule2),
                    localDownloadFolder = tmpFolder.newFolder()
                )
            }
            runCurrent()
            // now, this time, fail the test
            fakeBackend.finishTest(
                testMatrixId = nexus5TestMatrixId,
                outcome = FAILURE
            )
            advanceUntilIdle()
            result2.await().let { response ->
                assertThat(response.testResult.allTestsPassed).isFalse()
                assertThat(response.testMatrixFor(pixelTestMatrixId)?.outcomeSummary)
                    .isEqualTo(SUCCESS)
                assertThat(response.testMatrixFor(nexus5TestMatrixId)?.outcomeSummary)
                    .isEqualTo(FAILURE)
                assertThat(response.downloadsFor(nexus5TestMatrixId)).isNotNull()
                assertThat(response.downloadsFor(pixelTestMatrixId)).isNotNull()
            }
        }
    }

    private companion object {
        private const val PROJECT_ID = "testProject"
    }
}
