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

package dev.androidx.ci.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Generates Kotlin models based on a discovery document.
 *
 * This is very similar to
 * https://github.com/googleapis/google-api-java-client-services/tree/master/clients/google-api-services-testing/v1
 * except it generates kotlin classes instead of subclasses of AbstractGoogleJsonClient
 * so that we can use them with Moshi.
 *
 * It also tries to get more clever about nullability from docs, which sometimes works but better
 * than nothing.
 * see: https://developers.google.com/discovery/v1/reference/apis
 */
internal class DiscoveryDocumentModelGenerator(
    /**
     * Output directory for sources
     */
    val outDir: File,
    /**
     * The discovery file url to fetch the models.
     * For firebase test lab: https://testing.googleapis.com/$discovery/rest?version=v1
     */
    val discoveryUrl: String,
    /**
     * The root package for generated classes
     */
    val pkg: String,
    /**
     * If true, classes will be generated with internal modifier.
     */
    val typeSpecModifiers: List<KModifier> = emptyList(),
) {
    fun generate() {
        val discoveryDoc = fetchDiscoveryDocument()
        val processor = SchemaProcessor(
            schemas = discoveryDoc.schemas.values,
            pkg = pkg
        )
        val typeSpecs = processor.process().map {
            it.addModifiers(typeSpecModifiers).build()
        }
        typeSpecs.forEach { typeSpec ->
            val fileSpec = FileSpec.builder(
                packageName = pkg,
                fileName = typeSpec.name!!
            )
                .addType(typeSpec)
                .indent("    ")
                .addAnnotation(
                    AnnotationSpec.builder(
                        Suppress::class
                    ).addMember("\"RedundantVisibilityModifier\"").build()
                ).build()
            fileSpec.writeTo(outDir)
        }
    }

    private fun fetchDiscoveryDocument(): DiscoveryDto {
        val client = OkHttpClient.Builder()
            .build()
        val request = Request.Builder()
            .url(discoveryUrl)
            .build()
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val discoveryAdapter = moshi.adapter(DiscoveryDto::class.java).lenient()
        return client.newCall(request).execute().use { response ->
            discoveryAdapter.fromJson(response.body!!.source())
        } ?: error("Cannot get discovery document")
    }
}
