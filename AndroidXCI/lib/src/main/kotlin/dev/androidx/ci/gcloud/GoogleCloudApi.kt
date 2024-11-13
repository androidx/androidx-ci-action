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

import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
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

    /**
     * Copies data from source GCS path to the target relative path.
     */
    suspend fun copy(
        sourceGcsPath: GcsPath,
        targetRelativePath: String
    ): GcsPath

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
     * Walks all entries under the given [gcsPath].
     */
    suspend fun walkEntries(gcsPath: GcsPath): Sequence<BlobVisitor>

    /**
     * Gets the blobvisitor for the given [gcsPath] if the gcsPath represents a file.
     * Returns null if the gcsPath points to a folder
     */
    suspend fun getBlob(gcsPath: GcsPath): BlobVisitor?
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
    walkEntries(gcsPath).filter { visitor ->
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
        visitor.obtainInputStream().use { inputStream ->
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

    override suspend fun copy(
        sourceGcsPath: GcsPath,
        targetRelativePath: String
    ): GcsPath = withContext(context) {
        val sourceBlobId = BlobId.fromGsUtilUri(sourceGcsPath.path)
        val artifactBucketPath = makeBucketPath(targetRelativePath)
        val targetBlobId = BlobId.of(config.bucketName, artifactBucketPath)
        service.copy(
            Storage.CopyRequest.newBuilder().setSource(sourceBlobId).setTarget(targetBlobId).build()
        )
        GcsPath.create(config.bucketName, artifactBucketPath)
    }

    override suspend fun walkEntries(
        gcsPath: GcsPath
    ): Sequence<BlobVisitor> {
        val blobId = gcsPath.blobId
        val blob = service.get(blobId)
        return sequence<BlobVisitor> {
            if (blob != null) {
                yield(
                    BlobVisitorImpl(
                        rootBlobId = blobId,
                        blob = blob
                    )
                )
            } else {
                // probably a folder, list them
                var page = service.list(
                    config.bucketName,
                    Storage.BlobListOption.prefix(blobId.name)
                )
                while (page != null) {
                    page.values.forEach { fileBlob ->
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

    override suspend fun getBlob(
        gcsPath: GcsPath
    ): BlobVisitor? {
        val blobId = gcsPath.blobId
        val blob = service.get(blobId)
        return if (blob != null) {
            BlobVisitorImpl(
                rootBlobId = blobId,
                blob = blob
            )
        } else null
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

/**
 * Provides access to a Blob returned from a [GoogleCloudApi.walkEntries] method.
 */
internal interface BlobVisitor {
    /**
     * Returns true if this Blob is the root blob that matches the `gcsPath` parameter of [GoogleCloudApi.walkEntries].
     */
    fun isRoot() = relativePath.isEmpty()

    /**
     * Returns the relative path of the blob wrt to the `gcsPath` parameter of [GoogleCloudApi.walkEntries].
     */
    val relativePath: String

    /**
     * Full gcsPath of the blob
     */
    val gcsPath: GcsPath

    /**
     * The filename of the blob (name after the last `/` in the [gcsPath].
     */
    val fileName: String
        get() = gcsPath.path.substringAfterLast('/')

    /**
     * The size of the blob
     */
    val size: Long

    /**
     * Opens the input stream to the blob. You must make sure to close it after using it.
     */
    fun obtainInputStream(): InputStream
}

private class BlobVisitorImpl(
    val rootBlobId: BlobId,
    private val blob: Blob
) : BlobVisitor {
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

    override val size: Long = blob.size
}
