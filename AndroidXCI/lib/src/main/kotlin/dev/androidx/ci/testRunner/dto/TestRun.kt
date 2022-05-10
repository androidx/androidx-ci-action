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

package dev.androidx.ci.testRunner.dto

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.IncompleteKey
import com.google.cloud.datastore.Key
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.util.sha256
import dev.zacsweers.moshix.reflect.MetadataKotlinJsonAdapterFactory

private const val PROP_TEST_MATRIX_ID = "testMatrixId"

/**
 * Object that gets pushed into Datastore for each unique test run.
 * Uniqueness of a TestRun is computed from its parameters, see [createId].
 */
internal class TestRun(
    val id: Id,
    val testMatrixId: String
) {
    inline class Id(val key: Key)
    /**
     * Unique key for each test run. This is used to de-dup TestMatrix requests such that we won't recreate a TestMatrix
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
            moshi.adapter<Map<String, Any>>(type)
        }

        /**
         * Creates a unique ID for the given parameters
         */
        fun createId(
            datastoreApi: DatastoreApi,
            environment: EnvironmentMatrix,
            appApk: ApkInfo,
            testApk: ApkInfo
        ): Id {
            val json = adapter.toJson(
                mapOf(
                    "e" to environment,
                    "app" to appApk.idHash,
                    "test" to testApk.idHash
                )
            )
            val sha = sha256(json.toByteArray(Charsets.UTF_8))
            return Id(datastoreApi.createKey(datastoreApi.testRunObjectKind, sha))
        }
    }
}

internal fun TestRun.toEntity(): FullEntity<IncompleteKey> = Entity.newBuilder()
    .set(PROP_TEST_MATRIX_ID, testMatrixId)
    .setKey(id.key)
    .build()

internal fun Entity.toTestRun(): TestRun? {
    if (this.isNull(PROP_TEST_MATRIX_ID)) {
        return null
    }
    val key = this.key ?: return null
    val testMatrixId = this.getString(PROP_TEST_MATRIX_ID)
    return TestRun(
        id = TestRun.Id(key),
        testMatrixId = testMatrixId
    )
}
