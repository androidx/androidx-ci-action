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
 * Response from artifacts endpoint in github API
 */
internal data class ArtifactsResponse(
    val artifacts: List<Artifact>
) {
    data class Artifact(
        val id: String,
        val url: String,
        val name: String,
        @Json(name = "archive_download_url")
        val archiveDownloadUrl: String
    )
}
