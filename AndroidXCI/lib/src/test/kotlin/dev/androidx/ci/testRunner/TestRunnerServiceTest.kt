package dev.androidx.ci.testRunner

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeBackend
import dev.androidx.ci.util.sha256
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TestRunnerServiceTest {
    private val fakeBackend = FakeBackend()
    private val subject = TestRunnerService(
        googleCloudApi = fakeBackend.fakeGoogleCloudApi,
        firebaseProjectId = fakeBackend.firebaseProjectId,
        datastoreApi = fakeBackend.datastoreApi,
        toolsResultApi = fakeBackend.fakeToolsResultApi,
        firebaseTestLabApi = fakeBackend.fakeFirebaseTestLabApi,
        gcsResultPath = "testRunnerServiceTest"
    )

    @Test
    fun uploadApk() = runBlocking<Unit> {
        val apk1Bytes = byteArrayOf(1, 2, 3, 4, 5)
        val apk1Sha = sha256(apk1Bytes)
        val upload1 = subject.getOrUploadApk(
            name = "foo.apk",
            sha256 = apk1Sha
        ) {
            apk1Bytes
        }
        assertThat(
            fakeBackend.fakeGoogleCloudApi.uploadCount
        ).isEqualTo(1)
        // re-upload
        val upload2 = subject.getOrUploadApk(
            name = "foo.apk",
            sha256 = apk1Sha
        ) {
            error("shouldn't query bytes")
        }
        assertThat(
            upload1
        ).isEqualTo(upload2)
        assertThat(
            fakeBackend.fakeGoogleCloudApi.uploadCount
        ).isEqualTo(1)
    }
}