package com.example.gymtime.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text as semanticsText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Calibrated spring & effect choreography tokens for IronLog's machined hardware feel.
 * Spatial properties use tuned springs; effect properties (alpha/color/glow) are critically damped.
 */
object IronMotion {
    val PressDown: SpringSpec<Float> = spring(
        dampingRatio = 1.0f,
        stiffness = 2400f,
        visibilityThreshold = 0.002f
    )

    val PressRelease: SpringSpec<Float> = spring(
        dampingRatio = 0.62f,
        stiffness = 750f,
        visibilityThreshold = 0.002f
    )

    val KeypadSnap: SpringSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = 1400f,
        visibilityThreshold = 0.002f
    )

    val KeypadSnapIntOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.82f,
        stiffness = 1400f,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )

    val MagneticDetent: SpringSpec<Float> = spring(
        dampingRatio = 0.68f,
        stiffness = 650f,
        visibilityThreshold = 0.002f
    )

    val MagneticDetentPx: SpringSpec<Float> = spring(
        dampingRatio = 0.68f,
        stiffness = 650f,
        visibilityThreshold = 0.5f
    )

    val MagneticDetentOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.68f,
        stiffness = 650f,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )

    val MagneticDetentSize: SpringSpec<IntSize> = spring(
        dampingRatio = 0.68f,
        stiffness = 650f,
        visibilityThreshold = IntSize.VisibilityThreshold
    )

    val MagneticDetentDp: SpringSpec<Dp> = spring(
        dampingRatio = 0.68f,
        stiffness = 650f,
        visibilityThreshold = 0.5.dp
    )

    val MagneticDetentRect: SpringSpec<Rect> = spring(
        dampingRatio = 0.68f,
        stiffness = 650f,
        visibilityThreshold = Rect.VisibilityThreshold
    )

    val RewardPop: SpringSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = 500f,
        visibilityThreshold = 0.002f
    )

    val SpatialLayout: SpringSpec<Float> = spring(
        dampingRatio = 0.80f,
        stiffness = 380f,
        visibilityThreshold = 0.002f
    )

    val SpatialLayoutOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = 0.80f,
        stiffness = 380f,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )

    val SpatialLayoutSize: SpringSpec<IntSize> = spring(
        dampingRatio = 0.80f,
        stiffness = 380f,
        visibilityThreshold = IntSize.VisibilityThreshold
    )

    val SpatialLayoutDp: SpringSpec<Dp> = spring(
        dampingRatio = 0.80f,
        stiffness = 380f,
        visibilityThreshold = 0.5.dp
    )

    val SpatialSlow: SpringSpec<Float> = spring(
        dampingRatio = 0.80f,
        stiffness = 200f,
        visibilityThreshold = 0.002f
    )

    val SpatialSlowSize: SpringSpec<IntSize> = spring(
        dampingRatio = 0.80f,
        stiffness = 200f,
        visibilityThreshold = IntSize.VisibilityThreshold
    )

    val SpatialSlowDp: SpringSpec<Dp> = spring(
        dampingRatio = 0.80f,
        stiffness = 200f,
        visibilityThreshold = 0.5.dp
    )

    val EffectFast: SpringSpec<Float> = spring(
        dampingRatio = 1.0f,
        stiffness = 3800f,
        visibilityThreshold = 0.002f
    )

    val EffectDefault: SpringSpec<Float> = spring(
        dampingRatio = 1.0f,
        stiffness = 1600f,
        visibilityThreshold = 0.002f
    )

    const val AtmosphericBreathMillis: Int = 2400

    val AtmosphericBreath: DurationBasedAnimationSpec<Float> = tween(
        durationMillis = AtmosphericBreathMillis,
        easing = FastOutSlowInEasing
    )

    fun <T> magneticDetent(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.68f, stiffness = 650f, visibilityThreshold = visibilityThreshold)

    fun <T> spatialLayout(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.80f, stiffness = 380f, visibilityThreshold = visibilityThreshold)

    fun <T> effectFast(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 1.0f, stiffness = 3800f, visibilityThreshold = visibilityThreshold)

    fun <T> effectDefault(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 1.0f, stiffness = 1600f, visibilityThreshold = visibilityThreshold)

    fun <T> orSnap(reducedMotion: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spec
}

/**
 * Ambient reduced-motion flag honoring OS `ANIMATOR_DURATION_SCALE == 0f`.
 * Falls back safely to `false` in unit tests and previews.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember(context) {
        mutableStateOf(readReducedMotionFlag(context))
    }

    DisposableEffect(context) {
        val observer = runCatching {
            val handler = Handler(Looper.getMainLooper())
            val obs = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    reduced = readReducedMotionFlag(context)
                }
            }
            val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
            if (uri != null) {
                context.contentResolver.registerContentObserver(uri, false, obs)
            }
            obs
        }.getOrNull()

        onDispose {
            if (observer != null) {
                runCatching { context.contentResolver.unregisterContentObserver(observer) }
            }
        }
    }

    return reduced
}

private fun readReducedMotionFlag(context: Context): Boolean = runCatching {
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    scale <= 0.001f
}.getOrDefault(false)

/**
 * Zero-recomposition asymmetric press physics + subtle top-edge specular flash in the draw phase.
 * Never blocks click dispatch and retargets cleanly across rapid taps via `collectLatest`.
 */
data class MachinedPressIndication(
    val pressedScale: Float = 0.965f,
    val highlightAlpha: Float = 0.08f,
    val cornerRadius: Dp = IronLogRadius.Control
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        MachinedPressNode(
            interactionSource = interactionSource,
            pressedScale = pressedScale,
            highlightAlpha = highlightAlpha,
            cornerRadius = cornerRadius
        )
}

private class MachinedPressNode(
    private val interactionSource: InteractionSource,
    private val pressedScale: Float,
    private val highlightAlpha: Float,
    private val cornerRadius: Dp
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private val scaleAnim = Animatable(1f)
    private val highlightAnim = Animatable(0f)
    private var cachedWidth: Float = -1f
    private var cachedHeight: Float = -1f
    private var cachedBandSize: Size = Size.Zero
    private var cachedCornerRadius: CornerRadius = CornerRadius.Zero
    private var cachedUnitBrush: Brush? = null

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions
                .filterIsInstance<PressInteraction>()
                .collectLatest { interaction ->
                    val reduced = currentValueOf(LocalReducedMotion)
                    when (interaction) {
                        is PressInteraction.Press -> {
                            coroutineScope {
                                launch {
                                    scaleAnim.animateTo(
                                        targetValue = if (reduced) 1f else pressedScale,
                                        animationSpec = if (reduced) snap() else IronMotion.PressDown
                                    )
                                }
                                launch {
                                    highlightAnim.animateTo(
                                        targetValue = highlightAlpha,
                                        animationSpec = if (reduced) tween(50) else IronMotion.EffectFast
                                    )
                                }
                            }
                        }

                        is PressInteraction.Release, is PressInteraction.Cancel -> {
                            coroutineScope {
                                launch {
                                    scaleAnim.animateTo(
                                        targetValue = 1f,
                                        animationSpec = if (reduced) snap() else IronMotion.PressRelease
                                    )
                                }
                                launch {
                                    highlightAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = if (reduced) tween(70) else IronMotion.EffectDefault
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    override fun ContentDrawScope.draw() {
        val currentScale = scaleAnim.value
        val currentHighlight = highlightAnim.value
        if (currentScale != 1f) {
            scale(scaleX = currentScale, scaleY = currentScale, pivot = center) {
                this@draw.drawContent()
                if (currentHighlight > 0.001f) {
                    drawTopSpecularFlash(currentHighlight)
                }
            }
        } else {
            drawContent()
            if (currentHighlight > 0.001f) {
                drawTopSpecularFlash(currentHighlight)
            }
        }
    }

    private fun DrawScope.drawTopSpecularFlash(alpha: Float) {
        if (size.width != cachedWidth || size.height != cachedHeight || cachedUnitBrush == null) {
            cachedWidth = size.width
            cachedHeight = size.height
            val maxRadius = min(size.width, size.height) * 0.5f
            val r = cornerRadius.toPx().coerceIn(0f, maxRadius)
            val bandHeight = (size.height * 0.45f).coerceAtLeast(1f)
            cachedBandSize = Size(size.width, bandHeight)
            cachedCornerRadius = CornerRadius(r, r)
            cachedUnitBrush = Brush.verticalGradient(
                colors = listOf(Color.White, Color.Transparent),
                startY = 0f,
                endY = bandHeight
            )
        }
        val brush = cachedUnitBrush ?: return
        drawRoundRect(
            brush = brush,
            size = cachedBandSize,
            cornerRadius = cachedCornerRadius,
            alpha = alpha.coerceIn(0f, 1f)
        )
    }
}

/**
 * Non-replaying spring entrance for cards and list rows.
 * Animates only inside `graphicsLayer` (`alpha`, `translationY`, `scale` from `0.96f -> 1f`),
 * caps stagger at `5 * 35ms`, and degrades cleanly under `LocalReducedMotion`.
 */
fun Modifier.staggeredEntranceOnce(
    key: Any,
    index: Int
): Modifier = composed {
    val enteredFlag = rememberSaveable(key, index) { booleanArrayOf(false) }
    val reducedMotion = LocalReducedMotion.current
    val alphaAnim = remember(key, index) { Animatable(if (enteredFlag[0]) 1f else 0f) }
    val progressAnim = remember(key, index) { Animatable(if (enteredFlag[0]) 1f else 0f) }

    LaunchedEffect(key, index, reducedMotion) {
        if (!enteredFlag[0]) {
            if (reducedMotion) {
                enteredFlag[0] = true
                progressAnim.snapTo(1f)
                alphaAnim.animateTo(1f, tween(durationMillis = 120))
            } else {
                val staggerMs = if (index in 1..5) index * 35L else 0L
                if (staggerMs > 0L) delay(staggerMs)
                enteredFlag[0] = true
                coroutineScope {
                    launch { alphaAnim.animateTo(1f, IronMotion.EffectDefault) }
                    launch { progressAnim.animateTo(1f, IronMotion.SpatialLayout) }
                }
            }
        } else {
            alphaAnim.snapTo(1f)
            progressAnim.snapTo(1f)
        }
    }

    graphicsLayer {
        val p = progressAnim.value
        alpha = alphaAnim.value
        if (!reducedMotion && p < 0.9995f) {
            translationY = 20.dp.toPx() * (1f - p)
            val s = 0.96f + (0.04f * p)
            scaleX = s
            scaleY = s
        }
    }
}

/**
 * Horizontal damped micro-shake triggered whenever `trigger` increments (> 0).
 * Executes in `graphicsLayer` with zero recomposition and disables lateral motion under reduced motion.
 */
fun Modifier.shakeOnTrigger(trigger: Int): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val offsetFraction = remember { Animatable(0f) }

    LaunchedEffect(trigger, reducedMotion) {
        if (trigger <= 0 || reducedMotion) {
            offsetFraction.snapTo(0f)
            return@LaunchedEffect
        }
        offsetFraction.snapTo(0f)
        offsetFraction.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 220
                0f at 0
                -1f at 40 using FastOutLinearInEasing
                0.82f at 85 using LinearEasing
                -0.52f at 130 using LinearEasing
                0.26f at 175 using LinearEasing
                0f at 220
            }
        )
    }

    graphicsLayer {
        val f = offsetFraction.value
        if (f != 0f) {
            translationX = f * 8.dp.toPx()
        }
    }
}

private class OdometerHistoryRef(
    var text: String,
    var lastUpdateMs: Long
)

/**
 * Kinetic rolling numeric readout with forced `tnum` tabular figures, direction-aware per-character
 * vertical spring slide, snapping `SizeTransform(clip = false)`, unified semantics text,
 * rapid-burst coalescing (`< 90ms`), and clean reduced-motion / non-numeric fallback.
 */
@Composable
fun OdometerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val reducedMotion = LocalReducedMotion.current
    val tabularStyle = remember(style) { style.copy(fontFeatureSettings = "tnum") }

    val nowMs = System.nanoTime() / 1_000_000L
    val historyRef = remember { OdometerHistoryRef(text = text, lastUpdateMs = nowMs - 1_000L) }
    val isTextChanged = historyRef.text != text
    val isIncreasing = isTextIncreasing(historyRef.text, text)
    val isRapidBurst = isTextChanged && (nowMs - historyRef.lastUpdateMs) < 90L
    SideEffect {
        if (historyRef.text != text) {
            historyRef.text = text
            historyRef.lastUpdateMs = nowMs
        }
    }

    if (reducedMotion || text.isEmpty() || !text.any(Char::isDigit)) {
        Text(
            text = text,
            modifier = modifier,
            style = tabularStyle,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow
        )
        return
    }

    val decimalIndex = text.indexOf('.')
    val horizontalArrangement = when (textAlign) {
        TextAlign.End, TextAlign.Right -> Arrangement.End
        TextAlign.Center -> Arrangement.Center
        else -> Arrangement.Start
    }

    Row(
        modifier = modifier.clearAndSetSemantics {
            semanticsText = AnnotatedString(text)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement
    ) {
        text.forEachIndexed { index, ch ->
            // Anchor integer digits from the decimal/right edge so place values never shift sideways.
            val slotKey = if (decimalIndex >= 0) {
                index - decimalIndex
            } else {
                -(text.length - 1 - index)
            }
            key(slotKey) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val contentTransform = if (isRapidBurst) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val enter = slideInVertically(animationSpec = IronMotion.KeypadSnapIntOffset) { fullHeight ->
                                if (isIncreasing) fullHeight else -fullHeight
                            } + fadeIn(animationSpec = IronMotion.EffectFast)
                            val exit = slideOutVertically(animationSpec = IronMotion.KeypadSnapIntOffset) { fullHeight ->
                                if (isIncreasing) -fullHeight else fullHeight
                            } + fadeOut(animationSpec = IronMotion.EffectFast)
                            enter togetherWith exit
                        }
                        contentTransform.using(
                            SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() })
                        )
                    },
                    label = "odometerChar_$slotKey"
                ) { targetChar ->
                    Text(
                        text = targetChar.toString(),
                        style = tabularStyle,
                        color = color,
                        maxLines = maxLines,
                        softWrap = false,
                        overflow = overflow
                    )
                }
            }
        }
    }
}

private fun isTextIncreasing(previous: String, current: String): Boolean {
    val prevNum = extractComparableNumber(previous)
    val currNum = extractComparableNumber(current)
    if (prevNum != null && currNum != null && prevNum != currNum) {
        return currNum > prevNum
    }
    return current >= previous
}

private fun extractComparableNumber(value: String): Double? {
    val cleaned = buildString(value.length) {
        var seenDecimal = false
        for (c in value) {
            when {
                c.isDigit() -> append(c)
                c == '-' && isEmpty() -> append(c)
                c == '.' && !seenDecimal -> {
                    seenDecimal = true
                    append(c)
                }
            }
        }
    }
    return cleaned.toDoubleOrNull()
}

/**
 * Spatial depth NavHost transitions (forward push/pop + bottom-bar sibling cross-scale),
 * automatically honoring `reducedMotion`.
 */
object IronNavTransitions {
    fun pushEnter(
        reducedMotion: Boolean = false
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reducedMotion) {
            fadeIn(tween(durationMillis = 120))
        } else {
            slideInHorizontally(animationSpec = IronMotion.SpatialLayoutOffset) { it / 5 } +
                scaleIn(animationSpec = IronMotion.SpatialLayout, initialScale = 0.96f) +
                fadeIn(animationSpec = IronMotion.EffectDefault)
        }
    }

    fun pushExit(
        reducedMotion: Boolean = false
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reducedMotion) {
            fadeOut(tween(durationMillis = 90))
        } else {
            slideOutHorizontally(animationSpec = IronMotion.SpatialLayoutOffset) { -it / 8 } +
                scaleOut(animationSpec = IronMotion.SpatialLayout, targetScale = 0.96f) +
                fadeOut(animationSpec = IronMotion.EffectFast)
        }
    }

    fun popEnter(
        reducedMotion: Boolean = false
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reducedMotion) {
            fadeIn(tween(durationMillis = 120))
        } else {
            slideInHorizontally(animationSpec = IronMotion.SpatialLayoutOffset) { -it / 8 } +
                scaleIn(animationSpec = IronMotion.SpatialLayout, initialScale = 0.96f) +
                fadeIn(animationSpec = IronMotion.EffectDefault)
        }
    }

    fun popExit(
        reducedMotion: Boolean = false
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reducedMotion) {
            fadeOut(tween(durationMillis = 90))
        } else {
            slideOutHorizontally(animationSpec = IronMotion.SpatialLayoutOffset) { it / 5 } +
                scaleOut(animationSpec = IronMotion.SpatialLayout, targetScale = 0.96f) +
                fadeOut(animationSpec = IronMotion.EffectFast)
        }
    }

    fun tabEnter(
        reducedMotion: Boolean = false
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reducedMotion) {
            fadeIn(tween(durationMillis = 120))
        } else {
            scaleIn(animationSpec = IronMotion.SpatialLayout, initialScale = 0.97f) +
                fadeIn(animationSpec = IronMotion.EffectDefault)
        }
    }

    fun tabExit(
        reducedMotion: Boolean = false
    ): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reducedMotion) {
            fadeOut(tween(durationMillis = 90))
        } else {
            scaleOut(animationSpec = IronMotion.SpatialLayout, targetScale = 0.985f) +
                fadeOut(animationSpec = IronMotion.EffectFast)
        }
    }
}
