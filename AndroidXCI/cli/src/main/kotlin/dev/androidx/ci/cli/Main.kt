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
import dev.androidx.ci.testRunner.TestRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.FileAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Command line launcher for test runner.
 */
private class Cli : CliktCommand() {
    val runId: String by option(
        help = """
            The workflow run id from Github whose artifacts will be used to run tests.
            e.g. github.event.workflow_run.id
        """.trimIndent(),
        envvar = "ANDROIDX_RUN_ID"
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

    val githubRepository by option(
        help = """
            The github repository which is running this action.
            e.g. AndroidX/androidx
        """.trimIndent(),
        envvar = "GITHUB_REPOSITORY"
    ).required()

    override fun run() {
        configureLogger()
        val repoParts = githubRepository.split("/")
        check(repoParts.size >= 2) {
            "invalid github repo: $githubRepository"
        }
        val githubOwner = repoParts.first()
        val githubRepo = repoParts.drop(1).joinToString("/")
        val result = runBlocking {
            val testRunner = TestRunner.create(
                runId = runId,
                githubToken = githubToken,
                googleCloudCredentials = gcpServiceAccountKey,
                ioDispatcher = Dispatchers.IO,
                outputFolder = outputFolder,
                githubOwner = githubOwner,
                githubRepo = githubRepo
            )
            testRunner.runTests()
        }
        println(result.toJson())
        flushLogs()
        if (result.allTestsPassed) {
            exitProcess(0)
        } else {
            exitProcess(1)
        }
    }

    /**
     * Add new logger to log into the output directory.
     */
    private fun configureLogger() {
        val ctx = LogManager.getContext(false) as LoggerContext
        val config = ctx.configuration
        val layout = PatternLayout.createDefaultLayout(config)
        val appender = FileAppender.newBuilder<FileAppender.Builder<*>>()
            .withFileName(outputFolder.resolve("logs.txt").absolutePath)
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
}

fun main(args: Array<String>) {
    Cli().main(args)
}
