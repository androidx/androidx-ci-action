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

package dev.androidx.ci.codegen

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.SortedMap
import java.util.TreeMap


// see: https://developers.google.com/discovery/v1/reference/apis
/**
 * The root discovery class
 */
internal class DiscoveryDto(
    val schemas: SortedMap<String, SchemaDto>
)

/**
 * Represents the Schema for a single model
 */
internal data class SchemaDto(
    val id: String,
    /**
     * https://tools.ietf.org/html/draft-zyp-json-schema-03#section-5.1
     */
    val type: String,
    val description: String? = null,
    // discovery documents are not stable hence we keep properties
    // sorted by name.
    val properties: SortedMap<String, PropertyDto>? = null
) {
    fun isObject() = type == "object"
}

internal data class PropertyDto(
    val description: String? = null,
    val type: String?,
    @Json(name = "\$ref")
    val ref: String? = null,
    val enum: List<String>? = null,
    val enumDescriptions: List<String>? = null,
    val items: PropertyDto? = null,
    val format: String? = null
)

/**
 * A moshi adapter for generic SortedMaps that delegates to moshi's map
 * adapter.
 */
internal class SortedMapAdapter<K, V>(
    private val mapAdapter: JsonAdapter<Map<K, V>>
) : JsonAdapter<SortedMap<K, V>>() {
    override fun fromJson(reader: JsonReader): SortedMap<K, V>? {
        val map = mapAdapter.fromJson(reader) ?: return null
        return TreeMap(map)
    }

    override fun toJson(writer: JsonWriter, value: SortedMap<K, V>?) {
        mapAdapter.toJson(value)
    }

    companion object {
        val FACTORY = object : Factory {
            override fun create(
                type: Type,
                annotations: MutableSet<out Annotation>,
                moshi: Moshi
            ): JsonAdapter<*>? {
                if (annotations.isNotEmpty()) return null
                val rawType: Class<*> = Types.getRawType(type)
                if (rawType != SortedMap::class.java) return null
                if (type is ParameterizedType) {
                    val key = type.actualTypeArguments[0]
                    val value = type.actualTypeArguments[1]
                    val mapType = Types.newParameterizedType(
                        Map::class.java,
                        key,
                        value
                    )
                    return SortedMapAdapter<Any, Any>(moshi.adapter(mapType))
                }
                return null
            }
        }
    }
}
