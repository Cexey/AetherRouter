package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.VoiceUiState
import kotlin.math.abs

/**
 * Full-screen On-Device Voice Mode overlay powered by Android SpeechRecognizer & TextToSpeech.
 * Displays a live responsive RMS amplitude waveform Canvas, real-time partial/final transcript,
 * Auto-Send / Auto-Speak toggles, and TTS playback controls.
 */
@Composable
fun VoiceModeOverlay(
    voiceState: VoiceUiState,
    isStreamingResponse: Boolean,
    onClose: () -> Unit,
    onStartListening: (autoSend: Boolean, autoSpeakReply: Boolean) -> Unit,
    onStopListening: () -> Unit,
    onSendTranscriptNow: (transcript: String, autoSpeakReply: Boolean) -> Unit,
    onStopSpeaking: () -> Unit,
) {
    var autoSend by rememberSaveable { mutableStateOf(true) }
    var autoSpeakReply by rememberSaveable { mutableStateOf(true) }

    BackHandler {
        onStopListening()
        onClose()
    }

    val smoothAmp by animateFloatAsState(
        targetValue = if (voiceState.isListening) voiceState.amplitude.coerceIn(0.08f, 1f) else 0.06f,
        animationSpec = spring(stiffness = 280f, dampingRatio = 0.65f),
        label = "voice_amplitude",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
        ),
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "On-Device Voice Mode",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = when {
                            voiceState.isListening -> "Listening to microphone…"
                            voiceState.isSpeaking -> "Speaking reply aloud…"
                            isStreamingResponse -> "Generating response…"
                            else -> "Tap the mic to speak"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                IconButton(
                    onClick = {
                        onStopListening()
                        onClose()
                    },
                    modifier = Modifier.testTag("voice_mode_close_button"),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close Voice Mode")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mode chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = autoSend,
                    onClick = { autoSend = !autoSend },
                    label = { Text("Auto-Send") },
                    modifier = Modifier.testTag("voice_autosend_chip"),
                )
                FilterChip(
                    selected = autoSpeakReply,
                    onClick = { autoSpeakReply = !autoSpeakReply },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = { Text("Auto-Speak Reply") },
                    modifier = Modifier.testTag("voice_autospeak_chip"),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Live RMS Amplitude Waveform Canvas (uses DrawScope size exclusively)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
            ) {
                val barCount = 19
                val gap = size.width / (barCount * 2f)
                val barWidth = (size.width - gap * (barCount + 1)) / barCount
                val centerIndex = (barCount - 1) / 2f
                val maxBarH = size.height * 0.88f

                for (i in 0 until barCount) {
                    val distRatio = 1f - (abs(i - centerIndex) / (centerIndex + 1f))
                    val barFactor = (0.15f + smoothAmp * (0.45f + distRatio * 0.55f)).coerceIn(0.12f, 1f)
                    val h = maxBarH * barFactor
                    val x = gap + i * (barWidth + gap)
                    val y = (size.height - h) / 2f
                    drawRoundRect(
                        color = if (i % 2 == 0) primaryColor else tertiaryColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Live Transcript Card
            val shownTranscript = voiceState.partialTranscript.ifBlank { voiceState.finalTranscript }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = shownTranscript.ifBlank {
                            voiceState.errorMessage ?: "Speak naturally — your live transcript will appear here."
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (shownTranscript.isNotBlank()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                        textAlign = TextAlign.Center,
                    )
                    if (shownTranscript.isNotBlank() && !voiceState.isListening && !autoSend) {
                        Button(
                            onClick = {
                                onSendTranscriptNow(shownTranscript, autoSpeakReply)
                                onClose()
                            },
                            modifier = Modifier.testTag("voice_send_transcript_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send to Chat")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (voiceState.isSpeaking) {
                OutlinedButton(
                    onClick = onStopSpeaking,
                    modifier = Modifier
                        .padding(bottom = 14.dp)
                        .testTag("voice_stop_tts_button"),
                ) {
                    Icon(Icons.Outlined.VolumeOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Speaking")
                }
            }

            // Prominent Pulsing Mic Control
            Surface(
                shape = CircleShape,
                color = if (voiceState.isListening) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                tonalElevation = 6.dp,
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (voiceState.isListening) {
                            onStopListening()
                        } else {
                            onStartListening(autoSend, autoSpeakReply)
                        }
                    }
                    .testTag("voice_mic_toggle_button"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (voiceState.isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = if (voiceState.isListening) "Stop listening" else "Start listening",
                        tint = if (voiceState.isListening) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
