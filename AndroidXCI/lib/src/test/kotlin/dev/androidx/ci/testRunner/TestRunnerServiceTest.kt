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
class TestRunnerServiceTest {
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
            FTLTestDevices.PIXEL6_31, FTLTestDevices.NEXUS5_19
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

    private companion object {
        private const val PROJECT_ID = "testProject"
    }
}
