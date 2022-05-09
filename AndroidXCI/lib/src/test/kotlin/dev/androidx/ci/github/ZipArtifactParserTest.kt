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
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.lang.IllegalStateException
import kotlin.random.Random

@RunWith(JUnit4::class)
internal class ZipArtifactParserTest {
    private val random = Random(System.nanoTime())
    private val api = FakeGithubApi()

    private val entry1Bytes = random.nextBytes(10)
    private val entry2Bytes = random.nextBytes(44)

    init {
        api.putArchive(
            "artifacts.zip",
            listOf(
                "foo.txt" to entry1Bytes,
                "app.apk" to entry2Bytes
            )
        )
    }

    @Test
    fun zipArtifactContents() {
        val entries = runBlocking {
            api.zipArchiveStream("artifacts.zip").map {
                it.entry.name to it.bytes
            }.toList()
        }
        assertThat(
            entries.map { it.first }
        ).containsExactly("foo.txt", "app.apk")
        assertThat(entries[0].second)
            .isEqualTo(entry1Bytes)
        assertThat(entries[1].second)
            .isEqualTo(entry2Bytes)
    }

    @Test
    fun readZipEntryAfterClosedStream() {
        lateinit var firstEntry: ZipEntryScope
        runBlocking {
            api.zipArchiveStream("artifacts.zip").forEachIndexed { index, zipEntryScope ->
                if (index == 0) {
                    firstEntry = zipEntryScope
                }
            }
        }
        // read bytes after closing stream
        val bytes = kotlin.runCatching {
            firstEntry.bytes
        }
        assertThat(bytes.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }
}
