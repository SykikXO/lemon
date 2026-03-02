package com.sykik.lemon.presentation.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

class Star(
    val initialX: Float,
    val initialY: Float,
    val radius: Float,
    val speed: Float,
    val blinkSpeed: Float,
    val baseAlpha: Float
)

@Composable
fun AnimatedStarfield(modifier: Modifier = Modifier, isDarkMode: Boolean) {
    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }

    val stars = remember { mutableListOf<Star>() }

    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (width != size.width || height != size.height) {
            width = size.width
            height = size.height
            stars.clear()
            val starCount = (width * height / 8000).toInt().coerceIn(50, 250)
            for (i in 0 until starCount) {
                stars.add(
                    Star(
                        initialX = Random.nextFloat() * width,
                        initialY = Random.nextFloat() * height,
                        radius = Random.nextFloat() * 2f + 0.5f,
                        speed = Random.nextFloat() * 1.5f + 0.2f,
                        blinkSpeed = Random.nextFloat() * 0.3f + 0.1f,
                        baseAlpha = Random.nextFloat() * 0.7f + 0.3f
                    )
                )
            }
        }

        // Anime gradient backgrounds
        val darkBg = Brush.verticalGradient(
            colors = listOf(Color(0xFF090E17), Color(0xFF131A2A), Color(0xFF1B2338))
        )
        val lightBg = Brush.verticalGradient(
            colors = listOf(Color(0xFF87CEEB), Color(0xFFCBEBFF), Color(0xFFE2F3FD))
        )

        drawRect(brush = if (isDarkMode) darkBg else lightBg)

        val activeStarColor = Color.White

        for (star in stars) {
            var currentY = (star.initialY - star.speed * time) % height
            if (currentY < 0) currentY += height
            
            var currentX = (star.initialX - (star.speed * 0.3f) * time) % width
            if (currentX < 0) currentX += width

            val alphaMod = (sin((time * star.blinkSpeed) + star.initialX) + 1f) / 2f
            val currentAlpha = (star.baseAlpha * alphaMod).coerceIn(0.1f, 1f)
            val finalAlpha = if (isDarkMode) currentAlpha else currentAlpha * 0.6f

            drawCircle(
                color = activeStarColor.copy(alpha = finalAlpha),
                radius = star.radius,
                center = Offset(currentX, currentY)
            )
        }
    }
}
