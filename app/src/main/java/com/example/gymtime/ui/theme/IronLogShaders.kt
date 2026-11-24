package com.example.gymtime.ui.theme

import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

private const val ANODIZED_GRAIN_AGSL = """
uniform float uAlpha;
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
half4 main(vec2 fragCoord) {
    float n = hash12(floor(fragCoord));
    float signedNoise = (n - 0.5) * 2.0;
    float lum = signedNoise > 0.0 ? 1.0 : 0.0;
    float a = abs(signedNoise) * uAlpha;
    return half4(lum * a, lum * a, lum * a, a);
}
"""

private const val TELEMETRY_SPOTLIGHT_AGSL = """
uniform vec2 uSize;
uniform vec2 uCenter;
uniform vec4 uAccent;
uniform float uIntensity;
uniform float uVignetteAlpha;
half4 main(vec2 fragCoord) {
    vec2 safeSize = max(uSize, vec2(1.0));
    vec2 uv = fragCoord / safeSize;
    vec2 delta = (fragCoord - uCenter) / max(max(safeSize.x, safeSize.y), 1.0);
    float dist = length(delta);
    float bloom = exp(-dist * dist * 4.5) * uIntensity;
    vec2 centered = (uv - 0.5) * 2.0;
    float vig = smoothstep(0.60, 1.48, length(centered)) * uVignetteAlpha;
    half3 rgb = half3(uAccent.rgb) * half(bloom);
    half a = half(clamp(bloom + vig, 0.0, 1.0));
    return half4(rgb, a);
}
"""

private const val PR_SHOCKWAVE_AGSL = """
uniform vec2 uSize;
uniform vec4 uAccent;
uniform float uProgress;
half4 main(vec2 fragCoord) {
    vec2 safeSize = max(uSize, vec2(1.0));
    vec2 center = safeSize * 0.5;
    float maxRadius = length(center);
    float dist = length(fragCoord - center) / max(maxRadius, 1.0);
    float ringRadius = mix(0.06, 1.02, uProgress);
    float bandWidth = mix(0.14, 0.05, uProgress);
    float d = abs(dist - ringRadius);
    float ring = exp(-(d * d) / max(bandWidth * bandWidth * 0.25, 0.0001));
    float dRim = abs(dist - (ringRadius - 0.025));
    float edgeSheen = exp(-(dRim * dRim) / max(bandWidth * bandWidth * 0.20, 0.0001));
    float fade = 1.0 - uProgress;
    half3 coreColor = half3(uAccent.rgb);
    half3 rimColor = mix(coreColor, half3(1.0, 1.0, 1.0), half(0.45));
    float alphaCore = ring * fade * 0.55;
    float alphaRim = edgeSheen * fade * 0.28;
    half3 rgb = coreColor * half(alphaCore) + rimColor * half(alphaRim);
    half totalA = half(clamp(alphaCore + alphaRim, 0.0, 1.0));
    return half4(rgb, totalA);
}
"""

private const val AMBIENT_BACKDROP_AGSL = """
uniform vec2 uSize;
uniform vec4 uTopColor;
uniform vec4 uBottomColor;
uniform vec4 uSpotlightColor;
uniform float uSpotlightStrength;
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
half4 main(vec2 fragCoord) {
    vec2 safeSize = max(uSize, vec2(1.0));
    vec2 uv = fragCoord / safeSize;
    float verticalT = smoothstep(0.0, 1.0, uv.y);
    vec3 baseRgb = mix(uTopColor.rgb, uBottomColor.rgb, verticalT);
    vec2 lightPos = vec2(safeSize.x * 0.5, safeSize.y * 0.08);
    float dist = length((fragCoord - lightPos) / max(max(safeSize.x, safeSize.y), 1.0));
    float glow = exp(-dist * dist * 3.8) * uSpotlightStrength;
    vec3 litRgb = mix(baseRgb, uSpotlightColor.rgb, clamp(glow, 0.0, 0.35));
    float dither = (hash12(floor(fragCoord)) - 0.5) * (1.0 / 255.0);
    return half4(half3(clamp(litRgb + dither, 0.0, 1.0)), 1.0);
}
"""

private fun compileShaderOrNull(agsl: String): RuntimeShader? =
    runCatching { RuntimeShader(agsl) }.getOrNull()

/**
 * Ultra-subtle static anodized aluminum / bead-blasted micro-grain cached in `drawWithCache`
 * (zero per-frame cost). Falls back cleanly to a no-op if `RuntimeShader` is unavailable.
 */
fun Modifier.anodizedMicroGrain(
    alpha: Float = 0.025f
): Modifier = if (alpha <= 0.001f) {
    this
} else {
    composed {
        val shader = remember { compileShaderOrNull(ANODIZED_GRAIN_AGSL) }
        drawWithCache {
            val brush = shader?.let { s ->
                runCatching {
                    s.setFloatUniform("uAlpha", alpha.coerceIn(0f, 0.25f))
                    ShaderBrush(s)
                }.getOrNull()
            }
            onDrawWithContent {
                drawContent()
                if (brush != null) {
                    drawRect(brush = brush)
                }
            }
        }
    }
}

/**
 * Localized radial accent bloom + subtle peripheral obsidian vignette cached in `drawWithCache`.
 * Draws behind content (after preceding background modifiers) with a `Brush.radialGradient` fallback.
 */
fun Modifier.telemetrySpotlight(
    accent: Color,
    centerFraction: Offset = Offset(0.85f, 0.12f),
    intensity: Float = 0.18f,
    vignetteAlpha: Float = 0.08f
): Modifier = composed {
    val shader = remember { compileShaderOrNull(TELEMETRY_SPOTLIGHT_AGSL) }
    drawWithCache {
        val centerPx = Offset(size.width * centerFraction.x, size.height * centerFraction.y)
        val safeIntensity = intensity.coerceIn(0f, 1f)
        val safeVignette = vignetteAlpha.coerceIn(0f, 0.5f)
        val brush = shader?.let { s ->
            runCatching {
                s.setFloatUniform("uSize", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                s.setFloatUniform("uCenter", centerPx.x, centerPx.y)
                s.setFloatUniform("uAccent", accent.red, accent.green, accent.blue, 1f)
                s.setFloatUniform("uIntensity", safeIntensity)
                s.setFloatUniform("uVignetteAlpha", safeVignette)
                ShaderBrush(s)
            }.getOrNull()
        } ?: Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = safeIntensity),
                accent.copy(alpha = safeIntensity * 0.35f),
                Color.Transparent
            ),
            center = centerPx,
            radius = max(size.width, size.height).coerceAtLeast(1f) * 0.85f
        )

        onDrawBehind {
            drawRect(brush = brush)
        }
    }
}

/**
 * Event-driven expanding chromatic/accent energy ripple active only while `progressProvider() in 0.001f..0.999f`.
 * Reads `progressProvider()` strictly in the draw phase for zero recompositions and zero idle GPU cost.
 */
fun Modifier.prShockwaveOverlay(
    progressProvider: () -> Float,
    accent: Color
): Modifier = composed {
    val shader = remember { compileShaderOrNull(PR_SHOCKWAVE_AGSL) }
    val shaderBrush = remember(shader) {
        shader?.let { s -> runCatching { ShaderBrush(s) }.getOrNull() }
    }
    drawWithContent {
        drawContent()
        val progress = progressProvider()
        if (progress !in 0.001f..0.999f) return@drawWithContent
        val clamped = progress.coerceIn(0.001f, 0.999f)
        val drawnShader = if (shader != null && shaderBrush != null) {
            runCatching {
                shader.setFloatUniform(
                    "uSize",
                    size.width.coerceAtLeast(1f),
                    size.height.coerceAtLeast(1f)
                )
                shader.setFloatUniform("uAccent", accent.red, accent.green, accent.blue, 1f)
                shader.setFloatUniform("uProgress", clamped)
                drawRect(brush = shaderBrush)
                true
            }.getOrDefault(false)
        } else {
            false
        }
        if (!drawnShader) {
            val maxRadius = hypot(size.width * 0.5f, size.height * 0.5f).coerceAtLeast(1f)
            val ringRadius = maxRadius * (0.06f + 0.96f * clamped)
            val fade = 1f - clamped
            drawCircle(
                color = accent.copy(alpha = fade * 0.45f),
                radius = ringRadius,
                style = Stroke(width = (fade * 12.dp.toPx() + 2.dp.toPx()))
            )
        }
    }
}

/**
 * Convenience overload for static/stateful `progress` float callers.
 * Prefer the `progressProvider: () -> Float` overload for zero-recomposition frame updates.
 */
fun Modifier.prShockwaveOverlay(
    progress: Float,
    accent: Color
): Modifier = if (progress !in 0.001f..0.999f) {
    this
} else {
    prShockwaveOverlay(progressProvider = { progress }, accent = accent)
}

/**
 * Deep obsidian void (`#07080B` tinted 2% toward accent with subtle overhead light falloff and
 * 1-LSB dither) in dark mode, or machined ceramic (`#E9ECEF` tinted 2% toward accent) in light mode.
 */
fun Modifier.obsidianAmbientBackdrop(
    accent: Color,
    isDark: Boolean
): Modifier = composed {
    val shader = remember { compileShaderOrNull(AMBIENT_BACKDROP_AGSL) }
    val seed = accent.copy(alpha = 1f)
    val topColor = if (isDark) {
        lerp(Color(0xFF0A0C11), seed, 0.025f)
    } else {
        lerp(Color(0xFFEEF1F4), seed, 0.02f)
    }
    val bottomColor = if (isDark) {
        lerp(Color(0xFF050609), seed, 0.015f)
    } else {
        lerp(Color(0xFFE3E7EC), seed, 0.02f)
    }
    val spotlightColor = if (isDark) {
        lerp(Color(0xFF141822), seed, 0.14f)
    } else {
        lerp(Color.White, seed, 0.06f)
    }
    val spotlightStrength = if (isDark) 0.35f else 0.25f

    drawWithCache {
        val brush = shader?.let { s ->
            runCatching {
                s.setFloatUniform("uSize", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                s.setFloatUniform("uTopColor", topColor.red, topColor.green, topColor.blue, 1f)
                s.setFloatUniform("uBottomColor", bottomColor.red, bottomColor.green, bottomColor.blue, 1f)
                s.setFloatUniform("uSpotlightColor", spotlightColor.red, spotlightColor.green, spotlightColor.blue, 1f)
                s.setFloatUniform("uSpotlightStrength", spotlightStrength)
                ShaderBrush(s)
            }.getOrNull()
        } ?: Brush.verticalGradient(
            colors = listOf(topColor, bottomColor)
        )

        onDrawBehind {
            drawRect(brush = brush)
        }
    }
}
