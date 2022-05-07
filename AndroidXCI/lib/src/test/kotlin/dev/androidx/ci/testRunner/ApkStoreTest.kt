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

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeGoogleCloudApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class ApkStoreTest {
    private val gcpApi = FakeGoogleCloudApi()
    private val apkStore = ApkStore(gcpApi)

    @Test
    fun upload() = runBlocking<Unit> {
        val uploaded = apkStore.uploadApk(
            name = "foo.apk",
            bytes = byteArrayOf(0, 1, 2, 3)
        )
        val artifacts = gcpApi.artifacts()
        assertThat(gcpApi.uploadCount).isEqualTo(1)
        assertThat(artifacts).hasSize(1)
        assertThat(artifacts.values.first()).isEqualTo(byteArrayOf(0, 1, 2, 3))
        assertThat(uploaded.gcsPath).isEqualTo(
            artifacts.keys.first()
        )
        // try to upload again, should not upload
        val reUploaded = apkStore.uploadApk(
            name = "foo.apk",
            bytes = byteArrayOf(0, 1, 2, 3)
        )
        assertThat(gcpApi.uploadCount).isEqualTo(1)
        assertThat(uploaded).isEqualTo(reUploaded)
        // try to upload with new content, should upload
        val newContent = apkStore.uploadApk(
            name = "foo.apk",
            bytes = byteArrayOf(3, 4, 5, 6)
        )
        assertThat(gcpApi.uploadCount).isEqualTo(2)
        assertThat(gcpApi.artifacts()).hasSize(2)
        // new content, should have a different path
        assertThat(newContent.gcsPath).isNotEqualTo(uploaded.gcsPath)
        // try to upload with new name, should upload
        val newName = apkStore.uploadApk(
            name = "bar.apk",
            bytes = byteArrayOf(0, 1, 2, 3)
        )
        assertThat(gcpApi.uploadCount).isEqualTo(3)
        assertThat(gcpApi.artifacts()).hasSize(3)
        assertThat(newName.gcsPath).isNotEqualTo(uploaded.gcsPath)
    }

    @Test
    fun placeholderApk() = runBlocking<Unit> {
        val placeholderApk = apkStore.getPlaceholderApk()
        assertThat(gcpApi.uploadCount).isEqualTo(1)
        assertThat(gcpApi.artifacts()[placeholderApk.gcsPath]).isNotNull()
    }
}
