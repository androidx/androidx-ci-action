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

package dev.androidx.ci.config

import com.google.auth.Credentials
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Common configuration for TestRunner.
 */
internal class Config {
    class Github(
        val endPoint: String = "https://api.github.com",
        val owner: String,
        val repo: String,
        val token: String
    )
    class CloudStorage(
        val gcp: Gcp,
        /**
         * The name of the bucket to use
         */
        val bucketName: String,
        /**
         * The relative path in the bucket to put values
         */
        val bucketPath: String
    )
    class FirebaseTestLab(
        val gcp: Gcp,
        val endPoint: String = "https://testing.googleapis.com/v1/",
        val httpLogLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.NONE,
    )
    class Datastore(
        val gcp: Gcp,
        val testRunObjectKind: String,
    ) {
        companion object {
            val GITHUB_OBJECT_KIND = "TestRun"
            val AOSP_OBJECT_KIND = "AOSP-TestRun"
            val PLAYGROUND_OBJECT_KIND = "Playground-TestRun"
        }
    }
    class ToolsResult(
        val gcp: Gcp,
        val endPoint: String = "https://toolresults.googleapis.com/toolresults/v1beta3/",
        val httpLogLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.NONE,
    )

    class Gcp(
        val credentials: Credentials,
        val projectId: String
    )
}
