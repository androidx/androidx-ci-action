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

import com.google.cloud.NoCredentials
import com.google.cloud.storage.StorageException
import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class GoogleCloudApiTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun noAuthentication_checkCorrectContext() {
        val testScope = TestScope()
        val api = GoogleCloudApi.build(
            context = testScope.coroutineContext,
            config = Config.CloudStorage(
                gcp = Config.Gcp(
                    credentials = NoCredentials.getInstance(),
                    projectId = "no-project"
                ),
                bucketName = "non-existing-bucket",
                bucketPath = "testing",
            )
        )
        testScope.runTest {
            val result = async(SupervisorJob()) {
                api.upload(
                    "foo/bar",
                    byteArrayOf(1, 2, 3, 4)
                )
            }
            assertThat(result.isActive).isTrue()
            advanceUntilIdle()
            assertThat(
                result.getCompletionExceptionOrNull()
            ).isInstanceOf(StorageException::class.java)
            val storageException = result.getCompletionExceptionOrNull() as StorageException
            storageException.printStackTrace()
            assertThat(storageException.code).isAtLeast(400)
        }
    }

    @Test
    fun validateBucketPath() {
        val result = kotlin.runCatching {
            GoogleCloudApi.build(
                context = Dispatchers.IO,
                config = Config.CloudStorage(
                    gcp = Config.Gcp(
                        credentials = NoCredentials.getInstance(),
                        projectId = "no-project"
                    ),
                    bucketName = "bucketname",
                    bucketPath = "badbucketpath/",
                )
            )
        }
        assertThat(
            result.exceptionOrNull()
        ).isInstanceOf(IllegalArgumentException::class.java)
    }
}
