package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DashboardCardModel(
    val title: String,
    val subtitle: String,
    val color: Color,
    val icon: ImageVector,
    val route: String? = null,
    val action: (() -> Unit)? = null
)

@Composable
fun DashboardCard(card: DashboardCardModel) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = card.color.copy(alpha = 0.1f),
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = { card.action?.invoke() })
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(card.color.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = "Open ${card.title}",
                    tint = card.color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        letterSpacing = 0.5.sp
                    ),
                    maxLines = 2,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun McqsQuizHomeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateQuiz: () -> Unit,
    onNavigateToSavedQuizzes: () -> Unit,
    onNavigateToAttemptedQuizzes: () -> Unit,
    onNavigateToGodMode: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val gridCells = when {
        configuration.screenWidthDp < 600 -> 2
        configuration.screenWidthDp < 900 -> 3
        else -> 4
    }

    val cards = remember {
        listOf(
            DashboardCardModel(
                title = "Create Quiz",
                subtitle = "Filter by subject, topic, and difficulty.",
                color = Color(0xFF4F46E5), // Indigo
                icon = Icons.Outlined.AddCircleOutline,
                action = onNavigateToCreateQuiz
            ),
            DashboardCardModel(
                title = "Saved Quizzes",
                subtitle = "Resume paused quizzes or view completed ones.",
                color = Color(0xFF10B981), // Emerald
                icon = Icons.Outlined.Save,
                action = onNavigateToSavedQuizzes
            ),
            DashboardCardModel(
                title = "Attempted Quizzes",
                subtitle = "Review your past performance.",
                color = Color(0xFFF59E0B), // Amber
                icon = Icons.Outlined.History,
                action = onNavigateToAttemptedQuizzes
            ),
            DashboardCardModel(
                title = "God-Mode",
                subtitle = "Create Exam Blueprints",
                color = Color(0xFFF43F5E), // Rose
                icon = Icons.Outlined.WorkspacePremium,
                action = onNavigateToGodMode
            )
        )
    }

    // Simple clean background
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFAFAFA)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    TextButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F172A))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back to Dashboard", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF0F172A))
                    }
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back to Dashboard", color = Color(0xFF0F172A))
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Header Section
                item {
                    Text(
                        text = "MCQs Quiz",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        ),
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create, resume, or review your mock tests.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Grid Section
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val chunkedCards = cards.chunked(gridCells)
                        chunkedCards.forEach { rowCards ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                rowCards.forEach { card ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        DashboardCard(card = card)
                                    }
                                }
                                // Fill empty spaces if the row is not full
                                repeat(gridCells - rowCards.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}
