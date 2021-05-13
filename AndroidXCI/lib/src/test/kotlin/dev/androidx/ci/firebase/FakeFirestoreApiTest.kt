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

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeFirestoreApi
import dev.androidx.ci.firebase.dto.FirestoreDocument
import kotlinx.coroutines.runBlocking
import org.junit.Test

class FakeFirestoreApiTest {
    private val api = FakeFirestoreApi()

    @Test
    fun put() {
        val doc = FirestoreDocument(
            fields = mapOf("a" to "b")
        )
        val received = runBlocking {
            api.put(
                projectId = "p1",
                collectionId = "c1",
                documentPath = "foo/bar",
                document = doc
            )
        }
        assertThat(received.name).isEqualTo("p1/(default)/c1/foo/bar")
    }
}
