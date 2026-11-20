package com.example.service

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PlaybackState
import com.example.model.ScreenTextNode
import com.example.model.SupportedLanguages
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.StudioMint
import com.example.ui.theme.WarmCoral
import kotlin.math.roundToInt

@Composable
fun VoxLectOverlayRoot(
    engine: VoxLectEngine,
    onDragBubble: (dx: Float, dy: Float) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onCloseService: () -> Unit
) {
    val isExpanded by engine.isOverlayExpanded.collectAsStateWithLifecycle()
    val isPickerActive by engine.isScreenPickerActive.collectAsStateWithLifecycle()
    val playbackState by engine.playbackState.collectAsStateWithLifecycle()
    val settings by engine.settings.collectAsStateWithLifecycle()
    val voices by engine.availableVoices.collectAsStateWithLifecycle()
    val screenNodes by engine.capturedScreenNodes.collectAsStateWithLifecycle()
    val accessibilityConnected by VoxLectAccessibilityService.isConnected.collectAsStateWithLifecycle()
    val lastOriginal by engine.lastOriginalText.collectAsStateWithLifecycle()
    val lastTranslated by engine.lastTranslatedText.collectAsStateWithLifecycle()

    when {
        isPickerActive -> {
            ScreenSelectorHud(
                nodes = screenNodes,
                accessibilityConnected = accessibilityConnected,
                onSelectSingle = { node ->
                    engine.closeScreenSelector()
                    engine.processAndSpeak(node.text, sourceLabel = "Sélecteur écran")
                },
                onSelectMultiple = { selectedNodes ->
                    val combined = selectedNodes.joinToString("\n\n") { it.text }
                    engine.closeScreenSelector()
                    engine.processAndSpeak(combined, sourceLabel = "Sélection (${selectedNodes.size})")
                },
                onReadAll = {
                    val combined = screenNodes.joinToString("\n\n") { it.text }
                    engine.closeScreenSelector()
                    if (combined.isNotBlank()) {
                        engine.processAndSpeak(combined, sourceLabel = "Écran complet")
                    }
                },
                onReadClipboard = {
                    engine.closeScreenSelector()
                    engine.readClipboard()
                },
                onRefreshNodes = { engine.openScreenSelector() },
                onOpenAccessibilitySettings = {
                    engine.closeScreenSelector()
                    onOpenAccessibilitySettings()
                },
                onClose = { engine.closeScreenSelector() }
            )
        }

        isExpanded -> {
            FloatingControlPanel(
                playbackState = playbackState,
                speechRate = settings.speechRate,
                selectedVoiceId = settings.selectedVoiceId,
                voices = voices,
                translationEnabled = settings.translationEnabled,
                targetLanguageCode = settings.targetLanguageCode,
                lastOriginal = lastOriginal,
                lastTranslated = lastTranslated,
                onOpenSelector = { engine.openScreenSelector() },
                onReadClipboard = { engine.readClipboard() },
                onTogglePlayPause = { engine.togglePlayPause() },
                onStopPlayback = { engine.stopPlayback() },
                onChangeRate = { engine.setSpeechRate(it) },
                onSelectVoice = { engine.selectVoice(it) },
                onToggleTranslation = { engine.setTranslationEnabled(it) },
                onSelectLanguage = { engine.setTargetLanguage(it) },
                onMinimize = { engine.setOverlayExpanded(false) },
                onCloseService = onCloseService
            )
        }

        else -> {
            FloatingBubblePill(
                playbackState = playbackState,
                onClick = { engine.setOverlayExpanded(true) },
                onDrag = onDragBubble
            )
        }
    }
}

@Composable
private fun FloatingBubblePill(
    playbackState: PlaybackState,
    onClick: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val isActive = playbackState is PlaybackState.Speaking || playbackState is PlaybackState.Translating
    val infiniteTransition = rememberInfiniteTransition(label = "bubble_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val borderBrush = if (playbackState is PlaybackState.Translating) {
        Brush.linearGradient(listOf(WarmCoral, ElectricCyan))
    } else {
        Brush.linearGradient(listOf(ElectricCyan, StudioMint))
    }

    Surface(
        modifier = Modifier
            .padding(4.dp)
            .size(58.dp)
            .scale(pulseScale)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable(onClick = onClick)
            .testTag("floating_bubble"),
        shape = CircleShape,
        color = ObsidianSurface.copy(alpha = 0.96f),
        shadowElevation = 10.dp,
        border = BorderStroke(2.dp, borderBrush)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (playbackState is PlaybackState.Translating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = WarmCoral,
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "Ouvrir le panneau flottant VoxLect",
                    tint = if (isActive) ElectricCyan else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingControlPanel(
    playbackState: PlaybackState,
    speechRate: Float,
    selectedVoiceId: String?,
    voices: List<com.example.model.VoiceOption>,
    translationEnabled: Boolean,
    targetLanguageCode: String,
    lastOriginal: String,
    lastTranslated: String?,
    onOpenSelector: () -> Unit,
    onReadClipboard: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStopPlayback: () -> Unit,
    onChangeRate: (Float) -> Unit,
    onSelectVoice: (String?) -> Unit,
    onToggleTranslation: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onMinimize: () -> Unit,
    onCloseService: () -> Unit
) {
    var showVoicePicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .padding(8.dp)
            .widthIn(min = 300.dp, max = 336.dp)
            .heightIn(max = 540.dp)
            .testTag("floating_panel"),
        shape = RoundedCornerShape(24.dp),
        color = ObsidianBackground.copy(alpha = 0.97f),
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "VoxLect",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = statusLabel(playbackState),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (playbackState) {
                                is PlaybackState.Error -> MaterialTheme.colorScheme.error
                                is PlaybackState.Translating -> WarmCoral
                                is PlaybackState.Speaking -> StudioMint
                                else -> Color(0xFF94A3B8)
                            }
                        )
                    }
                }
                Row {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Minimize,
                            contentDescription = "Réduire en bulle",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(
                        onClick = onCloseService,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fermer la superposition",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Primary Capture Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenSelector,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("overlay_screen_selector_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color(0xFF04262E)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Sélecteur", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onReadClipboard,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("overlay_clipboard_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Copié", color = Color.White, maxLines = 1)
                }
            }

            // Active Playback & Live Preview
            if (lastOriginal.isNotBlank() || playbackState is PlaybackState.Error) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (playbackState is PlaybackState.Error) {
                            Text(
                                text = playbackState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = lastTranslated ?: lastOriginal,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onTogglePlayPause) {
                                Icon(
                                    imageVector = if (playbackState is PlaybackState.Speaking) {
                                        Icons.Filled.Pause
                                    } else {
                                        Icons.Filled.PlayArrow
                                    },
                                    contentDescription = "Lecture ou pause",
                                    tint = ElectricCyan
                                )
                            }
                            IconButton(onClick = onStopPlayback) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Arrêter",
                                    tint = WarmCoral
                                )
                            }
                        }
                    }
                }
            }

            // Speed Control
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vitesse vocale",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { onChangeRate((speechRate - 0.1f).coerceAtLeast(0.5f)) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Remove,
                                    contentDescription = "Ralentir",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = String.format("%.2fx", speechRate),
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                            IconButton(
                                onClick = { onChangeRate((speechRate + 0.1f).coerceAtMost(2.5f)) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Accélérer",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) { preset ->
                            val selected = kotlin.math.abs(speechRate - preset) < 0.04f
                            FilterChip(
                                selected = selected,
                                onClick = { onChangeRate(preset) },
                                label = { Text("${preset}x", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = ElectricCyan
                                )
                            )
                        }
                    }
                }
            }

            // Voice Picker Inline Toggle
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showVoicePicker = !showVoicePicker }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val activeVoiceName = voices.firstOrNull { it.id == selectedVoiceId }?.displayName
                        ?: "Voix automatique"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RecordVoiceOver,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Voix",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = activeVoiceName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = if (showVoicePicker) "Fermer" else "Changer",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }

                    AnimatedVisibility(visible = showVoicePicker) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                VoiceRowMini(
                                    title = "Voix automatique (système)",
                                    selected = selectedVoiceId == null,
                                    onClick = {
                                        onSelectVoice(null)
                                        showVoicePicker = false
                                    }
                                )
                            }
                            items(voices.take(20), key = { it.id }) { voice ->
                                VoiceRowMini(
                                    title = voice.displayName,
                                    selected = selectedVoiceId == voice.id,
                                    onClick = {
                                        onSelectVoice(voice.id)
                                        showVoicePicker = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Real-Time Translation Toggle & Target Language
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Translate,
                                contentDescription = null,
                                tint = WarmCoral,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Traduction temps réel",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                        Switch(
                            checked = translationEnabled,
                            onCheckedChange = onToggleTranslation,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = WarmCoral
                            )
                        )
                    }

                    AnimatedVisibility(visible = translationEnabled) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(SupportedLanguages.all, key = { it.code }) { lang ->
                                val selected = targetLanguageCode == lang.code
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectLanguage(lang.code) },
                                    label = {
                                        Text(
                                            text = "${lang.badge} · ${lang.frenchName}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = WarmCoral.copy(alpha = 0.25f),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRowMini(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) ElectricCyan.copy(alpha = 0.16f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) ElectricCyan else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ScreenSelectorHud(
    nodes: List<ScreenTextNode>,
    accessibilityConnected: Boolean,
    onSelectSingle: (ScreenTextNode) -> Unit,
    onSelectMultiple: (List<ScreenTextNode>) -> Unit,
    onReadAll: () -> Unit,
    onReadClipboard: () -> Unit,
    onRefreshNodes: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onClose: () -> Unit
) {
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var rootOriginX by remember { mutableStateOf(0) }
    var rootOriginY by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x8C090F1D))
            .onGloballyPositioned { coords ->
                val pos = coords.positionOnScreen()
                rootOriginX = pos.x.roundToInt()
                rootOriginY = pos.y.roundToInt()
            }
            .testTag("screen_selector_hud")
    ) {
        // Clickable bounding boxes over detected screen text
        nodes.forEach { node ->
            val leftPx = node.boundsInScreen.left - rootOriginX
            val topPx = node.boundsInScreen.top - rootOriginY
            val widthDp = with(density) { node.boundsInScreen.width().coerceAtLeast(24).toDp() }
            val heightDp = with(density) { node.boundsInScreen.height().coerceAtLeast(24).toDp() }
            val selectionOrder = selectedIds.indexOf(node.id)
            val isSelected = selectionOrder >= 0

            val borderColor = if (isSelected) WarmCoral else ElectricCyan
            val fillColor = if (isSelected) {
                WarmCoral.copy(alpha = 0.28f)
            } else {
                ElectricCyan.copy(alpha = 0.14f)
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx, topPx) }
                    .size(widthDp, heightDp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(fillColor)
                    .border(
                        width = if (isSelected) 2.5.dp else 1.5.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        if (multiSelectMode) {
                            if (isSelected) {
                                selectedIds.remove(node.id)
                            } else {
                                selectedIds.add(node.id)
                            }
                        } else {
                            onSelectSingle(node)
                        }
                    }
            ) {
                if (isSelected) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp),
                        shape = CircleShape,
                        color = WarmCoral
                    ) {
                        Text(
                            text = "${selectionOrder + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Top Control Bar
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = ObsidianBackground.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sélecteur d'écran VoxLect",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        Text(
                            text = if (nodes.isNotEmpty()) {
                                "${nodes.size} zones détectées · Touchez un texte à lire"
                            } else {
                                "Activez l'accessibilité ou rafraîchissez"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    IconButton(onClick = onRefreshNodes) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Actualiser l'écran",
                            tint = ElectricCyan
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fermer le sélecteur",
                            tint = Color.White
                        )
                    }
                }

                if (nodes.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = multiSelectMode,
                            onClick = {
                                multiSelectMode = !multiSelectMode
                                if (!multiSelectMode) selectedIds.clear()
                            },
                            label = { Text("Multi-sélection") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WarmCoral.copy(alpha = 0.25f),
                                selectedLabelColor = Color.White
                            )
                        )
                        OutlinedButton(
                            onClick = onReadAll,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Tout lire", color = ElectricCyan)
                        }
                    }
                }
            }
        }

        // Empty / Permission prompt if no nodes detected
        if (nodes.isEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .widthIn(max = 340.dp),
                shape = RoundedCornerShape(24.dp),
                color = ObsidianBackground.copy(alpha = 0.97f),
                border = BorderStroke(1.dp, WarmCoral.copy(alpha = 0.45f)),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessibilityNew,
                        contentDescription = null,
                        tint = WarmCoral,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (!accessibilityConnected) {
                            "Sélecteur d'accessibilité requis"
                        } else {
                            "Aucun texte capturable sur cet écran"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = if (!accessibilityConnected) {
                            "Activez « Sélecteur VoxLect » dans les paramètres d'accessibilité pour sélectionner directement n'importe quel paragraphe dans vos autres applications."
                        } else {
                            "Essayez d'actualiser sur une autre application, utilisez le menu système « Lire avec VoxLect » en sélectionnant un texte, ou lisez le presse-papiers."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    if (!accessibilityConnected) {
                        Button(
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color(0xFF04262E)
                            )
                        ) {
                            Text("Activer dans Accessibilité")
                        }
                    }
                    OutlinedButton(
                        onClick = onReadClipboard,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text("Lire le presse-papiers", color = Color.White)
                    }
                }
            }
        }

        // Multi-Select Bottom Action Bar
        AnimatedVisibility(
            visible = multiSelectMode && selectedIds.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            val selectedNodes = selectedIds.mapNotNull { id -> nodes.firstOrNull { it.id == id } }
            Button(
                onClick = { onSelectMultiple(selectedNodes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmCoral,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Lire la sélection (${selectedNodes.size})")
            }
        }
    }
}

private fun statusLabel(state: PlaybackState): String = when (state) {
    is PlaybackState.Idle -> "Prêt à lire"
    is PlaybackState.Translating -> "Traduction temps réel…"
    is PlaybackState.Speaking -> "Lecture ${state.currentChunk}/${state.totalChunks}"
    is PlaybackState.Paused -> "En pause (${state.currentChunk}/${state.totalChunks})"
    is PlaybackState.Error -> "Erreur"
}
