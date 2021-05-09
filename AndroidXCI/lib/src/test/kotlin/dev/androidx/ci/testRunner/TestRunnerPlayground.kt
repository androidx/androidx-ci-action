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

import com.google.auth.Credentials
import com.google.auth.oauth2.ServiceAccountCredentials
import dev.androidx.ci.config.Config
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.FirestoreApi
import dev.androidx.ci.gcloud.GoogleCloudApi
import dev.androidx.ci.github.GithubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.Test

/**
 * This is not a real test. Instead, a utility to run [TestRunner]/
 *
 * To run it, you'll need Google Cloud Authentication in your environment as well as Github token (can be a personal
 * token)
 *
 * export ANDROIDX_GCLOUD_CREDENTIALS="<cloud json key from iam>"
 * export ANDROIDX_GITHUB_TOKEN="<github token>"
 */
class TestRunnerPlayground {
    private lateinit var cred: Credentials
    private lateinit var githubToken: String
    private lateinit var testRunner: TestRunner
    @Before
    fun loadCredentials() {
        val gcloudCredsEnv = System.getenv("ANDROIDX_GCLOUD_CREDENTIALS")
            ?: throw AssumptionViolatedException("skip test without credentials")
        githubToken = System.getenv("ANDROIDX_GITHUB_TOKEN")
            ?: throw AssumptionViolatedException("no github token in environment.")
        cred = ServiceAccountCredentials.fromStream(
            gcloudCredsEnv.byteInputStream(Charsets.UTF_8)
        )
        val runId = "821097113"
        testRunner = TestRunner(
            googleCloudApi = GoogleCloudApi.build(
                Config.GCloud(
                    credentials = cred,
                    bucketName = "androidx-ftl-test-results",
                    bucketPath = "local-test/$runId"
                ),
                context = Dispatchers.IO
            ),
            githubApi = GithubApi.build(
                Config.Github(
                    owner = "androidX",
                    repo = "androidx",
                    token = githubToken
                )
            ),
//            firebaseTestLabApi = FakeFirebaseTestLabApi(
//                onTestCreated = { api, matrix ->
//                    api.setTestMatrix(
//                        matrix.copy(
//                            state = TestMatrix.State.FINISHED,
//                            outcomeSummary = TestMatrix.OutcomeSummary.SUCCESS
//                        )
//                    )
//                }
//            ),
            firebaseTestLabApi = FirebaseTestLabApi.build(
                config = Config.FirebaseTestLab(
                    credentials = cred
                )
            ),
            firebaseProjectId = "androidx-dev-prod",
            artifactFilter = {
                it.name.contains("artifacts_room")
            },
            runId = runId,
            firestoreApi = FirestoreApi.build(
                Config.Firestore(
                    credentials = cred
                )
            )
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
