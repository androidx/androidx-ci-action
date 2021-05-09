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

package dev.androidx.ci.fake

import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.dto.EnvironmentType
import dev.androidx.ci.generated.ftl.TestEnvironmentCatalog
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.util.MOSHI
import okio.buffer
import okio.source
import java.util.UUID

class FakeFirebaseTestLabApi(
    private val onTestCreated: ((FakeFirebaseTestLabApi, TestMatrix) -> Unit)? = null
) : FirebaseTestLabApi {
    private val testMatrices = mutableMapOf<String, TestMatrix>()
    private var environmentCatalog: TestEnvironmentCatalog? = null

    private val realEnvironmentCatalog by lazy {
        FakeFirebaseTestLabApi::class.java.classLoader
            .getResourceAsStream("env_catalog.json")!!.use {
            MOSHI.adapter(TestEnvironmentCatalog::class.java).lenient()
                .fromJson(it.source().buffer())!!
        }
    }

    fun setTestEnvironmentCatalog(
        environmentCatalog: TestEnvironmentCatalog
    ) {
        this.environmentCatalog = environmentCatalog
    }

    fun setTestMatrix(
        testMatrix: TestMatrix
    ) {
        check(testMatrix.testMatrixId != null)
        testMatrices[testMatrix.testMatrixId!!] = testMatrix
    }

    fun getTestMatrices() = testMatrices.values.toList()

    override suspend fun getTestMatrix(projectId: String, testMatrixId: String): TestMatrix {
        val testMatrix = testMatrices[testMatrixId] ?: throwNotFound<TestMatrix>()
        check(testMatrix.projectId == projectId)
        return testMatrix
    }

    override suspend fun createTestMatrix(
        projectId: String,
        requestId: String,
        testMatrix: TestMatrix
    ): TestMatrix {
        val id = UUID.randomUUID().toString()
        check(projectId == testMatrix.projectId)
        testMatrices[id] = testMatrix.copy(
            testMatrixId = id,
            state = TestMatrix.State.PENDING
        )
        onTestCreated?.invoke(this, testMatrices[id]!!)
        return testMatrices[id]!!
    }

    override suspend fun getTestEnvironmentCatalog(
        environmentType: EnvironmentType,
        projectId: String
    ): TestEnvironmentCatalog {
        return environmentCatalog ?: realEnvironmentCatalog
    }
}
