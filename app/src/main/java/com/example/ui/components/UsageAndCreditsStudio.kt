package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.OpenRouterActivityBreakdown
import com.example.data.model.OpenRouterActivityGroup
import com.example.data.model.OpenRouterAlertState
import com.example.data.model.OpenRouterBudgetWindow
import com.example.data.model.OpenRouterUsageSnapshot
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiveWebActiveColor
import com.example.ui.theme.ReasoningAccentColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatUsdShort(value: Double): String = when {
    value == 0.0 -> "$0.00"
    value < 0.01 -> String.format(Locale.US, "$%.4f", value)
    else -> String.format(Locale.US, "$%.2f", value)
}

private fun formatUsdExact(value: Double): String = when {
    value == 0.0 -> "$0.0000"
    value < 1.0 -> String.format(Locale.US, "$%.4f", value)
    else -> String.format(Locale.US, "$%.3f", value)
}

/**
 * Compact, tappable live spend / remaining-credits pill shown in the Navigation Drawer
 * and Settings header. Tapping opens the full Usage & Credits Studio.
 */
@Composable
fun CompactSpendCreditsPill(
    usageSnapshot: OpenRouterUsageSnapshot?,
    hasApiKey: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTagName: String = "compact_credits_pill",
) {
    val progress = usageSnapshot?.limitProgressFraction
    val remaining = usageSnapshot?.displayRemainingUsd
    val statusColor = when {
        !hasApiKey -> MaterialTheme.colorScheme.outline
        remaining != null && remaining <= 0.01 -> MaterialTheme.colorScheme.error
        progress != null && progress >= 0.85f -> ReasoningAccentColor
        else -> LiveWebActiveColor
    }

    val headline = when {
        !hasApiKey -> "Add API key for live credits"
        usageSnapshot == null -> "Tap to view spend & credits"
        remaining != null -> "${formatUsdShort(remaining)} left"
        usageSnapshot.isFreeTier -> "Free Tier • ${formatUsdShort(usageSnapshot.usageDailyUsd)} today"
        else -> "${formatUsdShort(usageSnapshot.usageDailyUsd)} today"
    }
    val subline = when {
        !hasApiKey -> "Usage & Credits Studio"
        usageSnapshot == null -> if (isLoading) "Syncing…" else "Usage & Credits"
        remaining != null -> "Today ${formatUsdShort(usageSnapshot.usageDailyUsd)} • All ${formatUsdShort(usageSnapshot.usageUsd)}"
        else -> "Month ${formatUsdShort(usageSnapshot.usageMonthlyUsd)} • All ${formatUsdShort(usageSnapshot.usageUsd)}"
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (hasApiKey) statusColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTagName),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subline,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = "Open Usage and Credits Studio",
                    tint = statusColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Human-readable actionable alert banner for OpenRouter 402 (credits/key limit/in-flight budget),
 * 403 (budget/guardrail), and 429 (rate limit with live X-RateLimit-Reset countdown).
 */
@Composable
fun OpenRouterAlertBanner(
    alert: OpenRouterAlertState,
    onDismiss: () -> Unit,
    onOpenUsageStudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowMillis by remember(alert) { mutableLongStateOf(System.currentTimeMillis()) }

    if (alert is OpenRouterAlertState.RateLimited429 && alert.resetEpochMillis != null) {
        LaunchedEffect(alert.resetEpochMillis) {
            while (System.currentTimeMillis() < alert.resetEpochMillis) {
                nowMillis = System.currentTimeMillis()
                delay(1_000L)
            }
            nowMillis = System.currentTimeMillis()
        }
    }

    val remainingSeconds = if (alert is OpenRouterAlertState.RateLimited429 && alert.resetEpochMillis != null) {
        ((alert.resetEpochMillis - nowMillis) / 1000L).coerceAtLeast(0L)
    } else {
        null
    }

    val containerColor = when (alert) {
        is OpenRouterAlertState.InsufficientCredits402 -> MaterialTheme.colorScheme.errorContainer
        is OpenRouterAlertState.GuardrailForbidden403 -> MaterialTheme.colorScheme.tertiaryContainer
        is OpenRouterAlertState.RateLimited429 -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (alert) {
        is OpenRouterAlertState.InsufficientCredits402 -> MaterialTheme.colorScheme.onErrorContainer
        is OpenRouterAlertState.GuardrailForbidden403 -> MaterialTheme.colorScheme.onTertiaryContainer
        is OpenRouterAlertState.RateLimited429 -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val icon = when (alert) {
        is OpenRouterAlertState.InsufficientCredits402 -> Icons.Outlined.ErrorOutline
        is OpenRouterAlertState.GuardrailForbidden403 -> Icons.Outlined.Shield
        is OpenRouterAlertState.RateLimited429 -> Icons.Outlined.Schedule
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .testTag("openrouter_alert_banner_${alert.httpCode}"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = alert.headline,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = alert.headline,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("dismiss_openrouter_alert_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Dismiss alert",
                        tint = contentColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                text = alert.detail,
                style = MaterialTheme.typography.bodySmall.copy(color = contentColor),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (remainingSeconds != null) {
                    val countdownText = if (remainingSeconds == 0L) {
                        "Rate limit window reset — ready to retry"
                    } else {
                        val mins = remainingSeconds / 60
                        val secs = remainingSeconds % 60
                        String.format(Locale.US, "Resets in %d:%02d", mins, secs)
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = contentColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = countdownText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                            ),
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("rate_limit_countdown_text"),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                TextButton(
                    onClick = onOpenUsageStudio,
                    modifier = Modifier.testTag("alert_open_usage_studio_button"),
                ) {
                    Text(
                        text = "Usage & Credits",
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * Full-screen Material 3 Expressive Usage & Credits Studio displaying live OpenRouter
 * key spend (daily UTC / weekly / monthly / all-time), key & workspace budgets,
 * and optional 30-Day Management Key Activity Breakdown grouped by model and date.
 */
@Composable
fun UsageAndCreditsStudioOverlay(
    usageSnapshot: OpenRouterUsageSnapshot?,
    activityBreakdown: OpenRouterActivityBreakdown?,
    hasApiKey: Boolean,
    hasManagementApiKey: Boolean,
    isLoadingUsage: Boolean,
    isLoadingActivity: Boolean,
    onClose: () -> Unit,
    onRefreshAll: () -> Unit,
    onOpenSettingsForKeys: () -> Unit,
) {
    BackHandler(onBack = onClose)

    var activityGroupByModel by remember { mutableStateOf(true) }
    val updatedLabel = remember(usageSnapshot?.fetchedAtMillis) {
        val ts = usageSnapshot?.fetchedAtMillis ?: 0L
        if (ts <= 0L) "Not synced yet"
        else SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()).format(Date(ts))
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("usage_studio_close_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Usage & Credits Studio",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Synced: $updatedLabel",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                        IconButton(
                            onClick = onRefreshAll,
                            enabled = !isLoadingUsage && !isLoadingActivity && hasApiKey,
                            modifier = Modifier.testTag("usage_studio_refresh_button"),
                        ) {
                            if (isLoadingUsage || isLoadingActivity) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Refresh usage and credits",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!hasApiKey) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = aetherCardBorder(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(
                                    text = "Connect Your OpenRouter API Key",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = "Add your OpenRouter API key in Settings to track live daily/weekly/monthly spend, remaining key limits, and account credits.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                                Button(
                                    onClick = onOpenSettingsForKeys,
                                    modifier = Modifier.testTag("usage_studio_add_key_button"),
                                ) {
                                    Text("Open Settings")
                                }
                            }
                        }
                    }
                } else {
                    // Hero Balance & Key Limit Card
                    item {
                        HeroBalanceCard(
                            snapshot = usageSnapshot,
                            isLoading = isLoadingUsage,
                            onRefresh = onRefreshAll,
                        )
                    }

                    // Daily (UTC) / Weekly / Monthly / All-Time Spend Cards
                    item {
                        Text(
                            text = "Key Spend Windows",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SpendMetricCard(
                                    title = "Today (UTC)",
                                    amount = usageSnapshot?.usageDailyUsd ?: 0.0,
                                    accent = LiveWebActiveColor,
                                    modifier = Modifier.weight(1f),
                                )
                                SpendMetricCard(
                                    title = "This Week",
                                    amount = usageSnapshot?.usageWeeklyUsd ?: 0.0,
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SpendMetricCard(
                                    title = "This Month",
                                    amount = usageSnapshot?.usageMonthlyUsd ?: 0.0,
                                    accent = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f),
                                )
                                SpendMetricCard(
                                    title = "All-Time Key",
                                    amount = usageSnapshot?.usageUsd ?: 0.0,
                                    accent = ReasoningAccentColor,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Workspace / Guardrail Budgets (if present)
                    val budgets = usageSnapshot?.budgets.orEmpty()
                    if (budgets.isNotEmpty() || usageSnapshot?.effectiveBudgetUsd != null) {
                        item {
                            Text(
                                text = "Effective Budgets & Guardrails",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                        if (budgets.isEmpty() && usageSnapshot?.effectiveBudgetUsd != null) {
                            item {
                                BudgetWindowCard(
                                    window = OpenRouterBudgetWindow(
                                        name = "Effective Budget",
                                        limitUsd = usageSnapshot.effectiveBudgetUsd,
                                        usageUsd = usageSnapshot.usageUsd,
                                        remainingUsd = (usageSnapshot.effectiveBudgetUsd - usageSnapshot.usageUsd).coerceAtLeast(0.0),
                                        resetInterval = usageSnapshot.limitReset,
                                    ),
                                )
                            }
                        } else {
                            items(items = budgets) { window ->
                                BudgetWindowCard(window = window)
                            }
                        }
                    }

                    item {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    // 30-Day Activity Breakdown (Management Key)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BarChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "30-Day Activity Breakdown",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                            if (hasManagementApiKey) {
                                Text(
                                    text = activityBreakdown?.let {
                                        "${it.totalRequests30d} reqs • ${formatUsdShort(it.totalSpend30dUsd)}"
                                    } ?: "",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    }

                    if (!hasManagementApiKey) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = aetherCardBorder(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Unlock 30-Day Model & Date Analytics",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Text(
                                        text = "Add an optional OpenRouter Management API Key in Settings (encrypted via Android Keystore) to fetch /api/v1/activity spend grouped by model and UTC date.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                    OutlinedButton(
                                        onClick = onOpenSettingsForKeys,
                                        colors = aetherSlateButtonColors(),
                                        border = aetherCardBorder(),
                                        modifier = Modifier.testTag("usage_add_management_key_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Key,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Configure Management Key")
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilterChip(
                                    selected = activityGroupByModel,
                                    onClick = { activityGroupByModel = true },
                                    colors = aetherFilterChipColors(),
                                    border = aetherFilterChipBorder(enabled = true, selected = activityGroupByModel),
                                    label = { Text("By Model") },
                                    modifier = Modifier.testTag("activity_group_by_model_chip"),
                                )
                                FilterChip(
                                    selected = !activityGroupByModel,
                                    onClick = { activityGroupByModel = false },
                                    colors = aetherFilterChipColors(),
                                    border = aetherFilterChipBorder(enabled = true, selected = !activityGroupByModel),
                                    label = { Text("By Date") },
                                    modifier = Modifier.testTag("activity_group_by_date_chip"),
                                )
                            }
                        }

                        val rows = if (activityGroupByModel) {
                            activityBreakdown?.byModel.orEmpty()
                        } else {
                            activityBreakdown?.byDate.orEmpty()
                        }

                        if (isLoadingActivity && rows.isEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(vertical = 12.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Loading 30-day activity from OpenRouter…")
                                }
                            }
                        } else if (rows.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = aetherCardBorder(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "No completed activity records returned for the last 30 UTC days yet.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        modifier = Modifier.padding(14.dp),
                                    )
                                }
                            }
                        } else {
                            val maxUsd = rows.maxOfOrNull { it.usageUsd }?.coerceAtLeast(0.0001) ?: 1.0
                            items(items = rows, key = { it.key }) { group ->
                                ActivityGroupRowCard(
                                    group = group,
                                    fraction = (group.usageUsd / maxUsd).toFloat().coerceIn(0.02f, 1f),
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBalanceCard(
    snapshot: OpenRouterUsageSnapshot?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    val remaining = snapshot?.displayRemainingUsd
    val cap = snapshot?.effectiveCapUsd
    val progress = snapshot?.limitProgressFraction

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = aetherCardBorder(selected = true),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = snapshot?.label?.takeIf { it.isNotBlank() } ?: "Active OpenRouter Key",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (snapshot?.limitReset != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "Resets ${snapshot.limitReset}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Text(
                text = when {
                    snapshot == null -> if (isLoading) "Syncing…" else "—"
                    remaining != null -> formatUsdShort(remaining)
                    snapshot.isFreeTier -> "Free Tier"
                    else -> "No Key Cap"
                },
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.testTag("usage_hero_remaining_text"),
            )

            Text(
                text = when {
                    snapshot == null -> "Tap refresh to read /api/v1/key & /api/v1/credits (does not count toward key write limits)."
                    cap != null && remaining != null ->
                        "Remaining out of ${formatUsdShort(cap)} limit • Key usage: ${formatUsdExact(snapshot.usageUsd)}"
                    snapshot.totalCreditsUsd != null ->
                        "Account credits: ${formatUsdShort(snapshot.totalCreditsUsd)} • Account usage: ${formatUsdExact(snapshot.totalAccountUsageUsd ?: 0.0)}"
                    else ->
                        "Cumulative key usage: ${formatUsdExact(snapshot.usageUsd)}"
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )

            if (progress != null) {
                val barColor = when {
                    progress >= 0.9f -> MaterialTheme.colorScheme.error
                    progress >= 0.75f -> ReasoningAccentColor
                    else -> MaterialTheme.colorScheme.primary
                }
                LinearProgressIndicator(
                    progress = { progress },
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (snapshot?.isFreeTier == true) "Free Tier Key" else "Paid / Standard Key",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    colors = aetherSlateButtonColors(),
                    border = aetherCardBorder(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun SpendMetricCard(
    title: String,
    amount: Double,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = aetherCardBorder(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
            Text(
                text = formatUsdExact(amount),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BudgetWindowCard(
    window: OpenRouterBudgetWindow,
) {
    val limit = window.limitUsd
    val used = window.usageUsd ?: if (limit != null && window.remainingUsd != null) {
        (limit - window.remainingUsd).coerceAtLeast(0.0)
    } else null
    val progress = if (limit != null && limit > 0.0 && used != null) {
        (used / limit).toFloat().coerceIn(0f, 1f)
    } else null

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = aetherCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = window.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                if (!window.resetInterval.isNullOrBlank()) {
                    Text(
                        text = window.resetInterval,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            Text(
                text = buildString {
                    if (used != null) append("Used ${formatUsdExact(used)}")
                    if (limit != null) {
                        if (isNotEmpty()) append(" / ")
                        append("Cap ${formatUsdShort(limit)}")
                    }
                    if (window.remainingUsd != null) {
                        if (isNotEmpty()) append(" • ")
                        append("${formatUsdShort(window.remainingUsd)} left")
                    }
                }.ifBlank { "Active guardrail budget" },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
            }
        }
    }
}

@Composable
private fun ActivityGroupRowCard(
    group: OpenRouterActivityGroup,
    fraction: Float,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = aetherCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.key,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatUsdExact(group.usageUsd),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
            Text(
                text = "${group.requests} request${if (group.requests == 1) "" else "s"}" +
                    if (group.totalTokens > 0L) " • ${formatTokenCount(group.totalTokens)} tokens" else "",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000L -> String.format(Locale.US, "%.2fM", tokens / 1_000_000.0)
    tokens >= 1_000L -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
    else -> tokens.toString()
}
