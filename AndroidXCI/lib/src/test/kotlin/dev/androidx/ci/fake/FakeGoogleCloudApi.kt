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

package dev.androidx.ci.fake

import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.gcloud.GoogleCloudApi

/**
 * A simple implementation of [GoogleCloudApi] for testing and verification.
 *
 * Unfortunately, there is no easy way to test our usage of the Storage API as the NIO local storage
 * does not support RPC methods (https://github.com/googleapis/java-storage-nio).
 *
 * This fake is useful for other tests that would interact with GCloud.
 */
class FakeGoogleCloudApi : GoogleCloudApi {
    private val rootGcsPath = GcsPath("gs://test")
    private val artifacts = mutableMapOf<GcsPath, ByteArray>()

    fun getArtifact(gcsPath: GcsPath) = artifacts[gcsPath]

    override suspend fun upload(relativePath: String, bytes: ByteArray): GcsPath {
        val path = makeGcsPath(relativePath)
        artifacts[path] = bytes
        return path
    }

    override suspend fun existingFilePath(relativePath: String): GcsPath? {
        val path = makeGcsPath(relativePath)
        return if (artifacts.containsKey(path)) {
            path
        } else {
            null
        }
    }

    private fun makeGcsPath(relativePath: String) = GcsPath.create(
        bucketName = "local-test",
        bucketPath = relativePath
    )

    override fun getGcsPath(relativePath: String): GcsPath = rootGcsPath + relativePath
}