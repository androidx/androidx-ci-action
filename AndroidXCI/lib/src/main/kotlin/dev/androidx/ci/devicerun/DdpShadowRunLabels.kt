/*
 * Copyright 2026 The Android Open Source Project
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

package dev.androidx.ci.devicerun

import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.ClientInfo
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.testRunner.vo.ApkInfo
import org.apache.logging.log4j.kotlin.logger

/**
 * Labels that pair a Developer Device Platform (DDP) shadow job with the Firebase Test Lab (FTL)
 * test matrix that runs the same tests on the same device.
 *
 * DDP returns job labels in `JobReport.labels` and stores them with the job, so FTL and DDP results
 * can be compared side by side while tests run on both backends. Labels can only be set when a
 * session is created.
 */
internal object DdpShadowRunLabels {
    /** FTL test matrix that the DDP job mirrors. */
    const val FTL_MATRIX_ID = "ftl_matrix_id"

    /** GCP project that ran the FTL test matrix. */
    const val FTL_PROJECT_ID = "ftl_project_id"

    /** Groups DDP jobs that share a configuration, e.g. in the FTL-to-DDP parity dashboard. */
    const val DARK_LAUNCH_EXPERIMENT = "dark_launch_experiment"

    /** How the DDP job was configured, e.g. in the FTL-to-DDP parity dashboard. */
    const val COMPATIBILITY_MODE = "compatibility_mode"

    /** SHA-256 of the test APK (see [ApkInfo.idHash]). */
    const val TEST_APK_SHA256 = "test_apk_sha256"

    /** SHA-256 of the app APK (see [ApkInfo.idHash]). */
    const val APP_APK_SHA256 = "app_apk_sha256"

    /**
     * Experiment name for DDP jobs that shadow FTL test matrices. Change it whenever the DDP job
     * configuration changes so that results can be compared per configuration.
     */
    const val DEFAULT_EXPERIMENT = "androidx_shadow_v1"

    /** Marks DDP jobs that use AndroidX's own configuration rather than a translated FTL matrix. */
    const val DEFAULT_COMPATIBILITY_MODE = "androidx_ci"

    /** Limits that the DDP API enforces on `JobConfig.labels`. */
    const val MAX_LABELS = 16
    const val MAX_KEY_BYTES = 32
    const val MAX_VALUE_BYTES = 1024

    private val logger = logger()

    /**
     * Returns the labels for the DDP job that runs on [device], pairing it with the test matrix in
     * [ftlTestMatrices] that runs on the same device. Returns an empty map if there is no such test
     * matrix.
     */
    fun forDevice(
        device: AndroidDevice,
        ftlTestMatrices: List<TestMatrix>,
        ftlProjectId: String,
        testApk: ApkInfo,
        appApk: ApkInfo,
        clientInfo: ClientInfo?
    ): Map<String, String> {
        val ftlMatrixId = ftlTestMatrices.firstOrNull { it.runsOn(device) }?.testMatrixId
        if (ftlMatrixId.isNullOrBlank()) {
            logger.warn {
                "No FTL test matrix runs on $device, so its DDP job is not paired with FTL."
            }
            return emptyMap()
        }
        return create(
            ftlMatrixId = ftlMatrixId,
            ftlProjectId = ftlProjectId,
            testApkSha256 = testApk.idHash,
            appApkSha256 = appApk.idHash,
            clientInfo = clientInfo
        )
    }

    /**
     * Returns the labels for a DDP job that mirrors the FTL test matrix [ftlMatrixId].
     *
     * The details of [clientInfo] are copied as labels too (e.g. AndroidX's `testRunConfigName`),
     * so that both runs carry the same metadata. Details that would replace one of the labels
     * above or exceed DDP's limits are skipped.
     *
     * @throws IllegalArgumentException if [ftlMatrixId] is blank
     */
    fun create(
        ftlMatrixId: String,
        ftlProjectId: String,
        testApkSha256: String,
        appApkSha256: String,
        clientInfo: ClientInfo?,
        experiment: String = DEFAULT_EXPERIMENT,
        compatibilityMode: String = DEFAULT_COMPATIBILITY_MODE
    ): Map<String, String> {
        require(ftlMatrixId.isNotBlank()) { "ftlMatrixId must not be blank" }
        val labels = linkedMapOf(
            FTL_MATRIX_ID to ftlMatrixId,
            FTL_PROJECT_ID to ftlProjectId,
            DARK_LAUNCH_EXPERIMENT to experiment,
            COMPATIBILITY_MODE to compatibilityMode,
            TEST_APK_SHA256 to testApkSha256,
            APP_APK_SHA256 to appApkSha256
        )
        clientInfo?.clientInfoDetails?.forEach { detail ->
            val key: String? = detail.key
            val value: String = detail.value.orEmpty()
            if (key.isNullOrEmpty() || key in labels) {
                return@forEach
            }
            if (labels.size < MAX_LABELS && fitsLimits(key, value)) {
                labels[key] = value
            } else {
                logger.warn { "Not copying FTL client info detail '$key' to the DDP job labels." }
            }
        }
        validate(labels)
        return labels
    }

    /**
     * Checks [labels] against DDP's limits so that a bad label fails here rather than when the
     * session is created.
     *
     * @throws IllegalArgumentException if any limit is exceeded
     */
    fun validate(labels: Map<String, String>) {
        require(labels.size <= MAX_LABELS) {
            "DDP allows at most $MAX_LABELS labels per job, got ${labels.size}: ${labels.keys}"
        }
        labels.forEach { (key, value) ->
            require(fitsLimits(key, value)) {
                "DDP label '$key' must have a non-empty key of at most $MAX_KEY_BYTES bytes and a " +
                    "value of at most $MAX_VALUE_BYTES bytes."
            }
        }
    }

    private fun fitsLimits(key: String, value: String) =
        key.isNotEmpty() && key.utf8Size() <= MAX_KEY_BYTES && value.utf8Size() <= MAX_VALUE_BYTES

    private fun TestMatrix.runsOn(device: AndroidDevice): Boolean {
        val environment: EnvironmentMatrix? = environmentMatrix
        return environment?.androidDeviceList?.androidDevices.orEmpty().any {
            it.androidModelId == device.androidModelId &&
                it.androidVersionId == device.androidVersionId &&
                it.locale == device.locale &&
                it.orientation == device.orientation
        }
    }

    private fun String.utf8Size() = toByteArray(Charsets.UTF_8).size
}
