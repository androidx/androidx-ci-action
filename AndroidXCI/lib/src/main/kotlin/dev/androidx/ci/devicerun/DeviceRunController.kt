/*
 * Copyright 2024 The Android Open Source Project
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

import dev.androidx.ci.generated.ddp.AndroidBugreportDeviceAction
import dev.androidx.ci.generated.ddp.AndroidInstallPackagesDeviceAction
import dev.androidx.ci.generated.ddp.AndroidInstallable
import dev.androidx.ci.generated.ddp.AndroidInstrumentationTest
import dev.androidx.ci.generated.ddp.AndroidLogcatDeviceAction
import dev.androidx.ci.generated.ddp.AndroidOrientationDeviceAction
import dev.androidx.ci.generated.ddp.AndroidPullFilesDeviceAction
import dev.androidx.ci.generated.ddp.AndroidPushFilesDeviceAction
import dev.androidx.ci.generated.ddp.AndroidPushFilesDeviceActionFileConfig
import dev.androidx.ci.generated.ddp.AndroidRecordVideoDeviceAction
import dev.androidx.ci.generated.ddp.AndroidSwitchLocaleDeviceAction
import dev.androidx.ci.generated.ddp.AllocationConfig
import dev.androidx.ci.generated.ddp.DeviceAction
import dev.androidx.ci.generated.ddp.DeviceConfig
import dev.androidx.ci.generated.ddp.DeviceRequirement
import dev.androidx.ci.generated.ddp.GcsPath
import dev.androidx.ci.generated.ddp.InputFile
import dev.androidx.ci.generated.ddp.JobAction
import dev.androidx.ci.generated.ddp.JobConfig
import dev.androidx.ci.generated.ddp.JobSettings
import dev.androidx.ci.generated.ddp.RetrySettings
import dev.androidx.ci.generated.ddp.RetrySettingsFlakyTestRetryStrategy
import dev.androidx.ci.generated.ddp.Session
import dev.androidx.ci.generated.ddp.SessionConfig
import dev.androidx.ci.generated.ddp.SessionConfigSessionOutputFileDirectoryConfig
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.ShardingOption
import dev.androidx.ci.testRunner.vo.DeviceSetup
import dev.androidx.ci.testRunner.vo.UploadedApk
import dev.androidx.ci.util.LazyComputedValue
import kotlinx.coroutines.delay
import org.apache.logging.log4j.kotlin.logger
import retrofit2.HttpException
import java.util.UUID

/**
 * Controller for Developer Device Platform (DDP) that maps FTL semantics into DDP jobs.
 */
internal class DeviceRunController(
    private val deviceRunApi: DeviceRunApi,
    private val gcpProjectId: String
) {
    private val logger = logger()

    private val catalog = LazyComputedValue {
        deviceRunApi.listDevices(projectId = gcpProjectId)
    }

    suspend fun submitTests(
        appApk: UploadedApk,
        testApk: UploadedApk,
        devices: List<AndroidDevice>,
        sharding: ShardingOption?,
        deviceSetup: DeviceSetup?,
        pullScreenshots: Boolean = false,
        testTargets: List<String>? = null,
        flakyTestAttempts: Int = 2,
        testTimeoutSeconds: Int = 2700,
        orchestratorOption: dev.androidx.ci.generated.ftl.AndroidInstrumentationTest.OrchestratorOption? = null,
        gcsResultPath: String
    ): Session {
        val jobConfigs = mutableListOf<JobConfig>()
        // Rule 1: Chunking Limit -> Max 10 JobConfigs per SessionConfig (but here we expand AndroidDevices into Jobs)
        
        devices.forEach { device ->
            // FTL locale: en_US -> DDP BCP-47: en-US
            val localeCode = device.locale?.replace('_', '-') ?: "en-US"
            val deviceIdSlug = "${device.androidModelId?.slug()}-${device.androidVersionId?.slug()}"
            val orientation = device.orientation?.lowercase() ?: "portrait"

            val deviceActions = mutableListOf<DeviceAction>()

            // 1. App installation
            deviceActions.add(
                DeviceAction(
                    androidInstallPackages = AndroidInstallPackagesDeviceAction(
                        targetApp = AndroidInstallable(
                            files = listOf(InputFile(gcsInputFile = GcsPath(path = appApk.gcsPath.path)))
                        ),
                        postTargetAppInstallables = deviceSetup?.additionalApks?.map {
                            AndroidInstallable(
                                files = listOf(InputFile(gcsInputFile = GcsPath(path = it.gcsPath.path)))
                            )
                        }
                    )
                )
            )
            
            // 2. Orientation & Locale
            deviceActions.add(DeviceAction(androidOrientation = AndroidOrientationDeviceAction(orientation = orientation)))
            deviceActions.add(DeviceAction(androidSwitchLocale = AndroidSwitchLocaleDeviceAction(localeCode = localeCode)))

            // 3. Push/Pull Files
            val pullDirs = deviceSetup?.directoriesToPull?.toMutableList() ?: mutableListOf()
            if (pullScreenshots) {
                pullDirs.add("/sdcard/screenshots")
                pullDirs.add("/sdcard/Pictures/screenshots")
            }
            if (pullDirs.isNotEmpty()) {
                deviceActions.add(DeviceAction(androidPullFiles = AndroidPullFilesDeviceAction(paths = pullDirs)))
            }

            // 4. Artifact Collection (Rule 5: Explicit defaults)
            deviceActions.add(DeviceAction(androidLogcat = AndroidLogcatDeviceAction()))
            deviceActions.add(DeviceAction(androidBugreport = AndroidBugreportDeviceAction()))

            // Construct instrumentation options
            val extraOptions = mutableListOf<String>()
            extraOptions.add("disable_window_animations=true")
            extraOptions.add("firebase.test.lab=true")
            deviceSetup?.instrumentationArguments?.forEach {
                extraOptions.add("${it.key}=${it.value}")
            }

            // Test Targets mapping
            val mappedTestTargets = mutableListOf<String>()
            testTargets?.forEach {
                mappedTestTargets.add(it)
            }
            // Move AndroidJUnitRunner filter keys (package, class, annotation, etc.) from extraOptions to testTargets if necessary.
            val filterKeys = listOf("package", "notPackage", "class", "notClass", "annotation", "notAnnotation", "size")
            val filteredOptions = extraOptions.filter { opt ->
                val key = opt.substringBefore('=')
                if (filterKeys.contains(key)) {
                    val value = opt.substringAfter('=')
                    value.split(',').forEach { v ->
                        mappedTestTargets.add("$key $v")
                    }
                    false
                } else {
                    true
                }
            }

            // Retry Settings
            val retrySettings = if (flakyTestAttempts > 0) {
                RetrySettings(
                    flakyTestRetryStrategy = RetrySettingsFlakyTestRetryStrategy(
                        flakyTestAttempts = (flakyTestAttempts + 1).coerceIn(1, 5)
                    )
                )
            } else null

            val ddpOrchestratorOption = if (orchestratorOption == dev.androidx.ci.generated.ftl.AndroidInstrumentationTest.OrchestratorOption.USE_ORCHESTRATOR) {
                "auto"
            } else null

            jobConfigs.add(
                JobConfig(
                    allocationConfig = AllocationConfig(
                        deviceConfigs = listOf(
                            DeviceConfig(
                                requirement = DeviceRequirement(deviceId = deviceIdSlug),
                                actions = deviceActions
                            )
                        )
                    ),
                    action = JobAction(
                        androidInstrumentationTest = AndroidInstrumentationTest(
                            testInstallable = AndroidInstallable(
                                files = listOf(InputFile(gcsInputFile = GcsPath(path = testApk.gcsPath.path)))
                            ),
                            testTargets = if (mappedTestTargets.isNotEmpty()) mappedTestTargets else null,
                            orchestratorVersion = ddpOrchestratorOption,
                            additionalTestOptions = if (filteredOptions.isNotEmpty()) filteredOptions else null,
                            instrumentationTimeout = "${testTimeoutSeconds.coerceIn(60, 10800)}s"
                        )
                    ),
                    settings = if (retrySettings != null) JobSettings(retrySettings = retrySettings) else null
                )
            )
        }

        val sessionId = "session-${UUID.randomUUID().toString().take(8)}"
        val sessionConfig = SessionConfig(
            outputDirectoryConfig = SessionConfigSessionOutputFileDirectoryConfig(
                gcsOutputDirectory = GcsPath(path = gcsResultPath)
            ),
            jobConfigs = jobConfigs
        )
        val sessionRequest = Session(
            sessionConfig = sessionConfig
        )
        
        logger.info { "Submitting DDP Session $sessionId" }
        deviceRunApi.createSession(
            projectId = gcpProjectId,
            sessionId = sessionId,
            session = sessionRequest
        )
        return deviceRunApi.getSession(
            projectId = gcpProjectId,
            sessionId = sessionId
        )
    }

    suspend fun getSession(sessionId: String): Session? {
        return try {
            deviceRunApi.getSession(
                projectId = gcpProjectId,
                sessionId = sessionId
            )
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw e
        }
    }

    private fun String.slug(): String = lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
