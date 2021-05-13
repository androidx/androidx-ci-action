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

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.FirestoreApi
import dev.androidx.ci.firebase.dto.FirestoreDocument
import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.generated.ftl.AndroidInstrumentationTest
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.FileReference
import dev.androidx.ci.generated.ftl.GoogleCloudStorage
import dev.androidx.ci.generated.ftl.ResultStorage
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.ftl.TestSpecification
import dev.androidx.ci.testRunner.vo.ApkInfo
import dev.androidx.ci.testRunner.vo.UploadedApk
import dev.androidx.ci.util.sha256
import dev.zacsweers.moshix.reflect.MetadataKotlinJsonAdapterFactory
import org.apache.logging.log4j.kotlin.logger
import retrofit2.HttpException
import java.util.UUID

class TestMatrixStore(
    private val firebaseProjectId: String,
    private val firestoreApi: FirestoreApi,
    private val firebaseTestLabApi: FirebaseTestLabApi,
    private val resultsGcsPrefix: GcsPath,
) {
    private val logger = logger()

    suspend fun getOrCreateTestMatrix(
        appApk: UploadedApk,
        testApk: UploadedApk,
        environmentMatrix: EnvironmentMatrix
    ): TestMatrix {
        val testRunKey = TestRunKey.createKey(
            environment = environmentMatrix,
            appApk = appApk.apkInfo,
            testApk = testApk.apkInfo
        )

        getExistingTestMatrix(
            testRunKey
        )?.let {
            logger.info("found existing test matrix: ${it.testMatrixId}")
            return it
        }
        val newTestMatrix = firebaseTestLabApi.createTestMatrix(
            projectId = firebaseProjectId,
            requestId = UUID.randomUUID().toString(),
            testMatrix = createNewTestMatrix(
                environmentMatrix = environmentMatrix,
                appApk = appApk,
                testApk = testApk
            )
        )
        logger.info {
            "created test matrix: $newTestMatrix"
        }
        // save it to cache, we don't worry about races here much
        val doc = FirestoreDocument(
            fields = mapOf(
                "testMatrixId" to newTestMatrix.testMatrixId
            )
        )
        firestoreApi.put(
            projectId = firebaseProjectId,
            collectionId = "testMatrices",
            documentPath = testRunKey.key,
            document = doc
        ).also {
            logger.info {
                "saved test matrix info: $it"
            }
        }
        return newTestMatrix
    }

    private suspend fun getExistingTestMatrix(
        testRunKey: TestRunKey
    ): TestMatrix? {
        return try {
            val testMatrixId = firestoreApi.get(
                projectId = firebaseProjectId,
                collectionId = "testMatrices",
                documentPath = testRunKey.key
            ).fields["testMatrixId"] as? String ?: return null
            firebaseTestLabApi.getTestMatrix(
                projectId = firebaseProjectId,
                testMatrixId = testMatrixId
            )
        } catch (ex: HttpException) {
            if (ex.code() == 404) {
                null
            } else {
                throw ex
            }
        }
    }

    private fun createNewTestMatrix(
        environmentMatrix: EnvironmentMatrix,
        appApk: UploadedApk,
        testApk: UploadedApk
    ) = TestMatrix(
        projectId = firebaseProjectId,
        flakyTestAttempts = 2,
        testSpecification = TestSpecification(
            disableVideoRecording = true,
            androidInstrumentationTest = AndroidInstrumentationTest(
                appApk = FileReference(
                    gcsPath = appApk.gcsPath.path
                ),
                testApk = FileReference(
                    gcsPath = testApk.gcsPath.path
                )
            )
        ),
        environmentMatrix = environmentMatrix,
        resultStorage = ResultStorage(
            googleCloudStorage = GoogleCloudStorage(
                gcsPath = (resultsGcsPrefix + (appApk.apkInfo.idHash + "-" + testApk.apkInfo.idHash)).path
            )
        )
    )

    private class TestRunKey(
        val key: String
    ) {
        companion object {
            private val adapter by lazy {
                val type = Types.newParameterizedType(
                    Map::class.java,
                    String::class.java,
                    Any::class.java
                )
                val moshi = Moshi.Builder()
                    .add(MetadataKotlinJsonAdapterFactory())
                    .build()
                moshi.adapter<Map<String, Any>>(type)
            }
            fun createKey(
                environment: EnvironmentMatrix,
                appApk: ApkInfo,
                testApk: ApkInfo
            ): TestRunKey {
                val json = adapter.toJson(
                    mapOf(
                        "e" to environment,
                        "app" to appApk,
                        "test" to testApk
                    )
                )
                val sha = sha256(json.toByteArray(Charsets.UTF_8))
                return TestRunKey(sha)
            }
        }
    }
}
