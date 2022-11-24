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

import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.gcloud.GoogleCloudApi
import dev.androidx.ci.gcloud.download
import dev.androidx.ci.testRunner.vo.TestResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.logging.log4j.kotlin.logger
import java.io.File

/**
 * Helper class to download test artifacts for a given [TestResult] object.
 */
internal class TestResultDownloader(
    private val googleCloudApi: GoogleCloudApi,
) {
    private val logger = logger()
    suspend fun downloadTestResults(
        outputFolder: File,
        result: TestResult,
        clearOutputFolder: Boolean
    ): List<DownloadedTestResults> {
        if (clearOutputFolder) {
            if (outputFolder.exists()) {
                outputFolder.deleteRecursively()
            }
        }
        outputFolder.mkdirs()
        val resultJson = result.toJson().toByteArray(Charsets.UTF_8)
        outputFolder.resolve(TestRunner.RESULT_JSON_FILE_NAME).writeBytes(resultJson)
        // download test artifacts
        if (result is TestResult.CompleteRun) {
            logger.info("will download test artifacts")
            return coroutineScope {
                val artifactDownloads = result.matrices.map { testMatrix ->
                    async {
                        logger.info {
                            "Downloading artifacts for ${testMatrix.testMatrixId}"
                        }
                        val downloadFolder = TestRunner.localResultFolderFor(
                            matrix = testMatrix,
                            outputFolder = outputFolder
                        ).also {
                            it.mkdirs()
                        }
                        googleCloudApi.download(
                            gcsPath = GcsPath(testMatrix.resultStorage.googleCloudStorage.gcsPath),
                            target = downloadFolder,
                            filter = { name ->
                                // these are logs per test, they are plenty in numbers so lets not download them
                                !name.contains("test_cases")
                            }
                        )
                        logger.info {
                            "Downloaded artifacts for ${testMatrix.testMatrixId} into $downloadFolder"
                        }
                        DownloadedTestResults.buildFrom(
                            testMatrixId = testMatrix.testMatrixId!!,
                            folder = downloadFolder
                        )
                    }
                }
                artifactDownloads.awaitAll()
            }
        }
        return emptyList()
    }
}
