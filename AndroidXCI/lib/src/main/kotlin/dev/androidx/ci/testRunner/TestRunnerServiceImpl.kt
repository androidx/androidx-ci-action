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
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.testRunner.vo.UploadedApk
import java.io.InputStream

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

    override suspend fun getApkIfExists(name: String, sha256: String): UploadedApk? {
        return apkStore.getUploadedApk(
            name = name,
            sha256 = sha256
        )
    }

    override suspend fun getOrUploadApk(
        name: String,
        sha256: String,
        bytes: () -> ByteArray
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

    override suspend fun uploadApk(
        name: String,
        bytes: ByteArray
    ) = apkStore.uploadApk(name = name, bytes = bytes)

    override suspend fun scheduleTests(
        testApk: UploadedApk,
        appApk: UploadedApk?,
        devicePicker: DevicePicker
    ): TestRunnerService.ScheduleTestsResponse {
        val testMatrices = testLabController.submitTests(
            appApk = appApk ?: apkStore.getPlaceholderApk(),
            testApk = testApk,
            devicePicker = devicePicker
        )
        return TestRunnerService.ScheduleTestsResponse.create(
            testMatrices
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
        return googleCloudApi.walkEntires(path)
    }

    internal suspend fun findResultFiles(
        resultPath: GcsPath
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
        googleCloudApi.walkEntires(
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
                getTestResultFiles(visitor).intrumentationResult = ResultFileResourceImpl(visitor)
            }
        }
        return mergedXmlBlobs.map { mergedXmlEntry ->
            val relatedRuns = byFullDeviceId.entries.filter {
                it.key.startsWith(mergedXmlEntry.key)
            }.map { it.value }.sortedBy { it.runNumber }
            TestRunnerService.TestRunResult(
                deviceId = mergedXmlEntry.key,
                mergedResults = ResultFileResourceImpl(mergedXmlEntry.value),
                testRuns = relatedRuns
            )
        }
    }

    suspend fun getTestMatrixResults(
        testMatrixId: String
    ): List<TestRunnerService.TestRunResult>? {
        val testMatrix = testLabController.getTestMatrix(testMatrixId) ?: return null
        return getTestMatrixResults(testMatrix)
    }

    override suspend fun getTestMatrixResults(
        testMatrix: TestMatrix
    ): List<TestRunnerService.TestRunResult>? {
        if (!testMatrix.isComplete()) return null
        val resultPath = GcsPath(testMatrix.resultStorage.googleCloudStorage.gcsPath)
        return findResultFiles(resultPath)
    }

    companion object {
        private const val MERGED_TEST_RESULT_SUFFIX = "-test_results_merged.xml"
        private const val LOGCAT_FILE_NAME = "logcat"
        private const val TEST_RESULT_XML_PREFIX = "test_result_"
        private const val TEST_RESULT_XML_SUFFIX = ".xml"
        private const val INSTRUMENTATION_RESULTS_FILE_NAME = "instrumentation.results"
    }

    private class ResultFileResourceImpl(
        private val blobVisitor: BlobVisitor
    ) : TestRunnerService.ResultFileResource {
        override val gcsPath = blobVisitor.gcsPath
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
        override val fullDeviceId: String,
    ) : TestRunnerService.TestResultFiles {
        private val xmlResultBlobs = mutableListOf<TestRunnerService.ResultFileResource>()
        override var logcat: TestRunnerService.ResultFileResource? = null
            internal set
        override var intrumentationResult: TestRunnerService.ResultFileResource? = null
            internal set
        override val xmlResults: List<TestRunnerService.ResultFileResource> = xmlResultBlobs

        /**
         * Returns the run # for the test.
         * e.g. if the test run 3 times (due to retries), this will return 0, 1 and 2.
         */
        override val runNumber: Int

        init {
            val rerunNumber = fullDeviceId.substringAfterLast("rerun_", missingDelimiterValue = "")
            runNumber = if (rerunNumber.isBlank()) {
                0
            } else {
                rerunNumber.toIntOrNull() ?: 0
            }
        }

        internal fun addXmlResult(resultFileResource: TestRunnerService.ResultFileResource) {
            xmlResultBlobs.add(resultFileResource)
        }

        override fun toString(): String {
            return """
                TestResultFiles(
                  fullDeviceId='$fullDeviceId',
                  runNumber=$runNumber,
                  logcat=$logcat,
                  intrumentationResult=$intrumentationResult,
                  xmlResults=$xmlResults,
                )
            """.trimIndent()
        }
    }
}
