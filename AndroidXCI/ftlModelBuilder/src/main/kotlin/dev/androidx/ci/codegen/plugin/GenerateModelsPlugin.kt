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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * Provides tasks to generate APIs for a Google Rest discovery document.
 *
 * https://developers.google.com/discovery/v1/reference/apis
 */
class GenerateModelsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension =
            target.extensions.create("generatedModels", GeneratedModelsExtension::class.java)
        val generateModelsTaskProvider =
            target.tasks.register(GENERATED_SOURCES_FOLDER_NAME, GenerateModelsTask::class.java)
        val targetFolder = target.layout.buildDirectory.dir("generatedModels")
        addGeneratedCodeToSourceSet(target, targetFolder)

        patchKtLint(target, generateModelsTaskProvider)

        generateModelsTaskProvider.configure { task ->
            task.description = "Generate models"
            task.models.set(extension.models)
            task.sourceOutDir.set(
                target.layout.buildDirectory.dir("generatedModels")
            )
        }
        target.tasks.withType(KotlinCompile::class.java).configureEach {
            it.dependsOn(generateModelsTaskProvider)
        }
    }

    private fun addGeneratedCodeToSourceSet(
        target: Project,
        targetFolder: Provider<Directory>
    ) {
        val sourceSetContainer = target.extensions.getByType(KotlinSourceSetContainer::class.java)
        sourceSetContainer.sourceSets.getByName(
            SourceSet.MAIN_SOURCE_SET_NAME
        ).kotlin.srcDir(targetFolder)
    }

    /**
     * Modifies the ktlint task to have proper dependencies.
     * Otherwise, it will depend on outputs without declaring a dependency.
     */
    private fun patchKtLint(
        target: Project,
        generateModelsTaskProvider: TaskProvider<GenerateModelsTask>
    ) {
        target.pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
            val ktlintExt = target.extensions.findByType(KtlintExtension::class.java)!!
            ktlintExt.filter {
                it.exclude {
                    it.path.contains(GENERATED_SOURCES_FOLDER_NAME)
                }
            }
            target.tasks.named("runKtlintCheckOverMainSourceSet").configure {
                // we need to do this to keep gradle cache optimizations
                it.dependsOn(generateModelsTaskProvider)
            }
        }
    }

    companion object {
        private const val GENERATED_SOURCES_FOLDER_NAME = "generateModels"
    }
}
