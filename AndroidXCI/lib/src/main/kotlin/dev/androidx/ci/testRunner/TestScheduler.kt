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
 * 2) ByTestConfig will parse the TestConfig.json files and only run tests specified in that list, along with the
 * given test tags.
 *
 * Since schedulers require dependencies, to obtain an instance of this, you need to first obtain a factory via
 * [createFactory] and then use it to get the instance of [TestScheduler].
 */
internal sealed interface TestScheduler {
    suspend fun enqueueTests(
        artifact: ArtifactsResponse.Artifact,
    ): List<TestMatrix>
    class PairAndRunAllApks(
        private val githubApi: GithubApi,
        private val firebaseTestLabController: FirebaseTestLabController,
        private val apkStore: ApkStore,
        private val devicePicker: DevicePicker?,
    ) : TestScheduler {
        override suspend fun enqueueTests(
            artifact: ArtifactsResponse.Artifact
        ): List<TestMatrix> {
            val apks = uploadApksToGoogleCloud(githubApi, artifact)
            logger.info { "will start tests for these apks: $apks" }
            return firebaseTestLabController.pairAndStartTests(
                apks = apks,
                placeholderApk = apkStore.getPlaceholderApk(),
                devicePicker = devicePicker,
            )
        }

        private suspend fun uploadApksToGoogleCloud(
            githubApi: GithubApi,
            artifact: ArtifactsResponse.Artifact
        ): List<UploadedApk> {
            logger.info { "will upload apks for $artifact" }
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
                devicePicker: DevicePicker?,
            ): TestScheduler {
                return PairAndRunAllApks(
                    githubApi = githubApi,
                    firebaseTestLabController = firebaseTestLabController,
                    apkStore = apkStore,
                    devicePicker = devicePicker,
                )
            }
        }
    }

    class RunUsingTestRunConfig(
        private val testSuiteTags: List<String>,
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
            }.mapNotNull { zipEntryScope ->
                adapter.fromJson(zipEntryScope.bytes.toString(Charsets.UTF_8)).also { testRunConfig ->
                    "Found AndroidTest config: ${zipEntryScope.entry.name}"
                }
            }.filter { testRunConfig ->
                // additional apks are not supported, filter them out
                val hasNoAdditionalApks = testRunConfig.additionalApkKeys.isEmpty()
                if (!hasNoAdditionalApks) {
                    logger.warn {
                        "TestRunConfigs with additional APKs are not supported. Skipping $testRunConfig"
                    }
                }
                hasNoAdditionalApks
            }.filter { testRunConfig ->
                // check for test suite tags
                testSuiteTags.isEmpty() || testRunConfig.testSuiteTags.any { testSuiteTags.contains(it) }
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
            val testRunConfig: TestRunConfig
        ) {
            val appApk: PendingApk? = testRunConfig.appApk?.let { appApk ->
                testRunConfig.appApkSha256?.let { sha256 ->
                    PendingApk(name = appApk, sha256 = sha256)
                }
            }
            val testApk: PendingApk = PendingApk(
                name = testRunConfig.testApk,
                sha256 = testRunConfig.testApkSha256
            )

            suspend fun trySubmit(
                firebaseTestLabController: FirebaseTestLabController,
                apkStore: ApkStore,
                devicePicker: DevicePicker?
            ): List<TestMatrix> {
                logger.info {
                    "Will try to submit $testRunConfig"
                }
                val uploadedAppApk = if (appApk == null) {
                    apkStore.getPlaceholderApk()
                } else {
                    appApk.apk.also {
                        if (it == null) {
                            logger.warn("Couldn't find apk for ${testRunConfig.appApk}, skipping test")
                        }
                    }
                }
                val uploadedTestApk = testApk.apk
                if (uploadedAppApk == null || uploadedTestApk == null) {
                    logger.warn {
                        """Skipping $testRunConfig because we couldn't find either the app or test apk"""
                    }
                    return emptyList()
                }
                val instrumentationArguments =
                    testRunConfig.instrumentationArgs.map {
                        DeviceSetup.InstrumentationArgument(
                            key = it.key,
                            value = it.value
                        )
                    }
                logger.info {
                    "Submitting test for $testRunConfig"
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
                    pullScreenshots = false,
                    cachedTestMatrixFilter = { true }
                )
            }

            /**
             * Tries to see if the given ZipEntry is one of the APKs we are looking for.
             * If so, it will be uploaded (if necessary).
             */
            suspend fun processZipEntry(
                apkStore: ApkStore,
                scope: ZipEntryScope
            ) {
                appApk?.processZipEntry(apkStore, scope)
                testApk.processZipEntry(apkStore, scope)
            }

            /**
             * A mutable wrapper class for an APK inside a [TestRunConfig]. It will find its own
             * APK based on the [processZipEntry] calls.
             */
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

        /**
         * TestRunConfig json file structure.
         * It is based on https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:buildSrc/private/src/main/kotlin/androidx/build/testConfiguration/AndroidTestConfigBuilder.kt
         */
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
        ) {
            fun serialize(): String {
                return adapter.toJson(this)
            }
        }

        /**
         * Arguments to pass for each test run.
         */
        data class InstrumentationArg(
            val key: String,
            val value: String
        )

        companion object {
            private val moshi = Moshi.Builder()
                .add(MetadataKotlinJsonAdapterFactory())
                .build()
            private val adapter = moshi.adapter(TestRunConfig::class.java)
            class Factory(private val testSuiteTags: List<String>) : TestScheduler.Factory {
                override fun create(
                    githubApi: GithubApi,
                    firebaseTestLabController: FirebaseTestLabController,
                    apkStore: ApkStore,
                    devicePicker: DevicePicker?
                ): TestScheduler {
                    return RunUsingTestRunConfig(
                        testSuiteTags = testSuiteTags,
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
            devicePicker: DevicePicker?,
        ): TestScheduler
    }
    companion object {
        private val logger = logger()

        /**
         * @param useTestConfigFiles If true, a scheduler that will use the AndroidTest.json files will be returned
         * @param testSuiteTags If not empty, AndroidTest.json scheduler will only run test configs matching the given
         * test suite tag.
         */
        fun createFactory(
            useTestConfigFiles: Boolean,
            testSuiteTags: List<String>
        ): Factory {
            return if (useTestConfigFiles) {
                RunUsingTestRunConfig.Companion.Factory(testSuiteTags)
            } else {
                PairAndRunAllApks.Factory
            }
        }
    }
}
