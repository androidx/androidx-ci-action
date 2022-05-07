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

package dev.androidx.ci.github

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.config.Config
import dev.androidx.ci.github.dto.ArtifactsResponse
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class GithubApiTest {
    private val mockWebServer = MockWebServer()
    private val api = GithubApi.build(
        config = Config.Github(
            endPoint = mockWebServer.url("/").toString(),
            owner = "foo",
            repo = "bar",
            token = "someToken"
        )
    )
    @Test
    fun artifacts() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(505) // fail first, should retry
        )
        mockWebServer.enqueue(
            MockResponse().setBody(
                ARTIFACTS_RESPONSE
            )
        )
        val result = runBlocking {
            api.artifacts("run123")
        }
        assertThat(result).isEqualTo(
            ArtifactsResponse(
                artifacts = listOf(
                    ArtifactsResponse.Artifact(
                        id = "56945664",
                        url = "https://url/56945664",
                        name = "artifacts_activity",
                        archiveDownloadUrl = "https://download/test/56945664/zip"
                    ),
                    ArtifactsResponse.Artifact(
                        id = "56945672",
                        url = "https://url/56945672",
                        name = "artifacts_work",
                        archiveDownloadUrl = "https://download/56945672/zip"
                    )
                )
            )
        )
        val request = mockWebServer.takeRequest()
        assertThat(request.headers["Authorization"]).isEqualTo(
            "token someToken"
        )
    }

    companion object {
        val ARTIFACTS_RESPONSE = """
            {
              "total_count": 2,
              "artifacts": [
                {
                  "id": 56945664,
                  "node_id": "MDg6QXJ0aWZhY3Q1Njk0NTY2NA==",
                  "name": "artifacts_activity",
                  "size_in_bytes": 109786686,
                  "url": "https://url/56945664",
                  "archive_download_url": "https://download/test/56945664/zip",
                  "expired": false,
                  "created_at": "2021-04-28T13:07:35Z",
                  "updated_at": "2021-04-28T13:07:42Z",
                  "expires_at": "2021-07-27T12:54:00Z"
                },
                {
                  "id": 56945672,
                  "node_id": "MDg6QXJ0aWZhY3Q1Njk0NTY3Mg==",
                  "name": "artifacts_work",
                  "size_in_bytes": 143343777,
                  "url": "https://url/56945672",
                  "archive_download_url": "https://download/56945672/zip",
                  "expired": false,
                  "created_at": "2021-04-28T13:07:35Z",
                  "updated_at": "2021-04-28T13:07:42Z",
                  "expires_at": "2021-07-27T12:54:35Z"
                }
              ]
            }
        """.trimIndent()
    }
}
