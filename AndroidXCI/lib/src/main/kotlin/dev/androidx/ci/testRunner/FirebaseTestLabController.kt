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
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.dto.EnvironmentType
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.AndroidDeviceList
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.TestEnvironmentCatalog
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.testRunner.vo.TestResult
import dev.androidx.ci.testRunner.vo.UploadedApk
import dev.androidx.ci.util.LazyComputedValue
import kotlinx.coroutines.delay
import org.apache.logging.log4j.kotlin.logger

/**
 * Controller to understand TestMatrix states, picks the environment matrix (set of devices to run
 * the test on) and can observe multiple TestMatrices for their results.
 */
internal class FirebaseTestLabController(
    private val firebaseTestLabApi: FirebaseTestLabApi,
    private val firebaseProjectId: String,
    private val testMatrixStore: TestMatrixStore
) {
    private val logger = logger()

    private val testCatalog = LazyComputedValue {
        firebaseTestLabApi.getTestEnvironmentCatalog(
            environmentType = EnvironmentType.ANDROID,
            projectId = firebaseProjectId
        )
    }

    private val defaultDevicePicker = { catalog: TestEnvironmentCatalog ->
        logger.info { "received catalog: $catalog" }
        val defaultModel = catalog.androidDeviceCatalog?.models?.first { model ->
            model.tags?.contains("default") == true
        } ?: error("Cannot find default model in test device catalog:  $catalog")
        val defaultModelVersion = defaultModel.supportedVersionIds
            ?.mapNotNull { it.toIntOrNull() }
            ?.maxOrNull()
            ?: error("Cannot find supported version for $defaultModel in test device catalog: $catalog")
        listOf(
            AndroidDevice(
                locale = "en",
                androidModelId = defaultModel.id,
                androidVersionId = defaultModelVersion.toString(),
                orientation = "portrait"
            )
        )
    }

    private suspend fun DevicePicker.pickDevices(): List<AndroidDevice> {
        return testCatalog.get().let(this)
    }

    private fun List<AndroidDevice>.createEnvironmentMatrix() = EnvironmentMatrix(
        androidDeviceList = AndroidDeviceList(
            androidDevices = this
        )
    )

    @VisibleForTesting
    internal suspend fun getDefaultEnvironmentMatrix(): EnvironmentMatrix {
        return testCatalog.get().let(defaultDevicePicker).createEnvironmentMatrix()
    }

    @VisibleForTesting
    internal suspend fun getCatalog(): TestEnvironmentCatalog {
        return testCatalog.get()
    }

    /**
     * Enqueues [TestMatrix]es to run the tests for the given APKs on the devices picked by
     * [devicePicker]. Note that, for each device, a separate [TestMatrix] is created to optimize
     * cacheability (e.g. adding a new device wont invalidate test runs on other devices).
     *
     * Note that, if same exact test was run before, its results will be re-used.
     *
     * @param devicePicker The [DevicePicker] that will decide on which [AndroidDevice]s to
     * use to run the tests.
     */
    suspend fun submitTests(
        appApk: UploadedApk,
        testApk: UploadedApk,
        devicePicker: DevicePicker? = null
    ): List<TestMatrix> {
        val devices = (devicePicker ?: defaultDevicePicker).pickDevices()
        logger.info {
            "submitting tests for app: $appApk / test: $testApk on $devices"
        }
        // create 1 TestMatrix for each device so that they can be better cached.
        return devices.map {
            testMatrixStore.getOrCreateTestMatrix(
                appApk = appApk,
                testApk = testApk,
                environmentMatrix = listOf(it).createEnvironmentMatrix()
            )
        }
    }

    suspend fun collectTestResultsByTestMatrixIds(
        testMatrixIds: List<String>,
        pollIntervalMs: Long
    ): TestResult = collectTestResults(
        matrices = testMatrixIds.map { testMatrixId ->
            firebaseTestLabApi.getTestMatrix(
                projectId = firebaseProjectId,
                testMatrixId = testMatrixId
            )
        },
        pollIntervalMs = pollIntervalMs
    )

    /**
     * Collects the results for the given list of TestMatrices.
     *
     * This method will poll FTL backend until all of the given TestMatrices has an outcome.
     */
    suspend fun collectTestResults(
        matrices: List<TestMatrix>,
        pollIntervalMs: Long
    ): TestResult {
        logger.info {
            "will collect test results for " +
                matrices.joinToString(",") { it.testMatrixId ?: "no-id" }
        }
        val pending = matrices.toMutableList()
        val completed = mutableListOf<TestMatrix>()
        var nextMatrixCounter = 0
        while (pending.isNotEmpty()) {
            logger.info {
                "checking matrices. pending: ${pending.size} / completed: ${completed.size}"
            }
            val nextMatrixIndex = nextMatrixCounter % pending.size
            val nextToCheck = pending[nextMatrixIndex]
            logger.info {
                "next matrix to check: ${nextToCheck.testMatrixId}"
            }
            val updated = firebaseTestLabApi.getTestMatrix(
                projectId = firebaseProjectId,
                testMatrixId = nextToCheck.testMatrixId!!
            )
            logger.info {
                "updated matrix: $updated"
            }
            if (updated.isComplete()) {
                completed.add(updated)
                pending.removeAt(nextMatrixIndex)
            } else {
                delay(pollIntervalMs)
            }
            nextMatrixCounter ++
        }
        return TestResult.CompleteRun(
            matrices = completed
        )
    }

    /**
     * Matches given APKs by name as test apk and app apk and starts tests for them
     */
    suspend fun pairAndStartTests(
        apks: List<UploadedApk>,
        placeholderApk: UploadedApk,
        devicePicker: DevicePicker? = null
    ): List<TestMatrix> {
        val pairs = apks.mapNotNull { uploadedApk ->
            val isTestApk = uploadedApk.apkInfo.filePath.endsWith(TEST_APK_SUFFIX)
            if (isTestApk) {
                // find the app apk
                val targetName = uploadedApk.apkInfo.filePath.replace(TEST_APK_SUFFIX, ".apk")
                val appApk = apks.firstOrNull {
                    it.apkInfo.filePath == targetName
                }
                if (appApk != null) {
                    appApk to uploadedApk
                } else {
                    logger.info("using placeholder app apk for $uploadedApk")
                    placeholderApk to uploadedApk
                }
            } else {
                null
            }
        }
        return pairs.flatMap {
            submitTests(
                appApk = it.first,
                testApk = it.second,
                devicePicker = devicePicker
            )
        }
    }

    companion object {
        private const val TEST_APK_SUFFIX = "-androidTest.apk"
    }
}

internal val incompleteTestStates = setOf(
    TestMatrix.State.TEST_STATE_UNSPECIFIED,
    TestMatrix.State.VALIDATING,
    TestMatrix.State.PENDING,
    TestMatrix.State.RUNNING,
)

internal fun TestMatrix.isComplete() = state != null && state !in incompleteTestStates
