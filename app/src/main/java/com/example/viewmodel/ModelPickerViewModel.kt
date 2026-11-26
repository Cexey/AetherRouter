package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesRepository
import com.example.data.model.ModelInfo
import com.example.data.model.ModelPickerMemory
import com.example.data.model.ModelSortMode
import com.example.data.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ModelPickerRow {
    val key: String

    data class CompanyHeader(
        val company: String,
        val count: Int,
    ) : ModelPickerRow {
        override val key: String = "header_$company"
    }

    data class ModelCard(
        val model: ModelInfo,
    ) : ModelPickerRow {
        override val key: String = "model_${model.id}"
    }
}

data class ModelPickerUiState(
    val memory: ModelPickerMemory = ModelPickerMemory(),
    val isMemoryHydrated: Boolean = false,
    val rows: List<ModelPickerRow> = emptyList(),
    val companies: List<String> = emptyList(),
    val totalModels: Int = 0,
    val filteredModels: Int = 0,
    val isRefreshing: Boolean = false,
    val statusMessage: String? = null,
)

class ModelPickerViewModel(
    private val vaultRepository: VaultRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val isHydrated = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            vaultRepository.ensureStarterModelsSeeded()
        }
        viewModelScope.launch {
            preferencesRepository.modelPickerMemory.collect {
                if (!isHydrated.value) {
                    isHydrated.value = true
                }
            }
        }
    }

    val uiState: StateFlow<ModelPickerUiState> = combine(
        vaultRepository.observeModels(),
        preferencesRepository.modelPickerMemory,
        isHydrated,
        isRefreshing,
        statusMessage,
    ) { models, memory, hydrated, refreshing, message ->
        val companies = models.map { it.company }.distinct().sorted()
        val query = memory.searchQuery.trim().lowercase()

        val filtered = models.filter { m ->
            val matchesQuery = query.isEmpty() ||
                m.name.lowercase().contains(query) ||
                m.id.lowercase().contains(query) ||
                m.company.lowercase().contains(query) ||
                m.description.lowercase().contains(query)

            val matchesCompany = memory.selectedCompany.isEmpty() ||
                m.company.equals(memory.selectedCompany, ignoreCase = true)
            val matchesFav = !memory.filterFavorites || m.isFavorite
            val matchesVision = !memory.filterVision || m.supportsVision
            val matchesReasoning = !memory.filterReasoning || m.supportsReasoning
            val matchesFree = !memory.filterFree || m.isFree
            val matchesTools = !memory.filterTools || m.supportsTools

            matchesQuery && matchesCompany && matchesFav && matchesVision &&
                matchesReasoning && matchesFree && matchesTools
        }

        val sorted: List<ModelInfo> = when (memory.sortMode) {
            ModelSortMode.A_Z -> {
                if (memory.sortAscending) {
                    filtered.sortedBy { it.name.lowercase() }
                } else {
                    filtered.sortedByDescending { it.name.lowercase() }
                }
            }
            ModelSortMode.COMPANY -> {
                if (memory.sortAscending) {
                    filtered.sortedWith(
                        compareBy<ModelInfo> { it.company.lowercase() }
                            .thenByDescending { it.createdEpochSeconds }
                            .thenBy { it.name.lowercase() },
                    )
                } else {
                    filtered.sortedWith(
                        compareByDescending<ModelInfo> { it.company.lowercase() }
                            .thenByDescending { it.createdEpochSeconds }
                            .thenBy { it.name.lowercase() },
                    )
                }
            }
            ModelSortMode.RELEASE_DATE -> {
                if (memory.sortAscending) {
                    filtered.sortedWith(
                        compareBy<ModelInfo> { if (it.createdEpochSeconds <= 0L) Long.MAX_VALUE else it.createdEpochSeconds }
                            .thenBy { it.name.lowercase() },
                    )
                } else {
                    filtered.sortedWith(
                        compareByDescending<ModelInfo> { it.createdEpochSeconds }
                            .thenBy { it.name.lowercase() },
                    )
                }
            }
        }

        val rows = buildList {
            if (memory.sortMode == ModelSortMode.COMPANY) {
                val grouped = sorted.groupBy { it.company }
                for ((company, list) in grouped) {
                    add(ModelPickerRow.CompanyHeader(company = company, count = list.size))
                    list.forEach { add(ModelPickerRow.ModelCard(it)) }
                }
            } else {
                sorted.forEach { add(ModelPickerRow.ModelCard(it)) }
            }
        }

        ModelPickerUiState(
            memory = memory,
            isMemoryHydrated = hydrated,
            rows = rows,
            companies = companies,
            totalModels = models.size,
            filteredModels = sorted.size,
            isRefreshing = refreshing,
            statusMessage = message,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ModelPickerUiState(),
        )

    fun updateSearchQuery(query: String) {
        viewModelScope.launch {
            preferencesRepository.updateModelPickerMemory {
                it.copy(searchQuery = query, scrollIndex = 0, scrollOffset = 0)
            }
        }
    }

    fun selectSortMode(mode: ModelSortMode) {
        viewModelScope.launch {
            preferencesRepository.updateModelPickerMemory { current ->
                if (current.sortMode == mode) {
                    current.copy(sortAscending = !current.sortAscending)
                } else {
                    val defaultAsc = mode != ModelSortMode.RELEASE_DATE
                    current.copy(
                        sortMode = mode,
                        sortAscending = defaultAsc,
                        scrollIndex = 0,
                        scrollOffset = 0,
                    )
                }
            }
        }
    }

    fun toggleSortDirection() {
        viewModelScope.launch {
            preferencesRepository.updateModelPickerMemory {
                it.copy(sortAscending = !it.sortAscending)
            }
        }
    }

    fun selectCompanyFilter(company: String) {
        viewModelScope.launch {
            preferencesRepository.updateModelPickerMemory { current ->
                val next = if (current.selectedCompany == company) "" else company
                current.copy(selectedCompany = next, scrollIndex = 0, scrollOffset = 0)
            }
        }
    }

    fun toggleFilterFavorites() = updateFilter { it.copy(filterFavorites = !it.filterFavorites) }
    fun toggleFilterVision() = updateFilter { it.copy(filterVision = !it.filterVision) }
    fun toggleFilterReasoning() = updateFilter { it.copy(filterReasoning = !it.filterReasoning) }
    fun toggleFilterFree() = updateFilter { it.copy(filterFree = !it.filterFree) }
    fun toggleFilterTools() = updateFilter { it.copy(filterTools = !it.filterTools) }

    private fun updateFilter(block: (ModelPickerMemory) -> ModelPickerMemory) {
        viewModelScope.launch {
            preferencesRepository.updateModelPickerMemory { current ->
                block(current).copy(scrollIndex = 0, scrollOffset = 0)
            }
        }
    }

    fun clearFiltersAndSearch() {
        viewModelScope.launch {
            preferencesRepository.updateModelPickerMemory { current ->
                current.copy(
                    searchQuery = "",
                    selectedCompany = "",
                    filterFavorites = false,
                    filterVision = false,
                    filterReasoning = false,
                    filterFree = false,
                    filterTools = false,
                    scrollIndex = 0,
                    scrollOffset = 0,
                )
            }
        }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        viewModelScope.launch {
            preferencesRepository.saveModelPickerScroll(index, offset)
        }
    }

    fun toggleModelFavorite(modelId: String, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            vaultRepository.toggleModelFavorite(modelId, !currentlyFavorite)
        }
    }

    fun refreshCatalogFromOpenRouter() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            statusMessage.value = null
            val key = preferencesRepository.getDecryptedApiKey()
            val result = vaultRepository.refreshModelsFromOpenRouter(key)
            result.onSuccess { count ->
                statusMessage.value = "Synced $count live models from OpenRouter"
            }.onFailure { err ->
                statusMessage.value = "Could not sync catalog: ${err.message ?: "Offline"}"
            }
            isRefreshing.value = false
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ModelPickerViewModel(
                vaultRepository = container.vaultRepository,
                preferencesRepository = container.preferencesRepository,
            ) as T
        }
    }
}
