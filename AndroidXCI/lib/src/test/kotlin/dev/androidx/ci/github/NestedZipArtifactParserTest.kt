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

package dev.androidx.ci.github

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeGithubApi
import dev.androidx.ci.fake.createZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.random.Random

@RunWith(JUnit4::class)
internal class NestedZipArtifactParserTest {
    private val random = Random(System.nanoTime())
    private val api = FakeGithubApi()

    private val bytes_1 = random.nextBytes(10)
    private val bytes_2 = random.nextBytes(44)

    val bytes_3_1 = random.nextBytes(10)
    private val bytes_3 = createZipFile(
        listOf(
            "nested_1_1" to bytes_3_1
        )
    ).readByteArray()

    private val bytes_4_1 = random.nextBytes(10)
    private val bytes_4_2_1 = random.nextBytes(20)
    private val bytes_4_2_2 = random.nextBytes(5)
    private val bytes_4_3 = random.nextBytes(41)

    private val bytes_4 = createZipFile(
        listOf(
            "nested_2_1" to bytes_4_1,
            "nested_2_2.zip" to createZipFile(
                listOf(
                    "nested_2_2_1" to bytes_4_2_1,
                    "nested_2_2_2" to bytes_4_2_2
                )
            ).readByteArray(),
            "nested_2_3" to bytes_4_3,
        )
    ).readByteArray()

    init {
        api.putArchive(
            "artifacts.zip",
            listOf(
                "foo.txt" to bytes_1,
                "app.apk" to bytes_2,
                "zipped1.zip" to bytes_3,
                "zipped2.zip" to bytes_4
            )
        )
    }

    @Test
    fun shallowContents() {
        val entries = runBlocking {
            api.zipArchiveStream("artifacts.zip", unwrapNestedZipEntries = false).map {
                it.entry.name to it.bytes.signature()
            }.toList()
        }
        assertThat(
            entries
        ).containsExactly(
            "foo.txt" to bytes_1.signature(),
            "app.apk" to bytes_2.signature(),
            "zipped1.zip" to bytes_3.signature(),
            "zipped2.zip" to bytes_4.signature()
        )
    }

    @Test
    fun nestedContents() {
        val entries = runBlocking {
            api.zipArchiveStream("artifacts.zip", unwrapNestedZipEntries = true).map {
                it.entry.name to it.bytes.signature()
            }.toList()
        }
        assertThat(
            entries
        ).containsExactlyElementsIn(
            listOf(
                "foo.txt" to bytes_1,
                "app.apk" to bytes_2,
                "nested_1_1" to bytes_3_1,
                "nested_2_1" to bytes_4_1,
                "nested_2_2_1" to bytes_4_2_1,
                "nested_2_2_2" to bytes_4_2_2,
                "nested_2_3" to bytes_4_3
            ).map { it.first to it.second.signature() }
        )
    }

    private fun ByteArray.signature() = this.decodeToString()
}
