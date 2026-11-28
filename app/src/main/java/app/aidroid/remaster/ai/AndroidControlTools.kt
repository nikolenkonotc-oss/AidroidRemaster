package app.aidroid.remaster.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Small, allowlisted Android controls.
 *
 * Reading device status is local and read-only. Opening system settings and changing
 * media volume are represented as AgentExternalAction so AgentRunner always asks
 * the user for approval before executing them.
 */
object AndroidControlTools {
    private val settingsSections = linkedMapOf(
        "main" to "Главные настройки",
        "wifi" to "Wi-Fi",
        "bluetooth" to "Bluetooth",
        "display" to "Экран",
        "sound" to "Звук",
        "battery" to "Батарея и энергосбережение",
        "apps" to "Приложения",
        "notifications" to "Уведомления",
        "location" to "Местоположение",
        "accessibility" to "Специальные возможности",
        "date_time" to "Дата и время",
        "storage" to "Хранилище"
    )

    fun definitions(enabled: Set<String>): List<LlmTool> = buildList {
        if ("android_device_status" in enabled) {
            add(
                LlmTool(
                    name = "android_device_status",
                    description = "Локально получает базовую информацию об устройстве: производитель, модель, версия Android, уровень и состояние батареи. Ничего не меняет.",
                    parameters = emptyObjectSchema()
                )
            )
        }
        if ("android_open_settings" in enabled) {
            add(
                LlmTool(
                    name = "android_open_settings",
                    description = "После подтверждения пользователем открывает один из разрешённых системных разделов Android. Самостоятельно включать или выключать Wi-Fi и Bluetooth этот инструмент не может.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("section") {
                                put("type", "string")
                                put("description", "Раздел системных настроек для открытия.")
                                put("enum", buildJsonArray {
                                    settingsSections.keys.forEach { add(JsonPrimitive(it)) }
                                })
                            }
                        }
                        putJsonArray("required") { add(JsonPrimitive("section")) }
                        put("additionalProperties", false)
                    }
                )
            )
        }
        if ("android_set_media_volume" in enabled) {
            add(
                LlmTool(
                    name = "android_set_media_volume",
                    description = "После отдельного подтверждения устанавливает громкость мультимедиа от 0 до 100 процентов. Не меняет громкость звонков и будильников.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("percent") {
                                put("type", "integer")
                                put("description", "Желаемая громкость мультимедиа в процентах.")
                                put("minimum", 0)
                                put("maximum", 100)
                            }
                        }
                        putJsonArray("required") { add(JsonPrimitive("percent")) }
                        put("additionalProperties", false)
                    }
                )
            )
        }
    }

    fun prepare(name: String, arguments: JsonObject, chatId: Long): AgentToolResult =
        when (name) {
            "android_open_settings" -> {
                val section = arguments["section"]?.jsonPrimitive?.contentOrNull
                    ?.lowercase()
                    ?: throw IllegalArgumentException("Не указан раздел настроек Android.")
                val label = settingsSections[section]
                    ?: throw IllegalArgumentException("Этот раздел системных настроек не разрешён.")
                AgentToolResult(
                    text = "Ожидаю подтверждения открытия раздела «$label».",
                    action = AgentExternalAction(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        type = AgentActionType.ANDROID_CONTROL,
                        title = "Открыть настройки Android: $label?",
                        subject = "open_settings",
                        body = section
                    )
                )
            }
            "android_set_media_volume" -> {
                val percent = arguments["percent"]?.jsonPrimitive?.intOrNull
                    ?: throw IllegalArgumentException("Укажи громкость целым числом от 0 до 100.")
                require(percent in 0..100) { "Громкость должна быть в диапазоне от 0 до 100 процентов." }
                AgentToolResult(
                    text = "Ожидаю подтверждения установки громкости мультимедиа на $percent%.",
                    action = AgentExternalAction(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        type = AgentActionType.ANDROID_CONTROL,
                        title = "Изменить громкость мультимедиа на $percent%?",
                        subject = "set_media_volume",
                        body = percent.toString()
                    )
                )
            }
            else -> throw IllegalArgumentException("Инструмент Android '$name' не зарегистрирован.")
        }

    fun readDeviceStatus(context: Context): String {
        @Suppress("DEPRECATION")
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) {
            "${(level * 100f / scale).roundToInt()}%"
        } else {
            "недоступно"
        }
        val batteryStatus = when (
            battery?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        ) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "заряжается"
            BatteryManager.BATTERY_STATUS_FULL -> "заряжена"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "не заряжается"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "не заряжается"
            else -> "неизвестно"
        }

        return buildString {
            appendLine("Состояние Android-устройства")
            appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}".trim())
            appendLine("Версия Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            if (Build.VERSION.SECURITY_PATCH.isNotBlank()) {
                appendLine("Патч безопасности: ${Build.VERSION.SECURITY_PATCH}")
            }
            appendLine("Батарея: $batteryPercent")
            append("Состояние зарядки: $batteryStatus")
        }
    }

    fun execute(context: Context, action: AgentExternalAction): AgentToolResult {
        require(action.type == AgentActionType.ANDROID_CONTROL) {
            "Передано действие другого типа в обработчик Android."
        }
        return when (action.subject) {
            "open_settings" -> openSettings(context, action.body)
            "set_media_volume" -> setMediaVolume(context, action.body)
            else -> throw IllegalArgumentException("Неизвестное действие Android.")
        }
    }

    private fun openSettings(context: Context, section: String): AgentToolResult {
        val label = settingsSections[section]
            ?: throw IllegalArgumentException("Этот раздел системных настроек не разрешён.")
        val action = when (section) {
            "main" -> Settings.ACTION_SETTINGS
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "notifications" -> Settings.ACTION_NOTIFICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "date_time" -> Settings.ACTION_DATE_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            else -> throw IllegalArgumentException("Этот раздел системных настроек не разрешён.")
        }
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return AgentToolResult("Открыт раздел настроек Android «$label». Изменения выполняются пользователем в системном интерфейсе.")
    }

    private fun setMediaVolume(context: Context, rawPercent: String): AgentToolResult {
        val percent = rawPercent.toIntOrNull()
            ?: throw IllegalArgumentException("Не удалось прочитать уровень громкости.")
        require(percent in 0..100) { "Громкость должна быть в диапазоне от 0 до 100 процентов." }

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: throw IllegalStateException("Служба управления звуком недоступна.")
        require(!audio.isVolumeFixed) { "На этом устройстве системная громкость зафиксирована." }

        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        require(maxVolume > 0) { "Системный уровень громкости недоступен." }
        val targetVolume = (percent / 100f * maxVolume).roundToInt().coerceIn(0, maxVolume)
        audio.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            targetVolume,
            AudioManager.FLAG_SHOW_UI
        )
        val actualPercent = (targetVolume * 100f / maxVolume).roundToInt()
        return AgentToolResult(
            "Громкость мультимедиа установлена примерно на $actualPercent% ($targetVolume из $maxVolume). Громкость звонков и будильников не изменялась."
        )
    }

    private fun emptyObjectSchema() = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { }
        putJsonArray("required") { }
        put("additionalProperties", false)
    }
}
