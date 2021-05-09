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

import dev.androidx.ci.firebase.FirestoreApi
import dev.androidx.ci.firebase.dto.FirestoreDocument

class FakeFirestoreApi : FirestoreApi {
    private val docs = mutableMapOf<String, FirestoreDocument>()
    private fun key(
        projectId: String,
        databaseId: String,
        collectionId: String,
        documentPath: String
    ) = listOf(projectId, databaseId, collectionId, documentPath).joinToString("/")
    override suspend fun get(
        projectId: String,
        databaseId: String,
        collectionId: String,
        documentPath: String
    ): FirestoreDocument {
        val key = key(projectId, databaseId, collectionId, documentPath)
        return docs[key] ?: throwNotFound<FirestoreDocument>()
    }

    override suspend fun put(
        projectId: String,
        databaseId: String,
        collectionId: String,
        documentPath: String,
        document: FirestoreDocument
    ): FirestoreDocument {
        val key = key(projectId, databaseId, collectionId, documentPath)
        val saved = document.copy(
            name = key,
            createTime = "create",
            updateTime = "update"
        )
        docs[key] = saved
        return saved
    }
}
