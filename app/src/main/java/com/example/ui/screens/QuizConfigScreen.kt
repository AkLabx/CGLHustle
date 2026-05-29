package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Save
import com.example.ui.components.FilterCapsule
import com.example.ui.viewmodel.QuizConfigState
import com.example.ui.viewmodel.QuizConfigViewModel
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuizConfigScreen(
    viewModel: QuizConfigViewModel,
    initialMode: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToLibrary: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var activeSheet by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(viewModel.events, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            viewModel.events.collect { event ->
                when (event) {
                    is com.example.ui.viewmodel.QuizConfigEvent.ShowToast -> {
                        Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    }
                    is com.example.ui.viewmodel.QuizConfigEvent.NavigateToLibrary -> {
                        onNavigateToLibrary()
                    }
                }
            }
        }
    }
    
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(initialMode) {
        if (initialMode != null) {
            viewModel.setMode(initialMode)
        }
    }

    var quizName by remember { mutableStateOf("") }
    
    val bgLight = Color(0xFFFAFAFA)
    val textDark = Color(0xFF0F172A)
    val textGray = Color(0xFF64748B)

    Scaffold(
        containerColor = bgLight,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textGray)
                        Spacer(Modifier.width(4.dp))
                        Text("Back", color = textGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgLight)
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = quizName,
                        onValueChange = { quizName = it },
                        placeholder = { Text("Quiz Name (Optional)", color = textGray) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White,
                            focusedTextColor = textDark,
                            unfocusedTextColor = textDark
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            color = Color.White,
                            modifier = Modifier
                                .size(56.dp)
                                .clickable { viewModel.resetAllFilters() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = textDark)
                            }
                        }
                        
                        Button(
                            onClick = { viewModel.createQuizWithQuestions(25) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5A51E1),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFE2E8F0),
                                disabledContentColor = Color(0xFF94A3B8)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = uiState.totalAvailableCount > 0 && !uiState.isStartingQuiz
                        ) {
                            if (uiState.isStartingQuiz) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Creating...", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            } else {
                                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Create Quiz (${uiState.totalAvailableCount})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Create New Quiz", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color(0xFF1E1B4B))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Select from ", color = textGray, fontSize = 16.sp)
                        Text("${uiState.totalAvailableCount}", color = Color(0xFF5A51E1), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(" available questions.", color = textGray, fontSize = 16.sp)
                    }
                }
            }

            item {
                // Segmented Control + Quick Dropdown
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp)).padding(4.dp)
                        ) {
                            listOf(
                                "learning" to "Learning",
                                "mock" to "Mock",
                                "god" to "God Mode"
                            ).forEach { (id, label) ->
                                val isSelected = uiState.selectedMode == id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color.White else Color.Transparent)
                                        .clickable { viewModel.setMode(id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF5A51E1) else textGray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.width(8.dp))
                        
                        // Quick Presets box
                        var showQuickMenu by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF3F0FF),
                                modifier = Modifier.height(48.dp).clickable { showQuickMenu = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Quick", tint = Color(0xFF5A51E1), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Quick", color = Color(0xFF5A51E1), fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF5A51E1), modifier = Modifier.size(20.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = showQuickMenu,
                                onDismissRequest = { showQuickMenu = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Quick Revision") },
                                    onClick = { 
                                        viewModel.applyQuickPreset("Quick Revision")
                                        showQuickMenu = false 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Hard Challenge") },
                                    onClick = { 
                                        viewModel.applyQuickPreset("Hard Challenge")
                                        showQuickMenu = false 
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            if (uiState.selectedMode != "god") {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = Color(0xFF1E1B4B), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Classification", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(Modifier.height(24.dp))
                            
                            // Subjects
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("SUBJECT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textGray)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            val subjectsList = uiState.subjectCounts.keys.toList()
                            if (subjectsList.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    subjectsList.forEach { subject ->
                                        FilterCapsule(
                                            label = subject,
                                            count = uiState.subjectCounts[subject] ?: 0,
                                            isSelected = uiState.selectedSubjects.contains(subject),
                                            onClick = { viewModel.toggleSubject(subject) }
                                        )
                                    }
                                }
                            } else {
                                Text("No subjects available.", color = textGray)
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.width(32.dp))
                            Spacer(Modifier.height(24.dp))
                            
                            // Topics
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("TOPIC", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textGray)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            if (uiState.selectedSubjects.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    color = Color(0xFFFAFAFA),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("Select Subject First", color = textGray, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else if (uiState.availableTopics.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uiState.availableTopics.forEach { topic ->
                                        FilterCapsule(
                                            label = topic,
                                            count = uiState.topicCounts[topic] ?: 0,
                                            isSelected = uiState.selectedTopics.contains(topic),
                                            onClick = { viewModel.toggleTopic(topic) }
                                        )
                                    }
                                }
                            } else {
                                Text("No topics available.", color = textGray)
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            
                            // Sub-Topics
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("SUB-TOPIC", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textGray)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            if (uiState.selectedTopics.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    color = Color(0xFFFAFAFA),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("Select Topic First", color = textGray, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else if (uiState.availableSubTopics.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uiState.availableSubTopics.forEach { subTopic ->
                                        FilterCapsule(
                                            label = subTopic,
                                            count = uiState.subTopicCounts[subTopic] ?: 0,
                                            isSelected = uiState.selectedSubTopics.contains(subTopic),
                                            onClick = { viewModel.toggleSubTopic(subTopic) }
                                        )
                                    }
                                }
                            } else {
                                Text("No sub-topics available.", color = textGray)
                            }
                        }
                    }
                }
                
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Settings, contentDescription = null, tint = Color(0xFF1E1B4B), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Properties", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(Modifier.height(24.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("DIFFICULTY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textGray)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF3F4F6),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        uiState.difficultyCounts.forEach { (difficulty, count) ->
                                            val isSelected = uiState.selectedDifficulties.contains(difficulty)
                                            val textColor = if (isSelected) Color(0xFF5A51E1) else textDark
                                            Row(
                                                modifier = Modifier.clickable { viewModel.toggleDifficulty(difficulty) }.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(difficulty, color = textColor, fontSize = 16.sp)
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (isSelected) Color(0xFFE0E7FF) else Color(0xFFE2E8F0)
                                                ) {
                                                    Text(
                                                        "$count",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = textColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("QUESTION TYPE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textGray)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF3F4F6),
                                modifier = Modifier.fillMaxWidth().height(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("MCQ", color = textDark, fontSize = 16.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFE2E8F0)
                                        ) {
                                            Text(
                                                "${uiState.totalAvailableCount}",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = textDark
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                item {
                    var expanded by remember { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7) // Yellow-100
                                ) {
                                    Text(
                                        "Advanced Filters", 
                                        style = MaterialTheme.typography.titleMedium, 
                                        fontWeight = FontWeight.Bold, 
                                        color = Color(0xFF92400E), // Yellow-800
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                Icon(
                                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, 
                                    contentDescription = null,
                                    tint = textGray
                                )
                            }
                            
                            if (!expanded) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null, tint = textDark, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Active Filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textDark)
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        
                                        val activeFilters = uiState.selectedExams.size + uiState.selectedYears.size + uiState.selectedShifts.size + uiState.selectedTags.size
                                        if (activeFilters == 0) {
                                            Text(
                                                "No filters selected. Select criteria above to refine questions.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = textGray,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        } else {
                                            Text("$activeFilters advanced filters applied.", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3B82F6))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                            
                            if (expanded) {
                                Column(modifier = Modifier.padding(16.dp).background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))) {
                                    Column(modifier = Modifier.padding(24.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = Color(0xFF1E1B4B), modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Source", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                        
                                        listOf(
                                            "EXAM NAME" to Triple("Exams", uiState.availableExams, uiState.selectedExams),
                                            "EXAM YEAR" to Triple("Years", uiState.availableYears, uiState.selectedYears),
                                            "EXAM SHIFT" to Triple("Shifts", uiState.availableShifts, uiState.selectedShifts)
                                        ).forEach { (label, data) ->
                                            val (title, availableOptions, selectedOptions) = data
                                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textGray)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = availableOptions.isNotEmpty() && selectedOptions.size == availableOptions.size,
                                                            onCheckedChange = { /* Implement ALL later if needed */ },
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = textGray)
                                                    }
                                                }
                                                Spacer(Modifier.height(12.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                    color = Color.White,
                                                    modifier = Modifier.fillMaxWidth().height(48.dp).clickable { activeSheet = label }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(if (selectedOptions.isEmpty()) "Select $title" else "${selectedOptions.size} Selected", color = textGray)
                                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = textGray)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(Modifier.height(16.dp))
                                
                                Column(modifier = Modifier.padding(horizontal = 16.dp).background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))) {
                                    Column(modifier = Modifier.padding(24.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = Color(0xFF1E1B4B), modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Tags", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                                        }
                                        Spacer(modifier = Modifier.height(24.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("SEARCH TAGS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = textGray)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = uiState.availableTags.isNotEmpty() && uiState.selectedTags.size == uiState.availableTags.size,
                                                    onCheckedChange = { },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = textGray)
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            color = Color.White,
                                            modifier = Modifier.fillMaxWidth().height(48.dp).clickable { activeSheet = "TAGS" }
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(if (uiState.selectedTags.isEmpty()) "Filter by Tags" else "${uiState.selectedTags.size} Selected", color = textGray)
                                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = textGray)
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.WorkspacePremium, 
                                contentDescription = null, 
                                modifier = Modifier.size(48.dp), 
                                tint = Color(0xFFE11D48)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "God Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textDark
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Select predefined exam blueprints to auto-configure filters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = textGray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            
            // Add spacing for bottom bar
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    if (activeSheet != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val (title, availableOptions, selectedOptions) = when (activeSheet) {
            "EXAM NAME" -> Triple("Exams", uiState.availableExams, uiState.selectedExams)
            "EXAM YEAR" -> Triple("Years", uiState.availableYears, uiState.selectedYears)
            "EXAM SHIFT" -> Triple("Shifts", uiState.availableShifts, uiState.selectedShifts)
            "TAGS" -> Triple("Tags", uiState.availableTags, uiState.selectedTags)
            "SUBJECT" -> Triple("Subjects", uiState.subjectCounts.keys.toList(), uiState.selectedSubjects)
            "TOPIC" -> Triple("Topics", uiState.availableTopics, uiState.selectedTopics)
            "SUB-TOPIC" -> Triple("Sub-Topics", uiState.availableSubTopics, uiState.selectedSubTopics)
            else -> Triple("", emptyList(), emptySet<String>())
        }
        
        val scope = rememberCoroutineScope()
        
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
            containerColor = Color.White,
            contentColor = textDark
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textDark)
                    TextButton(onClick = {
                        activeSheet?.let { label -> 
                            viewModel.clearFilter(label)
                        }
                    }) {
                        Text("Clear All", color = Color(0xFF3B82F6))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                var searchQuery by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search $title...", color = textGray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = textDark,
                        unfocusedTextColor = textDark
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val filteredOptions = availableOptions.filter { it.contains(searchQuery, ignoreCase = true) }
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(filteredOptions.size) { index ->
                        val option = filteredOptions[index]
                        val isSelected = selectedOptions.contains(option)
                        val count = when (activeSheet) {
                            "EXAM NAME" -> uiState.examCounts[option] ?: 0
                            "EXAM YEAR" -> uiState.yearCounts[option] ?: 0
                            "EXAM SHIFT" -> uiState.shiftCounts[option] ?: 0
                            "TAGS" -> uiState.tagCounts[option] ?: 0
                            "SUBJECT" -> uiState.subjectCounts[option] ?: 0
                            "TOPIC" -> uiState.topicCounts[option] ?: 0
                            "SUB-TOPIC" -> uiState.subTopicCounts[option] ?: 0
                            else -> 0
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    activeSheet?.let { label -> 
                                        when (label) {
                                            "EXAM NAME" -> viewModel.toggleExam(option)
                                            "EXAM YEAR" -> viewModel.toggleYear(option)
                                            "EXAM SHIFT" -> viewModel.toggleShift(option)
                                            "TAGS" -> viewModel.toggleTag(option)
                                            "SUBJECT" -> viewModel.toggleSubject(option)
                                            "TOPIC" -> viewModel.toggleTopic(option)
                                            "SUB-TOPIC" -> viewModel.toggleSubTopic(option)
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF3B82F6),
                                    uncheckedColor = Color(0xFF94A3B8),
                                    checkmarkColor = Color.White
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(option, style = MaterialTheme.typography.bodyLarge, color = textDark)
                            Spacer(Modifier.weight(1f))
                            Text("($count)", style = MaterialTheme.typography.bodySmall, color = textGray)
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { 
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                activeSheet = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        contentColor = Color.White
                    )
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
