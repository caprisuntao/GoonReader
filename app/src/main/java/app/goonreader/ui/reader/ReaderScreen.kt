package app.goonreader.ui.reader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.goonreader.data.network.Ao3Urls
import app.goonreader.data.prefs.ReaderAction
import app.goonreader.data.prefs.ReaderMode
import app.goonreader.data.prefs.ReaderSettings
import app.goonreader.data.reader.ReaderAppearance
import app.goonreader.data.reader.ReaderHtml
import app.goonreader.data.reader.ReaderPalette
import app.goonreader.data.reader.palette
import app.goonreader.data.repo.BookmarkRepository
import app.goonreader.data.repo.FailureReason
import app.goonreader.data.tts.TtsState
import app.goonreader.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/**
 * How often scrolling may publish a position to the view model while the finger is down.
 * A 120 Hz drag produces a touch event every ~8 ms; recomposing the reader that often is
 * wasted work and competes with the WebView's own drawing. The exact position is still
 * written when the gesture settles.
 */
private const val PROGRESS_REPORT_NS = 100_000_000L

/**
 * The reader (PRD 10).
 *
 * The screen owns gesture dispatch and chrome; text goes through [ReaderEngine], so a paginated
 * engine can replace the WebView without changes here (PRD 10.10). Tap zones, the brightness
 * swipe, pinch-to-resize and page swipes all come from one gesture loop — which is why the
 * WebView's own touch handling is off: a tap and a drag have to be told apart before either can
 * act.
 */
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onOpenComments: (chapterId: String) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tts by viewModel.ttsState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val fontError by viewModel.fontError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val density = LocalDensity.current
    val settings = state.settings
    val paged = settings.mode == ReaderMode.Paged
    val palette = settings.palette()
    val background = Color.parseColor(palette.background)
    val paper = ComposeColor(background)

    // CSS px are density-independent, so only the user's font scale has to be applied to sp.
    val fontCss = remember(settings.customFontPath) { viewModel.fontCss() }
    val appearance = remember(settings, density.fontScale, fontCss) {
        settings.toAppearance(palette, density.fontScale, fontCss)
    }

    val webView = remember {
        ReaderWebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }
    val engine = remember(webView) { WebViewReaderEngine(webView).also { it.attach(background) } }

    val html = remember(state.content, appearance) {
        state.content?.let { ReaderHtml.render(it, appearance) }
    }

    // Seeded from the view model so a rotation (new WebView, same state) restores the position
    // the reader had, not the position stored when the chapter was opened.
    var lastChapterId by remember { mutableStateOf(state.chapterId) }
    var lastProgress by remember { mutableFloatStateOf(state.progress) }
    var lastReportAt by remember { mutableLongStateOf(0L) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(state.chapterId, html) {
        val document = html ?: return@LaunchedEffect
        // Same chapter, new appearance: stay put. New chapter: use its stored position.
        val restore = if (state.chapterId == lastChapterId) lastProgress else state.initialProgress
        engine.show(document, restore, paged)
        lastChapterId = state.chapterId
        lastProgress = restore
        lastReportAt = 0L
    }

    var showSettings by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showTts by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf<Float?>(null) }
    var pinch by remember { mutableStateOf<Float?>(null) }
    var autoScroll by remember { mutableStateOf(false) }
    var autoPaused by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }

    fun settle() {
        val progress = engine.progress()
        lastProgress = progress
        lastReportAt = System.nanoTime()
        viewModel.onProgress(progress, settled = true)
    }

    /** Publishes a mid-gesture position, at most [PROGRESS_REPORT_NS] apart. */
    fun reportScrollProgress() {
        val now = System.nanoTime()
        if (now - lastReportAt < PROGRESS_REPORT_NS) return
        lastReportAt = now
        val progress = engine.progress()
        lastProgress = progress
        viewModel.onProgress(progress, settled = false)
    }

    /** One page (paged mode) or one screenful (scroll mode) forward or back. */
    fun advance(forward: Boolean) {
        flingJob?.cancel()
        if (paged) {
            if (!engine.turnPage(forward, settings.pageAnimation)) {
                if (forward) viewModel.nextChapter() else viewModel.previousChapter()
            }
        } else {
            val step = screenSize.height * 0.85f
            engine.scrollBy(if (forward) step else -step)
        }
        settle()
    }

    fun act(action: ReaderAction) {
        when (action) {
            ReaderAction.ScrollDown, ReaderAction.NextPage -> advance(true)
            ReaderAction.ScrollUp, ReaderAction.PrevPage -> advance(false)
            ReaderAction.Menu -> viewModel.toggleChrome()
            ReaderAction.NextChapter -> viewModel.nextChapter()
            ReaderAction.PrevChapter -> viewModel.previousChapter()
            ReaderAction.None -> Unit
        }
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFont)
    }
    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(settings.keepScreenOn) {
        if (settings.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // PRD 10.3: volume keys turn pages. The activity dispatches to whichever reader is on screen.
    DisposableEffect(settings) {
        VolumeKeyPager.pager = if (settings.volumeKeys) ({ forward -> advance(forward) }) else null
        onDispose { VolumeKeyPager.pager = null }
    }

    // Immersive while the chrome is hidden (PRD 10.5). Showing the bars has to leave the
    // transient state behind, otherwise they overlay the chrome instead of insetting it.
    val view = LocalView.current
    DisposableEffect(state.chromeVisible) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (state.chromeVisible) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Brightness is a reader-local override; leaving restores the system value.
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    // PRD 10.8: auto-scroll. Whole-screen steps would look jerky, so it moves a few pixels per
    // frame; position is reported a few times a second, not every frame.
    LaunchedEffect(autoScroll, autoPaused, settings.autoScrollSpeed, state.chapterId, state.isLoading, paged) {
        if (!autoScroll || autoPaused || paged || state.isLoading) return@LaunchedEffect
        var previous = 0L
        var sinceCheck = 0L
        while (true) {
            val frame = withFrameNanos { it }
            if (previous != 0L) {
                val elapsed = (frame - previous).coerceAtMost(64_000_000L)
                engine.scrollBy(settings.autoScrollSpeed * density.density * elapsed / 1_000_000_000f)
                reportScrollProgress()
                sinceCheck += elapsed
                // Reaching the end only needs checking a few times a second; asking for the
                // scroll extent on every frame is another native call per 8 ms.
                if (sinceCheck >= 200_000_000L) {
                    sinceCheck = 0L
                    if (engine.progress() >= 1f) {
                        autoScroll = false
                        settle()
                        break
                    }
                }
            }
            previous = frame
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.persistProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.persistProgress()
        }
    }

    // Read-aloud highlight (PRD 10.7): mark the spoken paragraph, and follow the controller into
    // the next chapter only while the reader was in sync with it.
    LaunchedEffect(tts.paragraph, tts.status, tts.chapterId, state.chapterId, html) {
        if (tts.workId == viewModel.workId && tts.chapterId == state.chapterId && tts.isActive) {
            engine.highlight(tts.paragraph)
        } else {
            engine.highlight(null)
        }
    }
    var syncedTtsChapter by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tts.chapterId, state.chapterId) {
        val id = tts.chapterId ?: return@LaunchedEffect
        if (id == state.chapterId) {
            syncedTtsChapter = id
        } else if (syncedTtsChapter == state.chapterId && tts.isActive) {
            viewModel.openChapterById(id)
        }
    }

    Box(Modifier.fillMaxSize().background(paper)) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
            onRelease = { engine.destroy() },
        )

        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { screenSize = it }
                // TalkBack cannot use the tap-zone grid, so the same actions are exposed as
                // accessibility actions on the reading surface.
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Next page") {
                            advance(true)
                            true
                        },
                        CustomAccessibilityAction("Previous page") {
                            advance(false)
                            true
                        },
                        CustomAccessibilityAction("Next chapter") {
                            viewModel.nextChapter()
                            true
                        },
                        CustomAccessibilityAction("Previous chapter") {
                            viewModel.previousChapter()
                            true
                        },
                        CustomAccessibilityAction("Reading menu") {
                            viewModel.toggleChrome()
                            true
                        },
                    )
                }
                .pointerInput(state.chapterId, settings, autoScroll) {
                    val zones = settings.tapZones

                    awaitEachGesture {
                        val size = screenSize
                        if (size.width == 0) return@awaitEachGesture
                        val step = size.height * 0.85f
                        val edge = size.width * 0.16f

                        val down = awaitFirstDown(requireUnconsumed = true)
                        flingJob?.cancel()
                        if (autoScroll) autoPaused = true
                        val start = down.position
                        val startBrightness = activity?.window?.attributes?.screenBrightness
                            ?.takeIf { it >= 0f } ?: 0.5f
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, start)
                        var last = start
                        var moved = false
                        var adjusting = false
                        var pinching = false
                        var pinchStart = 0f
                        var pinchRatio = 1f
                        var total = Offset.Zero

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }

                            if (pressed.size >= 2) {
                                val span = (pressed[0].position - pressed[1].position).getDistance()
                                if (!pinching) {
                                    pinching = true
                                    pinchStart = span
                                } else if (pinchStart > 1f) {
                                    pinchRatio = (span / pinchStart).coerceIn(0.7f, 1.4f)
                                    engine.previewZoom(pinchRatio)
                                    pinch = (settings.fontSizeSp * pinchRatio).coerceIn(12f, 36f)
                                }
                                moved = true
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)

                            if (!change.pressed) {
                                change.consume()
                                break
                            }

                            val delta = change.position - last
                            last = change.position
                            total += delta
                            if (!moved && total.getDistance() > viewConfiguration.touchSlop) moved = true

                            if (moved && !pinching) {
                                if (!adjusting && start.x < edge && abs(total.y) > abs(total.x)) {
                                    adjusting = true
                                }
                                if (adjusting) {
                                    val target = (startBrightness - total.y / size.height * 1.3f)
                                        .coerceIn(0.02f, 1f)
                                    activity?.window?.let { window ->
                                        window.attributes = window.attributes.apply {
                                            screenBrightness = target
                                        }
                                    }
                                    brightness = target
                                } else if (!paged) {
                                    engine.scrollBy(-delta.y)
                                    reportScrollProgress()
                                }
                                change.consume()
                            }
                        }

                        if (pinching) {
                            engine.previewZoom(1f)
                            val ratio = pinchRatio
                            pinch = null
                            if (abs(ratio - 1f) > 0.05f) {
                                viewModel.updateSettings {
                                    it.copy(fontSizeSp = (it.fontSizeSp * ratio).coerceIn(12f, 36f))
                                }
                            }
                        } else if (!moved) {
                            act(zoneAction(start, size, zones))
                        } else {
                            settle()
                            brightness = null
                            if (paged && abs(total.x) > size.width * 0.15f && abs(total.x) > abs(total.y)) {
                                advance(forward = total.x < 0)
                            } else if (!adjusting && !paged) {
                                val velocity = tracker.calculateVelocity().y
                                if (abs(velocity) > 400f) {
                                    flingJob = scope.launch {
                                        fling(engine, velocity, ::reportScrollProgress)
                                        settle()
                                    }
                                }
                            }
                        }
                    }
                },
        )

        AnimatedVisibility(
            visible = state.isLoading,
            enter = fadeIn(tween(Motion.Fast, easing = Motion.EaseOut)),
            exit = fadeOut(tween(Motion.Fast, easing = Motion.EaseIn)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CircularProgressIndicator(color = ComposeColor(Color.parseColor(palette.accent)))
        }

        state.failure?.let { reason ->
            ReaderFailure(
                reason = reason,
                onRetry = viewModel::retry,
                onBack = onBack,
                onOpenInBrowser = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(Ao3Urls.work(viewModel.workId))),
                    )
                },
            )
        }

        // PRD 10.5: status overlay while the chrome is hidden.
        AnimatedVisibility(
            visible = settings.statusOverlay && !state.chromeVisible,
            enter = fadeIn(tween(Motion.Standard, easing = Motion.EaseOut)),
            exit = fadeOut(tween(Motion.Fast, easing = Motion.EaseIn)),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            StatusOverlay(
                chapterTitle = state.chapterTitle,
                progress = state.progress,
                palette = palette,
            )
        }

        // PRD 10.1: reaching the end offers the next chapter.
        AnimatedVisibility(
            visible = state.finished && state.hasNext && !state.isLoading,
            enter = fadeIn(tween(Motion.Fast, easing = Motion.EaseOut)) +
                slideInVertically(tween(Motion.Standard, easing = Motion.EaseOut)) { it / 2 },
            exit = fadeOut(tween(Motion.Fast, easing = Motion.EaseIn)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (state.chromeVisible) 214.dp else 44.dp),
        ) {
            Button(
                onClick = viewModel::nextChapter,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(Color.parseColor(palette.accent)),
                ),
            ) {
                Text("Next chapter")
            }
        }

        // PRD 10.8: tap anywhere to pause; the pill is the way back to running.
        AnimatedVisibility(
            visible = autoScroll,
            enter = fadeIn(tween(Motion.Fast, easing = Motion.EaseOut)) +
                slideInVertically(tween(Motion.Standard, easing = Motion.EaseOut)) { it / 2 },
            exit = fadeOut(tween(Motion.Fast, easing = Motion.EaseIn)) +
                slideOutVertically(tween(Motion.Fast, easing = Motion.EaseIn)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (state.chromeVisible) 214.dp else 16.dp),
        ) {
            Text(
                text = if (autoPaused) "Paused · tap to resume" else "Auto-scroll · tap to pause",
                color = ComposeColor(Color.parseColor(palette.muted)),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .background(paper.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .clickable { autoPaused = !autoPaused }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        AnimatedVisibility(
            visible = state.chromeVisible,
            enter = fadeIn(tween(Motion.Standard, easing = Motion.EaseOut)) +
                slideInVertically(tween(Motion.Standard, easing = Motion.EaseOut)) { -it },
            exit = fadeOut(tween(Motion.Fast, easing = Motion.EaseIn)) +
                slideOutVertically(tween(Motion.Fast, easing = Motion.EaseIn)) { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                workTitle = state.workTitle,
                chapterTitle = state.chapterTitle,
                palette = palette,
                bookmarked = bookmarks.any {
                    it.chapterId == state.chapterId &&
                        abs(it.progress - state.progress) < BookmarkRepository.NEAR
                },
                onBookmark = viewModel::toggleBookmark,
                onBack = {
                    viewModel.persistProgress()
                    onBack()
                },
            )
        }

        AnimatedVisibility(
            visible = state.chromeVisible,
            enter = fadeIn(tween(Motion.Standard, easing = Motion.EaseOut)) +
                slideInVertically(tween(Motion.Standard, easing = Motion.EaseOut)) { it },
            exit = fadeOut(tween(Motion.Fast, easing = Motion.EaseIn)) +
                slideOutVertically(tween(Motion.Fast, easing = Motion.EaseIn)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(
                state = state,
                palette = palette,
                paged = paged,
                autoScroll = autoScroll && !autoPaused,
                ttsActive = tts.isActive,
                onSeek = { fraction ->
                    flingJob?.cancel()
                    engine.scrollToProgress(fraction)
                    lastProgress = fraction
                    viewModel.onProgress(fraction, settled = false)
                },
                onSeekFinished = {
                    val progress = engine.progress()
                    lastProgress = progress
                    viewModel.onProgress(progress, settled = true)
                },
                onPrevious = viewModel::previousChapter,
                onNext = viewModel::nextChapter,
                onChapters = { showChapterList = true },
                onComments = {
                    state.chapterId.takeIf { it.isNotEmpty() }?.let(onOpenComments)
                    viewModel.hideChrome()
                },
                onAutoScroll = {
                    autoScroll = !autoScroll
                    autoPaused = false
                    // Hands-free reading: get the chrome out of the way.
                    if (autoScroll) viewModel.hideChrome()
                },
                onTts = { showTts = true },
                onSettings = { showSettings = true },
            )
        }

        brightness?.let { value ->
            Text(
                text = "${(value * 100).toInt()}%",
                color = ComposeColor(Color.parseColor(palette.text)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .background(paper.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        pinch?.let { size ->
            Text(
                text = "${size.roundToInt()} sp",
                color = ComposeColor(Color.parseColor(palette.text)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(paper.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            autoScroll = autoScroll,
            onChange = viewModel::updateSettings,
            onAutoScrollChange = { enabled ->
                autoScroll = enabled
                autoPaused = false
            },
            customFont = settings.customFontPath.isNotEmpty(),
            fontError = fontError,
            onPickFont = { fontPicker.launch(arrayOf("*/*")) },
            onClearFont = viewModel::clearFont,
            onDismiss = {
                showSettings = false
                viewModel.dismissFontError()
            },
        )
    }

    if (showChapterList) {
        ChapterListSheet(
            chapters = state.chapters,
            currentIndex = state.index,
            onSelect = { index ->
                showChapterList = false
                viewModel.openChapterById(state.chapters[index].id)
            },
            onDismiss = { showChapterList = false },
        )
    }

    if (showTts) {
        LaunchedEffect(Unit) { viewModel.warmUpTts() }
        TtsSheet(
            state = tts,
            onStart = {
                askNotificationPermission()
                autoScroll = false
                viewModel.startTts()
            },
            onToggle = viewModel::toggleTts,
            onNext = viewModel::nextTtsParagraph,
            onPrevious = viewModel::previousTtsParagraph,
            onStop = viewModel::stopTts,
            onSpeed = { speed -> viewModel.updateSettings { it.copy(ttsSpeed = speed) } },
            onVoice = { voice -> viewModel.updateSettings { it.copy(ttsVoice = voice) } },
            onSleep = viewModel::setTtsSleep,
            onDismiss = { showTts = false },
        )
    }
}

/**
 * Momentum after a drag, so scrolling feels like a scrollable. Every frame is one
 * [ReaderEngine.scrollBy], which keeps the feel identical when the engine is replaced.
 * [onTick] is called each frame; the caller throttles how often it reads the scroll offset.
 */
private suspend fun fling(engine: ReaderEngine, initialVelocity: Float, onTick: () -> Unit) {
    val decay = exponentialDecay<Float>(frictionMultiplier = 2.2f, absVelocityThreshold = 40f)
    var previous = 0f
    Animatable(0f).animateDecay(initialVelocity, decay) {
        val delta = value - previous
        previous = value
        engine.scrollBy(-delta)
        onTick()
    }
}

@Composable
private fun StatusOverlay(
    chapterTitle: String,
    progress: Float,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var clock by remember { mutableStateOf(LocalTime.now()) }
    var battery by remember { mutableIntStateOf(batteryPercent(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            clock = LocalTime.now()
            battery = batteryPercent(context)
        }
    }

    val paper = ComposeColor(Color.parseColor(palette.background))
    val text = ComposeColor(Color.parseColor(palette.text))
    val muted = ComposeColor(Color.parseColor(palette.muted))

    Column(
        modifier
            .statusBarsPadding()
            .padding(12.dp)
            .background(paper.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(clock.format(CLOCK), style = MaterialTheme.typography.labelMedium, color = text)
        Text(
            text = buildString {
                append((progress * 100).roundToInt()).append('%')
                if (battery >= 0) append(" · ").append(battery).append('%')
            },
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
        if (chapterTitle.isNotBlank()) {
            Text(
                text = chapterTitle,
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun batteryPercent(context: Context): Int =
    context.getSystemService(BatteryManager::class.java)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?: -1

@Composable
private fun ReaderTopBar(
    workTitle: String,
    chapterTitle: String,
    palette: ReaderPalette,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onBack: () -> Unit,
) {
    val paper = ComposeColor(Color.parseColor(palette.background))
    val text = ComposeColor(Color.parseColor(palette.text))
    val muted = ComposeColor(Color.parseColor(palette.muted))

    Column(
        // Opaque: the text underneath must not show through while the chrome is up.
        Modifier
            .fillMaxWidth()
            .background(paper)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = text)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = workTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    maxLines = 1,
                )
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = text,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onBookmark) {
                Icon(
                    imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark this position",
                    tint = if (bookmarked) ComposeColor(Color.parseColor(palette.accent)) else text,
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: ReaderUiState,
    palette: ReaderPalette,
    paged: Boolean,
    autoScroll: Boolean,
    ttsActive: Boolean,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onComments: () -> Unit,
    onAutoScroll: () -> Unit,
    onTts: () -> Unit,
    onSettings: () -> Unit,
) {
    val paper = ComposeColor(Color.parseColor(palette.background))
    val text = ComposeColor(Color.parseColor(palette.text))
    val muted = ComposeColor(Color.parseColor(palette.muted))
    val accent = ComposeColor(Color.parseColor(palette.accent))

    Column(
        Modifier
            .fillMaxWidth()
            .background(paper)
            .navigationBarsPadding(),
    ) {
        Slider(
            value = state.progress,
            onValueChange = onSeek,
            onValueChangeFinished = onSeekFinished,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.25f),
            ),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = state.hasPrev) {
                Icon(
                    Icons.AutoMirrored.Outlined.NavigateBefore,
                    contentDescription = "Previous chapter",
                    tint = if (state.hasPrev) text else muted.copy(alpha = 0.4f),
                )
            }
            IconButton(onClick = onNext, enabled = state.hasNext) {
                Icon(
                    Icons.AutoMirrored.Outlined.NavigateNext,
                    contentDescription = "Next chapter",
                    tint = if (state.hasNext) text else muted.copy(alpha = 0.4f),
                )
            }
            Text(
                text = if (state.chapters.isEmpty()) {
                    ""
                } else {
                    "Chapter ${state.index + 1} of ${state.chapters.size}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            val commentCount = state.content?.commentCount
            IconButton(onClick = onComments) {
                BadgedBox(
                    badge = {
                        if ((commentCount ?: 0) > 0) {
                            Badge(containerColor = accent) {
                                Text(
                                    text = if (commentCount!! > 999) "999+" else commentCount.toString(),
                                    color = paper,
                                )
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = text,
                    )
                }
            }
            IconButton(onClick = onChapters) {
                Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Chapters", tint = text)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Tune, contentDescription = "Reader settings", tint = text)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAutoScroll) {
                Icon(
                    Icons.Outlined.KeyboardDoubleArrowDown,
                    contentDescription = if (autoScroll) "Stop auto-scroll" else "Start auto-scroll",
                    tint = if (autoScroll) accent else muted,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onTts) {
                Icon(
                    Icons.Outlined.RecordVoiceOver,
                    contentDescription = "Read aloud",
                    tint = if (ttsActive) accent else muted,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (paged) "Paged" else "Scroll",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        }
    }
}

@Composable
private fun ReaderFailure(
    reason: FailureReason,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = reason.readerTitle(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = reason.readerMessage(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text("Try again") }
            TextButton(onClick = onBack) { Text("Back") }
        }
        // PRD 11: parsing failures always leave a way to the real page.
        TextButton(onClick = onOpenInBrowser) { Text("Open on AO3") }
    }
}

private fun FailureReason.readerTitle(): String = when (this) {
    FailureReason.NotFound -> "Chapter not found"
    FailureReason.ChallengeRequired -> "AO3 wants a browser check"
    FailureReason.Offline -> "No connection"
    FailureReason.HttpError -> "AO3 returned an error"
    FailureReason.ParseError -> "Couldn't read this chapter"
}

private fun FailureReason.readerMessage(): String = when (this) {
    FailureReason.NotFound -> "This chapter may have been deleted."
    FailureReason.ChallengeRequired -> "Open the work in your browser to clear the check, then try again."
    FailureReason.Offline -> "Check your connection and try again."
    FailureReason.HttpError -> "The archive is busy. Try again in a moment."
    FailureReason.ParseError -> "AO3 may have changed its layout."
}

internal fun ReaderSettings.toAppearance(
    palette: ReaderPalette,
    fontScale: Float,
    fontFaceCss: String = "",
) = ReaderAppearance(
    palette = palette,
    fontPx = fontSizeSp * fontScale,
    lineSpacing = lineSpacing,
    paragraphSpacingEm = paragraphSpacingEm,
    sideMarginPx = sideMarginDp,
    verticalMarginPx = verticalMarginDp,
    justify = justify,
    showSummary = showSummary,
    showNotes = showNotes,
    paged = mode == ReaderMode.Paged,
    fontFaceCss = fontFaceCss,
)

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
