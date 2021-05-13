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

/**
 * Maintains test matrices by also adding a caching layer over FTL to avoid re-creating test matrices for the same
 * configuration.
 */
class TestMatrixStore(
    private val firebaseProjectId: String,
    private val firestoreApi: FirestoreApi,
    private val firebaseTestLabApi: FirebaseTestLabApi,
    private val resultsGcsPrefix: GcsPath,
) {
    private val logger = logger()

    /**
     * Creates a TestMatrix for the given configuration or returns an existing one if we've run the same test with the
     * same APKs and environment configuration.
     */
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

        getExistingTestMatrix(testRunKey)?.let {
            logger.info("found existing test matrix: ${it.testMatrixId}")
            return it
        }
        val newTestMatrix = firebaseTestLabApi.createTestMatrix(
            projectId = firebaseProjectId,
            requestId = UUID.randomUUID().toString(),
            testMatrix = createNewTestMatrix(
                testRunKey = testRunKey,
                environmentMatrix = environmentMatrix,
                appApk = appApk,
                testApk = testApk
            )
        )
        logger.info {
            "created test matrix: $newTestMatrix"
        }
        // save it to cache, we don't worry about races here much such that if another instance happens to be creating
        // the exact same test, one of them will win but that is OK since they'll each use their own test matrices and
        // followup calls will re-use the winner of this race.
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

    /**
     * Returns an existing TestMatrix for the given [testRunKey].
     *
     * This happens in two steps.
     * We first check the FireStore backend to see if it already exists, if so, get the testMatrixId from there.
     * Then we query FTL to get the TestMatrix object. Notice that test matrices on FTL are subject to its own retention
     * policy hence having a record in firestore does not guarantee having it in Firebase Test Lab.
     */
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

    /**
     * Creates a new [TestMatrix] with the given configuration.
     * This [TestMatrix] is pushed to the FTL API to trigger a create.
     */
    private fun createNewTestMatrix(
        testRunKey: TestRunKey,
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
                gcsPath = (resultsGcsPrefix + testRunKey.key).path
            )
        )
    )

    /**
     * Unique key for each test run. This is used to de-dup TestMatrix requests such that we won't recreate a TestMatrix
     * if we've already run the exact same test.
     */
    private class TestRunKey private constructor(
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
                        "app" to appApk.idHash,
                        "test" to testApk.idHash
                    )
                )
                val sha = sha256(json.toByteArray(Charsets.UTF_8))
                return TestRunKey(sha)
            }
        }
    }
}
