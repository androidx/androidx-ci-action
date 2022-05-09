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

import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A common rule to read GCloud credentials from environment variables for
 * local testing.
 */
internal class GithubAuthRule : TestRule {
    lateinit var githubToken: String
        private set

    private fun loadToken() {
        githubToken = System.getenv("ANDROIDX_GITHUB_TOKEN")
            ?: throw AssumptionViolatedException("skip test without credentials")
    }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                loadToken()
                base.evaluate()
            }
        }
    }
}
