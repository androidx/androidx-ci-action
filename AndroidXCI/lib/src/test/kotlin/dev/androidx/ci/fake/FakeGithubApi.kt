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
import dev.androidx.ci.github.dto.CommitInfo
import dev.androidx.ci.github.dto.RunInfo
import okhttp3.ResponseBody
import okhttp3.internal.http.RealResponseBody
import okio.Buffer
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class FakeGithubApi : GithubApi {
    private val runInfos = mutableMapOf<String, RunInfo>()
    private val commitInfos = mutableMapOf<String, CommitInfo>()
    private val artifacts = mutableMapOf<String, ArtifactsResponse>()
    private val zipArchives = mutableMapOf<String, ResponseBody>()

    private fun getOrCreateRunInfo(runId: String): RunInfo {
        return runInfos.getOrPut(runId) {
            val sha = UUID.randomUUID().toString()
            commitInfos[sha] = CommitInfo(
                sha = sha,
                state = CommitInfo.State.PENDING,
                statuses = emptyList()
            )
            RunInfo(
                id = runId,
                name = "test-run",
                url = "https://test/run/$runId",
                headSha = sha
            )
        }
    }

    fun putArtifact(
        runId: String,
        response: ArtifactsResponse
    ): RunInfo {
        val runInfo = getOrCreateRunInfo(runId)
        artifacts[runInfo.id] = response
        return runInfo
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

    override suspend fun artifacts(runId: String): ArtifactsResponse {
        return artifacts[runId] ?: throwNotFound<ArtifactsResponse>()
    }

    override suspend fun zipArchive(path: String): ResponseBody {
        return zipArchives[path] ?: throwNotFound<ResponseBody>()
    }

    override suspend fun runInfo(runId: String): RunInfo {
        return runInfos[runId] ?: throwNotFound<RunInfo>()
    }

    override suspend fun commitStatus(ref: String): CommitInfo {
        return commitInfos[ref] ?: throwNotFound<CommitInfo>()
    }

    override suspend fun updateCommitStatus(sha: String, update: CommitInfo.Update): CommitInfo.Status {
        val commitInfo = commitInfos[sha] ?: throwNotFound<CommitInfo.Status>()
        val newStatus = CommitInfo.Status(
            url = "https://status/$sha",
            id = UUID.randomUUID().toString(),
            description = update.description,
            state = update.state,
            targetUrl = update.targetUrl,
            context = update.context
        )
        val combinedStatuses = (listOf(newStatus) + commitInfo.statuses).distinctBy {
            it.context
        }
        commitInfos[sha] = commitInfo.copy(
            statuses = combinedStatuses,
            state = combinedStatuses.minByOrNull { it.state }?.state ?: CommitInfo.State.PENDING
        )
        return newStatus
    }
}

public fun createZipFile(
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
