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
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import org.jlleitschuh.gradle.ktlint.KtlintExtension

class GenerateModelsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension =
            target.extensions.create("generatedModels", GeneratedModelsExtension::class.java)
        val generateModelsTaskProvider =
            target.tasks.register("generateModels", GenerateModelsTask::class.java)
        val targetFolder = target.layout.buildDirectory.dir("generatedModels")
        val sourceSetContainer = target.extensions.getByType(KotlinSourceSetContainer::class.java)

        target.pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
            val ktlintExt = target.extensions.findByType(KtlintExtension::class.java)!!
            ktlintExt.filter {
                it.exclude {
                    it.path.contains("generatedModels")
                }
            }
            target.tasks.named("runKtlintCheckOverMainSourceSet").configure {
                // we need to do this to keep gradle cache optimizations
                it.dependsOn(generateModelsTaskProvider)
            }
        }

        sourceSetContainer.sourceSets.getByName(
            SourceSet.MAIN_SOURCE_SET_NAME
        ).kotlin.srcDir(targetFolder)

        generateModelsTaskProvider.configure { task ->
            task.discoveryFileUrl.set(extension.discoveryFileUrl)
            task.pkg.set(extension.pkg)
            task.sourceOutDir.set(
                target.layout.buildDirectory.dir("generatedModels")
            )
        }
        target.tasks.withType(KotlinCompile::class.java).configureEach {
            it.dependsOn(generateModelsTaskProvider)
        }
    }
}