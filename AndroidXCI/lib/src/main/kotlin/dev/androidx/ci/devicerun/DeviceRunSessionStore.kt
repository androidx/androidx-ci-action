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

import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.devicerun.dto.SessionRun
import dev.androidx.ci.devicerun.dto.toEntity
import dev.androidx.ci.devicerun.dto.toSessionRun
import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.generated.ddp.Session
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.AndroidDeviceList
import dev.androidx.ci.generated.ftl.AndroidInstrumentationTest.OrchestratorOption
import dev.androidx.ci.generated.ftl.ClientInfo
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.ShardingOption
import dev.androidx.ci.testRunner.DevicePicker
import dev.androidx.ci.testRunner.vo.DeviceSetup
import dev.androidx.ci.testRunner.vo.UploadedApk
import org.apache.logging.log4j.kotlin.logger
import java.util.UUID

/**
 * Maintains DDP sessions by adding a caching layer over DDP to avoid re-creating sessions
 * for the same configuration.
 */
internal class DeviceRunSessionStore(
    private val datastoreApi: DatastoreApi,
    private val deviceRunController: DeviceRunController,
    private val resultsGcsPrefix: GcsPath
) {
    private val logger = logger()

    internal fun createUniqueResultGcsPath(sessionRunId: SessionRun.Id): GcsPath {
        return resultsGcsPrefix + sessionRunId.key.name + UUID.randomUUID().toString()
    }

    private fun List<AndroidDevice>.createEnvironmentMatrix() = EnvironmentMatrix(
        androidDeviceList = AndroidDeviceList(
            androidDevices = this
        )
    )

    /**
     * Creates a Session for the given configuration or returns an existing one if we've run the same test with the
     * same APKs and environment configuration.
     */
    suspend fun getOrCreateSession(
        appApk: UploadedApk?,
        testApk: UploadedApk,
        devicePicker: DevicePicker,
        clientInfo: ClientInfo?,
        sharding: ShardingOption?,
        deviceSetup: DeviceSetup?,
        pullScreenshots: Boolean = false,
        cachedSessionFilter: CachedSessionFilter = { true },
        testTargets: List<String>? = null,
        flakyTestAttempts: Int = 2,
        testTimeoutSeconds: Int = 2700,
        orchestratorOption: OrchestratorOption? = null,
        gcsSmartShardingFilePath: String
    ): Session {
        val environmentMatrix = deviceRunController.createEnvironmentMatrix(devicePicker)
        val sessionRunId = SessionRun.createId(
            datastoreApi = datastoreApi,
            environment = environmentMatrix,
            clientInfo = clientInfo,
            sharding = sharding,
            appApk = appApk?.apkInfo,
            testApk = testApk.apkInfo,
            deviceSetup = deviceSetup,
            orchestratorOption = orchestratorOption,
        )
        logger.trace {
            "session run id: $sessionRunId"
        }

        val existingSession = getCachedSession(sessionRunId, cachedSessionFilter)
        if (existingSession != null) {
            return existingSession
        }

        val newSession = deviceRunController.submitTests(
            appApk = appApk,
            testApk = testApk,
            devicePicker = devicePicker,
            deviceSetup = deviceSetup,
            pullScreenshots = pullScreenshots,
            testTargets = testTargets,
            flakyTestAttempts = flakyTestAttempts,
            testTimeoutSeconds = testTimeoutSeconds,
            orchestratorOption = orchestratorOption,
            gcsResultPath = createUniqueResultGcsPath(sessionRunId).path,
            gcsSmartShardingFilePath = gcsSmartShardingFilePath
        )

        logger.info {
            "created session: ${newSession.name}"
        }

        // save it to cache
        val sessionId = newSession.name?.substringAfterLast("/") ?: error("Created session missing name")
        val sessionRun = SessionRun(
            id = sessionRunId,
            sessionId = sessionId
        )
        datastoreApi.put(sessionRun.toEntity())
        logger.info {
            "saved session info: $sessionRun"
        }
        return newSession
    }

    /**
     * Check if a [Session] exists for the given [sessionRunId]
     * and [cachedSessionFilter] allows reusing the session
     */
    private suspend fun getCachedSession(
        sessionRunId: SessionRun.Id,
        cachedSessionFilter: CachedSessionFilter,
    ): Session? {
        getExistingSession(sessionRunId)?.let { session ->
            logger.info("found existing session: ${session.name}")

            // ERROR, TIMED_OUT, CANCELLED could be terminal failure states, but usually we just let the filter decide
            // For now just defer to the filter
            if (!cachedSessionFilter(session)) {
                logger.info {
                    "Not re-using cached session due to filter"
                }
            } else {
                return session
            }
        }
        logger.trace {
            "No session run history for $sessionRunId or cached Session is rejected."
        }
        return null
    }

    private suspend fun getExistingSession(
        sessionRunId: SessionRun.Id
    ): Session? {
        val sessionId = datastoreApi.get(
            sessionRunId.key
        )?.toSessionRun()?.sessionId ?: return null

        return deviceRunController.getSession(sessionId)
    }
}
