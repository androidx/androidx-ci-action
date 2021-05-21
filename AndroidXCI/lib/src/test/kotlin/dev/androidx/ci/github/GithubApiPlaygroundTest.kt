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

package dev.androidx.ci.github

import dev.androidx.ci.config.Config
import dev.androidx.ci.github.dto.IssueComment
import dev.androidx.ci.util.GithubAuthRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class GithubApiPlaygroundTest {
    @get:Rule
    val githubAuthRule = GithubAuthRule()

    private val api by lazy {
        GithubApi.build(
            config = Config.Github(
                owner = "yigit",
                repo = "github-workflow-playground",
                token = githubAuthRule.githubToken
            )
        )
    }

    @Test
    fun getRunInfo() = runBlocking<Unit> {
        val info = api.runInfo("787303337")
        val issueNumber = info.pullRequests.firstOrNull()?.number ?: return@runBlocking
        val labels = api.getLabels(issueNumber)
        println(labels)
        if (true) return@runBlocking
        api.tryDeletingLabel(issueNumber, "l1")

        val addLabelResponse = api.addLabels(issueNumber, listOf("l1", "l4"))
        println(info)
        println(addLabelResponse)
        api.comment(issueNumber, IssueComment(body = "test"))
    }
}
