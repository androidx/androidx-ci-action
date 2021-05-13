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

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

/**
 * Data structure for FireStore rest api documents
 */
data class FirestoreDocument(
    val name: String? = null,
    val fields: Map<String, Any?>,
    val createTime: String? = null,
    val updateTime: String? = null
) {
    class MoshiAdapter {
        @Suppress("unused") // used by moshi
        @FromJson
        fun fromJson(data: Map<String, Any?>): FirestoreDocument {
            return FirestoreDocument(
                name = data["name"] as? String,
                createTime = data["createTime"] as? String,
                updateTime = data["updateTime"] as? String,
                fields = (data["fields"] as? Map<String, Any?>)?.mapValues {
                    val value = it.value
                    if (value is Map<*, *>) {
                        value.entries.firstOrNull()?.value
                    } else {
                        null
                    }
                } ?: emptyMap<String, Any?>()
            )
        }

        @Suppress("unused") // used by moshi
        @ToJson
        fun toJson(doc: FirestoreDocument): Map<String, Any?> = mapOf(
            "name" to doc.name,
            "fields" to doc.fields.mapValues {
                mapOf(valueKey(it.value) to it.value)
            },
            "createTime" to doc.createTime,
            "updateTime" to doc.updateTime
        )

        private fun valueKey(value: Any?): String = when (value) {
            is String -> "stringValue"
            null -> "nullValue"
            // TODO we can add more here but we don't need yet
            else -> error("unsupported value type $value")
        }
    }
}
