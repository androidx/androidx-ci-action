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

package dev.androidx.ci.testRunner

import dev.androidx.ci.config.Config
import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.gcloud.GoogleCloudApi
import dev.androidx.ci.github.GithubApi
import dev.androidx.ci.util.GithubAuthRule
import dev.androidx.ci.util.GoogleCloudCredentialsRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * This is not a real test. Instead, a utility to run [TestRunner]
 *
 * To run it, you'll need Google Cloud Authentication in your environment as well as Github token (can be a personal
 * token)
 *
 * export ANDROIDX_GCLOUD_CREDENTIALS="<cloud json key from iam>"
 * export ANDROIDX_GITHUB_TOKEN="<github token>"
 */
class TestRunnerPlayground {
    private lateinit var testRunner: TestRunner

    @get:Rule
    val playgroundCredentialsRule = GoogleCloudCredentialsRule()

    @get:Rule
    val githubAuthRule = GithubAuthRule()

    @Before
    fun initRunner() {
        val runId = "1243675283"
        testRunner = TestRunner(
            googleCloudApi = GoogleCloudApi.build(
                Config.CloudStorage(
                    gcp = playgroundCredentialsRule.gcpConfig,
                    bucketName = "androidx-ftl-test-results",
                    bucketPath = "github-ci-action",
                ),
                context = Dispatchers.IO
            ),
            githubApi = GithubApi.build(
                Config.Github(
                    owner = "androidX",
                    repo = "androidx",
                    token = githubAuthRule.githubToken
                )
            ),
            firebaseTestLabApi = FirebaseTestLabApi.build(
                config = Config.FirebaseTestLab(
                    gcp = playgroundCredentialsRule.gcpConfig,
                )
            ),
            toolsResultApi = ToolsResultApi.build(
                config = Config.ToolsResult(
                    gcp = playgroundCredentialsRule.gcpConfig,
                )
            ),
            projectId = "androidx-dev-prod",
            datastoreApi = DatastoreApi.build(
                Config.Datastore(
                    gcp = playgroundCredentialsRule.gcpConfig,
                    testRunObjectKind = Config.Datastore.PLAYGROUND_OBJECT_KIND,
                ),
                context = Dispatchers.IO
            ),
            githubArtifactFilter = {
                it.name.contains("artifacts_navigation")
            },
            targetRunId = runId,
            hostRunId = runId
        )
    }

    @Test
    fun testRunner() = runBlocking<Unit> {
        val results = testRunner.runTests()
        println("-------------------------------")
        println("result: $results")
        println("-------------------------------")
    }
}
