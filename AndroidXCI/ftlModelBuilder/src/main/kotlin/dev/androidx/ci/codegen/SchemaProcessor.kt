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

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import java.util.Locale

/**
 * Converts schemas into KotlinPoet's TypeSpecs with some hand tailored customizations for enums and
 * nullability.
 */
internal class SchemaProcessor(
    val schemas: Collection<SchemaDto>,
    val pkg: String,
) {
    fun process(): List<TypeSpec.Builder> {
        return schemas.filter {
            it.isObject()
        }.map { schemaDto ->
            schemaDto.toTypeSpec()
        }
    }

    private fun SchemaDto.toTypeSpec(): TypeSpec.Builder {
        fun PropertyDto.enums(
            name: String
        ): List<Triple<PropertyDto, String, List<String>>> {
            return if (enum != null && enum.isNotEmpty()) {
                listOf(
                    Triple(this, name, enum)
                )
            } else {
                emptyList()
            } + items?.enums("${name}Item").orEmpty()
        }
        val enumSpecs = this.properties?.flatMap { propertyEntry ->
            propertyEntry.value.enums(propertyEntry.key)
        }?.map { (propertyDto, name, values) ->
            // build an enum for this
            val enumClassName = ClassName(
                packageName = pkg,
                simpleNames = arrayOf(id, name.capitalize(Locale.US))
            )
            val enumSpec = TypeSpec.enumBuilder(enumClassName)
            values.forEachIndexed { index, enumValue ->
                enumSpec.addEnumConstant(
                    name = enumValue,
                    typeSpec = TypeSpec.anonymousClassBuilder().addKdoc(
                        propertyDto.enumDescriptions?.getOrNull(index)?.sanitize() ?: ""
                    ).build()
                )
            }
            enumSpec.build()
        } ?: emptyList()
        val propertySpecs = this.properties?.map { (name, propertyDto) ->
            propertyDto.toPropertySpec(
                schema = this,
                name = name
            )
        } ?: emptyList()
        val constructorSpec = FunSpec.constructorBuilder()
        propertySpecs.forEach { propSpec ->
            val paramSpec = ParameterSpec.builder(
                name = propSpec.name,
                type = propSpec.type
            )
            if (propSpec.type.isNullable) {
                paramSpec.defaultValue("null")
            }
            constructorSpec.addParameter(
                paramSpec.build()
            )
        }
        val typeSpec = TypeSpec.classBuilder(
            className = ClassName(pkg, id)
        )
        if (propertySpecs.isNotEmpty()) {
            typeSpec.addModifiers(KModifier.DATA)
        }
        typeSpec.primaryConstructor(constructorSpec.build())
        typeSpec.addProperties(
            propertySpecs
        )
        typeSpec.addTypes(enumSpecs)
        typeSpec.addKdoc(
            description?.sanitize() ?: ""
        )
        return typeSpec
    }

    private fun PropertyDto.toPropertySpec(
        schema: SchemaDto,
        name: String
    ): PropertySpec {
        return PropertySpec.builder(
            name = name,
            type = toTypeName(
                schema = schema,
                name = name,
            )
        ).addKdoc(
            description?.sanitize() ?: ""
        ).initializer(name)
            .build()
    }

    private fun PropertyDto.toTypeName(
        schema: SchemaDto,
        name: String,
    ): TypeName {
        val isEnum = enum != null && enum.isNotEmpty()
        val typeName = if (isEnum) {
            ClassName(
                packageName = pkg,
                schema.id,
                name.capitalize(Locale.US)
            )
        } else when (type) {
            // https://developers.google.com/discovery/v1/type-format
            "any" -> ANY
            "array" -> {
                val componentType = this.items ?: error("no component type for $this")
                LIST.parameterizedBy(
                    componentType.toTypeName(
                        schema = schema,
                        name = "${name}Item"
                    ).copy(nullable = false)
                )
            }
            "boolean" -> BOOLEAN
            "integer" -> INT
            "string" -> STRING
            "number" -> when (format) {
                "double" -> DOUBLE
                "float" -> FLOAT
                else -> error("invalid format: $format")
            }
            "object" -> ANY
            null -> when (ref) {
                null -> error("no ref or type for $this")
                else -> ClassName(pkg, ref)
            }
            else -> error("invalid type: $type")
        }
        return typeName.copy(
            nullable = isNullable(name)
        )
    }

    private fun String.sanitize() = replace("%", "%%")
}

/**
 * There is no nullability information in discovery documents.
 * Instead we infer from:
 * property name (id)
 * documentation (if it starts with Required)
 */
internal fun PropertyDto.isNullable(name: String): Boolean {
    return name != "id" && description?.startsWith("Required.") != true
}
