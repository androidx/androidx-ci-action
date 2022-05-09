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

import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.generated.ftl.AndroidInstrumentationTest
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.FileReference
import dev.androidx.ci.generated.ftl.GoogleCloudStorage
import dev.androidx.ci.generated.ftl.ResultStorage
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.ftl.TestSpecification
import dev.androidx.ci.generated.ftl.ToolResultsHistory
import dev.androidx.ci.testRunner.dto.TestRun
import dev.androidx.ci.testRunner.dto.toEntity
import dev.androidx.ci.testRunner.dto.toTestRun
import dev.androidx.ci.testRunner.vo.UploadedApk
import org.apache.logging.log4j.kotlin.logger
import retrofit2.HttpException
import java.util.UUID

/**
 * Maintains test matrices by also adding a caching layer over FTL to avoid re-creating test
 * matrices for the same configuration.
 */
internal class TestMatrixStore(
    private val firebaseProjectId: String,
    private val datastoreApi: DatastoreApi,
    private val firebaseTestLabApi: FirebaseTestLabApi,
    toolsResultApi: ToolsResultApi,
    private val resultsGcsPrefix: GcsPath,
) {
    private val logger = logger()
    private val toolsResultStore = ToolsResultStore(
        firebaseProjectId = firebaseProjectId,
        toolsResultApi = toolsResultApi
    )
    /**
     * Creates a TestMatrix for the given configuration or returns an existing one if we've run the same test with the
     * same APKs and environment configuration.
     */
    suspend fun getOrCreateTestMatrix(
        appApk: UploadedApk,
        testApk: UploadedApk,
        environmentMatrix: EnvironmentMatrix
    ): TestMatrix {
        val testRunId = TestRun.createId(
            datastoreApi = datastoreApi,
            environment = environmentMatrix,
            appApk = appApk.apkInfo,
            testApk = testApk.apkInfo
        )
        logger.trace {
            "test run id: $testRunId"
        }

        getExistingTestMatrix(testRunId)?.let {
            logger.info("found existing test matrix: ${it.testMatrixId}")
            return it
        }
        logger.trace {
            "No test run history for $testRunId, creating anew one."
        }
        val newTestMatrix = firebaseTestLabApi.createTestMatrix(
            projectId = firebaseProjectId,
            requestId = UUID.randomUUID().toString(),
            testMatrix = createNewTestMatrix(
                testRunKey = testRunId,
                environmentMatrix = environmentMatrix,
                appApk = appApk,
                testApk = testApk
            )
        )
        logger.info {
            "created test matrix: $newTestMatrix"
        }
        // save it to cache, we don't worry about races here much such that if another instance happens to be creating
        // the exact same test, one of them will win but that is OK since they'll each use their own test matrices and
        // followup calls will re-use the winner of this race.
        val testRun = TestRun(
            id = testRunId,
            testMatrixId = checkNotNull(newTestMatrix.testMatrixId) {
                "newly created test matrix should not have null id $newTestMatrix"
            }
        )
        datastoreApi.put(testRun.toEntity())
        logger.info {
            "saved test matrix info: $testRun"
        }
        return newTestMatrix
    }

    /**
     * Returns an existing TestMatrix for the given [testRunId].
     *
     * This happens in two steps.
     * We first check the FireStore backend to see if it already exists, if so, get the testMatrixId from there.
     * Then we query FTL to get the TestMatrix object. Notice that test matrices on FTL are subject to its own retention
     * policy hence having a record in firestore does not guarantee having it in Firebase Test Lab.
     */
    private suspend fun getExistingTestMatrix(
        testRunId: TestRun.Id
    ): TestMatrix? {
        val testMatrixId = datastoreApi.get(
            testRunId.key
        )?.toTestRun()?.testMatrixId ?: return null

        return try {
            firebaseTestLabApi.getTestMatrix(
                projectId = firebaseProjectId,
                testMatrixId = testMatrixId
            )
        } catch (ex: HttpException) {
            if (ex.code() == 404) {
                null
            } else {
                throw ex
            }
        }
    }

    /**
     * Creates a new [TestMatrix] with the given configuration.
     * This [TestMatrix] is pushed to the FTL API to trigger a create.
     */
    private suspend fun createNewTestMatrix(
        testRunKey: TestRun.Id,
        environmentMatrix: EnvironmentMatrix,
        appApk: UploadedApk,
        testApk: UploadedApk
    ): TestMatrix {
        val packageName = firebaseTestLabApi.getApkDetails(
            FileReference(
                gcsPath = testApk.gcsPath.path
            )
        ).apkDetail?.apkManifest?.packageName ?: error("Cannot find package name for $testApk")
        val historyId = toolsResultStore.getHistoryId(
            packageName
        )
        return TestMatrix(
            projectId = firebaseProjectId,
            flakyTestAttempts = 2,
            testSpecification = TestSpecification(
                disableVideoRecording = true,
                androidInstrumentationTest = AndroidInstrumentationTest(
                    appApk = FileReference(
                        gcsPath = appApk.gcsPath.path
                    ),
                    testApk = FileReference(
                        gcsPath = testApk.gcsPath.path
                    )
                )
            ),
            environmentMatrix = environmentMatrix,
            resultStorage = ResultStorage(
                googleCloudStorage = GoogleCloudStorage(
                    gcsPath = testRunKey.resultGcsPath().path
                ),
                toolResultsHistory = ToolResultsHistory(
                    projectId = firebaseProjectId,
                    historyId = historyId
                )
            )
        )
    }

    private fun TestRun.Id.resultGcsPath() = (resultsGcsPrefix + key.name)
}
