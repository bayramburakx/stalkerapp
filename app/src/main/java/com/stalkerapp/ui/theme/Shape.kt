package com.stalkerapp.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Portio Design System - Shapes
 */
object PortioShape {
    val None = RoundedCornerShape(0.dp)
    val ExtraSmall = RoundedCornerShape(4.dp)
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(18.dp)
    val ExtraLarge = RoundedCornerShape(24.dp)
    val Full = CircleShape
    val Pill = RoundedCornerShape(50)

    // Component-specific shapes
    val Card = RoundedCornerShape(18.dp)
    val CardSmall = RoundedCornerShape(12.dp)
    val CardLarge = RoundedCornerShape(24.dp)
    val Poster = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(14.dp)
    val ButtonPill = RoundedCornerShape(50)
    val Badge = RoundedCornerShape(6.dp)
    val Chip = RoundedCornerShape(50)
    val Avatar = CircleShape
    val BottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Dialog = RoundedCornerShape(20.dp)
    val SearchInput = RoundedCornerShape(50)

    val MaterialShapes = Shapes(
        extraSmall = ExtraSmall,
        small = Small,
        medium = Medium,
        large = Large,
        extraLarge = ExtraLarge
    )
}
