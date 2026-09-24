package dev.kesav.redline.ui.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Size as AndroidSize
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.TorchState
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.kesav.redline.ui.RedlineIcons
import dev.kesav.redline.ui.RedlineMotion
import dev.kesav.redline.ui.animationsEnabled
import dev.kesav.redline.ui.motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The lease photographed page by page without leaving the app.
 *
 * The system camera hands back one photo and closes, so a five-page lease was five round
 * trips, and nothing in its viewfinder said whether the page would be readable. Here the
 * same on-device recogniser the import uses runs on the preview: every block of text it can
 * read is outlined as the phone moves, and the line under the frame says when there is a
 * page's worth of text in view and the hands are still enough to take it.
 *
 * Each photo is written under the FileProvider's `pages/` cache path and handed to
 * [onCaptured] as a content Uri, and the scanner stays open for the next page. Pass the Uri
 * to [deleteScannedPage] once its text has been read. [pagesSoFar] is the count already in
 * the field when the scanner opened, so the page counter carries on from it.
 */
@Composable
internal fun CameraScan(
    pagesSoFar: Int,
    onCaptured: (Uri) -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    CameraWindow()

    var granted by remember { mutableStateOf(cameraAllowed(context)) }
    var stage by rememberSaveable { mutableStateOf(Ask.NOT_YET) }
    var refusals by rememberSaveable { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        stage = Ask.ANSWERED
        if (!ok) refusals++
    }
    // Back from the app's settings page with the switch turned on.
    LifecycleResumeEffect(context) {
        granted = cameraAllowed(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        if (!granted && stage == Ask.NOT_YET) {
            stage = Ask.ASKING
            ask.launch(Manifest.permission.CAMERA)
        }
        // Pages left behind by a read the app never finished, from an earlier session.
        withContext(Dispatchers.IO) { sweepOldPages(context) }
    }

    Box(Modifier.fillMaxSize().background(Backdrop)) {
        when {
            granted -> Scanner(pagesSoFar, onCaptured, onDone, onClose)
            stage == Ask.ANSWERED -> Denied(
                refusals = refusals,
                onAsk = {
                    stage = Ask.ASKING
                    ask.launch(Manifest.permission.CAMERA)
                },
                onClose = onClose,
            )
            // The system prompt is up. The dark frame behind it is the scanner arriving.
            else -> Unit
        }
    }
}

/** Deletes a page [CameraScan] wrote, once its text has been read. A photo of a lease has no business outliving the scan. */
internal fun deleteScannedPage(context: Context, uri: Uri) {
    val name = uri.lastPathSegment ?: return
    if (!PAGE_NAME.matches(name)) return
    File(pagesDir(context), name).delete()
}

private enum class Ask { NOT_YET, ASKING, ANSWERED }

// The scanner sits on live camera imagery in both themes, so its chrome is the camera's own
// black and white rather than page roles: a light-theme surface over a viewfinder would be a
// pale bar across the picture. The one accent comes from the theme.
private val Backdrop = Color.Black
private val OnCamera = Color.White

/** The theme's primary in the tone Material tunes for a dark background, in either theme. */
@Composable
private fun accent(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) scheme.primary else scheme.inversePrimary
}

private const val ANALYSE_EVERY_MS = 200L
private val PAGE_NAME = Regex("""scan-\d+\.jpg""")
private const val STALE_MS = 60 * 60 * 1000L

@Composable
private fun Scanner(
    pagesSoFar: Int,
    onCaptured: (Uri) -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val accent = accent()
    val animate = animationsEnabled()
    val handOver by rememberUpdatedState(onCaptured)

    val start = rememberSaveable { pagesSoFar }
    var taken by rememberSaveable { mutableIntStateOf(0) }
    val total = start + taken

    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }
    // A TextureView rather than a SurfaceView, so the preview fades and scales with the
    // Compose tree around it, and so the frame under the shutter can be copied off it.
    val preview = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var blocks by remember { mutableStateOf(emptyList<Block>()) }
    var framing by remember { mutableStateOf(Framing.NONE) }
    var torch by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    DisposableEffect(controller, lifecycle) {
        val main = ContextCompat.getMainExecutor(context)
        val worker = Executors.newSingleThreadExecutor()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val steadiness = Steadiness()
        // View-referenced coordinates: CameraX maps each box through the preview's own crop
        // and rotation, so it lands on the words it outlines at any aspect ratio.
        val analyzer = MlKitAnalyzer(
            listOf(recognizer),
            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
            main,
        ) { result ->
            val text = result.getValue(recognizer)
            if (text != null) {
                val found = text.textBlocks.mapNotNull { block ->
                    block.boundingBox?.let {
                        Block(
                            it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat(),
                            key = block.text.filter(Char::isLetterOrDigit).lowercase(),
                        )
                    }
                }
                val diagonal = hypot(preview.width.toFloat(), preview.height.toFloat())
                blocks = found
                framing = steadiness.next(found, text.text.count(Char::isLetterOrDigit), diagonal)
            }
        }
        controller.setImageAnalysisAnalyzer(worker, Throttled(analyzer, ANALYSE_EVERY_MS))
        preview.controller = controller
        controller.bindToLifecycle(lifecycle)
        controller.initializationFuture.addListener({
            failed = runCatching { controller.initializationFuture.get() }.isFailure || !chooseLens(controller)
            hasTorch = !failed && controller.cameraInfo?.hasFlashUnit() == true
        }, main)
        val torchWatch = Observer<Int> {
            torch = it == TorchState.ON
            hasTorch = controller.cameraInfo?.hasFlashUnit() == true
        }
        controller.torchState.observe(lifecycle, torchWatch)
        onDispose {
            controller.torchState.removeObserver(torchWatch)
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            preview.controller = null
            recognizer.close()
            worker.shutdown()
        }
    }

    // Some countries require the shutter to be heard. Everywhere else the flash and the
    // haptic are the confirmation, and a scanner stays quiet.
    val sound = remember {
        if (Build.VERSION.SDK_INT >= 33 && MediaActionSound.mustPlayShutterSound()) {
            MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }
        } else {
            null
        }
    }
    DisposableEffect(sound) { onDispose { sound?.release() } }

    LaunchedEffect(problem) {
        if (problem != null) {
            delay(2500)
            problem = null
        }
    }

    // Boxes glide from where they were to where the next frame found them. Recognition runs
    // a few times a second, and boxes that jumped at that rate read as a fault, not as
    // tracking.
    val reach = with(LocalDensity.current) { 56.dp.toPx() }
    val glideSpec = motion(tween<Float>(170))
    val glide = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(Glide(emptyList(), emptyList())) }
    LaunchedEffect(blocks) {
        val now = shown.at(glide.value)
        shown = Glide(origins = pair(now, blocks, reach), targets = blocks)
        glide.snapTo(0f)
        glide.animateTo(1f, glideSpec)
    }
    val strength by animateFloatAsState(
        targetValue = when (framing) {
            Framing.NONE -> 0.45f
            Framing.FOUND -> 0.8f
            Framing.STEADY -> 1f
        },
        animationSpec = motion(RedlineMotion.effects()),
        label = "strength",
    )

    var focusAt by remember { mutableStateOf(Offset.Unspecified) }
    var focusKey by remember { mutableIntStateOf(0) }
    val focusRing = remember { Animatable(1f) }
    val focusSpec = motion(tween<Float>(900))
    LaunchedEffect(focusKey) {
        if (focusKey > 0) {
            focusRing.snapTo(0f)
            focusRing.animateTo(1f, focusSpec)
        }
    }

    // The frame under the shutter, flown from full screen into the page stack.
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var slot by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    var flying by remember { mutableStateOf<ImageBitmap?>(null) }
    var flightJob by remember { mutableStateOf<Job?>(null) }
    val flight = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val flightSpec = motion(tween<Float>(420, delayMillis = 80, easing = RedlineMotion.Decelerate))
    val flashSpec = motion(tween<Float>(300, easing = RedlineMotion.Decelerate))

    fun capture() {
        if (saving || failed) return
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        sound?.play(MediaActionSound.SHUTTER_CLICK)
        saving = true
        val before = thumb
        val frame = runCatching { preview.bitmap }.getOrNull()?.asImageBitmap()
        if (frame != null) {
            flightJob?.cancel()
            flying = frame
            flightJob = scope.launch {
                flight.snapTo(0f)
                flight.animateTo(1f, flightSpec)
                thumb = frame
                flying = null
            }
        }
        if (animate) scope.launch {
            flash.snapTo(0.7f)
            flash.animateTo(0f, flashSpec)
        }
        val file = File(pagesDir(context), "scan-${System.currentTimeMillis()}.jpg")
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    saving = false
                    taken++
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    handOver(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                }

                override fun onError(exception: ImageCaptureException) {
                    saving = false
                    file.delete()
                    flightJob?.cancel()
                    flying = null
                    thumb = before
                    problem = "That page did not save. Try again."
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { root = it }) {
        AndroidView(
            factory = { preview },
            modifier = Modifier
                .fillMaxSize()
                // The preview's own tap-to-focus runs underneath. This only watches, on the
                // first pass, so it can draw where the focus landed.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                        if ((up.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                            focusAt = up.position
                            focusKey++
                        }
                    }
                },
        )

        Canvas(Modifier.fillMaxSize()) {
            val t = glide.value
            val pad = 4.dp.toPx()
            val corner = CornerRadius(6.dp.toPx())
            val line = Stroke(1.5.dp.toPx())
            shown.targets.forEachIndexed { i, target ->
                val origin = shown.origins[i]
                val b = if (origin == null) target else lerp(origin, target, t)
                val a = strength * if (origin == null) t else 1f
                val topLeft = Offset(b.left - pad, b.top - pad)
                val size = Size(b.right - b.left + 2 * pad, b.bottom - b.top + 2 * pad)
                drawRoundRect(accent.copy(alpha = 0.16f * a), topLeft, size, corner)
                drawRoundRect(accent.copy(alpha = 0.9f * a), topLeft, size, corner, style = line)
            }
            val p = focusRing.value
            if (focusAt.isSpecified && p < 1f) {
                val grow = (p / 0.3f).coerceAtMost(1f)
                val radius = 34.dp.toPx() * (1.25f - 0.25f * grow)
                val fade = if (p < 0.6f) 1f else (1f - p) / 0.4f
                drawCircle(OnCamera.copy(alpha = 0.9f * fade), radius, focusAt, style = Stroke(1.5.dp.toPx()))
            }
        }

        // Scrims top and bottom: white controls have to read over a white page.
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Backdrop.copy(alpha = 0.6f), Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(240.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Backdrop.copy(alpha = 0.7f)))),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Backdrop.copy(alpha = 0.35f),
                    contentColor = OnCamera,
                ),
            ) { Icon(RedlineIcons.Close, contentDescription = "Close the camera") }
            Text(
                text = "Page ${total + 1}",
                style = MaterialTheme.typography.titleMedium,
                color = OnCamera,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            if (hasTorch) {
                IconToggleButton(
                    checked = torch,
                    onCheckedChange = { on ->
                        controller.enableTorch(on)
                        haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    },
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        containerColor = Backdrop.copy(alpha = 0.35f),
                        contentColor = OnCamera,
                        checkedContainerColor = OnCamera,
                        checkedContentColor = Backdrop,
                    ),
                ) { Icon(if (torch) FlashOn else FlashOff, contentDescription = "Torch") }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Cue(
                text = when {
                    failed -> "The camera did not start. Close it and try again."
                    problem != null -> problem.orEmpty()
                    saving -> "Saving page ${total + 1}"
                    framing == Framing.STEADY -> "Steady. Take the photo."
                    framing == Framing.FOUND -> "Text found, hold steady"
                    else -> "Point at a page of the lease"
                },
                ready = framing == Framing.STEADY && !saving && !failed,
                accent = accent,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Pages(image = thumb, count = total, onPlaced = { slot = it })
                }
                Shutter(
                    page = total + 1,
                    ready = framing == Framing.STEADY,
                    busy = saving,
                    enabled = !failed,
                    accent = accent,
                    onClick = ::capture,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Done(visible = total > 0, onDone = onDone)
                }
            }
        }

        val frame = flying
        if (frame != null) {
            Canvas(Modifier.fillMaxSize()) {
                val here = root
                val there = slot
                val target = if (here != null && there != null && here.isAttached && there.isAttached) {
                    here.localBoundingBoxOf(there)
                } else {
                    return@Canvas
                }
                val t = flight.value
                val rect = lerp(Rect(Offset.Zero, size), target, t)
                val corner = CornerRadius(8.dp.toPx() * t)
                val (srcOffset, srcSize) = cropTo(IntSize(frame.width, frame.height), rect.size)
                clipPath(Path().apply { addRoundRect(RoundRect(rect, corner)) }) {
                    drawImage(
                        image = frame,
                        srcOffset = srcOffset,
                        srcSize = srcSize,
                        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                    )
                }
                drawRoundRect(OnCamera.copy(alpha = t), rect.topLeft, rect.size, corner, style = Stroke(1.5.dp.toPx()))
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = flash.value }
                .background(OnCamera),
        )
    }
}

/** Offered once there is at least one page to finish with. */
@Composable
private fun Done(visible: Boolean, onDone: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion(tween(180))) + scaleIn(motion(tween(180)), initialScale = 0.9f),
        exit = fadeOut(motion(tween(120))),
    ) {
        Button(
            onClick = onDone,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = OnCamera, contentColor = Backdrop),
        ) { Text("Done", style = MaterialTheme.typography.labelLarge) }
    }
}

/** The line under the frame. Read out as it changes, so the cue works with the screen off too. */
@Composable
private fun Cue(text: String, ready: Boolean, accent: Color) {
    val arrive = motion(tween<Float>(160))
    val leave = motion(tween<Float>(90))
    val dot by animateColorAsState(
        targetValue = if (ready) accent else OnCamera.copy(alpha = 0.55f),
        animationSpec = motion(RedlineMotion.effects()),
        label = "dot",
    )
    Row(
        Modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clip(MaterialTheme.shapes.small)
            .background(Backdrop.copy(alpha = 0.5f))
            .animateContentSize(motion(RedlineMotion.spatial()))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).drawBehind { drawCircle(dot) })
        Spacer(Modifier.size(10.dp))
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(arrive) togetherWith fadeOut(leave) },
            label = "cue",
        ) { shown ->
            Text(shown, style = MaterialTheme.typography.labelLarge, color = OnCamera)
        }
    }
}

/**
 * The shutter. The ring takes the accent once the page is steady, so the moment to press is
 * visible without reading the cue; the disc sinks under the finger and dims while saving.
 */
@Composable
private fun Shutter(page: Int, ready: Boolean, busy: Boolean, enabled: Boolean, accent: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val ring by animateColorAsState(
        targetValue = if (ready && !busy) accent else OnCamera,
        animationSpec = motion(RedlineMotion.effects()),
        label = "ring",
    )
    val disc by animateFloatAsState(if (pressed) 0.86f else 1f, motion(RedlineMotion.effectsFast()), label = "disc")
    val fill by animateFloatAsState(if (busy || !enabled) 0.45f else 1f, motion(RedlineMotion.effects()), label = "fill")
    Box(
        Modifier
            .size(78.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled && !busy,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Take page $page" }
            .drawBehind {
                val r = size.minDimension / 2
                drawCircle(ring, radius = r - 2.dp.toPx(), style = Stroke(4.dp.toPx()))
                drawCircle(OnCamera.copy(alpha = fill), radius = (r - 9.dp.toPx()) * disc)
            },
    )
}

/** The pages taken so far: the last one as a thumbnail, with the count on its corner. */
@Composable
private fun Pages(image: ImageBitmap?, count: Int, onPlaced: (LayoutCoordinates) -> Unit) {
    val shape = MaterialTheme.shapes.extraSmall
    // The count rolls up a digit rather than swapping in place.
    val rise = motion(tween<IntOffset>(200))
    val riseFade = motion(tween<Float>(200))
    val sink = motion(tween<IntOffset>(140))
    val sinkFade = motion(tween<Float>(140))
    Box(
        Modifier
            .size(48.dp, 64.dp)
            .onGloballyPositioned(onPlaced)
            .clearAndSetSemantics {
                if (count > 0) contentDescription = if (count == 1) "1 page taken" else "$count pages taken"
            },
    ) {
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .border(1.5.dp, OnCamera, shape),
            )
            count > 0 -> Box(Modifier.fillMaxSize().border(1.5.dp, OnCamera.copy(alpha = 0.7f), shape)) {
                Icon(
                    RedlineIcons.Document,
                    contentDescription = null,
                    tint = OnCamera.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center).size(22.dp),
                )
            }
        }
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = (-9).dp)
                    .sizeIn(minWidth = 22.dp, minHeight = 22.dp)
                    .background(OnCamera, CircleShape)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        (slideInVertically(rise) { it } + fadeIn(riseFade)) togetherWith
                            (slideOutVertically(sink) { -it } + fadeOut(sinkFade))
                    },
                    label = "count",
                ) { n ->
                    Text("$n", style = MaterialTheme.typography.labelMedium, color = Backdrop)
                }
            }
        }
    }
}

/** What the camera needs, said once, with the one way forward that still works. */
@Composable
private fun Denied(refusals: Int, onAsk: () -> Unit, onClose: () -> Unit) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    // After one refusal the system will show its prompt again. After the second it stops
    // asking, and the app's own settings page is the only switch left.
    val canAsk = remember(refusals, activity) {
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 480.dp)) {
            Icon(RedlineIcons.Camera, contentDescription = null, tint = OnCamera, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(20.dp))
            Text("The camera is off for Redline", style = MaterialTheme.typography.headlineSmall, color = OnCamera)
            Spacer(Modifier.height(12.dp))
            Text(
                "Redline uses it only to photograph your lease, one page at a time. The photos stay on this phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = OnCamera.copy(alpha = 0.78f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "You can still open a photo or a PDF you already have.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnCamera.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    if (canAsk) {
                        onAsk()
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = OnCamera, contentColor = Backdrop),
            ) { Text(if (canAsk) "Allow the camera" else "Open settings", style = MaterialTheme.typography.labelLarge) }
            TextButton(
                onClick = onClose,
                colors = ButtonDefaults.textButtonColors(contentColor = OnCamera),
            ) { Text("Not now", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** Light bar icons over the dark frame, and the screen kept awake while a page is lined up. */
@Composable
private fun CameraWindow() {
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val bars = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = bars?.isAppearanceLightStatusBars
        val lightNavigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        view.keepScreenOn = true
        onDispose {
            if (bars != null && lightStatus != null && lightNavigation != null) {
                bars.isAppearanceLightStatusBars = lightStatus
                bars.isAppearanceLightNavigationBars = lightNavigation
            }
            view.keepScreenOn = false
        }
    }
}

private fun cameraAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** The back camera, or the front one on a device that only has that. False when there is neither. */
private fun chooseLens(controller: LifecycleCameraController): Boolean = when {
    controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> true
    controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> {
        controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        true
    }
    else -> false
}

private fun pagesDir(context: Context): File = File(context.cacheDir, "pages").apply { mkdirs() }

private fun sweepOldPages(context: Context) {
    val cutoff = System.currentTimeMillis() - STALE_MS
    pagesDir(context).listFiles()
        ?.filter { PAGE_NAME.matches(it.name) && it.lastModified() < cutoff }
        ?.forEach { it.delete() }
}

/**
 * Hands a frame to [inner] at most once every [everyMs] and drops the rest.
 *
 * The outlines only need to keep up with a hand, and recognising every frame the camera
 * delivers would spend the battery and heat the phone for nothing a person could see.
 * Everything else is forwarded, so CameraX still maps coordinates through to the analyser.
 */
private class Throttled(private val inner: ImageAnalysis.Analyzer, private val everyMs: Long) : ImageAnalysis.Analyzer {
    private var last = 0L

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - last < everyMs) {
            image.close()
            return
        }
        last = now
        inner.analyze(image)
    }

    override fun getDefaultTargetResolution(): AndroidSize? = inner.defaultTargetResolution

    override fun getTargetCoordinateSystem(): Int = inner.targetCoordinateSystem

    override fun updateTransform(matrix: Matrix?) = inner.updateTransform(matrix)
}

/** The blocks on screen: each new one paired with where it came from, null when it just appeared. */
private data class Glide(val origins: List<Block?>, val targets: List<Block>) {
    fun at(t: Float): List<Block> = targets.mapIndexed { i, b -> origins[i]?.let { lerp(it, b, t) } ?: b }
}

/** The centred part of an image of [image] size with the shape of [frame], so a thumbnail crops instead of squashing. */
private fun cropTo(image: IntSize, frame: Size): Pair<IntOffset, IntSize> {
    if (frame.width <= 0f || frame.height <= 0f || image.width <= 0 || image.height <= 0) {
        return IntOffset.Zero to image
    }
    val want = frame.width / frame.height
    val have = image.width.toFloat() / image.height
    return if (have > want) {
        val w = (image.height * want).roundToInt().coerceIn(1, image.width)
        IntOffset((image.width - w) / 2, 0) to IntSize(w, image.height)
    } else {
        val h = (image.width / want).roundToInt().coerceIn(1, image.height)
        IntOffset(0, (image.height - h) / 2) to IntSize(image.width, h)
    }
}

// Material Symbols flash_on and flash_off. The shared icon set has no torch, and a camera
// screen is the only place that needs one.
private val FlashOn = icon("M7,2v11h3v9l7,-12h-4l4,-8z")
private val FlashOff = icon("M3.27,3L2,4.27l5,5V13h3v9l3.58,-6.14L17.73,20 19,18.73 3.27,3zM17,10h-4l4,-8H7v2.18l8.46,8.46L17,10z")

private fun icon(path: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply { addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)) }
        .build()
