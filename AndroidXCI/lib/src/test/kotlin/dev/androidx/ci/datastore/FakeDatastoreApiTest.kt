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

package dev.androidx.ci.datastore

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.StringValue
import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeDatastore
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class FakeDatastoreApiTest {
    private val datastore = FakeDatastore()
    @Test
    fun readWrite() = runBlocking<Unit> {
        val key = datastore.createKey(
            kind = "local-test",
            id = "foo"
        )
        val nonExisting = datastore.get(key)
        assertThat(nonExisting).isNull()
        val entity = Entity.newBuilder()
            .set("a", "b")
            .set("c", "dd")
            .setKey(
                key
            )
            .build()
        val written = datastore.put(entity)
        assertThat(
            written.properties
        ).containsExactlyEntriesIn(
            mapOf(
                "a" to StringValue("b"),
                "c" to StringValue("dd")
            )
        )
    }
}
