package com.example.feature.background

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
fun BackgroundLayer(
    state: BackgroundLayerUiState,
    modifier: Modifier = Modifier
) {
    if (state.isNightDimmedBlack) {
        // Night black mode: strictly blank black canvas
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    // Default neutral background if not fresh or no image
    val bitmap = state.currentBitmap
    // The chosen wallpaper stays visible even when the reading is stale (it is just dimmed a bit more)
    val showImage = bitmap != null && !bitmap.isRecycled

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (showImage && bitmap != null) {
            val crossfadeMillis = (state.preferences.crossfadeDurationSeconds * 1000f).toInt().coerceAtLeast(0)

            // Compose ColorMatrix for Saturation, Brightness, and Contrast
            val colorFilter = remember(state.preferences.saturation, state.preferences.brightness, state.preferences.contrast) {
                val cm = ColorMatrix()
                cm.setToSaturation(state.preferences.saturation)

                val c = state.preferences.contrast
                val b = state.preferences.brightness
                val translate = (b - 1f) * 255f + (1f - c) * 128f

                val contrastMatrix = ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, translate,
                        0f, c, 0f, 0f, translate,
                        0f, 0f, c, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                contrastMatrix.timesAssign(cm)
                ColorFilter.colorMatrix(contrastMatrix)
            }

            // Optional slow zoom ("Ken Burns"); the scale is read at draw time so nothing recomposes per frame
            val kenScale: State<Float>? = if (state.preferences.kenBurnsEnabled) {
                val transition = rememberInfiniteTransition(label = "ken_burns")
                transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 24_000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "ken_burns_scale"
                )
            } else {
                null
            }
            val imageModifier = if (kenScale != null) {
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = kenScale.value
                    scaleY = kenScale.value
                }
            } else {
                Modifier.fillMaxSize()
            }

            if (crossfadeMillis > 0) {
                Crossfade(
                    targetState = bitmap,
                    animationSpec = tween(durationMillis = crossfadeMillis),
                    label = "bg_crossfade"
                ) { targetBitmap ->
                    if (!targetBitmap.isRecycled) {
                        Image(
                            bitmap = targetBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = colorFilter,
                            modifier = imageModifier
                        )
                    }
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = imageModifier
                )
            }

            // Dark layer overlay with Auto Reading Protection
            val baseOpacity = state.preferences.darkLayerOpacity.coerceIn(0f, 0.95f)
            val protectedOpacity = if (state.preferences.autoReadingProtection) {
                val brightOffset = (state.averageBrightness - 0.35f).coerceAtLeast(0f) * 0.45f
                (baseOpacity + brightOffset).coerceIn(0.15f, 0.92f)
            } else {
                baseOpacity
            }
            // Stale/missing reading: dim the wallpaper a little more
            val effectiveOpacity = if (state.isFreshActual) protectedOpacity else (protectedOpacity + 0.25f).coerceAtMost(0.95f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = effectiveOpacity))
            )

            // Optional caption with the picture's name
            val name = state.currentName
            if (state.preferences.showPhotoName && !name.isNullOrBlank()) {
                Text(
                    text = name,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 10.dp)
                )
            }
        }
    }
}
