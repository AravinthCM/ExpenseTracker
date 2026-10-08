package com.example.expensetracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val appColors = LocalAppColors.current

    val scaleAnim = remember { Animatable(0.4f) }
    val alphaAnim = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    LaunchedEffect(Unit) {
        // Run entrance animations
        scaleAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
        alphaAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 500)
        )
        // Wait on splash screen
        delay(1600)
        onSplashFinished()
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            appColors.surface,
            appColors.bg,
            appColors.surfaceAlt
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(32.dp)
                .scale(scaleAnim.value)
                .alpha(alphaAnim.value)
        ) {
            // Glowing Logo Container
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Outer glowing pulse ring
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                        .background(
                            color = appColors.accentSoft,
                            shape = CircleShape
                        )
                )

                // Inner Logo Badge
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(appColors.accent, appColors.accentSoft)
                            ),
                            shape = RoundedCornerShape(26.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "ExpenseTracker Logo",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App Brand Name
            Text(
                text = "ExpenseTracker",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    letterSpacing = 0.5.sp
                ),
                color = appColors.ink
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Track Expenses • Split Bills • Settle Debts",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.4.sp
                ),
                color = appColors.inkFaint,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Loading Dot Row Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(appColors.accent, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(appColors.accent.copy(alpha = 0.5f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(appColors.accent.copy(alpha = 0.2f), CircleShape)
                )
            }
        }
    }
}
