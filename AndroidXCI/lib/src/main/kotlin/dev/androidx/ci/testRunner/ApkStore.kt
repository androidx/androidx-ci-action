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

    /**
     * Returns the placeholder app APK if the test does not have an app apk (required by FTL).
     */
    suspend fun getPlaceholderApk() = placeholderApk.get()

    /**
     * Uploads the given APK or returns the existing one if it was uploaded before.
     *
     * @param name The name of the APK. Anything that identifies it good enough, only used for retrieval and bucket
     *        organization.
     * @param bytes The bytes of the APK file.
     */
    suspend fun uploadApk(
        name: String,
        bytes: ByteArray
    ): UploadedApk {
        val apkInfo = ApkInfo.create(
            filePath = name,
            contents = bytes
        )
        getUploadedApk(apkInfo)?.let {
            return it
        }
        val relativePath = apkInfo.gcpRelativePath()
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

    /**
     * Returns the APK for the given [name] and [sha256] if it already exists in the backend.
     */
    suspend fun getUploadedApk(
        name: String,
        sha256: String
    ): UploadedApk? {
        return getUploadedApk(
            ApkInfo(
                filePath = name,
                idHash = sha256
            )
        )
    }

    private suspend fun getUploadedApk(
        apkInfo: ApkInfo
    ): UploadedApk? {
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
        return null
    }

    private fun ApkInfo.gcpRelativePath() = filePathWithoutExtension + "/" + this.idHash + ".apk"
}
