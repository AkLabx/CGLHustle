package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.graphics.toArgb
import androidx.core.text.HtmlCompat
import android.widget.TextView
import com.example.ui.viewmodel.ActiveQuizViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

val PurpleCustom = Color(0xFF5844DF)
val OptionBorderUnselected = Color(0xFFE0E0E0)
val CorrectBg = Color(0xFFEAF7EE)
val CorrectBorder = Color(0xFF4AC26A)
val IncorrectBg = Color(0xFFFDE8EA)
val IncorrectBorder = Color(0xFFE25C6B)
val InfoBg = Color(0xFFEDF3FD)
val InfoBorder = Color(0xFFB5D1F6)
val FunFactBg = Color(0xFFFFF9E6)
val FunFactBorder = Color(0xFFFFDAB9)
val GreyText = Color(0xFF757575)
val LightGreyBg = Color(0xFFF5F5F5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveQuizScreen(
    viewModel: ActiveQuizViewModel,
    onBack: () -> Unit,
    onSubmitSuccess: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.pauseTimer()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.resumeTimer()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val session by viewModel.uiState.collectAsState()
    val hydrationState by viewModel.hydrationState.collectAsState()

    val isSubmitting by viewModel.isSubmitting.collectAsState()

    val isSubmitted = session?.status == "result"
    LaunchedEffect(isSubmitted) {
        if (isSubmitted) {
            onSubmitSuccess()
        }
    }
    
    if (isSubmitting) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = PurpleCustom)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Finalizing Quiz Session...", color = PurpleCustom, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    when (hydrationState) {
        is com.example.viewmodel.HydrationState.Idle,
        is com.example.viewmodel.HydrationState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PurpleCustom)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Hydrating your quiz session...")
                }
            }
            return
        }
        is com.example.viewmodel.HydrationState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${(hydrationState as com.example.viewmodel.HydrationState.Error).message}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            }
            return
        }
        is com.example.viewmodel.HydrationState.Success -> {
            // Proceed rendering session
        }
    }

    if (session == null || session!!.activeQuestions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Error: Quiz not started or empty.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    val currentQuestionIndex = session!!.currentQuestionIndex
    val currentQuestion = session!!.activeQuestions.getOrNull(currentQuestionIndex) ?: return

    var showNavigationGrid by remember { mutableStateOf(false) }

    val isAttempted = session!!.answers.containsKey(currentQuestion.id)
    val selectedOptionIndex = session!!.answers[currentQuestion.id]?.toIntOrNull() ?: -1

    if (showNavigationGrid) {
        ModalBottomSheet(
            onDismissRequest = { showNavigationGrid = false },
            containerColor = Color.White
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(session!!.activeQuestions.size) { index ->
                    val q = session!!.activeQuestions[index]
                    val isAns = session!!.answers.containsKey(q.id)
                    val ans = session!!.answers[q.id]?.toIntOrNull() ?: -1
                    val correctIndex = try { (q.correctOption.uppercase().firstOrNull() ?: 'A') - 'A' } catch(e:Exception){0}
                    val isMark = session!!.markedForReview.contains(q.id)
                    val isCurr = index == currentQuestionIndex
                    
                    val bgColor = if (isAns) {
                        if (ans == correctIndex) CorrectBorder else IncorrectBorder
                    } else Color.White
                    
                    val borderColor = if (isAns) {
                        if (ans == correctIndex) CorrectBorder else IncorrectBorder
                    } else OptionBorderUnselected
                    
                    val textColor = if (isAns) Color.White else Color.DarkGray
                    
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .border(if (isCurr) 2.dp else 1.dp, if (isCurr) Color.Black else borderColor, RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.goToQuestion(index)
                                showNavigationGrid = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = textColor, fontWeight = FontWeight.Bold)
                        if (isMark) {
                            Icon(
                                Icons.Filled.Flag,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(12.dp),
                                tint = Color.Red
                            )
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showNavigationGrid = true }
                    ) {
                        Text(session!!.quizName ?: "Saved Quiz", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Expand", tint = Color.White)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Home, contentDescription = "Home", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Handle Pause */ }) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PurpleCustom,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            BottomActionBar(
                onSettings = {},
                onMenu = { showNavigationGrid = true },
                onPrevious = { if (currentQuestionIndex > 0) viewModel.goToQuestion(currentQuestionIndex - 1) },
                onFiftyFifty = { viewModel.useFiftyFifty(currentQuestion.id) },
                onNext = {
                    if (currentQuestionIndex < session!!.activeQuestions.size - 1) {
                        viewModel.goToQuestion(currentQuestionIndex + 1)
                    } else {
                        viewModel.submitQuiz {
                            onSubmitSuccess()
                        }
                    }
                },
                isPreviousEnabled = currentQuestionIndex > 0,
                isNextEnabled = true, // as per audit, changed behavior
                isLastQuestion = currentQuestionIndex == session!!.activeQuestions.size - 1,
                isMarked = session!!.markedForReview.contains(currentQuestion.id),
                onMarkForReview = { viewModel.toggleMarkForReview(currentQuestion.id) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding)
        ) {
            // Action Row
            val isBookmarked = false // Needs proper DB state, assume false
            ActionRow(
                timeFlow = viewModel.timeRemaining,
                onBookmark = { viewModel.toggleBookmark(currentQuestion.id) }
            )
            
            // Progress Bar
            ProgressSection(
                current = currentQuestionIndex + 1,
                total = session!!.activeQuestions.size
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                // Tags
                TagsRow()
                Spacer(modifier = Modifier.height(24.dp))
                
                // Question Text
                QuestionTextSection(
                    questionIndex = currentQuestionIndex + 1,
                    englishText = currentQuestion.questionText,
                    hindiText = currentQuestion.question_hi ?: getHindiTranslation(currentQuestion.questionText),
                    isAttempted = isAttempted
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Options
                val hiddenOptions = session!!.hiddenOptions[currentQuestion.id] ?: emptyList()
                val correctIndex = try { (currentQuestion.correctOption.uppercase().firstOrNull() ?: 'A') - 'A' } catch(e:Exception){0}
                currentQuestion.options.forEachIndexed { index, option ->
                    if (hiddenOptions.contains(index.toString())) {
                        return@forEachIndexed // completely hide 50/50 removed options
                    }
                    
                    val isSelected = selectedOptionIndex == index
                    val isCorrect = index == correctIndex
                    
                    OptionCard(
                        englishOption = option,
                        hindiOption = currentQuestion.options_hi?.getOrNull(index) ?: getHindiTranslation(option),
                        isAttempted = isAttempted,
                        isSelected = isSelected,
                        isCorrect = isCorrect,
                        onClick = {
                            if (!isAttempted) {
                                viewModel.selectAnswer(currentQuestion.id, index)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Explanation Section
                AnimatedVisibility(visible = isAttempted) {
                    ExplanationSection(
                        correctOptionLetter = ('A' + correctIndex).toString(),
                        correctOptionText = currentQuestion.options.getOrNull(correctIndex) ?: "",
                        explanation = currentQuestion.explanation?.toString() ?: ""
                    )
                }
                Spacer(modifier = Modifier.height(32.dp)) // padding at bottom
            }
        }
    }
}

@Composable
fun ActionRow(timeFlow: kotlinx.coroutines.flow.StateFlow<Int>, onBookmark: () -> Unit) {
    val timeRemaining by timeFlow.collectAsState()
    val minutes = timeRemaining / 60
    val seconds = timeRemaining % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timer Chip
        OutlinedCard(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, OptionBorderUnselected)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Timer, contentDescription = "Timer", modifier = Modifier.size(16.dp), tint = GreyText)
                Spacer(modifier = Modifier.width(4.dp))
                Text(timeString, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
            }
        }
        
        // Zoom Chip
        OutlinedCard(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, OptionBorderUnselected)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp), tint = GreyText)
                Spacer(modifier = Modifier.width(4.dp))
                Box(modifier = Modifier.width(1.dp).height(16.dp).background(OptionBorderUnselected))
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(16.dp), tint = GreyText)
            }
        }
        
        // Fullscreen Mode
        OutlinedCard(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, OptionBorderUnselected)
        ) {
            Box(modifier = Modifier.padding(6.dp)) {
                Icon(Icons.Filled.OpenInFull, contentDescription = "Fullscreen", modifier = Modifier.size(16.dp), tint = GreyText)
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Star Bookmark
        var localBookmarkState by remember { mutableStateOf(false) }
        OutlinedCard(
            onClick = {
                localBookmarkState = !localBookmarkState
                onBookmark()
            },
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (localBookmarkState) PurpleCustom else OptionBorderUnselected),
            colors = CardDefaults.outlinedCardColors(containerColor = if (localBookmarkState) PurpleCustom.copy(alpha=0.1f) else Color.White)
        ) {
            Box(modifier = Modifier.padding(6.dp)) {
                Icon(if (localBookmarkState) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = "Bookmark", modifier = Modifier.size(16.dp), tint = if (localBookmarkState) PurpleCustom else GreyText)
            }
        }
    }
}

@Composable
fun ProgressSection(current: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val animatedProgress by animateFloatAsState(targetValue = current.toFloat() / total.toFloat(), label = "progress")
        Text("$current / $total", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Spacer(modifier = Modifier.width(16.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = PurpleCustom,
            trackColor = PurpleCustom.copy(alpha = 0.2f),
            drawStopIndicator = {}
        )
    }
}

@Composable
fun TagsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TagItem(Icons.Filled.Tag, "HIS63")
        TagItem(Icons.Filled.Description, "RRB NTPC UG 2024", tint = PurpleCustom.copy(alpha = 0.6f))
        TagItem(Icons.Filled.DateRange, "Aug-Sept 2025")
    }
}

@Composable
fun TagItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color = GreyText) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = GreyText)
    }
}

@Composable
fun HtmlText(text: String, modifier: Modifier = Modifier, color: Color = Color.Black, fontSize: Float = 16f) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(color.toArgb())
                textSize = fontSize
                setLineSpacing(0f, 1.5f)
            }
        },
        update = { textView ->
            textView.text = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    )
}

@Composable
fun QuestionTextSection(questionIndex: Int, englishText: String, hindiText: String, isAttempted: Boolean) {
    Column {
        HtmlText(
            text = "<b>Q.$questionIndex)</b> $englishText",
            modifier = Modifier.fillMaxWidth(),
            color = Color.Black,
            fontSize = 16f
        )
        
        if (isAttempted) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                IconBox(Icons.Filled.ContentCopy)
                IconBox(Icons.Filled.Download)
                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleCustom),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Filled.SmartToy, contentDescription = "Tutor", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ask AI Tutor", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFD6DBF0)) // Light blue/grey accent
            )
            Spacer(modifier = Modifier.width(12.dp))
            HtmlText(
                text = hindiText,
                modifier = Modifier.weight(1f),
                color = Color.DarkGray,
                fontSize = 16f
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDEEFC)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read Aloud", tint = PurpleCustom)
            }
        }
    }
}

@Composable
fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, OptionBorderUnselected),
        modifier = Modifier.size(36.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = GreyText)
        }
    }
}

@Composable
fun OptionCard(
    englishOption: String,
    hindiOption: String,
    isAttempted: Boolean,
    isSelected: Boolean,
    isCorrect: Boolean,
    onClick: () -> Unit
) {
    val bgColor: Color
    val borderColor: Color
    
    if (isAttempted) {
        if (isCorrect) {
            bgColor = Color(0xFFF2FCF5)
            borderColor = CorrectBorder
        } else if (isSelected && !isCorrect) {
            bgColor = IncorrectBg
            borderColor = IncorrectBorder
        } else {
            bgColor = Color.White
            borderColor = OptionBorderUnselected.copy(alpha = 0.5f) // Faded unselected
        }
    } else {
        bgColor = Color.White
        borderColor = OptionBorderUnselected
    }

    val alpha = if (isAttempted && !isCorrect && !isSelected) 0.5f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAttempted, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isAttempted && isCorrect) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = englishOption,
                    color = Color.DarkGray.copy(alpha = alpha),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hindiOption,
                    color = GreyText.copy(alpha = alpha),
                    fontSize = 14.sp
                )
            }
            if (isAttempted && isCorrect) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Correct", tint = CorrectBorder)
            } else if (isAttempted && isSelected && !isCorrect) {
                Icon(Icons.Filled.Cancel, contentDescription = "Incorrect", tint = IncorrectBorder)
            }
        }
    }
}

@Composable
fun ExplanationSection(correctOptionLetter: String, correctOptionText: String, explanation: String) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        // Answer Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, PurpleCustom.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
        ) {
            Box(modifier = Modifier.width(4.dp).heightIn(min = 60.dp).background(PurpleCustom))
            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                Text("ANSWER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GreyText, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Correct Answer: $correctOptionLetter) $correctOptionText",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurpleCustom
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Why this is correct block
        InfoBlock(
            title = "Why this is correct",
            icon = Icons.Filled.CheckCircle,
            content = explanation, // In a real app we would have specific fields for this
            bgColor = CorrectBg,
            iconColor = CorrectBorder
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Key Takeaway
        InfoBlock(
            title = "Key Takeaway",
            icon = Icons.Filled.Description,
            content = "This is a placeholder for key takeaway derived from the topic. The original Lion Capital has four lions standing back to back.",
            bgColor = InfoBg,
            iconColor = PurpleCustom,
            iconBg = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Did you know
        InfoBlock(
            title = "Did you know?",
            icon = Icons.Filled.Lightbulb,
            content = "This is a placeholder for interesting facts related to the question topic.",
            bgColor = FunFactBg,
            iconColor = Color(0xFFE4A11B)
        )
    }
}

@Composable
fun InfoBlock(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: String,
    bgColor: Color,
    iconColor: Color,
    iconBg: Color = Color.Transparent
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))
                Text(content, fontSize = 14.sp, color = Color.DarkGray, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
fun BottomActionBar(
    onSettings: () -> Unit,
    onMenu: () -> Unit,
    onPrevious: () -> Unit,
    onFiftyFifty: () -> Unit,
    onNext: () -> Unit,
    isPreviousEnabled: Boolean,
    isNextEnabled: Boolean,
    isLastQuestion: Boolean,
    isMarked: Boolean,
    onMarkForReview: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightGreyBg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Flag (Mark for review)
            OutlinedCard(
                onClick = onMarkForReview,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isMarked) PurpleCustom else OptionBorderUnselected),
                colors = CardDefaults.outlinedCardColors(containerColor = if (isMarked) PurpleCustom.copy(alpha=0.1f) else Color.White)
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Filled.Flag, contentDescription = "Mark for Review", tint = if (isMarked) PurpleCustom else GreyText)
                }
            }
            
            // Menu (Nav Grid)
            OutlinedCard(
                onClick = onMenu,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OptionBorderUnselected),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = GreyText)
                }
            }
            
            // Previous
            OutlinedCard(
                onClick = { if (isPreviousEnabled) onPrevious() },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isPreviousEnabled) GreyText else OptionBorderUnselected),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                enabled = isPreviousEnabled
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous",
                        tint = if (isPreviousEnabled) Color.DarkGray else OptionBorderUnselected
                    )
                }
            }
            
            // 50:50
            Button(
                onClick = onFiftyFifty,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF7BD22)),
                shape = RoundedCornerShape(20.dp), // Pill shape as per screenshot
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("50:50", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Next
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isNextEnabled) PurpleCustom else LightGreyBg.copy(alpha = 0.5f),
                    contentColor = if (isNextEnabled) Color.White else GreyText
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp),
                //enabled = isNextEnabled // Based on prompt, button changes color but keeps active in learning mode or forced next? Let's keep it enabled but styled differently.
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isLastQuestion) "Submit" else "Next", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// Helper to provide a mock Hindi translation since our models only have english strings.
fun getHindiTranslation(englishText: String): String {
    // A simple mapping for the screenshot example to make it look realistic.
    if (englishText.contains("ashramas", ignoreCase = true)) {
         return "प्रश्न 63) वैदिक काल में सामाजिक जीवन कितने आश्रमों में विभाजित था?"
    } else if (englishText.contains("Lion Capital", ignoreCase = true)) {
         return "प्रश्न 6) सारनाथ में स्थित सिंह-चतुर्मुख स्तंभशीर्ष, जिसे भारत गणराज्य के राष्ट्रीय प्रतीक के रूप में अपनाया गया है, निम्नलिखित में से किस मौर्य राजा द्वारा बनवाया गया था?"
    }
    
    // Default mock translations for options
    if (englishText.equals("Six", ignoreCase = true)) return "छह"
    if (englishText.equals("Four", ignoreCase = true)) return "चार"
    if (englishText.equals("Ten", ignoreCase = true)) return "दस"
    if (englishText.equals("Three", ignoreCase = true)) return "तीन"
    
    if (englishText.contains("Ashoka", ignoreCase = true)) return "अशोक"
    if (englishText.contains("Bindusara", ignoreCase = true)) return "बिंदुसार"
    if (englishText.contains("Samprati", ignoreCase = true)) return "संप्रति मौर्य"
    if (englishText.contains("Devavarman", ignoreCase = true)) return "देववर्मन मौर्य"
    
    return englishText + " (Mock Hindi translation)"
}

