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
import dev.androidx.ci.github.dto.IssueComment
import dev.androidx.ci.github.dto.IssueLabel
import dev.androidx.ci.github.dto.RunInfo
import okhttp3.ResponseBody
import okhttp3.internal.http.RealResponseBody
import okio.Buffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FakeGithubApi : GithubApi {
    private val workflowRuns = mutableMapOf<String, FakeWorkflowRun>()
    private val zipArchives = mutableMapOf<String, ResponseBody>()
    private val issues = mutableMapOf<String, FakeIssue>()

    fun getComments(
        issueNumber: String
    ) = issues[issueNumber]?.comments

    override suspend fun getLabels(
        issueNumber: String
    ) = issues[issueNumber]?.labels ?: throwNotFound<List<IssueLabel>>()

    fun createIssue(
        issueNumber: String
    ) {
        val issue = FakeIssue(
            number = issueNumber,
            labels = emptyList(),
            comments = emptyList()
        )
    }

    fun putArtifact(
        runId: String,
        response: ArtifactsResponse
    ) {
        val run = workflowRuns[runId] ?: FakeWorkflowRun(
            id = runId,
            artifacts = ArtifactsResponse(emptyList()),
            info = RunInfo(
                id = runId,
                name = "test run $runId",
                url = "https://testing/run/$runId"
            )
        )
        workflowRuns[runId] = run.copy(
            artifacts = response
        )
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
        return workflowRuns[runId]?.artifacts ?: throwNotFound<ArtifactsResponse>()
    }

    override suspend fun zipArchive(path: String): ResponseBody {
        return zipArchives[path] ?: throwNotFound<ResponseBody>()
    }

    override suspend fun runInfo(runId: String): RunInfo {
        return workflowRuns[runId]?.info ?: throwNotFound<RunInfo>()
    }

    override suspend fun addLabels(issueNumber: String, labels: List<String>): List<IssueLabel> {
        val issue = issues[issueNumber] ?: throwNotFound<List<IssueLabel>>()
        val created = labels.map {
            IssueLabel(
                url = "https://label/$it",
                name = it
            )
        }
        issues[issueNumber] = issue.copy(
            labels = (issue.labels + created).distinct()
        )
        return created
    }

    override suspend fun deleteLabel(issueNumber: String, label: String) {
        val issue = issues[issueNumber] ?: throwNotFound<List<IssueLabel>>()
        val existing = issue.labels.firstOrNull {
            it.name == label
        } ?: throwNotFound<Unit>()
        issues[issueNumber] = issue.copy(
            labels = issue.labels - existing
        )
    }

    override suspend fun comment(issueNumber: String, comment: IssueComment): IssueComment {
        val issue = issues[issueNumber] ?: throwNotFound<List<IssueComment>>()
        issues[issueNumber] = issue.copy(
            comments = issue.comments + comment
        )
        return comment
    }

    private data class FakeWorkflowRun(
        val id: String,
        val artifacts: ArtifactsResponse,
        val info: RunInfo,
    )

    private data class FakeIssue(
        val number: String,
        val labels: List<IssueLabel>,
        val comments: List<IssueComment>
    )
}
