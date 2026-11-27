package app.aidroid.remaster.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolSchemaNormalizerTest {
    @Test
    fun addsEveryPropertyToRequiredForStrictWebSearchSchema() {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") { put("type", "string") }
                putJsonObject("freshness") {
                    put("type", "string")
                    putJsonArray("enum") {
                        add(kotlinx.serialization.json.JsonPrimitive("any"))
                        add(kotlinx.serialization.json.JsonPrimitive("day"))
                    }
                }
            }
            putJsonArray("required") {
                add(kotlinx.serialization.json.JsonPrimitive("query"))
            }
            put("additionalProperties", false)
        }

        val normalized = ToolSchemaNormalizer.forStrictFunction(schema)
        val required = normalized["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()

        assertEquals(setOf("query", "freshness"), required)
        assertFalse(normalized["additionalProperties"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(2, normalized["properties"]!!.jsonObject.size)
    }

    @Test
    fun geminiFunctionDeclarationOmitsAdditionalPropertiesButRequiresAllProperties() {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") { put("type", "string") }
                putJsonObject("freshness") { put("type", "string") }
            }
            putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("query")) }
            put("additionalProperties", false)
        }

        val normalized = ToolSchemaNormalizer.forFunctionDeclaration(schema)

        assertEquals(
            setOf("query", "freshness"),
            normalized["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        )
        assertFalse("additionalProperties" in normalized)
        assertFalse("additionalProperties" in normalized["properties"]!!.jsonObject["query"]!!.jsonObject)
    }

    @Test
    fun normalizesNestedObjectsAndForcesAdditionalPropertiesOff() {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("options") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("language") { put("type", "string") }
                        putJsonObject("stdin") { put("type", "string") }
                    }
                    putJsonArray("required") {
                        add(kotlinx.serialization.json.JsonPrimitive("language"))
                    }
                    put("additionalProperties", true)
                }
            }
            putJsonArray("required") { }
        }

        val normalized = ToolSchemaNormalizer.forStrictFunction(schema)
        val outerRequired = normalized["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        val nested = normalized["properties"]!!.jsonObject["options"]!!.jsonObject
        val nestedRequired = nested["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()

        assertEquals(listOf("options"), outerRequired)
        assertEquals(setOf("language", "stdin"), nestedRequired)
        assertTrue(!nested["additionalProperties"]!!.jsonPrimitive.content.toBoolean())
    }
}
