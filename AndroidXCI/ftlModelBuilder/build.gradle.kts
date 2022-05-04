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
    `java-gradle-plugin`
    `kotlin-dsl`
    alias(libs.plugins.ktlintGradle)
}

group = "dev.androidx.ci"

dependencies {
    implementation(gradleApi())
    implementation(kotlin("gradle-plugin-api"))
    implementation(kotlin("stdlib"))
    implementation("org.jlleitschuh.gradle:ktlint-gradle:10.0.0")
    implementation(libs.okhttp.core)
    implementation(libs.moshi.kotlin)
    implementation(libs.kotlinpoet)
    testImplementation(libs.truth)
}

gradlePlugin {
    plugins.register("androidx-model-builder") {
        id = "androidx-model-builder"
        implementationClass = "dev.androidx.ci.codegen.plugin.GenerateModelsPlugin"
    }
}
