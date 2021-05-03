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

import okhttp3.internal.http.RealResponseBody
import okio.Buffer
import retrofit2.HttpException
import retrofit2.Response

fun <T> throwNotFound(): Nothing {
    throw HttpException(
        Response.error<T>(
            404,
            RealResponseBody(null, 0, Buffer())
        )
    )
}