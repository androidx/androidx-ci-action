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
package dev.androidx.ci.testRunner

import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.gcloud.BlobVisitor
import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.gcloud.GoogleCloudApi
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.ClientInfo
import dev.androidx.ci.generated.ftl.ShardingOption
import dev.androidx.ci.generated.ftl.TestEnvironmentCatalog
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.testRunner.vo.DeviceSetup
import dev.androidx.ci.testRunner.vo.RemoteApk
import dev.androidx.ci.testRunner.vo.UploadedApk
import java.io.InputStream
import java.util.Locale

/**
 * Real implementation of [TestRunnerService].
 */
internal class TestRunnerServiceImpl internal constructor(
    private val googleCloudApi: GoogleCloudApi,
    firebaseProjectId: String,
    datastoreApi: DatastoreApi,
    toolsResultApi: ToolsResultApi,
    firebaseTestLabApi: FirebaseTestLabApi,
    gcsResultPath: String
) : TestRunnerService {
    private val testMatrixStore = TestMatrixStore(
        firebaseProjectId = firebaseProjectId,
        datastoreApi = datastoreApi,
        firebaseTestLabApi = firebaseTestLabApi,
        toolsResultApi = toolsResultApi,
        resultsGcsPrefix = googleCloudApi.getGcsPath("aosp-ftl/$gcsResultPath")
    )
    private val apkStore = ApkStore(googleCloudApi)
    private val testLabController = FirebaseTestLabController(
        firebaseTestLabApi = firebaseTestLabApi,
        firebaseProjectId = firebaseProjectId,
        testMatrixStore = testMatrixStore
    )
    private val testExecutionStore = TestExecutionStore(
        toolsResultApi = toolsResultApi
    )

    override suspend fun getOrUploadApk(
        name: String,
        sha256: String,
        bytes: suspend () -> ByteArray
    ): UploadedApk {
        apkStore.getUploadedApk(
            name = name,
            sha256 = sha256
        )?.let {
            return it
        }
        return apkStore.uploadApk(
            name = name,
            bytes = bytes()
        )
    }

    override suspend fun getOrUploadRemoteApk(
        sourceGcsPath: GcsPath,
        targetRelativePath: String,
    ): RemoteApk {
        val targetGcsPath = googleCloudApi.existingFilePath(targetRelativePath)
            ?: googleCloudApi.copy(sourceGcsPath, targetRelativePath)

        return RemoteApk(gcsPath = targetGcsPath)
    }

    override suspend fun uploadApk(
        name: String,
        bytes: ByteArray
    ) = apkStore.uploadApk(name = name, bytes = bytes)

    override suspend fun scheduleTests(
        testApk: UploadedApk,
        appApk: UploadedApk?,
        clientInfo: ClientInfo?,
        sharding: ShardingOption?,
        deviceSetup: DeviceSetup?,
        devicePicker: (TestEnvironmentCatalog) -> List<AndroidDevice>,
        pullScreenshots: Boolean,
        cachedTestMatrixFilter: CachedTestMatrixFilter,
        testTargets: List<String>?,
        flakyTestAttempts: Int,
        testTimeoutSeconds: Int
    ): TestRunnerService.ScheduleTestsResponse {
        val testMatrices = testLabController.submitTests(
            appApk = appApk ?: apkStore.getPlaceholderApk(),
            testApk = testApk,
            clientInfo = clientInfo,
            sharding = sharding,
            deviceSetup = deviceSetup,
            devicePicker = devicePicker,
            pullScreenshots = pullScreenshots,
            cachedTestMatrixFilter = cachedTestMatrixFilter,
            testTargets = testTargets,
            flakyTestAttempts = flakyTestAttempts,
            testTimeoutSeconds = testTimeoutSeconds
        )
        return TestRunnerService.ScheduleTestsResponse.create(
            testMatrices
        )
    }

    override suspend fun scheduleTests(
        testMatrix: TestMatrix,
        testTargets: List<String>,
        cachedTestMatrixFilter: CachedTestMatrixFilter
    ): TestMatrix {
        return testLabController.scheduleTests(
            testMatrix = testMatrix,
            testTargets = testTargets,
            cachedTestMatrixFilter = cachedTestMatrixFilter
        )
    }

    override suspend fun getTestMatrix(
        testMatrixId: String
    ): TestMatrix? {
        return testLabController.getTestMatrix(testMatrixId)
    }

    internal suspend fun test(
        gcsPath: String = "gs://androidx-ftl-test-results/github-ci-action/ftl"
    ): Sequence<BlobVisitor> {
        val path = GcsPath(path = gcsPath)
        return googleCloudApi.walkEntries(path)
    }

    internal suspend fun findResultFiles(
        resultPath: GcsPath,
        testMatrix: TestMatrix
    ): List<TestRunnerService.TestRunResult> {
        val byFullDeviceId = mutableMapOf<String, TestResultFilesImpl>()
        fun BlobVisitor.fullDeviceId() = relativePath.substringBefore('/', "")
        val mergedXmlBlobs = mutableMapOf<String, BlobVisitor>()
        fun getTestResultFiles(
            visitor: BlobVisitor
        ) = byFullDeviceId.getOrPut(visitor.fullDeviceId()) {
            TestResultFilesImpl(fullDeviceId = visitor.fullDeviceId())
        }

        // sample output looks like:
        // redfin-30-en-portrait-test_results_merged.xml
        // redfin-30-en-portrait/instrumentation.results
        // redfin-30-en-portrait/logcat
        // redfin-30-en-portrait/test_cases/0000_logcat
        // redfin-30-en-portrait/test_cases/0001_logcat
        // redfin-30-en-portrait/test_cases/0002_logcat
        // redfin-30-en-portrait/test_result_1.xml
        // redfin-30-en-portrait/video.mp4
        // redfin-30-en-portrait_rerun_1/logcat
        // redfin-30-en-portrait_rerun_1/test_result_1.xml
        // redfin-30-en-portrait_rerun_2/logcat
        // redfin-30-en-portrait_rerun_2/test_result_1.xml

        val steps = testExecutionStore.getTestExecutionSteps(testMatrix)
        googleCloudApi.walkEntries(
            gcsPath = resultPath
        ).forEach { visitor ->
            val fileName = visitor.fileName
            if (fileName.endsWith(MERGED_TEST_RESULT_SUFFIX)) {
                mergedXmlBlobs[
                    fileName.substringBefore(MERGED_TEST_RESULT_SUFFIX)
                ] = visitor
            } else if (fileName.startsWith(TEST_RESULT_XML_PREFIX) && fileName.endsWith(TEST_RESULT_XML_SUFFIX)) {
                getTestResultFiles(visitor).addXmlResult(
                    ResultFileResourceImpl(visitor)
                )
            } else if (fileName == LOGCAT_FILE_NAME) {
                getTestResultFiles(visitor).logcat = ResultFileResourceImpl(visitor)
            } else if (fileName == INSTRUMENTATION_RESULTS_FILE_NAME) {
                getTestResultFiles(visitor).instrumentationResult = ResultFileResourceImpl(visitor)
            } else if (fileName.endsWith(LOGCAT_FILE_NAME_SUFFIX)) {
                val step = steps.flatMap {
                    it.testExecutionStep?.toolExecution?.toolOutputs ?: emptyList()
                }.find {
                    (it.output?.fileUri == visitor.gcsPath.toString())
                }
                val runNumber = DeviceRun.create(visitor.fullDeviceId()).runNumber
                step?.testCase?.className?.let { className ->
                    step.testCase.name?.let { name ->
                        TestRunnerService.TestIdentifier(
                            className,
                            name,
                            runNumber
                        )
                    }
                }?.let { testIdentifier ->
                    getTestResultFiles(visitor).addTestCaseArtifact(
                        testIdentifier,
                        ResultFileResourceImpl(visitor),
                        "LOGCAT"
                    )
                }
            } else if (fileName.contains(CRASH_REPORT_FILE_NAME)) {
                getTestResultFiles(visitor).crashReport = ResultFileResourceImpl(visitor)
            }
        }
        // remove this if block when b/299975596 is fixed
        if (mergedXmlBlobs.isEmpty()) {
            return byFullDeviceId.values.map {
                TestRunnerService.TestRunResult(
                    deviceId = it.deviceRun.deviceId,
                    mergedResults = null,
                    testRuns = listOf(it)
                )
            }
        }
        return mergedXmlBlobs.map { mergedXmlEntry ->
            val relatedRuns = byFullDeviceId.entries.filter {
                it.key.startsWith(mergedXmlEntry.key)
            }.map { it.value }.sortedBy { it.deviceRun.runNumber }
            TestRunnerService.TestRunResult(
                deviceId = mergedXmlEntry.key,
                mergedResults = ResultFileResourceImpl(mergedXmlEntry.value),
                testRuns = relatedRuns
            )
        }
    }

    private suspend fun findArtifacts(
        resultPath: GcsPath,
        testIdentifiers: List<TestRunnerService.TestIdentifier>
    ): Map< TestRunnerService.TestIdentifier, List<TestRunnerService.TestCaseArtifact>>? {
        if (testIdentifiers.isEmpty()) return null
        val testArtifactsBlobs = mutableMapOf<TestRunnerService.TestIdentifier, MutableList<TestRunnerService.TestCaseArtifact>>()
        val testNames = testIdentifiers.associateBy { testIdentifier ->
            "${testIdentifier.className}_${testIdentifier.name}"
        }
        val testRunNumber = testIdentifiers.first().runNumber
        fun BlobVisitor.fullDeviceId() = relativePath.substringBefore('/', "")
        googleCloudApi.walkEntries(
            gcsPath = resultPath
        ).forEach { visitor ->
            val runNumber = DeviceRun.create(visitor.fullDeviceId()).runNumber
            if (runNumber == testRunNumber) {
                val testName = testNames.keys.find { testName ->
                    visitor.fileName.startsWith(testName)
                }
                val testIdentifier = testNames[testName]
                if (testIdentifier != null) {
                    testArtifactsBlobs.getOrPut(testIdentifier) {
                        mutableListOf()
                    }.add(
                        TestRunnerService.TestCaseArtifact(
                            ResultFileResourceImpl(visitor),
                            visitor.fileName.substringAfterLast(".").uppercase(Locale.US)
                        )
                    )
                }
            }
        }
        return testArtifactsBlobs
    }

    override suspend fun getResultFileResource(
        gcsPath: GcsPath
    ): TestRunnerService.ResultFileResource? {
        return googleCloudApi.getBlob(gcsPath)?.let { blobVisitor ->
            ResultFileResourceImpl(
                blobVisitor
            )
        }
    }

    override suspend fun getTestMatrixTestIssues(testMatrix: TestMatrix): Map<String, List<TestRunnerService.TestIssue>> {
        val steps = testExecutionStore.getTestExecutionSteps(testMatrix)
        return steps.associate {
            (it.stepId ?: "invalidStepId") to (
                (it.testExecutionStep?.testIssues)?.map { testIssue ->
                    TestRunnerService.TestIssue(
                        errorMessage = testIssue.errorMessage ?: "error message not set",
                        severity = testIssue.severity?.name ?: "unspecifiedSeverity",
                        type = testIssue.type?.name,
                        stackTrace = testIssue.stackTrace?.exception
                    )
                } ?: emptyList()
                )
        }.filter {
            it.key != "invalidStepId"
        }.filter {
            it.value.isNotEmpty()
        }
    }

    suspend fun getTestMatrixResults(
        testMatrixId: String
    ): List<TestRunnerService.TestRunResult>? {
        val testMatrix = testLabController.getTestMatrix(testMatrixId) ?: return null
        return getTestMatrixResults(testMatrix)
    }

    suspend fun getTestMatrixArtifacts(
        testMatrixId: String,
        testIdentifiers: List<TestRunnerService.TestIdentifier>
    ): Map<TestRunnerService.TestIdentifier, List<TestRunnerService.TestCaseArtifact>>? {
        val testMatrix = testLabController.getTestMatrix(testMatrixId) ?: return null
        return getTestMatrixArtifacts(testMatrix, testIdentifiers)
    }

    override suspend fun getTestMatrixResults(
        testMatrix: TestMatrix
    ): List<TestRunnerService.TestRunResult>? {
        if (!testMatrix.isComplete()) return null
        val resultPath = GcsPath(testMatrix.resultStorage.googleCloudStorage.gcsPath)
        return findResultFiles(resultPath, testMatrix)
    }

    override suspend fun getTestMatrixArtifacts(
        testMatrix: TestMatrix,
        testIdentifiers: List<TestRunnerService.TestIdentifier>
    ): Map<TestRunnerService.TestIdentifier, List<TestRunnerService.TestCaseArtifact>>? {
        if (!testMatrix.isComplete()) return null
        val resultPath = GcsPath(testMatrix.resultStorage.googleCloudStorage.gcsPath)
        return findArtifacts(resultPath, testIdentifiers)
    }

    companion object {
        private const val MERGED_TEST_RESULT_SUFFIX = "-test_results_merged.xml"
        private const val LOGCAT_FILE_NAME = "logcat"
        private const val LOGCAT_FILE_NAME_SUFFIX = "_logcat"
        private const val TEST_RESULT_XML_PREFIX = "test_result_"
        private const val TEST_RESULT_XML_SUFFIX = ".xml"
        private const val INSTRUMENTATION_RESULTS_FILE_NAME = "instrumentation.results"
        private const val CRASH_REPORT_FILE_NAME = "crash"
    }
    class ResultFileResourceImpl(
        private val blobVisitor: BlobVisitor
    ) : TestRunnerService.ResultFileResource {
        override val gcsPath = blobVisitor.gcsPath
        override val size = blobVisitor.size
        override fun openInputStream(): InputStream = blobVisitor.obtainInputStream()
        override fun toString(): String {
            return "ResultFile('$gcsPath')"
        }
    }

    class TestResultFilesImpl internal constructor(
        /**
         * an identifier for the device that run the test
         * e.g. redfin-30-en-portrait
         * e.g. redfin-30-en-portrait-rerun_1/
         */
        fullDeviceId: String,
    ) : TestRunnerService.TestResultFiles {
        private val xmlResultBlobs = mutableListOf<TestRunnerService.ResultFileResource>()
        private val testCaseArtifactBlobs = mutableMapOf<TestRunnerService.TestIdentifier, MutableList<TestRunnerService.TestCaseArtifact>>()

        override var logcat: TestRunnerService.ResultFileResource? = null
            internal set
        override var instrumentationResult: TestRunnerService.ResultFileResource? = null
            internal set
        override val xmlResults: List<TestRunnerService.ResultFileResource> = xmlResultBlobs
        override val testCaseArtifacts: Map<TestRunnerService.TestIdentifier, List<TestRunnerService.TestCaseArtifact>> = testCaseArtifactBlobs
        override val deviceRun: DeviceRun = DeviceRun.create(fullDeviceId)
        override var crashReport: TestRunnerService.ResultFileResource? = null
            internal set

        internal fun addXmlResult(resultFileResource: TestRunnerService.ResultFileResource) {
            xmlResultBlobs.add(resultFileResource)
        }

        internal fun addTestCaseArtifact(
            testCase: TestRunnerService.TestIdentifier,
            resultFileResource: TestRunnerService.ResultFileResource,
            resourceType: String
        ) {
            testCaseArtifactBlobs.getOrPut(testCase) {
                mutableListOf()
            }.add(
                TestRunnerService.TestCaseArtifact(
                    resultFileResource,
                    resourceType
                )
            )
        }
        override fun toString(): String {
            return """
                TestResultFiles(
                  device='$deviceRun',
                  logcat=$logcat,
                  intrumentationResult=$instrumentationResult,
                  xmlResults=$xmlResults,
                )
            """.trimIndent()
        }
    }
}
