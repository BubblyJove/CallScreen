package com.callscreen.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

private val avatarColors = listOf(
    Color(0xFF2E7D32), // Green
    Color(0xFF1565C0), // Blue
    Color(0xFFC62828), // Red
    Color(0xFF6A1B9A), // Purple
    Color(0xFFEF6C00), // Orange
    Color(0xFF00838F), // Teal
    Color(0xFF4E342E), // Brown
    Color(0xFF37474F), // Blue Grey
)

@Composable
fun ContactAvatar(
    name: String?,
    phoneNumber: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val letter = (name?.firstOrNull() ?: phoneNumber.firstOrNull { it.isLetter() } ?: '#')
        .uppercaseChar()
    val colorIndex = phoneNumber.hashCode().absoluteValue % avatarColors.size
    val bgColor = avatarColors[colorIndex]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}
