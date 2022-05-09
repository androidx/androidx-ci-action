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

package dev.androidx.ci.testRunner.vo

import dev.androidx.ci.util.sha256

/**
 * Information about an APK file.
 */
internal data class ApkInfo(
    /**
     * Path of the ApkFile as seen in the output artifact
     */
    val filePath: String,
    /**
     * A hash value computed from the contents of the ApkFile. Is good enough to be used as a unique identifier.
     */
    val idHash: String
) {
    val fileName: String by lazy {
        filePath.split('/').last()
    }

    val fileNameWithoutExtension: String by lazy {
        fileName.split('.').dropLast(1).joinToString(".")
    }

    val filePathWithoutExtension: String by lazy {
        filePath.split('.').dropLast(1).joinToString("/")
    }

    companion object {
        fun create(
            filePath: String,
            contents: ByteArray
        ) = ApkInfo(
            filePath = filePath,
            idHash = sha256(contents)
        )
    }
}
