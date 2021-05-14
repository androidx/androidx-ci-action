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

/**
 * Common configuration for TestRunner.
 */
class Config {
    class Github(
        val endPoint: String = "https://api.github.com",
        val owner: String,
        val repo: String,
        val token: String
    )
    class GCloud(
        val credentials: Credentials,
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
        val endPoint: String = "https://testing.googleapis.com/v1/",
        val credentials: Credentials
    )
    class Datastore(
        val credentials: Credentials,
    )
}
