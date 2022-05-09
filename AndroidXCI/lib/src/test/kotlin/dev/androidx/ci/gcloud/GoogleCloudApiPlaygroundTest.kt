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

package dev.androidx.ci.gcloud

import com.google.common.truth.Truth
import dev.androidx.ci.config.Config
import dev.androidx.ci.util.GoogleCloudCredentialsRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * This is not a real test. Instead, a utility to play with Google Cloud API.
 *
 * To run it, you'll need Google Cloud Authentication in your environment.
 *
 * export ANDROIDX_GCLOUD_CREDENTIALS="<cloud json key from iam>"
 */
@RunWith(JUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
internal class GoogleCloudApiPlaygroundTest {
    @get:Rule
    val playgroundCredentialsRule = GoogleCloudCredentialsRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testScope = TestScope()
    @Test
    fun putItem() = testScope.runTest {
        val client = GoogleCloudApi.build(
            config = Config.CloudStorage(
                gcp = playgroundCredentialsRule.gcpConfig,
                bucketName = "androidx-ftl-test-results",
                bucketPath = "testing",
            ),
            context = testScope.coroutineContext
        )
        val bytes = byteArrayOf(1, 2, 3)
        val result = client.upload(
            "unitTest.txt", bytes
        )
        Truth.assertThat(
            result
        ).isEqualTo(
            GcsPath("gs://androidx-ftl-test-results/testing/unitTest.txt")
        )
    }

    @Test
    fun downloadFolder() = testScope.runTest {
        val folder = tmpFolder.newFolder()
        val client = GoogleCloudApi.build(
            config = Config.CloudStorage(
                gcp = playgroundCredentialsRule.gcpConfig,
                bucketName = "androidx-ftl-test-results",
                bucketPath = "github-ci-action",
            ),
            context = testScope.coroutineContext
        )
        val path = GcsPath(
            "gs://androidx-ftl-test-results/github-ci-action/ftl/821097113"
        )
        client.download(
            path,
            folder
        ) {
            !it.contains("test_cases")
        }
        println("donwloaded")
    }
}
