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

package dev.androidx.ci.firebase

import dev.androidx.ci.config.Config
import okhttp3.OkHttpClient

/**
 * Adds authentication to the Rest API call with Google credentials
 */
internal fun OkHttpClient.Builder.authenticateWith(
    gcpConfig: Config.Gcp
) = addInterceptor {
    val requestMetadata = gcpConfig.credentials.getRequestMetadata(
        it.request().url.toUri()
    )
    val newBuilder = it.request().newBuilder()
    requestMetadata.forEach { (key, values) ->
        values.firstOrNull()?.let { value ->
            newBuilder.addHeader(key, value)
        }
    }
    newBuilder.addHeader("X-Goog-User-Project", gcpConfig.projectId)
    it.proceed(newBuilder.build())
}
