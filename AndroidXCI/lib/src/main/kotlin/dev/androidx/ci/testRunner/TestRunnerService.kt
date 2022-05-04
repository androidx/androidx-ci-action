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

import com.google.auth.Credentials
import dev.androidx.ci.config.Config
import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.gcloud.GoogleCloudApi
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.TestEnvironmentCatalog
import dev.androidx.ci.testRunner.vo.TestResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.util.concurrent.TimeUnit

class TestRunnerService(
    private val googleCloudApi: GoogleCloudApi,
    datastoreApi: DatastoreApi,
    firebaseTestLabApi: FirebaseTestLabApi,
    toolsResultApi: ToolsResultApi,
    firebaseProjectId: String,
    gcsResultPath: String, // should be unique per run, otherwise it will turn into a giant folder
) {
    private val logger = logger()
    private val testMatrixStore = TestMatrixStore(
        firebaseProjectId = firebaseProjectId,
        datastoreApi = datastoreApi,
        firebaseTestLabApi = firebaseTestLabApi,
        toolsResultApi = toolsResultApi,
        resultsGcsPrefix = googleCloudApi.getGcsPath("ftl/$gcsResultPath")
    )
    private val apkStore = ApkStore(googleCloudApi)
    val testLabController = FirebaseTestLabController(
        firebaseTestLabApi = firebaseTestLabApi,
        firebaseProjectId = firebaseProjectId,
        testMatrixStore = testMatrixStore
    )

    /**
     * Runs the test for the given [testApk] [appApk] pair.
     *
     * To optimize cacheability, a new TestMatrix will be created for each device.
     *
     * @param testApk The test apk which has the instrumentation tests
     * @param appApk The application under test. Can be `null` for library tests
     * @param localDownloadFolder A local directory into which the test run outputs (logcat, result
     * xml etc) should be downloaded. This folder will be cleaned so it shouldn't be shared with
     * other tasks.
     * @param devices List of [AndroidDevice]s to run the test on. See [FTLTestDevices] for a set of
     * common devices.
     *
     * @return A [TestRunResponse] that includes the [TestMatrix] list as well as links to each
     * downloaded artifact.
     */
    suspend fun runTest(
        testApk: File,
        appApk: File? = null,
        localDownloadFolder: File,
        devices: List<AndroidDevice>
    ): TestRunResponse {
        return runTest(
            testApk = testApk,
            appApk = appApk,
            localDownloadFolder = localDownloadFolder,
            devicePicker = {
                devices
            }
        )
    }

    /**
     * Runs the test for the given [testApk] [appApk] pair.
     *
     * To optimize cacheability, a new TestMatrix will be created for each device chosen by the
     * [devicePicker].
     *
     * @param testApk The test apk which has the instrumentation tests
     * @param appApk The application under test. Can be `null` for library tests
     * @param localDownloadFolder A local directory into which the test run outputs (logcat, result
     * xml etc) should be downloaded. This folder will be cleaned so it shouldn't be shared with
     * other tasks.
     * @param devicePicker A block that can return desired devices from a [TestEnvironmentCatalog].
     *
     * @return A [TestRunResponse] that includes the [TestMatrix] list as well as links to each
     * downloaded artifact.
     */
    suspend fun runTest(
        testApk: File,
        appApk: File? = null,
        localDownloadFolder: File,
        devicePicker: DevicePicker? = null,
    ): TestRunResponse {
        logger.trace { "Running tests for testApk: $testApk appApk: $appApk" }
        val result = try {
            val uploadedTestApk = apkStore.uploadApk(testApk.name, testApk.readBytes())
            val uploadedAppApk = appApk?.let {
                apkStore.uploadApk(appApk.name, appApk.readBytes())
            } ?: apkStore.getPlaceholderApk()
            logger.trace { "Will submit tests to the test lab" }
            val testMatrices = testLabController.submitTests(
                appApk = uploadedAppApk,
                testApk = uploadedTestApk,
                devicePicker = devicePicker
            )
            logger.trace {
                """
                Created ${testMatrices.size} test matrices. Will poll them until they complete.
                """.trimIndent()
            }
            testLabController.collectTestResults(
                matrices = testMatrices,
                pollIntervalMs = TimeUnit.SECONDS.toMillis(10)
            )
        } catch (th: Throwable) {
            logger.error("exception in test run", th)
            TestResult.IncompleteRun(th.stackTraceToString())
        }
        logger.trace { "Done running tests for $testApk / $appApk" }
        val downloadResult = TestResultDownloader(
            googleCloudApi = googleCloudApi
        ).downloadTestResults(
            outputFolder = localDownloadFolder,
            result = result
        )
        return TestRunResponse(
            testResult = result,
            downloads = downloadResult
        )
    }

    companion object {
        fun create(
            /**
             * service account file contents
             */
            credentials: Credentials,
            /**
             * Firebase project id
             */
            firebaseProjectId: String,
            /**
             * GCP bucket name
             */
            bucketName: String,
            /**
             * GCP bucket path (path inside the bucket)
             */
            bucketPath: String,
            /**
             * GCP path to put the results into
             */
            gcsResultPath: String,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        ): TestRunnerService {
            return TestRunnerService(
                googleCloudApi = GoogleCloudApi.build(
                    Config.GCloud(
                        credentials = credentials,
                        bucketName = bucketName,
                        bucketPath = bucketPath
                    ),
                    context = ioDispatcher
                ),
                datastoreApi = DatastoreApi.build(
                    Config.Datastore(
                        credentials = credentials
                    ),
                    context = ioDispatcher
                ),
                toolsResultApi = ToolsResultApi.build(
                    config = Config.ToolsResult(
                        credentials = credentials
                    )
                ),
                firebaseProjectId = firebaseProjectId,
                firebaseTestLabApi = FirebaseTestLabApi.build(
                    config = Config.FirebaseTestLab(
                        credentials = credentials
                    )
                ),
                gcsResultPath = gcsResultPath
            )
        }
    }

    data class TestRunResponse(
        val testResult: TestResult,
        val downloads: List<TestResultDownloader.DownloadedTestResults>
    ) {
        fun downloadsFor(
            testMatrixId: String
        ) = downloads.find {
            it.testMatrixId == testMatrixId
        }
    }
}
