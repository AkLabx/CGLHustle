package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActiveSessionEntity
import com.example.domain.models.QuizRuntimeState
import com.example.ui.viewmodel.QuizLibraryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizLibraryScreen(
    viewModel: QuizLibraryViewModel,
    initialTab: String = "saved",
    onNavigateBack: () -> Unit,
    onQuizSelected: (String) -> Unit
) {
    val quizzes by viewModel.quizzes.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    LaunchedEffect(initialTab) {
        viewModel.initialize(forceRefresh = (initialTab == "created"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Quizzes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFAFAFA)
                )
            )
        },
        containerColor = Color(0xFFFAFAFA)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isRefreshing && quizzes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF5A51E1))
                }
            } else if (quizzes.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.LibraryBooks,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF94A3B8)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No saved quizzes found.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF64748B)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = quizzes, key = { it.id }) { session ->
                        QuizSessionCard(session = session, onClick = { onQuizSelected(session.id) })
                    }
                }
                
                if (isRefreshing) {
                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.TopCenter) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF5A51E1), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun QuizSessionCard(session: ActiveSessionEntity, onClick: () -> Unit) {
    // Decoding just enough info to display
    val parsedState = remember(session.state) {
        try {
            com.example.di.SupabaseModule.appJson.decodeFromString<QuizRuntimeState>(session.state)
        } catch (e: Exception) {
            QuizRuntimeState(quizName = "Corrupted Session")
        }
    }
    
    val createdStr = remember { "Saved Quiz" }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parsedState.quizName?.takeIf { it.isNotBlank() } ?: "Custom Quiz",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = createdStr,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF64748B)
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (session.status == "paused") Color(0xFFFEF3C7) else Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = session.status.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (session.status == "paused") Color(0xFFD97706) else Color(0xFF475569)
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val answered = parsedState.answers.size
                    val total = parsedState.activeQuestions.size
                    val percentage = if (total > 0) (answered * 100) / total else 0
                    
                    CircularProgressIndicator(
                        progress = { percentage / 100f },
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF5A51E1),
                        trackColor = Color(0xFFE2E8F0),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$answered / $total completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8)
                )
            }
        }
    }
}
