/*
 * Copyright 2023 The Android Open Source Project
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

/**
 * Represents a device that run a test
 */
data class DeviceRun internal constructor(
    /**
     * The device identifier that run the test.
     * e.g. redfin-30-en-portrait
     * e.g. redfin-30-en-portrait_rerun_1
     * e.g. redfin-30-en-portrait-shard_0
     * e.g. redfin-30-en-portrait-shard_2-rerun_2
     */
    val fullDeviceId: String,
    /**
     * The device identifier that run the test, excluding shard or rerun numbers.
     * e.g. redfin-30-en-portrait
     */
    val deviceId: String,
    /**
     * The run number. First run is 0 and any subsequent re-run starts from 1.
     */
    val runNumber: Int,
    /**
     * The shard number. It will be `null` if the test is not sharded. Otherwise,
     * it will be the shard index starting from 0
     */
    val shard: Int?
) {
    companion object {
        private val regex = listOf(
            """^(.*?)""", // non-greedy beginning of the text
            """([_-](shard|rerun)_(\d+))?""", // rerun or shard, if it exists
            """([_-](shard|rerun)_(\d+))?""", // rerun or shard, if it exists
        ).joinToString("").toRegex(RegexOption.IGNORE_CASE)

        /**
         * Parses the given [fullDeviceId] to create [DeviceRun] which
         * has components for various parts of it.
         */
        fun create(
            fullDeviceId: String
        ): DeviceRun {
            // the regex, which will turn it into a list of key, value pairs.
            // later we'll scan it below to update values.
            val match = regex.matchEntire(fullDeviceId)
            var deviceId = fullDeviceId
            var runNumber = 0
            var shard: Int? = null
            match?.let { result ->
                result.groupValues.scanIndexed("") { index, prev, next ->
                    when (prev) {
                        fullDeviceId -> {
                            // group 0 is full text, group 1 is the
                            // device id
                            if (index == 1) {
                                deviceId = next
                            }
                        }
                        "rerun" -> {
                            runNumber = next.toIntOrNull() ?: 0
                        }
                        "shard" -> {
                            shard = next.toIntOrNull()
                        }
                    }
                    // return next as accumulated so it becomes previous
                    next
                }
            }
            return DeviceRun(
                fullDeviceId = fullDeviceId,
                deviceId = deviceId,
                runNumber = runNumber,
                shard = shard
            )
        }
    }
}
