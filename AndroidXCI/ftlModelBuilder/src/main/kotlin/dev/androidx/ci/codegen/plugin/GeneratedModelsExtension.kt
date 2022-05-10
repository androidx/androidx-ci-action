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

package dev.androidx.ci.codegen.plugin

import org.gradle.api.provider.ListProperty
import java.io.Serializable

/**
 * Extension to configure the settings for model generation.
 */
interface GeneratedModelsExtension {
    val models: ListProperty<GeneratedModelInfo>
}

data class GeneratedModelInfo(
    /**
     * The URL to the discovery file
     */
    val discoveryFileUrl: String,

    /**
     * Root package for generated classes
     */
    val pkg: String,
    /**
     * If true, classes will be generated with the internal modifier
     */
    val internal: Boolean = false
) : Serializable
