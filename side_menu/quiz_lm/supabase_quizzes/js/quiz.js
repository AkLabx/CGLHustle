import { config, state, saveState } from './state.js';
import { dom } from './dom.js';
import { playSound, triggerHapticFeedback, shuffleArray, buildExplanationHtml, Toast, cleanQuestionText } from './utils.js'; // Assuming cleanQuestionText is moved to utils.js
import { typewriterAnimate } from './animations.js';
import { applyTextZoom } from './settings.js';

let appCallbacks = {};

// Helper to parse new coded IDs like "HIS1", "POL72"
function parseCodedId(idString) {
    if (typeof idString !== 'string') {
        // Fallback for old numeric IDs during transition
        return { prefix: '', num: parseInt(idString, 10) || 0 };
    }
    const match = idString.match(/^([A-Z]+)(\d+)$/);
    if (match) {
        return { prefix: match[1], num: parseInt(match[2], 10) };
    }
    // Fallback for non-matching strings or old IDs
    return { prefix: idString, num: 0 };
}

function reorderQuizQuestions() {
    if (!state.isQuizActive || !state.currentQuizData) return;

    const cd = state.currentQuizData;
    const currentIndex = cd.currentQuestionIndex;
    const currentShuffledList = cd.shuffledQuestions;
    
    // Separate past/current questions from future ones
    const pastAndCurrentQuestions = currentShuffledList.slice(0, currentIndex + 1);
    let futureQuestions = currentShuffledList.slice(currentIndex + 1);

    if (state.isShuffleActive) {
        shuffleArray(futureQuestions);
    } else {
        // Sort remaining questions back to their original order (by ID)
        futureQuestions.sort((a, b) => {
            const idA = parseCodedId(a.id);
            const idB = parseCodedId(b.id);
            if (idA.prefix < idB.prefix) return -1;
            if (idA.prefix > idB.prefix) return 1;
            return idA.num - idB.num;
        });
    }
    
    cd.shuffledQuestions = [...pastAndCurrentQuestions, ...futureQuestions];
    
    // Update navigation to reflect new order
    populateQuizInternalNavigation();
}

export function initQuizModule(callbacks) {
    appCallbacks = callbacks;
    state.callbacks.nextQuestionHandler = nextQuestionHandler;
    state.callbacks.previousQuestionHandler = previousQuestionHandler;
    state.callbacks.quizKeyPressHandler = handleKeyPress;
    state.callbacks.toggleQuizInternalNavigation = toggleQuizInternalNavigation;
    state.callbacks.reorderQuizQuestions = reorderQuizQuestions;
    
    bindQuizEventListeners();
    initializeGemini();
}

export function loadQuiz(questions) {
    divideQuestionsIntoGroups(questions);

    // Pre-populate shuffled/sorted order for ALL groups at the start of the quiz.
    state.questionGroups.forEach(group => {
        if (state.isShuffleActive) {
            group.shuffledQuestions = [...group.questions];
            shuffleArray(group.shuffledQuestions);
        } else {
            // Default sort by coded ID (prefix then number)
            group.shuffledQuestions = [...group.questions].sort((a, b) => {
                const idA = parseCodedId(a.id);
                const idB = parseCodedId(b.id);
                if (idA.prefix < idB.prefix) return -1;
                if (idA.prefix > idB.prefix) return 1;
                return idA.num - idB.num;
            });
        }
    });
    
    state.currentGroupIndex = 0;
    loadQuestionGroup(state.currentGroupIndex);
    startQuizLogicForGroup();
    applyHeaderCollapsedState();
}

function bindQuizEventListeners() {
    dom.navMenuIcon.addEventListener('click', () => toggleQuizInternalNavigation());
    dom.navOverlay.addEventListener('click', () => toggleQuizInternalNavigation());
    dom.prevQuestionBtn.onclick = () => previousQuestionHandler();
    dom.nextQuestionBtn.onclick = () => nextQuestionHandler();
    dom.markReviewBtn.onclick = () => toggleMarkForReview();
    dom.aiExplainerBtn.addEventListener('click', () => getGeminiExplanation());
    dom.lifelineBtn.onclick = () => useLifeline();
    dom.nextBtn.onclick = () => nextQuestionHandler();
    dom.toggleHeaderBtn.addEventListener('click', toggleHeader);
    dom.bookmarkBtn.addEventListener('click', toggleBookmark);
    dom.nextGroupBtn.onclick = loadNextGroup;


    const submitQuizBtn = document.getElementById('submit-quiz-btn');
    if (submitQuizBtn) submitQuizBtn.onclick = () => submitAndReviewAll();
}

function initializeGemini() {
    console.log("AI Explainer feature is for demonstration. A backend proxy is needed for full functionality.");
    if (dom.aiExplainerBtn) {
        dom.aiExplainerBtn.title = "Get an AI-powered explanation (requires backend setup).";
        dom.aiExplainerBtn.style.display = ''; 
    }
    state.ai = null;
}

function divideQuestionsIntoGroups(questionsList) {
    state.questionGroups = [];
    const totalQuestions = questionsList.length;
    for (let i = 0; i < totalQuestions; i += config.questionsPerGroup) {
        const groupQuestions = questionsList.slice(i, i + config.questionsPerGroup);
        const startQ = i + 1;
        const endQ = Math.min(i + config.questionsPerGroup, totalQuestions);
        state.questionGroups.push({
            groupName: `Questions ${startQ}-${endQ}`,
            questions: groupQuestions,
            shuffledQuestions: [],
            attempts: [],
            markedForReview: [],
            isSubmenuOpen: true,
        });
    }
}

function loadQuestionGroup(newGroupIndex) {
    if (newGroupIndex < 0 || newGroupIndex >= state.questionGroups.length) return;

    state.currentGroupIndex = newGroupIndex;
    state.currentQuizData = state.questionGroups[state.currentGroupIndex];
    
    if (!state.currentQuizData) {
        console.error(`Attempted to load a null or undefined question group at index ${newGroupIndex}.`);
        appCallbacks.restartFullQuiz();
        return;
    }
    
    if (state.currentQuizData.attempts.length > 0) {
        const answeredIds = new Set(state.currentQuizData.attempts.map(a => a.questionId));
        let firstUnansweredIndex = state.currentQuizData.shuffledQuestions.findIndex(q => !answeredIds.has(q.id));
        if (firstUnansweredIndex === -1) { 
            endQuiz();
            return;
        }
        state.currentQuizData.currentQuestionIndex = firstUnansweredIndex;
    } else {
        state.currentQuizData.currentQuestionIndex = 0;
    }

    dom.quizSection.style.display = 'block';
    dom.quizSection.classList.add('section-fade-in');

    displayQuestion();
    updateStatusTracker();
    populateQuizInternalNavigation();
    saveState();
}

function startQuizLogicForGroup() {
    if (!state.currentQuizData || !state.currentQuizData.questions.length === 0) return;
    applyTextZoom();
    updateStatusTracker();
    updateQuizProgressBar();
}

function checkAnswer(selectedEnglishOption, button) {
    stopTimer();
    dom.timerBar.classList.add('paused');
    let q = state.currentQuizData.shuffledQuestions[state.currentQuizData.currentQuestionIndex];
    let isCorrect = selectedEnglishOption.trim() === q.correct?.trim();
    let attemptStatus = "";
    const timeTaken = config.timePerQuestion - state.timeLeftForQuestion;
    dom.optionsEl.querySelectorAll("button").forEach(btn => btn.disabled = true);
    dom.lifelineBtn.disabled = true;

    const feedbackIcon = document.createElement('span');
    feedbackIcon.classList.add('icon-feedback');

    if (isCorrect) {
        button.classList.add("correct");
        feedbackIcon.innerHTML = "✔️";
        button.appendChild(feedbackIcon.cloneNode(true));
        dom.timerBar.classList.add('correct-pause');
        dom.timerBar.style.backgroundColor = 'var(--correct-color)';
        playSound('correct-sound');
        triggerHapticFeedback('correct');
        attemptStatus = "Correct";
    } else {
        button.classList.add("wrong");
        feedbackIcon.innerHTML = "❌";
        button.appendChild(feedbackIcon.cloneNode(true));
        dom.timerBar.style.backgroundColor = 'var(--wrong-color)';
        dom.optionsEl.querySelectorAll("button").forEach(btn => {
            if (btn.dataset.value.trim() === q.correct?.trim()) {
                btn.classList.add("reveal-correct");
                const correctIconReveal = document.createElement('span');
                correctIconReveal.classList.add('icon-feedback');
                correctIconReveal.innerHTML = "✔️";
                if (!btn.querySelector('.icon-feedback')) btn.appendChild(correctIconReveal.cloneNode(true));
            }
        });
        playSound('wrong-sound');
        triggerHapticFeedback('wrong');
        attemptStatus = "Wrong";
    }

    const displayedOptionsData = (q.displayOrderIndices || q.options?.map((_, i) => i) || []).map(originalOptIndex => ({
        eng: q.options?.[originalOptIndex] || "",
        hin: (q.options_hi && q.options_hi[originalOptIndex]) ? q.options_hi[originalOptIndex] : null
    }));

    state.currentQuizData.attempts.push({
        questionId: q.id,
         v1_id: q.v1_id, 
        question: q.question, question_hi: q.question_hi || null,
        optionsDisplayedBilingual: displayedOptionsData,
        status: attemptStatus,
        selected: selectedEnglishOption,
        correct: q.correct,
        explanation: q.explanation,
        timeTaken: timeTaken
    });

    showExplanation();
    dom.nextBtn.style.display = "inline-block";
    dom.aiExplainerBtn.disabled = false;
    updateStatusTracker();
    applyTextZoom();
    populateQuizInternalNavigation();
    saveState();
}

function endQuiz() {
    stopTimer();
    if (!state.currentQuizData) {
        console.warn("endQuiz called without active quiz data. Restarting.");
        appCallbacks.restartFullQuiz();
        return;
    }
    appCallbacks.endQuiz();
}

function nextQuestionHandler() {
    if (!state.currentQuizData || state.isTransitioningQuestion) return;
    stopTimer();
    const currentIdx = state.currentQuizData.currentQuestionIndex;
    const attemptsMap = new Map(state.currentQuizData.attempts.map(a => [a.questionId, a]));
    
    let nextUnansweredIndex = -1;
    for (let i = currentIdx + 1; i < state.currentQuizData.shuffledQuestions.length; i++) {
        if (!attemptsMap.has(state.currentQuizData.shuffledQuestions[i].id)) {
            nextUnansweredIndex = i;
            break;
        }
    }

    if (nextUnansweredIndex !== -1) {
        state.currentQuizData.currentQuestionIndex = nextUnansweredIndex;
        displayQuestion();
    } else {
        if (state.currentQuizData.attempts.length >= state.currentQuizData.shuffledQuestions.length) {
            endQuiz();
        } else {
            const firstUnanswered = state.currentQuizData.shuffledQuestions.findIndex(q => !attemptsMap.has(q.id));
            if (firstUnanswered !== -1) {
                state.currentQuizData.currentQuestionIndex = firstUnanswered;
                displayQuestion();
            } else {
                endQuiz();
            }
        }
    }
    saveState();
}

function previousQuestionHandler() {
    if (!state.currentQuizData || state.currentQuizData.currentQuestionIndex <= 0 || state.isTransitioningQuestion) return;
    state.currentQuizData.currentQuestionIndex--;
    displayQuestion();
    saveState();
}

function submitAndReviewAll() {
    Swal.fire({
        target: dom.quizMainContainer,
        position: 'top',
        title: 'Submit your current quiz?',
        text: "Your score will be calculated based on your answered questions. Unanswered questions will be marked as skipped.",
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: 'var(--correct-color)',
        cancelButtonColor: 'var(--wrong-color)',
        confirmButtonText: 'Yes, submit!'
    }).then((result) => {
        if (result.isConfirmed) {
            stopTimer();
            if (dom.navigationPanel.classList.contains('open')) toggleQuizInternalNavigation();
            const cd = state.currentQuizData;
            if (!cd) return;
            const attemptedIds = new Set(cd.attempts.map(a => a.questionId));
            cd.shuffledQuestions.forEach(q => {
                if (!attemptedIds.has(q.id)) {
                    cd.attempts.push({
                        questionId: q.id,  v1_id: q.v1_id,   question: q.question, question_hi: q.question_hi || null,
                        optionsDisplayedBilingual: q.options?.map((eng, i) => ({ eng, hin: q.options_hi?.[i] || "" })) || [],
                        status: 'Skipped', selected: 'Skipped', correct: q.correct, explanation: q.explanation, timeTaken: 0 
                    });
                }
            });
            endQuiz();
        }
    });
}

function displayQuestion() {
    if (!state.currentQuizData || state.currentQuizData.currentQuestionIndex >= state.currentQuizData.shuffledQuestions.length) {
        endQuiz();
        return;
    }
    if (state.isTransitioningQuestion) return;
    state.isTransitioningQuestion = true;

    const updateContent = () => {
        const q = state.currentQuizData.shuffledQuestions[state.currentQuizData.currentQuestionIndex];
        const attempt = state.currentQuizData.attempts.find(a => a.questionId === q.id);

        renderCurrentQuestion(q, attempt);

        dom.quizContainer.classList.remove('is-transitioning-out');
        dom.quizContainer.classList.add('is-transitioning-in');
        dom.quizContainer.addEventListener('animationend', () => {
            dom.quizContainer.classList.remove('is-transitioning-in');
            state.isTransitioningQuestion = false;
        }, { once: true });
    };

    dom.quizContainer.classList.add('is-transitioning-out');
    dom.quizContainer.addEventListener('animationend', updateContent, { once: true });
}

/**
 * The single source of truth for rendering a question's state to the DOM.
 * It handles both fresh questions and restoring previously attempted ones.
 * @param {object} q - The question object to display.
 * @param {object|null} attempt - The attempt object if the question has been answered, otherwise null.
 */
function renderCurrentQuestion(q, attempt = null) {
    // --- Phase 1: RENDER COMMON CONTENT (Text, Options, Numbers) ---
    updateQuizProgressBar();

    const cleanQText = cleanQuestionText(q?.question);
    const cleanQHiText = cleanQuestionText(q?.question_hi);
    dom.questionTextEl.innerHTML = `Q.${state.currentQuizData.currentQuestionIndex + 1}) ${cleanQText}${(cleanQHiText && cleanQHiText.trim() !== '') ? '<hr class="lang-separator"><span class="hindi-text">' + cleanQHiText + '</span>' : ''}`;
    
    dom.sequentialQuestionNumberEl.innerText = `Q.${state.currentQuizData.currentQuestionIndex + 1} / ${state.currentQuizData.shuffledQuestions.length}`;
    dom.actualQuestionNumberEl.innerText = `ID: ${q.v1_id}`;
    
    dom.examNameTag.innerHTML = q?.sourceInfo?.examName ? `<i class="fas fa-file-alt"></i> ${q.sourceInfo.examName}` : '';
    dom.examNameTag.style.display = q?.sourceInfo?.examName ? 'inline-flex' : 'none';
    dom.examDateShiftTag.innerHTML = q?.sourceInfo?.examDateShift ? `<i class="fas fa-calendar-day"></i> ${q.sourceInfo.examDateShift}` : '';
    dom.examDateShiftTag.style.display = q?.sourceInfo?.examDateShift ? 'inline-flex' : 'none';
    
    dom.bookmarkBtn.dataset.questionId = q.id;
    dom.optionsEl.innerHTML = "";

    (q?.options || []).forEach((engOpt, originalIndex) => {
        const hinOpt = q.options_hi?.[originalIndex] || "";
        const btn = document.createElement("button");
        btn.innerHTML = `${engOpt}${hinOpt ? '<br><span class="hindi-text">' + hinOpt + '</span>' : ''}`;
        btn.dataset.value = engOpt;
        btn.dataset.index = originalIndex + 1; // Use original index for consistency if needed, or map to display order
        dom.optionsEl.appendChild(btn);
    });

    // --- Phase 2: APPLY STATE (Attempted or Fresh) ---
    if (attempt) {
        // --- RESTORE ATTEMPTED STATE ---
        stopTimer();
        dom.timerBar.classList.add('paused');
        dom.timerBar.style.width = `${100 - (attempt.timeTaken / config.timePerQuestion) * 100}%`;
        dom.timerDisplay.innerText = Math.max(0, config.timePerQuestion - Math.round(attempt.timeTaken));

        if (attempt.status === 'Correct') {
            dom.timerBar.classList.add('correct-pause');
            dom.timerBar.style.backgroundColor = 'var(--correct-color)';
        } else {
            dom.timerBar.style.backgroundColor = 'var(--wrong-color)';
            if (attempt.status === 'Timeout') dom.timerBar.style.width = '0%';
        }
        
        dom.optionsEl.querySelectorAll("button").forEach(btn => {
            btn.disabled = true;
            const btnValue = btn.dataset.value.trim();
            const correctValue = attempt.correct.trim();
            const selectedValue = attempt.selected?.trim();

            const feedbackIconCorrect = `<span class="icon-feedback">✔️</span>`;
            const feedbackIconWrong = `<span class="icon-feedback">❌</span>`;

            if (btnValue === correctValue) {
                btn.classList.add(attempt.status === "Wrong" || attempt.status === "Timeout" || attempt.status === "Skipped" ? "reveal-correct" : "correct");
                btn.insertAdjacentHTML('beforeend', feedbackIconCorrect);
            }
            if (btnValue === selectedValue && attempt.status === "Wrong") {
                btn.classList.add("wrong");
                if(btnValue !== correctValue) btn.insertAdjacentHTML('beforeend', feedbackIconWrong);
            }
        });

        showExplanation();
        dom.lifelineBtn.disabled = true;
        dom.aiExplainerBtn.disabled = false;

    } else {
        // --- PREPARE FRESH QUESTION ---
        resetQuestionState();
        dom.optionsEl.querySelectorAll("button").forEach(btn => {
            btn.onclick = () => checkAnswer(btn.dataset.value, btn);
        });
        startTimer();
    }
    
    // --- Phase 3: UPDATE SHARED UI & STATE ---
    dom.quizNavBar.style.display = 'flex';
    dom.prevQuestionBtn.disabled = (state.currentQuizData.currentQuestionIndex === 0);
    updateBookmarkButton();
    updateMarkForReviewButton();
    updateStatusTracker();
    populateQuizInternalNavigation();
    requestAnimationFrame(() => applyTextZoom());
}

function renderQuestionContent() {
    // This function is now a legacy stub and can be removed.
    // The logic has been moved to renderCurrentQuestion.
}

function resetQuestionState() {
    stopTimer();
    state.timeLeftForQuestion = config.timePerQuestion;
    if (dom.timerElement) dom.timerElement.classList.remove('timeout');
    if (dom.timerBar) {
        dom.timerBar.style.transition = 'none';
        dom.timerBar.style.width = '100%';
        dom.timerBar.classList.remove('paused', 'correct-pause');
        dom.timerBar.style.backgroundColor = 'var(--timer-bar-color)';
        void dom.timerBar.offsetWidth;
        dom.timerBar.style.transition = `width ${config.timePerQuestion}s linear`;
    }
    if (dom.timerDisplay) dom.timerDisplay.innerText = state.timeLeftForQuestion;
    if (dom.nextBtn) dom.nextBtn.style.display = "none";
    if (dom.explanationEl) dom.explanationEl.style.display = "none";
    state.currentLifelineUsed = false;
    if (dom.lifelineBtn) {
        dom.lifelineBtn.disabled = false;
        dom.lifelineBtn.style.display = 'inline-block';
    }
    if (dom.optionsEl) {
        dom.optionsEl.querySelectorAll('.icon-feedback').forEach(icon => icon.remove());
        dom.optionsEl.querySelectorAll("button").forEach(btn => {
            btn.disabled = false;
            btn.className = '';
        });
    }
    if (dom.timeoutOverlay) dom.timeoutOverlay.classList.remove('visible');
    if (dom.questionTextEl) dom.questionTextEl.classList.remove('match-question');
    if (dom.aiExplainerBtn) dom.aiExplainerBtn.disabled = true;
}

function updateStatusTracker() {
    if (!dom.statusTrackerEl || !state.currentQuizData) return;
    const cd = state.currentQuizData;
    const attemptedCount = cd.attempts.length;
    const remainingCount = Math.max(0, cd.questions.length - attemptedCount);
    const correctCount = cd.attempts.filter(a => a.status === 'Correct').length;
    const wrongCount = cd.attempts.filter(a => a.status === 'Wrong' || a.status === 'Timeout').length;
    dom.statusTrackerEl.innerHTML = `<span>✅ Correct: ${correctCount}</span> | <span>❌ Wrong/Timeout: ${wrongCount}</span> | <span>⏳ Remaining: ${remainingCount}</span>`;
}

function startTimer() {
    state.timeLeftForQuestion = config.timePerQuestion;
    dom.timerDisplay.innerText = state.timeLeftForQuestion;
    dom.timerElement.classList.remove('timeout');
    dom.timerBar.style.transition = 'none';
    dom.timerBar.style.width = '100%';
    dom.timerBar.classList.remove('paused', 'correct-pause');
    dom.timerBar.style.backgroundColor = 'var(--timer-bar-color)';
    void dom.timerBar.offsetWidth;
    dom.timerBar.style.transition = `width ${config.timePerQuestion}s linear`;
    dom.timerBar.style.width = '0%';
    clearInterval(state.timer);
    state.timer = setInterval(() => {
        state.timeLeftForQuestion--;
        dom.timerDisplay.innerText = state.timeLeftForQuestion;
        if (state.timeLeftForQuestion <= 5 && state.timeLeftForQuestion > 0 && !dom.timerElement.classList.contains('timeout')) {
            dom.timerElement.classList.add('timeout');
        }
        if (state.timeLeftForQuestion <= 0) handleTimeout();
    }, 1000);
}

// js/quiz.js

function stopTimer() {
    clearInterval(state.timer);

    // NEW, MORE EFFICIENT LOGIC
    if (dom.timerBar) {
        // Get the computed style (which doesn't force a reflow like offsetWidth)
        const computedStyle = window.getComputedStyle(dom.timerBar);
        const currentWidth = computedStyle.getPropertyValue('width');
        
        // Stop the CSS transition and immediately set the width to its current pixel value
        dom.timerBar.style.transition = 'none';
        dom.timerBar.style.width = currentWidth;
    }

    if (dom.timerElement) {
        dom.timerElement.classList.remove('timeout');
    }
}

function handleTimeout() {
    stopTimer();
    triggerHapticFeedback('wrong');
    dom.timerBar.style.width = '0%';
    dom.timerBar.classList.add('paused');
    dom.timerBar.style.backgroundColor = 'var(--wrong-color)';
    playSound('wrong-sound');
    let q = state.currentQuizData.shuffledQuestions[state.currentQuizData.currentQuestionIndex];
    if (dom.timeoutOverlay) dom.timeoutOverlay.classList.add('visible');

    const displayedOptionsData = (q.displayOrderIndices || q.options?.map((_, i) => i) || []).map(originalOptIndex => ({
        eng: q.options?.[originalOptIndex] || "",
        hin: (q.options_hi && q.options_hi[originalOptIndex]) ? q.options_hi[originalOptIndex] : null
    }));

    state.currentQuizData.attempts.push({
        questionId: q.id,   v1_id: q.v1_id,    // Add this//
        question: q.question, question_hi: q.question_hi || null,
        optionsDisplayedBilingual: displayedOptionsData,
        status: "Timeout", selected: "Timed Out", correct: q.correct, explanation: q.explanation, timeTaken: config.timePerQuestion
    });

    dom.optionsEl.querySelectorAll("button").forEach(btn => {
        btn.disabled = true;
        if (btn.dataset.value.trim() === q.correct?.trim()) {
            btn.classList.add("reveal-correct");
            const correctIconTimeout = document.createElement('span');
            correctIconTimeout.classList.add('icon-feedback');
            correctIconTimeout.innerHTML = "✔️";
            if (!btn.querySelector('.icon-feedback')) btn.appendChild(correctIconTimeout.cloneNode(true));
        }
    });
    if (dom.lifelineBtn) dom.lifelineBtn.disabled = true;
    showExplanation();
    updateStatusTracker();
    applyTextZoom();
    dom.nextBtn.style.display = "inline-block";
    dom.aiExplainerBtn.disabled = false;
    setTimeout(() => {
        if (dom.timeoutOverlay) dom.timeoutOverlay.classList.remove('visible');
    }, 1500);
    populateQuizInternalNavigation();
    saveState();
}

function useLifeline() {
    if (!state.currentQuizData || state.currentLifelineUsed || state.timeLeftForQuestion <= 0) return;
    let q = state.currentQuizData.shuffledQuestions[state.currentQuizData.currentQuestionIndex];
    const optionsButtons = Array.from(dom.optionsEl.querySelectorAll("button"));
    const correctEnglishOption = q.correct?.trim();
    const incorrectOptions = optionsButtons.filter(btn => btn.dataset.value.trim() !== correctEnglishOption && !btn.disabled);
    if (incorrectOptions.length >= 2) {
        shuffleArray(incorrectOptions);
        let countDisabled = 0;
        for (let i = 0; i < incorrectOptions.length && countDisabled < 2; i++) {
            incorrectOptions[i].disabled = true;
            incorrectOptions[i].classList.add('lifeline-disabled');
            countDisabled++;
        }
        state.currentLifelineUsed = true;
        dom.lifelineBtn.disabled = true;
    }
}

function showExplanation() {
    if (!state.currentQuizData) return;
    const q = state.currentQuizData.shuffledQuestions[state.currentQuizData.currentQuestionIndex];
    if (q?.explanation) {
        const explanationHtml = buildExplanationHtml(q.explanation);
        dom.explanationEl.classList.remove('section-fade-in');

        if (state.isTypewriterEnabled) {
            typewriterAnimate(dom.explanationEl, explanationHtml, 2.5);
        } else {
            dom.explanationEl.innerHTML = explanationHtml;
            void dom.explanationEl.offsetWidth; 
            dom.explanationEl.classList.add('section-fade-in');
        }
        dom.explanationEl.style.display = "block";
    } else {
        dom.explanationEl.style.display = "none";
    }
}

function getGeminiExplanation() {
    if (!state.currentQuizData || !dom.aiExplainerBtn) return;
    appCallbacks.showAIExplanation();
    dom.aiExplainerBtn.disabled = true;
    dom.aiExplainerBtn.classList.add('ai-thinking');
    dom.aiExplanationBody.classList.add('is-loading');
    dom.aiExplanationBody.innerHTML = `<div class="ai-loading-container"><lottie-player src="https://assets9.lottiefiles.com/packages/lf20_j1adxtyb.json" background="transparent" speed="1" style="width: 200px; height: 200px;" loop autoplay></lottie-player><p>Connecting to AI service...</p></div>`;

    setTimeout(() => {
        dom.aiExplanationBody.classList.remove('is-loading');
        dom.aiExplanationBody.innerHTML = `<div class="ai-error-container">
            <h3><i class="fas fa-cogs"></i> AI Explainer - Feature Not Active</h3>
            <p>This feature requires a secure backend proxy to protect the API key. It cannot be called directly from a simple HTML/JS application.</p>
            <pre>Error: API_KEY cannot be exposed on the client-side.</pre>
        </div>`;
        dom.aiExplainerBtn.disabled = false;
        dom.aiExplainerBtn.classList.remove('ai-thinking');
    }, 1500);
}

function handleKeyPress(event) {
    if (event.key >= '1' && event.key <= '4') {
        const targetButton = dom.optionsEl.querySelector(`button[data-index="${event.key}"]:not(:disabled)`);
        if (targetButton) targetButton.click();
    } else if (event.key === 'Enter' || event.key === 'ArrowRight') {
        if (dom.nextQuestionBtn && !dom.nextQuestionBtn.disabled) dom.nextQuestionBtn.click();
    } else if (event.key === 'ArrowLeft') {
        if (dom.prevQuestionBtn && !dom.prevQuestionBtn.disabled) previousQuestionHandler();
    } else if (event.key.toLowerCase() === 'l' || event.key === '5') {
        if (dom.lifelineBtn && !dom.lifelineBtn.disabled) dom.lifelineBtn.click();
    } else if (event.key.toLowerCase() === 'a') {
        if (dom.aiExplainerBtn && !dom.aiExplainerBtn.disabled) dom.aiExplainerBtn.click();
    } else if (event.key.toLowerCase() === 'b') {
        if (dom.bookmarkBtn) dom.bookmarkBtn.click();
    } else if (event.key === 'Escape') toggleQuizInternalNavigation();
}

function loadNextGroup() {
    if (state.currentGroupIndex < state.questionGroups.length - 1) {
        
        const scoreSection = dom.finalScoreSection;
        scoreSection.classList.remove('section-fade-in');
        scoreSection.classList.add('section-fade-out');

        const onTransitionEnd = () => {
            scoreSection.removeEventListener('animationend', onTransitionEnd);
            scoreSection.style.display = 'none'; // Hide after fading out
            scoreSection.classList.remove('section-fade-out');

            // Now show and fade in the quiz section
            dom.quizSection.style.display = 'block';
            dom.quizSection.classList.remove('section-fade-in'); // Ensure clean state
            void dom.quizSection.offsetWidth; // Trigger reflow
            dom.quizSection.classList.add('section-fade-in');

            // Load new group data
            state.currentGroupIndex++;
            loadQuestionGroup(state.currentGroupIndex);
            startQuizLogicForGroup();
            appCallbacks.updateDynamicHeaders();
        };

        scoreSection.addEventListener('animationend', onTransitionEnd);

    } else {
        console.error("Attempted to load next group when there are no more groups.");
    }
}

function populateQuizInternalNavigation() {
    const panel = dom.navigationPanel;
    if (!panel || !state.questionGroups) return;

    const contentWrapper = panel.querySelector('.nav-panel-content');
    contentWrapper.innerHTML = '';
    // Add this at the end
const legendHtml = `
  <div class="nav-group-header" style="margin-top: 15px;">Legend</div>
  <div style="font-size: 0.8em; padding: 0 15px;">
    <span style="color:var(--correct-color);">■ Correct</span>
    <span style="color:var(--wrong-color); margin-left: 10px;">■ Wrong</span>
    <span style="color:#9c27b0; margin-left: 10px;">■ Skipped</span>
    <span style="margin-left: 10px;">■ Unanswered</span>
  </div>
`;
contentWrapper.insertAdjacentHTML('beforeend', legendHtml);

    const groupHeader = document.createElement('div');
    groupHeader.classList.add('nav-group-header');
    groupHeader.textContent = `Quiz Progress Map`;
    contentWrapper.appendChild(groupHeader);

    const listContainer = document.createElement('div');
    listContainer.classList.add('nav-groups-container');
    contentWrapper.appendChild(listContainer);

    const questionStatusMap = new Map();
    state.questionGroups.forEach(group => {
        group.attempts.forEach(attempt => {
            questionStatusMap.set(attempt.questionId, attempt.status.toLowerCase());
        });
    });

    let questionNumberOffset = 0; // Initialize an offset for global question numbering

    state.questionGroups.forEach((group, groupIdx) => {
        const groupItemDiv = document.createElement('div');
        groupItemDiv.classList.add('nav-group-item');
        const groupHeaderClickable = document.createElement('div');
        groupHeaderClickable.classList.add('nav-group-header-clickable');
        groupHeaderClickable.innerHTML = `<span>${group.groupName}</span> <i class="fas fa-chevron-down toggle-icon"></i>`;
        groupItemDiv.appendChild(groupHeaderClickable);

        const questionGrid = document.createElement('div');
        questionGrid.classList.add('nav-question-grid');
        if (group.isSubmenuOpen) {
            questionGrid.classList.add('open');
            groupHeaderClickable.querySelector('.toggle-icon').classList.add('rotated');
        }

        group.shuffledQuestions.forEach((q, questionIdx) => {
            const gridItemLink = document.createElement('a');
            gridItemLink.href = '#';
            gridItemLink.textContent = questionNumberOffset + questionIdx + 1; // Use offset for correct global number
                       gridItemLink.classList.add('nav-grid-item');

            gridItemLink.dataset.groupIndex = groupIdx;
            gridItemLink.dataset.questionId = q.id;

            const status = questionStatusMap.get(q.id);
            if (status) gridItemLink.dataset.status = status;

            if (state.currentQuizData?.markedForReview?.includes(q.id)) {
                gridItemLink.classList.add('marked-for-review');
            }

            if (groupIdx === state.currentGroupIndex && state.currentQuizData && state.currentQuizData.currentQuestionIndex === questionIdx) {
                gridItemLink.classList.add('active-question');
            }

            gridItemLink.addEventListener('click', (e) => {
                e.preventDefault();
                goToQuestionInAnyGroup(parseInt(e.target.dataset.groupIndex, 10), e.target.dataset.questionId);
                if (panel.classList.contains('open')) toggleQuizInternalNavigation();
            });
            questionGrid.appendChild(gridItemLink);
        });
        groupItemDiv.appendChild(questionGrid);

        groupHeaderClickable.addEventListener('click', () => {
            group.isSubmenuOpen = !group.isSubmenuOpen;
            questionGrid.classList.toggle('open');
            groupHeaderClickable.querySelector('.toggle-icon').classList.toggle('rotated', group.isSubmenuOpen);
        });

        listContainer.appendChild(groupItemDiv);
        
        // Update offset for the next group
        questionNumberOffset += group.shuffledQuestions.length;
    });
}

function goToQuestionInAnyGroup(targetGroupIndex, targetQuestionId) {
    if (targetGroupIndex !== state.currentGroupIndex) {
        loadQuestionGroup(targetGroupIndex);
    }
    if (!state.currentQuizData || !state.currentQuizData.shuffledQuestions) return;
    
    const foundIndex = state.currentQuizData.shuffledQuestions.findIndex(q => q.id === targetQuestionId);

    if (foundIndex !== -1) {
        state.currentQuizData.currentQuestionIndex = foundIndex;
        displayQuestion();
        saveState();
    } else {
        Toast.fire({ icon: 'error', title: `Question ID ${targetQuestionId} not found.` });
    }
    populateQuizInternalNavigation();
}

function toggleQuizInternalNavigation() {
    const isOpen = dom.navigationPanel.classList.toggle('open');
    dom.navOverlay.classList.toggle('active');
    dom.navMenuIcon.setAttribute('aria-expanded', isOpen);
    dom.navMenuIcon.classList.toggle('is-active');
    dom.navMenuIcon.setAttribute('aria-label', isOpen ? 'Close Navigation Menu' : 'Open Navigation Menu');
    
    if (isOpen) {
        setTimeout(() => {
            const activeQuestionEl = dom.navigationPanel.querySelector('.active-question');
            if (activeQuestionEl) {
                activeQuestionEl.closest('.nav-group-item')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
        }, 100);
    }
}

function toggleBookmark() {
    const questionId = dom.bookmarkBtn.dataset.questionId;
    if (!questionId) return;

    const index = state.bookmarkedQuestions.indexOf(questionId);
    if (index > -1) {
        state.bookmarkedQuestions.splice(index, 1);
    } else {
        state.bookmarkedQuestions.push(questionId);
    }
    updateBookmarkButton();
    saveState();
}

function updateBookmarkButton() {
    if (!dom.bookmarkBtn || !state.currentQuizData) return;
    const currentQuestionId = state.currentQuizData.shuffledQuestions[state.currentQuizData.currentQuestionIndex].id;
    const isBookmarked = state.bookmarkedQuestions.includes(currentQuestionId);
    dom.bookmarkBtn.classList.toggle('bookmarked', isBookmarked);
    dom.bookmarkBtn.innerHTML = isBookmarked ? '<i class="fas fa-star"></i>' : '<i class="far fa-star"></i>';
    dom.bookmarkBtn.dataset.questionId = currentQuestionId;
}

function toggleMarkForReview() {
    if (!state.currentQuizData) return;
    const questionId = state.currentQuizData.shuffledQuestions[state.currentQuizData.currentQuestionIndex].id;
    const reviewList = state.currentQuizData.markedForReview;
    const index = reviewList.indexOf(questionId);
    if (index > -1) {
        reviewList.splice(index, 1);
    } else {
        reviewList.push(questionId);
    }
    updateMarkForReviewButton();
    populateQuizInternalNavigation();
    saveState();
}

function updateMarkForReviewButton() {
    if (!dom.markReviewBtn || !state.currentQuizData) return;
    const q = state.currentQuizData.shuffledQuestions[state.currentQuizData.currentQuestionIndex];
    const isMarked = state.currentQuizData.markedForReview.includes(q.id);
    dom.markReviewBtn.classList.toggle('marked', isMarked);
    dom.markReviewBtn.innerHTML = isMarked ? '<i class="fas fa-flag"></i> Marked' : '<i class="far fa-flag"></i> Mark for Review';
    dom.markReviewBtn.title = isMarked ? 'Unmark this question for review' : 'Mark this question for later review';
}

function updateQuizProgressBar() {
    if (!dom.quizProgressBar || !state.currentQuizData || !state.currentQuizData.shuffledQuestions.length) return;
    const total = state.currentQuizData.shuffledQuestions.length;
    const current = state.currentQuizData.currentQuestionIndex;
    const progress = ((current + 1) / total) * 100;
    dom.quizProgressBar.style.width = `${progress}%`;
}

function toggleHeader() {
    state.isHeaderCollapsed = !state.isHeaderCollapsed;
    applyHeaderCollapsedState();
    saveState();
}

function applyHeaderCollapsedState() {
    dom.collapsibleHeaderContent.classList.toggle('collapsed', state.isHeaderCollapsed);
    dom.toggleHeaderBtn.classList.toggle('collapsed', state.isHeaderCollapsed);
    dom.quizHeaderBar.classList.toggle('collapsed', state.isHeaderCollapsed);
    dom.toggleHeaderBtn.setAttribute('aria-expanded', !state.isHeaderCollapsed);
}