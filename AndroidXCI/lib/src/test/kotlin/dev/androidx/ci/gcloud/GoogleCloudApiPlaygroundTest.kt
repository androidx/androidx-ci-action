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

import com.google.auth.Credentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.common.truth.Truth
import dev.androidx.ci.config.Config
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.Test
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
class GoogleCloudApiPlaygroundTest {
    private lateinit var cred: Credentials
    private val testScope = TestCoroutineScope()
    @Before
    fun loadCredentials() {
        val envValue = System.getenv("ANDROIDX_GCLOUD_CREDENTIALS")
            ?: throw AssumptionViolatedException("skip test without credentials")
        cred = ServiceAccountCredentials.fromStream(
            envValue.byteInputStream(Charsets.UTF_8)
        )
    }

    @Test
    fun putItem() = testScope.runBlockingTest {
        val client = GoogleCloudApi.build(
            config = Config.GCloud(
                credentials = cred,
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
}
