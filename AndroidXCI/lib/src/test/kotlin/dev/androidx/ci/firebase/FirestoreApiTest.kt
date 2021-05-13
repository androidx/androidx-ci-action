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
import dev.androidx.ci.firebase.dto.FirestoreDocument
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import java.time.Instant
import java.util.Date

class FirestoreApiTest {
    private val mockWebServer = MockWebServer().also {
        it.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                    name : "n1",
                    fields : {
                        "aa" : {
                            stringValue = "aaa1"
                        },
                        "bb" : {
                            stringValue = "bbb1"
                        }
                    }
                }
                """.trimIndent()
            )
        )
    }
    private val fakeCreds = OAuth2Credentials.newBuilder()
        .setAccessToken(
            AccessToken(
                "no value",
                Date.from(
                    Instant.now().plusSeconds(600)
                )
            )
        ).build()
    private val api = FirestoreApi.build(
        config = Config.Firestore(
            endPoint = mockWebServer.url("/").toString(),
            credentials = fakeCreds
        )
    )

    @Test
    fun putDocument() = runBlocking<Unit> {
        val result = api.put(
            projectId = "p1",
            collectionId = "c1",
            documentPath = "foo/bar",
            document = FirestoreDocument(
                fields = mapOf(
                    "a" to "b",
                    "c" to "d"
                )
            )
        )
        assertResult(result)
        val request = mockWebServer.takeRequest()
        assertThat(request.path).endsWith("foo/bar") // make sure / is not encoded
        assertThat(request.body.snapshot().string(Charsets.UTF_8))
            .isEqualTo("""{"fields":{"a":{"stringValue":"b"},"c":{"stringValue":"d"}}}""")
    }

    @Test
    fun getDocument() = runBlocking<Unit> {
        val result = api.get(
            projectId = "p1",
            collectionId = "c1",
            documentPath = "foo/bar"
        )
        assertResult(result)
        val request = mockWebServer.takeRequest()
        assertThat(request.path).endsWith("foo/bar") // make sure / is not encoded
    }

    private fun assertResult(result: FirestoreDocument) {
        // server sends intentionally different values to ensure we parsed them properly
        assertThat(result.fields).containsExactlyEntriesIn(
            mapOf(
                "aa" to "aaa1",
                "bb" to "bbb1"
            )
        )
        assertThat(result.name).isEqualTo("n1")
    }
}
