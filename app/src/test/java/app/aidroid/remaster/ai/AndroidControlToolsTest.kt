package app.aidroid.remaster.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidControlToolsTest {
    @Test
    fun definitionsOnlyExposeEnabledAndroidTools() {
        val tools = AndroidControlTools.definitions(
            setOf("android_device_status", "android_set_media_volume", "android_set_display_parameter")
        )

        assertEquals(
            listOf(
                "android_device_status",
                "android_set_media_volume",
                "android_set_display_parameter"
            ),
            tools.map { it.name }
        )
    }

    @Test
    fun openingSettingsCreatesAnActionForAnAllowedSection() {
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
    fun openingAValidGenericAndroidSettingsActionIsAllowed() {
        val result = AndroidControlTools.prepare(
            "android_open_settings",
            buildJsonObject { put("section", "android.settings.APP_SEARCH_SETTINGS") },
            chatId = 42L
        )

        assertEquals("open_settings", result.action?.subject)
        assertEquals("android.settings.APP_SEARCH_SETTINGS", result.action?.body)
    }

    @Test
    fun genericAndroidSettingsActionIsNormalizedWhenInputIsLowercase() {
        val result = AndroidControlTools.prepare(
            "android_open_settings",
            buildJsonObject { put("section", "android.settings.app_search_settings") },
            chatId = 42L
        )

        assertEquals("android.settings.APP_SEARCH_SETTINGS", result.action?.body)
    }

    @Test
    fun openingCriticalFactoryResetSettingsIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidControlTools.prepare(
                "android_open_settings",
                buildJsonObject { put("section", "android.settings.FACTORY_RESET") },
                chatId = 42L
            )
        }
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
    fun wifiPanelCreatesAnAllowlistedSystemPanelAction() {
        val result = AndroidControlTools.prepare(
            "android_open_settings_panel",
            buildJsonObject { put("panel", "wifi") },
            chatId = 7L
        )

        assertEquals("open_settings_panel", result.action?.subject)
        assertEquals("wifi", result.action?.body)
        assertTrue(AndroidControlTools.isEligibleForAutonomousExecution(result.action!!))
    }

    @Test
    fun unknownPanelIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidControlTools.prepare(
                "android_open_settings_panel",
                buildJsonObject { put("panel", "arbitrary_intent") },
                chatId = 7L
            )
        }
    }

    @Test
    fun audioVolumeSupportsSelectedStreamAndKeepsRequestedPercent() {
        val result = AndroidControlTools.prepare(
            "android_set_media_volume",
            buildJsonObject {
                put("stream", "alarm")
                put("percent", 65)
            },
            chatId = 7L
        )

        assertNotNull(result.action)
        assertEquals(AgentActionType.ANDROID_CONTROL, result.action?.type)
        assertEquals("set_audio_volume", result.action?.subject)
        assertEquals("alarm:65", result.action?.body)
        assertTrue(AndroidControlTools.isEligibleForAutonomousExecution(result.action!!))
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

    @Test
    fun audioVolumeRejectsUnknownStreams() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidControlTools.prepare(
                "android_set_media_volume",
                buildJsonObject {
                    put("stream", "private")
                    put("percent", 50)
                },
                chatId = 7L
            )
        }
    }

    @Test
    fun displayBrightnessActionValidatesSafeRange() {
        val result = AndroidControlTools.prepare(
            "android_set_display_parameter",
            buildJsonObject {
                put("setting", "brightness")
                put("value", 65)
            },
            chatId = 7L
        )

        assertEquals("set_display_parameter", result.action?.subject)
        assertEquals("brightness:65", result.action?.body)
        assertTrue(AndroidControlTools.isEligibleForAutonomousExecution(result.action!!))
    }

    @Test
    fun displayBrightnessRejectsValuesThatCouldBlankTheScreen() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidControlTools.prepare(
                "android_set_display_parameter",
                buildJsonObject {
                    put("setting", "brightness")
                    put("value", 0)
                },
                chatId = 7L
            )
        }
    }

    @Test
    fun displayTimeoutRejectsUnboundedValues() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidControlTools.prepare(
                "android_set_display_parameter",
                buildJsonObject {
                    put("setting", "screen_timeout")
                    put("value", 3601)
                },
                chatId = 7L
            )
        }
    }

    @Test
    fun persistentAndroidPermissionCannotApproveOtherActionTypes() {
        val codeAction = AgentExternalAction(
            id = "test",
            chatId = 7L,
            type = AgentActionType.CODE_EXECUTION,
            title = "Run code",
            subject = "python"
        )

        assertFalse(AndroidControlTools.isEligibleForAutonomousExecution(codeAction))
    }
}
