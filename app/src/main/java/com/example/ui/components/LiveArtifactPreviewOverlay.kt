package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.CodeArtifactUtils
import com.example.data.model.LiveArtifactPreviewRequest
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LiveWebActiveColor
import java.io.ByteArrayInputStream

/**
 * Full-screen strictly offline sandboxed HTML/CSS/JS Artifact Preview overlay.
 *
 * Security hardening:
 * - JavaScript enabled strictly for the isolated `about:blank` / `loadDataWithBaseURL` document.
 * - `allowFileAccess = false`, `allowContentAccess = false`, file-URL cross-access disabled,
 *   no Android `@JavascriptInterface` bridge.
 * - Blocks ALL external network requests (`http://`, `https://`, CDN scripts/styles/images)
 *   and external navigations per the confirmed Offline Sandbox policy.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveArtifactPreviewOverlay(
    artifact: LiveArtifactPreviewRequest,
    onClose: () -> Unit,
    onExportHtml: (fileName: String, htmlContent: String) -> Unit,
) {
    BackHandler(onBack = onClose)

    val wrappedHtml = remember(artifact.language, artifact.code) {
        CodeArtifactUtils.wrapArtifactForOfflinePreview(artifact.language, artifact.code)
    }
    val htmlFileName = remember(artifact.suggestedFileName) {
        val base = artifact.suggestedFileName.substringBeforeLast('.').ifBlank { "artifact" }
        "$base.html"
    }

    var showSourceMode by remember { mutableStateOf(false) }
    var blockedExternalCount by remember { mutableIntStateOf(0) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Chrome
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("artifact_preview_close_button"),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close live preview")
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = "Live Artifact Preview",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Offline sandbox",
                                    tint = LiveWebActiveColor,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "Offline Sandbox • External requests blocked",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                blockedExternalCount = 0
                                reloadNonce++
                                showSourceMode = false
                                webViewRef?.loadDataWithBaseURL(
                                    "about:blank",
                                    wrappedHtml,
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                            },
                            modifier = Modifier.testTag("artifact_preview_reload_button"),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Reload artifact")
                        }
                        IconButton(
                            onClick = { onExportHtml(htmlFileName, wrappedHtml) },
                            modifier = Modifier.testTag("artifact_preview_export_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = "Export HTML file",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Mode Toggle + Blocked External Request Indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !showSourceMode,
                                onClick = { showSourceMode = false },
                                colors = aetherFilterChipColors(),
                                border = aetherFilterChipBorder(enabled = true, selected = !showSourceMode),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Visibility,
                                        contentDescription = null,
                                        tint = if (!showSourceMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp),
                                    )
                                },
                                label = { Text("Preview") },
                                modifier = Modifier.testTag("artifact_tab_preview"),
                            )
                            FilterChip(
                                selected = showSourceMode,
                                onClick = { showSourceMode = true },
                                colors = aetherFilterChipColors(),
                                border = aetherFilterChipBorder(enabled = true, selected = showSourceMode),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Code,
                                        contentDescription = null,
                                        tint = if (showSourceMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp),
                                    )
                                },
                                label = { Text("View Source") },
                                modifier = Modifier.testTag("artifact_tab_source"),
                            )
                        }
                        if (blockedExternalCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    text = "$blockedExternalCount external blocked",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // Main Sandbox / Source Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                if (showSourceMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Text(
                            text = wrappedHtml,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.testTag("artifact_source_text"),
                        )
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = false
                                    databaseEnabled = false
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    @Suppress("DEPRECATION")
                                    allowFileAccessFromFileURLs = false
                                    @Suppress("DEPRECATION")
                                    allowUniversalAccessFromFileURLs = false
                                    blockNetworkLoads = true
                                    loadsImagesAutomatically = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        safeBrowsingEnabled = true
                                    }
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        val urlStr = request?.url?.toString().orEmpty()
                                        if (urlStr == "about:blank" || urlStr.startsWith("about:blank#")) {
                                            return false
                                        }
                                        blockedExternalCount++
                                        return true
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): WebResourceResponse? {
                                        val scheme = request?.url?.scheme?.lowercase().orEmpty()
                                        if (scheme == "about" || scheme == "data" || scheme == "blob") {
                                            return null
                                        }
                                        blockedExternalCount++
                                        return WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            403,
                                            "Blocked by Offline Sandbox",
                                            mapOf("X-AetherRouter-Sandbox" to "offline"),
                                            ByteArrayInputStream(ByteArray(0)),
                                        )
                                    }
                                }
                                loadDataWithBaseURL(
                                    "about:blank",
                                    wrappedHtml,
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                                webViewRef = this
                            }
                        },
                        update = { view ->
                            webViewRef = view
                            val lastLoaded = view.tag as? Pair<*, *>
                            val currentKey = wrappedHtml to reloadNonce
                            if (lastLoaded != currentKey) {
                                view.tag = currentKey
                                view.loadDataWithBaseURL(
                                    "about:blank",
                                    wrappedHtml,
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("artifact_sandbox_webview"),
                    )
                }
            }
        }
    }
}
