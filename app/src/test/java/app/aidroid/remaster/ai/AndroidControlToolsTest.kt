package app.aidroid.remaster.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidControlToolsTest {
    @Test
    fun definitionsOnlyExposeEnabledAndroidTools() {
        val tools = AndroidControlTools.definitions(
            setOf("android_device_status", "android_set_media_volume")
        )

        assertEquals(
            listOf("android_device_status", "android_set_media_volume"),
            tools.map { it.name }
        )
    }

    @Test
    fun openingSettingsCreatesAnApprovalActionForAnAllowedSection() {
        val result = AndroidControlTools.prepare(
            "android_open_settings",
            buildJsonObject { put("section", "wifi") },
            chatId = 42L
        )

        assertEquals(AgentActionType.ANDROID_CONTROL, result.action?.type)
        assertEquals("open_settings", result.action?.subject)
        assertEquals("wifi", result.action?.body)
        assertEquals(42L, result.action?.chatId)
    }

    @Test
    fun openingAnUnlistedSettingsSectionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidControlTools.prepare(
                "android_open_settings",
                buildJsonObject { put("section", "unknown_intent") },
                chatId = 42L
            )
        }
    }

    @Test
    fun mediaVolumeRequiresApprovalAndKeepsRequestedPercent() {
        val result = AndroidControlTools.prepare(
            "android_set_media_volume",
            buildJsonObject { put("percent", 65) },
            chatId = 7L
        )

        assertNotNull(result.action)
        assertEquals(AgentActionType.ANDROID_CONTROL, result.action?.type)
        assertEquals("set_media_volume", result.action?.subject)
        assertEquals("65", result.action?.body)
    }

    @Test
    fun mediaVolumeRejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidControlTools.prepare(
                "android_set_media_volume",
                buildJsonObject { put("percent", 101) },
                chatId = 7L
            )
        }
    }
}
