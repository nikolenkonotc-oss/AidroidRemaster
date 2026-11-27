package app.aidroid.remaster.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * OpenAI Responses strict function calling requires every declared object property
 * to appear in "required", including fields that the executor treats as optional.
 * The model can use an empty string for an optional text value.
 */
internal object ToolSchemaNormalizer {
    fun forStrictFunction(parameters: JsonObject): JsonObject = normalizeObject(parameters, includeAdditionalProperties = true)

    fun forFunctionDeclaration(parameters: JsonObject): JsonObject =
        normalizeObject(parameters, includeAdditionalProperties = false)

    private fun normalizeElement(element: JsonElement, includeAdditionalProperties: Boolean): JsonElement = when (element) {
        is JsonObject -> normalizeObject(element, includeAdditionalProperties)
        is JsonArray -> JsonArray(element.map { normalizeElement(it, includeAdditionalProperties) })
        else -> element
    }

    private fun normalizeObject(schema: JsonObject, includeAdditionalProperties: Boolean): JsonObject {
        val properties = schema["properties"] as? JsonObject
        return buildJsonObject {
            schema.forEach { (key, value) ->
                when (key) {
                    "properties" -> {
                        put("properties", buildJsonObject {
                            properties?.forEach { (name, propertySchema) ->
                                put(name, normalizeElement(propertySchema, includeAdditionalProperties))
                            }
                        })
                    }
                    "required" -> {
                        if (properties != null) {
                            put("required", JsonArray(properties.keys.map(::JsonPrimitive)))
                        } else {
                            put(key, value)
                        }
                    }
                    "additionalProperties" -> if (includeAdditionalProperties) put(key, JsonPrimitive(false))
                    else -> put(key, normalizeElement(value, includeAdditionalProperties))
                }
            }
            if (properties != null) {
                if ("required" !in schema) {
                    put("required", JsonArray(properties.keys.map(::JsonPrimitive)))
                }
                if (includeAdditionalProperties && "additionalProperties" !in schema) {
                    put("additionalProperties", JsonPrimitive(false))
                }
            }
        }
    }
}
