/*
 * Copyright 2022 The Android Open Source Project
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

package dev.androidx.ci.testRunner

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class ScheduledFtlTestsSerializationTest {
    @get:Rule
    val tmmFolder = TemporaryFolder()

    @Test
    fun nonExistingFile() {
        val result = kotlin.runCatching {
            TestRunnerService.ScheduledFtlTests.readFromFile(
                tmmFolder.root.resolve("myFile.json")
            )
        }
        assertThat(
            result.exceptionOrNull()
        ).hasMessageThat().contains(
            "myFile.json does not exist"
        )
    }

    @Test
    fun badJson() {
        val result = kotlin.runCatching {
            TestRunnerService.ScheduledFtlTests.readFromFile(
                tmmFolder.root.resolve("myFile.json").also {
                    it.writeText("{}")
                }
            )
        }
        assertThat(
            result.exceptionOrNull()
        ).hasMessageThat().contains(
            "Required value 'testMatrixIds' missing"
        )
    }

    @Test
    fun goodJson() {
        val subject = TestRunnerService.ScheduledFtlTests(
            testMatrixIds = listOf("a", "b", "C"),
            newTests = 2,
            cachedTests = 3
        )
        val file = tmmFolder.root.resolve("myFile.json")
        subject.writeToFile(file)
        val result = TestRunnerService.ScheduledFtlTests.readFromFile(file)
        assertThat(
            result
        ).isEqualTo(
            subject
        )
        assertThat(
            result
        ).isNotSameInstanceAs(subject)
    }
}
