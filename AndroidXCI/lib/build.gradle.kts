import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

plugins {
    `kotlin-dsl`
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("androidx-model-builder")
}

generatedModels {
    this.pkg.set("dev.androidx.ci.generated.ftl")
    this.discoveryFileUrl.set("https://testing.googleapis.com/\$discovery/rest?version=v1")
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
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    freeCompilerArgs = listOf("-Xinline-classes")
}
