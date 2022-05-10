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

package dev.androidx.ci.testRunner

import dev.androidx.ci.gcloud.GoogleCloudApi
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.testRunner.vo.UploadedApk
import dev.androidx.ci.util.LazyComputedValue
import org.apache.logging.log4j.kotlin.logger

/**
 * Store for Apks
 *
 * This class is the handler between GCP and rest of the code where ApkStore handles uploading new Apks and de-duping
 * them.
 */
internal class ApkStore(
    private val googleCloudApi: GoogleCloudApi,
) {
    private val logger = logger()

    private val placeholderApk = LazyComputedValue<UploadedApk> {
        val bytes = ApkStore::class.java.getResource("/placeholderApp.apk")?.openStream()?.use {
            it.readAllBytes()
        }
        checkNotNull(bytes) {
            "Cannot read placeholder apk from resources"
        }
        uploadApk("placeholderApp.apk", bytes)
    }

    suspend fun getPlaceholderApk() = placeholderApk.get()

    suspend fun uploadApk(
        name: String,
        bytes: ByteArray
    ): UploadedApk {
        val apkInfo = ApkInfo.create(
            filePath = name,
            contents = bytes
        )
        val relativePath = apkInfo.gcpRelativePath()
        logger.info {
            "checking if apk already exists"
        }
        val existing = googleCloudApi.existingFilePath(relativePath)
        if (existing != null) {
            logger.info { "apk exists already, returning without re-upload: $existing" }
            return UploadedApk(
                gcsPath = existing,
                apkInfo = apkInfo
            )
        }
        logger.info {
            "uploading $name to $relativePath"
        }
        val gcsPath = googleCloudApi.upload(
            relativePath = relativePath,
            bytes = bytes
        )
        return UploadedApk(
            gcsPath = gcsPath,
            apkInfo = apkInfo
        ).also {
            logger.info {
                "completed uploading apk: $it"
            }
        }
    }

    private fun ApkInfo.gcpRelativePath() = filePathWithoutExtension + "/" + this.idHash + ".apk"
}
