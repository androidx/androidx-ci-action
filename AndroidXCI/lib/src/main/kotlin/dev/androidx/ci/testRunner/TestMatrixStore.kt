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

import com.google.common.annotations.VisibleForTesting
import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.generated.ftl.AndroidInstrumentationTest
import dev.androidx.ci.generated.ftl.ClientInfo
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.FileReference
import dev.androidx.ci.generated.ftl.GoogleCloudStorage
import dev.androidx.ci.generated.ftl.ResultStorage
import dev.androidx.ci.generated.ftl.ShardingOption
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.ftl.TestSetup
import dev.androidx.ci.generated.ftl.TestSpecification
import dev.androidx.ci.generated.ftl.ToolResultsHistory
import dev.androidx.ci.testRunner.dto.TestRun
import dev.androidx.ci.testRunner.dto.toEntity
import dev.androidx.ci.testRunner.dto.toTestRun
import dev.androidx.ci.testRunner.vo.DeviceSetup
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
    private val resultsGcsPrefix: GcsPath
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
        environmentMatrix: EnvironmentMatrix,
        clientInfo: ClientInfo?,
        sharding: ShardingOption?,
        deviceSetup: DeviceSetup?,
        pullScreenshots: Boolean = false,
        cachedTestMatrixFilter: CachedTestMatrixFilter = { true },
        testTargets: List<String>? = null,
        flakyTestAttempts: Int = 2,
        testTimeoutSeconds: Int = 2700
    ): TestMatrix {

        val testRunId = TestRun.createId(
            datastoreApi = datastoreApi,
            environment = environmentMatrix,
            clientInfo = clientInfo,
            sharding = sharding,
            appApk = appApk.apkInfo,
            testApk = testApk.apkInfo,
            deviceSetup = deviceSetup
        )
        logger.trace {
            "test run id: $testRunId"
        }

        val existingTestMatrix = getCachedTestMatrix(testRunId, cachedTestMatrixFilter)
        if (existingTestMatrix != null) {
            return existingTestMatrix
        }
        val newTestMatrix = createNewTestMatrix(
            testRunKey = testRunId,
            environmentMatrix = environmentMatrix,
            clientInfo = clientInfo,
            sharding = sharding,
            deviceSetup = deviceSetup,
            appApk = appApk,
            testApk = testApk,
            pullScreenshots = pullScreenshots,
            testTargets = testTargets,
            flakyTestAttempts = flakyTestAttempts,
            testTimeoutSeconds = testTimeoutSeconds
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
     * Creates a TestMatrix for the given configuration or returns an existing one if we've run the same tests
     * specified in [testTargets] with the same environment configuration.
     */
    suspend fun getOrCreateTestMatrix(
        testMatrix: TestMatrix,
        cachedTestMatrixFilter: CachedTestMatrixFilter = { true },
        testTargets: List<String>,
        flakyTestAttempts: Int = 0
    ): TestMatrix {
        checkNotNull(testMatrix.testMatrixId) {
            "Test matrix id for the base test matrix should not be null"
        }
        val sharding = if (testTargets.isEmpty()) {
            // It's beneficial to keep sharding same as base testMatrix
            // when we are running all the tests instead of a subset
            testMatrix.testSpecification.androidInstrumentationTest?.shardingOption
        } else {
            null
        }
        val testRunId = TestRun.createId(
            datastoreApi = datastoreApi,
            environment = testMatrix.environmentMatrix,
            clientInfo = testMatrix.clientInfo,
            sharding = sharding,
            testSetup = testMatrix.testSpecification.testSetup,
            testTargets = testTargets,
            baseTestMatrixId = testMatrix.testMatrixId
        )
        logger.trace {
            "test run id: $testRunId"
        }

        val existingTestMatrix = getCachedTestMatrix(testRunId, cachedTestMatrixFilter)
        if (existingTestMatrix != null) {
            return existingTestMatrix
        }

        val newTestMatrix = createNewTestMatrix(
            testRunKey = testRunId,
            testMatrix = testMatrix,
            testTargets = testTargets,
            flakyTestAttempts = flakyTestAttempts,
            sharding = sharding
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
     * Check if a [TestMatrix] exists for the given [testRunId]
     * If it does, ensure it is not in an ERROR state
     * and [cachedTestMatrixFilter] allows reusing the testMatrix
     */
    private suspend fun getCachedTestMatrix(
        testRunId: TestRun.Id,
        cachedTestMatrixFilter: CachedTestMatrixFilter,
    ): TestMatrix? {
        getExistingTestMatrix(testRunId)?.let {
            logger.info("found existing test matrix: ${it.testMatrixId} with state: ${it.state}")
            val state = it.state
            // these states are ordered so anything above ERROR is not worth re-using
            if (state != null && state >= TestMatrix.State.ERROR) {
                logger.warn {
                    "Skipping cache for ${it.testMatrixId} because its state is $state"
                }
            } else if (!cachedTestMatrixFilter(it)) {
                logger.info {
                    "Not re-using cached matrix due to filter"
                }
            } else {
                return it
            }
        }
        logger.trace {
            "No test run history for $testRunId or cached TestMatrix is rejected."
        }
        return null
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
        clientInfo: ClientInfo?,
        sharding: ShardingOption?,
        deviceSetup: DeviceSetup?,
        appApk: UploadedApk,
        testApk: UploadedApk,
        pullScreenshots: Boolean = false,
        testTargets: List<String>? = null,
        flakyTestAttempts: Int = 2,
        testTimeoutSeconds: Int = 2700
    ): TestMatrix {
        val packageName = firebaseTestLabApi.getApkDetails(
            FileReference(
                gcsPath = testApk.gcsPath.path
            )
        ).apkDetail?.apkManifest?.packageName ?: error("Cannot find package name for $testApk")
        val historyId = toolsResultStore.getHistoryId(
            packageName
        )
        // Directory on the device that is used to store the output files as defined here:
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:test/screenshot/screenshot/src/main/java/androidx/test/screenshot/ScreenshotTestRule.kt;l=90
        val screenshotsDirectory = "/sdcard/Android/data/$packageName/cache/androidx_screenshots"
        val testSetup = if (deviceSetup != null) {
            if (pullScreenshots) {
                deviceSetup
                    .directoriesToPull
                    .add(screenshotsDirectory)
            }
            deviceSetup.toTestSetup()
        } else {
            TestSetup(
                directoriesToPull = if (pullScreenshots)
                    listOf(screenshotsDirectory)
                else null
            )
        }
        val testSpecification = TestSpecification(
            testTimeout = "${testTimeoutSeconds}s",
            disableVideoRecording = false,
            disablePerformanceMetrics = true, // Not a useful feature for androidx
            androidInstrumentationTest = AndroidInstrumentationTest(
                appApk = FileReference(
                    gcsPath = appApk.gcsPath.path
                ),
                testApk = FileReference(
                    gcsPath = testApk.gcsPath.path
                ),
                shardingOption = sharding,
                testTargets = testTargets
            ),
            testSetup = testSetup
        )
        val resultStorage = ResultStorage(
            googleCloudStorage = GoogleCloudStorage(
                gcsPath = createUniqueResultGcsPath(testRunKey).path
            ),
            toolResultsHistory = ToolResultsHistory(
                projectId = firebaseProjectId,
                historyId = historyId
            )
        )
        return createTestMatrix(
            flakyTestAttempts = flakyTestAttempts,
            testSpecification = testSpecification,
            clientInfo = clientInfo,
            environmentMatrix = environmentMatrix,
            resultStorage = resultStorage
        )
    }

    /**
     * Creates a [TestMatrix] to run the tests specified in [testTargets] list
     * using the same configuration as [testMatrix]
     */
    private suspend fun createNewTestMatrix(
        testRunKey: TestRun.Id,
        testMatrix: TestMatrix,
        testTargets: List<String>? = null,
        flakyTestAttempts: Int = 0,
        sharding: ShardingOption?
    ): TestMatrix {
        logger.trace {
            "test matrix id: ${testMatrix.testMatrixId}"
        }

        val testSpecification = testMatrix.testSpecification.copy(
            androidInstrumentationTest = testMatrix.testSpecification.androidInstrumentationTest?.copy(
                testTargets = testTargets,
                shardingOption = sharding
            )
        )

        val resultStorage = ResultStorage(
            googleCloudStorage = GoogleCloudStorage(
                gcsPath = createUniqueResultGcsPath(testRunKey).path
            ),
            toolResultsHistory = testMatrix.resultStorage.toolResultsHistory
        )

        return createTestMatrix(
            clientInfo = testMatrix.clientInfo,
            environmentMatrix = testMatrix.environmentMatrix,
            testSpecification = testSpecification,
            resultStorage = resultStorage,
            flakyTestAttempts = flakyTestAttempts
        )
    }

    /**
     * Creates a [TestMatrix] using the specified parameters
     */
    suspend fun createTestMatrix(
        clientInfo: ClientInfo?,
        environmentMatrix: EnvironmentMatrix,
        testSpecification: TestSpecification,
        resultStorage: ResultStorage,
        flakyTestAttempts: Int
    ): TestMatrix {
        return firebaseTestLabApi.createTestMatrix(
            projectId = firebaseProjectId,
            requestId = UUID.randomUUID().toString(),
            testMatrix = TestMatrix(
                projectId = firebaseProjectId,
                flakyTestAttempts = flakyTestAttempts,
                testSpecification = testSpecification,
                clientInfo = clientInfo,
                environmentMatrix = environmentMatrix,
                resultStorage = resultStorage
            )
        )
    }

    @VisibleForTesting
    internal fun createUniqueResultGcsPath(testRunKey: TestRun.Id): GcsPath {
        return resultsGcsPrefix + testRunKey.key.name + UUID.randomUUID().toString()
    }
}
