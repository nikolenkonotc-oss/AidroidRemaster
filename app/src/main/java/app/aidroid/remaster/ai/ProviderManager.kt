package app.aidroid.remaster.ai

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.UUID

class ProviderManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "aidroid_provider_settings",
        Context.MODE_PRIVATE
    )
    private val credentials = SecureCredentialStore(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun providers(): List<ProviderConfig> = runCatching {
        json.decodeFromString<List<ProviderConfig>>(preferences.getString(KEY_PROVIDERS, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    @Synchronized
    fun saveProvider(config: ProviderConfig, apiKey: String) {
        validateBaseUrl(config)
        val clean = config.copy(
            name = config.name.trim().ifBlank { config.type.name },
            baseUrl = config.baseUrl.trim().trimEnd('/'),
            model = config.model.trim()
        )
        require(clean.model.isNotBlank()) { "Укажи идентификатор модели." }
        val updated = providers().filterNot { it.id == clean.id } + clean
        preferences.edit().putString(KEY_PROVIDERS, json.encodeToString(updated)).apply()
        if (apiKey.isNotBlank()) credentials.put(clean.id, apiKey.trim())
        if (activeProviderId() == null) setActiveProvider(clean.id)
    }

    @Synchronized
    fun removeProvider(providerId: String) {
        val updated = providers().filterNot { it.id == providerId }
        preferences.edit().putString(KEY_PROVIDERS, json.encodeToString(updated)).apply()
        credentials.delete(providerId)
        if (activeProviderId() == providerId) {
            setActiveProvider(updated.firstOrNull { it.enabled }?.id)
        }
        if (agentPreferences().imageProviderId == providerId) {
            saveAgentPreferences(agentPreferences().copy(imageProviderId = null))
        }
    }

    fun newProviderId(): String = UUID.randomUUID().toString()
    fun apiKey(providerId: String): String? = credentials.get(providerId)

    fun setToolCredential(toolId: String, value: String) {
        require(toolId.matches(Regex("[a-z0-9_-]{1,48}")))
        val storageKey = "tool:$toolId"
        if (value.isBlank()) credentials.delete(storageKey) else credentials.put(storageKey, value.trim())
    }

    fun toolCredential(toolId: String): String? {
        require(toolId.matches(Regex("[a-z0-9_-]{1,48}")))
        return credentials.get("tool:$toolId")
    }

    fun saveCodeRunnerUrl(value: String) {
        val clean = value.trim().trimEnd('/')
        if (clean.isNotBlank()) validateLocalOrSecureEndpoint(clean)
        saveAgentPreferences(agentPreferences().copy(codeRunnerUrl = clean))
    }

    private fun validateLocalOrSecureEndpoint(value: String) {
        val parsed = runCatching { URI(value) }
            .getOrElse { throw IllegalArgumentException("Проверь URL сервера выполнения кода.") }
        require((parsed.scheme == "https" || parsed.scheme == "http") && !parsed.host.isNullOrBlank()) {
            "URL сервера должен начинаться с https:// или http://."
        }
        if (parsed.scheme == "http") {
            val host = parsed.host.lowercase()
            val local = host == "localhost" || host == "127.0.0.1" || host == "::1" ||
                host.endsWith(".local") || host.startsWith("10.") || host.startsWith("192.168.") ||
                host.matches(Regex("172\\.(1[6-9]|2[0-9]|3[01])\\..*"))
            require(local) { "Внешний сервер выполнения кода должен использовать HTTPS." }
        }
    }

    fun activeProviderId(): String? = preferences.getString(KEY_ACTIVE_PROVIDER, null)

    fun activeProvider(): ProviderConfig? {
        val id = activeProviderId() ?: return null
        return providers().firstOrNull { it.id == id && it.enabled }
    }

    fun setActiveProvider(providerId: String?) {
        preferences.edit().putString(KEY_ACTIVE_PROVIDER, providerId).apply()
        val current = agentPreferences()
        saveAgentPreferences(current.copy(activeProviderId = providerId))
    }

    fun agentPreferences(): AgentPreferences {
        val stored = runCatching {
            json.decodeFromString<AgentPreferences>(
                preferences.getString(KEY_AGENT_PREFERENCES, "{}") ?: "{}"
            )
        }.getOrDefault(AgentPreferences(activeProviderId = activeProviderId()))
        val normalized = stored.copy(
            enabledTools = stored.enabledTools
                .filterNot { it == "email_draft" || it == "calendar_event" }
        )
        if (!preferences.getBoolean(KEY_TERMUX_SAFE_MIGRATED, false)) {
            val migrated = normalized.copy(
                enabledTools = (normalized.enabledTools + "termux_auto_safe").distinct()
            )
            preferences.edit()
                .putString(KEY_AGENT_PREFERENCES, json.encodeToString(migrated))
                .putBoolean(KEY_TERMUX_SAFE_MIGRATED, true)
                .apply()
            return migrated
        }
        return normalized
    }

    fun saveAgentPreferences(value: AgentPreferences) {
        preferences.edit()
            .putString(KEY_AGENT_PREFERENCES, json.encodeToString(value))
            .putString(KEY_ACTIVE_PROVIDER, value.activeProviderId)
            .apply()
    }

    private fun validateBaseUrl(config: ProviderConfig) {
        val parsed = runCatching { URI(config.baseUrl.trim()) }
            .getOrElse { throw IllegalArgumentException("Проверь адрес API.") }
        require(parsed.scheme == "https" || parsed.scheme == "http") { "Адрес должен начинаться с https:// или http://." }
        require(!parsed.host.isNullOrBlank()) { "Не удалось определить адрес сервера." }
        if (parsed.scheme == "http") {
            val host = parsed.host.lowercase()
            val local = host == "localhost" || host == "127.0.0.1" || host == "::1" ||
                host.endsWith(".local") || host.startsWith("10.") || host.startsWith("192.168.") ||
                host.matches(Regex("172\\.(1[6-9]|2[0-9]|3[01])\\..*")) ||
                host.startsWith("169.254.")
            require(local && config.type in setOf(ProviderType.OLLAMA, ProviderType.OPENAI_COMPATIBLE)) {
                "Незащищённый HTTP разрешён только для локального сервера в домашней сети. Для внешнего API используй HTTPS."
            }
        }
    }

    private companion object {
        const val KEY_PROVIDERS = "providers_v1"
        const val KEY_ACTIVE_PROVIDER = "active_provider"
        const val KEY_AGENT_PREFERENCES = "agent_preferences_v1"
        const val KEY_TERMUX_SAFE_MIGRATED = "termux_safe_migrated_v1"
    }
}
