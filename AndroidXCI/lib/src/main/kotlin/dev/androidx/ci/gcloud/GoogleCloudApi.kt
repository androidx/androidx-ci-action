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

import com.google.cloud.storage.*
import com.google.common.io.ByteStreams
import dev.androidx.ci.config.Config
import dev.androidx.ci.util.configure
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.channels.Channels
import kotlin.coroutines.CoroutineContext

/**
 * Wrapper for Google Cloud Storage API.
 *
 * Note that, GCS library has built in retry logic so caller should not have additional retry logic
 * for failed requests.
 */
internal interface GoogleCloudApi {
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

    /**
     * Returns a GcsPath for an object if it exists
     */
    suspend fun existingFilePath(
        relativePath: String
    ): GcsPath?

    fun getGcsPath(relativePath: String): GcsPath

    companion object {
        fun build(
            config: Config.CloudStorage,
            context: CoroutineContext
        ): GoogleCloudApi {
            return GoogleCloudApiImpl(config, context)
        }
    }

    /**
     * Walks all entries in the given gcsPath from top to bottom.
     */
    suspend fun walkTopDown(gcsPath: GcsPath): Sequence<BlobVisitor>
}

/**
 * Downloads the given [gcsPath] into the given [target].
 *
 * If the [gcsPath] is a blob, it will be downloaded to [target].
 * If the [gcsPath] is a prefix, everything inside it will be downloaded into [target].
 */
internal suspend fun GoogleCloudApi.download(
    gcsPath: GcsPath,
    target: File,
    filter: (String) -> Boolean
) {
    walkTopDown(gcsPath).filter {visitor ->
        filter(visitor.relativePath)
    }.forEach { visitor ->
        val targetFile = if (visitor.isRoot()) {
            check(!target.isDirectory) {
                "trying to download a blob file into a folder ?"
            }
            target
        } else {
            target.resolve(
                visitor.relativePath
            )
        }
        targetFile.parentFile?.mkdirs()
        visitor.obtainInputStream().use {inputStream ->
            targetFile.outputStream().use { outputStream ->
                ByteStreams.copy(
                    inputStream,
                    outputStream
                )
            }
        }
    }
}

private class GoogleCloudApiImpl(
    val config: Config.CloudStorage,
    val context: CoroutineContext
) : GoogleCloudApi {
    init {
        if (config.bucketPath.endsWith('/')) {
            throw IllegalArgumentException("bucket path cannot end with /")
        }
    }

    private val gcspPathPrefix = "gs://${config.bucketName}"

    private val rootGcsPath = GcsPath(gcspPathPrefix) + config.bucketPath

    private val service: Storage = StorageOptions.newBuilder()
        .configure(config.gcp)
        .build().service

    override suspend fun upload(
        relativePath: String,
        bytes: ByteArray
    ): GcsPath = withContext(context) {
        val blobId = createBlobId(relativePath)
        val blobInfo = BlobInfo.newBuilder(blobId).build()
        val blob = service.create(
            blobInfo, bytes
        )
        GcsPath.create(blob)
    }

    override suspend fun walkTopDown(
        gcsPath: GcsPath
    ): Sequence<BlobVisitor> {
        val blobId = gcsPath.blobId
        val blob = service.get(blobId)
        return sequence<BlobVisitor> {
            if (blob != null) {
                yield(BlobVisitorImpl(
                    rootBlobId = blobId,
                    blob = blob
                ))
            } else {
                // probably a folder, list them
                var page = service.list(
                    config.bucketName,
                    Storage.BlobListOption.prefix(blobId.name)
                )
                while (page != null) {
                    page.iterateAll().forEach { fileBlob ->
                        yield(
                            BlobVisitorImpl(
                                rootBlobId = blobId,
                                blob = fileBlob
                            )
                        )
                    }
                    page = page.nextPage
                }
            }
        }
    }

    override suspend fun existingFilePath(relativePath: String): GcsPath? {
        val blobId = createBlobId(relativePath)
        val blob = service.get(blobId)
        return blob?.let {
            GcsPath.create(blob)
        }
    }

    private fun createBlobId(relativePath: String): BlobId {
        val artifactBucketPath = makeBucketPath(relativePath)
        return BlobId.of(config.bucketName, artifactBucketPath)
    }

    override fun getGcsPath(relativePath: String) = rootGcsPath + relativePath

    private val GcsPath.blobId: BlobId
        get() {
            check(path.startsWith(rootGcsPath.path)) {
                "Invalid gcs path, cannot get bucket: $this does not start with ${config.bucketName}"
            }
            val relativePath = path.substring(rootGcsPath.path.length + 1)
            return createBlobId(relativePath)
        }

    private fun makeBucketPath(relativePath: String) =
        "${config.bucketPath}/$relativePath"
}

internal interface BlobVisitor {
    fun isRoot() = relativePath.isEmpty()

    val relativePath: String
    val gcsPath: GcsPath

    val fileName: String
        get() = gcsPath.path.substringAfterLast('/')
    fun obtainInputStream(): InputStream
}

private class BlobVisitorImpl(
    val rootBlobId: BlobId,
    private val blob: Blob
): BlobVisitor {
    override val gcsPath = GcsPath(
        blob.blobId.toGsUtilUri()
    )
    override val relativePath: String
        get() = blob.name.substringAfter(rootBlobId.name).trimStart('/')

    override fun obtainInputStream(): InputStream {
        return Channels.newInputStream(blob.reader())
    }

    override fun toString(): String {
        return "Blob($gcsPath)"
    }
}