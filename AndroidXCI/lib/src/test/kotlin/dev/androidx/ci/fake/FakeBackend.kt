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

import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.github.dto.ArtifactsResponse
import dev.androidx.ci.github.dto.RunInfo
import java.util.UUID
import kotlin.random.Random

internal class FakeBackend(
    val fakeGithubApi: FakeGithubApi = FakeGithubApi(),
    val fakeGoogleCloudApi: FakeGoogleCloudApi = FakeGoogleCloudApi(),
    val fakeFirebaseTestLabApi: FakeFirebaseTestLabApi = FakeFirebaseTestLabApi(),
    val fakeToolsResultApi: FakeToolsResultApi = FakeToolsResultApi(),
    val datastoreApi: FakeDatastore = FakeDatastore(),
    val firebaseProjectId: String = "project1",
    randomSeed: Long = -1
) {
    private val random = Random(randomSeed)
    fun createRun(runId: String, artifacts: List<ArtifactsResponse.Artifact>): RunInfo {
        val response = ArtifactsResponse(
            artifacts = artifacts
        )
        return fakeGithubApi.putArtifact(runId, response)
    }

    fun finishAllTests(
        outcome: TestMatrix.OutcomeSummary
    ) {
        fakeFirebaseTestLabApi.getTestMatrices().forEach {
            finishTest(
                testMatrixId = it.testMatrixId!!,
                outcome = outcome
            )
        }
    }

    fun finishTest(
        testMatrixId: String,
        outcome: TestMatrix.OutcomeSummary
    ) {
        val testMatrix = fakeFirebaseTestLabApi.getTestMatrices().firstOrNull {
            it.testMatrixId == testMatrixId
        } ?: error("cannot find test matrix $testMatrixId")
        fakeFirebaseTestLabApi.setTestMatrix(
            testMatrix.copy(
                state = TestMatrix.State.FINISHED,
                outcomeSummary = outcome
            )
        )
    }

    /**
     * Creates a test archive for the Github API and returns the absolute path for it
     */
    fun createArchive(
        vararg contentNames: String
    ): ArtifactsResponse.Artifact {
        val path = "github.com/${UUID.randomUUID()}"
        fakeGithubApi.putArchive(
            path = path,
            zipEntries = contentNames.map {
                it to random.nextBytes(10)
            }
        )
        return ArtifactsResponse.Artifact(
            id = UUID.randomUUID().toString(),
            url = path,
            name = "random-artifact-${random.nextBytes(10).toString(Charsets.UTF_8)}",
            archiveDownloadUrl = path
        )
    }
}
