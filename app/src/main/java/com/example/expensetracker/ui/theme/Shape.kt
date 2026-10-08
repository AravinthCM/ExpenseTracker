package com.example.expensetracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val RadiusSm = RoundedCornerShape(8.dp)
val RadiusMd = RoundedCornerShape(14.dp)
val RadiusLg = RoundedCornerShape(22.dp)
val RadiusPill = RoundedCornerShape(100.dp)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RadiusSm,
    medium = RadiusMd,
    large = RadiusLg,
    extraLarge = RoundedCornerShape(32.dp)
)
