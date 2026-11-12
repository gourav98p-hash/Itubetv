package com.github.itube

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.outlined.ContentCopy
import com.github.itube.ui.components.EqualizerSheet
import com.github.itube.ui.components.MaterialLoader
import com.github.itube.ui.components.VideoRowSkeleton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.github.itube.ui.components.BlurSetting
import com.github.itube.ui.components.TrackBlurPopup
import com.github.itube.ui.components.popupContainerColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW

// ── Geometry shared with the morph (MainActivity builds MorphGeometry from these) ──

// The video runs edge to edge as a plain rectangle, right under the status bar (Derek).
internal val StageTopGap = 0.dp
/** How far up the screen (share of its height) the video's top edge gets before the status bar starts turning black. */
private const val StatusBlackFromUp = 0.25f
/** Morph progress by which the status bar over the video is fully black. */
private const val StatusBlackEnd = 0.9f
internal val StageSideGap = 0.dp
internal val StageRadius = 0.dp
internal val MiniHeight = 104.dp
internal val MiniRadius = 18.dp
internal val MiniThumbStart = 0.dp
internal val MiniThumbWidth = 184.dp
internal val MiniThumbHeight = 104.dp
internal val MiniThumbRadius = MiniRadius
/**
 * The portrait video takes the video's own shape (Derek, 2026-10-10: no black bars), from
 * 4:5 for tall videos to 2.4:1 for wide ones; anything beyond fits inside with bars.
 */
internal const val StageMinAspect = 0.8f
internal const val StageMaxAspect = 2.4f
/**
 * The stage never takes more than this share of the window's height: in a short split-screen
 * pane a tall video would otherwise push the details (and half the video) off the bottom.
 * On a full phone screen 4:5 stays under it, so nothing changes there.
 */
internal const val StageMaxHeightShare = 0.6f
/** Morph progress after which the details and tint start fading in (tuning dial). */
private const val DetailsFadeStart = 0.55f

// ── Full player (portrait) ─────────────────────────────────────────────────────

@Composable
fun PlayerScreen(
    state: PlayerState,
    morph: PlayerMorph,
    onOpen: (VideoItem) -> Unit,
    onCollapse: () -> Unit,
    onFullscreen: () -> Unit,
    onPortraitFullscreen: () -> Unit,
    /** Room the floating player bar (the nav pill over the player) takes at the bottom. */
    bottomClearance: androidx.compose.ui.unit.Dp,
) {
    val c = MaterialTheme.colorScheme
    val item = state.current ?: return
    // Collapsing (or collapsed): the player gives up the screen immediately, so the
    // mini-player underneath answers taps and swipes even while the animation finishes.
    val interactive = state.expanded || morph.dragging
    // A drag on the video or details whose page goes away mid-gesture (turning to fullscreen)
    // never gets its release: settle it here, or the morph stays "dragging" for good.
    DisposableEffect(morph) { onDispose { morph.settle(0f, reducedMotion = false) } }
    val showDetails by remember { derivedStateOf { state.expanded || morph.dragging || morph.progress.value > DetailsFadeStart } }
    val details = state.details
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Audio only (Derek, 2026-10-25): the open player has no video space at all — the stage
    // folds away (the picture fading and lifting as its height closes) and unfolds for video.
    // Only on the fully open player: swiping it down unfolds it again, so the thumbnail still
    // lands in the mini-player. With the controls on the video (PlayerControls) it never folds:
    // the audio stage holds them (VideoStage).
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val enterPortraitFullscreen by rememberUpdatedState(onPortraitFullscreen)
    val portraitFsPull = remember { Animatable(0f) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val stageTopGapPx = with(density) { (top + StageTopGap).toPx() }
    val stageHeightPx = with(density) {
        val stageWidthDp = configuration.screenWidthDp.dp - StageSideGap * 2
        (stageWidthDp / state.stageAspect).toPx()
    }
    val portraitFsTravelPx = remember(screenHeightPx, stageTopGapPx, stageHeightPx) {
        ((screenHeightPx - stageHeightPx) / 2f - stageTopGapPx).coerceAtLeast(140f)
    }

    val detailsPull = remember(portraitFsTravelPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val current = portraitFsPull.value
                // When finger reverses direction while pulling down:
                if (source == NestedScrollSource.UserInput && delta < 0f && current > 0f) {
                    val maxConsumable = current * portraitFsTravelPx
                    val consumed = maxOf(delta, -maxConsumable)
                    val next = (current + consumed / portraitFsTravelPx).coerceIn(0f, 1f)
                    scope.launch { portraitFsPull.snapTo(next) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Overscroll pull at the top of the details list:
                if (source == NestedScrollSource.UserInput && available.y > 0f && morph.progress.value >= 0.99f) {
                    val current = portraitFsPull.value
                    if (current < 1f) {
                        val delta = available.y * 0.85f
                        val maxConsumable = (1f - current) * portraitFsTravelPx
                        val consumed = minOf(delta, maxConsumable)
                        val next = (current + consumed / portraitFsTravelPx).coerceIn(0f, 1f)
                        scope.launch { portraitFsPull.snapTo(next) }
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val current = portraitFsPull.value
                if (current <= 0f) return Velocity.Zero
                val shouldCommit = (current > 0.18f || available.y > 900f) && available.y > -500f
                if (shouldCommit) {
                    portraitFsPull.animateTo(
                        1f,
                        spring(dampingRatio = 0.86f, stiffness = 420f),
                        initialVelocity = available.y / portraitFsTravelPx,
                    )
                    enterPortraitFullscreen()
                    portraitFsPull.snapTo(0f)
                } else {
                    portraitFsPull.animateTo(
                        0f,
                        spring(dampingRatio = 0.86f, stiffness = 450f),
                        initialVelocity = available.y / portraitFsTravelPx,
                    )
                }
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return Velocity.Zero
            }
        }
    }
    val fold = remember { Animatable(if (AudioOnly.enabled) 1f else 0f) }
    LaunchedEffect(AudioOnly.enabled) {
        val target = if (AudioOnly.enabled) 1f else 0f
        if (reduced) fold.snapTo(target) else fold.animateTo(target, spring(dampingRatio = 1f, stiffness = 260f, visibilityThreshold = 0.0005f))
    }
    // Derek, 2026-10-26: over a video the status bar is pitch black (white icons); audio only
    // and the pages keep the page look. It fades from the page's own color once the video's top
    // edge is a quarter of the way up the screen (black before the growing frame reaches the
    // top), and out with the fold.
    val blackStatus = {
        val g = morph.geometry
        // The video's top edge travels from the mini-player's thumbnail to the stage's top.
        val start = if (g == null) StatusBlackFromUp else ((g.miniThumb.top - g.screen.height * (1f - StatusBlackFromUp)) / (g.miniThumb.top - g.stage.top))
        ramp(morph.progress.value, start.coerceIn(0f, StatusBlackEnd - 0.1f), StatusBlackEnd) * (1f - fold.value)
    }
    LaunchedEffect(Unit) { snapshotFlow { blackStatus() > 0.5f }.collect { StatusBarOverVideo.on = it } }
    DisposableEffect(Unit) { onDispose { StatusBarOverVideo.on = false } }

    val pageColor = c.surface
    val miniColor = c.surfaceContainerHighest
    Box(
        Modifier
            .fillMaxSize()
            // The morph's moving frame: the mini-player card growing to the full screen.
            .graphicsLayer {
                val g = morph.geometry ?: return@graphicsLayer
                val p = morph.progress.value
                // The page itself becomes the mini-player card. Its bottom edge holds at the
                // card's bottom until well into the open (and from there on the way down), so
                // it never sweeps over the tab bar.
                val full = Rect(Offset.Zero, size)
                val frame = Rect(
                    lerpF(g.miniCard.left, full.left, p), lerpF(g.miniCard.top, full.top, p),
                    lerpF(g.miniCard.right, full.right, p), lerpF(g.miniCard.bottom, full.bottom, ramp(p, 0.3f)),
                )
                shape = RoundRectClip(frame, lerpF(g.miniRadius, 0f, p.coerceIn(0f, 1f)))
                clip = true
            }
            .drawBehind {
                val p = morph.progress.value
                // Opaque throughout: at the small end it IS the mini-player card (same color).
                drawRect(lerpColor(miniColor, pageColor, ramp(p, 0f, 0.6f)))
            }
            // Open: a hit target over the whole screen, so taps never reach the tabs beneath.
            // Collapsing: no hit target at all, so touches fall through to the mini-player.
            .then(if (interactive) Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } } else Modifier),
    ) {
        // Soft tint behind the video, fading into the page.
        Box(
            Modifier.fillMaxWidth().height(520.dp)
                .graphicsLayer { alpha = ramp(morph.progress.value, DetailsFadeStart) * (1f - portraitFsPull.value) }
                .background(Brush.verticalGradient(listOf(c.primaryContainer.copy(alpha = 0.75f), c.surface))),
        )
        // Flow-style immersive backdrop fading in live under finger as you pull down toward portrait fullscreen:
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = portraitFsPull.value }
                .background(Color.Black),
        )
        ActionMessage(state, Modifier.align(Alignment.BottomCenter).zIndex(1f).padding(bottom = bottomClearance + 8.dp))
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(top + StageTopGap))
            // Pinned: the video stays put while the details scroll beneath it (and tracks portrait fullscreen pull).
            VideoStage(state, morph, fold, onCollapse, onFullscreen, interactive, portraitFsOffset = { portraitFsPull.value * portraitFsTravelPx })
            // While collapsing the details are invisible anyway; leaving them out keeps them
            // from catching touches meant for the mini-player.
            if (!showDetails) Spacer(Modifier.weight(1f)) else
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(detailsPull)
                    .graphicsLayer {
                        // The details ride along under the video's bottom edge (so they never
                        // overlap it mid-morph) and only fade in for the last stretch; live-slides with portrait pull.
                        val p = morph.progress.value
                        val fsFactor = (1f - portraitFsPull.value * 1.5f).coerceIn(0f, 1f)
                        alpha = if (p < 1f && state.sheet.isPanel) 0f else ramp(p, DetailsFadeStart) * fsFactor
                        val g = morph.geometry
                        val morphY = if (g != null) lerpF(g.miniThumb.bottom - g.stage.bottom, 0f, p) else 0f
                        translationY = morphY + portraitFsPull.value * portraitFsTravelPx
                    },
                contentPadding = PaddingValues(bottom = bottomClearance + 16.dp),
            ) {
                item(key = "info") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressable { state.sheet = PlayerSheet.Description },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 19.sp,
                            lineHeight = 23.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        val handle = Regex("/@([^/?]+)").find(item.channelUrl.orEmpty())?.groupValues?.getOrNull(1)?.let { "@$it" } ?: item.channel
                        val stats = listOfNotNull(
                            handle.takeIf { it.isNotBlank() },
                            details?.likes?.takeIf { it > 0 }?.let { "${formatCount(it)} likes" },
                            item.views.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
                            item.uploaded?.ifBlank { null },
                        ).joinToString("  ")
                        val hasDescription = !details?.description.isNullOrBlank()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stats, modifier = Modifier.weight(1f), color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (hasDescription) {
                                Text(
                                    "See more",
                                    modifier = Modifier.pressable { state.sheet = PlayerSheet.Description },
                                    color = c.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        PlayerActions(state)
                        if (details?.quality != "Downloaded") {
                            CommentsCard(state.comments, onOpen = { state.sheet = PlayerSheet.Comments })
                        }
                        QueueCard(state, onOpen = { state.sheet = PlayerSheet.Queue })
                    }
                }
                val related = details?.related.orEmpty()
                if (related.isNotEmpty()) {
                    item(key = "moreTitle") { SectionTitle("More videos") }
                    items(related, key = { "rel-" + it.id }) { video ->
                        Box(Modifier.padding(horizontal = 20.dp)) { VideoRow(video, onClick = { onOpen(video) }) }
                    }
                } else if (state.loading) {
                    item(key = "loading") {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(3) {
                                VideoRowSkeleton()
                            }
                        }
                    }
                }
            }
        }
        // The page's bottom vignette, as on the tabs: the details dissolve behind the player
        // bar instead of running hard into it. Drawn only, never touchable.
        val vignette = remember(pageColor) { vignetteBrush(pageColor, opaqueAtTop = false) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottom + 130.dp)
                .graphicsLayer { alpha = ramp(morph.progress.value, DetailsFadeStart) }
                .drawBehind { drawRect(vignette) },
        )
        // Swiping home from the bottom edge never scrolls the details (open player only: while
        // collapsing, touches must still fall through to the mini-player).
        if (interactive) BottomGestureGuard(Modifier.align(Alignment.BottomCenter))
        // The mini-player's title and buttons travel with the shrinking video and fade in as
        // it lands, so the page turns into the mini-player rather than flying to one.
        MorphingMiniDetails(state, morph)

        // Comments open as a panel whose top sits just under the video. Swiping the player down
        // takes it along like the details (under the video's bottom edge, fading out), and it's
        // closed once the details are gone, so it never shrinks into the mini-player or comes
        // back with the next open (GitHub #33).
        LaunchedEffect(showDetails) { if (!showDetails && state.sheet.isPanel) state.sheet = null }
        if (state.sheet.isPanel && showDetails) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val p = morph.progress.value
                    alpha = ramp(p, DetailsFadeStart)
                    val g = morph.geometry
                    translationY = if (g != null) lerpF(g.miniThumb.bottom - g.stage.bottom, 0f, p) else 0f
                },
            ) {
                when (state.sheet) {
                    PlayerSheet.Transcript -> TranscriptPanel(state, partialTop = panelRestTop(state), onClose = { state.sheet = null })
                    PlayerSheet.Description -> VideoDescriptionPanel(state, partialTop = panelRestTop(state), onClose = { state.sheet = null })
                    PlayerSheet.OnTheGo -> OnTheGoPanel(state, partialTop = panelRestTop(state), onClose = { state.sheet = null })
                    else -> CommentsPanel(state.comments, partialTop = panelRestTop(state), onClose = { state.sheet = null })
                }
            }
        }
    }
    // Outside the morph's frame, so it never slides in with the card: it just fades over
    // whatever the status bar shows.
    Box(
        Modifier.fillMaxWidth().height(top)
            .graphicsLayer { alpha = blackStatus() }
            .background(Color.Black),
    )
}

/**
 * Where a panel opened over the player rests: just under the portrait video. The comments
 * panel opens to here, and Save to playlist stops at the same height.
 */
@Composable
fun panelRestTop(state: PlayerState): Dp {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    return top + StageTopGap + (screenW - StageSideGap * 2) / state.stageAspect + 10.dp
}

/** Flow-style video description popup: title, channel, compact facts and expandable body. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun VideoDescriptionPanel(state: PlayerState, partialTop: Dp, onClose: () -> Unit) {
    LaunchedEffect(state.current?.id) { state.loadDescriptionFacts() }
    val item = state.current ?: return
    val details = state.details
    val c = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val openChannel = LocalChannelOpener.current
    val description = details?.description.orEmpty()
    val (cardTint, cardInk, accentColor) = rememberDescriptionPalette(item.thumbnail)
    val watchFacts = state.descriptionFacts?.takeIf { it.videoId == item.id }
    var bodyExpanded by remember(description) { mutableStateOf(false) }
    var bodyOverflows by remember(description) { mutableStateOf(false) }
    var chaptersExpanded by remember(item.id) { mutableStateOf(false) }
    val notePrefs = remember(context) { context.getSharedPreferences("video_description_notes", android.content.Context.MODE_PRIVATE) }
    var note by remember(item.id) { mutableStateOf(notePrefs.getString(item.id, "").orEmpty()) }
    var editNote by remember(item.id) { mutableStateOf(false) }
    var noteDraft by remember(item.id) { mutableStateOf(note) }
    val dateFact = remember(item.uploaded, watchFacts?.publishedDateText) {
        val raw = watchFacts?.publishedDateText?.trim()?.takeIf { it.isNotBlank() } ?: item.uploaded?.trim().orEmpty()
        val exact = Regex("^(.+?)[, ]+((?:19|20)\\d{2})$").find(raw)
        if (exact != null) exact.groupValues[1].trimEnd(',') to exact.groupValues[2]
        else if (raw.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            val parts = raw.split('-')
            val month = parts[1].toIntOrNull()?.let { java.time.Month.of(it).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US) } ?: raw
            "$month ${parts[2]}" to parts[0]
        } else raw.ifBlank { "—" } to "Uploaded"
    }
    val annotated = remember(description, accentColor) {
        val timestampRanges = Regex("(?<!\\d)(?:\\d{1,2}:)?\\d{1,2}:\\d{2}(?!\\d)")
            .findAll(description).map { it.range }.toList()
        buildAnnotatedString {
            append(description)
            timestampRanges.forEach { range ->
                addStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold), range.first, range.last + 1)
                addStringAnnotation("timestamp", description.substring(range), range.first, range.last + 1)
            }
            Regex("https?://[^\\s]+|www\\.[^\\s]+").findAll(description).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                if (timestampRanges.none { it.first < end && it.last + 1 > start }) {
                    addStyle(SpanStyle(color = accentColor, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), start, end)
                    addStringAnnotation("url", match.value, start, end)
                }
            }
        }
    }
    var textLayout by remember(annotated) { mutableStateOf<TextLayoutResult?>(null) }
    val hashtags = remember(description) { Regex("(?<!\\w)#[\\p{L}\\p{N}_]+").findAll(description).map { it.value }.distinct().take(8).toList() }

    PlayerPanel(
        partialTop = partialTop,
        onClose = onClose,
        closeLabel = "Close description",
        // Light mode is deliberately plain white; dark and AMOLED retain their readable surface.
        containerColor = if (isDarkTheme()) c.surface else Color.White,
        title = { },
    ) { listModifier ->
        Column(
            listModifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title lives in the scrollable content, not beside the fixed close button.
            Text(
                item.title,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                maxLines = Int.MAX_VALUE,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelAvatar(details?.item?.avatar ?: item.avatar, item.channel, size = 28)
                Spacer(Modifier.width(9.dp))
                Text(item.channel, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DescriptionFact((watchFacts?.likes ?: details?.likes)?.takeIf { it > 0 }?.let(::descriptionCount) ?: "—", "Likes", cardTint, cardInk, Modifier.weight(1f))
                DescriptionFact((watchFacts?.viewCount ?: item.views).takeIf { it >= 0 }?.let(::descriptionCount) ?: "—", "Views", cardTint, cardInk, Modifier.weight(1f))
                DescriptionFact(dateFact.first, dateFact.second, cardTint, cardInk, Modifier.weight(1f))
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardTint).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (description.isBlank()) {
                    Text("No description available.", color = cardInk, fontSize = 15.sp)
                } else {
                    SelectionContainer {
                        Text(
                            text = annotated,
                            color = cardInk,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            maxLines = if (bodyExpanded) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { textLayout = it; bodyOverflows = it.hasVisualOverflow },
                            modifier = Modifier.fillMaxWidth().animateContentSize().pointerInput(annotated) {
                                detectTapGestures { point ->
                                    val offset = textLayout?.getOffsetForPosition(point) ?: return@detectTapGestures
                                    val timestamp = annotated.getStringAnnotations("timestamp", offset, offset).firstOrNull()?.item
                                    if (timestamp != null) {
                                        val seconds = timestamp.split(':').mapNotNull(String::toLongOrNull).fold(0L) { acc, value -> acc * 60 + value }
                                        state.controller?.seekTo(seconds * 1000L)
                                        return@detectTapGestures
                                    }
                                    annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.item?.let { url ->
                                        runCatching { uriHandler.openUri(if (url.startsWith("www.")) "https://$url" else url) }
                                    }
                                }
                            },
                        )
                    }
                    if (bodyOverflows) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                                .border(1.dp, cardInk.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                                .pressable { bodyExpanded = !bodyExpanded }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (bodyExpanded) "See less" else "See more", color = cardInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (hashtags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hashtags.forEach { tag ->
                        Text(tag, color = accentColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable {
                            runCatching { uriHandler.openUri("https://www.youtube.com/hashtag/${tag.removePrefix("#")}") }
                        })
                    }
                }
            }

            details?.chapters?.takeIf { it.isNotEmpty() }?.let { chapters ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardTint).padding(14.dp)) {
                    Row(Modifier.fillMaxWidth().pressable { chaptersExpanded = !chaptersExpanded }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Chapters", color = cardInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("${chapters.size} chapters · Jump to a moment", color = cardInk.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                        Text(if (chaptersExpanded) "⌃" else "›", color = cardInk, fontSize = 25.sp)
                    }
                    if (chaptersExpanded) {
                        Spacer(Modifier.height(8.dp))
                        chapters.forEach { chapter ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable {
                                    state.controller?.seekTo(chapter.startMs)
                                    state.sheet = null
                                }.padding(horizontal = 8.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(formatDuration(chapter.startMs), color = cardInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(chapter.title, color = cardInk, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            if (state.captions.tracks.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardTint)
                        .pressable { state.sheet = PlayerSheet.Transcript }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Subtitles, "", tint = cardInk)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Transcript", color = cardInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Read and search captions", color = cardInk.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("›", color = cardInk, fontSize = 28.sp, lineHeight = 28.sp)
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardTint)
                    .pressable { noteDraft = note; editNote = true }.padding(16.dp),
            ) {
                Text("Note", color = cardInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (note.isNotBlank()) Text(note, color = cardInk, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                else Text("Add a note", color = cardInk.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }

            Spacer(Modifier.height(22.dp))
        }
    }

    if (editNote) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editNote = false },
            title = { Text("Video note") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    placeholder = { Text("Add a note about this video") },
                )
            },
            confirmButton = {
                Text("Save", modifier = Modifier.pressable {
                    note = noteDraft.trim()
                    notePrefs.edit().putString(item.id, note).apply()
                    editNote = false
                }.padding(12.dp))
            },
            dismissButton = { Text("Cancel", modifier = Modifier.pressable { editNote = false }.padding(12.dp)) },
        )
    }
}

@Composable
internal fun rememberDescriptionPalette(thumbnailUrl: String?): Triple<Color, Color, Color> {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var artworkColor by remember(thumbnailUrl) { mutableStateOf<Color?>(null) }
    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl.isNullOrBlank()) return@LaunchedEffect
        val bitmap = runCatching {
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context).data(thumbnailUrl).allowHardware(false).size(128).build()
                val result = context.imageLoader.execute(request) as? SuccessResult
                result?.drawable?.toBitmap()
            }
        }.getOrNull() ?: return@LaunchedEffect
        artworkColor = withContext(Dispatchers.Default) {
            val palette = runCatching { Palette.from(bitmap).generate() }.getOrNull()
            val swatch = palette?.vibrantSwatch ?: palette?.lightVibrantSwatch ?: palette?.mutedSwatch ?: palette?.dominantSwatch
            swatch?.rgb?.let(::Color)
        }
    }
    val dark = scheme.surface.luminance() < 0.45f
    val tint = remember(artworkColor, dark, scheme.surfaceContainerHigh) {
        artworkColor?.let { seed ->
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(android.graphics.Color.rgb((seed.red * 255).toInt(), (seed.green * 255).toInt(), (seed.blue * 255).toInt()), hsl)
            hsl[2] = if (dark) 0.16f else 0.92f
            Color(ColorUtils.HSLToColor(hsl))
        } ?: scheme.surfaceContainerHigh
    }
    val ink = if (artworkColor == null) scheme.onSurface else if (dark) Color.White else Color(0xFF161616)
    val accent = remember(artworkColor, dark, scheme.primary) {
        val seed = artworkColor ?: return@remember scheme.primary
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(android.graphics.Color.rgb((seed.red * 255).toInt(), (seed.green * 255).toInt(), (seed.blue * 255).toInt()), hsl)
        hsl[2] = if (dark) 0.80f else 0.40f
        Color(ColorUtils.HSLToColor(hsl))
    }
    return Triple(tint, ink, accent)
}

@Composable
private fun DescriptionFact(value: String, label: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(container).padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, color = content, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = content, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun descriptionCount(value: Long): String {
    val (scaled, suffix) = when {
        value >= 1_000_000_000L -> value / 1_000_000_000.0 to "B"
        value >= 1_000_000L -> value / 1_000_000.0 to "M"
        value >= 1_000L -> value / 1_000.0 to "K"
        else -> return value.toString()
    }
    val text = if (scaled >= 100 || scaled % 1.0 == 0.0) scaled.toInt().toString() else "%.1f".format(java.util.Locale.US, scaled).trimEnd('0').trimEnd('.')
    return text + suffix
}

/**
 * The pinned video in portrait: the morph's container-transform layer around [VideoContent]
 * (with the controls on the video, also its bottom-edge playback head).
 * Swiping it down minimizes (the morph). Swiping it up while open goes fullscreen, as in the
 * YouTube app (Derek, 2026-10-10): the video swells under the finger, and past
 * [FullscreenPull] or with a flick it turns to landscape; short of that it eases back.
 */
@Composable
private fun VideoStage(
    state: PlayerState,
    morph: PlayerMorph,
    fold: Animatable<Float, *>,
    onCollapse: () -> Unit,
    onFullscreen: () -> Unit,
    interactive: Boolean,
    portraitFsOffset: () -> Float = { 0f },
) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val enterFullscreen by rememberUpdatedState(onFullscreen)
    // The height follows the video; when its real shape arrives the change eases in.
    val aspect by animateFloatAsState(
        state.stageAspect,
        if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 380f),
        label = "stageAspect",
    )
    val pullUp = remember { Animatable(0f) }
    val pullDistance = with(LocalDensity.current) { FullscreenPull.toPx() }
    // Rubber band: up to 6% bigger, ever more slowly, growing down from the top edge.
    val swell = { 1f + 0.06f * (1f - exp(-pullUp.value / (pullDistance * 1.5f))) }
    val folded = { if (PlayerControls.inBar) fold.value * ramp(morph.progress.value, 0.8f) else 0f }
    val stage = Modifier
        .graphicsLayer { clip = folded() > 0.001f }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val k = 1f - folded()
            layout(placeable.width, (placeable.height * k).roundToInt()) {
                placeable.placeWithLayer(0, 0) {
                    alpha = ramp(k, 0.1f, 0.85f)
                    translationY = -placeable.height * 0.3f * (1f - k)
                }
            }
        }
        .fillMaxSize()
        .graphicsLayer {
            val k = swell()
            scaleX = k
            scaleY = k
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
        // Container transform: at progress 0 the video's centred 16:9 part sits exactly
        // on the mini-player's thumbnail (scale, position and corner radius); at 1 the
        // whole stage is home.
        .graphicsLayer {
            val g = morph.geometry
            val p = morph.progress.value
            if (g != null) {
                val f = g.videoFrame(size, p)
                scaleX = f.scale
                scaleY = f.scale
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = f.offset.x
                translationY = f.offset.y
                // Radius in layer space: divided by the scale so the small version
                // shows the thumbnail's 17 dp corners, not a shrunken 28 dp.
                shape = RoundRectClip(f.window, lerpF(g.thumbRadius / f.startScale, g.stageRadius, p.coerceIn(0f, 1f)))
            } else {
                shape = RoundedCornerShape(StageRadius)
            }
            clip = true
            // Only while it floats mid-morph; landed, it is a flat edge-to-edge rectangle.
            shadowElevation = 14.dp.toPx() * (1f - p).coerceIn(0f, 1f)
            ambientShadowColor = Color.Black.copy(alpha = 0.25f)
            spotShadowColor = Color.Black.copy(alpha = 0.35f)
        }

    // Handle the stage swipe on the stationary outer bounds in the Initial pass so the
    // TextureView/player surface cannot steal a downward drag before it reaches the mini-player morph.
    val currentInteractive by rememberUpdatedState(interactive)
    val stageSwipe = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!currentInteractive) return@awaitEachGesture
            val tracker = VelocityTracker().apply {
                resetTracking()
                addPosition(down.uptimeMillis, down.position)
            }
            var previous = down.position
            var direction = 0 // 1 = morph down to mini player, -1 = pull up toward fullscreen
            var pullAmount = 0f
            var cumulativeDragY = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.position - previous
                previous = change.position
                val fromDown = change.position - down.position
                tracker.addPosition(change.uptimeMillis, change.position)

                if (direction == 0) {
                    val slop = viewConfiguration.touchSlop
                    when {
                        fromDown.y > slop && fromDown.y > kotlin.math.abs(fromDown.x) * 0.5f -> {
                            direction = 1
                            cumulativeDragY = fromDown.y
                            morph.dragBy(fromDown.y)
                            change.consume()
                        }
                        fromDown.y < -slop && kotlin.math.abs(fromDown.y) > kotlin.math.abs(fromDown.x) * 0.5f &&
                            state.expanded && !state.fullscreen && !AudioOnly.enabled && morph.progress.value >= 0.999f -> {
                            direction = -1
                            pullAmount = -fromDown.y
                            scope.launch { pullUp.snapTo(pullAmount) }
                            change.consume()
                        }
                        kotlin.math.abs(fromDown.x) > slop * 2f && kotlin.math.abs(fromDown.x) > kotlin.math.abs(fromDown.y) * 1.5f -> break
                    }
                } else {
                    if (direction > 0) {
                        cumulativeDragY += delta.y
                        morph.dragBy(delta.y)
                    } else {
                        pullAmount = (pullAmount - delta.y).coerceAtLeast(0f)
                        scope.launch { pullUp.snapTo(pullAmount) }
                    }
                    change.consume()
                }
                if (!change.pressed) break
            }

            val velocityY = tracker.calculateVelocity().y
            when (direction) {
                1 -> {
                    // Downward swipe on video -> settle to mini-player
                    if (velocityY < -200f) {
                        morph.settle(-600f, reduced)
                    } else if (velocityY > 200f || morph.progress.value < 0.92f || cumulativeDragY > 40f) {
                        morph.settle(maxOf(velocityY, 600f), reduced)
                    } else {
                        morph.settle(velocityY, reduced)
                    }
                }
                -1 -> {
                    // Upward swipe on video -> enter fullscreen
                    if (pullAmount > pullDistance || velocityY < -300f) enterFullscreen()
                    scope.launch {
                        if (reduced) pullUp.snapTo(0f) else pullUp.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                    }
                }
            }
        }
    }
    // Controls appear only once the video has nearly landed, so they never ride the morph.
    val controlsAlpha = { ramp(morph.progress.value, 0.7f) }
    // Audio only ↔ video in place (Derek, 2026-10-14): [cover] is how much of the audio stage
    // lies over the video, [gather] its controls coming together.
    val audio = AudioOnly.enabled
    val cover = remember { Animatable(if (audio) 1f else 0f) }
    val gather = remember { Animatable(if (audio) 1f else 0f) }
    LaunchedEffect(audio) {
        if (reduced) { cover.snapTo(if (audio) 1f else 0f); gather.snapTo(if (audio) 1f else 0f); return@LaunchedEffect }
        if (audio) {
            launch { cover.animateTo(1f, tween(AudioSwitchMs, easing = Motion.Emphasized)) }
            delay(AudioSwitchMs / 3L)
            gather.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f))
        } else {
            // Straight away (Derek: waiting for the video felt like the tap hadn't registered).
            // Underneath, the video shows its thumbnail and a spinner until a fresh frame
            // arrives ([PlayerState.videoStale]).
            launch { gather.animateTo(0f, tween(AudioSwitchMs / 2, easing = Motion.Emphasized)) }
            cover.animateTo(0f, tween(AudioSwitchMs, easing = Motion.Emphasized))
        }
    }
    val showVideo by remember { derivedStateOf { !AudioOnly.enabled || cover.value < 1f } }
    val showAudio by remember { derivedStateOf { AudioOnly.enabled || cover.value > 0f } }
    // With the controls on the video, its bottom edge is the playback head (VideoView.kt's videoEdge),
    // drawn out here, past the stage's clip, so its knob can overhang the edge; above the details below.
    val onVideo = !PlayerControls.inBar
    val edge = remember { EdgeScrub() }
    Box(
        Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = portraitFsOffset() }
            .padding(horizontal = StageSideGap)
            .fillMaxWidth()
            .aspectRatio(aspect)
            .then(stageSwipe)
            .videoEdge(
                state, edge,
                enabled = onVideo && interactive && !audio,
                shown = { if (onVideo) ramp(morph.progress.value, 0.85f) * (1f - cover.value) else 0f },
                scale = swell,
            ),
    ) {
    Box(stage) {
        if (showVideo) {
            VideoContent(
                state = state,
                fullscreen = false,
                interactive = interactive,
                // A tap on the shrinking video brings the player straight back.
                onInactiveTap = { state.expanded = true },
                onCollapse = onCollapse,
                onToggleFullscreen = onFullscreen,
                controlsAlpha = controlsAlpha,
                edge = if (onVideo) edge else null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showAudio) {
            AudioOnlyStage(
                state = state,
                interactive = interactive && audio,
                onInactiveTap = { state.expanded = true },
                progress = { morph.progress.value },
                controlsAlpha = controlsAlpha,
                cover = { cover.value },
                gather = { gather.value },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    }
}

/** Audio only ↔ video: how long the audio stage takes to cover (or uncover) the picture. */
private const val AudioSwitchMs = 360

/** How far a swipe up on the open video travels before it goes fullscreen on release. */
private val FullscreenPull = 64.dp

// ── Account actions (signed in) ────────────────────────────────────────────────
// Subscribe + bell: SubscribeWithBell (Bell.kt), shared with the channel page.

/**
 * The action row: like | dislike, share, save-to-playlist sparkle, then More (GitHub issue #19; signed out
 * it's the whole row). It runs edge to edge inside so a long like count scrolls it sideways instead of squeezing
 * the buttons, and the like sparks still have room to fly.
 */
@Composable
private fun PlayerActions(state: PlayerState) {
    Row(
        Modifier
            .padding(top = 14.dp)
            .bleed(20.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val item = state.current ?: return
        val details = state.details
        val channelUrl = details?.item?.channelUrl ?: item.channelUrl
        val openChannel = LocalChannelOpener.current
        // Nudge the channel identity toward the action group without moving the right-side icons.
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier
                .size(36.dp)
                .pressable(enabled = channelUrl != null, pressedScale = 0.96f) { channelUrl?.let(openChannel) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(details?.item?.avatar ?: item.avatar, item.channel, size = 32)
        }
        val subscribed = state.accountState?.subscribed == true
        val channelId = state.accountState?.channelId
            ?: channelUrl?.substringAfter("/channel/", "")?.takeIf { it.startsWith("UC") }
        val bellChannel = channelId?.let { BellChannel(it, item.channel, details?.item?.avatar ?: item.avatar) }
        val toggleBell = rememberBellToggle { state.actionMessage = it }
        val colors = MaterialTheme.colorScheme
        if (subscribed && bellChannel != null) {
            // After YouTube confirms subscription the text button becomes the real channel bell.
            // Bells.set writes YouTube's notification preference and rolls back if the request fails.
            val allNotifications = Bells.isOn(bellChannel.id)
            Box(
                Modifier
                    .size(40.dp)
                    .pressable(pressedScale = 0.92f) { toggleBell(bellChannel) }
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                FlourishIcon(
                    if (allNotifications) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                    if (allNotifications) "All notifications on" else "Turn on all notifications",
                    active = allNotifications,
                    kind = Flourish.Ring,
                    tint = colors.onSurface,
                    size = 22.dp,
                )
            }
        } else {
            val lightTheme = colors.surface.luminance() >= 0.5f
            Box(
                Modifier
                    .height(40.dp)
                    .pressable(enabled = state.accountState != null, pressedScale = 0.97f) { state.toggleSubscribe() }
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (lightTheme) Color.Black else Color.White)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Subscribe",
                    color = if (lightTheme) Color.White else Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
        // Keep the avatar and Subscribe at the left, with the four action icons grouped toward
        // the right edge. The surrounding horizontal scroll still handles narrower windows.
        Spacer(Modifier.width(42.dp))
        AccountActions(state)
    }
}

/** Lays the content out [x] wider on each side than the parent's padding allows. */
private fun Modifier.bleed(x: androidx.compose.ui.unit.Dp) = layout { measurable, constraints ->
    val extra = (x * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Kotlin player actions; vector drawable XML supplies only the button glyphs. */
@Composable
private fun AccountActions(state: PlayerState) {
    val like = state.accountState?.like ?: LikeStatus.None
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var moreOpen by remember(state.current?.id) { mutableStateOf(false) }
    var showDownload by remember(state.current?.id) { mutableStateOf(false) }

    // Keep the controls visible while signed out or while account state is still loading.
    // setLike safely no-ops until the signed-in state has loaded.
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DrawableActionPill(
            icon = if (like == LikeStatus.Like) R.drawable.yt_fill_experimental_thumb_up_vd_theme_24 else R.drawable.yt_outline_experimental_thumb_up_vd_theme_24,
            label = null,
            active = like == LikeStatus.Like,
            description = if (like == LikeStatus.Like) "Unlike" else "Like",
            shape = RoundedCornerShape(22.dp),
        ) { state.setLike(LikeStatus.Like) }
        DrawableActionPill(
            icon = if (like == LikeStatus.Dislike) R.drawable.yt_fill_experimental_thumb_down_vd_theme_24 else R.drawable.yt_outline_experimental_thumb_down_vd_theme_24,
            label = null,
            active = like == LikeStatus.Dislike,
            description = if (like == LikeStatus.Dislike) "Remove dislike" else "Dislike",
            shape = RoundedCornerShape(22.dp),
        ) { state.setLike(LikeStatus.Dislike) }
    }

    DrawableActionPill(
        icon = R.drawable.ic_share,
        label = null,
        active = false,
        description = "Share video",
    ) { state.current?.let { shareLink(context, "https://youtu.be/${it.id}", it.title) } }

    DrawableActionPill(
        icon = R.drawable.ic_more_vert,
        label = null,
        active = false,
        description = "More player actions",
    ) { moreOpen = true }

    if (moreOpen) {
        PlayerMoreSheet(
            state = state,
            onDismiss = { moreOpen = false },
            onCopyLink = {
                state.current?.let { clipboard.setText(AnnotatedString("https://youtu.be/${it.id}")) }
                state.actionMessage = "Link copied"
                moreOpen = false
            },
            onDownload = { moreOpen = false; showDownload = true },
        )
    }
    if (showDownload) {
        DownloadSheet(state, onDismiss = { showDownload = false })
    }
    if (state.sheet == PlayerSheet.Equalizer) {
        EqualizerSheet(onDismiss = { state.sheet = null })
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerMoreSheet(
    state: PlayerState,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit,
) {
    val item = state.current ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) { Thumbnail(item.thumbnail, Modifier.fillMaxSize(), radius = 0) }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Video options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(item.title, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                }
            }
            PlayerMoreRow(Icons.Rounded.Tune, "On-the-go", "Premium listening controls & audio mode") { state.onTheGoOpen = true; onDismiss() }
            PlayerMoreRow(Icons.Rounded.Description, "Description", "Read the video description") { state.sheet = PlayerSheet.Description; onDismiss() }
            PlayerMoreRow(Icons.Rounded.Speed, "Playback speed", "${PlaybackSpeed.label(state.speed)}×") { state.sheet = PlayerSheet.Speed; onDismiss() }
            PlayerMoreRow(Icons.Rounded.Bedtime, "Sleep timer", SleepTimerState.labelForVideo()) { state.sheet = PlayerSheet.SleepTimer; onDismiss() }
            PlayerMoreRow(Icons.Rounded.HighQuality, "Video quality", state.details?.quality ?: "Auto") { state.sheet = PlayerSheet.Quality; onDismiss() }
            PlayerMoreRow(
                Icons.Rounded.Equalizer,
                "Equalizer",
                if (com.github.itube.backend.EqualizerManager.isEnabled) "On (${com.github.itube.backend.EqualizerManager.currentPresetName})" else "Audio frequencies & presets"
            ) {
                state.sheet = PlayerSheet.Equalizer
                onDismiss()
            }
            PlayerMoreRow(Icons.Rounded.GraphicEq, "Audio track", "Original") { state.actionMessage = "Original audio track selected"; onDismiss() }
            PlayerMoreRow(Icons.Rounded.Download, "Download", "Save video or audio") { onDownload() }
            PlayerMoreRow(Icons.Rounded.QueueMusic, "Queue", "Manage upcoming videos") { state.sheet = PlayerSheet.Queue; onDismiss() }
            if (state.captions.tracks.isNotEmpty()) PlayerMoreRow(Icons.Rounded.Subtitles, "Transcript", "Read along") { state.sheet = PlayerSheet.Transcript; onDismiss() }
            PlayerMoreRow(Icons.Outlined.ContentCopy, "Copy video link", "Copy the YouTube link") { onCopyLink() }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PlayerMoreRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).pressable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun DrawableActionPill(
    icon: Int,
    label: String?,
    active: Boolean,
    description: String,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp),
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(44.dp)
            .pressable(onClick = onClick)
            .clip(shape)
            .padding(horizontal = if (label == null) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = colors.onSurface,
            modifier = Modifier
                .size(22.dp)
                .squishOn(active),
        )
        if (label != null) {
            Spacer(Modifier.width(7.dp))
            Text(label, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    active: Boolean,
    /** Null: a plain icon (an action with no on/off state). */
    flourish: Flourish?,
    shape: androidx.compose.ui.graphics.Shape,
    description: String,
    transparent: Boolean = false,
    labelRollsUp: Boolean = true,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val bg by androidx.compose.animation.animateColorAsState(if (active) c.primary else if (transparent) Color.Transparent else c.surfaceContainerHigh, tween(220), label = "pillBg")
    val tint by androidx.compose.animation.animateColorAsState(if (active) c.onPrimary else c.onSurface, tween(220), label = "pillInk")
    Row(
        modifier = Modifier
            .squishOn(active)
            .height(44.dp)
            .pressable(onClick = onClick)
            // background(shape), not clip: the like sparks may fly past the pill's edge.
            .background(bg, shape)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flourish == null) Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
        else FlourishIcon(icon, description, active = active, kind = flourish, tint = tint, size = 20.dp, sparkColor = c.primary)
        if (label != null) {
            Spacer(Modifier.width(7.dp))
            RollingText(label, tint, up = labelRollsUp)
        }
    }
}

/** Brief confirmation or failure note for account actions; clears itself. */
@Composable
private fun ActionMessage(state: PlayerState, modifier: Modifier) {
    val message = state.actionMessage ?: return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2_500)
        state.actionMessage = null
    }
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(CircleShape)
            .background(c.inverseSurface)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) { Text(message, color = c.inverseOnSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

// ── Mini player ────────────────────────────────────────────────────────────────

@Composable
fun MiniPlayer(state: PlayerState, onExpand: () -> Unit, modifier: Modifier = Modifier, showVideo: Boolean = true) {
    val item = state.current ?: return
    val shape = RoundedCornerShape(MiniRadius)
    Box(
        modifier = modifier
            .fillMaxSize()
            .pressable(pressedScale = 0.98f, onClick = onExpand)
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .background(Color.Black),
    ) {
        if (showVideo) {
            Thumbnail(item.thumbnail, Modifier.fillMaxSize(), radius = 0)
            Box(Modifier.fillMaxSize().aspectFit(state.stageAspect, cover = true)) {
                LocalVideoSurface.current(Modifier.fillMaxSize().aspectFit(state.videoAspect).hideStaleVideo(state))
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.20f), Color.Transparent, Color.Black.copy(alpha = 0.45f)))))
        FloatingMiniControls(state, interactive = true)
    }
}

@Composable
private fun MusicMiniControls(state: PlayerState) {
    val c = MaterialTheme.colorScheme
    val wantsPlayback = state.controller?.playWhenReady ?: state.isPlaying
    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp))) {
            Thumbnail(state.current?.thumbnail, Modifier.fillMaxSize(), radius = 0)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(state.current?.title.orEmpty(), color = c.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.current?.channel.orEmpty(), color = c.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.SkipPrevious, "Previous track", tint = c.onSurface, modifier = Modifier.size(23.dp).pressable { state.playPrevious() })
        Icon(if (wantsPlayback) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (wantsPlayback) "Pause" else "Play", tint = c.onSurface, modifier = Modifier.size(30.dp).pressable { state.togglePlay() })
        Icon(Icons.Rounded.SkipNext, "Next track", tint = c.onSurface, modifier = Modifier.size(23.dp).pressable { state.playNextUp() })
    }
    val progress = if (state.durationMs > 0L) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(c.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(c.primary))
    }
    }
}

@Composable
private fun FloatingMiniControls(state: PlayerState, interactive: Boolean) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        MiniOverlayButton(
            modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
            icon = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            label = if (state.isPlaying) "Pause" else "Play",
            enabled = interactive,
            onClick = state::togglePlay,
        )
        MiniOverlayButton(
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
            icon = Icons.Rounded.Close,
            label = "Close player",
            enabled = interactive,
            onClick = state::close,
        )
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniOverlayButton(icon = Icons.Rounded.Replay10, label = "Back 10 seconds", enabled = interactive) {
                state.controller?.seekTo((state.positionMs - 10_000L).coerceAtLeast(0L))
            }
            MiniOverlayButton(icon = Icons.Rounded.Forward10, label = "Forward 10 seconds", enabled = interactive) {
                state.controller?.seekTo((state.positionMs + 10_000L).coerceAtMost(state.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE))
            }
        }
        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.45f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(c.primary))
        }
    }
}

@Composable
private fun MiniOverlayButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(38.dp).pressable(enabled = enabled, pressedScale = 0.9f, onClick = onClick)
            .clip(CircleShape).background(Color.Black.copy(alpha = 0.64f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

/**
 * Flow-style controls are drawn over the shrinking tile and fade out as the real floating
 * mini-player becomes touchable underneath.
 */
@Composable
private fun MorphingMiniDetails(state: PlayerState, morph: PlayerMorph) {
    val g = morph.geometry ?: return
    Box(
        Modifier
            .layout { measurable, _ ->
                val p = morph.progress.value
                val f = g.videoFrame(g.stage.size, p)
                val left = g.stage.left + f.offset.x + f.window.left * f.scale
                val top = g.stage.top + f.offset.y + f.window.top * f.scale
                val width = (f.window.width * f.scale).roundToInt().coerceAtLeast(1)
                val height = (f.window.height * f.scale).roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
                layout(width, height) {
                    placeable.placeRelative(left.roundToInt(), top.roundToInt())
                }
            }
            .graphicsLayer { alpha = 1f - ramp(morph.progress.value, 0f, 0.3f) },
    ) { FloatingMiniControls(state, interactive = false) }
}
