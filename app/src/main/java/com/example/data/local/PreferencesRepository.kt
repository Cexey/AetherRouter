package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.CryptoManager
import com.example.data.model.AppPreferences
import com.example.data.model.AppearanceConfig
import com.example.data.model.CreatorProfile
import com.example.data.model.LinkOpenBehavior
import com.example.data.model.ModelPickerMemory
import com.example.data.model.ModelSortMode
import com.example.data.model.ThemeMode
import com.example.data.model.WebSearchEngine
import com.example.data.model.WebSearchMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aetherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aetherrouter_prefs",
)

class PreferencesRepository(
    private val context: Context,
    private val cryptoManager: CryptoManager,
) {
    private object Keys {
        val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_openrouter_api_key")

        // Appearance, Typography & Zoom
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val CUSTOM_ACCENT_HEX = stringPreferencesKey("custom_accent_hex")
        val USER_BUBBLE_HEX = stringPreferencesKey("user_bubble_hex")
        val ASSISTANT_SURFACE_HEX = stringPreferencesKey("assistant_surface_hex")
        val SELECTED_FONT_KEY = stringPreferencesKey("selected_font_key")
        val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
        val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
        val ZOOM_SCALE = floatPreferencesKey("zoom_scale")
        val LINE_SPACING_MULT = floatPreferencesKey("line_spacing_mult")
        val PARAGRAPH_SPACING_DP = floatPreferencesKey("paragraph_spacing_dp")
        val BUBBLE_RADIUS_DP = floatPreferencesKey("bubble_radius_dp")
        val COMPACT_DENSITY = booleanPreferencesKey("compact_density")

        // Creator Profile & Project Links
        val PROFILE_DISPLAY_NAME = stringPreferencesKey("profile_display_name")
        val PROFILE_EMAIL = stringPreferencesKey("profile_email")
        val PROFILE_GITHUB_URL = stringPreferencesKey("profile_github_url")
        val PROFILE_WEBSITE_URL = stringPreferencesKey("profile_website_url")
        val PROFILE_CUSTOM_LABEL = stringPreferencesKey("profile_custom_label")
        val PROFILE_CUSTOM_URL = stringPreferencesKey("profile_custom_url")
        val PROFILE_BIO_NOTES = stringPreferencesKey("profile_bio_notes")

        // Clock, Links & Web Defaults
        val USE_24H_CLOCK = booleanPreferencesKey("use_24h_clock")
        val SHOW_MESSAGE_TIMESTAMPS = booleanPreferencesKey("show_message_timestamps")
        val LINK_OPEN_BEHAVIOR = stringPreferencesKey("link_open_behavior")
        val DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
        val DEFAULT_WEB_ENABLED = booleanPreferencesKey("default_web_enabled")
        val DEFAULT_WEB_MODE = stringPreferencesKey("default_web_mode")
        val DEFAULT_WEB_ENGINE = stringPreferencesKey("default_web_engine")
        val DEFAULT_WEB_MAX_RESULTS = intPreferencesKey("default_web_max_results")
        val DEFAULT_WEB_MAX_TOTAL = intPreferencesKey("default_web_max_total")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val SMART_REPLIES_ENABLED = booleanPreferencesKey("smart_replies_enabled")

        // Stateful Model Picker Memory
        val PICKER_SORT_MODE = stringPreferencesKey("picker_sort_mode")
        val PICKER_SORT_ASC = booleanPreferencesKey("picker_sort_asc")
        val PICKER_SEARCH_QUERY = stringPreferencesKey("picker_search_query")
        val PICKER_SELECTED_COMPANY = stringPreferencesKey("picker_selected_company")
        val PICKER_FILTER_FAV = booleanPreferencesKey("picker_filter_fav")
        val PICKER_FILTER_VISION = booleanPreferencesKey("picker_filter_vision")
        val PICKER_FILTER_REASONING = booleanPreferencesKey("picker_filter_reasoning")
        val PICKER_FILTER_FREE = booleanPreferencesKey("picker_filter_free")
        val PICKER_FILTER_TOOLS = booleanPreferencesKey("picker_filter_tools")
        val PICKER_SCROLL_INDEX = intPreferencesKey("picker_scroll_index")
        val PICKER_SCROLL_OFFSET = intPreferencesKey("picker_scroll_offset")
    }

    val appPreferences: Flow<AppPreferences> = context.aetherDataStore.data.map { prefs ->
        val encryptedKey = prefs[Keys.ENCRYPTED_API_KEY].orEmpty()
        AppPreferences(
            appearance = AppearanceConfig(
                themeMode = ThemeMode.fromString(prefs[Keys.THEME_MODE]),
                useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true,
                customAccentHex = prefs[Keys.CUSTOM_ACCENT_HEX].orEmpty(),
                userBubbleHex = prefs[Keys.USER_BUBBLE_HEX].orEmpty(),
                assistantSurfaceHex = prefs[Keys.ASSISTANT_SURFACE_HEX].orEmpty(),
                selectedFontKey = prefs[Keys.SELECTED_FONT_KEY] ?: "plus_jakarta_sans",
                customFontPath = prefs[Keys.CUSTOM_FONT_PATH].orEmpty(),
                customFontName = prefs[Keys.CUSTOM_FONT_NAME].orEmpty(),
                zoomScale = (prefs[Keys.ZOOM_SCALE] ?: 1.0f).coerceIn(0.8f, 1.6f),
                lineSpacingMultiplier = (prefs[Keys.LINE_SPACING_MULT] ?: 1.38f).coerceIn(1.0f, 2.2f),
                paragraphSpacingDp = (prefs[Keys.PARAGRAPH_SPACING_DP] ?: 8f).coerceIn(4f, 20f),
                bubbleCornerRadiusDp = (prefs[Keys.BUBBLE_RADIUS_DP] ?: 18f).coerceIn(8f, 28f),
                compactDensity = prefs[Keys.COMPACT_DENSITY] ?: false,
            ),
            creatorProfile = CreatorProfile(
                displayName = prefs[Keys.PROFILE_DISPLAY_NAME].orEmpty(),
                email = prefs[Keys.PROFILE_EMAIL].orEmpty(),
                githubRepoUrl = prefs[Keys.PROFILE_GITHUB_URL].orEmpty(),
                websiteUrl = prefs[Keys.PROFILE_WEBSITE_URL].orEmpty(),
                customLinkLabel = prefs[Keys.PROFILE_CUSTOM_LABEL].orEmpty(),
                customLinkUrl = prefs[Keys.PROFILE_CUSTOM_URL].orEmpty(),
                bioNotes = prefs[Keys.PROFILE_BIO_NOTES].orEmpty(),
            ),
            use24HourClock = prefs[Keys.USE_24H_CLOCK] ?: false,
            showMessageTimestamps = prefs[Keys.SHOW_MESSAGE_TIMESTAMPS] ?: true,
            linkOpenBehavior = LinkOpenBehavior.fromString(prefs[Keys.LINK_OPEN_BEHAVIOR]),
            defaultModelId = prefs[Keys.DEFAULT_MODEL_ID] ?: "anthropic/claude-sonnet-4",
            defaultWebSearchEnabled = prefs[Keys.DEFAULT_WEB_ENABLED] ?: true,
            defaultWebSearchMode = WebSearchMode.fromString(prefs[Keys.DEFAULT_WEB_MODE]),
            defaultWebSearchEngine = WebSearchEngine.fromWire(prefs[Keys.DEFAULT_WEB_ENGINE]),
            defaultWebMaxResults = prefs[Keys.DEFAULT_WEB_MAX_RESULTS] ?: 5,
            defaultWebMaxTotalResults = prefs[Keys.DEFAULT_WEB_MAX_TOTAL] ?: 15,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK_ENABLED] ?: false,
            smartRepliesEnabled = prefs[Keys.SMART_REPLIES_ENABLED] ?: true,
            hasApiKey = encryptedKey.isNotBlank(),
        )
    }

    val modelPickerMemory: Flow<ModelPickerMemory> = context.aetherDataStore.data.map { prefs ->
        ModelPickerMemory(
            sortMode = ModelSortMode.fromString(prefs[Keys.PICKER_SORT_MODE]),
            sortAscending = prefs[Keys.PICKER_SORT_ASC] ?: false,
            searchQuery = prefs[Keys.PICKER_SEARCH_QUERY].orEmpty(),
            selectedCompany = prefs[Keys.PICKER_SELECTED_COMPANY].orEmpty(),
            filterFavorites = prefs[Keys.PICKER_FILTER_FAV] ?: false,
            filterVision = prefs[Keys.PICKER_FILTER_VISION] ?: false,
            filterReasoning = prefs[Keys.PICKER_FILTER_REASONING] ?: false,
            filterFree = prefs[Keys.PICKER_FILTER_FREE] ?: false,
            filterTools = prefs[Keys.PICKER_FILTER_TOOLS] ?: false,
            scrollIndex = prefs[Keys.PICKER_SCROLL_INDEX] ?: 0,
            scrollOffset = prefs[Keys.PICKER_SCROLL_OFFSET] ?: 0,
        )
    }

    suspend fun saveApiKey(rawApiKey: String) {
        val trimmed = rawApiKey.trim()
        val encrypted = if (trimmed.isEmpty()) "" else cryptoManager.encrypt(trimmed)
        context.aetherDataStore.edit { prefs ->
            prefs[Keys.ENCRYPTED_API_KEY] = encrypted
        }
    }

    suspend fun getDecryptedApiKey(): String {
        val encrypted = context.aetherDataStore.data.first()[Keys.ENCRYPTED_API_KEY].orEmpty()
        return if (encrypted.isBlank()) "" else cryptoManager.decrypt(encrypted).trim()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.aetherDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.aetherDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun updateAppearance(transform: (AppearanceConfig) -> AppearanceConfig) {
        context.aetherDataStore.edit { prefs ->
            val current = AppearanceConfig(
                themeMode = ThemeMode.fromString(prefs[Keys.THEME_MODE]),
                useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true,
                customAccentHex = prefs[Keys.CUSTOM_ACCENT_HEX].orEmpty(),
                userBubbleHex = prefs[Keys.USER_BUBBLE_HEX].orEmpty(),
                assistantSurfaceHex = prefs[Keys.ASSISTANT_SURFACE_HEX].orEmpty(),
                selectedFontKey = prefs[Keys.SELECTED_FONT_KEY] ?: "plus_jakarta_sans",
                customFontPath = prefs[Keys.CUSTOM_FONT_PATH].orEmpty(),
                customFontName = prefs[Keys.CUSTOM_FONT_NAME].orEmpty(),
                zoomScale = prefs[Keys.ZOOM_SCALE] ?: 1.0f,
                lineSpacingMultiplier = prefs[Keys.LINE_SPACING_MULT] ?: 1.38f,
                paragraphSpacingDp = prefs[Keys.PARAGRAPH_SPACING_DP] ?: 8f,
                bubbleCornerRadiusDp = prefs[Keys.BUBBLE_RADIUS_DP] ?: 18f,
                compactDensity = prefs[Keys.COMPACT_DENSITY] ?: false,
            )
            val next = transform(current)
            prefs[Keys.THEME_MODE] = next.themeMode.name
            prefs[Keys.USE_DYNAMIC_COLOR] = next.useDynamicColor
            prefs[Keys.CUSTOM_ACCENT_HEX] = next.customAccentHex.trim()
            prefs[Keys.USER_BUBBLE_HEX] = next.userBubbleHex.trim()
            prefs[Keys.ASSISTANT_SURFACE_HEX] = next.assistantSurfaceHex.trim()
            prefs[Keys.SELECTED_FONT_KEY] = next.selectedFontKey
            prefs[Keys.CUSTOM_FONT_PATH] = next.customFontPath
            prefs[Keys.CUSTOM_FONT_NAME] = next.customFontName
            prefs[Keys.ZOOM_SCALE] = next.zoomScale.coerceIn(0.8f, 1.6f)
            prefs[Keys.LINE_SPACING_MULT] = next.lineSpacingMultiplier.coerceIn(1.0f, 2.2f)
            prefs[Keys.PARAGRAPH_SPACING_DP] = next.paragraphSpacingDp.coerceIn(4f, 20f)
            prefs[Keys.BUBBLE_RADIUS_DP] = next.bubbleCornerRadiusDp.coerceIn(8f, 28f)
            prefs[Keys.COMPACT_DENSITY] = next.compactDensity
        }
    }

    suspend fun saveCreatorProfile(profile: CreatorProfile) {
        context.aetherDataStore.edit { prefs ->
            prefs[Keys.PROFILE_DISPLAY_NAME] = profile.displayName.trim()
            prefs[Keys.PROFILE_EMAIL] = profile.email.trim()
            prefs[Keys.PROFILE_GITHUB_URL] = profile.githubRepoUrl.trim()
            prefs[Keys.PROFILE_WEBSITE_URL] = profile.websiteUrl.trim()
            prefs[Keys.PROFILE_CUSTOM_LABEL] = profile.customLinkLabel.trim()
            prefs[Keys.PROFILE_CUSTOM_URL] = profile.customLinkUrl.trim()
            prefs[Keys.PROFILE_BIO_NOTES] = profile.bioNotes.trim()
        }
    }

    suspend fun setUse24HourClock(enabled: Boolean) {
        context.aetherDataStore.edit { it[Keys.USE_24H_CLOCK] = enabled }
    }

    suspend fun setShowMessageTimestamps(enabled: Boolean) {
        context.aetherDataStore.edit { it[Keys.SHOW_MESSAGE_TIMESTAMPS] = enabled }
    }

    suspend fun setLinkOpenBehavior(behavior: LinkOpenBehavior) {
        context.aetherDataStore.edit { it[Keys.LINK_OPEN_BEHAVIOR] = behavior.name }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.aetherDataStore.edit { it[Keys.BIOMETRIC_LOCK_ENABLED] = enabled }
    }

    suspend fun setSmartRepliesEnabled(enabled: Boolean) {
        context.aetherDataStore.edit { it[Keys.SMART_REPLIES_ENABLED] = enabled }
    }

    suspend fun setDefaultModelId(modelId: String) {
        context.aetherDataStore.edit { it[Keys.DEFAULT_MODEL_ID] = modelId }
    }

    suspend fun setDefaultWebConfig(
        enabled: Boolean,
        mode: WebSearchMode,
        engine: WebSearchEngine,
        maxResults: Int,
        maxTotalResults: Int,
    ) {
        context.aetherDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_WEB_ENABLED] = enabled
            prefs[Keys.DEFAULT_WEB_MODE] = mode.name
            prefs[Keys.DEFAULT_WEB_ENGINE] = engine.wireValue
            prefs[Keys.DEFAULT_WEB_MAX_RESULTS] = maxResults.coerceIn(1, 10)
            prefs[Keys.DEFAULT_WEB_MAX_TOTAL] = maxTotalResults.coerceIn(5, 30)
        }
    }

    suspend fun updateModelPickerMemory(transform: (ModelPickerMemory) -> ModelPickerMemory) {
        context.aetherDataStore.edit { prefs ->
            val current = ModelPickerMemory(
                sortMode = ModelSortMode.fromString(prefs[Keys.PICKER_SORT_MODE]),
                sortAscending = prefs[Keys.PICKER_SORT_ASC] ?: false,
                searchQuery = prefs[Keys.PICKER_SEARCH_QUERY].orEmpty(),
                selectedCompany = prefs[Keys.PICKER_SELECTED_COMPANY].orEmpty(),
                filterFavorites = prefs[Keys.PICKER_FILTER_FAV] ?: false,
                filterVision = prefs[Keys.PICKER_FILTER_VISION] ?: false,
                filterReasoning = prefs[Keys.PICKER_FILTER_REASONING] ?: false,
                filterFree = prefs[Keys.PICKER_FILTER_FREE] ?: false,
                filterTools = prefs[Keys.PICKER_FILTER_TOOLS] ?: false,
                scrollIndex = prefs[Keys.PICKER_SCROLL_INDEX] ?: 0,
                scrollOffset = prefs[Keys.PICKER_SCROLL_OFFSET] ?: 0,
            )
            val updated = transform(current)
            prefs[Keys.PICKER_SORT_MODE] = updated.sortMode.name
            prefs[Keys.PICKER_SORT_ASC] = updated.sortAscending
            prefs[Keys.PICKER_SEARCH_QUERY] = updated.searchQuery
            prefs[Keys.PICKER_SELECTED_COMPANY] = updated.selectedCompany
            prefs[Keys.PICKER_FILTER_FAV] = updated.filterFavorites
            prefs[Keys.PICKER_FILTER_VISION] = updated.filterVision
            prefs[Keys.PICKER_FILTER_REASONING] = updated.filterReasoning
            prefs[Keys.PICKER_FILTER_FREE] = updated.filterFree
            prefs[Keys.PICKER_FILTER_TOOLS] = updated.filterTools
            prefs[Keys.PICKER_SCROLL_INDEX] = updated.scrollIndex.coerceAtLeast(0)
            prefs[Keys.PICKER_SCROLL_OFFSET] = updated.scrollOffset.coerceAtLeast(0)
        }
    }

    suspend fun saveModelPickerScroll(index: Int, offset: Int) {
        context.aetherDataStore.edit { prefs ->
            prefs[Keys.PICKER_SCROLL_INDEX] = index.coerceAtLeast(0)
            prefs[Keys.PICKER_SCROLL_OFFSET] = offset.coerceAtLeast(0)
        }
    }
}
