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

import dev.androidx.ci.config.Config
import dev.androidx.ci.firebase.dto.FirestoreDocument
import dev.androidx.ci.util.MOSHI
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface FirestoreApi {

    // TODO make this nullable
    @GET("projects/{project_id}/databases/{database_id}/documents/{collection_id}/{document_id}")
    suspend fun get(
        @Path("project_id") projectId: String,
        @Path("database_id") databaseId: String = "(default)",
        @Path("collection_id") collectionId: String,
        @Path("document_id", encoded = true) documentPath: String
    ): FirestoreDocument

    @PATCH("projects/{project_id}/databases/{database_id}/documents/{collection_id}/{document_id}")
    suspend fun put(
        @Path("project_id") projectId: String,
        @Path("database_id") databaseId: String = "(default)",
        @Path("collection_id") collectionId: String,
        @Path("document_id", encoded = true) documentPath: String,
        @Body document: FirestoreDocument
    ): FirestoreDocument

    companion object {
        fun build(
            config: Config.Firestore
        ): FirestoreApi {
            val logging = HttpLoggingInterceptor()
            logging.level = HttpLoggingInterceptor.Level.BODY
            val client = OkHttpClient.Builder().apply {
                this.addInterceptor {
                    val requestMetadata = config.credentials.getRequestMetadata(
                        it.request().url.toUri()
                    )
                    val newBuilder = it.request().newBuilder()
                    requestMetadata.forEach { (key, values) ->
                        values.firstOrNull()?.let { value ->
                            newBuilder.addHeader(key, value)
                        }
                    }
                    newBuilder.addHeader(
                        "Content-Type", "application/json;charset=utf-8",
                    )
                    newBuilder.addHeader(
                        "Accept", "application/json"
                    )
                    it.proceed(
                        newBuilder.build()
                    )
                }
            }.addInterceptor(logging).build()

            return Retrofit.Builder()
                .client(client)
                .baseUrl(config.endPoint)
                .addConverterFactory(MoshiConverterFactory.create(MOSHI).asLenient())
                .build()
                .create(FirestoreApi::class.java)
        }
    }
}
