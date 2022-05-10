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

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.IncompleteKey
import com.google.cloud.datastore.Key
import dev.androidx.ci.datastore.DatastoreApi
import java.util.UUID

internal class FakeDatastore : DatastoreApi {
    override val testRunObjectKind: String
        get() = "Fake"
    private val data = mutableMapOf<Key, Entity>()
    override fun createKey(kind: String, id: String): Key {
        return Key.newBuilder("local", kind, id).build()
    }

    override suspend fun put(entity: FullEntity<IncompleteKey>): Entity {
        val key: Key = (entity.key as? Key) ?: createKey(kind = "random", id = UUID.randomUUID().toString())
        val built = Entity.newBuilder(key, entity).build()
        data[key] = built
        return built
    }

    override suspend fun get(key: Key): Entity? {
        return data[key]
    }
}
