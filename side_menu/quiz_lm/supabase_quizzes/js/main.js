import { state } from './state.js';
import { dom } from './dom.js';
import { initializeFilters } from './filter.js';
// Note: startQuiz is imported but used within filter.js, which is correct.
import { startQuiz } from './quiz.js';

document.addEventListener('DOMContentLoaded', async () => {
    initializeTheme();
    initializeEventListeners();
    // Initialize the Lottie animation for the loader
    lottie.loadAnimation({
        container: dom.lottieLoader,
        renderer: 'svg',
        loop: true,
        autoplay: true,
        path: 'https://assets10.lottiefiles.com/packages/lf20_os6xj3cv.json' // A simple loading animation
    });
    await loadQuestions();
});

function initializeTheme() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    state.theme = savedTheme;
    document.body.setAttribute('data-theme', state.theme);
    updateThemeIcon();
}

function updateThemeIcon() {
    const icon = dom.themeSwitcher.querySelector('i');
    if (state.theme === 'dark') {
        icon.classList.remove('fa-moon');
        icon.classList.add('fa-sun');
    } else {
        icon.classList.remove('fa-sun');
        icon.classList.add('fa-moon');
    }
}

function initializeEventListeners() {
    dom.themeSwitcher.addEventListener('click', () => {
        state.theme = state.theme === 'light' ? 'dark' : 'light';
        localStorage.setItem('theme', state.theme);
        document.body.setAttribute('data-theme', state.theme);
        updateThemeIcon();
    });

    dom.fullscreenBtn.addEventListener('click', () => {
        if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen().catch(err => {
                console.error(`Error attempting to enable full-screen mode: ${err.message} (${err.name})`);
            });
        } else {
            if (document.exitFullscreen) {
                document.exitFullscreen();
            }
        }
    });
}

/**
 * CORRECTED VERSION:
 * This function now uses a standard and robust fetch method. It no longer relies on
 * the 'Content-Length' header, which was causing the failure on your web host.
 */
async function loadQuestions() {
    try {
        // Use the standard fetch and .json() method, which is universally compatible.
        const response = await fetch('questions.json');
        
        // Check if the file was found (status 200). If not (e.g., 404), throw an error.
        if (!response.ok) {
            throw new Error(`HTTP error! Status: ${response.status} - Could not find questions.json`);
        }
        
        const questions = await response.json();

        // Update UI to show processing is happening.
        dom.loadingPercentage.textContent = 'Processing Data...';

        // Initialize LokiJS database with the loaded questions.
        state.db = new loki('quiz.db');
        state.questionsCollection = state.db.addCollection('questions');
        state.questionsCollection.insert(questions);

        // A short delay for a smoother visual transition.
        setTimeout(() => {
            dom.loadingOverlay.classList.add('hidden');
            initializeFilters();
        }, 500);

    } catch (error) {
        // This block will now provide a much more helpful error message.
        console.error("Failed to load or parse questions.json:", error);
        
        // Display a detailed error message to the user.
        Swal.fire({
            icon: 'error',
            title: 'Loading Failed',
            html: `Could not load the question bank. Please ensure <strong>questions.json</strong> is in the same directory as <strong>index.html</strong>.<br><br><i>Error: ${error.message}</i>`,
        });
        
        // Update the loading screen text as well.
        dom.loadingPercentage.textContent = 'Error loading data. Please refresh.';
    }
}