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

package dev.androidx.ci.testRunner.vo

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.ftl.TestMatrix.OutcomeSummary.FAILURE
import dev.androidx.ci.generated.ftl.TestMatrix.OutcomeSummary.SUCCESS
import dev.zacsweers.moshix.reflect.MetadataKotlinJsonAdapterFactory

sealed class TestResult(
    val type: Type
) {
    abstract val allTestsPassed: Boolean

    abstract val hasFailedTest: Boolean

    abstract val failureLog: String

    data class CompleteRun(
        val matrices: List<TestMatrix>
    ) : TestResult(Type.COMPLETE_RUN) {
        override val allTestsPassed: Boolean by lazy {
            matrices.none {
                it.outcomeSummary != SUCCESS
            }
        }
        override val hasFailedTest: Boolean by lazy {
            matrices.any {
                it.outcomeSummary == FAILURE
            }
        }
        override val failureLog: String
            get() = buildString {
                val failed = matrices.filter {
                    it.outcomeSummary != SUCCESS
                }
                append("${failed.size} of ${matrices.size} failed")
                failed.forEach { failure ->
                    appendLine("Matrix: ${failure.testMatrixId}: URL: ${failure.resultStorage.resultsUrl}")
                }
            }
    }

    data class IncompleteRun(
        val stacktrace: String
    ) : TestResult(Type.INCOMPLETE_RUN) {
        override val allTestsPassed: Boolean
            get() = false
        override val hasFailedTest: Boolean
            get() = true
        override val failureLog: String
            get() = buildString {
                appendLine("FTL failed with an exception:")
                appendLine(stacktrace)
            }
    }

    fun toJson() = adapter.toJson(this)

    enum class Type {
        /**
         * Run finished successfully. This does not mean all tests passed, rather, it means we got
         * an outcome for all tests.
         */
        COMPLETE_RUN,

        /**
         * An unexpected error happened while running tests.
         */
        INCOMPLETE_RUN
    }

    companion object {
        private val moshi = Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(TestResult::class.java, "type")
                    .withSubtype(CompleteRun::class.java, Type.COMPLETE_RUN.name)
                    .withSubtype(IncompleteRun::class.java, Type.INCOMPLETE_RUN.name)
            )
            .addLast(MetadataKotlinJsonAdapterFactory())
            .build()
        private val adapter = moshi.adapter(TestResult::class.java).indent("  ").lenient()

        fun fromJson(json: String) = adapter.fromJson(json)
    }
}
