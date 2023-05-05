/*
 * Copyright 2023 The Android Open Source Project
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

package dev.androidx.ci.aosp

import com.squareup.moshi.Moshi
import dev.androidx.ci.github.GithubApi
import dev.zacsweers.moshix.reflect.MetadataKotlinJsonAdapterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.FileNotFoundException
import javax.annotation.CheckReturnValue
import org.apache.commons.compress.archivers.zip.ZipFile

/**
 * Wrapper for ZipFile exported by the aosp builds that contains test information.
 */
class AospBuildServerApkZipFile(
    private val zipFile: ZipFile
) : Closeable {
    // controls the access to the zip entries to ensure we don't read them in parallel
    private val zipFileMutex = Mutex()

    /**
     * Number of files fetched
     */
    var fetchedFileCount: Int = 0
        private set

    /**
     * Number of bytes fetched
     */
    var fetchedBytesLength: Long = 0
        private set
    val testRunConfigs by lazy {
        zipFile.entries.asSequence().filter {
            it.name.endsWith("AndroidTest.json")
        }.map { testConfigEntry ->
            zipFile.getInputStream(testConfigEntry).use {
                testConfigAdapter.fromJson(it.source().buffer())!!
            }
        }.toList()
    }

    /**
     * Reads the given [fileName] bytes from the zip file.
     * Note that there is a single mutex while reading files.
     * It will only read 1 file at a time.
     */
    suspend fun getFile(
        fileName: String
    ): ByteArray {
        val entry = zipFile.getEntry(fileName)
            ?: throw FileNotFoundException("No such file in the zip: $fileName")
        // it is important to use a mutex here so the callers can park their
        // coroutines instead of blocking the thread.
        return zipFileMutex.withLock {
            zipFile.getInputStream(
                entry
            ).use {
                it.readAllBytes()
            }.also {
                fetchedFileCount++
                fetchedBytesLength += it.size
            }
        }
    }

    override fun close() {
        zipFile.close()
    }

    companion object {
        private val testConfigAdapter by lazy {
            val moshi = Moshi.Builder()
                .add(MetadataKotlinJsonAdapterFactory())
                .build()
                .adapter(AospTestRunConfig::class.java)
        }

        /**
         * Creates a [BuildServerApkZipFile] for the given [testRun] from the given [buildService].
         */
        @CheckReturnValue
        fun createFrom(
            githubApi: GithubApi,
            testRun: TestRun
        ): AospBuildServerApkZipFile {
            val zipFile = buildService
                .fetchRemoteZipFile(
                    buildId = testRun.buildId,
                    artifactName = testRun.testZipPath,
                    target = testRun.target,
                    attemptId = testRun.attemptId
                )
            return BuildServerApkZipFile(zipFile)
        }
    }
}