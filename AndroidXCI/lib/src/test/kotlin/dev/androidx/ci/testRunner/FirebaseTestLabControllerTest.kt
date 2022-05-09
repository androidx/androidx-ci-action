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

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.androidx.ci.fake.FakeBackend
import dev.androidx.ci.firebase.dto.EnvironmentType
import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.testRunner.vo.UploadedApk
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class FirebaseTestLabControllerTest {
    private val fakeBackend = FakeBackend()
    private val firebaseTestLabApi = FirebaseTestLabController(
        firebaseTestLabApi = fakeBackend.fakeFirebaseTestLabApi,
        firebaseProjectId = fakeBackend.firebaseProjectId,
        testMatrixStore = TestMatrixStore(
            firebaseProjectId = fakeBackend.firebaseProjectId,
            datastoreApi = fakeBackend.datastoreApi,
            firebaseTestLabApi = fakeBackend.fakeFirebaseTestLabApi,
            toolsResultApi = fakeBackend.fakeToolsResultApi,
            resultsGcsPrefix = GcsPath("gs://test-results")
        )
    )

    @Test
    fun testPairing() = runBlocking<Unit> {
        val app1Apk = createUploadedApk("app1.apk")
        val app1TestApk = createUploadedApk("app1-androidTest.apk")
        val app2Apk = createUploadedApk("app2.apk")
        val app2TestApk = createUploadedApk("app2-androidTest.apk")
        val placeholderApk = createUploadedApk("placeholder.apk")
        val noAppTestApk = createUploadedApk("no-app-apk-androidTest.apk")
        val apks = listOf(
            app1TestApk,
            createUploadedApk("foo.apk"),
            createUploadedApk("bar.apk"),
            app1Apk,
            app2Apk,
            noAppTestApk,
            app2TestApk,
        )
        val testMatrices = firebaseTestLabApi.pairAndStartTests(
            apks = apks,
            placeholderApk = placeholderApk
        )
        assertThat(testMatrices).hasSize(3)
        val appApkPaths = testMatrices.mapNotNull {
            it.testSpecification.androidInstrumentationTest?.let {
                it.appApk!!.gcsPath to it.testApk.gcsPath
            }
        }
        assertThat(
            appApkPaths
        ).containsExactlyElementsIn(
            listOf(
                app1Apk.gcsPath.path to app1TestApk.gcsPath.path,
                app2Apk.gcsPath.path to app2TestApk.gcsPath.path,
                placeholderApk.gcsPath.path to noAppTestApk.gcsPath.path
            )
        )
    }

    @Test
    fun testDefaultEnvironment() {
        runBlocking {
            val environmentMatrix = firebaseTestLabApi.getDefaultEnvironmentMatrix()
            val androidDevice = environmentMatrix.androidDeviceList?.androidDevices?.first()
                ?: error("No android device found in the environment")
            val catalog = fakeBackend.fakeFirebaseTestLabApi.getTestEnvironmentCatalog(
                environmentType = EnvironmentType.ANDROID,
                projectId = "-"
            )
            val validEnvironment = catalog.androidDeviceCatalog?.models?.any { model ->
                model.id == androidDevice.androidModelId && (
                    model.supportedVersionIds?.contains(androidDevice.androidVersionId) ?: false
                    )
            }
            assertWithMessage(
                "Default device ($androidDevice) should be in the catalog"
            ).that(validEnvironment).isTrue()
        }
    }

    private fun createUploadedApk(
        name: String,
    ) = UploadedApk(
        gcsPath = GcsPath("gs://apk/$name"),
        apkInfo = ApkInfo(
            filePath = "files/$name",
            idHash = name
        )
    )
}
