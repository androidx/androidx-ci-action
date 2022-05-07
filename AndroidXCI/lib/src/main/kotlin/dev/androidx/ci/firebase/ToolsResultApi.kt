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

import com.squareup.moshi.Moshi
import dev.androidx.ci.config.Config
import dev.androidx.ci.generated.testResults.History
import dev.androidx.ci.generated.testResults.ListHistoriesResponse
import dev.androidx.ci.util.Retry
import dev.androidx.ci.util.RetryCallAdapterFactory
import dev.androidx.ci.util.withLog4J
import dev.zacsweers.moshix.reflect.MetadataKotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal interface ToolsResultApi {
    @Retry
    @GET("projects/{projectId}/histories")
    suspend fun getHistories(
        @Path("projectId") projectId: String,
        @Query("filterByName") name: String? = null,
        @Query("pageSize") pageSize: Int = 100
    ): ListHistoriesResponse

    @Retry
    @POST("projects/{projectId}/histories")
    suspend fun create(
        @Path("projectId") projectId: String,
        @Query("requestId") requestId: String? = null,
        @Body history: History
    ): History

    companion object {
        fun build(
            config: Config.ToolsResult
        ): ToolsResultApi {
            val client = OkHttpClient.Builder().authenticateWith(
                config.gcp
            ).addInterceptor {
                val newBuilder = it.request().newBuilder()
                newBuilder.addHeader(
                    "Content-Type", "application/json;charset=utf-8",
                )
                newBuilder.addHeader(
                    "Accept", "application/json"
                )
                it.proceed(
                    newBuilder.build()
                )
            }.withLog4J(
                level = config.httpLogLevel,
                klass = ToolsResultApi::class
            ).build()
            val moshi = Moshi.Builder()
                .add(MetadataKotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .client(client)
                .baseUrl(config.endPoint)
                .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
                .addCallAdapterFactory(RetryCallAdapterFactory.GLOBAL)
                .build()
                .create(ToolsResultApi::class.java)
        }
    }
}
