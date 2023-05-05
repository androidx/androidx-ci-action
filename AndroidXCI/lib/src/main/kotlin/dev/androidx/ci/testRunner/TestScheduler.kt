/*
 * Copyright 2023 The Android Open Source Project
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

import com.squareup.moshi.Moshi
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.github.GithubApi
import dev.androidx.ci.github.ZipEntryScope
import dev.androidx.ci.github.dto.ArtifactsResponse
import dev.androidx.ci.github.zipArchiveStream
import dev.androidx.ci.testRunner.vo.DeviceSetup
import dev.androidx.ci.testRunner.vo.UploadedApk
import dev.zacsweers.moshix.reflect.MetadataKotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.logging.log4j.kotlin.logger
import java.util.zip.ZipEntry

/**
 * Helper class to select which tests to run out of an artifact.
 *
 * There are 2 implementations:
 * 1) RunAllTests will select all APKs, pair them based on the names and try to run.
 * 2) ByTestConfig will parse the TestConfig.json files and only run tests specified in that list.
 */
internal sealed interface TestScheduler {
    suspend fun enqueueTests(
        artifact: ArtifactsResponse.Artifact,
    ): List<TestMatrix>
    class PairAndRunAllApks(
        private val githubApi: GithubApi,
        private val firebaseTestLabController: FirebaseTestLabController,
        private val apkStore: ApkStore,
        private val devicePicker: DevicePicker?
    ) : TestScheduler {
        override suspend fun enqueueTests(
            artifact: ArtifactsResponse.Artifact
        ): List<TestMatrix> {
            val apks = uploadApksToGoogleCloud(githubApi, artifact)
            return firebaseTestLabController.pairAndStartTests(
                apks = apks,
                placeholderApk = apkStore.getPlaceholderApk(),
                devicePicker = devicePicker
            )
        }

        private suspend fun uploadApksToGoogleCloud(
            githubApi: GithubApi,
            artifact: ArtifactsResponse.Artifact
        ): List<UploadedApk> {
            return coroutineScope {
                val uploads = githubApi.zipArchiveStream(
                    path = artifact.archiveDownloadUrl,
                    unwrapNestedZipEntries = true
                ).filter {
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

        object Factory : TestScheduler.Factory {
            override fun create(
                githubApi: GithubApi,
                firebaseTestLabController: FirebaseTestLabController,
                apkStore: ApkStore,
                devicePicker: DevicePicker?
            ): TestScheduler {
                return PairAndRunAllApks(
                    githubApi = githubApi,
                    firebaseTestLabController = firebaseTestLabController,
                    apkStore = apkStore,
                    devicePicker = devicePicker
                )
            }
        }
    }

    class RunUsingTestRunConfig(
        private val githubApi: GithubApi,
        private val firebaseTestLabController: FirebaseTestLabController,
        private val apkStore: ApkStore,
        private val devicePicker: DevicePicker?
    ) : TestScheduler {
        override suspend fun enqueueTests(artifact: ArtifactsResponse.Artifact): List<TestMatrix> {
            val testsToBeScheduled = githubApi.zipArchiveStream(
                path = artifact.archiveDownloadUrl,
                unwrapNestedZipEntries = true
            ).filter {
                it.entry.name.endsWith("AndroidTest.json")
            }.mapNotNull {
                adapter.fromJson(it.bytes.toString(Charsets.UTF_8))
            }.filter { testRunConfig ->
                // additional apks are not supported
                testRunConfig.additionalApkKeys.isEmpty().also {
                    if (!it) {
                        logger.info {
                            "TestRunConfigs with additional APKs are not supported. Skipping $testRunConfig"
                        }
                    }
                }
            }.map {
                TestToBeScheduled(it)
            }.toList()
            githubApi.zipArchiveStream(
                path = artifact.archiveDownloadUrl,
                unwrapNestedZipEntries = true
            ).filter {
                it.entry.name.endsWith(".apk")
            }.forEach { zipEntryScope ->
                testsToBeScheduled.forEach {
                    it.processZipEntry(apkStore, zipEntryScope)
                }
            }
            return testsToBeScheduled.flatMap {
                it.trySubmit(
                    firebaseTestLabController = firebaseTestLabController,
                    apkStore = apkStore,
                    devicePicker = devicePicker
                )
            }
        }

        /**
         * A mutable data structure that can collect / upload APKs out of a zip stream.
         * It is created from a [TestRunConfig] and will be called with each zip entry to
         * find its APKs. After the zip is parsed, it will be called to schedule its tests if possible.
         */
        class TestToBeScheduled(
            val config: TestRunConfig
        ) {
            val appApk: PendingApk? = config.appApk?.let { appApk ->
                config.appApkSha256?.let { sha256 ->
                    PendingApk(name = appApk, sha256 = sha256)
                }
            }
            val testApk: PendingApk = PendingApk(
                name = config.testApk,
                sha256 = config.testApkSha256
            )

            suspend fun trySubmit(
                firebaseTestLabController: FirebaseTestLabController,
                apkStore: ApkStore,
                devicePicker: DevicePicker?
            ): List<TestMatrix> {
                val uploadedAppApk = if (appApk == null) {
                    apkStore.getPlaceholderApk()
                } else {
                    appApk.apk.also {
                        if (it == null) {
                            logger.warn("Couldn't find apk for ${config.appApk}, skipping test")
                        }
                    }
                }
                val uploadedTestApk = testApk.apk
                if (uploadedAppApk == null || uploadedTestApk == null) {
                    logger.warn {
                        """Skipping $config because we couldn't find either the app or test apk"""
                    }
                    return emptyList()
                }
                val instrumentationArguments =
                    config.instrumentationArgs.map {
                        DeviceSetup.InstrumentationArgument(
                            key = it.key,
                            value = it.value
                        )
                    }
                return firebaseTestLabController.submitTests(
                    appApk = uploadedAppApk,
                    testApk = uploadedTestApk,
                    clientInfo = null,
                    sharding = null,
                    deviceSetup = DeviceSetup(
                        instrumentationArguments = instrumentationArguments
                    ),
                    devicePicker = devicePicker,
                    pullScreenshots = false
                )
            }

            suspend fun processZipEntry(
                apkStore: ApkStore,
                scope: ZipEntryScope
            ) {
                appApk?.processZipEntry(apkStore, scope)
                testApk.processZipEntry(apkStore, scope)
            }

            class PendingApk(
                val name: String,
                val sha256: String
            ) {
                private var uploadedApk: UploadedApk? = null

                val apk: UploadedApk?
                    get() = uploadedApk

                suspend fun processZipEntry(
                    apkStore: ApkStore,
                    scope: ZipEntryScope
                ) {
                    if (uploadedApk != null) {
                        return
                    }
                    if (scope.entry.name == name) {
                        val alreadyUploaded = apkStore.getUploadedApk(name, sha256)
                        uploadedApk = alreadyUploaded ?: apkStore.uploadApk(name, scope.bytes)
                    }
                }
            }
        }

        data class TestRunConfig(
            val name: String,
            val testApk: String,
            val testApkSha256: String,
            val appApk: String?,
            val appApkSha256: String?,
            val minSdkVersion: Int,
            val instrumentationArgs: List<InstrumentationArg>,
            val testSuiteTags: List<String>,
            val additionalApkKeys: List<String>
        )

        data class InstrumentationArg(
            val key: String,
            val value: String
        )

        companion object {
            private val moshi = Moshi.Builder()
                .add(MetadataKotlinJsonAdapterFactory())
                .build()
            private val adapter = moshi.adapter(TestRunConfig::class.java)
            val Factory = object : Factory {
                override fun create(
                    githubApi: GithubApi,
                    firebaseTestLabController: FirebaseTestLabController,
                    apkStore: ApkStore,
                    devicePicker: DevicePicker?
                ): TestScheduler {
                    return RunUsingTestRunConfig(
                        githubApi = githubApi,
                        firebaseTestLabController = firebaseTestLabController,
                        apkStore = apkStore,
                        devicePicker = devicePicker
                    )
                }
            }
        }
    }

    interface Factory {
        fun create(
            githubApi: GithubApi,
            firebaseTestLabController: FirebaseTestLabController,
            apkStore: ApkStore,
            devicePicker: DevicePicker?
        ): TestScheduler
    }
    companion object {
        private val logger = logger()
        fun getFactory(
            useTestConfigFiles: Boolean
        ): Factory {
            return if (useTestConfigFiles) {
                RunUsingTestRunConfig.Factory
            } else {
                PairAndRunAllApks.Factory
            }
        }
    }
}
