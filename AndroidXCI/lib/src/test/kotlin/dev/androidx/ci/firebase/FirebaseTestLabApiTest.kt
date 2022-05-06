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

package dev.androidx.ci.firebase

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.OAuth2Credentials
import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.config.Config
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Instant
import java.util.Date

@RunWith(JUnit4::class)
class FirebaseTestLabApiTest {
    private val mockWebServer = MockWebServer()
    private val fakeCreds = OAuth2Credentials.newBuilder()
        .setAccessToken(
            AccessToken(
                "no value",
                Date.from(
                    Instant.now().plusSeconds(600)
                )
            )
        ).build()
    private val api = FirebaseTestLabApi.build(
        config = Config.FirebaseTestLab(
            endPoint = mockWebServer.url("/").toString(),
            credentials = fakeCreds,
            gcpProjectId = "no-project"
        )
    )

    @Test
    fun authentication() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(404)
        )
        runBlocking {
            kotlin.runCatching {
                // 404, ignore
                api.getTestMatrix(
                    projectId = "none",
                    testMatrixId = "none"
                )
            }
        }
        val request = mockWebServer.takeRequest()
        val headers = request.headers
        assertThat(headers).isNotEmpty()
        assertThat(headers["Authorization"]).isEqualTo(
            "Bearer no value"
        )
    }
}
