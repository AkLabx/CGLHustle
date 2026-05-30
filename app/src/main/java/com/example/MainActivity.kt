package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.ui.screens.*
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.AppTheme
import com.example.viewmodel.QuizViewModel

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.QuizConfigViewModel
import com.example.ui.viewmodel.QuizConfigState
import com.example.domain.engine.QuizFilterEngine
import com.example.data.repository.QuizRepository
import com.example.domain.models.QuestionMetadata
import com.example.di.SupabaseModule
import com.example.data.repository.QuizRepositoryImpl
import com.example.data.repository.ActiveSessionRepository

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "cgl-hustle-database"
        ).fallbackToDestructiveMigration().build()
        
        val quizFilterEngine = QuizFilterEngine()
        val quizRepository = QuizRepositoryImpl(SupabaseModule.questionClient)
        val activeSessionRepository = ActiveSessionRepository(
            primaryClient = SupabaseModule.primaryClient,
            questionClient = SupabaseModule.questionClient,
            activeSessionDao = database.activeSessionDao(),
            bridgeDao = database.bridgeDao(),
            quizHistoryDao = database.quizHistoryDao(),
            questionDao = database.questionDao()
        )

        enableEdgeToEdge()
        setContent {
            AppTheme {
                val factory = remember {
                    object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return QuizViewModel(database.testResultDao(), activeSessionRepository) as T
                        }
                    }
                }
                val configFactory = remember {
                    object : androidx.lifecycle.AbstractSavedStateViewModelFactory(this@MainActivity, null) {
                        override fun <T : ViewModel> create(
                            key: String,
                            modelClass: Class<T>,
                            handle: androidx.lifecycle.SavedStateHandle
                        ): T {
                            val createQuizUseCase = com.example.domain.usecase.CreateQuizUseCase(quizRepository, activeSessionRepository)
                            @Suppress("UNCHECKED_CAST")
                            return QuizConfigViewModel(quizRepository, quizFilterEngine, createQuizUseCase, handle) as T
                        }
                    }
                }
                
                val viewModel = ViewModelProvider(this, factory)[QuizViewModel::class.java]
                val configViewModel = ViewModelProvider(this, configFactory)[QuizConfigViewModel::class.java]
                
                val navController = rememberNavController()
                
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController, 
                        startDestination = LoginRoute,
                        enterTransition = {
                            androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) + 
                            androidx.compose.animation.scaleIn(initialScale = 0.95f, animationSpec = androidx.compose.animation.core.tween(400))
                        },
                        exitTransition = {
                            androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(400)) + 
                            androidx.compose.animation.scaleOut(targetScale = 1.05f, animationSpec = androidx.compose.animation.core.tween(400))
                        },
                        popEnterTransition = {
                            androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) + 
                            androidx.compose.animation.scaleIn(initialScale = 1.05f, animationSpec = androidx.compose.animation.core.tween(400))
                        },
                        popExitTransition = {
                            androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(400)) + 
                            androidx.compose.animation.scaleOut(targetScale = 0.95f, animationSpec = androidx.compose.animation.core.tween(400))
                        }
                    ) {
                        composable<LoginRoute> {
                            LoginScreen(
                                onNavigateToDashboard = {
                                    navController.navigate(DashboardRoute) {
                                        popUpTo(LoginRoute) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable<DashboardRoute> {
                            DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToExams = { navController.navigate(ExamSelectionRoute) },
                                onNavigateToCustom = { navController.navigate(McqsQuizHomeRoute) }
                            )
                        }
                        composable<ExamSelectionRoute> {
                            ExamSelectionScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onQuizSelected = { quizId -> 
                                    navController.navigate(ActiveQuizRoute(quizId))
                                }
                            )
                        }
                        composable<McqsQuizHomeRoute> {
                            McqsQuizHomeScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToCreateQuiz = { navController.navigate(CustomQuizRoute(initialMode = "learning")) },
                                onNavigateToSavedQuizzes = { navController.navigate(QuizLibraryRoute(tab = "saved")) },
                                onNavigateToAttemptedQuizzes = { /* TODO implement Attempted Quizzes */ },
                                onNavigateToGodMode = { navController.navigate(CustomQuizRoute(initialMode = "god")) }
                            )
                        }
                        composable<CustomQuizRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<CustomQuizRoute>()
                            QuizConfigScreen(
                                viewModel = configViewModel,
                                initialMode = route.initialMode,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToLibrary = {
                                    navController.navigate(QuizLibraryRoute(tab = "created")) {
                                        popUpTo(CustomQuizRoute::class) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable<QuizLibraryRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<QuizLibraryRoute>()
                            val libraryFactory = remember {
                                object : ViewModelProvider.Factory {
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return com.example.ui.viewmodel.QuizLibraryViewModel(activeSessionRepository) as T
                                    }
                                }
                            }
                            val libraryViewModel = ViewModelProvider(this@MainActivity, libraryFactory)[com.example.ui.viewmodel.QuizLibraryViewModel::class.java]
                            
                            com.example.ui.screens.QuizLibraryScreen(
                                viewModel = libraryViewModel,
                                initialTab = route.tab,
                                onNavigateBack = { navController.popBackStack() },
                                onQuizSelected = { quizId ->
                                    navController.navigate(ActiveQuizRoute(quizId))
                                }
                            )
                        }
                        
                        composable<ActiveQuizRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<ActiveQuizRoute>()
                            val handle = androidx.lifecycle.SavedStateHandle(mapOf("quizId" to route.quizId))
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val activeFactory = remember(route.quizId) {
                                object : ViewModelProvider.Factory {
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return com.example.ui.viewmodel.ActiveQuizViewModel(
                                            handle,
                                            activeSessionRepository,
                                            this@MainActivity.database.testResultDao()
                                        ) as T
                                    }
                                }
                            }
                            val activeQuizViewModel = ViewModelProvider(this@MainActivity, activeFactory)[com.example.ui.viewmodel.ActiveQuizViewModel::class.java]

                            ActiveQuizScreen(
                                viewModel = activeQuizViewModel,
                                onBack = { 
                                    activeQuizViewModel.clearSession()
                                    navController.popBackStack() 
                                },
                                onSubmitSuccess = {
                                    navController.navigate(DynamicQuizResultRoute(route.quizId)) { // Create this route
                                        popUpTo(DashboardRoute)
                                    }
                                }
                            )
                        }
                        composable<DynamicQuizResultRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<DynamicQuizResultRoute>()
                            DynamicQuizResultScreen(
                                quizId = route.quizId,
                                repository = activeSessionRepository,
                                onBackToDashboard = {
                                    navController.navigate(DashboardRoute) {
                                        popUpTo(DashboardRoute) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable<QuizResultRoute> {
                            QuizResultScreen(
                                viewModel = viewModel,
                                onBackToDashboard = {
                                    viewModel.clearSession()
                                    navController.navigate(DashboardRoute) {
                                        popUpTo(DashboardRoute) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
