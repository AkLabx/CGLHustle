package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterCapsule(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    unselectedBgColor: Color = Color.White,
    unselectedBorderColor: Color = Color(0xFFE2E8F0),
    unselectedTextColor: Color = Color(0xFF64748B)
) {
    val isEnabled = count > 0 || isSelected
    
    val bgColor by animateColorAsState(targetValue = if (isSelected) activeColor.copy(alpha = 0.15f) else unselectedBgColor, label = "bg")
    val borderColor by animateColorAsState(targetValue = if (isSelected) activeColor.copy(alpha = 0.6f) else unselectedBorderColor, label = "border")
    val contentColor by animateColorAsState(targetValue = if (isSelected) activeColor else unselectedTextColor, label = "content")

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.height(36.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = if (isEnabled) contentColor else contentColor.copy(alpha=0.3f),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(
                        color = if (isSelected) activeColor.copy(alpha = 0.25f) else unselectedBorderColor, // Use unselectedBorderColor as a slight darker gray for bubble
                        shape = CircleShape
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isEnabled) contentColor else contentColor.copy(alpha=0.3f)
                )
            }
        }
    }
}

