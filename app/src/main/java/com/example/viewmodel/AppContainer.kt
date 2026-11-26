package com.example.viewmodel

import android.content.Context
import com.example.core.CryptoManager
import com.example.core.DocumentExtractor
import com.example.core.ExportManager
import com.example.core.LiveClockProvider
import com.example.core.PersonaCardEngine
import com.example.core.VoiceEngineManager
import com.example.data.local.AetherDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.repository.VaultRepository
import com.example.network.LiveWebFetcher
import com.example.network.McpJsonRpcClient
import com.example.network.OpenRouterNetworkFactory
import com.example.network.OpenRouterStreamClient

/**
 * Simple Constructor-Injection application container created once from applicationContext.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val cryptoManager: CryptoManager = CryptoManager()
    val liveClockProvider: LiveClockProvider = LiveClockProvider(appContext)
    val documentExtractor: DocumentExtractor = DocumentExtractor(appContext)
    val exportManager: ExportManager = ExportManager(appContext, cryptoManager)
    val personaCardEngine: PersonaCardEngine = PersonaCardEngine(appContext)
    val voiceEngineManager: VoiceEngineManager = VoiceEngineManager(appContext)
    val database: AetherDatabase = AetherDatabase.getInstance(appContext)
    val preferencesRepository: PreferencesRepository = PreferencesRepository(appContext, cryptoManager)

    val okHttpClient = OpenRouterNetworkFactory.createOkHttpClient()
    val openRouterApi = OpenRouterNetworkFactory.createApiService(okHttpClient)
    val streamClient = OpenRouterStreamClient(okHttpClient, documentExtractor)
    val liveWebFetcher = LiveWebFetcher(okHttpClient)
    val mcpClient = McpJsonRpcClient(okHttpClient)

    val vaultRepository: VaultRepository = VaultRepository(
        database = database,
        cryptoManager = cryptoManager,
        apiService = openRouterApi,
    )
}
