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

import dev.androidx.ci.util.GoogleCloudCredentialsRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

internal class TestRunnerServicePlayground {
    @get:Rule
    val playgroundCredentialsRule = GoogleCloudCredentialsRule()

    lateinit var testRunnerService: TestRunnerService
    @Before
    fun initTestService() {
        testRunnerService = TestRunnerService.create(
            credentials = playgroundCredentialsRule.gcpConfig.credentials,
            firebaseProjectId = playgroundCredentialsRule.gcpConfig.projectId,
            bucketName = "androidx-ftl-aosp-bucket-2",
            bucketPath = "localRuns",
            gcsResultPath = "yigit",
            logHttpCalls = true
        )
    }

    @Test
    fun manual() {
        val apkPath = File(
            "/Users/yboyar/src/androidx-main/frameworks/support/out/room-playground/room-playground/room/room-runtime/build/outputs/apk/androidTest/debug/room-runtime-debug-androidTest.apk"
        )
        runBlocking {
            val l3 = testRunnerService.runTest(
                testApk = apkPath,
                appApk = null,
                devicePicker = {
                    listOf(
                        FTLTestDevices.PIXEL2_API_26_VIRTUAL
                    )
                },
                localDownloadFolder = File("/Users/yboyar/src/androidx-ci-action/AndroidXCI/real-test-out")
            )
            println(l3.downloads)
        }
    }

    /**
     * Handy class to regenerate [FTLTestDevices].
     */
    @Test
    fun buildCommonDevices() {
        runBlocking {
            val ftlDevicesCode = testRunnerService.testLabController.getCatalog()
                .androidDeviceCatalog
                ?.models
                ?.flatMap { model ->
                    if (model.manufacturer?.lowercase(Locale.US) == "google" ||
                        model.id.startsWith("Nexus") ||
                        model.id.startsWith("Pixel")
                    ) {
                        model.supportedVersionIds?.map { sdk ->
                            """
                            val ${model.id.uppercase(Locale.US)}_API_${sdk}_${model.form} = AndroidDevice(
                                id = "${model.id}",
                                sdk = "$sdk"
                            )
                            """.trimIndent()
                        } ?: emptyList()
                    } else {
                        emptyList()
                    }
                }?.joinToString("\n")

            println(ftlDevicesCode)
        }
    }
}
