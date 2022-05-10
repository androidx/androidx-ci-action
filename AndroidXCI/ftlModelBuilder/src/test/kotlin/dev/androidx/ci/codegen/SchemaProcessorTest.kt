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

package dev.androidx.ci.codegen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SchemaProcessorTest {
    @Test
    fun emptyClass_escapeDocs() {
        val schema = SchemaDto(
            id = "MyClass",
            type = "object",
            description = "escape me please % %T %S",
            properties = emptyMap()
        )
        val processor = SchemaProcessor(
            schemas = listOf(schema),
            pkg = "foo.bar"
        )
        val specs = processor.process().map { it.build() }
        assertThat(specs).hasSize(1)
        val code = specs.first().toString()
        assertThat(code.trimIndent()).isEqualTo(
            """
            /**
             * escape me please % %T %S
             */
            public class MyClass
            """.trimIndent()
        )
    }

    @Test
    fun enumClass() {
        val schema = SchemaDto(
            id = "MyClass",
            type = "object",
            properties = mapOf(
                "prop1" to PropertyDto(
                    type = "string",
                    enum = listOf("A", "B", "C"),
                    enumDescriptions = listOf("aa", "bb")
                )
            )
        )
        val processor = SchemaProcessor(
            schemas = listOf(schema),
            pkg = "foo.bar"
        )
        val specs = processor.process().map { it.build() }
        assertThat(specs).hasSize(1)
        val code = specs.first().toString()
        assertThat(code.trimIndent()).isEqualTo(
            """
            public data class MyClass(
              public val prop1: foo.bar.MyClass.Prop1? = null
            ) {
              public enum class Prop1 {
                /**
                 * aa
                 */
                A,
                /**
                 * bb
                 */
                B,
                C,
              }
            }
            """.trimIndent()
        )
    }

    @Test
    fun nullability() {
        val prop1 = PropertyDto(
            type = "string"
        )
        assertThat(prop1.isNullable("foo")).isTrue()
        assertThat(prop1.isNullable("id")).isFalse()

        val required = PropertyDto(
            type = "string",
            description = "Required."
        )
        assertThat(required.isNullable("foo")).isFalse()
        assertThat(required.isNullable("id")).isFalse()
    }
}
