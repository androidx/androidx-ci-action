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

package dev.androidx.ci.github.dto

import com.squareup.moshi.Json

/**
 * Details of a git commit
 */
internal data class CommitInfo(
    /**
     * The commit sha
     */
    val sha: String,
    /**
     * The state of the commit based on [statuses]
     */
    val state: State,
    /**
     * Individual status reports for the commit.
     */
    val statuses: List<Status>
) {
    data class Status(
        val url: String,
        val id: String,
        val state: State,
        val description: String?,
        @Json(name = "target_url")
        val targetUrl: String?,
        val context: String?
    )
    enum class State {
        @Json(name = "error")
        ERROR,
        @Json(name = "failure")
        FAILURE,
        @Json(name = "pending")
        PENDING,
        @Json(name = "success")
        SUCCESS,
    }

    data class Update(
        val state: State,
        /**
         * The url which will be linked from the Github UI
         */
        @Json(name = "target_url")
        val targetUrl: String,
        /**
         * Some description of what happened
         */
        val description: String,
        /**
         * The context for this check. This is like the ID of the status, should usually be the same for the same type
         * of check.
         */
        val context: String
    )
}
