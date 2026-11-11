/*
 * Copyright 2026 The Android Open Source Project
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

package dev.androidx.ci.devicerun

import com.squareup.moshi.Moshi
import dev.androidx.ci.config.Config
import dev.androidx.ci.firebase.authenticateWith
import dev.androidx.ci.generated.ddp.CancelSessionRequest
import dev.androidx.ci.generated.ddp.CancelSessionResponse
import dev.androidx.ci.generated.ddp.CatalogListDevicesResponse
import dev.androidx.ci.generated.ddp.CatalogListSoftwareVersionsResponse
import dev.androidx.ci.generated.ddp.GoogleLongrunningOperation
import dev.androidx.ci.generated.ddp.Session
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

internal interface DeviceRunApi {

    @POST("projects/{projectId}/locations/global/sessions")
    suspend fun createSession(
        @Path("projectId") projectId: String,
        @Query("sessionId") sessionId: String?,
        @Body session: Session
    ): GoogleLongrunningOperation

    @Retry
    @GET("projects/{projectId}/locations/global/sessions/{sessionId}")
    suspend fun getSession(
        @Path("projectId") projectId: String,
        @Path("sessionId") sessionId: String
    ): Session

    @POST("projects/{projectId}/locations/global/sessions/{sessionId}:cancel")
    suspend fun cancelSession(
        @Path("projectId") projectId: String,
        @Path("sessionId") sessionId: String,
        @Body request: CancelSessionRequest
    ): CancelSessionResponse

    @Retry
    @GET("projects/{projectId}/locations/global/devices")
    suspend fun listDevices(
        @Path("projectId") projectId: String
    ): CatalogListDevicesResponse

    @Retry
    @GET("projects/{projectId}/locations/global/softwareVersions")
    suspend fun listSoftwareVersions(
        @Path("projectId") projectId: String
    ): CatalogListSoftwareVersionsResponse

    companion object {
        fun build(
            config: Config.DeviceRun
        ): DeviceRunApi {
            val client = OkHttpClient
                .Builder()
                .dispatcher(config.dispatcher)
                .authenticateWith(
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
                    klass = DeviceRunApi::class
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
                .create(DeviceRunApi::class.java)
        }
    }
}
