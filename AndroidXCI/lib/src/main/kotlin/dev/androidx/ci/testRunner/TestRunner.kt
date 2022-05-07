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
import dev.androidx.ci.github.zipArchiveStream
import dev.androidx.ci.testRunner.vo.TestResult
import dev.androidx.ci.testRunner.vo.UploadedApk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry

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
    private val githubArtifactFilter: ((ArtifactsResponse.Artifact) -> Boolean) = { true },
    /**
     * The directory where results will be saved locally
     */
    private val outputFolder: File? = null,
) {
    private val logger = logger()
    private val testMatrixStore = TestMatrixStore(
        firebaseProjectId = projectId,
        datastoreApi = datastoreApi,
        firebaseTestLabApi = firebaseTestLabApi,
        toolsResultApi = toolsResultApi,
        resultsGcsPrefix = googleCloudApi.getGcsPath("ftl/$targetRunId")
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
                    logger.info { "will upload apks for $artifact" }
                    val uploadedApks = uploadApksToGoogleCloud(artifact)
                    logger.info { "will start tests for these apks: $uploadedApks" }
                    testLabController.pairAndStartTests(
                        apks = uploadedApks,
                        placeholderApk = apkStore.getPlaceholderApk()
                    ).also { testMatrices ->
                        logger.info { "started all tests for $testMatrices" }
                    }
                }
            logger.info("will wait for test results")
            testLabController.collectTestResults(
                matrices = allTestMatrices,
                pollIntervalMs = TimeUnit.SECONDS.toMillis(10)
            )
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
                    result = result
                )
            }
            statusReporter.reportEnd(result)
        } catch (th: Throwable) {
            logger.error("unexpected error while writing results", th)
        }
        return result
    }

    private suspend fun uploadApksToGoogleCloud(artifact: ArtifactsResponse.Artifact): List<UploadedApk> {
        return coroutineScope {
            val uploads = githubApi.zipArchiveStream(
                path = artifact.archiveDownloadUrl,
                unwrapNestedZipEntries = true
            ).filter {
                it.entry.name.endsWith(".apk")
            }.map {
                uploadApkToGcsAsync(it.entry, it.bytes)
            }.toList()
            uploads.awaitAll()
        }
    }

    private fun CoroutineScope.uploadApkToGcsAsync(
        zipEntry: ZipEntry,
        bytes: ByteArray,
    ) = async {
        apkStore.uploadApk(
            name = zipEntry.name,
            bytes = bytes
        )
    }

    companion object {
        const val RESULT_JSON_FILE_NAME = "result.json"
        private val ALLOWED_ARTIFACTS = listOf(
            "artifacts_activity",
            "artifacts_fragment",
            "artifacts_lifecycle",
            "artifacts_navigation",
            "artifacts_room"
        )
        fun create(
            targetRunId: String,
            hostRunId: String,
            githubToken: String,
            googleCloudCredentials: String,
            ioDispatcher: CoroutineDispatcher,
            outputFolder: File?,
            githubOwner: String,
            githubRepo: String
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
                        bucketName = "androidx-ftl-test-results",
                        bucketPath = "github-ci-action",
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
                    )
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
                    ALLOWED_ARTIFACTS.any {
                        artifact.name.contains(it)
                    }
                },
                outputFolder = outputFolder,
                targetRunId = targetRunId,
                hostRunId = hostRunId
            )
        }

        /**
         * Specifies an output folder for the given test matrix where its artifacts will be downloaded into.
         */
        fun localResultFolderFor(
            matrix: TestMatrix,
            outputFolder: File
        ): File {
            return outputFolder.resolve("testMatrices/${matrix.testMatrixId}")
        }
    }
}
