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

import dev.androidx.ci.github.GithubApi
import dev.androidx.ci.github.dto.ArtifactsResponse
import okhttp3.ResponseBody
import okhttp3.internal.http.RealResponseBody
import okio.Buffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FakeGithubApi : GithubApi {
    private val artifacts = mutableMapOf<String, ArtifactsResponse>()
    private val zipArchives = mutableMapOf<String, ResponseBody>()

    fun putArtifact(
        runId: String,
        response: ArtifactsResponse
    ) {
        artifacts[runId] = response
    }

    fun putArchive(
        path: String,
        zipEntries: List<Pair<String, ByteArray>>
    ) {
        val zipBytes = createZipFile(
            entries = zipEntries
        )
        val responseBody = RealResponseBody(
            contentTypeString = "application/zip",
            contentLength = zipBytes.size,
            source = zipBytes
        )
        zipArchives[path] = responseBody
    }

    private fun createZipFile(
        entries: List<Pair<String, ByteArray>>
    ): Buffer {
        val buff = Buffer()
        ZipOutputStream(buff.outputStream()).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents)
            }
        }
        return buff
    }

    override suspend fun artifacts(runId: String): ArtifactsResponse {
        return artifacts[runId] ?: throwNotFound<ArtifactsResponse>()
    }

    override suspend fun zipArchive(path: String): ResponseBody {
        return zipArchives[path] ?: throwNotFound<ResponseBody>()
    }
}
