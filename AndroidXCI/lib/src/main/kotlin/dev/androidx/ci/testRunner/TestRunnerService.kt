package dev.androidx.ci.testRunner

import com.google.auth.Credentials
import dev.androidx.ci.config.Config
import dev.androidx.ci.config.Config.Datastore.Companion.AOSP_OBJECT_KIND
import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.gcloud.GoogleCloudApi
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.TestEnvironmentCatalog
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.testRunner.vo.UploadedApk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.logging.HttpLoggingInterceptor
import java.io.InputStream

interface TestRunnerService {
    /**
     * Uploads the given APK [bytes] with the given [name] if it was not uploaded before.
     *
     * Returns an instance of [UploadedApk] that identifies the APK's object in the file storage.
     */
    suspend fun uploadApk(
        name: String,
        bytes: ByteArray
    ): UploadedApk

    /**
     * Finds the APK in Google Cloud Storage with the given name and sha.
     * If it doesn't exist, uses the [bytes] method to obtain the bytes and uploads it.
     *
     * @param name Name of the APK. Should be the name that uniquely identifies the APK, can be a path.
     * @param sha256 sha256 of the bytes of the APK
     * @param bytes Callback method that can return the bytes of the APK to be uploaded, if necessary.
     *
     * @return Returns an [UploadedApk] instance that identifies the file in the cloud.
     */
    suspend fun getOrUploadApk(
        name: String,
        sha256: String,
        bytes: () -> ByteArray
    ): UploadedApk

    suspend fun getApkIfExists(
        name: String,
        sha256: String
    ): UploadedApk?

    /**
     * Schedules the tests for the given [testApk] / [appApk] pair using the provided [devicePicker].
     */
    suspend fun scheduleTests(
        testApk: UploadedApk,
        appApk: UploadedApk?,
        devicePicker: (TestEnvironmentCatalog) -> List<AndroidDevice>
    ): ScheduleTestsResponse

    /**
     * Queries the Firebase for the given [testMatrixId] and returns it if it exists.
     */
    suspend fun getTestMatrix(testMatrixId: String): TestMatrix?

    /**
     * Gets the result files for the given [testMatrix]. The result includes references to outputs of the test
     * (logs, junit xml files etc)
     */
    suspend fun getTestMatrixResults(testMatrix: TestMatrix): List<TestRunResult>?

    companion object {
        /**
         * Creates an implementation of [TestRunnerService].
         */
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

    /**
     * Represents a result resource, like xml results or logcat or even a video.
     */
    interface ResultFileResource {
        /**
         * The Google Cloud storage path of the file.
         */
        val gcsPath: GcsPath

        /**
         * Creates an [InputStream] for the file. Note that you must close it after using.
         */
        fun openInputStream(): InputStream
    }

    /**
     * Represents a result of scheduling tests.
     */
    data class ScheduleTestsResponse(
        /**
         * All test matrices that were either created or re-used from previous runs.
         */
        val testMatrices: List<TestMatrix>,
        /**
         * Numbers of new test matrices
         */
        val pendingTests: Int,
        /**
         * Number of re-used test matrices.
         */
        val alreadyCompletedTests: Int
    ) {
        companion object {
            internal fun create(
                testMatrices: List<TestMatrix>
            ) = ScheduleTestsResponse(
                testMatrices = testMatrices,
                pendingTests = testMatrices.count {
                    !it.isComplete()
                },
                alreadyCompletedTests = testMatrices.count {
                    it.isComplete()
                }
            )
        }
    }

    /**
     * Represents the result of a [TestMatrix].
     */
    class TestRunResult(
        /**
         * The device identifier that run the test.
         * e.g. redfin-30-en-portrait
         */
        val deviceId: String,
        /**
         * The xml file which includes all results from all test runs (including re-runs)
         */
        val mergedResults: ResultFileResource,
        /**
         * Each individual test run. This list usually contains 1 item but might contain more than one if
         * the [DevicePicker] choose multiple devices OR some tests were re-run due to failures.
         */
        val testRuns: List<TestResultFiles>
    ) {
        override fun toString(): String {
            return buildString {
                appendLine("TestRunResult(")
                appendLine("deviceId: $deviceId")
                appendLine("mergedResults: $mergedResults")
                testRuns.forEach {
                    appendLine(it)
                }
                appendLine(")")
            }
        }
    }

    /**
     * Files for an individual test run
     */
    interface TestResultFiles {
        /**
         * The device identifier that run the test.
         * e.g. redfin-30-en-portrait_rerun_1
         */
        val fullDeviceId: String

        /**
         * Full logcat file for the test
         */
        val logcat: ResultFileResource?

        /**
         * Instrumentation result output logs for the test
         */
        val intrumentationResult: ResultFileResource?

        /**
         * XML result files produced by the test.
         */
        val xmlResults: List<ResultFileResource>

        /**
         * The run number. First run is 0 and any subsequent re-run starts from 1.
         */
        val runNumber: Int
    }
}
