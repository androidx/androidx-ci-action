/*
 * Copyright 2024 The Android Open Source Project
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
 * Indicates which device testing backend to use when scheduling tests.
 */
enum class TestBackend {
    FTL,
    DDP,

    /**
     * Runs the tests on both FTL and DDP. Each DDP job is labeled with the FTL test matrix that
     * runs on the same device, so that their results can be compared.
     */
    BOTH
}
