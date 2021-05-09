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

import com.google.auth.Credentials
import com.google.auth.oauth2.ServiceAccountCredentials
import dev.androidx.ci.config.Config
import kotlinx.coroutines.runBlocking
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FirestoreApiPlayground {
    private lateinit var cred: Credentials
    private val projectId = "androidx-dev-prod"
    @Before
    fun loadCredentials() {
        val envValue = System.getenv("ANDROIDX_GCLOUD_CREDENTIALS")
            ?: throw AssumptionViolatedException("skip test without credentials")
        cred = ServiceAccountCredentials.fromStream(
            envValue.byteInputStream(Charsets.UTF_8)
        )
    }

    @Test
    fun foo() = runBlocking<Unit> {
        val firestore = FirestoreApi.build(
            Config.Firestore(
                credentials = cred
            )
        )
//        val doc = firestore.put(
//            projectId = projectId,
//            collectionId = "local-testing",
//            documentPath = "doc2",
//            document = FirestoreDocument(
//                fields = mapOf(
//                    "a" to "c"
//                )
//            )
//        )
//         println("received: $doc")
        val result = firestore.get(
            projectId = projectId,
            collectionId = "local-testing",
            documentPath = "dsadsasa",
            // documentPath = "PreSubmitWorkflow/0018360af96e915ffe708dfb054bfd5d1e6f9ded"
        )
        println(result)
    }
}
