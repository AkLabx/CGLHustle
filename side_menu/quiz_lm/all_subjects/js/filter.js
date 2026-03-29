

import { config, state } from './state.js';
import { dom } from './dom.js';
import { shuffleArray } from './utils.js';

let appCallbacks = {};

// --- HELPER FUNCTIONS (FROM BOTH VERSIONS) ---

// Helper from Old Version (Used for PPT/PDF text cleaning)
function cleanQuestionText(text) {
    return (text || "").replace(/^(Q\.\d+\)|प्रश्न \d+\))\s*/, '');
}

// Helper from New Version (For Loki.js field path mapping)
function getLokiFieldPath(key) {
    switch (key) {
        case 'subject': return 'classification.subject';
        case 'topic': return 'classification.topic';
        case 'subTopic': return 'classification.subTopic';
        case 'difficulty': return 'properties.difficulty';
        case 'questionType': return 'properties.questionType';
        case 'examName': return 'sourceInfo.examName';
        case 'examYear': return 'sourceInfo.examYear';
        case 'examDateShift': return 'sourceInfo.examDateShift';
        case 'tags': return 'tags';
        default: return key;
    }
}

// Helper from New Version (For safe data access from question objects)
function getQuestionValue(q, filterKey) {
    switch (filterKey) {
        case 'subject': return q?.classification?.subject;
        case 'topic': return q?.classification?.topic;
        case 'subTopic': return q?.classification?.subTopic;
        case 'difficulty': return q?.properties?.difficulty;
        case 'questionType': return q?.properties?.questionType;
        case 'examName': return q?.sourceInfo?.examName;
        case 'examYear': return q?.sourceInfo?.examYear;
        case 'examDateShift': return q?.sourceInfo?.examDateShift;
        case 'tags': return q?.tags;
        default: return null;
    }
}

// Helper from Old Version (For PPT markdown parsing)
function parseMarkdownForPptx(markdown) {
    if (!markdown) return [];

    const richTextArray = [];
    const lines = markdown.replace(/<br\s*\/?>/gi, '\n').replace(/<\/?pre>/g, '').split('\n');

    lines.forEach((line, index) => {
        const processedLine = line.replace(/^[-*]\s*/, '• ');
        const parts = processedLine.split(/(\*\*.*?\*\*)/g).filter(Boolean);

        if (parts.length === 0 && line.trim() === '') {
            richTextArray.push({ text: '\n' });
            return;
        }

        parts.forEach(part => {
            if (part.startsWith('**') && part.endsWith('**')) {
                richTextArray.push({
                    text: part.substring(2, part.length - 2),
                    options: { bold: true }
                });
            } else if (part) {
                richTextArray.push({ text: part });
            }
        });
        
        if (index < lines.length - 1) {
            richTextArray.push({ text: '\n' });
        }
    });
    return richTextArray;
}


// --- INITIALIZATION ---

export function initFilterModule(callbacks) {
    appCallbacks = callbacks;
    initializeTabs();
    bindFilterEventListeners();
    loadQuestionsForFiltering();
    state.callbacks.confirmGoBackToFilters = callbacks.confirmGoBackToFilters;
}

function initializeTabs() {
    dom.tabButtons.forEach(button => {
        button.addEventListener('click', () => {
            const targetPanelId = button.dataset.tab;
            
            dom.tabButtons.forEach(btn => btn.classList.remove('active'));
            button.classList.add('active');

            dom.tabPanels.forEach(panel => {
                panel.classList.toggle('active', panel.id === targetPanelId);
            });

            dom.tabTaglines.forEach(tagline => {
                tagline.classList.toggle('active', tagline.dataset.tab === targetPanelId);
            });
        });
    });
}

function bindFilterEventListeners() {
    dom.startQuizBtn.onclick = () => startFilteredQuiz();
    dom.createPptBtn.onclick = () => generatePowerPoint();
    dom.createPdfBtn.onclick = () => generatePDF();
    dom.downloadJsonBtn.onclick = () => downloadJSON();
    
    [dom.resetFiltersBtnQuiz, dom.resetFiltersBtnPpt, dom.resetFiltersBtnJson].forEach(btn => {
        if(btn) btn.onclick = () => resetFilters();
    });

    dom.quickStartButtons.forEach(btn => {
        btn.onclick = () => handleQuickStart(btn.dataset.preset);
    });

    config.filterKeys.forEach(key => {
        const elements = dom.filterElements[key];
        if (elements.toggleBtn) {
            elements.toggleBtn.onclick = (e) => {
                e.stopPropagation();
                toggleMultiSelectDropdown(key);
            };
        }
        if (elements.searchInput) {
            elements.searchInput.oninput = () => filterMultiSelectList(key);
        }
    });

    document.addEventListener('click', (e) => {
        config.filterKeys.forEach(key => {
            if (dom.filterElements[key]?.container && !dom.filterElements[key].container.contains(e.target)) {
                toggleMultiSelectDropdown(key, true);
            }
        });
    });

    if (dom.dynamicBreadcrumb) {
        dom.dynamicBreadcrumb.addEventListener('click', (e) => {
            if (e.target?.id === 'breadcrumb-filters-link') {
                e.preventDefault();
                appCallbacks.confirmGoBackToFilters();
            }
        });
    }
}

// --- DATA LOADING & DATABASE INITIALIZATION (COMBINED) ---

async function loadQuestionsForFiltering() {
    try {
        const response = await fetch('./questions.json');
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

        const contentLength = response.headers.get('Content-Length');
        const total = parseInt(contentLength, 10);
        let loaded = 0;

        if (!total || !response.body) {
            console.warn("Progress bar not supported by this server/browser. Loading questions...");
            if (dom.loadingPercentage) dom.loadingPercentage.textContent = 'Loading...';
            state.allQuestionsMasterList = await response.json();
        } else {
            const reader = response.body.getReader();
            const chunks = [];
            
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                chunks.push(value);
                loaded += value.length;
                const progress = Math.round((loaded / total) * 100);
                
                if (dom.loadingProgressBar) dom.loadingProgressBar.style.width = `${progress}%`;
                if (dom.loadingPercentage) dom.loadingPercentage.textContent = `${progress}%`;
            }

            const allChunks = new Uint8Array(loaded);
            let position = 0;
            for (const chunk of chunks) {
                allChunks.set(chunk, position);
                position += chunk.length;
            }

            const resultText = new TextDecoder("utf-8").decode(allChunks);
            state.allQuestionsMasterList = JSON.parse(resultText);
        }
        
        if (!state.allQuestionsMasterList || state.allQuestionsMasterList.length === 0) {
            throw new Error(`No questions were found or the file is empty.`);
        }

        // --- Loki.js and Fuse.js initialization (from New Version) ---
        state.lokiDb = new loki('quiz.db');
        state.lokiQuestionsCollection = state.lokiDb.addCollection('questions', { indices: ['id'] });
        state.lokiQuestionsCollection.insert(state.allQuestionsMasterList);

        config.searchableFilterKeys.forEach(key => {
            const uniqueOptions = [...new Set(state.allQuestionsMasterList.map(q => getQuestionValue(q, key)).flat().filter(Boolean))];
            state.fuseIndexes[key] = new Fuse(uniqueOptions, { threshold: 0.4 });
        });

        if (dom.loadingOverlay) {
            dom.loadingOverlay.classList.add('fade-out');
            dom.loadingOverlay.addEventListener('transitionend', () => {
                dom.loadingOverlay.style.display = 'none';
            }, { once: true });
        }
        
        populateFilterControls();
        onFilterStateChange();
    } catch (error) {
        console.error(`Could not load quiz questions:`, error);
        if (dom.loadingOverlay) {
            dom.loadingOverlay.innerHTML = `<div class="loader-content"><h1>Error Loading Quiz</h1><p>Could not fetch the questions. Please check your connection and refresh the page.</p><p style="font-size:0.8em; color: var(--text-color-light)">${error.message}</p></div>`;
        }
    }
}


// --- FILTERING LOGIC (USING LOKI.JS from New Version) ---

function buildLokiQuery(filters) {
    const query = { '$and': [] };
    config.filterKeys.forEach(key => {
        const selected = filters[key];
        if (selected && selected.length > 0) {
            const field = getLokiFieldPath(key);
            const condition = {};
            if (key === 'tags') {
                condition[field] = { '$containsAny': selected };
            } else {
                condition[field] = { '$in': selected };
            }
            query['$and'].push(condition);
        }
    });
    return query['$and'].length > 0 ? query : {};
}

function applyFilters() {
    if (!state.lokiQuestionsCollection) return;
    const query = buildLokiQuery(state.selectedFilters);
    state.filteredQuestionsMasterList = state.lokiQuestionsCollection.find(query);
    updateQuestionCount();
}

function onFilterStateChange() {
    updateDependentFilters();
    applyFilters();
    updateAllFilterCountsAndAvailability();
    updateActiveFiltersSummaryBar();
}

function handleSelectionChange(filterKey, value) {
    const selectedValues = state.selectedFilters[filterKey];
    const index = selectedValues.indexOf(value);

    if (index > -1) {
        selectedValues.splice(index, 1);
    } else {
        selectedValues.push(value);
    }

    if (filterKey === 'subject') {
        state.selectedFilters.topic = [];
        state.selectedFilters.subTopic = [];
    } else if (filterKey === 'topic') {
        state.selectedFilters.subTopic = [];
    }

    onFilterStateChange();
}


// --- UI POPULATION & UPDATES ---

function populateFilterControls() {
    const unique = {};
    config.filterKeys.forEach(key => unique[key] = new Set());

    state.allQuestionsMasterList.forEach(q => {
        config.filterKeys.forEach(key => {
            const value = getQuestionValue(q, key);
            if (Array.isArray(value)) {
                value.forEach(v => unique[key].add(v));
            } else if (value) {
                unique[key].add(value);
            }
        });
    });

    populateMultiSelect('subject', [...unique.subject].sort());
    populateSegmentedControl('difficulty', ['Easy', 'Medium', 'Hard'].filter(d => unique.difficulty.has(d)));
    populateSegmentedControl('questionType', [...unique.questionType].sort());
    populateMultiSelect('examName', [...unique.examName].sort());
    populateMultiSelect('examYear', [...unique.examYear].sort((a,b) => b-a));
    populateMultiSelect('tags', [...unique.tags].sort());
    populateMultiSelect('examDateShift', [...unique.examDateShift].sort());
    
    dom.filterElements.topic.toggleBtn.disabled = true;
    dom.filterElements.topic.toggleBtn.textContent = "Select a Subject first";
    dom.filterElements.subTopic.toggleBtn.disabled = true;
    dom.filterElements.subTopic.toggleBtn.textContent = "Select a Topic first";
}

function populateMultiSelect(filterKey, options) {
    const listElement = dom.filterElements[filterKey]?.list;
    if (!listElement) return;

    const selectedValues = state.selectedFilters[filterKey] || [];
    listElement.innerHTML = '';
    options.forEach(opt => {
        const label = document.createElement('label');
        label.className = 'multiselect-item';
        
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.value = opt;
        checkbox.checked = selectedValues.includes(opt);
        checkbox.onchange = () => handleSelectionChange(filterKey, opt);
        
        const text = document.createElement('span');
        text.textContent = opt;

        const countSpan = document.createElement('span');
        countSpan.className = 'filter-option-count';

        label.append(checkbox, text, countSpan);
        listElement.appendChild(label);
    });
}

function populateSegmentedControl(filterKey, options) {
    const container = dom.filterElements[filterKey]?.segmentedControl;
    if (!container) return;
    container.innerHTML = '';
    options.forEach(opt => {
        const btn = document.createElement('button');
        btn.className = 'segmented-btn';
        btn.dataset.value = opt;
        btn.onclick = () => handleSelectionChange(filterKey, opt);
        
        const text = document.createElement('span');
        text.textContent = opt;
        
        const countSpan = document.createElement('span');
        countSpan.className = 'filter-option-count';

        btn.append(text, countSpan);
        container.appendChild(btn);
    });
}

function updateDependentFilters() {
    const { subject: selectedSubjects, topic: selectedTopics } = state.selectedFilters;
    const { topic: topicElements, subTopic: subTopicElements } = dom.filterElements;

    if (selectedSubjects.length === 0) {
        topicElements.toggleBtn.disabled = true;
        topicElements.toggleBtn.textContent = "Select a Subject first";
        topicElements.list.innerHTML = '';
    } else {
        topicElements.toggleBtn.disabled = false;
        const query = { 'classification.subject': { '$in': selectedSubjects }, 'classification.topic': { '$ne': null } };
        const relevantTopics = [...new Set(state.lokiQuestionsCollection.find(query).map(q => q.classification.topic))].sort();
        populateMultiSelect('topic', relevantTopics);
    }
    
    if (selectedTopics.length === 0) {
        subTopicElements.toggleBtn.disabled = true;
        subTopicElements.toggleBtn.textContent = "Select a Topic first";
        subTopicElements.list.innerHTML = '';
    } else {
        subTopicElements.toggleBtn.disabled = false;
        const query = { 
            '$and': [
                { 'classification.subject': { '$in': selectedSubjects } },
                { 'classification.topic': { '$in': selectedTopics } },
                { 'classification.subTopic': { '$ne': null } }
            ]
        };
        const relevantSubTopics = [...new Set(state.lokiQuestionsCollection.find(query).map(q => q.classification.subTopic))].sort();
        populateMultiSelect('subTopic', relevantSubTopics);
    }
}

function updateAllFilterCountsAndAvailability() {
    if (!state.lokiQuestionsCollection) return;

    config.filterKeys.forEach(filterKey => {
        const tempFilters = JSON.parse(JSON.stringify(state.selectedFilters));
        tempFilters[filterKey] = [];
        
        const contextualQuery = buildLokiQuery(tempFilters);
        const contextualList = state.lokiQuestionsCollection.find(contextualQuery);

        const counts = {};
        contextualList.forEach(q => {
            const value = getQuestionValue(q, filterKey);
            if (Array.isArray(value)) {
                value.forEach(v => { counts[v] = (counts[v] || 0) + 1; });
            } else if (value) {
                counts[value] = (counts[value] || 0) + 1;
            }
        });

        updateFilterUI(filterKey, counts);
    });
}

function updateFilterUI(filterKey, counts) {
    const elements = dom.filterElements[filterKey];
    if (elements.list) {
        elements.list.querySelectorAll('.multiselect-item').forEach(label => {
            const checkbox = label.querySelector('input');
            const value = checkbox.value;
            checkbox.checked = state.selectedFilters[filterKey].includes(value);

            const count = counts[value] || 0;
            label.querySelector('.filter-option-count').textContent = `(${count})`;
            const isDisabled = count === 0 && !checkbox.checked;
            label.classList.toggle('disabled', isDisabled);
            checkbox.disabled = isDisabled;
        });
        updateMultiSelectButtonText(filterKey);
    } else if (elements.segmentedControl) {
        elements.segmentedControl.querySelectorAll('.segmented-btn').forEach(btn => {
            const value = btn.dataset.value;
            const count = counts[value] || 0;
            btn.querySelector('.filter-option-count').textContent = `(${count})`;
            btn.classList.toggle('active', state.selectedFilters[filterKey].includes(value));
        });
    }
}

function updateQuestionCount() {
    const count = state.filteredQuestionsMasterList.length;
    const isDisabled = count === 0;
    [dom.questionCount, dom.pptQuestionCount, dom.pdfQuestionCount, dom.jsonQuestionCount].forEach(el => {
        if (el) el.textContent = count;
    });
    [dom.startQuizBtn, dom.createPptBtn, dom.createPdfBtn, dom.downloadJsonBtn].forEach(btn => {
        if(btn) btn.disabled = isDisabled;
    });
}


// --- ACTIONS & TRIGGERS ---

function resetFilters() {
    config.filterKeys.forEach(key => state.selectedFilters[key] = []);
    config.searchableFilterKeys.forEach(key => {
        const searchInput = dom.filterElements[key]?.searchInput;
        if (searchInput) {
            searchInput.value = '';
            filterMultiSelectList(key);
        }
    });
    onFilterStateChange();
}

function startFilteredQuiz() {
    if (state.filteredQuestionsMasterList.length === 0) {
        Swal.fire('No Questions Found', 'Please adjust your filters to select at least one question.', 'warning');
        return;
    }
    appCallbacks.startQuiz(state.filteredQuestionsMasterList);
}

function handleQuickStart(preset) {
    const presetFilters = { subject: [], topic: [], subTopic: [], difficulty: [], questionType: [], examName: [], examYear: [], tags: [] };
    
    switch(preset) {
        case 'quick_25_easy': presetFilters.difficulty = ['Easy']; break;
        case 'quick_25_moderate': presetFilters.difficulty = ['Medium']; break;
        case 'quick_25_hard': presetFilters.difficulty = ['Hard']; break;
        case 'quick_25_mix': break;
    }

    const query = buildLokiQuery(presetFilters);
    let questions = state.lokiQuestionsCollection.find(query);
    
    shuffleArray(questions);
    const quickStartQuestions = questions.slice(0, 25);

    if (quickStartQuestions.length === 0) {
        Swal.fire({
            target: dom.filterSection,
            title: 'No Questions Found', 
            text: 'This quick start preset yielded no questions. Please try another or use the custom filters.', 
            icon: 'warning'
        });
        return;
    }
    
    appCallbacks.startQuiz(quickStartQuestions);
}

// --- PPT, PDF, JSON GENERATION (FULLY RESTORED from Old Version) ---

async function generatePowerPoint() {
    const questions = state.filteredQuestionsMasterList;
    if (questions.length === 0) {
        Swal.fire({
            target: dom.filterSection,
            title: 'No Questions Selected',
            text: 'Please apply filters to select questions before creating a PPT.',
            icon: 'info'
        });
        return;
    }

    dom.pptLoadingOverlay.style.display = 'flex';
    dom.pptLoadingText.textContent = 'Generating Your Presentation...';
    dom.pptLoadingDetails.textContent = '';
    dom.pptLoadingProgressBar.style.width = '0%';

    try {
        const pptx = new PptxGenJS();

        pptx.layout = 'LAYOUT_16x9';
        pptx.author = 'Quiz LM App';
        pptx.company = 'AI-Powered Learning';
        pptx.title = 'Customized Quiz Presentation';
        
        const TITLE_SLIDE_BG = 'F5F5F5';
        const QUESTION_SLIDE_BG = 'D6EAF8';
        const ANSWER_SLIDE_BG = 'E2F0D9';
        
        const TEXT_COLOR = '191919';
        const CORRECT_ANSWER_COLOR = '006400';
        const ENGLISH_FONT = 'Arial';
        const HINDI_FONT = 'Nirmala UI';

        // --- TITLE SLIDE (WITH DYNAMIC INFO) ---
        let titleSlide = pptx.addSlide();
        titleSlide.background = { color: TITLE_SLIDE_BG };
        
        titleSlide.addText("Quiz LM Presentation ✨", {
            x: 0.5, y: 0.8, w: '90%', h: 1,
            fontSize: 44, color: '303f9f', bold: true, align: 'center'
        });
        titleSlide.addText(`Generated with ${questions.length} questions.`, {
            x: 0, y: 2.0, w: '100%', align: 'center', color: TEXT_COLOR, fontSize: 18
        });

        const indianTimestamp = new Date().toLocaleString('en-IN', {
            timeZone: 'Asia/Kolkata',
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true
        });
        titleSlide.addText(`Created on: ${indianTimestamp} (IST)`, {
            x: 0, y: 2.4, w: '100%', align: 'center', color: '757575', fontSize: 11, italic: true
        });

        const filterTextForPPT = [];
        const filterHierarchy = {
            'Classification': ['subject', 'topic', 'subTopic'],
            'Properties': ['difficulty', 'questionType'],
            'Source': ['examName', 'examYear'],
            'Tags': ['tags']
        };

        let hasFilters = false;
        for (const category in filterHierarchy) {
            const filtersInCategory = [];
            filterHierarchy[category].forEach(filterKey => {
                const selected = state.selectedFilters[filterKey];
                if (selected && selected.length > 0) {
                    hasFilters = true;
                    const displayName = filterKey.charAt(0).toUpperCase() + filterKey.slice(1).replace(/([A-Z])/g, ' $1').trim();
                    filtersInCategory.push(`${displayName}: ${selected.join(', ')}`);
                }
            });

            if (filtersInCategory.length > 0) {
                filterTextForPPT.push({ text: category, options: { bold: true, breakLine: true, fontSize: 12, color: '303f9f', align: 'left'} });
                filtersInCategory.forEach(filterText => {
                    filterTextForPPT.push({ text: `  • ${filterText}`, options: { breakLine: true, fontSize: 11, color: TEXT_COLOR, align: 'left' }});
                });
                filterTextForPPT.push({ text: '', options: { breakLine: true } });
            }
        }
        
        if (hasFilters) {
            titleSlide.addText(filterTextForPPT, {
                x: 1.0, y: 3.0, w: '80%', h: 2.5,
                lineSpacing: 22, valign: 'top'
            });
        }
        
        // --- ASYNCHRONOUS QUESTION & ANSWER SLIDE GENERATION ---
        const totalQuestions = questions.length;
        const processQuestionSlides = () => {
            return new Promise(resolve => {
                let i = 0;
                function processNextQuestion() {
                    if (i >= totalQuestions) {
                        resolve();
                        return;
                    }
                    
                    const question_item = questions[i];
                    const slide_question_number = i + 1;

                    // SLIDE 1: QUESTION & OPTIONS
                    let q_slide = pptx.addSlide();
                    q_slide.background = { color: QUESTION_SLIDE_BG };
                    let question_text = cleanQuestionText(question_item.question);
                    const examInfoText = ` (${question_item.sourceInfo.examName}, ${question_item.sourceInfo.examDateShift})`;
                    const englishQuestionArray = [
                        ...parseMarkdownForPptx(`Q.${slide_question_number}) ${question_text}`),
                        { text: examInfoText, options: { fontSize: 12, color: 'C62828', italic: true } }
                    ];
                    q_slide.addText(englishQuestionArray, { x: 0.5, y: 0.3, w: 9, h: 1.2, fontFace: ENGLISH_FONT, fontSize: 20, color: TEXT_COLOR, bold: true });
                    const question_text_hi = cleanQuestionText(question_item.question_hi);
                    q_slide.addText(parseMarkdownForPptx(question_text_hi || ''), { x: 0.5, y: 1.5, w: 9, h: 0.6, fontFace: HINDI_FONT, fontSize: 18, color: TEXT_COLOR, bold: true });
                    let optionsY = 2.3;
                    let optionsArray = [];
                    (question_item.options || []).forEach((eng_option, index) => {
                        const hin_option = (question_item.options_hi || [])[index] || '';
                        const option_letter = String.fromCharCode(65 + index);
                        const engParsed = parseMarkdownForPptx(`${option_letter}) ${eng_option}`);
                        engParsed.forEach(p => { p.options = {...p.options, fontFace: ENGLISH_FONT, fontSize: 16, color: TEXT_COLOR }});
                        optionsArray.push(...engParsed);
                        const hinParsed = parseMarkdownForPptx(`    ${hin_option}\n`);
                        hinParsed.forEach(p => { p.options = {...p.options, fontFace: HINDI_FONT, fontSize: 14, color: TEXT_COLOR }});
                        optionsArray.push(...hinParsed);
                    });
                    q_slide.addText(optionsArray, { x: 0.6, y: optionsY, w: 9, h: 3.0, lineSpacing: 24 });

                    // SLIDE 2 & 3: ANSWER & EXPLANATION
                    const explanation = question_item.explanation || {};
                    const slideParts = [
                        { part: 1, title: `Answer & Explanation for Q.${slide_question_number} (Part 1)`, content: [ { text: `✅ Correct Answer: ${question_item.correct || 'N/A'}` }, explanation.analysis_correct, explanation.conclusion, ] },
                        { part: 2, title: `Answer & Explanation for Q.${slide_question_number} (Part 2)`, content: [ explanation.analysis_incorrect, explanation.fact, ] }
                    ];
                    slideParts.forEach(partInfo => {
                        const contentBlocks = partInfo.content.filter(Boolean);
                        if (contentBlocks.length === 0) return;
                        let aSlide = pptx.addSlide();
                        aSlide.background = { color: ANSWER_SLIDE_BG };
                        aSlide.addText(partInfo.title, { x: 0.5, y: 0.3, w: 9, h: 0.6, fontFace: ENGLISH_FONT, fontSize: 18, color: TEXT_COLOR, bold: true });
                        let combinedExplanation = [];
                        contentBlocks.forEach(block => {
                            if (typeof block === 'string') {
                                combinedExplanation.push(...parseMarkdownForPptx(block));
                                combinedExplanation.push({ text: '\n\n' });
                            } else if (block.text && block.text.includes('Correct Answer')) {
                                combinedExplanation.push({ text: block.text, options: { bold: true, color: CORRECT_ANSWER_COLOR } });
                                combinedExplanation.push({ text: '\n\n' });
                            }
                        });
                        if (combinedExplanation.length > 0) {
                            aSlide.addText(combinedExplanation, { x: 0.5, y: 1.1, w: 9, h: 4.2, fontFace: ENGLISH_FONT, fontSize: 14, color: TEXT_COLOR, lineSpacing: 22 });
                        }
                    });
                    
                    const progress = Math.round(((i + 1) / totalQuestions) * 100);
                    dom.pptLoadingProgressBar.style.width = `${progress}%`;
                    dom.pptLoadingDetails.textContent = `Processing question ${slide_question_number} of ${totalQuestions}... (${progress}%)`;
                    
                    i++;
                    setTimeout(processNextQuestion, 10);
                }
                processNextQuestion();
            });
        };
        
        await processQuestionSlides();

        dom.pptLoadingText.textContent = 'Finalizing & Downloading...';
        dom.pptLoadingDetails.textContent = 'Please wait, this may take a moment.';
        
        let filenameParts = [];
        const { subject, examName } = state.selectedFilters;
        
        const subjects = [...subject].sort();
        const exams = [...examName].sort();
        
        const uniqueShifts = [...new Set(questions.map(q => q.sourceInfo?.examDateShift).filter(Boolean))];
        const shifts = uniqueShifts.sort();

        if (subjects.length > 0) filenameParts.push(subjects.join('_'));
        if (exams.length > 0) filenameParts.push(exams.join('_'));
        if (shifts.length > 0) filenameParts.push(shifts.join('_'));
        
        filenameParts.push(`${questions.length}Qs`);

        let filename = filenameParts.join('_');
        
        filename = filename.replace(/[^a-zA-Z0-9_\-]/g, '_').replace(/_+/g, '_');
        if (!filename.trim() || filename.trim() === `${questions.length}Qs`) {
            filename = `Quiz_LM_${questions.length}Qs`;
        }

        await pptx.writeFile({ fileName: `${filename}.pptx` });

    } catch (error) {
        console.error("Error generating PPT:", error);
        Swal.fire({
            target: dom.filterSection,
            title: 'Error',
            text: `An unexpected error occurred while generating the presentation: ${error.message}`,
            icon: 'error'
        });
    } finally {
        dom.pptLoadingOverlay.style.display = 'none';
    }
}


async function generatePDF() {
    const questions = state.filteredQuestionsMasterList;
    if (questions.length === 0) {
        Swal.fire({
            target: dom.filterSection,
            title: 'No Questions Selected',
            text: 'Please apply filters to select questions before creating a PDF.',
            icon: 'info'
        });
        return;
    }

    dom.pdfLoadingOverlay.style.display = 'flex';
    dom.pdfLoadingText.textContent = 'Generating Your PDF...';
    dom.pdfLoadingDetails.textContent = '';
    dom.pdfLoadingProgressBar.style.width = '0%';

    try {
        const { jsPDF } = window.jspdf;
        const doc = new jsPDF({ unit: 'pt', format: 'a4' });
        
        const MARGIN = 40;
        const PAGE_WIDTH = doc.internal.pageSize.getWidth();
        const PAGE_HEIGHT = doc.internal.pageSize.getHeight();
        const CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);
        let y = MARGIN;

        const addFooter = (doc, pageNum, totalPages) => {
            doc.setFont('Helvetica', 'italic');
            doc.setFontSize(9);
            doc.setTextColor(150);
            doc.text('Compiler: Aalok Kumar Sharma', MARGIN, PAGE_HEIGHT - 20);
            doc.text(`Page ${pageNum} of ${totalPages}`, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 20, { align: 'right' });
        };
        
        // --- Title Page ---
        doc.setFont('Helvetica', 'bold');
        doc.setFontSize(26);
        const titleText = 'Quiz LM Question Bank';
        const titleLines = doc.splitTextToSize(titleText, CONTENT_WIDTH);
        doc.text(titleLines, PAGE_WIDTH / 2, y + 20, { align: 'center' });
        y += (titleLines.length * 26) + 30;

        doc.setFont('Helvetica', 'normal');
        doc.setFontSize(16);
        doc.text(`Generated with ${questions.length} questions.`, PAGE_WIDTH / 2, y, { align: 'center' });
        y += 30;

        const indianTimestamp = new Date().toLocaleString('en-IN', {
            timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', hour12: true
        });
        doc.setFontSize(11);
        doc.setTextColor(120);
        doc.text(`Created on: ${indianTimestamp} (IST)`, PAGE_WIDTH / 2, y, { align: 'center' });
        y += 40;
        
        const linkText = 'Attempt the quiz';
        const linkUrl = 'https://cglhustle.free.nf/side_menu/quiz_lm/';
        doc.setFontSize(12);
        doc.setFont('Helvetica', 'normal');
        const textWidth = doc.getTextWidth(linkText);
        const xOffset = (PAGE_WIDTH - textWidth) / 2;
        
        doc.setTextColor(0, 0, 238);
        doc.textWithLink(linkText, xOffset, y, { url: linkUrl });
        doc.setDrawColor(0, 0, 238);
        doc.line(xOffset, y + 1, xOffset + textWidth, y + 1);
        y += 40;
        
        doc.setTextColor(40);

        const filterHierarchy = {
            'Classification': ['subject', 'topic', 'subTopic'],
            'Properties': ['difficulty', 'questionType'],
            'Source': ['examName', 'examYear'],
            'Tags': ['tags']
        };

        let hasFilters = false;
        
        for (const category in filterHierarchy) {
            const filtersInCategory = [];
            filterHierarchy[category].forEach(filterKey => {
                const selected = state.selectedFilters[filterKey];
                if (selected && selected.length > 0) {
                    hasFilters = true;
                    const displayName = filterKey.charAt(0).toUpperCase() + filterKey.slice(1).replace(/([A-Z])/g, ' $1').trim();
                    filtersInCategory.push(`${displayName}: ${selected.join(', ')}`);
                }
            });

            if (filtersInCategory.length > 0) {
                if (y > PAGE_HEIGHT - MARGIN) { doc.addPage(); y = MARGIN; }
                doc.setFont('Helvetica', 'bold');
                doc.setFontSize(12);
                doc.setTextColor(48, 63, 159);
                doc.text(category, MARGIN, y);
                y += 18;

                filtersInCategory.forEach(filterText => {
                    if (y > PAGE_HEIGHT - MARGIN) { doc.addPage(); y = MARGIN; }
                    doc.setFont('Helvetica', 'normal');
                    doc.setFontSize(10);
                    doc.setTextColor(40);
                    const filterLines = doc.splitTextToSize(`• ${filterText}`, CONTENT_WIDTH - 20);
                    doc.text(filterLines, MARGIN + 20, y);
                    y += (filterLines.length * 10 * 1.2);
                });
                y += 10;
            }
        }

        if (!hasFilters) {
            doc.setFontSize(12);
            doc.setTextColor(120);
            doc.text('No filters applied.', MARGIN, y);
        }

        const answers = [];
        
        // --- Questions Loop ---
        doc.addPage();
        let pageNum = 2;
        y = MARGIN;
        
        for (let i = 0; i < questions.length; i++) {
            const question_item = questions[i];
            const questionNum = i + 1;

            const progress = Math.round((i / questions.length) * 50);
            dom.pdfLoadingProgressBar.style.width = `${progress}%`;
            dom.pdfLoadingDetails.textContent = `Processing question ${questionNum} of ${questions.length}...`;
            
            let letteredCorrect = '?';
            let correctTextToPush = 'Answer not found';

            const summary = question_item.explanation?.summary || "";
            const summaryMatch = summary.match(/Correct Answer: ([A-D])\)/);
            const correctOptIndexFromText = question_item.options.indexOf(question_item.correct);

            if (summaryMatch) {
                letteredCorrect = summaryMatch[1];
                const correctIndexFromLetter = letteredCorrect.charCodeAt(0) - 65;
                if (question_item.options[correctIndexFromLetter]) {
                    correctTextToPush = question_item.options[correctIndexFromLetter];
                } else {
                    correctTextToPush = "Text mismatch in data";
                }
            } else if (correctOptIndexFromText !== -1) {
                letteredCorrect = String.fromCharCode(65 + correctOptIndexFromText);
                correctTextToPush = question_item.correct;
            }
            
            answers.push(`${questionNum}. ${letteredCorrect}) ${correctTextToPush}`);

            const cleanQ = cleanQuestionText(question_item.question);
            const questionText = `Q.${questionNum}) ${cleanQ}`;
            
            doc.setFont('Helvetica', 'bold');
            doc.setFontSize(12);
            const questionLines = doc.splitTextToSize(questionText, CONTENT_WIDTH);
            const questionHeight = (questionLines.length * 12 * 1.2) + 10;

            let optionsHeight = 0;
            doc.setFont('Helvetica', 'normal');
            doc.setFontSize(10);
            question_item.options.forEach((opt, idx) => {
                const optionText = `(${String.fromCharCode(65 + idx)}) ${opt}`;
                const optionLines = doc.splitTextToSize(optionText, CONTENT_WIDTH - 20);
                optionsHeight += (optionLines.length * 10 * 1.2) + 5;
            });

            const totalQuestionBlockHeight = questionHeight + optionsHeight + 20;
            
            if (y + totalQuestionBlockHeight > PAGE_HEIGHT - MARGIN) {
                doc.addPage();
                pageNum++;
                y = MARGIN;
            }

            doc.setFont('Helvetica', 'bold');
            doc.setFontSize(12);
            doc.text(questionLines, MARGIN, y);
            y += (questionLines.length * 12 * 1.2) + 10;

            doc.setFont('Helvetica', 'normal');
            doc.setFontSize(10);
            question_item.options.forEach((opt, idx) => {
                const optionText = `(${String.fromCharCode(65 + idx)}) ${opt}`;
                const optionLines = doc.splitTextToSize(optionText, CONTENT_WIDTH - 20);
                doc.text(optionLines, MARGIN + 20, y);
                y += (optionLines.length * 10 * 1.2) + 5;
            });
            
            y += 15;
            doc.setDrawColor(220);
            doc.line(MARGIN, y, PAGE_WIDTH - MARGIN, y);
            y += 20;

            await new Promise(resolve => setTimeout(resolve, 1));
        }

        // --- Answer Key Page ---
        dom.pdfLoadingDetails.textContent = `Generating Answer Key...`;
        doc.addPage();
        pageNum++;
        y = MARGIN;
        doc.setFont('Helvetica', 'bold');
        doc.setFontSize(20);
        doc.text('Answer Key', PAGE_WIDTH / 2, y, { align: 'center' });
        y += 40;

        doc.setFont('Helvetica', 'normal');
        doc.setFontSize(10);
        
        const answerKeyGutter = 30;
        const answerKeyColWidth = (CONTENT_WIDTH - answerKeyGutter) / 2;
        const col1X = MARGIN;
        const col2X = MARGIN + answerKeyColWidth + answerKeyGutter;
        let currentY = y;
        const midPoint = Math.ceil(answers.length / 2);

        for (let i = 0; i < midPoint; i++) {
            const progress = 50 + Math.round((i / midPoint) * 50);
            dom.pdfLoadingProgressBar.style.width = `${progress}%`;

            const text1 = answers[i];
            const lines1 = doc.splitTextToSize(text1, answerKeyColWidth);
            const height1 = doc.getTextDimensions(lines1).h;
            
            const text2 = (i + midPoint < answers.length) ? answers[i + midPoint] : null;
            let lines2 = [];
            let height2 = 0;
            if (text2) {
                lines2 = doc.splitTextToSize(text2, answerKeyColWidth);
                height2 = doc.getTextDimensions(lines2).h;
            }

            const blockHeight = Math.max(height1, height2);


            if (currentY + blockHeight > PAGE_HEIGHT - MARGIN - 20) {
                doc.addPage();
                pageNum++;
                currentY = MARGIN;
            }

            doc.text(lines1, col1X, currentY);
            if (text2) {
                doc.text(lines2, col2X, currentY);
            }
            
            currentY += blockHeight + 5;
        }
        
        const totalPages = doc.internal.getNumberOfPages();
        for (let i = 1; i <= totalPages; i++) {
            doc.setPage(i);
            addFooter(doc, i, totalPages);
        }

        dom.pdfLoadingText.textContent = 'Finalizing & Downloading...';
        dom.pdfLoadingDetails.textContent = 'Please wait, this may take a moment.';
        
        let filenameParts = [];
        const { subject, examName } = state.selectedFilters;
        
        const subjects = [...subject].sort();
        const exams = [...examName].sort();
        
        const uniqueShifts = [...new Set(questions.map(q => q.sourceInfo?.examDateShift).filter(Boolean))];
        const shifts = uniqueShifts.sort();

        if (subjects.length > 0) filenameParts.push(subjects.join('_'));
        if (exams.length > 0) filenameParts.push(exams.join('_'));
        if (shifts.length > 0) filenameParts.push(shifts.join('_'));
        
        filenameParts.push(`${questions.length}Qs`);

        let filename = filenameParts.join('_');
        
        filename = filename.replace(/[^a-zA-Z0-9_\-]/g, '_').replace(/_+/g, '_');
        if (!filename.trim() || filename.trim() === `${questions.length}Qs`) {
            filename = `Quiz_LM_${questions.length}Qs`;
        }

        await doc.save(`${filename}.pdf`);

    } catch (error) {
        console.error("Error generating PDF:", error);
        Swal.fire({
            target: dom.filterSection,
            title: 'Error',
            text: `An unexpected error occurred while generating the PDF: ${error.message}`,
            icon: 'error'
        });
    } finally {
        dom.pdfLoadingOverlay.style.display = 'none';
    }
}


async function downloadJSON() {
    const questions = state.filteredQuestionsMasterList;
    if (questions.length === 0) {
        Swal.fire({
            target: dom.filterSection,
            title: 'No Questions Selected',
            text: 'Please apply filters to select questions before downloading.',
            icon: 'info'
        });
        return;
    }

    try {
        const jsonString = JSON.stringify(questions, null, 2);
        const blob = new Blob([jsonString], { type: 'application/json' });
        const url = URL.createObjectURL(blob);

        const a = document.createElement('a');
        a.href = url;
        a.download = 'Quiz_LM_Questions.json';
        document.body.appendChild(a);
        a.click();

        document.body.removeChild(a);
        URL.revokeObjectURL(url);

    } catch (error) {
        console.error("Error generating JSON file:", error);
        Swal.fire({
            target: dom.filterSection,
            title: 'Error',
            text: `An unexpected error occurred while generating the JSON file: ${error.message}`,
            icon: 'error'
        });
    }
}


// --- DROPDOWN & SUMMARY BAR UI ---

function toggleMultiSelectDropdown(filterKey, forceClose = false) {
    const dropdown = dom.filterElements[filterKey]?.dropdown;
    if (!dropdown) return;
    
    const isVisible = dropdown.style.display === 'flex';
    
    config.filterKeys.forEach(key => {
        if (key !== filterKey) {
            const otherDropdown = dom.filterElements[key]?.dropdown;
            if (otherDropdown) otherDropdown.style.display = 'none';
        }
    });
    
    if (forceClose || isVisible) {
        dropdown.style.display = 'none';
    } else {
        dropdown.style.display = 'flex';
        dom.filterElements[filterKey].searchInput?.focus();
    }
}

function filterMultiSelectList(filterKey) {
    const elements = dom.filterElements[filterKey];
    if (!elements || !elements.searchInput || !elements.list) return;

    const searchTerm = elements.searchInput.value;
    const fuse = state.fuseIndexes[filterKey];
    
    if (!searchTerm.trim() || !fuse) {
        elements.list.querySelectorAll('.multiselect-item').forEach(label => {
            label.style.display = 'flex';
        });
        return;
    }
    
    const results = fuse.search(searchTerm).map(result => result.item);
    const resultSet = new Set(results);

    elements.list.querySelectorAll('.multiselect-item').forEach(label => {
        const itemValue = label.querySelector('input[type="checkbox"]').value;
        label.style.display = resultSet.has(itemValue) ? 'flex' : 'none';
    });
}

function updateMultiSelectButtonText(filterKey) {
    const toggleBtn = dom.filterElements[filterKey]?.toggleBtn;
    if (!toggleBtn || toggleBtn.disabled) return;

    const selected = state.selectedFilters[filterKey] || [];
    const count = selected.length;
    const labelText = dom.filterElements[filterKey].container.previousElementSibling.textContent;

    if (count === 0) {
        toggleBtn.textContent = `Select ${labelText.endsWith('s') ? labelText : labelText + 's'}`;
    } else if (count === 1) {
        toggleBtn.textContent = selected[0];
    } else {
        toggleBtn.textContent = `${count} ${labelText} Selected`;
    }
}

function updateActiveFiltersSummaryBar() {
    dom.activeFiltersSummaryBar.innerHTML = '';
    let totalSelected = 0;
    config.filterKeys.forEach(key => {
        (state.selectedFilters[key] || []).forEach(value => {
            totalSelected++;
            const tag = document.createElement('span');
            tag.className = 'filter-tag';
            tag.textContent = value;
            
            const closeBtn = document.createElement('button');
            closeBtn.className = 'tag-close-btn';
            closeBtn.innerHTML = '&times;';
            closeBtn.setAttribute('aria-label', `Remove ${value} filter`);
            closeBtn.onclick = () => handleSelectionChange(key, value);
            
            tag.appendChild(closeBtn);
            dom.activeFiltersSummaryBar.appendChild(tag);
        });
    });
    dom.activeFiltersSummaryBarContainer.style.display = totalSelected > 0 ? 'block' : 'none';
}