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

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import dev.androidx.ci.config.Config
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Wrapper for Google Cloud Storage API.
 *
 * Note that, GCS library has built in retry logic so caller should not have additional retry logic
 * for failed requests.
 */
interface GoogleCloudApi {
    /**
     * Uploads the given [bytes] into the given [relativePath].
     * The [relativePath] is relative to the root path in the bucket.
     *
     * @return The fully qualified GCP path
     */
    suspend fun upload(
        relativePath: String,
        bytes: ByteArray
    ): GcsPath

    companion object {
        fun build(
            config: Config.GCloud,
            context: CoroutineContext
        ): GoogleCloudApi {
            return GoogleCloudApiImpl(config, context)
        }
    }
}

private class GoogleCloudApiImpl(
    val config: Config.GCloud,
    val context: CoroutineContext
) : GoogleCloudApi {
    init {
        if (config.bucketPath.endsWith('/')) {
            throw IllegalArgumentException("bucket path cannot end with /")
        }
    }

    private val service: Storage = StorageOptions.newBuilder()
        .setCredentials(
            config.credentials
        ).build().service

    override suspend fun upload(
        relativePath: String,
        bytes: ByteArray
    ): GcsPath = withContext(context) {
        val artifactBucketPath = makeBucketPath(relativePath)
        val blobId = BlobId.of(config.bucketName, artifactBucketPath)
        val blobInfo = BlobInfo.newBuilder(blobId).build()
        val blob = service.create(
            blobInfo, bytes
        )
        GcsPath.create(blob)
    }

    private fun makeBucketPath(relativePath: String) =
        "${config.bucketPath}/$relativePath"
}
