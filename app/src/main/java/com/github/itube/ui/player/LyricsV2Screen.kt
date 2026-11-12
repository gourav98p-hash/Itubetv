package com.github.itube.ui.player

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.github.itube.MusicPlayerState
import com.github.itube.LyricsLine
import com.github.itube.R
import com.github.itube.highQualityThumbnail
import com.github.itube.ui.components.WavySlider
import com.github.itube.ui.components.shimmerBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private fun unescapeLyricsHtml(text: String): String {
    if (!text.contains('&')) return text
    val cleaned = text
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .replace("&#x27;", "'")
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
    return try {
        if (cleaned.contains('&')) {
            android.text.Html.fromHtml(cleaned, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            cleaned
        }
    } catch (_: Throwable) {
        cleaned
    }
}

// ──────────────────────────────────────────────────────────────────────
// AirBeats Lyrics Constants
// ──────────────────────────────────────────────────────────────────────

val SFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_bold)
)

private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L
private val HEAD_LYRICS_ENTRY = AirBeatsLyricsEntry(time = 0L, text = "")

data class AirBeatsWordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val isBackground: Boolean = false
)

data class AirBeatsLyricsEntry(
    val time: Long,
    val text: String,
    val words: List<AirBeatsWordTimestamp>? = null,
    val agent: String? = null,
) : Comparable<AirBeatsLyricsEntry> {
    override fun compareTo(other: AirBeatsLyricsEntry): Int = (time - other.time).toInt()
}

/**
 * Exact AirBeats Lyrics Screen (AirBeatsLyricsScreen + LyricsV2).
 * Direct 1:1 port of AirBeats immersive lyrics interface featuring:
 * - Fullscreen atmospheric album artwork blur (48dp)
 * - Header: down chevron, "NOW PLAYING" + track title, 3-dot options
 * - AirBeats LyricsV2 engine with Apple Music style line-by-line scroll and 60fps frame loop
 * - Two-layer word liquid fill sweep with gradient mask (BlendMode.DstIn)
 * - Floating pop-up bounce on actively sung words (-4dp float with FastOutSlowInEasing)
 * - Glowing shadow build-up on active words
 * - AirBeats smoothFadingEdge on top and bottom of lyrics column
 * - Interactive line click-to-seek and long-press multi-selection with share
 * - AirBeats WavySlider seekbar, audio quality badge, playback controls and BigSeekBar volume slider
 */
@Composable
fun LyricsV2Screen(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = { MusicPlayerState.lyricsOpen = false },
) {
    val track = MusicPlayerState.currentTrack ?: return
    val context = LocalContext.current
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editedLyricsText by rememberSaveable(track.id) { mutableStateOf<String?>(null) }
    val displayedLyrics = remember(MusicPlayerState.lyrics, editedLyricsText) {
        editedLyricsText
            ?.takeIf { it.isNotBlank() }
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.mapIndexed { index, text ->
                LyricsLine(
                    timeMs = MusicPlayerState.lyrics.getOrNull(index)?.timeMs ?: index * 5_000L,
                    text = text,
                )
            }
            ?.toList()
            ?: MusicPlayerState.lyrics
    }

    val position = MusicPlayerState.positionMs
    val duration = if (MusicPlayerState.durationMs > 0) MusicPlayerState.durationMs else 1L
    val isPlaying = MusicPlayerState.isPlaying
    val isLoading = MusicPlayerState.isLoading

    BackHandler(enabled = MusicPlayerState.lyricsOpen) {
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Atmospheric artwork background blur (AirBeats 48dp blur)
        AsyncImage(
            model = track.thumbnailUrl?.highQualityThumbnail(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 48.dp)
                .graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }
        )

        // Dark tint overlay (AirBeats 0.45f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )

        // Main Portrait Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // ── 1. AirBeats Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Down arrow button (left)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.expand_more),
                        contentDescription = "Close",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Centered Now Playing title info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.8.sp
                        ),
                        color = Color.White.copy(alpha = 0.65f)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }

                // More options button (right)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { showOptionsSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.more_horiz),
                        contentDescription = "More Options",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ── 2. AirBeats LyricsV2 Content Area ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                AirBeatsLyricsV2Content(
                    lyrics = displayedLyrics,
                    sliderPositionProvider = { sliderPosition },
                    onSeek = { MusicPlayerState.seekTo(it) }
                )
            }

            // ── 3. AirBeats Bottom Playback & Volume Controls ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val currentDisplayPos = sliderPosition ?: position

                // AirBeats Wavy Seek Bar (SquigglySlider)
                WavySlider(
                    value = currentDisplayPos.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = {
                        sliderPosition = it.toLong().coerceIn(0L, duration)
                    },
                    onValueChangeFinished = {
                        sliderPosition?.let { MusicPlayerState.seekTo(it) }
                        sliderPosition = null
                    },
                    valueRange = 0f..duration.toFloat(),
                    isPlaying = isPlaying,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    thumbColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                // Time display row + Audio Quality Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentDisplayPos),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    // Audio Quality Badge Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.graphic_eq),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.85f)),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "YouTube • 128k",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = Color.White.copy(alpha = 0.90f)
                            )
                        }
                    }

                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Playback Control Buttons: Repeat, Previous, Play/Pause, Next, Shuffle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Repeat Button
                    IconButton(
                        onClick = { MusicPlayerState.toggleRepeat() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Image(
                            painter = painterResource(
                                when (MusicPlayerState.repeatMode) {
                                    Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                    Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                                    else -> R.drawable.repeat
                                }
                            ),
                            contentDescription = "Repeat",
                            colorFilter = ColorFilter.tint(
                                if (MusicPlayerState.repeatMode == Player.REPEAT_MODE_OFF)
                                    Color.White.copy(alpha = 0.40f)
                                else Color.White
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Previous Button
                    IconButton(
                        onClick = { MusicPlayerState.skipPrevious() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.skip_previous),
                            contentDescription = "Previous",
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Play/Pause Button (56dp prominent)
                    IconButton(
                        onClick = { MusicPlayerState.togglePlayPause() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        if (isLoading && !isPlaying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Image(
                                painter = painterResource(
                                    if (isPlaying) R.drawable.pause else R.drawable.play
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Next Button
                    IconButton(
                        onClick = { MusicPlayerState.skipNext() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = "Next",
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Shuffle Button
                    IconButton(
                        onClick = { MusicPlayerState.toggleShuffle() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Image(
                            painter = painterResource(
                                if (MusicPlayerState.shuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle
                            ),
                            contentDescription = "Shuffle",
                            colorFilter = ColorFilter.tint(
                                if (MusicPlayerState.shuffleEnabled) Color.White else Color.White.copy(alpha = 0.40f)
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // AirBeats BigSeekBar Volume Control Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Image(
                        painter = painterResource(R.drawable.volume_off),
                        contentDescription = "Volume Off",
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.70f)),
                        modifier = Modifier.size(18.dp)
                    )

                    AirBeatsBigSeekBar(
                        progressProvider = { MusicPlayerState.volume },
                        onProgressChange = { MusicPlayerState.adjustVolume(it) },
                        color = Color.White,
                        background = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .padding(horizontal = 14.dp)
                    )

                    Image(
                        painter = painterResource(R.drawable.volume_up),
                        contentDescription = "Volume Up",
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.70f)),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))
            }
        }

        if (showOptionsSheet) {
            LyricsActionsSheet(
                onDismiss = { showOptionsSheet = false },
                onSearch = {
                    showOptionsSheet = false
                    searchLyricsOnline(context, track.title, track.artists)
                },
                onEdit = {
                    showOptionsSheet = false
                    showEditor = true
                },
                onRefresh = {
                    showOptionsSheet = false
                    editedLyricsText = null
                    MusicPlayerState.refreshLyrics()
                },
                onShare = {
                    showOptionsSheet = false
                    shareLyrics(context, track.title, track.artists, displayedLyrics)
                },
            )
        }

        if (showEditor) {
            LyricsEditorSheet(
                initialText = editedLyricsText ?: displayedLyrics.joinToString("\n") { it.text },
                onDismiss = { showEditor = false },
                onSave = { edited ->
                    editedLyricsText = edited
                    showEditor = false
                },
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// AirBeats LyricsV2 Composable (1:1 Port)
// ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun AirBeatsLyricsV2Content(
    lyrics: List<LyricsLine>,
    sliderPositionProvider: () -> Long?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onUserInteraction: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lyricsLoading = MusicPlayerState.lyricsLoading

    val textColor = Color.White
    val inactiveAlpha = 0.38f
    val lyricsTextSize = 27f
    val lyricsLineSpacing = 1.25f

    // Selection mode state (from AirBeats)
    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }

    // Parse into AirBeatsLyricsEntry
    val entriesWithWords: List<AirBeatsLyricsEntry> = remember(lyrics) {
        if (lyrics.isEmpty()) return@remember emptyList()
        val mapped = lyrics.mapIndexed { index, line ->
            val cleanLineText = unescapeLyricsHtml(line.text)
            val nextTime = if (index < lyrics.lastIndex) lyrics[index + 1].timeMs else line.timeMs + 5000L
            val lineDurationMs = (nextTime - line.timeMs).coerceAtLeast(600L)

            val words = if (line.words.isNotEmpty()) {
                line.words.map { w ->
                    val cleanWord = unescapeLyricsHtml(w.text)
                    AirBeatsWordTimestamp(
                        text = if (cleanWord.endsWith(" ")) cleanWord else "$cleanWord ",
                        startTime = w.startMs / 1000.0,
                        endTime = w.endMs / 1000.0
                    )
                }
            } else {
                synthesizeWordTimestamps(cleanLineText, line.timeMs, lineDurationMs)
            }
            AirBeatsLyricsEntry(time = line.timeMs, text = cleanLineText, words = words)
        }
        listOf(HEAD_LYRICS_ENTRY) + mapped
    }

    val isSynced = remember(entriesWithWords) { entriesWithWords.size > 1 }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var currentLineIndex by remember { mutableIntStateOf(0) }

    // 60fps frame-accurate position loop
    LaunchedEffect(entriesWithWords, isSynced) {
        if (!isSynced || entriesWithWords.isEmpty()) return@LaunchedEffect
        while (isActive) {
            val sliderPos = sliderPositionProvider()
            val pos = sliderPos ?: MusicPlayerState.positionMs

            currentPositionMs = pos
            currentLineIndex = findCurrentLineIndex(entriesWithWords, currentPositionMs)
            delay(16L) // ~60fps
        }
    }

    // Scroll state
    val listState = rememberLazyListState()
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    // Detect manual scrolling
    val nestedScrollConnection = remember(onUserInteraction) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                    isManualScrolling = true
                    lastManualScrollTime = System.currentTimeMillis()
                    onUserInteraction()
                }
                return Offset.Zero
            }
        }
    }

    // Resume auto-scroll after timeout (3s)
    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    // Auto-scroll to active line with 35% viewport center bias
    LaunchedEffect(currentLineIndex, isManualScrolling) {
        if (isManualScrolling || !isSynced) return@LaunchedEffect
        if (currentLineIndex < 0 || currentLineIndex >= entriesWithWords.size) return@LaunchedEffect

        val visibleInfo = listState.layoutInfo
        val viewportHeight = visibleInfo.viewportSize.height
        val targetOffset = (viewportHeight * 0.35f).toInt()

        val distance = abs(currentLineIndex - listState.firstVisibleItemIndex)
        if (distance > 15) {
            listState.scrollToItem((currentLineIndex - 2).coerceAtLeast(0), 0)
        }
        listState.animateScrollToItem(
            index = currentLineIndex,
            scrollOffset = -targetOffset
        )
    }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        if (lyricsLoading) {
            // AirBeats shimmer skeleton
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                val brush = shimmerBrush()
                val widths = listOf(0.7f, 0.9f, 0.6f, 0.8f, 0.5f, 0.75f, 0.65f)
                widths.forEach { fraction ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(brush)
                    )
                }
            }
            return@BoxWithConstraints
        }

        if (entriesWithWords.isEmpty() || entriesWithWords.size <= 1) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Lyrics not available for this song",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
            return@BoxWithConstraints
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .smoothFadingEdge(vertical = 80.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            contentPadding = PaddingValues(
                start = 22.dp,
                end = 22.dp,
                top = 24.dp,
                bottom = 280.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            itemsIndexed(
                items = entriesWithWords,
                key = { index, entry -> "${index}_${entry.time}" }
            ) { index, item ->
                if (item == HEAD_LYRICS_ENTRY) {
                    Spacer(modifier = Modifier.height(16.dp))
                    return@itemsIndexed
                }

                val textAlign = TextAlign.Start
                val horizontalAlignment = Alignment.Start

                val isActive = isSynced && index == currentLineIndex
                val isPast = isSynced && index < currentLineIndex

                val distanceFromActive = if (isSynced) abs(index - currentLineIndex) else 0
                val lineAlpha = when {
                    !isSynced -> 0.95f
                    isActive -> 1f
                    else -> (inactiveAlpha - (distanceFromActive - 1) * 0.04f).coerceIn(0.18f, inactiveAlpha)
                }
                val wordLineAlpha = if (item.words != null && isSynced) 1f else lineAlpha

                val isSelected = selectedIndices.contains(index)
                val lineScale = if (isActive && isSynced) 1.04f else 1f

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = lineScale
                            scaleY = lineScale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            alpha = wordLineAlpha
                        }
                        .background(
                            color = if (isSelected && isSelectionModeActive)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 4.dp)
                        .combinedClickable(
                            enabled = true,
                            onClick = {
                                onUserInteraction()
                                if (isSelectionModeActive) {
                                    if (isSelected) {
                                        selectedIndices.remove(index)
                                        if (selectedIndices.isEmpty()) isSelectionModeActive = false
                                    } else {
                                        if (selectedIndices.size < 5) selectedIndices.add(index)
                                    }
                                } else if (isSynced && item.time > 0) {
                                    onSeek(item.time)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionModeActive) {
                                    isSelectionModeActive = true
                                    selectedIndices.add(index)
                                } else if (!isSelected && selectedIndices.size < 5) {
                                    selectedIndices.add(index)
                                }
                            }
                        ),
                    horizontalAlignment = horizontalAlignment
                ) {
                    if (!item.words.isNullOrEmpty() && isSynced) {
                        // Word-synced fluid rendering with SF Pro Display
                        AirBeatsLyricsLineV2(
                            words = item.words,
                            isActive = isActive,
                            isPast = isPast,
                            currentPositionMs = currentPositionMs,
                            textColor = textColor,
                            inactiveAlpha = inactiveAlpha,
                            baseFontSize = lyricsTextSize,
                            textAlign = textAlign
                        )
                    } else {
                        // Plain text rendering with SF Pro Display
                        Text(
                            text = item.text,
                            style = TextStyle(
                                fontFamily = SFProDisplay,
                                fontSize = if (isSynced) 27.sp else 23.sp,
                                fontWeight = if (isActive) FontWeight.W800 else FontWeight.W700,
                                letterSpacing = (-0.6).sp,
                                lineHeight = 33.sp
                            ),
                            color = textColor.copy(alpha = if (isActive) 1f else inactiveAlpha),
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Bottom spacer for overscroll
            item {
                Spacer(modifier = Modifier.height(280.dp))
            }
        }

        // Resume auto-scroll button
        if (isManualScrolling && isSynced) {
            FilledTonalButton(
                onClick = {
                    isManualScrolling = false
                    scope.launch {
                        val viewportHeight = listState.layoutInfo.viewportSize.height
                        listState.animateScrollToItem(
                            index = currentLineIndex,
                            scrollOffset = -(viewportHeight * 0.35f).toInt()
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.20f),
                    contentColor = Color.White
                )
            ) {
                Text(text = "Resume", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }

        // Selection mode bottom action bar (AirBeats)
        if (isSelectionModeActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape)
                            .clickable {
                                isSelectionModeActive = false
                                selectedIndices.clear()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = "Cancel",
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .background(
                                color = if (selectedIndices.isNotEmpty()) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable(enabled = selectedIndices.isNotEmpty()) {
                                if (selectedIndices.isNotEmpty()) {
                                    val sortedIndices = selectedIndices.sorted()
                                    val selectedText = sortedIndices
                                        .mapNotNull { entriesWithWords.getOrNull(it)?.text }
                                        .joinToString("\n")

                                    val trackInfo = MusicPlayerState.currentTrack
                                    val shareText = "\"$selectedText\"\n\n${trackInfo?.title} - ${trackInfo?.artists}"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Lyrics"))
                                    isSelectionModeActive = false
                                    selectedIndices.clear()
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_share),
                            contentDescription = "Share",
                            colorFilter = ColorFilter.tint(Color.Black),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Share",
                            color = Color.Black,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// AirBeats Word-level and Line-level Composables
// ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AirBeatsLyricsLineV2(
    words: List<AirBeatsWordTimestamp>,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    baseFontSize: Float,
    textAlign: TextAlign,
) {
    val arrangement = when (textAlign) {
        TextAlign.Center -> Arrangement.Center
        TextAlign.End -> Arrangement.End
        else -> Arrangement.Start
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = arrangement
    ) {
        words.forEachIndexed { wordIndex, word ->
            if (word.text == " ") {
                Text(
                    text = " ",
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontSize = baseFontSize.sp,
                        fontWeight = if (isActive) FontWeight.W800 else FontWeight.W700,
                        letterSpacing = (-0.6).sp,
                        lineHeight = 33.sp
                    ),
                    color = Color.Transparent
                )
                return@forEachIndexed
            }
            if (word.text == "\n") {
                Spacer(modifier = Modifier.fillMaxWidth())
                return@forEachIndexed
            }

            AirBeatsAnimatedWordV2(
                word = word,
                isLineActive = isActive,
                isLinePast = isPast,
                currentPositionMs = currentPositionMs,
                textColor = textColor,
                inactiveAlpha = inactiveAlpha,
                fontSize = baseFontSize
            )
        }
    }
}

@Composable
private fun AirBeatsAnimatedWordV2(
    word: AirBeatsWordTimestamp,
    isLineActive: Boolean,
    isLinePast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    fontSize: Float,
) {
    val wordStartMs = (word.startTime * 1000).toLong()
    val wordEndMs = (word.endTime * 1000).toLong()
    val wordDuration = (wordEndMs - wordStartMs).coerceAtLeast(1L)

    val isWordComplete = currentPositionMs >= wordEndMs
    val isWordActive = currentPositionMs in wordStartMs until wordEndMs

    val progress = when {
        isWordComplete -> 1f
        currentPositionMs <= wordStartMs -> 0f
        else -> ((currentPositionMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
    }

    // Bounce & Float Animation
    val sinProgress = sin(progress * PI).toFloat()
    val wordScale = 1f + (0.015f * sinProgress)

    val targetFloat = if (isWordActive) -4f * sinProgress else 0f
    val floatOffset by animateFloatAsState(
        targetValue = targetFloat,
        animationSpec = tween(
            durationMillis = if (isWordActive) 50 else 350,
            easing = FastOutSlowInEasing
        ),
        label = "wordFloat"
    )

    // Glow Intensity
    val glowProgress = (progress * 2f).coerceAtMost(1f)
    val glowAlpha = if (isWordActive) glowProgress * 0.45f else 0f
    val glowRadius = if (isWordActive) glowProgress * 12f else 0f

    val fontWeight = if (isLineActive) FontWeight.W800 else FontWeight.W700
    val density = LocalDensity.current

    val wordStyle = TextStyle(
        fontFamily = SFProDisplay,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        fontStyle = FontStyle.Normal,
        letterSpacing = (-0.6).sp,
        lineHeight = 33.sp
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = floatOffset * density.density
                scaleX = wordScale
                scaleY = wordScale
            }
    ) {
        // Layer 1: Base text (always dimmed)
        Text(
            text = word.text,
            style = wordStyle,
            color = textColor.copy(alpha = inactiveAlpha)
        )

        // Layer 2: Filled overlay with liquid sweep mask + glow (BlendMode.DstIn)
        if (isWordComplete || isWordActive || isLinePast) {
            Text(
                text = word.text,
                style = wordStyle.copy(
                    shadow = if (glowAlpha > 0f) {
                        Shadow(
                            color = textColor.copy(alpha = glowAlpha),
                            offset = Offset.Zero,
                            blurRadius = glowRadius.coerceAtLeast(1f)
                        )
                    } else null
                ),
                color = textColor,
                modifier = if (isWordActive && !isWordComplete) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val edgeWidth = 8.dp.toPx()
                            val center = (size.width + edgeWidth * 2) * progress - edgeWidth
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = center - edgeWidth,
                                    endX = center + edgeWidth
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                } else {
                    Modifier
                }
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Helper: Synthesizes word timestamps for text lines (AirBeats 1:1)
// ──────────────────────────────────────────────────────────────────────

private fun synthesizeWordTimestamps(
    text: String,
    startTimeMs: Long,
    durationMs: Long,
): List<AirBeatsWordTimestamp> {
    if (text.isBlank() || startTimeMs < 0) return emptyList()
    val lineStartSec = startTimeMs / 1000.0
    val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()

    val totalChars = tokens.sumOf { it.length }.coerceAtLeast(1)
    val words = mutableListOf<AirBeatsWordTimestamp>()
    var currentOffsetMs = 0.0

    tokens.forEachIndexed { wordIdx, token ->
        val weight = token.length.toDouble() / totalChars
        val wordDurMs = durationMs * weight
        val wordStartSec = lineStartSec + (currentOffsetMs / 1000.0)
        val wordEndSec = wordStartSec + (wordDurMs / 1000.0)

        val wordText = if (wordIdx < tokens.lastIndex) "$token " else token
        words.add(
            AirBeatsWordTimestamp(
                text = wordText,
                startTime = wordStartSec,
                endTime = wordEndSec
            )
        )
        currentOffsetMs += wordDurMs
    }
    return words
}

// Binary search current active lyric line index (AirBeats)
private fun findCurrentLineIndex(
    lines: List<AirBeatsLyricsEntry>,
    position: Long,
): Int {
    if (lines.isEmpty()) return -1
    var low = 0
    var high = lines.lastIndex
    while (low <= high) {
        val mid = (low + high).ushr(1)
        if (lines[mid].time <= position) {
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return high.coerceIn(0, lines.lastIndex)
}

// AirBeats smoothFadingEdge modifier
private fun Modifier.smoothFadingEdge(
    vertical: Dp = 80.dp,
): Modifier = graphicsLayer(alpha = 0.99f)
    .drawWithContent {
        drawContent()
        val topPx = vertical.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.3f to Color.Black.copy(alpha = 0.15f),
                    0.5f to Color.Black.copy(alpha = 0.4f),
                    0.7f to Color.Black.copy(alpha = 0.7f),
                    0.85f to Color.Black.copy(alpha = 0.9f),
                    1.0f to Color.Black,
                ),
                startY = 0f,
                endY = topPx,
            ),
            blendMode = BlendMode.DstIn,
        )
        val bottomPx = vertical.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Black,
                    0.15f to Color.Black.copy(alpha = 0.9f),
                    0.3f to Color.Black.copy(alpha = 0.7f),
                    0.5f to Color.Black.copy(alpha = 0.4f),
                    0.7f to Color.Black.copy(alpha = 0.15f),
                    1.0f to Color.Transparent,
                ),
                startY = size.height - bottomPx,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

// AirBeats BigSeekBar iOS-style pill slider
@Composable
private fun AirBeatsBigSeekBar(
    progressProvider: () -> Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color.White.copy(alpha = 0.15f),
    color: Color = Color.White,
) {
    var width by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(16.dp))
            .onPlaced {
                width = it.size.width.toFloat()
            }
            .pointerInput(progressProvider) {
                detectHorizontalDragGestures { _, dragAmount ->
                    val delta = if (width > 0f) dragAmount * 1.2f / width else 0f
                    onProgressChange((progressProvider() + delta).coerceIn(0f, 1f))
                }
            }
    ) {
        drawRect(color = background)
        drawRect(
            color = color,
            size = size.copy(width = size.width * progressProvider().coerceIn(0f, 1f)),
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format("%d:%02d", minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsActionsSheet(
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1C1C1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text("Lyrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LyricsActionRow(Icons.Rounded.Search, "Search lyrics", "Find another lyrics source", onSearch)
            LyricsActionRow(Icons.Rounded.Edit, "Edit lyrics", "Correct the lyrics on this screen", onEdit)
            LyricsActionRow(Icons.Rounded.Refresh, "Refresh lyrics", "Load the latest synchronized lyrics", onRefresh)
            LyricsActionRow(Icons.Rounded.Share, "Share lyrics", "Share these lyrics with someone", onShare)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun LyricsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.62f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsEditorSheet(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1C1C1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text("Edit lyrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("One lyric line per row. Your edits stay on this screen.", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(280.dp).padding(top = 16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                minLines = 8,
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onSave(text) }) { Text("Save") }
            }
        }
    }
}

private fun searchLyricsOnline(context: android.content.Context, title: String, artist: String) {
    val query = "$title $artist lyrics"
    val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
    runCatching { context.startActivity(search) }.getOrElse {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")))
    }
}

private fun shareLyrics(context: android.content.Context, title: String, artist: String, lyrics: List<LyricsLine>) {
    val body = lyrics.joinToString("\n") { it.text }.ifBlank { "Lyrics are not available yet." }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$body\n\n$title — $artist")
    }, "Share lyrics"))
}
