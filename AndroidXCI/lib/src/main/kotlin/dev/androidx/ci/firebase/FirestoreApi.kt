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
import dev.androidx.ci.firebase.dto.FirestoreDocument
import dev.zacsweers.moshix.reflect.MetadataKotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface FirestoreApi {
    /**
     * Gets an object from FirestoreAPI or throws Http 404 if it does not exist.
     */
    @GET("projects/{project_id}/databases/{database_id}/documents/{collection_id}/{document_id}")
    suspend fun get(
        @Path("project_id") projectId: String,
        @Path("database_id") databaseId: String = "(default)",
        @Path("collection_id") collectionId: String,
        @Path("document_id", encoded = true) documentPath: String
    ): FirestoreDocument

    /**
     * Puts an object to the Firestore API
     */
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
            val client = OkHttpClient.Builder().authenticateWith(config.credentials).addInterceptor {
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
            }.build()

            val moshi = Moshi.Builder()
                .add(FirestoreDocument.MoshiAdapter())
                .add(MetadataKotlinJsonAdapterFactory())
                .build()
            return Retrofit.Builder()
                .client(client)
                .baseUrl(config.endPoint)
                .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
                .build()
                .create(FirestoreApi::class.java)
        }
    }
}
