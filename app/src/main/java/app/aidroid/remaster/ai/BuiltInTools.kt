package app.aidroid.remaster.ai

import kotlinx.serialization.json.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object BuiltInTools {
    private val calculatorSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("expression") {
                put("type", "string")
                put("description", "Математическое выражение с числами, скобками, +, -, *, / и ^.")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("expression")) }
        put("additionalProperties", false)
    }

    private val dateTimeSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { }
        putJsonArray("required") { }
        put("additionalProperties", false)
    }

    fun definitions(enabled: Set<String>): List<LlmTool> = buildList {
        if ("calculator" in enabled) {
            add(
                LlmTool(
                    name = "calculator",
                    description = "Вычисляет математическое выражение. Используй инструмент вместо устного подсчёта.",
                    parameters = calculatorSchema
                )
            )
        }
        if ("current_datetime" in enabled) {
            add(
                LlmTool(
                    name = "current_datetime",
                    description = "Возвращает точные текущие дату, время и часовой пояс устройства.",
                    parameters = dateTimeSchema
                )
            )
        }
    }

    fun execute(name: String, arguments: JsonObject): String = when (name) {
        "calculator" -> {
            val expression = arguments["expression"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("Не передано выражение.")
            require(expression.length <= 160) { "Слишком длинное выражение." }
            val result = MathExpressionParser(expression).parse()
            require(result.isFinite()) { "Результат не является конечным числом." }
            result.toString()
        }
        "current_datetime" -> ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
        else -> throw IllegalArgumentException("Инструмент '$name' не подключён.")
    }
}

/** Small arithmetic parser; no eval, scripting engine, reflection or external execution. */
private class MathExpressionParser(private val source: String) {
    private var position = 0

    fun parse(): Double {
        val value = parseExpression()
        skipSpaces()
        require(position == source.length) { "Не удалось разобрать выражение." }
        return value
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            skipSpaces()
            value = when (peek()) {
                '+' -> { position++; value + parseTerm() }
                '-' -> { position++; value - parseTerm() }
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parsePower()
        while (true) {
            skipSpaces()
            value = when (peek()) {
                '*' -> { position++; value * parsePower() }
                '/' -> {
                    position++
                    val divisor = parsePower()
                    require(divisor != 0.0) { "Деление на ноль." }
                    value / divisor
                }
                else -> return value
            }
        }
    }

    private fun parsePower(): Double {
        var value = parseUnary()
        skipSpaces()
        if (peek() == '^') {
            position++
            value = value.pow(parsePower())
        }
        return value
    }

    private fun parseUnary(): Double {
        skipSpaces()
        return when (peek()) {
            '+' -> { position++; parseUnary() }
            '-' -> { position++; -parseUnary() }
            '(' -> {
                position++
                val value = parseExpression()
                skipSpaces()
                require(peek() == ')') { "Не закрыта скобка." }
                position++
                value
            }
            else -> parseNumber()
        }
    }

    private fun parseNumber(): Double {
        skipSpaces()
        val start = position
        var decimalSeen = false
        while (position < source.length) {
            val c = source[position]
            when {
                c.isDigit() -> position++
                (c == '.' || c == ',') && !decimalSeen -> {
                    decimalSeen = true
                    position++
                }
                else -> break
            }
        }
        require(position > start) { "Ожидалось число." }
        return source.substring(start, position).replace(',', '.').toDouble()
    }

    private fun peek(): Char? = source.getOrNull(position)
    private fun skipSpaces() { while (source.getOrNull(position)?.isWhitespace() == true) position++ }

    private fun Double.pow(other: Double): Double = kotlin.math.pow(this, other)
}
