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

// see: https://developers.google.com/discovery/v1/reference/apis
/**
 * The root discovery class
 */
internal class DiscoveryDto(
    val schemas: Map<String, SchemaDto>
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
    val description: String?,
    val properties: Map<String, PropertyDto>?
) {
    fun isObject() = type == "object"
}

internal data class PropertyDto(
    val description: String?,
    val type: String?,
    @Json(name = "\$ref")
    val ref: String?,
    val enum: List<String>?,
    val enumDescriptions: List<String>?,
    val items: PropertyDto?,
    val format: String?
)