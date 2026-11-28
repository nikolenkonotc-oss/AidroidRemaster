package app.aidroid.remaster.ai

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Allowlisted Android controls.
 *
 * The agent can only execute the explicit actions defined here. A separate persistent
 * user preference may skip Aidroid's per-action dialog for these low-risk actions;
 * Android's own special-access screens and OS restrictions are never bypassed.
 */
object AndroidControlTools {
    private val settingsSections = linkedMapOf(
        "main" to "Главные настройки",
        "network" to "Сеть и подключения",
        "wifi" to "Wi-Fi",
        "bluetooth" to "Bluetooth",
        "data_usage" to "Использование данных",
        "display" to "Экран",
        "sound" to "Звук",
        "battery" to "Батарея и энергосбережение",
        "apps" to "Приложения",
        "notifications" to "Уведомления",
        "notification_access" to "Доступ к уведомлениям",
        "location" to "Местоположение",
        "privacy" to "Конфиденциальность",
        "security" to "Безопасность",
        "accessibility" to "Специальные возможности",
        "date_time" to "Дата и время",
        "storage" to "Хранилище",
        "language" to "Язык",
        "keyboard" to "Клавиатура и ввод",
        "accounts" to "Аккаунты и синхронизация",
        "default_apps" to "Приложения по умолчанию",
        "vpn" to "VPN",
        "cast" to "Трансляция экрана",
        "nfc" to "NFC",
        "write_settings" to "Разрешение на изменение системных параметров",
        "device_info" to "Информация об устройстве"
    )

    private val volumeStreams = linkedMapOf(
        "media" to AudioManager.STREAM_MUSIC,
        "ring" to AudioManager.STREAM_RING,
        "alarm" to AudioManager.STREAM_ALARM,
        "notification" to AudioManager.STREAM_NOTIFICATION,
        "system" to AudioManager.STREAM_SYSTEM
    )

    fun definitions(enabled: Set<String>): List<LlmTool> = buildList {
        if ("android_device_status" in enabled) {
            add(
                LlmTool(
                    name = "android_device_status",
                    description = "Локально получает модель телефона, версию Android, уровень и состояние батареи, текущие уровни громкости, яркость, тайм-аут экрана и состояние автоповорота. Ничего не меняет.",
                    parameters = emptyObjectSchema()
                )
            )
        }
        if ("android_open_settings" in enabled) {
            add(
                LlmTool(
                    name = "android_open_settings",
                    description = "Открывает один из разрешённых системных разделов Android. Используй только по запросу пользователя. Wi-Fi и Bluetooth включаются в системном интерфейсе, не прямым скрытым переключением.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("section") {
                                put("type", "string")
                                put("description", "Ключ известного раздела (wifi, display и т.д.) или полный Android Settings action, например android.settings.APP_SEARCH_SETTINGS. Открытие только экрана; серьёзные системные действия не выполняются.")
                            }
                        }
                        putJsonArray("required") { add(JsonPrimitive("section")) }
                        put("additionalProperties", false)
                    }
                )
            )
        }
        if ("android_open_settings_panel" in enabled) {
            add(
                LlmTool(
                    name = "android_open_settings_panel",
                    description = "Открывает компактную системную панель Android для Wi-Fi, подключений к интернету, громкости или NFC. Включение переключателя пользователь выполняет в этой системной панели.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("panel") {
                                put("type", "string")
                                put("description", "Какую системную панель открыть.")
                                put("enum", buildJsonArray {
                                    listOf("wifi", "internet", "volume", "nfc").forEach { add(JsonPrimitive(it)) }
                                })
                            }
                        }
                        putJsonArray("required") { add(JsonPrimitive("panel")) }
                        put("additionalProperties", false)
                    }
                )
            )
        }
        if ("android_set_media_volume" in enabled) {
            add(
                LlmTool(
                    name = "android_set_media_volume",
                    description = "Устанавливает громкость выбранного аудиопотока в процентах: media (мультимедиа), ring (звонок), alarm (будильник), notification (уведомления), system (системные звуки). stream необязателен и по умолчанию media.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("stream") {
                                put("type", "string")
                                put("enum", buildJsonArray {
                                    volumeStreams.keys.forEach { add(JsonPrimitive(it)) }
                                })
                            }
                            putJsonObject("percent") {
                                put("type", "integer")
                                put("description", "Желаемая громкость от 0 до 100 процентов.")
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
        if ("android_set_display_parameter" in enabled) {
            add(
                LlmTool(
                    name = "android_set_display_parameter",
                    description = "Меняет базовые параметры экрана. setting=brightness: value 5..100 процентов; screen_timeout: 15..3600 секунд; auto_brightness и auto_rotate: value 1 для включения или 0 для выключения. Для записи Android может потребоваться однократно выдать Aidroid специальное разрешение «Изменение системных настроек».",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("setting") {
                                put("type", "string")
                                put("enum", buildJsonArray {
                                    listOf("brightness", "screen_timeout", "auto_brightness", "auto_rotate")
                                        .forEach { add(JsonPrimitive(it)) }
                                })
                            }
                            putJsonObject("value") {
                                put("type", "integer")
                                put("minimum", 0)
                                put("maximum", 3600)
                            }
                        }
                        putJsonArray("required") {
                            add(JsonPrimitive("setting"))
                            add(JsonPrimitive("value"))
                        }
                        put("additionalProperties", false)
                    }
                )
            )
        }
    }

    fun prepare(name: String, arguments: JsonObject, chatId: Long): AgentToolResult =
        when (name) {
            "android_open_settings" -> {
                val section = arguments["section"]?.jsonPrimitive?.contentOrNull?.lowercase()
                    ?: throw IllegalArgumentException("Не указан раздел настроек Android.")
                val label = settingsSections[section] ?: customSettingsLabel(section)
                actionResult(
                    chatId = chatId,
                    title = "Открыть настройки Android: $label?",
                    subject = "open_settings",
                    body = section,
                    text = "Готовлю открытие раздела «$label»."
                )
            }
            "android_open_settings_panel" -> {
                val panel = arguments["panel"]?.jsonPrimitive?.contentOrNull?.lowercase()
                    ?: throw IllegalArgumentException("Не указана системная панель Android.")
                val label = when (panel) {
                    "wifi" -> "Wi-Fi"
                    "internet" -> "Подключения к интернету"
                    "volume" -> "Громкость"
                    "nfc" -> "NFC"
                    else -> throw IllegalArgumentException("Эта системная панель не разрешена.")
                }
                actionResult(
                    chatId = chatId,
                    title = "Открыть системную панель: $label?",
                    subject = "open_settings_panel",
                    body = panel,
                    text = "Готовлю системную панель «$label»."
                )
            }
            "android_set_media_volume" -> {
                val percent = arguments["percent"]?.jsonPrimitive?.intOrNull
                    ?: throw IllegalArgumentException("Укажи громкость целым числом от 0 до 100.")
                require(percent in 0..100) { "Громкость должна быть в диапазоне от 0 до 100 процентов." }
                val stream = arguments["stream"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "media"
                require(stream in volumeStreams) { "Неизвестный тип громкости." }
                val label = volumeLabel(stream)
                actionResult(
                    chatId = chatId,
                    title = "Изменить громкость «$label» на $percent%?",
                    subject = "set_audio_volume",
                    body = "$stream:$percent",
                    text = "Готовлю изменение громкости «$label» на $percent%."
                )
            }
            "android_set_display_parameter" -> {
                val setting = arguments["setting"]?.jsonPrimitive?.contentOrNull?.lowercase()
                    ?: throw IllegalArgumentException("Не указан параметр экрана.")
                val value = arguments["value"]?.jsonPrimitive?.intOrNull
                    ?: throw IllegalArgumentException("Значение параметра должно быть целым числом.")
                val label = validateDisplayParameter(setting, value)
                actionResult(
                    chatId = chatId,
                    title = "Изменить параметр экрана: $label?",
                    subject = "set_display_parameter",
                    body = "$setting:$value",
                    text = "Готовлю изменение параметра экрана «$label»."
                )
            }
            else -> throw IllegalArgumentException("Инструмент Android '$name' не зарегистрирован.")
        }

    fun isEligibleForAutonomousExecution(action: AgentExternalAction): Boolean =
        action.type == AgentActionType.ANDROID_CONTROL &&
            action.subject in setOf(
                "open_settings",
                "open_settings_panel",
                "set_audio_volume",
                "set_display_parameter"
            )

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
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val brightness = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull()?.let { "${(it * 100f / 255f).roundToInt()}%" } ?: "недоступна"
        val timeout = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) / 1000
        }.getOrNull()?.let { "$it сек." } ?: "недоступен"
        val autoRotate = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
        }.getOrNull()?.let { if (it) "включён" else "выключен" } ?: "недоступен"

        return buildString {
            appendLine("Состояние Android-устройства")
            appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL}".trim())
            appendLine("Версия Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            if (Build.VERSION.SECURITY_PATCH.isNotBlank()) {
                appendLine("Патч безопасности: ${Build.VERSION.SECURITY_PATCH}")
            }
            appendLine("Батарея: $batteryPercent")
            appendLine("Состояние зарядки: $batteryStatus")
            appendLine("Яркость экрана: $brightness")
            appendLine("Тайм-аут экрана: $timeout")
            appendLine("Автоповорот: $autoRotate")
            if (audio != null) {
                volumeStreams.forEach { (key, stream) ->
                    val max = audio.getStreamMaxVolume(stream)
                    val current = audio.getStreamVolume(stream)
                    val percent = if (max > 0) (current * 100f / max).roundToInt() else 0
                    appendLine("Громкость ${volumeLabel(key)}: $percent%")
                }
            }
        }
    }

    fun execute(context: Context, action: AgentExternalAction): AgentToolResult {
        require(action.type == AgentActionType.ANDROID_CONTROL) {
            "Передано действие другого типа в обработчик Android."
        }
        return when (action.subject) {
            "open_settings" -> openSettings(context, action.body)
            "open_settings_panel" -> openSettingsPanel(context, action.body)
            "set_audio_volume" -> setAudioVolume(context, action.body)
            "set_display_parameter" -> setDisplayParameter(context, action.body)
            else -> throw IllegalArgumentException("Неизвестное действие Android.")
        }
    }

    private fun actionResult(
        chatId: Long,
        title: String,
        subject: String,
        body: String,
        text: String
    ) = AgentToolResult(
        text = text,
        action = AgentExternalAction(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            type = AgentActionType.ANDROID_CONTROL,
            title = title,
            subject = subject,
            body = body
        )
    )

    private fun settingsIntent(context: Context, section: String): Intent {
        val action = if (section.startsWith("android.settings.")) section else when (section) {
            "main" -> "android.settings.SETTINGS"
            "network" -> "android.settings.WIRELESS_SETTINGS"
            "wifi" -> "android.settings.WIFI_SETTINGS"
            "bluetooth" -> "android.settings.BLUETOOTH_SETTINGS"
            "data_usage" -> "android.settings.DATA_USAGE_SETTINGS"
            "display" -> "android.settings.DISPLAY_SETTINGS"
            "sound" -> "android.settings.SOUND_SETTINGS"
            "battery" -> "android.settings.BATTERY_SAVER_SETTINGS"
            "apps" -> "android.settings.APPLICATION_SETTINGS"
            "notifications" -> "android.settings.NOTIFICATION_SETTINGS"
            "notification_access" -> "android.settings.NOTIFICATION_LISTENER_SETTINGS"
            "location" -> "android.settings.LOCATION_SOURCE_SETTINGS"
            "privacy" -> "android.settings.PRIVACY_SETTINGS"
            "security" -> "android.settings.SECURITY_SETTINGS"
            "accessibility" -> "android.settings.ACCESSIBILITY_SETTINGS"
            "date_time" -> "android.settings.DATE_SETTINGS"
            "storage" -> "android.settings.INTERNAL_STORAGE_SETTINGS"
            "language" -> "android.settings.LOCALE_SETTINGS"
            "keyboard" -> "android.settings.INPUT_METHOD_SETTINGS"
            "accounts" -> "android.settings.SYNC_SETTINGS"
            "default_apps" -> "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"
            "vpn" -> "android.settings.VPN_SETTINGS"
            "cast" -> "android.settings.CAST_SETTINGS"
            "nfc" -> "android.settings.NFC_SETTINGS"
            "write_settings" -> Settings.ACTION_MANAGE_WRITE_SETTINGS
            "device_info" -> "android.settings.DEVICE_INFO_SETTINGS"
            else -> throw IllegalArgumentException("Этот раздел системных настроек не разрешён.")
        }
        return Intent(action).apply {
            if (section == "write_settings") data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }


    private fun customSettingsLabel(action: String): String {
        require(action.matches(Regex("android\\.settings\\.[A-Z0-9_]{2,100}"))) {
            "Укажи известный раздел или полный Android Settings action."
        }
        val suffix = action.substringAfterLast('.')
        val blockedTokens = setOf(
            "FACTORY_RESET",
            "MASTER_CLEAR",
            "MANAGE_DEVICE_ADMINS",
            "DEVICE_ADMIN",
            "MANAGE_ALL_FILES_ACCESS_PERMISSION",
            "MANAGE_APP_ALL_FILES_ACCESS_PERMISSION",
            "MANAGE_UNKNOWN_APP_SOURCES",
            "REQUEST_INSTALL_PACKAGES",
            "CREDENTIAL",
            "ENCRYPTION"
        )
        require(blockedTokens.none { suffix.contains(it) }) {
            "Этот системный экран относится к защищённым или критичным действиям и пока не открывается агентом."
        }
        return suffix.lowercase().replace('_', ' ')
    }

    private fun openSettings(context: Context, section: String): AgentToolResult {
        val label = settingsSections[section] ?: customSettingsLabel(section)
        val intent = settingsIntent(context, section)
        return try {
            context.startActivity(intent)
            AgentToolResult("Открыт раздел настроек Android «$label». Дальнейшие действия выполняются в системном интерфейсе.")
        } catch (_: ActivityNotFoundException) {
            if (section == "main") throw IllegalStateException("Системные настройки недоступны на этом устройстве.")
            context.startActivity(Intent("android.settings.SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            AgentToolResult("Раздел «$label» недоступен на этой версии Android. Открыты главные настройки.")
        }
    }

    private fun openSettingsPanel(context: Context, panel: String): AgentToolResult {
        val panelAction = when (panel) {
            "wifi" -> "android.settings.panel.action.WIFI"
            "internet" -> "android.settings.panel.action.INTERNET_CONNECTIVITY"
            "volume" -> "android.settings.panel.action.VOLUME"
            "nfc" -> "android.settings.panel.action.NFC"
            else -> throw IllegalArgumentException("Эта системная панель не разрешена.")
        }
        val fallbackSection = when (panel) {
            "wifi" -> "wifi"
            "internet" -> "network"
            "volume" -> "sound"
            "nfc" -> "nfc"
            else -> "main"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                context.startActivity(Intent(panelAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return AgentToolResult("Открыта системная панель Android «$panel». Переключатель можно изменить в ней.")
            } catch (_: ActivityNotFoundException) {
                // Older OEM builds may omit an optional panel. Use its regular Settings page.
            }
        }
        return openSettings(context, fallbackSection)
    }

    private fun setAudioVolume(context: Context, rawValue: String): AgentToolResult {
        val parts = rawValue.split(":", limit = 2)
        val streamName = parts.getOrNull(0)?.lowercase().orEmpty()
        val percent = parts.getOrNull(1)?.toIntOrNull()
            ?: throw IllegalArgumentException("Не удалось прочитать уровень громкости.")
        val stream = volumeStreams[streamName]
            ?: throw IllegalArgumentException("Неизвестный тип громкости.")
        require(percent in 0..100) { "Громкость должна быть в диапазоне от 0 до 100 процентов." }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: throw IllegalStateException("Служба управления звуком недоступна.")
        require(!audio.isVolumeFixed) { "На этом устройстве системная громкость зафиксирована." }
        val maxVolume = audio.getStreamMaxVolume(stream)
        require(maxVolume > 0) { "Системный уровень громкости недоступен." }
        val targetVolume = (percent / 100f * maxVolume).roundToInt().coerceIn(0, maxVolume)
        audio.setStreamVolume(stream, targetVolume, AudioManager.FLAG_SHOW_UI)
        val actualPercent = (targetVolume * 100f / maxVolume).roundToInt()
        return AgentToolResult(
            "Громкость «${volumeLabel(streamName)}» установлена примерно на $actualPercent% ($targetVolume из $maxVolume)."
        )
    }

    private fun setDisplayParameter(context: Context, rawValue: String): AgentToolResult {
        val parts = rawValue.split(":", limit = 2)
        val setting = parts.getOrNull(0)?.lowercase().orEmpty()
        val value = parts.getOrNull(1)?.toIntOrNull()
            ?: throw IllegalArgumentException("Не удалось прочитать значение параметра экрана.")
        val label = validateDisplayParameter(setting, value)

        if (!Settings.System.canWrite(context)) {
            val permissionIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(permissionIntent)
            } catch (_: ActivityNotFoundException) {
                throw IllegalStateException("Android не смог открыть экран специального доступа «Изменение системных настроек».")
            }
            return AgentToolResult(
                "Android открыл специальный доступ для Aidroid. Разреши приложению изменять системные настройки, затем повтори команду. Это системное разрешение выдаётся только пользователем."
            )
        }

        val resolver = context.contentResolver
        val success = when (setting) {
            "brightness" -> {
                val manual = Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                val brightness = Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    (value * 255f / 100f).roundToInt().coerceIn(1, 255)
                )
                manual && brightness
            }
            "screen_timeout" -> Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                value * 1000
            )
            "auto_brightness" -> Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (value == 1) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            "auto_rotate" -> Settings.System.putInt(
                resolver,
                Settings.System.ACCELEROMETER_ROTATION,
                value
            )
            else -> false
        }
        check(success) { "Android не принял изменение параметра «$label»." }
        return AgentToolResult("Параметр «$label» изменён.")
    }

    private fun validateDisplayParameter(setting: String, value: Int): String = when (setting) {
        "brightness" -> {
            require(value in 5..100) { "Яркость можно установить в диапазоне от 5 до 100 процентов." }
            "Яркость · $value%"
        }
        "screen_timeout" -> {
            require(value in 15..3600) { "Тайм-аут экрана должен быть от 15 до 3600 секунд." }
            "Тайм-аут экрана · $value сек."
        }
        "auto_brightness" -> {
            require(value in 0..1) { "Для автоподстройки яркости используй 1 (вкл.) или 0 (выкл.)." }
            "Автояркость · ${if (value == 1) "вкл." else "выкл."}"
        }
        "auto_rotate" -> {
            require(value in 0..1) { "Для автоповорота используй 1 (вкл.) или 0 (выкл.)." }
            "Автоповорот · ${if (value == 1) "вкл." else "выкл."}"
        }
        else -> throw IllegalArgumentException("Этот параметр экрана не разрешён.")
    }

    private fun volumeLabel(stream: String): String = when (stream) {
        "media" -> "мультимедиа"
        "ring" -> "звонок"
        "alarm" -> "будильник"
        "notification" -> "уведомления"
        "system" -> "системные звуки"
        else -> stream
    }

    private fun emptyObjectSchema() = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { }
        putJsonArray("required") { }
        put("additionalProperties", false)
    }
}
