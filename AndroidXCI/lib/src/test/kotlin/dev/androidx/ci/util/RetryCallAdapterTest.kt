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

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import dev.zacsweers.moshix.reflect.MetadataKotlinJsonAdapterFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * This test is ugly as it uses real time and might flake.
 * Unfortunately, MockWebServer does not provide an api to control time or respond to requests asynchronously. (creates
 * its own thread).
 *
 * This is good enough for now, if it flakes etc, we'll figure out a solution.
 */
internal class RetryCallAdapterTest {
    private val server = MockWebServer()
    interface Api {
        @Retry(2)
        @GET("foo")
        suspend fun retry2(): Response
    }

    private fun enqueueResponse(responseCode: Int) {
        if (responseCode == 200) {
            server.enqueue(
                MockResponse().setBody(TEST_DATA_JSON)
            )
        } else {
            server.enqueue(
                MockResponse().setResponseCode(responseCode)
            )
        }
    }

    lateinit var api: Api

    @Before
    fun init() {
        val moshi = Moshi.Builder()
            .add(MetadataKotlinJsonAdapterFactory())
            .build()
        val client = OkHttpClient.Builder()
            .callTimeout(1, TimeUnit.SECONDS)
            .build()
        api = Retrofit.Builder()
            .addCallAdapterFactory(
                RetryCallAdapterFactory { time, unit, callback ->
                    GlobalScope.launch {
                        delay(TEST_DELAY)
                        callback()
                    }
                }
            )
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .baseUrl(server.url("/"))
            .build()
            .create(Api::class.java)
    }

    @Test
    fun dontRetryOnClientError() = runBlocking<Unit> {
        enqueueResponse(404)
        val result = runCatching {
            api.retry2()
        }
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as? HttpException)?.code()).isEqualTo(404)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun dontRetryOnSuccess() = runBlocking<Unit> {
        enqueueResponse(200)
        val result = api.retry2()
        assertThat(result).isEqualTo(TEST_DATA)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun simple() = runBlocking<Unit> {
        enqueueResponse(500)
        enqueueResponse(501)
        enqueueResponse(200)
        val data = api.retry2()
        assertThat(data).isEqualTo(TEST_DATA)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun connectionError() = runBlocking<Unit> {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        enqueueResponse(500)
        enqueueResponse(200)
        val data = api.retry2()
        assertThat(data).isEqualTo(TEST_DATA)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun cancelledCall() = runBlocking<Unit> {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        enqueueResponse(500)
        enqueueResponse(200)
        val call = async {
            api.retry2()
        }
        delay(500)
        call.cancelAndJoin()
        delay(TEST_DELAY * 2) // wait for retry call
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun cancelledCall_duringRetry() = runBlocking<Unit> {
        enqueueResponse(500)
        enqueueResponse(500)
        enqueueResponse(200)
        val call = async {
            api.retry2()
        }
        delay(TEST_DELAY + TEST_DELAY / 2) // for retry
        call.cancelAndJoin()
        delay(TEST_DELAY * 2) // wait for retry call
        assertThat(server.requestCount).isEqualTo(2)
    }

    data class Response(
        val a: String,
        val b: String
    )

    companion object {
        private val TEST_DATA = Response(
            a = "abc",
            b = "cde"
        )
        private val TEST_DATA_JSON = """{ "a" : "abc", "b" :"cde" }"""
        private val TEST_DELAY = TimeUnit.SECONDS.toMillis(1)
    }
}
