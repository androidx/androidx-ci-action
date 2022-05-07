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

package dev.androidx.ci.testRunner

import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.generated.testResults.History
import dev.androidx.ci.util.LazyComputedValue
import org.apache.logging.log4j.kotlin.logger
import java.util.UUID

/**
 * Handles histories for test runs.
 */
internal class ToolsResultStore(
    private val firebaseProjectId: String,
    private val toolsResultApi: ToolsResultApi
) {
    private val logger = logger()
    private val cache = mutableMapOf<String, LazyComputedValue<String>>()
    suspend fun getHistoryId(
        pkg: String
    ): String {
        check(pkg != "dev.androidx.ci.emptyapp") {
            "Package should not be the placeholder app"
        }
        logger.info {
            "finding history id for $pkg"
        }
        return cache.getOrPut(pkg) {
            LazyComputedValue {
                getOrCreateHistory(pkg).also {
                    logger.info {
                        "history id for $pkg is $it"
                    }
                }
            }
        }.get()
    }

    private suspend fun getOrCreateHistory(pkg: String): String {
        // there might be many, choose the first one
        toolsResultApi.getHistories(
            projectId = firebaseProjectId,
            name = pkg
        ).histories?.firstOrNull()?.let {
            logger.info {
                "found history id in existing histories: $it"
            }
            return checkNotNull(it.historyId) {
                "history id cannot be null"
            }
        }
        // create a new one
        val history = History(
            name = pkg,
            displayName = pkg,
            testPlatform = History.TestPlatform.android,
        )
        logger.info {
            "creating new history: $history"
        }
        val requestId = UUID.randomUUID().toString()
        return toolsResultApi.create(
            projectId = firebaseProjectId,
            requestId = requestId,
            history = history
        ).let {
            logger.info {
                "created new history: $history"
            }
            checkNotNull(it.historyId) {
                "history id cannot be null"
            }
        }
    }
}
