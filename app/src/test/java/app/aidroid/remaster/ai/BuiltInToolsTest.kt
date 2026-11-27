package app.aidroid.remaster.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BuiltInToolsTest {
    @Test
    fun calculatorHonorsOperatorPrecedence() {
        val result = BuiltInTools.execute(
            "calculator",
            buildJsonObject { put("expression", "2 + 3 * 4") }
        )
        assertEquals("14.0", result)
    }

    @Test
    fun calculatorSupportsParenthesesAndPowers() {
        val result = BuiltInTools.execute(
            "calculator",
            buildJsonObject { put("expression", "(2 + 3) ^ 2") }
        )
        assertEquals("25.0", result)
    }

    @Test
    fun calculatorRejectsDivisionByZero() {
        assertThrows(IllegalArgumentException::class.java) {
            BuiltInTools.execute(
                "calculator",
                buildJsonObject { put("expression", "9 / (3 - 3)") }
            )
        }
    }

    @Test
    fun calculatorRejectsTrailingCodeOrTokens() {
        assertThrows(IllegalArgumentException::class.java) {
            BuiltInTools.execute(
                "calculator",
                buildJsonObject { put("expression", "1 + 2 garbage") }
            )
        }
    }

    @Test
    fun currentDateTimeToolReturnsAnIsoTimestamp() {
        val result = BuiltInTools.execute("current_datetime", buildJsonObject { })
        assertEquals(true, result.contains("T"))
    }
}
