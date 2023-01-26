package dev.androidx.ci.codegen

import com.google.common.truth.Truth.assertThat
import okio.Buffer
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CodeGenTest {
    @get:Rule
    val tmpDir = TemporaryFolder()

    @Test
    fun generateCode() {
        val firstOut = generate("""
            {
              "schemas": {
                "MyClass": {
                  "id": "MyClass",
                  "type": "object",
                  "properties": {
                    "aProp": {
                      "type": "integer"
                    },
                    "bProp": {
                      "type": "string"
                    }
                  }
                }
              }
            } 
        """.trimIndent())
        val secondOut = generate("""
            {
              "schemas": {
                "MyClass": {
                  "id": "MyClass",
                  "type": "object",
                  "properties": {
                    "bProp": {
                      "type": "string"
                    },
                    "aProp": {
                      "type": "integer"
                    }
                  }
                }
              }
            } 
        """.trimIndent())

        assertThat(
            firstOut.readText(Charsets.UTF_8)
        ).isEqualTo(secondOut.readText(Charsets.UTF_8))
    }

    private fun generate(
        @Language("json")
        json: String
    ): File {
        val outDir = tmpDir.newFolder()
        val buffer = Buffer()
        buffer.writeUtf8(json)
        val generator = DiscoveryDocumentModelGenerator(
            outDir = outDir,
            readDiscoverySource = { block ->
                block(buffer)
            },
            pkg = "foo.bar",
        )
        generator.generate()
        return outDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.single()
    }
}