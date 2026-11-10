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

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.AndroidDeviceList
import dev.androidx.ci.generated.ftl.ClientInfo
import dev.androidx.ci.generated.ftl.ClientInfoDetail
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.GoogleCloudStorage
import dev.androidx.ci.generated.ftl.ResultStorage
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.ftl.TestSpecification
import dev.androidx.ci.testRunner.vo.ApkInfo
import org.junit.Test

// The label keys are spelled out because the FTL-to-DDP comparison queries depend on them.
internal class DdpShadowRunLabelsTest {
    private val tokay = AndroidDevice(
        androidModelId = "tokay",
        androidVersionId = "34",
        locale = "en_US",
        orientation = "portrait"
    )
    private val oriole = AndroidDevice(
        androidModelId = "oriole",
        androidVersionId = "31",
        locale = "en_US",
        orientation = "portrait"
    )

    @Test
    fun create() {
        val labels = createLabels(
            clientInfo = ClientInfo(
                name = "androidx",
                clientInfoDetails = listOf(
                    ClientInfoDetail(key = "testRunConfigName", value = "fooAndroidTest.json")
                )
            )
        )
        assertThat(labels).containsExactlyEntriesIn(
            mapOf(
                "ftl_matrix_id" to "matrix-1",
                "ftl_project_id" to "androidx-dev-prod",
                "dark_launch_experiment" to "androidx_shadow_v1",
                "compatibility_mode" to "androidx_ci",
                "test_apk_sha256" to "test-sha",
                "app_apk_sha256" to "app-sha",
                "testRunConfigName" to "fooAndroidTest.json"
            )
        )
    }

    @Test
    fun create_withoutClientInfo() {
        assertThat(createLabels(clientInfo = null).keys).containsExactly(
            "ftl_matrix_id",
            "ftl_project_id",
            "dark_launch_experiment",
            "compatibility_mode",
            "test_apk_sha256",
            "app_apk_sha256"
        ).inOrder()
    }

    @Test
    fun create_keepsLabelsThatClientInfoDetailsWouldReplace() {
        val labels = createLabels(
            clientInfo = ClientInfo(
                name = "androidx",
                clientInfoDetails = listOf(
                    ClientInfoDetail(key = "ftl_matrix_id", value = "other-matrix")
                )
            )
        )
        assertThat(labels).containsEntry("ftl_matrix_id", "matrix-1")
        assertThat(labels).hasSize(6)
    }

    @Test
    fun create_skipsClientInfoDetailsThatDoNotFit() {
        val details = listOf(
            ClientInfoDetail(key = "", value = "empty key"),
            ClientInfoDetail(key = "k".repeat(33), value = "key too long"),
            ClientInfoDetail(key = "valueTooLong", value = "v".repeat(1025))
        ) + (0 until 12).map {
            ClientInfoDetail(key = "detail$it", value = "value$it")
        }
        val labels = createLabels(
            clientInfo = ClientInfo(name = "androidx", clientInfoDetails = details)
        )
        // DDP allows 16 labels per job: the 6 labels above and the first 10 details that fit.
        assertThat(labels).hasSize(16)
        assertThat(labels.keys).containsAtLeastElementsIn((0 until 10).map { "detail$it" })
        assertThat(labels.keys).containsNoneOf(
            "",
            "k".repeat(33),
            "valueTooLong",
            "detail10",
            "detail11"
        )
    }

    @Test
    fun create_blankMatrixId() {
        val result = kotlin.runCatching {
            createLabels(ftlMatrixId = " ")
        }
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun validate() {
        // Labels at the limits are valid.
        DdpShadowRunLabels.validate(
            (0 until 15).associate { "key$it" to "value" } + ("k".repeat(32) to "v".repeat(1024))
        )
        listOf(
            (0..16).associate { "key$it" to "value" },
            mapOf("" to "value"),
            mapOf("k".repeat(33) to "value"),
            // 17 characters, but 34 bytes in UTF-8.
            mapOf("\u00e9".repeat(17) to "value"),
            mapOf("key" to "v".repeat(1025))
        ).forEach { labels ->
            val result = kotlin.runCatching {
                DdpShadowRunLabels.validate(labels)
            }
            assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun forDevice() {
        val labels = DdpShadowRunLabels.forDevice(
            device = oriole,
            ftlTestMatrices = listOf(
                createTestMatrix(testMatrixId = "tokay-matrix", device = tokay),
                createTestMatrix(testMatrixId = "oriole-matrix", device = oriole)
            ),
            ftlProjectId = "androidx-dev-prod",
            testApk = ApkInfo(filePath = "files/test.apk", idHash = "test-sha"),
            appApk = ApkInfo(filePath = "files/app.apk", idHash = "app-sha"),
            clientInfo = null
        )
        assertThat(labels).containsExactlyEntriesIn(
            mapOf(
                "ftl_matrix_id" to "oriole-matrix",
                "ftl_project_id" to "androidx-dev-prod",
                "dark_launch_experiment" to "androidx_shadow_v1",
                "compatibility_mode" to "androidx_ci",
                "test_apk_sha256" to "test-sha",
                "app_apk_sha256" to "app-sha"
            )
        )
    }

    @Test
    fun forDevice_noTestMatrixForDevice() {
        val labels = DdpShadowRunLabels.forDevice(
            device = tokay,
            ftlTestMatrices = listOf(
                createTestMatrix(testMatrixId = "matrix-1", device = tokay.copy(locale = "de_DE"))
            ),
            ftlProjectId = "androidx-dev-prod",
            testApk = ApkInfo(filePath = "files/test.apk", idHash = "test-sha"),
            appApk = ApkInfo(filePath = "files/app.apk", idHash = "app-sha"),
            clientInfo = null
        )
        assertThat(labels).isEmpty()
    }

    private fun createLabels(
        ftlMatrixId: String = "matrix-1",
        clientInfo: ClientInfo? = null
    ) = DdpShadowRunLabels.create(
        ftlMatrixId = ftlMatrixId,
        ftlProjectId = "androidx-dev-prod",
        testApkSha256 = "test-sha",
        appApkSha256 = "app-sha",
        clientInfo = clientInfo
    )

    private fun createTestMatrix(testMatrixId: String, device: AndroidDevice) = TestMatrix(
        testMatrixId = testMatrixId,
        environmentMatrix = EnvironmentMatrix(
            androidDeviceList = AndroidDeviceList(androidDevices = listOf(device))
        ),
        testSpecification = TestSpecification(),
        resultStorage = ResultStorage(
            googleCloudStorage = GoogleCloudStorage(gcsPath = "gs://results/$testMatrixId")
        )
    )
}
