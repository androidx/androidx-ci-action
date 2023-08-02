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

package dev.androidx.ci.util

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import dev.androidx.ci.config.Config
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A common rule to read Github token from environment variables for local testing.
 */
internal class GoogleCloudCredentialsRule : TestRule {
    lateinit var gcpConfig: Config.Gcp
        private set
    private fun loadCredentials() {
        val appCredentialsProject = System.getenv("ANDROIDX_GCLOUD_APP_CREDENTIALS_PROJECT")
        val (projectId, credentials) = if (appCredentialsProject != null) {
            appCredentialsProject to GoogleCredentials.getApplicationDefault()
        } else {
            val credentialsParam = System.getenv("ANDROIDX_GCLOUD_CREDENTIALS")
                ?: throw AssumptionViolatedException("skip test without credentials")
            val credentials = ServiceAccountCredentials.fromStream(
                credentialsParam.byteInputStream(Charsets.UTF_8)
            )
            credentials.projectId to credentials
        }
        if (credentials == null) {
            throw AssumptionViolatedException("need to provide app credentials")
        }
        gcpConfig = Config.Gcp(
            credentials = credentials,
            projectId = projectId
        )
    }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                loadCredentials()
                base.evaluate()
            }
        }
    }
}
