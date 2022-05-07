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

package dev.androidx.ci.cli

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceSpecTest {
    @Test
    fun parseSingle() {
        assertThat(
            DeviceSpec.parseSpecs("foo:23")
        ).containsExactly(
            DeviceSpec("foo", "23")
        )
    }

    @Test
    fun parseMultiple() {
        assertThat(
            DeviceSpec.parseSpecs("foo:23, bar:22")
        ).containsExactly(
            DeviceSpec("foo", "23"),
            DeviceSpec("bar", "22")
        )
    }

    @Test(
        expected = IllegalArgumentException::class
    )
    fun parseEmpty() {
        assertThat(
            DeviceSpec.parseSpecs("")
        ).isEmpty()
    }

    @Test(
        expected = IllegalArgumentException::class
    )
    fun invalidSpec_noSdk() {
        assertThat(
            DeviceSpec.parseSpecs("a:")
        ).isEmpty()
    }

    @Test(
        expected = IllegalArgumentException::class
    )
    fun invalidSpec_noId() {
        assertThat(
            DeviceSpec.parseSpecs(":")
        ).isEmpty()
    }
}
