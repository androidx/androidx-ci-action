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

import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.FirestoreApi
import dev.androidx.ci.gcloud.GoogleCloudApi
import dev.androidx.ci.github.GithubApi
import dev.androidx.ci.github.dto.ArtifactsResponse
import dev.androidx.ci.github.zipArchiveStream
import dev.androidx.ci.testRunner.vo.TestResult
import dev.androidx.ci.testRunner.vo.UploadedApk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.logging.log4j.kotlin.logger
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
    firestoreApi: FirestoreApi,
    firebaseTestLabApi: FirebaseTestLabApi,
    firebaseProjectId: String,
    private val runId: String,
    private val artifactFilter: ((ArtifactsResponse.Artifact) -> Boolean) = { true },
) {
    private val logger = logger()
    private val testMatrixStore = TestMatrixStore(
        firebaseProjectId = firebaseProjectId,
        firestoreApi = firestoreApi,
        firebaseTestLabApi = firebaseTestLabApi,
        resultsGcsPrefix = googleCloudApi.getGcsPath("results")
    )
    private val apkStore = ApkStore(googleCloudApi)
    // TODO get this path somehow from the project, google cloud api has it
    private val testLabController = FirebaseTestLabController(
        firebaseTestLabApi = firebaseTestLabApi,
        firebaseProjectId = firebaseProjectId,
        testMatrixStore = testMatrixStore
    )

    /**
     * Runs all the test. This never throws, instead, returns an error result if something goes wrong.
     */
    suspend fun runTests(): TestResult {
        logger.trace("start running tests")
        val result = try {
            val artifactsResponse = githubApi.artifacts(runId)
            val allTestMatrices = artifactsResponse.artifacts.filter(artifactFilter).flatMap { artifact ->
                logger.info { "will upload apks for $artifact" }
                val uploadedApks = uploadApksToGoogleCloud(artifact)
                logger.info { "will start tests for these apks: $uploadedApks" }
                testLabController.pairAndStartTests(uploadedApks).also { testMatrices ->
                    logger.info { "started all tests for $testMatrices" }
                }
            }
            logger.info("will wait for test results")
            testLabController.collectTestResults(allTestMatrices, TimeUnit.SECONDS.toMillis(10))
        } catch (th: Throwable) {
            logger.error("exception in test run", th)
            TestResult.UnexpectedFailure(th)
        }
        googleCloudApi.upload("completeResult.txt", result.toString().toByteArray(Charsets.UTF_8))
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
}
