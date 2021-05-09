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
import dev.androidx.ci.firebase.dto.EnvironmentType
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.AndroidDeviceList
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.testRunner.vo.TestResult
import dev.androidx.ci.testRunner.vo.UploadedApk
import dev.androidx.ci.util.LazyComputedValue
import kotlinx.coroutines.delay
import org.apache.logging.log4j.kotlin.logger

internal class FirebaseTestLabController(
    private val firebaseTestLabApi: FirebaseTestLabApi,
    private val firebaseProjectId: String,
    private val testMatrixStore: TestMatrixStore
) {
    private val logger = logger()

    private val environmentMatrix = LazyComputedValue {
        logger.info { "finding default environment matrix" }
        val catalog = firebaseTestLabApi.getTestEnvironmentCatalog(
            environmentType = EnvironmentType.ANDROID,
            projectId = firebaseProjectId
        )
        logger.info { "received catalog: $catalog" }
        val defaultModel = catalog.androidDeviceCatalog?.models?.first { model ->
            model?.tags?.contains("default") == true
        } ?: error("Cannot find default model in test device catalog:  $catalog")
        val defaultVersion = catalog.androidDeviceCatalog.versions?.first { version ->
            version?.tags?.contains("default") == true
        } ?: error("Cannot find default version in test device catalog: $catalog")
        EnvironmentMatrix(
            androidDeviceList = AndroidDeviceList(
                androidDevices = listOf(
                    AndroidDevice(
                        locale = "en",
                        androidModelId = defaultModel.id,
                        androidVersionId = defaultVersion.id,
                        orientation = "portrait"
                    )
                )
            )
        ).also {
            logger.info { "default matrix:$it" }
        }
    }
    /**
     *
     */
    private suspend fun submitTest(
        appApk: UploadedApk,
        testApk: UploadedApk
    ): TestMatrix {
        val environmentMatrix = environmentMatrix.get()
        logger.info {
            "submitting tests for app: $appApk / test: $testApk"
        }
        return testMatrixStore.getOrCreateTestMatrix(
            appApk = appApk,
            testApk = testApk,
            environmentMatrix = environmentMatrix
        )
    }

    suspend fun collectTestResults(
        matrices: List<TestMatrix>,
        pollIntervalMs: Long
    ): TestResult {
        logger.info {
            "will collect test results for ${ matrices.joinToString(",") { it.testMatrixId ?: "no-id" } }"
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
            val outcomeSummary = updated.outcomeSummary
            if (outcomeSummary != null) {
                completed.add(updated)
                pending.removeAt(nextMatrixIndex)
            } else {
                delay(pollIntervalMs)
            }
            nextMatrixCounter ++
        }
        return TestResult.SuccessfulRun(
            matrices = completed
        )
    }

    /**
     * Matches given APKs by name as test apk and app apk and starts tests for them
     */
    suspend fun pairAndStartTests(apks: List<UploadedApk>): List<TestMatrix> {
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
                    null
                }
            } else {
                null
            }
        }
        return pairs.map {
            submitTest(
                appApk = it.first,
                testApk = it.second
            )
        }
    }

    companion object {
        private const val TEST_APK_SUFFIX = "-androidTest.apk"
    }
}
