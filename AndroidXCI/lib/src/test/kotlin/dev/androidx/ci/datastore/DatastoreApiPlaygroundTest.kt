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
import dev.androidx.ci.config.Config
import dev.androidx.ci.util.GoogleCloudCredentialsRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * This is not a real test. Instead, a utility to play with Google Cloud DataStore API.
 *
 * To run it, you'll need Google Cloud Authentication in your environment.
 *
 * export ANDROIDX_GCLOUD_CREDENTIALS="<cloud json key from iam>"
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DatastoreApiPlaygroundTest {
    @get:Rule
    val playgroundCredentialsRule = GoogleCloudCredentialsRule()

    private val testScope = TestScope()

    private val datastore by lazy {
        DatastoreApi.build(
            Config.Datastore(
                gcp = playgroundCredentialsRule.gcpConfig,
                testRunObjectKind = Config.Datastore.GITHUB_OBJECT_KIND,
            ),
            context = testScope.coroutineContext
        )
    }

    @Test
    fun readWrite() = testScope.runTest {
        val key = datastore.createKey(
            kind = "local-test",
            id = "foo"
        )
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
