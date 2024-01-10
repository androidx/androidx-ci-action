package dev.androidx.ci.testRunner

import com.google.common.truth.Truth.assertThat
import dev.androidx.ci.fake.FakeBackend
import dev.androidx.ci.gcloud.GcsPath
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
import dev.androidx.ci.generated.ftl.ToolResultsExecution
import dev.androidx.ci.generated.ftl.UniformSharding
import dev.androidx.ci.generated.testResults.FileReference
import dev.androidx.ci.generated.testResults.Step
import dev.androidx.ci.generated.testResults.TestCaseReference
import dev.androidx.ci.generated.testResults.TestExecutionStep
import dev.androidx.ci.generated.testResults.TestIssue
import dev.androidx.ci.generated.testResults.ToolExecution
import dev.androidx.ci.generated.testResults.ToolOutputReference
import dev.androidx.ci.testRunner.vo.DeviceSetup
import dev.androidx.ci.util.sha256
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.UUID

class TestRunnerServiceImplTest {
    private val fakeBackend = FakeBackend()
    private val fakeToolsResultApi = fakeBackend.fakeToolsResultApi
    private val subject = TestRunnerServiceImpl(
        googleCloudApi = fakeBackend.fakeGoogleCloudApi,
        firebaseProjectId = fakeBackend.firebaseProjectId,
        datastoreApi = fakeBackend.datastoreApi,
        toolsResultApi = fakeToolsResultApi,
        firebaseTestLabApi = fakeBackend.fakeFirebaseTestLabApi,
        gcsResultPath = "testRunnerServiceTest"
    )
    private val devicePicker = { _: TestEnvironmentCatalog ->
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
    fun uploadRemoteApk() = runBlocking<Unit> {
        // upload the source file
        val apkBytes = byteArrayOf(1, 2, 3, 4, 5)
        val apkSha = sha256(apkBytes)
        val upload1 = subject.getOrUploadApk(
            name = "foo.apk",
            sha256 = apkSha
        ) {
            apkBytes
        }
        assertThat(
            fakeBackend.fakeGoogleCloudApi.uploadCount
        ).isEqualTo(1)
        // copy
        subject.getOrUploadRemoteApk(upload1.gcsPath, "/copy/foo.apk")
        assertThat(
            fakeBackend.fakeGoogleCloudApi.uploadCount
        ).isEqualTo(2)
        // attempt to copy again
        subject.getOrUploadRemoteApk(upload1.gcsPath, "/copy/foo.apk")
        // found the apk - no copying needed
        assertThat(
            fakeBackend.fakeGoogleCloudApi.uploadCount
        ).isEqualTo(2)
        // copy to a different path
        subject.getOrUploadRemoteApk(upload1.gcsPath, "/copy_new/foo.apk")
        assertThat(
            fakeBackend.fakeGoogleCloudApi.uploadCount
        ).isEqualTo(3)
    }

    @Test
    fun getBlob() = runBlocking {
        // load some data into the backend
        val resultRelativePath = "my-test-matrix-results"
        val resultPath = "${fakeBackend.fakeGoogleCloudApi.rootGcsPath}/$resultRelativePath"
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/test1",
            "test1".toByteArray(Charsets.UTF_8)
        )

        // verify file existing in backend is returned
        val test1 = fakeBackend.fakeGoogleCloudApi.getBlob(
            GcsPath("$resultPath/test1")
        )
        assertThat(test1).isNotNull()
        assertThat(test1?.gcsPath?.path).isEqualTo("$resultPath/test1")
        assertThat(
            subject.getResultFileResource(
                GcsPath("$resultPath/test1")
            )?.readFully()
        ).isNotEmpty()

        assertThat(
            subject.getResultFileResource(
                GcsPath("$resultPath/test1")
            )?.readFully()
        ).isEqualTo("test1".toByteArray(Charsets.UTF_8))

        // no blob should be returned for a folder
        val test2 = fakeBackend.fakeGoogleCloudApi.getBlob(
            GcsPath(resultPath)
        )
        assertThat(test2).isNull()
        assertThat(
            subject.getResultFileResource(
                GcsPath(resultPath)
            )?.readFully()
        ).isNull()

        // no blob should be returned for files that don't exist in backend
        val test3 = fakeBackend.fakeGoogleCloudApi.getBlob(
            GcsPath("$resultPath/test3")
        )
        assertThat(test3).isNull()
        assertThat(
            subject.getResultFileResource(
                GcsPath("$resultPath/test3")
            )?.readFully()
        ).isNull()
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
        val testPackageName = fakeBackend.fakeFirebaseTestLabApi.getApkDetails(
            dev.androidx.ci.generated.ftl.FileReference(upload1.gcsPath.path)
        ).apkDetail?.apkManifest?.packageName
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
        // reject apk, should be a new one
        val noReuse = subject.scheduleTests(
            testApk = upload1,
            appApk = null,
            clientInfo = null,
            sharding = null,
            deviceSetup = null,
            devicePicker = devicePicker,
            cachedTestMatrixFilter = { false }
        )
        assertThat(
            noReuse.testMatrices
        ).containsNoneIn(result.testMatrices)

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
                additionalApks = listOf(extraApk),
                directoriesToPull = mutableListOf("/sdcard/foo/bar"),
                instrumentationArguments = listOf(
                    DeviceSetup.InstrumentationArgument("key1", "value1"),
                    DeviceSetup.InstrumentationArgument("key2", "value2")
                )
            ),
            devicePicker = devicePicker,
            pullScreenshots = true
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
            ).contains(
                "/sdcard/foo/bar"
            )
            assertThat(
                testSetup.directoriesToPull
            ).contains(
                "/sdcard/Android/data/$testPackageName/cache/androidx_screenshots"
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
    fun scheduleSpecificTests() = runBlocking<Unit> {
        // create a test Matrix
        val testApk = subject.getOrUploadApk(
            name = "foo.apk",
            sha256 = sha256(
                byteArrayOf(1, 2, 3, 4, 5)
            )
        ) {
            byteArrayOf(1, 2, 3, 4, 5)
        }
        val result = subject.scheduleTests(
            testApk = testApk,
            appApk = null,
            clientInfo = null,
            sharding = null,
            deviceSetup = null,
            devicePicker = devicePicker
        )
        assertThat(
            result.testMatrices
        ).hasSize(1)

        val testMatrix = result.testMatrices.first()

        fakeBackend.finishTest(
            testMatrixId = testMatrix.testMatrixId!!,
            outcome = TestMatrix.OutcomeSummary.FAILURE
        )

        val retryTests = listOf("test1", "test2")
        val newTestMatrix = subject.scheduleTests(
            testMatrix = testMatrix,
            testTargets = retryTests
        )
        assertThat(
            newTestMatrix
        ).isNotNull()

        assertThat(
            newTestMatrix.testSpecification.androidInstrumentationTest?.testTargets
        ).containsExactlyElementsIn(
            retryTests
        )

        assertThat(
            newTestMatrix.clientInfo
        ).isEqualTo(
            testMatrix.clientInfo
        )

        assertThat(
            newTestMatrix.environmentMatrix
        ).isEqualTo(
            testMatrix.environmentMatrix
        )

        // storage location must be different for both test matrices, otherwise results will clash
        assertThat(
            newTestMatrix.resultStorage
        ).isNotEqualTo(
            testMatrix.resultStorage
        )

        // retry same tests again, new testMatrix shouldn't be created
        // instead it should be feteched from cache
        val retryTestMatrix = subject.scheduleTests(
            testMatrix = testMatrix,
            testTargets = retryTests
        )

        assertThat(
            retryTestMatrix
        ).isEqualTo(
            newTestMatrix
        )
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
                    googleCloudStorage = GoogleCloudStorage(resultPath),
                    toolResultsExecution = ToolResultsExecution(
                        executionId = "test_executionId",
                        historyId = "test_historyId"
                    )
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
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/test_cases/0000_logcat",
            "test1 logcat".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/test_cases/0001_logcat",
            "test2 logcat".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_actual.png",
            "class1 name1 emulator actual".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_diff.png",
            "class1 name1 emulator diff".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_expected.png",
            "class1 name1 emulator expected".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_goldResult.textproto",
            "class1 name1 emulator textproto".toByteArray(Charsets.UTF_8)
        )
        // No test is associated with this artifact. findArtifacts should not throw errors, even when unexpected files are encountered
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class5_name5_emulator_goldResult.textproto",
            "class5 name5 emulator textproto".toByteArray(Charsets.UTF_8)
        )

        fakeToolsResultApi.addStep(
            projectId = fakeBackend.firebaseProjectId,
            executionId = "test_executionId",
            historyId = "test_historyId",
            step = Step(
                stepId = UUID.randomUUID().toString(),
                testExecutionStep = TestExecutionStep(
                    toolExecution = ToolExecution(
                        toolOutputs = listOf(
                            ToolOutputReference(
                                testCase = TestCaseReference(
                                    className = "class1",
                                    name = "name1"
                                ),
                                output = FileReference(
                                    fileUri = "$resultPath/redfin-30-en-portrait/test_cases/0000_logcat"
                                )
                            ),
                            ToolOutputReference(
                                testCase = TestCaseReference(
                                    className = "class3",
                                    name = "name3"
                                ),
                                output = FileReference(
                                    fileUri = "$resultPath/redfin-30-en-portrait/test_cases/0002_logcat"
                                )
                            )
                        )
                    )
                )
            )
        )
        val results = subject.getTestMatrixResults(testMatrixId)
        assertThat(results).hasSize(1)
        val result = results!!.single()
        assertThat(
            result.deviceId
        ).isEqualTo("redfin-30-en-portrait")

        assertThat(
            result.mergedResults?.readFully()
        ).isEqualTo(
            "merged-results".toByteArray(Charsets.UTF_8)
        )

        assertThat(result.testRuns).hasSize(1)
        result.testRuns.first().let { testRun ->
            val testIdentifier1 = TestRunnerService.TestIdentifier(
                className = "class1",
                name = "name1",
                runNumber = testRun.deviceRun.runNumber
            )
            val screenshots1 = subject.getTestMatrixArtifacts(
                testMatrixId,
                listOf(testIdentifier1)
            )
            assertThat(
                testRun.deviceRun.deviceId
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

            assertThat(
                testRun.testCaseArtifacts.size
            ).isEqualTo(
                1
            )
            assertThat(
                testRun.testCaseArtifacts[
                    testIdentifier1
                ]?.size
            ).isEqualTo(
                1
            )
            // step and logcat both have valid values for test1
            assertThat(
                testRun.testCaseArtifacts[
                    testIdentifier1
                ]?.first {
                    it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.LOGCAT
                }?.resultFileResource?.gcsPath.toString()
            ).isEqualTo(
                "$resultPath/redfin-30-en-portrait/test_cases/0000_logcat"
            )
            assertThat(
                testRun.testCaseArtifacts[
                    testIdentifier1
                ]?.first {
                    it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.LOGCAT
                }?.resultFileResource?.readFully()?.toString(Charsets.UTF_8)
            ).isEqualTo(
                "test1 logcat"
            )
            assertThat(
                screenshots1?.get(testIdentifier1)?.count {
                    it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.PNG
                }
            ).isEqualTo(
                3
            )
            assertThat(
                screenshots1?.get(testIdentifier1)?.count {
                    it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.TEXTPROTO
                }
            ).isEqualTo(
                1
            )
            assertThat(
                screenshots1?.get(testIdentifier1)?.first {
                    it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.TEXTPROTO
                }?.resultFileResource?.gcsPath.toString()
            ).isEqualTo(
                "$resultPath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_goldResult.textproto"
            )

            // step for test2 is missing
            assertThat(
                testRun.testCaseArtifacts[
                    TestRunnerService.TestIdentifier(
                        className = "class2",
                        name = "name2",
                        runNumber = testRun.deviceRun.runNumber
                    )
                ]
            ).isNull()
            // No screenshots for test2
            val testIdentifier2 = TestRunnerService.TestIdentifier(
                className = "class2",
                name = "name2",
                runNumber = testRun.deviceRun.runNumber
            )
            val screenshots2 = subject.getTestMatrixArtifacts(
                testMatrixId,
                listOf(testIdentifier2)
            )
            assertThat(
                screenshots2
            ).isEmpty()
            // logcat for test3 is missing from gcloud folder
            assertThat(
                testRun.testCaseArtifacts[
                    TestRunnerService.TestIdentifier(
                        className = "class3",
                        name = "name3",
                        runNumber = testRun.deviceRun.runNumber
                    )
                ]
            ).isNull()
            // No screenshots for test3 either
            val testIdentifier3 = TestRunnerService.TestIdentifier(
                className = "class2",
                name = "name2",
                runNumber = testRun.deviceRun.runNumber
            )
            val screenshots3 = subject.getTestMatrixArtifacts(
                testMatrixId,
                listOf(testIdentifier3)
            )
            assertThat(
                screenshots3
            ).isEmpty()
        }
        // check screenshots is null when list of testIdentifiers is empty
        val screenshots = subject.getTestMatrixArtifacts(
            testMatrixId,
            emptyList()
        )
        assertThat(
            screenshots
        ).isNull()
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
                    googleCloudStorage = GoogleCloudStorage(resultPath),
                    toolResultsExecution = ToolResultsExecution(
                        executionId = "test_executionId",
                        historyId = "test_historyId"
                    )
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
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_0/test_cases/0000_logcat",
            "test1 in shard0 logcat".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_1/test_cases/0000_logcat",
            "test2 in shard1 logcat".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_2/test_cases/0000_logcat",
            "test3 in shard2 logcat".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_2-rerun_1/test_cases/0000_logcat",
            "test3 in shard2 rerun1 logcat".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_2-rerun_2/test_cases/0000_logcat",
            "test3 in shard2 rerun2 logcat".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_0/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_actual.png",
            "class1 name1 emulator actual".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_0/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_diff.png",
            "class1 name1 emulator diff".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_0/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_expected.png",
            "class1 name1 emulator expected".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait-shard_0/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_goldResult.textproto",
            "class1 name1 emulator textproto".toByteArray(Charsets.UTF_8)
        )
        // every shard and rerun has its own step
        fakeToolsResultApi.addStep(
            projectId = fakeBackend.firebaseProjectId,
            executionId = "test_executionId",
            historyId = "test_historyId",
            step = Step(
                stepId = UUID.randomUUID().toString(),
                testExecutionStep = TestExecutionStep(
                    toolExecution = ToolExecution(
                        toolOutputs = listOf(
                            ToolOutputReference(
                                testCase = TestCaseReference(
                                    className = "class1",
                                    name = "name1"
                                ),
                                output = FileReference(
                                    fileUri = "$resultPath/redfin-30-en-portrait-shard_0/test_cases/0000_logcat"
                                )
                            )
                        )
                    )
                )
            )
        )
        fakeToolsResultApi.addStep(
            projectId = fakeBackend.firebaseProjectId,
            executionId = "test_executionId",
            historyId = "test_historyId",
            step = Step(
                stepId = UUID.randomUUID().toString(),
                testExecutionStep = TestExecutionStep(
                    toolExecution = ToolExecution(
                        toolOutputs = listOf(
                            ToolOutputReference(
                                testCase = TestCaseReference(
                                    className = "class1",
                                    name = "name1"
                                ),
                                output = FileReference(
                                    fileUri = "$resultPath/redfin-30-en-portrait-shard_1/test_cases/0000_logcat"
                                )
                            )
                        )
                    )
                )
            )
        )
        fakeToolsResultApi.addStep(
            projectId = fakeBackend.firebaseProjectId,
            executionId = "test_executionId",
            historyId = "test_historyId",
            step = Step(
                stepId = UUID.randomUUID().toString(),
                testExecutionStep = TestExecutionStep(
                    toolExecution = ToolExecution(
                        toolOutputs = listOf(
                            ToolOutputReference(
                                testCase = TestCaseReference(
                                    className = "class1",
                                    name = "name1"
                                ),
                                output = FileReference(
                                    fileUri = "$resultPath/redfin-30-en-portrait-shard_2/test_cases/0000_logcat"
                                )
                            )
                        )
                    )
                )
            )
        )
        fakeToolsResultApi.addStep(
            projectId = fakeBackend.firebaseProjectId,
            executionId = "test_executionId",
            historyId = "test_historyId",
            step = Step(
                stepId = UUID.randomUUID().toString(),
                testExecutionStep = TestExecutionStep(
                    toolExecution = ToolExecution(
                        toolOutputs = listOf(
                            ToolOutputReference(
                                testCase = TestCaseReference(
                                    className = "class1",
                                    name = "name1"
                                ),
                                output = FileReference(
                                    fileUri = "$resultPath/redfin-30-en-portrait-shard_2-rerun_1/test_cases/0000_logcat"
                                )
                            )
                        )
                    )
                )
            )
        )
        fakeToolsResultApi.addStep(
            projectId = fakeBackend.firebaseProjectId,
            executionId = "test_executionId",
            historyId = "test_historyId",
            step = Step(
                stepId = UUID.randomUUID().toString(),
                testExecutionStep = TestExecutionStep(
                    toolExecution = ToolExecution(
                        toolOutputs = listOf(
                            ToolOutputReference(
                                testCase = TestCaseReference(
                                    className = "class1",
                                    name = "name1"
                                ),
                                output = FileReference(
                                    fileUri = "$resultPath/redfin-30-en-portrait-shard_2-rerun_2/test_cases/0000_logcat"
                                )
                            )
                        )
                    )
                )
            )
        )
        val results = subject.getTestMatrixResults(testMatrixId)
        assertThat(results).hasSize(1)
        val result = results!!.single()
        assertThat(
            result.deviceId
        ).isEqualTo("redfin-30-en-portrait")

        assertThat(
            result.mergedResults?.readFully()
        ).isEqualTo(
            "merged-results".toByteArray(Charsets.UTF_8)
        )

        assertThat(result.testRuns).hasSize(5)
        assertThat(
            result.testRuns.map { it.deviceRun }
        ).containsExactly(
            DeviceRun(
                id = "redfin-30-en-portrait-shard_0",
                deviceId = "redfin-30-en-portrait",
                runNumber = 0,
                shard = 0
            ),
            DeviceRun(
                id = "redfin-30-en-portrait-shard_1",
                deviceId = "redfin-30-en-portrait",
                runNumber = 0,
                shard = 1
            ),
            DeviceRun(
                id = "redfin-30-en-portrait-shard_2",
                deviceId = "redfin-30-en-portrait",
                runNumber = 0,
                shard = 2
            ),
            DeviceRun(
                id = "redfin-30-en-portrait-shard_2-rerun_1",
                deviceId = "redfin-30-en-portrait",
                runNumber = 1,
                shard = 2
            ),
            DeviceRun(
                id = "redfin-30-en-portrait-shard_2-rerun_2",
                deviceId = "redfin-30-en-portrait",
                runNumber = 2,
                shard = 2
            ),
        )
        result.testRuns.forEach { testRun ->
            assertThat(
                testRun.testCaseArtifacts.size
            ).isEqualTo(
                1
            )
            assertThat(
                testRun.testCaseArtifacts[
                    TestRunnerService.TestIdentifier(
                        className = "class1",
                        name = "name1",
                        runNumber = testRun.deviceRun.runNumber
                    )
                ]?.first {
                    it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.LOGCAT
                }?.resultFileResource?.gcsPath.toString()
            ).isEqualTo(
                "$resultPath/${testRun.deviceRun.id}/test_cases/0000_logcat"
            )
        }

        val testIdentifier = TestRunnerService.TestIdentifier(
            "class1",
            "name1",
            0
        )
        val screenshots = subject.getTestMatrixArtifacts(
            testMatrixId,
            listOf(testIdentifier)
        )
        assertThat(
            result.testRuns[0].testCaseArtifacts[
                TestRunnerService.TestIdentifier(
                    className = "class1",
                    name = "name1",
                    runNumber = 0
                )
            ]?.first {
                it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.LOGCAT
            }?.resultFileResource?.readFully()?.toString(Charsets.UTF_8)
        ).isEqualTo(
            "test1 in shard0 logcat"
        )
        assertThat(
            screenshots?.get(testIdentifier)?.count {
                it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.PNG
            }
        ).isEqualTo(
            3
        )
        assertThat(
            screenshots?.get(testIdentifier)?.count {
                it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.TEXTPROTO
            }
        ).isEqualTo(
            1
        )
        assertThat(
            screenshots?.get(testIdentifier)?.first {
                it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.TEXTPROTO
            }?.resultFileResource?.gcsPath.toString()
        ).isEqualTo(
            "$resultPath/redfin-30-en-portrait-shard_0/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_goldResult.textproto"
        )
        assertThat(
            result.testRuns[1].testCaseArtifacts[
                TestRunnerService.TestIdentifier(
                    className = "class1",
                    name = "name1",
                    runNumber = 0
                )
            ]?.first {
                it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.LOGCAT
            }?.resultFileResource?.readFully()?.toString(Charsets.UTF_8)
        ).isEqualTo(
            "test2 in shard1 logcat"
        )
        assertThat(
            result.testRuns[2].testCaseArtifacts[
                TestRunnerService.TestIdentifier(
                    className = "class1",
                    name = "name1",
                    runNumber = 0
                )
            ]?.first {
                it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.LOGCAT
            }?.resultFileResource?.readFully()?.toString(Charsets.UTF_8)
        ).isEqualTo(
            "test3 in shard2 logcat"
        )
        assertThat(
            result.testRuns[3].testCaseArtifacts[
                TestRunnerService.TestIdentifier(
                    className = "class1",
                    name = "name1",
                    runNumber = 1
                )
            ]?.first {
                it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.LOGCAT
            }?.resultFileResource?.readFully()?.toString(Charsets.UTF_8)
        ).isEqualTo(
            "test3 in shard2 rerun1 logcat"
        )
        assertThat(
            result.testRuns[4].testCaseArtifacts[
                TestRunnerService.TestIdentifier(
                    className = "class1",
                    name = "name1",
                    runNumber = 2
                )
            ]?.first {
                it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.LOGCAT
            }?.resultFileResource?.readFully()?.toString(Charsets.UTF_8)
        ).isEqualTo(
            "test3 in shard2 rerun2 logcat"
        )
    }

    @Test
    fun emptyResults() = runBlocking {
        val resultRelativePath = "my-test-matrix-results"
        val resultPath = "${fakeBackend.fakeGoogleCloudApi.rootGcsPath}/$resultRelativePath"
        val testMatrix = fakeBackend.fakeFirebaseTestLabApi.createTestMatrix(
            projectId = fakeBackend.firebaseProjectId,
            requestId = "requestId",
            testMatrix = TestMatrix(
                resultStorage = ResultStorage(
                    googleCloudStorage = GoogleCloudStorage(resultPath),
                    toolResultsExecution = ToolResultsExecution(
                        executionId = "test_executionId",
                        historyId = "test_historyId"
                    )
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

        val results = subject.getTestMatrixResults(testMatrixId)
        assertThat(results).isEmpty()
        // check screenshots is null when list of testIdentifiers is empty
        val screenshots = subject.getTestMatrixArtifacts(
            testMatrixId,
            emptyList()
        )
        assertThat(
            screenshots
        ).isNull()
    }

    @Test
    fun mergedResultsMissing() = runBlocking {
        val resultRelativePath = "my-test-matrix-results"
        val resultPath = "${fakeBackend.fakeGoogleCloudApi.rootGcsPath}/$resultRelativePath"
        val testMatrix = fakeBackend.fakeFirebaseTestLabApi.createTestMatrix(
            projectId = fakeBackend.firebaseProjectId,
            requestId = "requestId",
            testMatrix = TestMatrix(
                resultStorage = ResultStorage(
                    googleCloudStorage = GoogleCloudStorage(resultPath),
                    toolResultsExecution = ToolResultsExecution(
                        executionId = "test_executionId",
                        historyId = "test_historyId"
                    )
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

        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/logcat",
            "logcat".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/test_result_1.xml",
            "test_result_1 content xml".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/test_cases/0000_logcat",
            "test1 logcat".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/test_cases/0001_logcat",
            "test2 logcat".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_actual.png",
            "class1 name1 emulator actual".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_diff.png",
            "class1 name1 emulator diff".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_expected.png",
            "class1 name1 emulator expected".toByteArray(Charsets.UTF_8)
        )
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_goldResult.textproto",
            "class1 name1 emulator textproto".toByteArray(Charsets.UTF_8)
        )
        // No test is associated with this artifact. findArtifacts should not throw errors, even when unexpected files are encountered
        fakeBackend.fakeGoogleCloudApi.upload(
            "$resultRelativePath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class5_name5_emulator_goldResult.textproto",
            "class5 name5 emulator textproto".toByteArray(Charsets.UTF_8)
        )

        fakeToolsResultApi.addStep(
            projectId = fakeBackend.firebaseProjectId,
            executionId = "test_executionId",
            historyId = "test_historyId",
            step = Step(
                stepId = UUID.randomUUID().toString(),
                testExecutionStep = TestExecutionStep(
                    toolExecution = ToolExecution(
                        toolOutputs = listOf(
                            ToolOutputReference(
                                testCase = TestCaseReference(
                                    className = "class1",
                                    name = "name1"
                                ),
                                output = FileReference(
                                    fileUri = "$resultPath/redfin-30-en-portrait/test_cases/0000_logcat"
                                )
                            ),
                            ToolOutputReference(
                                testCase = TestCaseReference(
                                    className = "class3",
                                    name = "name3"
                                ),
                                output = FileReference(
                                    fileUri = "$resultPath/redfin-30-en-portrait/test_cases/0002_logcat"
                                )
                            )
                        )
                    )
                )
            )
        )
        val results = subject.getTestMatrixResults(testMatrixId)
        assertThat(results).hasSize(1)
        val result = results!!.single()
        assertThat(
            result.deviceId
        ).isEqualTo("redfin-30-en-portrait")

        assertThat(
            result.mergedResults
        ).isNull()

        assertThat(result.testRuns).hasSize(1)
        result.testRuns.first().let { testRun ->
            val testIdentifier1 = TestRunnerService.TestIdentifier(
                className = "class1",
                name = "name1",
                runNumber = testRun.deviceRun.runNumber
            )
            val screenshots1 = subject.getTestMatrixArtifacts(
                testMatrixId,
                listOf(testIdentifier1)
            )
            assertThat(
                testRun.deviceRun.deviceId
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
                "test_result_1 content xml"
            )

            assertThat(
                testRun.testCaseArtifacts.size
            ).isEqualTo(
                1
            )
            assertThat(
                testRun.testCaseArtifacts[
                    testIdentifier1
                ]?.size
            ).isEqualTo(
                1
            )
            // step and logcat both have valid values for test1
            assertThat(
                testRun.testCaseArtifacts[
                    testIdentifier1
                ]?.first {
                    it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.LOGCAT
                }?.resultFileResource?.gcsPath.toString()
            ).isEqualTo(
                "$resultPath/redfin-30-en-portrait/test_cases/0000_logcat"
            )
            assertThat(
                testRun.testCaseArtifacts[
                    testIdentifier1
                ]?.first {
                    it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.LOGCAT
                }?.resultFileResource?.readFully()?.toString(Charsets.UTF_8)
            ).isEqualTo(
                "test1 logcat"
            )
            assertThat(
                screenshots1?.get(testIdentifier1)?.count {
                    it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.PNG
                }
            ).isEqualTo(
                3
            )
            assertThat(
                screenshots1?.get(testIdentifier1)?.count {
                    it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.TEXTPROTO
                }
            ).isEqualTo(
                1
            )
            assertThat(
                screenshots1?.get(testIdentifier1)?.first {
                    it.resourceType == TestRunnerService.TestCaseArtifact.ResourceType.TEXTPROTO
                }?.resultFileResource?.gcsPath.toString()
            ).isEqualTo(
                "$resultPath/redfin-30-en-portrait/artifacts/sdcard/Android/data/test/cache/androidx_screenshots/class1_name1_emulator_goldResult.textproto"
            )

            // step for test2 is missing
            assertThat(
                testRun.testCaseArtifacts[
                    TestRunnerService.TestIdentifier(
                        className = "class2",
                        name = "name2",
                        runNumber = testRun.deviceRun.runNumber
                    )
                ]
            ).isNull()
            // No screenshots for test2
            val testIdentifier2 = TestRunnerService.TestIdentifier(
                className = "class2",
                name = "name2",
                runNumber = testRun.deviceRun.runNumber
            )
            val screenshots2 = subject.getTestMatrixArtifacts(
                testMatrixId,
                listOf(testIdentifier2)
            )
            assertThat(
                screenshots2
            ).isEmpty()
            // logcat for test3 is missing from gcloud folder
            assertThat(
                testRun.testCaseArtifacts[
                    TestRunnerService.TestIdentifier(
                        className = "class3",
                        name = "name3",
                        runNumber = testRun.deviceRun.runNumber
                    )
                ]
            ).isNull()
            // No screenshots for test3 either
            val testIdentifier3 = TestRunnerService.TestIdentifier(
                className = "class2",
                name = "name2",
                runNumber = testRun.deviceRun.runNumber
            )
            val screenshots3 = subject.getTestMatrixArtifacts(
                testMatrixId,
                listOf(testIdentifier3)
            )
            assertThat(
                screenshots3
            ).isEmpty()
        }
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
                DeviceRun.create(it)
            }
        ).containsExactlyEntriesIn(
            mapOf(
                "redfin-30-en-portrait" to DeviceRun(
                    id = "redfin-30-en-portrait",
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 0,
                    shard = null
                ),
                "redfin-30-en-portrait_rerun_1" to DeviceRun(
                    id = "redfin-30-en-portrait_rerun_1",
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 1,
                    shard = null
                ),
                "redfin-30-en-portrait-shard_0" to DeviceRun(
                    id = "redfin-30-en-portrait-shard_0",
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 0,
                    shard = 0
                ),
                "redfin-30-en-portrait-shard_20" to DeviceRun(
                    id = "redfin-30-en-portrait-shard_20",
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 0,
                    shard = 20
                ),
                "redfin-30-en-portrait-shard_6-rerun_2" to DeviceRun(
                    id = "redfin-30-en-portrait-shard_6-rerun_2",
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 2,
                    shard = 6
                ),
                "redfin-30-en-portrait-shard_2-rerun_3" to DeviceRun(
                    id = "redfin-30-en-portrait-shard_2-rerun_3",
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 3,
                    shard = 2
                ),
                "redfin-30-en-portrait-shard_3-rerun_21" to DeviceRun(
                    id = "redfin-30-en-portrait-shard_3-rerun_21",
                    deviceId = "redfin-30-en-portrait",
                    runNumber = 21,
                    shard = 3
                )
            )
        )
    }

    @Test
    fun getTestIssues() = runBlocking {
        val testMatrix = fakeBackend.fakeFirebaseTestLabApi.createTestMatrix(
            projectId = fakeBackend.firebaseProjectId,
            requestId = "requestId",
            testMatrix = TestMatrix(
                resultStorage = ResultStorage(
                    googleCloudStorage = GoogleCloudStorage(
                        "${fakeBackend.fakeGoogleCloudApi.rootGcsPath}/my-test-matrix-results"
                    ),
                    toolResultsExecution = ToolResultsExecution(
                        executionId = "test_executionId",
                        historyId = "test_historyId"
                    )
                ),
                projectId = fakeBackend.firebaseProjectId,
                environmentMatrix = EnvironmentMatrix(),
                testSpecification = TestSpecification()
            )
        )
        assertThat(
            subject.getTestMatrixTestIssues(testMatrix)
        ).isEmpty()

        val stepId = UUID.randomUUID().toString()
        fakeToolsResultApi.addStep(
            projectId = fakeBackend.firebaseProjectId,
            executionId = "test_executionId",
            historyId = "test_historyId",
            step = Step(
                stepId = stepId,
                testExecutionStep = TestExecutionStep(
                    testIssues = listOf(
                        TestIssue(
                            errorMessage = "test module error",
                            severity = TestIssue.Severity.severe
                        )
                    )
                )
            )
        )
        val testIssues = subject.getTestMatrixTestIssues(testMatrix)
        assertThat(
            testIssues
        ).isNotEmpty()

        assertThat(
            testIssues[stepId]?.first()?.errorMessage
        ).isEqualTo(
            "test module error"
        )
    }

    private fun TestRunnerService.ResultFileResource.readFully() = openInputStream().use {
        it.readAllBytes()
    }
}
