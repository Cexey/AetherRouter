package com.example.network

import com.example.data.model.ModelInfo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.Locale
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenRouterModelsResponseDto(
    @Json(name = "data") val data: List<OpenRouterModelDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class OpenRouterModelDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String? = null,
    @Json(name = "created") val created: Long? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "context_length") val contextLength: Int? = null,
    @Json(name = "pricing") val pricing: OpenRouterPricingDto? = null,
    @Json(name = "architecture") val architecture: OpenRouterArchitectureDto? = null,
    @Json(name = "supported_parameters") val supportedParameters: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class OpenRouterPricingDto(
    @Json(name = "prompt") val prompt: String? = null,
    @Json(name = "completion") val completion: String? = null,
)

@JsonClass(generateAdapter = true)
data class OpenRouterArchitectureDto(
    @Json(name = "modality") val modality: String? = null,
    @Json(name = "input_modalities") val inputModalities: List<String>? = null,
)

interface OpenRouterApiService {
    @GET("api/v1/models")
    suspend fun getModels(
        @Header("Authorization") authorization: String? = null,
        @Header("HTTP-Referer") referer: String = "https://aetherrouter.local",
        @Header("X-Title") title: String = "AetherRouter",
    ): OpenRouterModelsResponseDto
}

object OpenRouterNetworkFactory {
    const val BASE_URL = "https://openrouter.ai/"

    fun createOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    fun createApiService(okHttpClient: OkHttpClient): OpenRouterApiService {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenRouterApiService::class.java)
    }

    fun normalizeCompany(modelId: String): String {
        val prefix = modelId.substringBefore('/').lowercase(Locale.US)
        return when (prefix) {
            "anthropic" -> "Anthropic"
            "openai" -> "OpenAI"
            "google" -> "Google"
            "meta-llama", "meta" -> "Meta"
            "deepseek" -> "DeepSeek"
            "mistralai", "mistral" -> "Mistral"
            "x-ai", "xai" -> "xAI"
            "qwen" -> "Qwen"
            "cohere" -> "Cohere"
            "perplexity" -> "Perplexity"
            "nvidia" -> "NVIDIA"
            "microsoft" -> "Microsoft"
            "amazon" -> "Amazon"
            "nousresearch" -> "Nous Research"
            "openrouter" -> "OpenRouter"
            "minimax" -> "MiniMax"
            "moonshotai" -> "Moonshot AI"
            "z-ai", "zhipu" -> "Zhipu AI"
            else -> prefix.split('-', '_').joinToString(" ") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }.ifBlank { "Other" }
        }
    }

    fun mapDtoToDomain(dto: OpenRouterModelDto, favoriteIds: Set<String>, nowMillis: Long): ModelInfo {
        val promptPerToken = dto.pricing?.prompt?.toDoubleOrNull() ?: 0.0
        val completionPerToken = dto.pricing?.completion?.toDoubleOrNull() ?: 0.0
        val promptPerM = (promptPerToken * 1_000_000.0).coerceAtLeast(0.0)
        val completionPerM = (completionPerToken * 1_000_000.0).coerceAtLeast(0.0)
        val isFree = dto.id.endsWith(":free") || (promptPerToken <= 0.0 && completionPerToken <= 0.0)

        val inputMods = dto.architecture?.inputModalities.orEmpty().map { it.lowercase(Locale.US) }
        val modalityStr = dto.architecture?.modality.orEmpty().lowercase(Locale.US)
        val supportsVision = inputMods.contains("image") || modalityStr.contains("image")
        val supportsPdf = inputMods.contains("file") || supportsVision

        val params = dto.supportedParameters.orEmpty().map { it.lowercase(Locale.US) }
        val lowerId = dto.id.lowercase(Locale.US)
        val supportsReasoning = params.contains("reasoning") ||
            params.contains("include_reasoning") ||
            lowerId.contains("r1") ||
            lowerId.contains("thinking") ||
            lowerId.contains("o1") ||
            lowerId.contains("o3") ||
            lowerId.contains("o4") ||
            lowerId.contains("claude-3.7") ||
            lowerId.contains("claude-sonnet-4") ||
            lowerId.contains("claude-opus-4") ||
            lowerId.contains("gemini-2.5") ||
            lowerId.contains("gemini-3")

        val supportsTools = if (params.isEmpty()) true else params.contains("tools")

        return ModelInfo(
            id = dto.id,
            name = dto.name?.takeIf { it.isNotBlank() } ?: dto.id.substringAfter('/'),
            company = normalizeCompany(dto.id),
            description = dto.description.orEmpty(),
            createdEpochSeconds = dto.created ?: 0L,
            contextLength = dto.contextLength ?: 128_000,
            promptPricingPerMillion = promptPerM,
            completionPricingPerMillion = completionPerM,
            supportsVision = supportsVision,
            supportsPdfFile = supportsPdf,
            supportsReasoning = supportsReasoning,
            supportsTools = supportsTools,
            isFree = isFree,
            isFavorite = dto.id in favoriteIds,
            fetchedAt = nowMillis,
        )
    }
}
