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
import java.io.File

internal class DownloadedTestResultsTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun emptyFolder() {
        val downloads = DownloadedTestResults.buildFrom(
            testMatrixId = "abc",
            folder = tmpFolder.newFolder()
        )
        assertThat(downloads.instrumentationResults).isEmpty()
        assertThat(downloads.logFiles).isEmpty()
        assertThat(downloads.mergedTestResults).isEmpty()
        assertThat(downloads.testMatrixId).isEqualTo("abc")
    }

    @Test
    fun realResult() {
        // copied layout from a real download
        val rootFolder = tmpFolder.newFolder("root")
        rootFolder.apply {
            resolve("result.json").createNewFile()
            resolve("testMatrices").also(File::mkdirs).apply {
                resolve("matrix-abc").also(File::mkdirs).apply {
                    resolve("Nexus5-19-en-portrait-test_results_merged.xml").createNewFile()
                    resolve("Nexus5-19-en-portrait").also(File::mkdirs).apply {
                        resolve("bugreport.txt").createNewFile()
                        resolve("instrumentation.results").createNewFile()
                        resolve("logcat").createNewFile()
                        resolve("test_result_1.xml").createNewFile()
                    }
                }
            }
        }
        val downloads = DownloadedTestResults.buildFrom(
            testMatrixId = "abc",
            folder = rootFolder
        )
        assertThat(downloads.testMatrixId).isEqualTo("abc")
        assertThat(downloads.logFiles).containsExactly(
            rootFolder.resolve("testMatrices/matrix-abc/Nexus5-19-en-portrait/logcat")
        )
        assertThat(downloads.instrumentationResults).containsExactly(
            rootFolder.resolve("testMatrices/matrix-abc/Nexus5-19-en-portrait/instrumentation.results")
        )
        assertThat(downloads.mergedTestResults).containsExactly(
            rootFolder.resolve("testMatrices/matrix-abc/Nexus5-19-en-portrait-test_results_merged.xml")
        )
        assertThat(downloads.rootFolder).isEqualTo(rootFolder)
    }
}
