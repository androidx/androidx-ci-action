/*
 * Copyright 2021 The Android Open Source Project
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

package dev.androidx.ci.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import dev.androidx.ci.generated.ftl.AndroidDevice
import dev.androidx.ci.generated.ftl.TestEnvironmentCatalog
import dev.androidx.ci.testRunner.DevicePicker
import dev.androidx.ci.testRunner.TestRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.FileAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Command line launcher for test runner.
 */
private class Cli : CliktCommand() {
    val targetRunId: String by option(
        help = """
            The workflow run id from Github whose artifacts will be used to run tests.
            e.g. github.event.workflow_run.id
        """.trimIndent(),
        envvar = "ANDROIDX_TARGET_RUN_ID"
    ).required()
    val hostRunId: String by option(
        help = """
            The workflow run id from Github which is running these tests right now.
            Will be useful to construct urls for results:
            e.g. github.run_id
        """.trimIndent(),
        envvar = "ANDROIDX_HOST_RUN_ID"
    ).required()
    val githubToken by option(
        help = """
            Github access token. Can also be set with ANDROIDX_GITHUB_TOKEN environment variable.
            e.g. secrets.GITHUB_TOKEN
            https://docs.github.com/en/actions/reference/authentication-in-a-workflow#about-the-github_token-secret
        """.trimIndent(),
        envvar = "ANDROIDX_GITHUB_TOKEN"
    ).required()

    val gcpServiceAccountKey by option(
        help = """
            Google Cloud Service Account credentials (JSON). Can also be set with ANDROIDX_GCLOUD_CREDENTIALS
            environment variable.
        """.trimIndent(),
        envvar = "ANDROIDX_GCLOUD_CREDENTIALS"
    ).required()

    val outputFolder by option(
        help = """
            The output folder where results will be downloaded to as well as logs.
        """.trimIndent(),
        envvar = "ANDROIDX_OUTPUT_FOLDER"
    ).file(canBeFile = false, canBeDir = true).required()

    val logFile by option(
        help = """
            The output file for test runner logs. Make sure this is different from the output
            folder so that it will not be deleted when outputs are being downloaded
        """.trimIndent(),
        envvar = "ANDROIDX_LOG_FILE"
    ).file(canBeFile = true, canBeDir = false)

    val githubRepository by option(
        help = """
            The github repository which is running this action.
            e.g. AndroidX/androidx
        """.trimIndent(),
        envvar = "GITHUB_REPOSITORY"
    ).required()

    val deviceSpecs by option(
        help = """
            List of device - sdk pairs to run the tests.
            Each spec should be in the form of <id>:<sdkVersion> concatenated by ','.
            e.g. "redfin:30, sailfish:25"
        """.trimIndent(),
        envvar = "ANDROIDX_DEVICE_SPECS",
    )

    val artifactNameRegex by option(
        help = """
            Regex to filter artifacts. If empty, all artifacts will be checked for apks which
            might be too big.
            e.g. "tests*zip"
        """.trimIndent(),
        envvar = "ANDROIDX_ARTIFACT_NAME_FILTER_REGEX"
    )

    val gcpBucketName by option(
        help = """
            Bucket name to use for artifacts
        """.trimIndent(),
        envvar = "ANDROIDX_BUCKET_NAME"
    ).required()

    val gcpBucketPath by option(
        help = """
            Bucket path to use for artifacts
        """.trimIndent(),
        envvar = "ANDROIDX_BUCKET_PATH"
    ).required()

    override fun run() {
        logFile?.let(::configureLogger)
        val repoParts = githubRepository.split("/")
        check(repoParts.size >= 2) {
            "invalid github repo: $githubRepository"
        }
        val githubOwner = repoParts.first()
        val githubRepo = repoParts.drop(1).joinToString("/")
        val artifactNameFilter = artifactNameRegex?.let { input ->
            createRegexArtifactFilter(input)
        } ?: acceptAll
        val result = runBlocking {
            val testRunner = TestRunner.create(
                targetRunId = targetRunId,
                hostRunId = hostRunId,
                githubToken = githubToken,
                googleCloudCredentials = gcpServiceAccountKey,
                ioDispatcher = Dispatchers.IO,
                outputFolder = outputFolder,
                githubOwner = githubOwner,
                githubRepo = githubRepo,
                devicePicker = deviceSpecs?.let(::createDevicePicker),
                artifactNameFilter = artifactNameFilter,
                bucketName = gcpBucketName,
                bucketPath = gcpBucketPath
            )
            testRunner.runTests()
        }
        println(result.toJson())
        flushLogs()
        if (result.allTestsPassed) {
            exitProcess(0)
        } else {
            println("================= FAILURE LOG =================")
            println(result.failureLog)
            println("===============================================")
            exitProcess(1)
        }
    }

    private val acceptAll = { _: String ->
        true
    }

    private fun createRegexArtifactFilter(
        input: String
    ): (String) -> Boolean {
        val regex = input.toRegex()
        return { artifactName ->
            regex.matches(artifactName)
        }
    }
    /**
     * Add new logger to log into the output directory.
     */
    private fun configureLogger(
        logFile: File
    ) {
        logFile.parentFile.mkdirs()
        val ctx = LogManager.getContext(false) as LoggerContext
        val config = ctx.configuration
        val layout = PatternLayout.createDefaultLayout(config)
        val appender = FileAppender.newBuilder<FileAppender.Builder<*>>()
            .withFileName(logFile.absolutePath)
            .withAppend(false)
            .withImmediateFlush(false)
            .setName("File")
            .setLayout(layout)
            .setIgnoreExceptions(false)
            .withBufferedIo(false)
            .withBufferSize(4000)
            .setConfiguration(config)
            .build()
        appender.start()
        config.rootLogger.addAppender(
            appender,
            Level.ALL,
            null
        )
        ctx.updateLoggers(config)
    }

    private fun flushLogs() {
        val ctx = LogManager.getContext(false) as LoggerContext
        ctx.stop(10, TimeUnit.SECONDS)
    }

    fun createDevicePicker(
        rawInput: String
    ): DevicePicker {
        val specs = DeviceSpec.parseSpecs(rawInput)
        check(specs.isNotEmpty()) {
            "Empty device specs: $rawInput"
        }
        return { catalog: TestEnvironmentCatalog ->
            val models = checkNotNull(catalog.androidDeviceCatalog?.models) {
                "No models in the environment catalog: $catalog"
            }
            specs.map { spec ->
                // validate we have a matching model.
                val model = models.find {
                    it.id == spec.deviceId && it.supportedVersionIds?.contains(spec.sdk) == true
                }
                checkNotNull(model) {
                    "Cannot find $spec in models: $models"
                }
                AndroidDevice(
                    orientation = "portrait",
                    androidModelId = model.id,
                    locale = "en",
                    androidVersionId = spec.sdk
                )
            }
        }
    }
}

data class DeviceSpec(
    val deviceId: String,
    val sdk: String
) {
    companion object {
        private fun parseSpec(spec: String): DeviceSpec {
            @Suppress("NAME_SHADOWING")
            val spec = spec.trim()
            val parts = spec.split(":")
            require(parts.size == 2) {
                """
                        Each device spec should have two parts separated by ':'.
                        Invalid input: $spec
                    """
            }
            val deviceId = parts[0].trim().also {
                require(it.isNotBlank()) {
                    "Device id cannot be blank"
                }
            }
            val sdkVersion = parts[1].trim().also {
                require(it.isNotBlank()) {
                    "SDK cannot be blank"
                }
            }
            return DeviceSpec(
                deviceId = deviceId,
                sdk = sdkVersion
            )
        }
        fun parseSpecs(input: String): List<DeviceSpec> {
            return input.split(",").map {
                it.trim()
            }.map(::parseSpec)
        }
    }
}

fun main(args: Array<String>) {
    Cli().main(args)
}
