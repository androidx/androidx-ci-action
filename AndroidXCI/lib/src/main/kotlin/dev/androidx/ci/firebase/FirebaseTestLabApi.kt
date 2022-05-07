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
import dev.androidx.ci.firebase.dto.EnvironmentType
import dev.androidx.ci.generated.ftl.FileReference
import dev.androidx.ci.generated.ftl.GetApkDetailsResponse
import dev.androidx.ci.generated.ftl.TestEnvironmentCatalog
import dev.androidx.ci.generated.ftl.TestMatrix
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

internal interface FirebaseTestLabApi {
    @Retry
    @GET("projects/{projectId}/testMatrices/{testMatrixId}")
    suspend fun getTestMatrix(
        @Path("projectId") projectId: String,
        @Path("testMatrixId") testMatrixId: String
    ): TestMatrix

    @POST("projects/{projectId}/testMatrices")
    suspend fun createTestMatrix(
        @Path("projectId") projectId: String,
        @Query("requestId") requestId: String,
        @Body testMatrix: TestMatrix
    ): TestMatrix

    @Retry
    @GET("testEnvironmentCatalog/{environmentType}")
    suspend fun getTestEnvironmentCatalog(
        @Path("environmentType") environmentType: EnvironmentType,
        @Query("projectId") projectId: String,
    ): TestEnvironmentCatalog

    @Retry
    @POST("applicationDetailService/getApkDetails")
    suspend fun getApkDetails(
        @Body fileReference: FileReference
    ): GetApkDetailsResponse

    companion object {
        fun build(
            config: Config.FirebaseTestLab
        ): FirebaseTestLabApi {
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
                klass = FirebaseTestLabApi::class
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
                .create(FirebaseTestLabApi::class.java)
        }
    }
}
