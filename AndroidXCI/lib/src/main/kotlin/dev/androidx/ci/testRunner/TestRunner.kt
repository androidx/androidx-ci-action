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
import dev.androidx.ci.gcloud.GoogleCloudApi
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
class TestRunner(
    private val googleCloudApi: GoogleCloudApi,
    private val githubApi: GithubApi,
    datastoreApi: DatastoreApi,
    firebaseTestLabApi: FirebaseTestLabApi,
    firebaseProjectId: String,
    /**
     * The workflow run id from github
     */
    private val runId: String,
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
        firebaseProjectId = firebaseProjectId,
        datastoreApi = datastoreApi,
        firebaseTestLabApi = firebaseTestLabApi,
        resultsGcsPrefix = googleCloudApi.getGcsPath("ftl/$runId")
    )
    private val apkStore = ApkStore(googleCloudApi)
    private val testLabController = FirebaseTestLabController(
        firebaseTestLabApi = firebaseTestLabApi,
        firebaseProjectId = firebaseProjectId,
        testMatrixStore = testMatrixStore
    )

    /**
     * Runs all the test. This never throws, instead, returns an error result if something goes
     * wrong.
     */
    suspend fun runTests(): TestResult {
        logger.trace("start running tests")
        var statusReporter: StatusReporter? = null
        val result = try {
            val runInfo = githubApi.runInfo(runId)
            logger.info {
                "Run details: $runInfo"
            }
            statusReporter = StatusReporter(
                githubApi = githubApi,
                runInfo = runInfo,
            )
            statusReporter.onStart()
            val artifactsResponse = githubApi.artifacts(runInfo.id)
            val allTestMatrices = artifactsResponse.artifacts
                .filter(githubArtifactFilter)
                .flatMap { artifact ->
                    logger.info { "will upload apks for $artifact" }
                    val uploadedApks = uploadApksToGoogleCloud(artifact)
                    logger.info { "will start tests for these apks: $uploadedApks" }
                    testLabController.pairAndStartTests(uploadedApks).also { testMatrices ->
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
        logger.trace("done running tests, will upload result to gcloud")

        try {
            val resultJson = result.toJson().toByteArray(Charsets.UTF_8)
            googleCloudApi.upload(
                "final-results/$runId/testResult.json",
                resultJson
            )
            outputFolder?.resolve("result.json")?.writeBytes(resultJson)

        } catch (th: Throwable) {
            logger.error("error while uploading results ${th.stackTraceToString()}")
        }
        statusReporter?.onFinsh(result)
        return result
    }

    private suspend fun uploadApksToGoogleCloud(artifact: ArtifactsResponse.Artifact): List<UploadedApk> {
        return coroutineScope {
            val uploads = githubApi.zipArchiveStream(artifact.archiveDownloadUrl)
                .filter {
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
        fun create(
            runId: String,
            githubToken: String,
            googleCloudCredentials: String,
            ioDispatcher: CoroutineDispatcher,
            outputFolder: File?
        ): TestRunner {
            val credentials = ServiceAccountCredentials.fromStream(
                googleCloudCredentials.byteInputStream(Charsets.UTF_8)
            )
            return TestRunner(
                googleCloudApi = GoogleCloudApi.build(
                    Config.GCloud(
                        credentials = credentials,
                        bucketName = "androidx-ftl-test-results",
                        bucketPath = "github-ci-action"
                    ),
                    context = ioDispatcher
                ),
                githubApi = GithubApi.build(
                    Config.Github(
                        owner = "androidX",
                        repo = "androidx",
                        token = githubToken
                    )
                ),
                firebaseTestLabApi = FirebaseTestLabApi.build(
                    config = Config.FirebaseTestLab(
                        credentials = credentials
                    )
                ),
                firebaseProjectId = "androidx-dev-prod",
                datastoreApi = DatastoreApi.build(
                    Config.Datastore(
                        credentials = credentials
                    ),
                    context = ioDispatcher
                ),
                githubArtifactFilter = {
                    it.name.contains("artifacts_room")
                },
                outputFolder = outputFolder,
                runId = runId
            )
        }
    }
}
