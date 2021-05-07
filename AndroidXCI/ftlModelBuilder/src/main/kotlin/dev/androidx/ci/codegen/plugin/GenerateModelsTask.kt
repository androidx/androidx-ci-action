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

import dev.androidx.ci.codegen.DiscoveryDocumentModelGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
internal abstract class GenerateModelsTask : DefaultTask() {
    @get:Input
    abstract val discoveryFileUrl: Property<String>

    @get:Input
    abstract val pkg: Property<String>

    @get:OutputDirectory
    abstract val sourceOutDir: DirectoryProperty

    @TaskAction
    fun generateModels() {
        val outDir = sourceOutDir.asFile.get()
        outDir.deleteRecursively()
        DiscoveryDocumentModelGenerator(
            outDir = outDir,
            discoveryUrl = discoveryFileUrl.get(),
            pkg = pkg.get()
        ).generate()
    }
}