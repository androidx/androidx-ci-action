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

import com.google.auth.Credentials
import dev.androidx.ci.config.Config
import dev.androidx.ci.config.Config.Datastore.Companion.AOSP_OBJECT_KIND
import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.gcloud.BlobVisitor
import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.gcloud.GoogleCloudApi
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.testRunner.vo.UploadedApk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.logging.HttpLoggingInterceptor
import org.apache.logging.log4j.kotlin.logger
import java.io.InputStream

/**
 * A new controller for APIs used by AOSP test runner.
 */
class TestRunnerServiceImpl internal constructor(
    private val googleCloudApi: GoogleCloudApi,
    firebaseProjectId: String,
    datastoreApi: DatastoreApi,
    toolsResultApi: ToolsResultApi,
    firebaseTestLabApi: FirebaseTestLabApi,
    gcsResultPath: String
) : TestRunnerService {
    private val logger = logger()

    private val testMatrixStore = TestMatrixStore(
        firebaseProjectId = firebaseProjectId,
        datastoreApi = datastoreApi,
        firebaseTestLabApi = firebaseTestLabApi,
        toolsResultApi = toolsResultApi,
        resultsGcsPrefix = googleCloudApi.getGcsPath("aosp-ftl/$gcsResultPath")
    )
    private val apkStore = ApkStore(googleCloudApi)
    internal val testLabController = FirebaseTestLabController(
        firebaseTestLabApi = firebaseTestLabApi,
        firebaseProjectId = firebaseProjectId,
        testMatrixStore = testMatrixStore
    )

    /**
     * Finds the APK in Google Cloud Storage with the given name and sha.
     * If it doesn't exist, uses the [bytes] method to obtain the bytes and uploads it.
     *
     * @param name Name of the APK. Should be the name that uniquely identifies the APK, can be a path.
     * @param sha256 sha256 of the bytes of the APK
     * @param bytes Callback method that can return the bytes of the APK to be uploaded, if necessary.
     */
    suspend fun getOrUploadApk(
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
        return googleCloudApi.walkTopDown(path)
    }

    internal suspend fun resultFiles(
        resultPath: GcsPath
    ): List<TestRunnerService.TestRunResult> {
        val byFullDeviceId = mutableMapOf<String, TestRunnerService.TestResultFiles>()
        fun BlobVisitor.fullDeviceId() = relativePath.substringBefore('/', "")
        val mergedXmlBlobs = mutableMapOf<String, BlobVisitor>()
        fun getTestResultFiles(
            visitor: BlobVisitor
        ) = byFullDeviceId.getOrPut(visitor.fullDeviceId()) {
            TestRunnerService.TestResultFiles(fullDeviceId = visitor.fullDeviceId())
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
        googleCloudApi.walkTopDown(
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

    suspend fun resultFiles(
        testMatrixId: String
    ): List<TestRunnerService.TestRunResult>? {
        val testMatrix = testLabController.getTestMatrix(testMatrixId) ?: return null
        return resultFiles(testMatrix)
    }

    suspend fun resultFiles(
        testMatrix: TestMatrix
    ): List<TestRunnerService.TestRunResult>? {
        if (!testMatrix.isComplete()) return null
        val resultPath = GcsPath(testMatrix.resultStorage.googleCloudStorage.gcsPath)
        return resultFiles(resultPath)
    }

    companion object {
        private const val MERGED_TEST_RESULT_SUFFIX = "-test_results_merged.xml"
        private const val LOGCAT_FILE_NAME = "logcat"
        private const val TEST_RESULT_XML_PREFIX = "test_result_"
        private const val TEST_RESULT_XML_SUFFIX = ".xml"
        private const val INSTRUMENTATION_RESULTS_FILE_NAME = "instrumentation.results"
        fun create(
            /**
             * service account file contents
             */
            credentials: Credentials,
            /**
             * Firebase project id
             */
            firebaseProjectId: String,
            /**
             * GCP bucket name
             */
            bucketName: String,
            /**
             * GCP bucket path (path inside the bucket)
             */
            bucketPath: String,
            /**
             * GCP path to put the results into. Re-using the same path might result in a very
             * big object in GCP so it might make sense to use a single path per initialization.
             * (e.g. a timestamp followed by a random suffix).
             */
            gcsResultPath: String,
            /**
             * If enabled, HTTP requests will also be logged. Keep in mind, they might include
             * sensitive data.
             */
            logHttpCalls: Boolean = false,
            /**
             * The coroutine dispatcher to use for IO operations. Defaults to [Dispatchers.IO].
             */
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
            /**
             * The "kind" for objects that are kept in Datastore, defaults to [AOSP_OBJECT_KIND].
             * You may want to modify this if you want to use the same GCP account for isolated
             * test runs.
             */
            testRunDataStoreObjectKind: String = AOSP_OBJECT_KIND
        ): TestRunnerService {
            val httpLogLevel = if (logHttpCalls) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            val gcpConfig = Config.Gcp(
                credentials = credentials,
                projectId = firebaseProjectId
            )
            return TestRunnerServiceImpl(
                googleCloudApi = GoogleCloudApi.build(
                    Config.CloudStorage(
                        gcp = gcpConfig,
                        bucketName = bucketName,
                        bucketPath = bucketPath,
                    ),
                    context = ioDispatcher
                ),
                datastoreApi = DatastoreApi.build(
                    Config.Datastore(
                        gcp = gcpConfig,
                        testRunObjectKind = testRunDataStoreObjectKind,
                    ),
                    context = ioDispatcher
                ),
                toolsResultApi = ToolsResultApi.build(
                    config = Config.ToolsResult(
                        gcp = gcpConfig,
                        httpLogLevel = httpLogLevel,
                    )
                ),
                firebaseProjectId = firebaseProjectId,
                firebaseTestLabApi = FirebaseTestLabApi.build(
                    config = Config.FirebaseTestLab(
                        gcp = gcpConfig,
                        httpLogLevel = httpLogLevel,
                    )
                ),
                gcsResultPath = gcsResultPath
            )
        }
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

}