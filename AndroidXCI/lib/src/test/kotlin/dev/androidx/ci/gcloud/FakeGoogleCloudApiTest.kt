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

package dev.androidx.ci.gcloud

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeGoogleCloudApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
internal class FakeGoogleCloudApiTest {
    private val api = FakeGoogleCloudApi()
    private val testScope = TestCoroutineScope()

    @Test
    fun putArtifact() = testScope.runBlockingTest {
        val result = api.upload("/foo/bar", byteArrayOf(1, 2, 3))
        assertThat(api.getArtifact(result)).isEqualTo(byteArrayOf(1, 2, 3))
    }
}
