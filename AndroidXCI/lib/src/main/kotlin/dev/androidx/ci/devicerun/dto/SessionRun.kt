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

package dev.androidx.ci.devicerun.dto

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.Key
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.generated.ftl.AndroidInstrumentationTest.OrchestratorOption
import dev.androidx.ci.generated.ftl.ClientInfo
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.ShardingOption
import dev.androidx.ci.generated.ftl.TestSetup
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.testRunner.vo.DeviceSetup
import dev.androidx.ci.util.sha256
import dev.zacsweers.moshix.reflect.MetadataKotlinJsonAdapterFactory

private const val PROP_SESSION_ID = "sessionId"

/**
 * Object that gets pushed into Datastore for each unique DDP session run.
 * Uniqueness of a SessionRun is computed from its parameters, see [createId].
 */
internal class SessionRun(
    val id: Id,
    val sessionId: String
) {
    @JvmInline
    value class Id(val key: Key)
    /**
     * Unique key for each session run. This is used to de-dup Session requests such that we won't recreate a Session
     * if we've already run the exact same test.
     */
    companion object {
        private val adapter by lazy {
            val type = Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                Any::class.java
            )
            val moshi = Moshi.Builder()
                .add(MetadataKotlinJsonAdapterFactory())
                .build()
            moshi.adapter<Map<String, Any?>>(type)
        }

        /**
         * Creates a unique ID for the given parameters
         */
        fun createId(
            datastoreApi: DatastoreApi,
            environment: EnvironmentMatrix,
            clientInfo: ClientInfo?,
            appApk: ApkInfo,
            testApk: ApkInfo,
            deviceSetup: DeviceSetup?,
            sharding: ShardingOption?,
            orchestratorOption: OrchestratorOption?,
        ): Id {
            val json = adapter.toJson(
                mapOf(
                    "e" to environment,
                    "clientInfo" to clientInfo,
                    "sharding" to sharding,
                    "app" to appApk.idHash,
                    "test" to testApk.idHash,
                    "instrumentationArgs" to deviceSetup?.instrumentationArguments?.flatMap {
                        listOf(it.key, it.value)
                    },
                    "additionalApks" to deviceSetup?.additionalApks?.map {
                        it.gcsPath.path
                    }, // The order we install additional apks is important, so we do not sort here.
                    "directoriesToPull" to deviceSetup?.directoriesToPull?.sorted()
                ) + if (orchestratorOption != null) mapOf("orchestratorOption" to orchestratorOption) else mapOf()
            )
            val sha = sha256(json.toByteArray(Charsets.UTF_8))
            val objectKind = datastoreApi.testRunObjectKind + "-DDP"
            return Id(datastoreApi.createKey(objectKind, sha))
        }

        /**
         * Creates a unique ID for the given parameters
         */
        fun createId(
            datastoreApi: DatastoreApi,
            environment: EnvironmentMatrix,
            clientInfo: ClientInfo?,
            testSetup: TestSetup?,
            sharding: ShardingOption?,
            testTargets: List<String>?,
            baseSessionId: String
        ): Id {
            val json = adapter.toJson(
                mapOf(
                    "e" to environment,
                    "clientInfo" to clientInfo,
                    "sharding" to sharding,
                    "instrumentationArgs" to testSetup?.environmentVariables?.flatMap {
                        listOf(it.key, it.value)
                    },
                    "additionalApks" to testSetup?.additionalApks?.map {
                        it.location?.gcsPath
                    }, // The order we install additional apks is important, so we do not sort here.
                    "directoriesToPull" to testSetup?.directoriesToPull?.sorted(),
                    "testTargets" to testTargets?.sorted(),
                    "baseSessionId" to baseSessionId
                )
            )
            val sha = sha256(json.toByteArray(Charsets.UTF_8))
            val objectKind = datastoreApi.testRunObjectKind + "-DDP"
            return Id(datastoreApi.createKey(objectKind, sha))
        }
    }
}

internal fun SessionRun.toEntity(): Entity = Entity.newBuilder(id.key)
    .set(PROP_SESSION_ID, sessionId)
    .setKey(id.key)
    .build()

internal fun Entity.toSessionRun(): SessionRun? {
    if (this.isNull(PROP_SESSION_ID)) {
        return null
    }
    val key = this.key ?: return null
    val sessionId = this.getString(PROP_SESSION_ID)
    return SessionRun(
        id = SessionRun.Id(key),
        sessionId = sessionId
    )
}
