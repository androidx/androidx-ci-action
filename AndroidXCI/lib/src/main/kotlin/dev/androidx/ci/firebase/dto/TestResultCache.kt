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

package dev.androidx.ci.firebase.dto

import com.squareup.moshi.Types
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.util.MOSHI
import dev.androidx.ci.util.sha256

data class TestResultCache(
    val key: String,
    val testMatrixId: String,
    val githubWorkflowRunId: String?,
) {
    companion object {

        fun createKey(
            environment: EnvironmentMatrix,
            appApk: ApkInfo,
            testApk: ApkInfo
        ): String {
            val type = Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                Any::class.java
            )
            val adapter = MOSHI.adapter<Map<String, Any>>(type)
            val json = adapter.toJson(
                mapOf(
                    "e" to environment,
                    "app" to appApk,
                    "test" to testApk
                )
            )
            return sha256(json.toByteArray(Charsets.UTF_8))
        }
    }
}
