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

import com.google.auth.oauth2.ServiceAccountCredentials
import dev.androidx.ci.config.Config
import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.gcloud.GoogleCloudApi
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.github.GithubApi
import dev.androidx.ci.github.dto.ArtifactsResponse
import dev.androidx.ci.testRunner.vo.TestResult
import kotlinx.coroutines.CoroutineDispatcher
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Main class that is responsible to run tests, download reports etc.
 *
 * Tests are run in multiple steps, mainly:
 *
 * * Find APKs in a github workflow
 * * Upload them to GCP
 * * Invoke FTL APIs to create Test Matrices
 * * Wait for all tests to finish, download outputs
 * * Generate final report
 */
class TestRunner internal constructor(
    private val googleCloudApi: GoogleCloudApi,
    private val githubApi: GithubApi,
    datastoreApi: DatastoreApi,
    firebaseTestLabApi: FirebaseTestLabApi,
    toolsResultApi: ToolsResultApi,
    projectId: String,
    /**
     * The workflow run id from github whose artifacts will be tested
     */
    private val targetRunId: String,
    /**
     * The workflow run id from github which is running this test runner
     */
    private val hostRunId: String,
    /**
     * An optional filter to pick which build artifacts should be downloaded.
     */
    private val githubArtifactFilter: (ArtifactsResponse.Artifact) -> Boolean = { true },
    /**
     * The directory where results will be saved locally
     */
    private val outputFolder: File? = null,
    /**
     * Device picker
     */
    private val devicePicker: DevicePicker? = null,
    /**
     * The actual class that will decide which tests to run out of the artifacts
     */
    testSchedulerFactory: TestScheduler.Factory,
    /**
     * When set to true, TestMatrix failures due to empty test suites (e.g. all ignored) will not
     * fail the test run.
     */
    private val ignoreEmptyTestMatrices: Boolean = true,
) {
    private val logger = logger()
    private val testMatrixStore = TestMatrixStore(
        firebaseProjectId = projectId,
        datastoreApi = datastoreApi,
        firebaseTestLabApi = firebaseTestLabApi,
        toolsResultApi = toolsResultApi,
        resultsGcsPrefix = googleCloudApi.getGcsPath("ftl/$targetRunId"),
        googleCloudApi = googleCloudApi
    )
    private val apkStore = ApkStore(googleCloudApi)
    private val testLabController = FirebaseTestLabController(
        firebaseTestLabApi = firebaseTestLabApi,
        firebaseProjectId = projectId,
        testMatrixStore = testMatrixStore
    )
    private val statusReporter = StatusReporter(
        githubApi = githubApi,
        hostRunId = hostRunId,
        targetRunId = targetRunId
    )
    private val testScheduler = testSchedulerFactory.create(
        githubApi = githubApi,
        firebaseTestLabController = testLabController,
        apkStore = apkStore,
        devicePicker = devicePicker
    )

    private val testExecutionStore = TestExecutionStore(
        toolsResultApi = toolsResultApi
    )

    /**
     * Runs all the test. This never throws, instead, returns an error result if something goes
     * wrong.
     */
    suspend fun runTests(): TestResult {
        logger.trace("start running tests")
        val result = try {
            statusReporter.reportStart()
            val artifactsResponse = githubApi.artifacts(targetRunId)
            val allTestMatrices = artifactsResponse.artifacts
                .filter(githubArtifactFilter)
                .flatMap { artifact ->
                    testScheduler.enqueueTests(artifact)
                }.also { testMatrices ->
                    logger.info { "started all tests for $testMatrices" }
                }
            logger.info("will wait for test results")
            val collectResult = testLabController.collectTestResults(
                matrices = allTestMatrices,
                pollIntervalMs = TimeUnit.SECONDS.toMillis(10)
            )
            if (ignoreEmptyTestMatrices && collectResult is TestResult.CompleteRun) {
                // when we skip tests, firebase marks it as failed instead of skipped. we patch fix it here if requested
                val updatedTestMatrices = collectResult.matrices.map { testMatrix ->
                    if (testMatrix.outcomeSummary == TestMatrix.OutcomeSummary.FAILURE &&
                        testMatrix.areAllTestsSkipped()
                    ) {
                        // change summary to SKIPPED instead.
                        testMatrix.copy(outcomeSummary = TestMatrix.OutcomeSummary.SKIPPED)
                    } else {
                        testMatrix
                    }
                }
                TestResult.CompleteRun(updatedTestMatrices)
            } else {
                collectResult
            }
        } catch (th: Throwable) {
            logger.error("exception in test run", th)
            TestResult.IncompleteRun(th.stackTraceToString())
        }
        logger.trace("done running tests, will upload result to gcloud and download artifacts")
        try {
            outputFolder?.let {
                TestResultDownloader(
                    googleCloudApi = googleCloudApi
                ).downloadTestResults(
                    outputFolder = outputFolder,
                    result = result,
                    // don't clear the output folder as the CLI uses it for logs as well.
                    clearOutputFolder = false
                )
            }
            statusReporter.reportEnd(result)
        } catch (th: Throwable) {
            logger.error("unexpected error while writing results", th)
        }
        return result
    }

    /**
     * Returns `true` if all tests in the TestMatrix are skipped.
     *
     * Firebase marks a TestMatrix as failed if all tests in it are skipped. It is WAI because not running any tests
     * is likely an error. In certain cases (e.g. AndroidX) this is OK hence requires special handling.
     *
     * see: [ignoreEmptyTestMatrices]
     */
    private suspend fun TestMatrix.areAllTestsSkipped(): Boolean {
        if (outcomeSummary == null) {
            // test is not complete yet
            return false
        }
        val steps = testExecutionStore.getTestExecutionSteps(this)
        val overviews = steps.flatMap { step ->
            step.testExecutionStep?.testSuiteOverviews ?: emptyList()
        }
        return overviews.isNotEmpty() && overviews.all { overview ->
            overview.totalCount != null && overview.totalCount == overview.skippedCount
        }
    }

    companion object {
        internal const val RESULT_JSON_FILE_NAME = "result.json"
        fun create(
            targetRunId: String,
            hostRunId: String,
            githubToken: String,
            googleCloudCredentials: String,
            ioDispatcher: CoroutineDispatcher,
            outputFolder: File?,
            githubOwner: String,
            githubRepo: String,
            bucketName: String,
            bucketPath: String,
            devicePicker: DevicePicker? = null,
            artifactNameFilter: (String) -> Boolean = { true },
            useTestConfigFiles: Boolean,
            testSuiteTags: List<String>,
            ignoreEmptyTestMatrices: Boolean,
        ): TestRunner {
            val credentials = ServiceAccountCredentials.fromStream(
                googleCloudCredentials.byteInputStream(Charsets.UTF_8)
            )
            val gcpConfig = Config.Gcp(
                credentials = credentials,
                projectId = credentials.projectId
            )
            return TestRunner(
                googleCloudApi = GoogleCloudApi.build(
                    Config.CloudStorage(
                        gcp = gcpConfig,
                        bucketName = bucketName,
                        bucketPath = bucketPath,
                    ),
                    context = ioDispatcher
                ),
                githubApi = GithubApi.build(
                    Config.Github(
                        owner = githubOwner,
                        repo = githubRepo,
                        token = githubToken
                    )
                ),
                firebaseTestLabApi = FirebaseTestLabApi.build(
                    config = Config.FirebaseTestLab(
                        gcp = gcpConfig
                    ),
                ),
                projectId = gcpConfig.projectId,
                datastoreApi = DatastoreApi.build(
                    Config.Datastore(
                        gcp = gcpConfig,
                        testRunObjectKind = "TestRun",
                    ),
                    context = ioDispatcher
                ),
                toolsResultApi = ToolsResultApi.build(
                    config = Config.ToolsResult(
                        gcp = gcpConfig
                    )
                ),
                githubArtifactFilter = { artifact ->
                    artifactNameFilter(artifact.name)
                },
                outputFolder = outputFolder,
                targetRunId = targetRunId,
                hostRunId = hostRunId,
                devicePicker = devicePicker,
                testSchedulerFactory = TestScheduler.createFactory(useTestConfigFiles, testSuiteTags),
                ignoreEmptyTestMatrices = ignoreEmptyTestMatrices,
            )
        }

        /**
         * Specifies an output folder for the given test matrix where its artifacts will be downloaded into.
         */
        internal fun localResultFolderFor(
            matrix: TestMatrix,
            outputFolder: File
        ): File {
            return outputFolder.resolve("testMatrices/${matrix.testMatrixId}")
        }
    }
}
