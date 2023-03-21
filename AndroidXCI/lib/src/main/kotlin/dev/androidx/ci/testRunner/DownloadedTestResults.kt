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

import java.io.File

/**
 * Data class to keep references to downloaded artifacts for discoverability on the client
 * side.
 */
data class DownloadedTestResults(
    val testMatrixId: String,
    val rootFolder: File,
    val mergedTestResults: List<File>,
    val instrumentationResults: List<File>,
    val logFiles: List<File>,
    val testcaseLogFiles: List<File>
) {
    companion object {
        /**
         * Collects the test results from the download folder
         */
        internal fun buildFrom(
            testMatrixId: String,
            folder: File
        ): DownloadedTestResults {
            val mergedResults = folder.walkTopDown().filter {
                it.name.endsWith("test_results_merged.xml")
            }
            val instrumentationResultFiles = folder.walkBottomUp().filter {
                it.name == "instrumentation.results"
            }
            val logFiles = folder.walkBottomUp().filter {
                it.name == "logcat"
            }
            val testcaseLogFiles = folder.walkBottomUp().filter {
                it.name.endsWith("_logcat")
            }
            return DownloadedTestResults(
                testMatrixId = testMatrixId,
                rootFolder = folder,
                mergedTestResults = mergedResults.toList(),
                instrumentationResults = instrumentationResultFiles.toList(),
                logFiles = logFiles.toList(),
                testcaseLogFiles = testcaseLogFiles.toList()
            )
        }
    }
}
