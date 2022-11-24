package dev.androidx.ci.testRunner

import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.TestEnvironmentCatalog
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.testRunner.vo.UploadedApk
import java.io.InputStream

interface TestRunnerService {
    suspend fun uploadApk(
        name: String,
        bytes: ByteArray
    ): UploadedApk

    suspend fun scheduleTests(
        testApk: UploadedApk,
        appApk: UploadedApk?,
        devicePicker: (TestEnvironmentCatalog) -> List<AndroidDevice>
    ): ScheduleTestsResponse

    suspend fun getTestMatrix(testMatrixId: String): TestMatrix?

    /**
     * Represents a result resource, like xml results or logcat or even a video.
     */
    interface ResultFileResource {
        val gcsPath: GcsPath
        fun openInputStream(): InputStream
    }

    data class ScheduleTestsResponse(
        val testMatrices: List<TestMatrix>,
        val pendingTests: Int,
        val alreadyCompletedTests: Int
    ) {
        companion object {
            fun create(
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

    class TestRunResult(
        val deviceId: String,
        val mergedResults: ResultFileResource,
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
    class TestResultFiles internal constructor(
        /**
         * an identifier for the device that run the test
         * e.g. redfin-30-en-portrait
         * e.g. redfin-30-en-portrait-rerun_1/
         */
        val fullDeviceId: String,
    ) {
        private val xmlResultBlobs = mutableListOf<ResultFileResource>()
        var logcat: ResultFileResource? = null
            internal set
        var intrumentationResult: ResultFileResource? = null
            internal set
        val xmlResults: List<ResultFileResource> = xmlResultBlobs
        /**
         * Returns the run # for the test.
         * e.g. if the test run 3 times (due to retries), this will return 0, 1 and 2.
         */
        val runNumber: Int
        init {
            val rerunNumber = fullDeviceId.substringAfterLast("rerun_", missingDelimiterValue = "")
            runNumber = if (rerunNumber.isBlank()) {
                0
            } else {
                rerunNumber.toIntOrNull() ?: 0
            }
        }

        internal fun addXmlResult(resultFileResource: ResultFileResource) {
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