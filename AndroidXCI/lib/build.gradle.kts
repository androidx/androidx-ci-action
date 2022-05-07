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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.binaryValidator)
    id("androidx-model-builder")
    id("maven-publish")
}
generatedModels {
    this.models.set(
        listOf(
            dev.androidx.ci.codegen.plugin.GeneratedModelInfo(
                discoveryFileUrl = "https://testing.googleapis.com/\$discovery/rest?version=v1",
                pkg = "dev.androidx.ci.generated.ftl"
            ),
            dev.androidx.ci.codegen.plugin.GeneratedModelInfo(
                discoveryFileUrl = "https://toolresults.googleapis.com/\$discovery/rest?version=v1beta3",
                pkg = "dev.androidx.ci.generated.testResults"
            )
        )
    )
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.bundles.retrofit)
    implementation(libs.moshi.adapters)
    implementation(libs.moshix.metadata)
    implementation(libs.coroutines.core)
    implementation(libs.gcloud.storage)
    implementation(libs.gcloud.datastore)
    implementation(libs.bundles.log4j)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.truth)
}
/**
 * Copy the empty apk into our build which will be used for library integration tests.
 * Unfortunately, FTL always requires a test apks, hence we need this fake one.
 */
// specify dependency so that we can access the packageDebug task from the placeholder.
evaluationDependsOn(":placeholderapp")
val copyDestDir = project.layout.buildDirectory.dir("placeholderApk")
val copyPlaceholderApkTask = tasks.register("copyPlaceholderApk", Copy::class.java) {
    val assembleTask = rootProject.project("placeholderapp").tasks.named(
        "packageDebug"
    )
    from(assembleTask.get().outputs.files) {
        include("*.apk")
    }
    into(copyDestDir)
    rename {
        "placeholderApp.apk"
    }
}
val compileKotlin: KotlinCompile by tasks
tasks.named("processResources").configure {
    dependsOn(copyPlaceholderApkTask)
}
java {
    sourceSets {
        main {
            resources.srcDir(
                copyDestDir
            )
        }
    }
}
compileKotlin.kotlinOptions {
    freeCompilerArgs = listOf("-Xinline-classes")
}
