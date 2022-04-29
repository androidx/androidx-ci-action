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
    // cannot use version catalog here yet:
    // https://melix.github.io/blog/2021/03/version-catalogs-faq.html#_can_i_use_a_version_catalog_to_declare_plugin_versions
    kotlin("jvm") version "1.5.30" apply false
    id("org.jlleitschuh.gradle.ktlint") version "10.0.0"
    id("org.jlleitschuh.gradle.ktlint-idea") version "10.0.0"
}

repositories {
    mavenCentral()
    google()
}

val githubRunId = project.providers.environmentVariable("GITHUB_RUN_ID")
val publishVersion = githubRunId.orElse("").map {
    if (it == "") {
        "1.0-SNAPSHOT"
    } else {
        "1.0.$it"
    }
}
val publishRepo = project.layout.buildDirectory.dir("repo")
subprojects {
    repositories {
        mavenCentral()
        google()
    }
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
            this.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        }
    }
    pluginManager.withPlugin("maven-publish") {
        group = "dev.androidx.build.ci"
        version = publishVersion.get()
        extensions.configure(PublishingExtension::class) {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                }
                repositories {
                    maven {
                        url = uri(publishRepo)
                    }
                }
            }
        }
    }
}
