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

    suspend fun getTestMatrixResults(testMatrix: TestMatrix): List<TestRunResult>?

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

    interface TestResultFiles {
        val fullDeviceId: String
        val logcat: ResultFileResource?
        val intrumentationResult: ResultFileResource?
        val xmlResults: List<ResultFileResource>
        val runNumber: Int
    }
}
