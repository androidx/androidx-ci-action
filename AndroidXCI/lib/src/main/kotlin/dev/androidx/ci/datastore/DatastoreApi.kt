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

import com.google.cloud.datastore.DatastoreOptions
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.FullEntity
import com.google.cloud.datastore.IncompleteKey
import com.google.cloud.datastore.Key
import dev.androidx.ci.config.Config
import dev.androidx.ci.util.configure
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Interface for Datastore communications so that we can also fake it in tests.
 */
internal interface DatastoreApi {
    // Default object kind that is used to group datastore objects.
    val testRunObjectKind: String
    /**
     * Creates a key for the datastore item
     */
    fun createKey(
        kind: String,
        id: String
    ): Key

    /**
     * Puts the entity into Datastore, replacing it if it exists
     */
    suspend fun put(
        entity: FullEntity<IncompleteKey>
    ): Entity

    /**
     * Gets the entity with the given key form Datastore, or null
     * if it does not exist.
     */
    suspend fun get(
        key: Key
    ): Entity?

    companion object {
        fun build(
            config: Config.Datastore,
            context: CoroutineContext
        ): DatastoreApi = DatastoreApiImpl(config, context)
    }
}

private class DatastoreApiImpl(
    config: Config.Datastore,
    private val context: CoroutineContext
) : DatastoreApi {
    override val testRunObjectKind: String = config.testRunObjectKind
    private val service by lazy {
        DatastoreOptions.newBuilder()
            .configure(config.gcp)
            .build().service
    }

    override fun createKey(
        kind: String,
        id: String
    ): Key = service.newKeyFactory().setKind(kind).newKey(id)

    override suspend fun put(
        entity: FullEntity<IncompleteKey>
    ): Entity = withContext(context) {
        service.put(entity)
    }

    override suspend fun get(
        key: Key
    ): Entity? = withContext(context) {
        service.get(key)
    }
}
