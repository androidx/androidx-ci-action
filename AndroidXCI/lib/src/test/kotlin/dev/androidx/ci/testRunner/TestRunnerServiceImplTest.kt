package dev.androidx.ci.testRunner

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeBackend
import dev.androidx.ci.generated.ftl.ClientInfo
import dev.androidx.ci.generated.ftl.ClientInfoDetail
import dev.androidx.ci.generated.ftl.EnvironmentMatrix
import dev.androidx.ci.generated.ftl.EnvironmentVariable
import dev.androidx.ci.generated.ftl.GoogleCloudStorage
import dev.androidx.ci.generated.ftl.ResultStorage
import dev.androidx.ci.generated.ftl.ShardingOption
import dev.androidx.ci.generated.ftl.TestEnvironmentCatalog
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.ftl.TestSpecification
import dev.androidx.ci.generated.ftl.UniformSharding
import dev.androidx.ci.testRunner.TestRunnerServiceImpl.TestResultFilesImpl.DeviceIdComponents
import dev.androidx.ci.testRunner.vo.DeviceSetup
import dev.androidx.ci.util.sha256
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TestRunnerServiceImplTest {
    private val fakeBackend = FakeBackend()
    private val subject = TestRunnerServiceImpl(
        googleCloudApi = fakeBackend.fakeGoogleCloudApi,
        firebaseProjectId = fakeBackend.firebaseProjectId,
        datastoreApi = fakeBackend.datastoreApi,
        toolsResultApi = fakeBackend.fakeToolsResultApi,
        firebaseTestLabApi = fakeBackend.fakeFirebaseTestLabApi,
        gcsResultPath = "testRunnerServiceTest"
    )
    private val devicePicker = { env: TestEnvironmentCatalog ->
        listOf(FTLTestDevices.BLUELINE_API_28_PHYSICAL)
    }

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

    @Test
    fun schedule() = runBlocking<Unit> {
        val apk1Bytes = byteArrayOf(1, 2, 3, 4, 5)
        val apk1Sha = sha256(apk1Bytes)
        val upload1 = subject.getOrUploadApk(
            name = "foo.apk",
            sha256 = apk1Sha
        ) {
            apk1Bytes
        }
        val result = subject.scheduleTests(
            testApk = upload1,
            appApk = null,
            clientInfo = null,
            sharding = null,
            deviceSetup = null,
            devicePicker = devicePicker
        )
        assertThat(
            result.testMatrices
        ).hasSize(1)
        assertThat(
            result.testMatrices.single().clientInfo
        ).isNull()

        val sameRequest = subject.scheduleTests(
            testApk = upload1,
            appApk = null,
            clientInfo = null,
            sharding = null,
            deviceSetup = null,
            devicePicker = devicePicker
        )
        assertThat(
            sameRequest.testMatrices
        ).containsExactlyElementsIn(result.testMatrices)
        // change client info, it should result in new test matrices
        val clientInfo = ClientInfo(
            name = "test",
            clientInfoDetails = listOf(
                ClientInfoDetail("key", "value")
            )
        )
        val newClientInfo = subject.scheduleTests(
            testApk = upload1,
            appApk = null,
            clientInfo = clientInfo,
            sharding = null,
            deviceSetup = null,
            devicePicker = devicePicker
        )
        assertThat(
            sameRequest.testMatrices
        ).containsExactlyElementsIn(result.testMatrices)
        assertThat(
            newClientInfo.testMatrices
        ).isNotEmpty()
        assertThat(
            newClientInfo.testMatrices
        ).containsNoneIn(
            result.testMatrices
        )
        // get the test matrix, make sure it has the client info
        assertThat(
            newClientInfo.testMatrices.single().clientInfo
        ).isEqualTo(clientInfo)
        assertThat(
            subject.getTestMatrix(
                newClientInfo.testMatrices.single().testMatrixId!!
            )?.clientInfo
        ).isEqualTo(clientInfo)

        // shard
        val shardingOption = ShardingOption(
            uniformSharding = UniformSharding(3)
        )
        val shardedTest = subject.scheduleTests(
            testApk = upload1,
            appApk = null,
            clientInfo = null,
            sharding = shardingOption,
            deviceSetup = null,
            devicePicker = devicePicker
        )
        // sharding will invalidate cache as it will change the test matrix response
        // significantly. Otherwise, we'll be returning a TestMatrix with mismatched
        // sharding information
        assertThat(
            shardedTest
        ).isNotEqualTo(result.testMatrices)
        assertThat(
            subject.getTestMatrix(
                shardedTest.testMatrices.first().testMatrixId!!
            )?.testSpecification?.androidInstrumentationTest?.shardingOption
        ).isEqualTo(shardingOption)

        // put device info
        val extraApkBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val extraApkSha = sha256(extraApkBytes)
        val extraApk = subject.getOrUploadApk(
            name = "extra.apk",
            sha256 = extraApkSha
        ) {
            extraApkBytes
        }
        val withDeviceInfo = subject.scheduleTests(
            testApk = upload1,
            appApk = null,
            clientInfo = null,
            sharding = null,
            deviceSetup = DeviceSetup(
                additionalApks = setOf(extraApk),
                directoriesToPull = setOf("/sdcard/foo/bar"),
                instrumentationArguments = listOf(
                    DeviceSetup.InstrumentationArgument("key1", "value1"),
                    DeviceSetup.InstrumentationArgument("key2", "value2")
                )
            ),
            devicePicker = devicePicker
        )
        withDeviceInfo.testMatrices.single().testSpecification.testSetup!!.let { testSetup ->
            assertThat(
                testSetup.environmentVariables
            ).containsExactly(
                EnvironmentVariable(key = "key1", value = "value1"),
                EnvironmentVariable(key = "key2", value = "value2")
            )
            assertThat(
                testSetup.directoriesToPull
            ).containsExactly(
                "/sdcard/foo/bar"
            )
            assertThat(
                testSetup.additionalApks?.map {
                    it.location?.gcsPath
                }
            ).containsExactly(
                extraApk.gcsPath.path
            )
        }
    }

    @Test
    fun results() = runBlocking<Unit> {
        val resultRelativePath = "my-test-matrix-results"
        val resultPath = "${fakeBackend.fakeGoogleCloudApi.rootGcsPath}/$resultRelativePath"
        val testMatrix = fakeBackend.fakeFirebaseTestLabApi.createTestMatrix(
            projectId = fakeBackend.firebaseProjectId,
            requestId = "requestId",
            testMatrix = TestMatrix(
                resultStorage = ResultStorage(
                    googleCloudStorage = GoogleCloudStorage(resultPath)
                ),
                projectId = fakeBackend.firebaseProjectId,
                environmentMatrix = EnvironmentMatrix(),
                testSpecification = TestSpecification()
            )
        )
        val testMatrixId = testMatrix.testMatrixId!!
        assertThat(
            subject.getTestMatrix(testMatrixId)
        ).isEqualTo(
            testMatrix
        )
        // incomplete, no results
        assertThat(
            subject.getTestMatrixResults(testMatrix)
        ).isNull()
        fakeBackend.finishTest(
            testMatrixId = testMatrixId,
            outcome = TestMatrix.OutcomeSummary.SUCCESS
        )
        assertThat(
            subject.getTestMatrixResults(testMatrix)
        ).isNull()

        // put some results
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-test_results_merged.xml",
            "merged-results".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/logcat",
            "logcat".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/test_result_1.xml",
            "test_result_1 content xml".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/test_result_2.xml",
            "test_result_2 content xml".toByteArray(Charsets.UTF_8)
        )
        val results = subject.getTestMatrixResults(testMatrixId)
        assertThat(results).hasSize(1)
        val result = results!!.single()
        assertThat(
            result.deviceId
        ).isEqualTo("redfin-30-en-portrait")

        assertThat(
            result.mergedResults.readFully()
        ).isEqualTo(
            "merged-results".toByteArray(Charsets.UTF_8)
        )

        assertThat(result.testRuns).hasSize(1)
        result.testRuns.first().let { testRun ->
            assertThat(
                testRun.fullDeviceId
            ).isEqualTo(
                "redfin-30-en-portrait"
            )

            assertThat(
                testRun.logcat?.readFully()
            ).isEqualTo(
                "logcat".toByteArray(Charsets.UTF_8)
            )

            assertThat(
                testRun.xmlResults.map { it.readFully().toString(Charsets.UTF_8) }
            ).containsExactly(
                "test_result_1 content xml",
                "test_result_2 content xml",
            )
        }
    }

    @Test
    fun results_shardedAndReRun() = runBlocking<Unit> {
        val resultRelativePath = "my-test-matrix-results"
        val resultPath = "${fakeBackend.fakeGoogleCloudApi.rootGcsPath}/$resultRelativePath"
        val testMatrix = fakeBackend.fakeFirebaseTestLabApi.createTestMatrix(
            projectId = fakeBackend.firebaseProjectId,
            requestId = "requestId",
            testMatrix = TestMatrix(
                resultStorage = ResultStorage(
                    googleCloudStorage = GoogleCloudStorage(resultPath)
                ),
                projectId = fakeBackend.firebaseProjectId,
                environmentMatrix = EnvironmentMatrix(),
                testSpecification = TestSpecification()
            )
        )
        val testMatrixId = testMatrix.testMatrixId!!
        assertThat(
            subject.getTestMatrix(testMatrixId)
        ).isEqualTo(
            testMatrix
        )
        // incomplete, no results
        assertThat(
            subject.getTestMatrixResults(testMatrix)
        ).isNull()
        fakeBackend.finishTest(
            testMatrixId = testMatrixId,
            outcome = TestMatrix.OutcomeSummary.SUCCESS
        )
        assertThat(
            subject.getTestMatrixResults(testMatrix)
        ).isNull()

        // put some results
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-test_results_merged.xml",
            "merged-results".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_0/test_result_1.xml",
            "test_result_1 content xml".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_1/test_result_1.xml",
            "test_result_1 content xml".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_2-rerun_1/test_result_1.xml",
            "test_result_1 content xml".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_2/test_result_1.xml",
            "test_result_1 content xml".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_2-rerun_2/test_result_1.xml",
            "test_result_1 content xml".toByteArray(Charsets.UTF_8)
        )
        val results = subject.getTestMatrixResults(testMatrixId)
        assertThat(results).hasSize(1)
        val result = results!!.single()
        assertThat(
            result.deviceId
        ).isEqualTo("redfin-30-en-portrait")

        assertThat(
            result.mergedResults.readFully()
        ).isEqualTo(
            "merged-results".toByteArray(Charsets.UTF_8)
        )

        assertThat(result.testRuns).hasSize(5)
        assertThat(
            result.testRuns.find {
                it.runNumber == 0 && it.shard == 0
            }?.fullDeviceId
        ).isEqualTo("redfin-30-en-portrait-shard_0")
        assertThat(
            result.testRuns.find {
                it.runNumber == 0 && it.shard == 1
            }?.fullDeviceId
        ).isEqualTo("redfin-30-en-portrait-shard_1")
        assertThat(
            result.testRuns.find {
                it.runNumber == 0 && it.shard == 2
            }?.fullDeviceId
        ).isEqualTo("redfin-30-en-portrait-shard_2")
        assertThat(
            result.testRuns.find {
                it.runNumber == 1 && it.shard == 2
            }?.fullDeviceId
        ).isEqualTo("redfin-30-en-portrait-shard_2-rerun_1")
        assertThat(
            result.testRuns.find {
                it.runNumber == 2 && it.shard == 2
            }?.fullDeviceId
        ).isEqualTo("redfin-30-en-portrait-shard_2-rerun_2")
    }

    @Test
    fun parseDeviceId() {
        assertThat(
            listOf(
                "redfin-30-en-portrait",
                "redfin-30-en-portrait_rerun_1",
                "redfin-30-en-portrait-shard_0",
                "redfin-30-en-portrait-shard_20",
                "redfin-30-en-portrait-shard_2-rerun_3",
                "redfin-30-en-portrait-shard_6-rerun_2",
                "redfin-30-en-portrait-shard_3-rerun_21"
            ).associateWith {
                TestRunnerServiceImpl.TestResultFilesImpl.parseComponents(it)
            }
        ).containsExactlyEntriesIn(
            mapOf(
                "redfin-30-en-portrait" to DeviceIdComponents(
                    deviceId = "redfin-30-en-portrait"
                ),
                "redfin-30-en-portrait_rerun_1" to DeviceIdComponents(
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 1
                ),
                "redfin-30-en-portrait-shard_0" to DeviceIdComponents(
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 0,
                    shard = 0
                ),
                "redfin-30-en-portrait-shard_20" to DeviceIdComponents(
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 0,
                    shard = 20
                ),
                "redfin-30-en-portrait-shard_6-rerun_2" to DeviceIdComponents(
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 2,
                    shard = 6
                ),
                "redfin-30-en-portrait-shard_2-rerun_3" to DeviceIdComponents(
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 3,
                    shard = 2
                ),
                "redfin-30-en-portrait-shard_3-rerun_21" to DeviceIdComponents(
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 21,
                    shard = 3
                )
            )
        )
    }

    private fun TestRunnerService.ResultFileResource.readFully() = openInputStream().use {
        it.readAllBytes()
    }
}
