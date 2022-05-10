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

import com.google.cloud.storage.BlobInfo

/**
 * Represents the unique path of a Google Cloud Storage object.
 */
internal class GcsPath(path: String) {
    init {
        check(path.startsWith("gs://")) {
            "Invalid Google Cloud Storage path: $path"
        }
    }
    val path = path.trim {
        it == '/'
    }

    operator fun plus(other: String): GcsPath {
        val trimmed = other.trim {
            it == '/'
        }
        return GcsPath("$path/$trimmed")
    }

    override fun toString(): String {
        return path
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GcsPath

        if (path != other.path) return false

        return true
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }

    companion object {
        fun create(
            blob: BlobInfo
        ): GcsPath = create(
            bucketName = blob.bucket,
            bucketPath = blob.name
        )

        fun create(
            bucketName: String,
            bucketPath: String
        ): GcsPath = GcsPath(
            "gs://$bucketName/$bucketPath"
        )
    }
}
