package dev.androidx.ci.testRunner

import dev.androidx.ci.gcloud.GcsPath
import dev.androidx.ci.util.GoogleCloudCredentialsRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

internal class TestRunnerServicePlayground {
    @get:Rule
    val playgroundCredentialsRule = GoogleCloudCredentialsRule()

    val subject by lazy {
        TestRunnerService.create(
            credentials = playgroundCredentialsRule.gcpConfig.credentials,
            firebaseProjectId = playgroundCredentialsRule.gcpConfig.projectId,
            bucketName = "androidx-ftl-test-results",
            bucketPath = "github-ci-action",
            gcsResultPath = "yigit-local",
            logHttpCalls = false
        )
    }

    @Test
    fun playground() = runBlocking<Unit> {
        val testMatrixId = "matrix-hzjx70s88liva"
        val testMatrix = subject.getTestMatrix(testMatrixId)
        println(testMatrix)
        subject.resultFiles(testMatrixId)?.forEach { resultFiles ->
            println(resultFiles)
            resultFiles.mergedResults.openInputStream().use {
                println(it.readAllBytes().toString(Charsets.UTF_8))
            }
        }
    }

    @Test
    fun log() = runBlocking<Unit> {
        subject.test("gs://androidx-ftl-test-results/github-ci-action/ftl/2490462699/fd6c9f30215965b0b00aa99c98dfb6bef4b5ea849b07f3b7a3c8869360279dde/").filter {
//            val fileName = it.fileName
//            fileName.startsWith("test_result_") && fileName.endsWith(".xml")
            it.fileName.endsWith(".xml")
        }.forEach {
            println("-----------")
            it.obtainInputStream().use {
                it.bufferedReader(Charsets.UTF_8).readLines().forEach {
                    println(it)
                }
            }
            println("-----------")

        }
    }

    @Test
    fun log2() = runBlocking<Unit> {
        val gcsPath = GcsPath("gs://androidx-ftl-test-results/github-ci-action/ftl/2490462699/fd6c9f30215965b0b00aa99c98dfb6bef4b5ea849b07f3b7a3c8869360279dde/")
        subject.resultFiles(gcsPath).forEach {
            println(it)
        }
    }
}