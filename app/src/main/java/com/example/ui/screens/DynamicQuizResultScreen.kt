package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.firstOrNull
import com.example.domain.models.QuizRuntimeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicQuizResultScreen(
    quizId: String,
    repository: com.example.data.repository.ActiveSessionRepository,
    onBackToDashboard: () -> Unit
) {
    var session by remember { mutableStateOf<QuizRuntimeState?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(quizId) {
        val entity = repository.getLocalSessionFlow(quizId).firstOrNull()
        if (entity != null && entity.state.isNotBlank()) {
            try {
                session = com.example.di.SupabaseModule.appJson.decodeFromString(
                    QuizRuntimeState.serializer(),
                    entity.state
                )
            } catch (e: Exception) {}
        }
        isLoading = false
    }
    
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (session == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Result not found")
        }
        return
    }
    
    val state = session!!
    val total = state.activeQuestions.size
    val score = state.score

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Results") },
                navigationIcon = {
                    IconButton(onClick = onBackToDashboard) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Dashboard")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Your Score", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("$score / $total", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            
            Text(
                "Detailed Review",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Bold
            )
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(state.activeQuestions) { index, question ->
                    val userAnsIdx = state.answers[question.id]?.toIntOrNull()
                    val correctChar = question.correctOption.uppercase().firstOrNull() ?: 'A'
                    val correctIdx = correctChar - 'A'
                    val isCorrect = userAnsIdx == correctIdx
                    
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Q${index + 1} You scored ${if(isCorrect) 1 else 0}/1", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HtmlText(text = question.questionText, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            question.options.forEachIndexed { i, opt ->
                                val isSelected = userAnsIdx == i
                                val isActuallyCorrect = correctIdx == i
                                val color = when {
                                    isActuallyCorrect -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                    isSelected && !isActuallyCorrect -> Color(0xFFF44336).copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(color)
                                        .padding(12.dp)
                                ) {
                                    HtmlText(text = opt)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            if (question.explanation != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Explanation:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        HtmlText(text = question.explanation.toString(), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HtmlText(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { context ->
            android.widget.TextView(context).apply {
                setTextColor(style.color.toArgb())
                textSize = style.fontSize.value
                setLineSpacing(0f, 1.5f)
            }
        },
        update = { textView ->
            textView.text = androidx.core.text.HtmlCompat.fromHtml(text, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    )
}
