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

package dev.androidx.ci.firebase

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeFirebaseTestLabApi
import dev.androidx.ci.firebase.dto.EnvironmentType
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.FileReference
import dev.androidx.ci.generated.ftl.GoogleCloudStorage
import dev.androidx.ci.generated.ftl.ResultStorage
import dev.androidx.ci.generated.ftl.TestEnvironmentCatalog
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.ftl.TestSpecification
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import retrofit2.HttpException

@RunWith(JUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
internal class FakeFirebaseTestLabApiTest {
    private val fakeApi = FakeFirebaseTestLabApi()

    @Test
    fun addMatrix() = runTest {
        val matrix = TestMatrix(
            resultStorage = ResultStorage(
                googleCloudStorage = GoogleCloudStorage("gs://fake-path")
            ),
            projectId = "myProject",
            environmentMatrix = EnvironmentMatrix(),
            testSpecification = TestSpecification()
        )
        val result = fakeApi.createTestMatrix(
            projectId = "myProject",
            requestId = "requestId",
            testMatrix = matrix
        )

        assertThat(result.testMatrixId).isNotNull()
        assertThat(result.state).isEqualTo(
            TestMatrix.State.PENDING
        )
        // now update state
        val updated = result.copy(
            state = TestMatrix.State.FINISHED
        )
        fakeApi.setTestMatrix(updated)
        // get it back
        assertThat(
            fakeApi.getTestMatrix(
                projectId = "myProject",
                testMatrixId = result.testMatrixId!!
            )

        ).isEqualTo(updated)
    }

    @Test
    fun getNonExistingMatrix() = runTest {
        val result = kotlin.runCatching {
            fakeApi.getTestMatrix(
                projectId = "none",
                testMatrixId = "foo"
            )
        }
        assertThat(result.exceptionOrNull()).isInstanceOf(HttpException::class.java)
        assertThat(
            (result.exceptionOrNull() as HttpException).code()
        ).isEqualTo(404)
    }

    @Test
    fun getRealEnvCatalog() = runTest {
        val catalog = fakeApi.getTestEnvironmentCatalog(
            environmentType = EnvironmentType.ANDROID,
            projectId = "foo"
        )

        assertThat(catalog.androidDeviceCatalog?.models).isNotEmpty()
    }

    @Test
    fun setEnvironmentCatalog() = runTest {
        val catalog = TestEnvironmentCatalog()
        fakeApi.setTestEnvironmentCatalog(catalog)
        assertThat(
            fakeApi.getTestEnvironmentCatalog(
                environmentType = EnvironmentType.ENVIRONMENT_TYPE_UNSPECIFIED,
                projectId = "none"
            )

        ).isEqualTo(catalog)
    }

    @Test
    fun getApkDetails() = runTest {
        val fileRef1 = FileReference(
            gcsPath = "gs://foo/bar.apk"
        )
        val fileRef2 = FileReference(
            gcsPath = "gs://foo/baz.apk"
        )
        val pkg1 = fakeApi.getApkDetails(
            fileRef1
        ).apkDetail?.apkManifest?.packageName
        assertThat(pkg1).isEqualTo("androidx.fake.pkg0")
        val pkg2 = fakeApi.getApkDetails(
            fileRef2
        ).apkDetail?.apkManifest?.packageName
        assertThat(pkg2).isEqualTo("androidx.fake.pkg1")
        val pkg1Dupe = fakeApi.getApkDetails(
            FileReference(
                gcsPath = fileRef1.gcsPath
            )
        ).apkDetail?.apkManifest?.packageName
        assertThat(pkg1Dupe).isEqualTo(pkg1)
    }
}
