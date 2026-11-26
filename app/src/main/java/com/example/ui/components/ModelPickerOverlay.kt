package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.LiveClockProvider
import com.example.data.model.ModelInfo
import com.example.data.model.ModelSortMode
import com.example.ui.theme.LiveWebActiveColor
import com.example.ui.theme.ReasoningAccentColor
import com.example.viewmodel.ModelPickerRow
import com.example.viewmodel.ModelPickerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ModelPickerOverlay(
    uiState: ModelPickerUiState,
    activeModelId: String,
    onSelectModel: (ModelInfo) -> Unit,
    onClose: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectSortMode: (ModelSortMode) -> Unit,
    onToggleSortDirection: () -> Unit,
    onSelectCompany: (String) -> Unit,
    onToggleFavFilter: () -> Unit,
    onToggleVisionFilter: () -> Unit,
    onToggleReasoningFilter: () -> Unit,
    onToggleFreeFilter: () -> Unit,
    onToggleToolsFilter: () -> Unit,
    onClearFilters: () -> Unit,
    onSaveScrollPosition: (Int, Int) -> Unit,
    onToggleModelFavorite: (String, Boolean) -> Unit,
    onRefreshCatalog: () -> Unit,
) {
    BackHandler { onClose() }

    val memory = uiState.memory
    val hasActiveFilter = memory.searchQuery.isNotBlank() ||
        memory.selectedCompany.isNotBlank() ||
        memory.filterFavorites ||
        memory.filterVision ||
        memory.filterReasoning ||
        memory.filterFree ||
        memory.filterTools

    // Local draft tracks immediate typing with zero cursor jump while syncing to DataStore memory
    var localSearch by remember(memory.searchQuery) { mutableStateOf(memory.searchQuery) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky Expressive Header & Controls
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("model_picker_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Close model picker",
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OpenRouter Models",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = "${uiState.filteredModels} of ${uiState.totalModels} models • State saved automatically",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(22.dp),
                                strokeWidth = 2.5.dp,
                            )
                        } else {
                            IconButton(
                                onClick = onRefreshCatalog,
                                modifier = Modifier.testTag("model_picker_refresh_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Sync live OpenRouter models",
                                )
                            }
                        }
                    }

                    // Persistent Search Bar
                    OutlinedTextField(
                        value = localSearch,
                        onValueChange = {
                            localSearch = it
                            onSearchChange(it)
                        },
                        placeholder = { Text("Search models, companies, or IDs…") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search models",
                            )
                        },
                        trailingIcon = {
                            if (localSearch.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        localSearch = ""
                                        onSearchChange("")
                                    },
                                    modifier = Modifier.testTag("model_picker_clear_search"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear search",
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("model_picker_search_input"),
                    )

                    // Sort Mode Row (A-Z, Company, Release Date) + Asc/Desc Flip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SortModePill(
                            label = "A–Z",
                            icon = Icons.Outlined.SortByAlpha,
                            selected = memory.sortMode == ModelSortMode.A_Z,
                            onClick = { onSelectSortMode(ModelSortMode.A_Z) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sort_mode_az"),
                        )
                        SortModePill(
                            label = "Company",
                            icon = Icons.Outlined.Business,
                            selected = memory.sortMode == ModelSortMode.COMPANY,
                            onClick = { onSelectSortMode(ModelSortMode.COMPANY) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sort_mode_company"),
                        )
                        SortModePill(
                            label = "Release",
                            icon = Icons.Outlined.CalendarMonth,
                            selected = memory.sortMode == ModelSortMode.RELEASE_DATE,
                            onClick = { onSelectSortMode(ModelSortMode.RELEASE_DATE) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sort_mode_release"),
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onToggleSortDirection)
                                .testTag("sort_direction_toggle"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (memory.sortAscending) {
                                        Icons.Outlined.ArrowUpward
                                    } else {
                                        Icons.Outlined.ArrowDownward
                                    },
                                    contentDescription = if (memory.sortAscending) "Ascending" else "Descending",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // Capability Filters Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = memory.filterFavorites,
                            onClick = onToggleFavFilter,
                            label = { Text("Favorites") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (memory.filterFavorites) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                        FilterChip(
                            selected = memory.filterReasoning,
                            onClick = onToggleReasoningFilter,
                            label = { Text("Reasoning") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                        )
                        FilterChip(
                            selected = memory.filterVision,
                            onClick = onToggleVisionFilter,
                            label = { Text("Vision") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                        )
                        FilterChip(
                            selected = memory.filterTools,
                            onClick = onToggleToolsFilter,
                            label = { Text("Tools") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                        )
                        FilterChip(
                            selected = memory.filterFree,
                            onClick = onToggleFreeFilter,
                            label = { Text("Free") },
                        )
                        if (hasActiveFilter) {
                            TextButton(onClick = {
                                localSearch = ""
                                onClearFilters()
                            }) {
                                Text("Reset")
                            }
                        }
                    }

                    // Quick Company Filter Strip (visible when sorting by Company or when a company filter is active)
                    if (uiState.companies.isNotEmpty() &&
                        (memory.sortMode == ModelSortMode.COMPANY || memory.selectedCompany.isNotBlank())
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            uiState.companies.forEach { company ->
                                val selected = memory.selectedCompany.equals(company, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectCompany(company) },
                                    label = { Text(company) },
                                )
                            }
                        }
                    }

                    if (!uiState.statusMessage.isNullOrBlank()) {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Virtualized High-Visibility Model List with Exact Scroll Restoration
            if (!uiState.isMemoryHydrated) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                StatefulModelList(
                    rows = uiState.rows,
                    activeModelId = activeModelId,
                    initialIndex = memory.scrollIndex,
                    initialOffset = memory.scrollOffset,
                    onSelectModel = onSelectModel,
                    onToggleFavorite = onToggleModelFavorite,
                    onSaveScrollPosition = onSaveScrollPosition,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatefulModelList(
    rows: List<ModelPickerRow>,
    activeModelId: String,
    initialIndex: Int,
    initialOffset: Int,
    onSelectModel: (ModelInfo) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onSaveScrollPosition: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = initialOffset.coerceAtLeast(0),
    )

    // Persist scroll index + exact pixel offset with lightweight debounce while scrolling
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collectLatest { (idx, off) ->
                delay(180)
                onSaveScrollPosition(idx, off)
            }
    }

    // Guarantee exact last scroll position is persisted on immediate exit
    DisposableEffect(listState) {
        onDispose {
            onSaveScrollPosition(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    if (rows.isEmpty()) {
        Box(
            modifier = modifier.padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No matching models",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Try clearing your search or capability filters, or tap Sync to fetch the full OpenRouter catalog.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .navigationBarsPadding()
            .testTag("model_picker_lazy_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = rows, key = { it.key }) { row ->
            when (row) {
                is ModelPickerRow.CompanyHeader -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = row.company,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = "${row.count}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                is ModelPickerRow.ModelCard -> {
                    HighVisibilityModelCard(
                        model = row.model,
                        isSelected = row.model.id == activeModelId,
                        onSelect = { onSelectModel(row.model) },
                        onToggleFavorite = { onToggleFavorite(row.model.id, row.model.isFavorite) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HighVisibilityModelCard(
    model: ModelInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        tonalElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
            .testTag("model_card_${model.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = model.company,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Text(
                            text = LiveClockProvider.formatReleaseDate(model.createdEpochSeconds),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Active model",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = model.id,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (model.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (model.isFavorite) "Remove favorite" else "Favorite model",
                        tint = if (model.isFavorite) ReasoningAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (model.description.isNotBlank()) {
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Metrics & Capabilities Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricBadge(text = model.formattedContext())
                MetricBadge(
                    text = model.formattedPricing(),
                    highlight = model.isFree,
                )
                if (model.supportsReasoning) {
                    CapabilityBadge(text = "Reasoning", accentColor = ReasoningAccentColor)
                }
                if (model.supportsVision) {
                    CapabilityBadge(text = "Vision", accentColor = MaterialTheme.colorScheme.primary)
                }
                if (model.supportsTools) {
                    CapabilityBadge(text = "Tools / Web", accentColor = LiveWebActiveColor)
                }
            }
        }
    }
}

@Composable
private fun SortModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetricBadge(text: String, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (highlight) {
            LiveWebActiveColor.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (highlight) LiveWebActiveColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CapabilityBadge(
    text: String,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}
