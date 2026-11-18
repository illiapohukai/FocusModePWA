/**
 * Physical module: src/core/bootstrap.js
 */

if(localStorage.getItem('focus-mode-language')==='ru')localStorage.setItem('focus-mode-language','de');

const I18N={en:{"Intro": "Intro", "Problem": "Problem", "Loesung": "Solution", "Workflow": "How it works", "Kernfunktion": "Core feature", "Motivation": "Motivation", "Überblick": "Overview", "Live testen": "Live demo", "01 / INTRO": "01 / INTRO", "Trainiere deine Aufmerksamkeit. Nicht deine Ablenkung.": "Train your attention. Not your distraction.", "Ein interaktives Konzept für nachhaltige Konzentration, digitale Selbstkontrolle und messbaren Fortschritt.": "An interactive concept for sustainable focus, digital self-control, and measurable progress.", "Konzept entdecken": "Explore the concept", "Prototyp testen": "Test the prototype", "Kapitel": "Chapters", "Prototyp": "Prototype", "Mission": "Mission", "02 / PROBLEM": "02 / PROBLEM", "Dein Smartphone gewinnt zu oft.": "Your smartphone wins too often.", "Der Wechsel von Arbeit zu Ablenkung dauert nur Sekunden. Focus Mode+ macht diesen Moment sichtbar und setzt genau dort eine bewusste Barriere.": "It takes only seconds to switch from work to distraction. Focus Mode+ makes that moment visible and places a conscious barrier exactly where it matters.", "2 Sekunden": "2 seconds", "bis zur nächsten Ablenkung": "until the next distraction", "Kein Widerstand": "No resistance", "zwischen Fokus und Feed": "between focus and the feed", "03 / LOESUNG": "03 / SOLUTION", "Willenskraft wird zum System.": "Willpower becomes a system.", "Fokus-Sitzungen, Verhaltensdaten und adaptive Hürden werden zu einem persönlichen Training für Aufmerksamkeit.": "Focus sessions, behavioral data, and adaptive barriers become personal training for your attention.", "Adaptive Sitzungen": "Adaptive sessions", "Digitale Barrieren": "Digital barriers", "Messbarer Fortschritt": "Measurable progress", "04 / WORKFLOW": "04 / HOW IT WORKS", "Starten. Erkennen. Dranbleiben.": "Start. Recognize. Stay focused.", "Eine Sitzung beginnt bewusst, kritische Wechsel werden erkannt und jede fokussierte Minute fließt in deinen Fortschritt ein.": "Every session starts intentionally, critical switches are detected, and every focused minute contributes to your progress.", "Sitzung starten": "Start a session", "Ablenkung erkennen": "Detect distraction", "Fortschritt sichern": "Save progress", "05 / KERNFUNKTION": "05 / CORE FEATURE", "Die Pause vor der Ablenkung.": "The pause before distraction.", "Eine klare Entscheidungsebene unterbricht den Automatismus, ohne aggressives Lock-in.": "A clear decision layer interrupts the automatic impulse without aggressive lock-in.", "Barriere anzeigen ↗": "Show the barrier ↗", "06 / MOTIVATION": "06 / MOTIVATION", "Fortschritt, den du sehen kannst.": "Progress you can see.", "Focus Score, Streaks, Erfolge und Ranglisten übersetzen Konzentration in verständliche Signale.": "Focus Score, streaks, achievements, and leaderboards turn concentration into clear signals.", "Tage Streak": "day streak", "Fokuszeit": "Focus time", "07 / PREMIUM": "07 / PREMIUM", "Leistung kann Premium freischalten.": "Performance can unlock Premium.", "Premium ist als Abo verfügbar, kann aber auch durch echte Fokuszeit verdient werden.": "Premium is available as a subscription, but it can also be earned through real focus time.", "/ Monat": "/ month", "Oder durch aktive Fokuszeit freischalten.": "Or unlock it through active focus time.", "08 / AI COACH": "08 / AI COACH", "Aus Daten werden Empfehlungen.": "Data becomes guidance.", "Der AI Coach erkennt Muster und formuliert konkrete Vorschläge für bessere Routinen.": "The AI Coach identifies patterns and turns them into practical recommendations for better routines.", "AI Coach öffnen ↗": "Open AI Coach ↗", "09 / VISION": "09 / VISION", "Ein Fokus-System über alle Geräte.": "One focus system across every device.", "Der nächste Schritt ist eine nahtlose Verbindung zwischen Smartphone, Mac und PC.": "The next step is a seamless connection across smartphone, Mac, and PC.", "App selbst testen": "Try the app", "Nach oben": "Back to top", "Willkommen bei": "Welcome to", "Heute gesammelt": "Collected today", "Minuten": "minutes", "Status": "Status", "Bereit": "Ready", "BEREIT": "READY", "Fokus-Serie": "Focus streak", "Diese Woche": "This week", "Verhaltens-Schutz aktiv": "Behavior protection active", "Digitale Barrieren greifen sofort": "Digital barriers activate instantly", "Fokus starten": "Start focus", "Lernmodus": "Study mode", "Fokus aktiv": "Focus active", "Sitzungsdauer": "Session duration", "Intensität": "Intensity", "Mittel": "Medium", "App-Blockierung": "App blocking", "Aktiviert": "Enabled", "Ablenkung": "Distraction", "Beenden": "End session", "Fortschritt": "Progress", "Täglicher Fokus": "Daily focus", "Wochenübersicht": "Weekly overview", "Soll: 60 min/Tag": "Goal: 60 min/day", "Ablenkungen": "Distractions", "1 blockiert": "1 blocked", "Rangliste & Erfolge": "Leaderboard & achievements", "Zurück zum Home": "Back to Home", "Serie": "Streak", "Tage": "days", "Top Fokus-User": "Top focus users", "👤 Du": "👤 You", "Erfolge": "Achievements", "Erste Stunde": "First hour", "60 Min. erreicht": "60 min reached", "Fokus Starter": "Focus starter", "5 Tage aktiv": "5 active days", "Meister": "Master", "100h Fokus": "100h focus", "Unaufhaltsam": "Unstoppable", "30 Tage Streak": "30-day streak", "Konfiguration": "Configuration", "Einstellungen": "Settings", "Fokus-Intensität": "Focus intensity", "Premium aktivieren": "Activate Premium", "Schalte alle Pro-Features & AI-Analysen frei.": "Unlock all Pro features and AI insights.", "Zurück": "Back", "Das Upgrade für deine Aufmerksamkeit": "The upgrade for your attention", "Detaillierte Langzeitstatistiken": "Detailed long-term statistics", "Verhaltensanalyse auf Basis künstlicher Intelligenz": "AI-powered behavioral analysis", "Erweiterte Blockierungs-Modi": "Advanced blocking modes", "Premium abonnieren": "Subscribe to Premium", "Oder kostenlos": "Or for free", "Premium durch Leistung freischalten": "Unlock Premium through performance", "1 Tag Premium": "1 day of Premium", "Für 100 Min. Fokuszeit": "For 100 min of focus time", "Einlösen": "Redeem", "3 Tage Premium": "3 days of Premium", "Benötigt 10h Fokuszeit": "Requires 10h focus time", "7 Tage Premium": "7 days of Premium", "Benötigt 50h Fokuszeit": "Requires 50h focus time", "Zurück zum Dashboard": "Back to dashboard", "Hallo! Ich bin dein AI Coach. Ich scanne, überprüfe und analysiere deine Fokus-Muster, um dir das beste Verhaltenstraining zu bieten.": "Hi! I am your AI Coach. I analyze your focus patterns to provide the most effective attention training.", "Premium erforderlich": "Premium required", "Um diese personalisierte Analyse-Funktion zu nutzen, aktiviere bitte dein Premium-Abonnement.": "Activate Premium to use this personalized analysis feature.", "Zu Premium wechseln": "Go to Premium", "Willkommen im Premium-Modus! Ich habe deine bisherigen Sitzungen analysiert. Wie kann ich dir heute helfen?": "Welcome to Premium! I have analyzed your previous sessions. How can I help you today?", "Was kannst du mir empfehlen?": "What would you recommend?", "Ablenkung erkannt": "Distraction detected", "Möchtest du wirklich die Fokus-Sitzung verlassen und deinen Streak verlieren?": "Do you really want to leave the focus session and lose your streak?", "Zurück zum Fokus": "Return to focus", "Trotzdem verlassen": "Leave anyway", "Belohnung freigeschaltet": "Reward unlocked", "Premium für 1 Tag": "Premium for 1 day", "Glückwunsch! Du hast über 100 Minuten fokussiert gearbeitet. Genieße alle Premium-Features.": "Congratulations! You focused for more than 100 minutes. Enjoy all Premium features.", "Scrollen, um den Prototyp zu steuern": "Scroll to control the prototype", "DISTRACTION": "DISTRACTION", "Basierend auf deinen Daten habe ich folgendes Muster erkannt:": "Based on your data, I identified the following pattern:", "Du verlierst deinen Fokus meistens zwischen 18:00 und 20:00 Uhr.": "You usually lose focus between 6:00 PM and 8:00 PM.", "Fokus-Niveau im Tagesverlauf": "Focus level throughout the day", "Deine erfolgreichsten Sitzungen dauern exakt": "Your most successful sessions last exactly", "Mein Tipp:": "My tip:", "Versuche kürzere Pausen nach intensiven Lernphasen einzuplanen und reduziere die Sitzungsdauer am Abend.": "Try scheduling shorter breaks after intense study periods and reduce session length in the evening."},ru:{"Intro": "Введение"}};let currentLanguage='de',hasEverUsedEn=false;function normalizeText(v){return v.replace(/\s+/g,' ').trim()}function translateNode(node,lang){if(node.nodeType!==Node.TEXT_NODE)return;if(lang==='de'&&!hasEverUsedEn&&!node.__deOriginal)return;const clean=normalizeText(node.nodeValue);if(!clean)return;if(!node.__deOriginal)node.__deOriginal=clean;const original=node.__deOriginal;const value=lang==='de'?original:(I18N[lang][original]||original);const lead=node.nodeValue.match(/^\s*/)[0],trail=node.nodeValue.match(/\s*$/)[0];const nextVal=lead+value+trail;if(node.nodeValue!==nextVal)node.nodeValue=nextVal}function translateTree(root,lang){if(!root)return;if(lang==='en')hasEverUsedEn=true;if(lang==='de'&&!hasEverUsedEn)return;const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT,{acceptNode(n){return ['SCRIPT','STYLE','SVG','PATH','CIRCLE','LINE','RECT','POLYGON','POLYLINE'].includes(n.parentElement?.tagName?.toUpperCase?.())?NodeFilter.FILTER_REJECT:NodeFilter.FILTER_ACCEPT}});const nodes=[];while(walker.nextNode())nodes.push(walker.currentNode);nodes.forEach(n=>translateNode(n,lang));const scope=typeof root.querySelectorAll==='function'?root:document;const titleEls=[...(root.nodeType===1&&root.hasAttribute?.('title')?[root]:[]),...scope.querySelectorAll('[title]')];titleEls.forEach(el=>{if(!el.dataset.deTitle)el.dataset.deTitle=el.title;const nextTitle=lang==='de'?el.dataset.deTitle:(I18N[lang][el.dataset.deTitle]||el.dataset.deTitle);if(el.title!==nextTitle)el.title=nextTitle});const ariaEls=[...(root.nodeType===1&&root.hasAttribute?.('aria-label')?[root]:[]),...scope.querySelectorAll('[aria-label]')];ariaEls.forEach(el=>{if(!el.dataset.deAriaLabel)el.dataset.deAriaLabel=el.getAttribute('aria-label');const orig=el.dataset.deAriaLabel;const nextAria=lang==='de'?orig:(I18N[lang][orig]||orig);if(el.getAttribute('aria-label')!==nextAria)el.setAttribute('aria-label',nextAria)})}function setLanguage(lang,save=true){if(!['de','en'].includes(lang))lang='de';if(lang==='en')hasEverUsedEn=true;currentLanguage=lang;document.documentElement.lang=lang;translateTree(document.body,lang);const langCodeEl=document.getElementById('langCode');if(langCodeEl)langCodeEl.textContent=lang.toUpperCase();document.querySelectorAll('.lang-option').forEach(b=>b.classList.toggle('active',b.dataset.lang===lang));document.title='v17.6 Navigation, Insights & League Polish';if(save)window.FocusModeApp.services.storage.setText('focus-mode-language',lang);if(typeof window.refreshDataManagementUI==='function')window.refreshDataManagementUI();if(typeof window.renderFocusChoiceV116==='function')window.renderFocusChoiceV116();if(typeof window.refreshHomeStats==='function')window.refreshHomeStats();document.getElementById('langPicker')?.classList.remove('open');document.getElementById('langToggle')?.setAttribute('aria-expanded','false')}function detectLanguage(){const saved=window.FocusModeApp.services.storage.getText('focus-mode-language');if(saved)return saved;const langs=navigator.languages||[navigator.language||'de'];for(const item of langs){const code=item.toLowerCase().split('-')[0];if(['de','en'].includes(code))return code}return'en'}document.title='v17.6 Navigation, Insights & League Polish';document.addEventListener('DOMContentLoaded',()=>{const picker=document.getElementById('langPicker'),toggle=document.getElementById('langToggle');toggle.addEventListener('click',e=>{e.stopPropagation();picker.classList.toggle('open');toggle.setAttribute('aria-expanded',picker.classList.contains('open'))});document.querySelectorAll('.lang-option').forEach(b=>b.addEventListener('click',()=>setLanguage(b.dataset.lang)));document.addEventListener('click',()=>picker.classList.remove('open'));setLanguage(detectLanguage(),false);new MutationObserver(records=>{if(currentLanguage==='de'&&!hasEverUsedEn)return;for(const record of records)for(const node of record.addedNodes){if(node.nodeType===Node.TEXT_NODE)translateNode(node,currentLanguage);else if(node.nodeType===Node.ELEMENT_NODE&&node.tagName?.toUpperCase?.()!=='SVG')translateTree(node,currentLanguage)}}).observe(document.body,{childList:true,subtree:true});lucide.createIcons()});

Object.assign(I18N.en,{"Lücke": "Product Gap", "Modi": "Modes", "Mission": "Mission", "Fokus": "Focus", "Anpassung": "Adaptation", "Fortschritt": "Progress", "Coach": "Coach", "Vision": "Vision", "Aus „Ich sollte anfangen“ wird echte Fokuszeit.": "Turn “I should start” into real focus.", "Focus Mode+ hilft dir, wichtige Aufgaben zu beginnen, digitale Ablenkungen zu blockieren und Schritt für Schritt dranzubleiben.": "Focus Mode+ helps you begin important tasks, block digital distractions, and keep moving forward one focused step at at a time.", "2 Fokusmodi": "2 focus modes", "1 klarer nächster Schritt": "1 clear next step", "0 unnötige Ablenkungen": "0 unnecessary distractions", "Das Problem ist nicht nur die Ablenkung.": "Distraction is only part of the problem.", "Viele Aufgaben scheitern schon vor der ersten Fokusminute: Das Ziel wirkt zu groß, der nächste Schritt ist unklar und das Smartphone bietet den einfacheren Ausweg.": "Many tasks fail before the first focused minute: the goal feels too large, the next step is unclear, and the smartphone offers an easier escape.", "Kein klarer Start": "No clear starting point", "Die Aufgabe bleibt abstrakt": "The task remains abstract", "Sofortige Ablenkung": "Instant distraction", "Der Feed ist nur einen Klick entfernt": "The feed is only one tap away", "03 / LÜCKE": "03 / PRODUCT GAP", "Ein Timer kennt dein Ziel nicht. Ein Plan schützt deinen Fokus nicht.": "A timer does not understand your goal. A plan does not protect your focus.", "Timer messen Zeit. App-Blocker sperren Anwendungen. Planungstools erstellen Listen. Focus Mode+ verbindet Ziel, nächsten Schritt, geschützte Ausführung und Anpassung in einem Ablauf.": "Timers measure time. App blockers restrict applications. Planning tools create lists. Focus Mode+ connects the goal, the next step, protected execution, and adaptation in one flow.", "Ziel statt nur Zeit": "Goals, not only time", "Ausführung statt nur Planung": "Execution, not only planning", "Anpassung statt statischer Liste": "Adaptation, not static lists", "04 / ZWEI MODI": "04 / TWO MODES", "Sofort fokussieren. Oder eine Mission verfolgen.": "Focus immediately. Or follow a mission.", "Nicht jede Aufgabe braucht Planung. Deshalb entscheidet der Nutzer selbst, wie viel Unterstützung gerade sinnvoll ist.": "Not every task requires a plan. Users decide how much support is useful in the current situation.", "Kurzer Fokus": "Quick Focus", "25 Minuten · Apps blockieren · direkt beginnen": "25 minutes · block distracting apps · start immediately", "Aufgabe strukturieren · Fokus-Schritte planen · Fortschritt anpassen": "Structure the task · plan focus steps · adapt progress", "Aus einer großen Aufgabe wird ein machbarer nächster Schritt.": "Turn a large task into one achievable next step.", "Der Nutzer beschreibt, was er schaffen möchte, fügt bei Bedarf Material hinzu und wählt den Kontext. Focus Mode+ erkennt Ziel und Frist, schätzt den Aufwand und erstellt realistische Fokus-Schritte.": "Users describe what they want to complete, optionally add relevant material, and select the context. Focus Mode+ identifies the goal and deadline, estimates the effort, and creates realistic focus steps.", "Prüfung": "Exam", "Aufgabe": "Task", "Projekt": "Project", "Eigene Mission": "Custom mission", "Planen. Fokussieren. Reflektieren. Anpassen.": "Plan. Focus. Reflect. Adapt.", "Eine Focus Mission begleitet die Aufgabe nicht nur bis zum Plan, sondern durch die tatsächliche Ausführung und den nächsten realistischen Schritt.": "A Focus Mission guides the task beyond planning, through actual execution and toward the next realistic step.", "Ziel verstehen": "Understand the goal", "Aufgabe, Material und Frist erfassen": "Capture the task, material, and deadline", "Nächsten Schritt wählen": "Choose the next step", "Eine realistische Sitzung statt eines überwältigenden Plans": "One realistic session instead of one overwhelming plan", "Geschützt arbeiten": "Work in a protected environment", "Ablenkende Apps blockieren": "Block distracting apps", "Ergebnis einschätzen": "Reflect on the result", "Verständnis und Sicherheit bewerten": "Assess understanding and confidence", "Mission anpassen": "Adapt the mission", "Dauer und nächste Schritte aktualisieren": "Update duration and next steps", "07 / GESCHÜTZTER FOKUS": "07 / PROTECTED FOCUS", "Der Plan zählt erst, wenn die Arbeit beginnt.": "A plan only matters when the work begins.", "Während der Sitzung schützt Focus Mode+ die gewählte Arbeitsumgebung. Beim Versuch, in eine Ablenkung zu wechseln, unterbricht eine bewusste Barriere den Automatismus.": "During the session, Focus Mode+ protects the selected working environment. When the user attempts to switch to a distraction, a conscious barrier interrupts the automatic impulse.", "Benötigte Werkzeuge bleiben verfügbar": "Required tools remain available", "Ablenkende Apps werden blockiert": "Distracting apps are blocked", "08 / ANPASSUNG": "08 / ADAPTATION", "Die Mission lernt aus jeder Sitzung.": "The mission learns from every session.", "Nach der Sitzung bewertet der Nutzer, wie sicher er sich mit dem Ergebnis fühlt. Focus Mode+ vergleicht Feedback und Planung, schätzt das persönliche Tempo neu und passt die nächste Sitzung an.": "After each session, users assess how confident they feel about the result. Focus Mode+ compares feedback with the original plan, reassesses the personal pace, and adapts the next session.", "Verständnis": "Understanding", "Minuten Praxis": "Practice minutes", "angepasster Schritt": "adapted step", "09 / FORTSCHRITT": "09 / PROGRESS", "Zeit zeigt deinen Einsatz. Missionen zeigen deinen Fortschritt.": "Time shows your effort. Missions show your progress.", "Fokuszeit bleibt eine reale persönliche Kennzahl. Mission-Fortschritt zeigt die Richtung. Die Plan-Serie belohnt den eigenen Rhythmus, nicht tägliche Aktivität um jeden Preis.": "Focus time remains a real personal metric. Mission progress shows direction. The Plan Streak rewards the user’s own rhythm, not daily activity at any cost.", "Plan-Serie": "Plan Streak", "Wochenliga": "Weekly League", "10 / LANGZEIT-COACHING": "10 / LONG-TERM COACHING", "Aus Fokusdaten werden bessere Routinen.": "Turn focus data into better routines.", "Während Focus Missions einzelne Aufgaben begleiten, erkennt der optionale AI Coach langfristige Muster: Wann sinkt die Konzentration? Welche Sitzungsdauer funktioniert? Welche Routine sollte angepasst werden?": "While Focus Missions guide individual tasks, the optional AI Coach identifies long-term patterns: When does concentration decrease? Which session length works best? Which routine should be adjusted?", "Erweiterte Langzeitstatistiken, Verhaltensanalyse und strengere Blockierungsmodi können Teil von Focus Mode+ Premium werden.": "Advanced long-term statistics, behavioral analysis, and stricter blocking modes may become part of Focus Mode+ Premium.", "AI Coach öffnen ↗": "Open AI Coach ↗", "11 / NEXT STEP": "11 / NEXT STEP", "Ein starker Prototyp. Jetzt braucht es echte Nutzung.": "A strong prototype. Now it needs real usage.", "Focus Mode+ ist aktuell ein interaktiver Concept Prototype. Der nächste Schritt ist nicht noch eine Funktion, sondern die Validierung mit echten Aufgaben und echten Nutzern.": "Focus Mode+ is currently an interactive concept prototype. The next step is not another feature, but validation with real tasks and real users.", "Verstehen Schüler den Mehrwert?": "Do students understand the value?", "Problem und Zielgruppe prüfen": "Test the problem and target group", "Kehren sie zurück?": "Do they return?", "Mehrere Sitzungen statt einmaliger Neugier": "Multiple sessions instead of one-time curiosity", "Verbessert der Ablauf die Ausführung?": "Does the flow improve execution?", "Planung und Schutz gemeinsam testen": "Test planning and protection together", "Langfristige Vision": "Long-term vision", "Ein Fokus-System für selbstständige Arbeit über Smartphone, Mac und PC.": "One focus system for independent work across smartphones, Macs, and PCs.", "Prototyp selbst testen": "Test the prototype", "Nach oben": "Back to top", "01 / INTRO": "01 / INTRO", "02 / PROBLEM": "02 / PROBLEM", "05 / FOCUS MISSION": "05 / FOCUS MISSION", "06 / WORKFLOW": "06 / WORKFLOW", "Konzept entdecken": "Explore the concept", "Prototyp testen": "Test the prototype", "Barriere anzeigen ↗": "Show the barrier ↗", "Trainiere deine Aufmerksamkeit. Nicht deine Ablenkung.": "Train your attention. Not your distraction.", "Daten & Backup": "Data & Backup", "Lokale Daten sichern, importieren oder zurücksetzen": "Export, import, or reset local data", "Lokale Datenstabilität": "Local Data Stability", "Übersicht deiner lokal gespeicherten Fokus-Daten.": "Overview of your locally stored focus data.", "Aktive Missionen": "Active Missions", "Gespeicherte Sitzungen": "Saved Sessions", "Aktive Sitzung": "Active Session", "Datenstatus": "Data Status", "Letztes Backup": "Last Backup", "Aktionen": "Actions", "Daten exportieren": "Export Data", "JSON-Backup herunterladen": "Download JSON backup", "Daten importieren": "Import Data", "Aus JSON-Datei wiederherstellen": "Restore from JSON file", "Letztes Backup wiederherstellen": "Restore Last Backup", "Vorherigen Zustand laden": "Load previous state", "Lokale Daten zurücksetzen": "Reset Local Data", "Missionen & Sitzungen löschen (Backup bleibt erhalten)": "Clear missions & sessions (keeps last backup)", "Import-Vorschau": "Import Preview", "Dies ersetzt die aktuellen lokalen App-Daten. Ein Backup der aktuellen Daten wird automatisch erstellt.": "This will replace local app data. A backup of the current data will be created automatically.", "Import bestätigen": "Confirm Import", "Abbrechen": "Cancel", "Lokale Daten wirklich zurücksetzen?": "Reset local data?", "Alle lokalen Missionen und Sitzungen werden gelöscht. Deine Spracheinstellung und das letzte Backup bleiben erhalten.": "All local missions and sessions will be cleared. Your language preference and last backup will be preserved.", "Jetzt zurücksetzen": "Reset Now", "Start": "Home", "Home": "Home", "Missionen": "Missions", "Verlauf": "History", "Profil": "Profile", "Hauptnavigation": "Main navigation", "Missionen verwalten": "Manage Missions", "Aktive, archivierte und primäre Missionen": "Active, archived and primary Missions", "2 aktive Missionen erreicht": "2 active Missions reached", "Deine Missionen": "Your Missions", "Aktiv": "Active", "Abgeschlossen": "Completed", "Archiv": "Archive", "Keine Missionen": "No Missions", "In diesem Bereich gibt es noch keine Mission.": "There are no missions in this section yet.", "Hauptmission": "Primary Mission", "HAUPTMISSION": "PRIMARY MISSION", "AKTIVE MISSION": "ACTIVE MISSION", "NÄCHSTER SCHRITT": "NEXT STEP", "Schritt starten": "Start step", "Was möchtest du jetzt erledigen?": "What would you like to get done now?", "HEUTE": "TODAY", "FOKUS": "FOCUS", "SITZUNGEN": "SESSIONS", "SITZUNG": "SESSION", "SERIE": "STREAK", "AKTIVITÄT": "ACTIVITY", "Alle anzeigen": "View all", "Noch keine Aktivität": "No activity yet", "Abgeschlossene und teilweise Fokus-Sitzungen erscheinen hier chronologisch.": "Completed and partial focus sessions appear here chronologically.", "Als Hauptmission setzen": "Set as Primary Mission", "Mission öffnen": "Open Mission", "Archivieren": "Archive", "Wieder aktivieren": "Reactivate", "Wieder öffnen": "Reopen", "Als abgeschlossen markieren": "Mark as completed", "Endgültig löschen": "Delete permanently", "Sitzungsverlauf": "Session History", "Deine Fokuszeit": "Your Focus Time", "Heute": "Today", "Alle Sitzungen": "All Sessions", "Sprache": "Language", "Oberflächensprache wählen": "Select interface language", "Was möchtest du fokussieren?": "What would you like to focus on?", "Kurzen Fokus starten": "Start Quick Focus", "Aufgabe und Dauer selbst festlegen": "Set your own task and duration", "Deine Focus Missions": "Your Focus Missions", "+ Neue Focus Mission": "+ New Focus Mission", "Schließen": "Close", "Statistiken": "Statistics", "Fokusschutz aktiv": "Focus Protection active", "Fokus-Schutz aktiv": "Focus Protection active", "Tippen zum Anpassen": "Tap to adjust", "Was lenkt dich ab?": "What distracts you?", "Fertig": "Done", "Auswahl speichern": "Done"}); window.FocusModeApp.services.storage.getText("focus-mode-language")==="ru"&&window.FocusModeApp.services.storage.setText("focus-mode-language","de");

/** Virtual module: src/core/legacy-cleanup-gate.js */
(function(g){
 'use strict';
 const A=g.FocusModeApp;
 if(!A?.core?.registerModule)throw new Error('Architecture Shell required');
 if(A.core.hasModule('core.legacyCleanupGate'))return;
 const candidates=Object.freeze([
  'legacyV10_startQuickFocus_1','legacyV10_prepareLearningSession_1','legacyV10_prepareLearningSession_2',
  'legacyV10_prepareLearningSession_3','legacyV10_completeLearningSession_1','legacyV10_completeLearningSession_2',
  'legacyV10_startFocusSession_1','legacyV10_stopFocusSession_1','legacyV10_currentUcSession_1',
  'legacyV10_showUcTask_1','legacyV10_startConfiguredQuickFocus_1','legacyUpdateRealWeekV81'
 ]);
 const requiredModules=Object.freeze(['repositories.sessions','core.runtimeOwnership','core.sessionRecovery','features.missionFlow']);
 const publicEntries=Object.freeze(['startSession','checkpointActiveSession','endSessionByUser','resumeRecoveredSession','confirmAIPlan','prepareLearningSession','showPlanUpdate','renderAIPlan']);
 function inspect(){
  const modules=requiredModules.map(name=>({name,available:A.core.hasModule(name)}));
  const entries=publicEntries.map(name=>({name,available:typeof g[name]==='function'}));
  const legacy=candidates.map(name=>({name,present:typeof g[name]==='function'}));
  const blockers=[];
  modules.filter(x=>!x.available).forEach(x=>blockers.push('missing_module:'+x.name));
  entries.filter(x=>!x.available).forEach(x=>blockers.push('missing_entry:'+x.name));
  return Object.freeze({version:'13.2.4',safeForPhysicalDeletion:blockers.length===0,modules,entries,legacy,blockers});
 }
 function assertReady(){const report=inspect();if(!report.safeForPhysicalDeletion)console.error('[LegacyCleanupGate]',{event:'cleanup_blocked',blockers:report.blockers});else console.info('[LegacyCleanupGate]',{event:'legacy_pruning_verified',remainingLegacyCandidates:report.legacy.filter(x=>x.present).length});return report}
 const gate=Object.freeze({version:'13.2.4',candidates,requiredModules,publicEntries,inspect,assertReady});
 A.core.legacyCleanupGate=A.core.registerModule('core.legacyCleanupGate',gate,{virtualPath:'src/core/legacy-cleanup-gate.js',version:'13.2.4',status:'post-pruning-audit'});
  A.version='v17.6 Navigation, Insights & League Polish';A.name='Home System';A.core.lifecycle.phase='home-system-ready';A.core.lifecycle.ready=true;
  A.compatibility.legacyCleanupGate=Object.freeze({version:'13.2.4',physicalDeletions:12,storageKeysRenamed:0,formatsChanged:0,uiChanged:false});
  document.addEventListener('DOMContentLoaded',()=>setTimeout(assertReady,0),{once:true});
})(window);

(function(g){
 'use strict';
 const A=g.FocusModeApp;
 if(!A?.core?.registerModule)throw new Error('Architecture Shell required');
 if(A.core.hasModule('core.architectureStabilization'))return;
 const requiredModules=Object.freeze(['services.storage','repositories.sessions','repositories.missions','core.runtimeOwnership','core.sessionRecovery','features.missionFlow']);
 const requiredStorageAPI=Object.freeze(['getText','setText','getJSON','setJSON','remove','has','backup']);
 const requiredEntries=Object.freeze(['setAppScreen','startSession','endSessionByUser','resumeRecoveredSession','confirmAIPlan','prepareLearningSession','showPlanUpdate','renderAIPlan']);
 function inspect(){
  const modules=requiredModules.map(name=>({name,available:A.core.hasModule(name)}));
  const storage=requiredStorageAPI.map(name=>({name,available:typeof A.services.storage?.[name]==='function'}));
  const entries=requiredEntries.map(name=>({name,available:typeof g[name]==='function'}));
  const blockers=[...modules.filter(x=>!x.available).map(x=>'module:'+x.name),...storage.filter(x=>!x.available).map(x=>'storage:'+x.name),...entries.filter(x=>!x.available).map(x=>'entry:'+x.name)];
  return Object.freeze({version:'17.3',stable:blockers.length===0,modules,storage,entries,blockers});
 }
 function assertStable(){const report=inspect();(report.stable?console.info:console.error)('[ArchitectureStabilization]',{event:report.stable?'architecture_stable':'architecture_blocked',blockers:report.blockers});return report}
 const api=Object.freeze({version:'17.3',inspect,assertStable});
 A.core.architectureStabilization=A.core.registerModule('core.architectureStabilization',api,{virtualPath:'src/core/architecture-stabilization.js',version:'17.3',status:'stable-baseline'});
 A.version='v17.6 Navigation, Insights & League Polish';A.name='Home System';A.core.lifecycle.phase='home-system-ready';A.core.lifecycle.ready=true;
 A.compatibility.architectureStabilization=Object.freeze({version:'17.3',storageRegressionFixed:true,storageKeysRenamed:0,formatsChanged:0,uiChanged:false});
 if(typeof g.installNavApplyHookV17==='function'){g.installNavApplyHookV17()}
 if(!A.core.hasModule('core.navigationController')&&typeof g.navigateToRoot==='function'){
  A.core.navigationController=A.core.registerModule('core.navigationController',Object.freeze({
   version:'17.5.1',
   navigateToRoot:g.navigateToRoot,
   navigateForward:g.navigateForward,
   navigateBack:g.navigateBack,
   replaceScreen:g.replaceScreen,
   showBottomNavigation:g.showBottomNavigation,
   hideBottomNavigation:g.hideBottomNavigation,
   syncBottomNavigationForScreen:g.syncBottomNavigationForScreen,
   getState:()=>g.__navigationStateV17
  }),{virtualPath:'src/core/navigation.js',version:'17.5.1',status:'active-controller'});
 }
 document.addEventListener('DOMContentLoaded',()=>{
  setTimeout(assertStable,0);
  if(typeof g.installNavApplyHookV17==='function')g.installNavApplyHookV17();
  if(typeof g.syncBottomNavigationForScreen==='function')g.syncBottomNavigationForScreen();
  setTimeout(()=>{if(typeof g.syncBottomNavigationForScreen==='function')g.syncBottomNavigationForScreen()},120);
 },{once:true});
})(window);

(function(g){
 'use strict';
 const A=g.FocusModeApp;
 if(typeof I18N==='object'&&I18N.en){
  Object.assign(I18N.en,{
   'Offline-Modus':'Offline Mode',
   'Lokale Funktionen bleiben verfügbar. AI-Funktionen benötigen eine Internetverbindung.':'Local features remain available. AI features require an internet connection.',
   'Keine Internetverbindung':'No internet connection',
   'Die AI-Mission kann erstellt werden, sobald du wieder online bist.':'Your AI Mission can be created when you are back online.',
   'Deine Eingaben bleiben erhalten.':'Your input has been preserved.',
   'Erneut versuchen':'Try Again',
   'Zurück':'Back',
   'Ohne Anpassung fortfahren':'Continue without Adaptation',
   'Plan mit AI anpassen':'Adapt Plan with AI',
   'KEINE INTERNETVERBINDUNG':'NO INTERNET CONNECTION',
   'ANPASSUNG NICHT VERFÜGBAR':'ADAPTATION UNAVAILABLE',
   'ERGEBNIS GESPEICHERT':'RESULT SAVED',
   'Keine Internetverbindung. Dein Fokus-Ergebnis wurde lokal gespeichert und dein bisheriger Plan bleibt unverändert.':'No internet connection. Your focus session result has been saved locally and your existing plan remains unchanged.',
   'Du kannst ohne Anpassung fortfahren oder es erneut versuchen, sobald du wieder online bist.':'You can continue without Adaptation or try again when you are back online.',
   'Zum Dashboard':'Back to Dashboard',
   'Was möchtest du fokussieren?':'What would you like to focus on?',
   'Starte direkt oder arbeite an einer Mission weiter.':'Start right away or continue working on a mission.',
   'Eine Aufgabe, direkt starten.':'One task, start right away.',
   'Ein Ziel planen und in Schritte teilen.':'Plan a goal and break it into steps.',
   'BESTEHENDE MISSIONEN':'EXISTING MISSIONS',
   'Bestehende Missionen':'Existing Missions',
   'Noch keine aktive Mission.':'No active mission yet.',
   'Missionen verwalten':'Manage missions',
   'Aktiv, abgeschlossen und archiviert':'Active, completed and archived',
   'Öffnen':'Open',
   'Als Hauptmission festlegen':'Set as primary mission',
   'Umbenennen':'Rename',
   'Archivieren':'Archive',
   'Wiederherstellen':'Restore',
   'Löschen':'Delete',
   'Endgültig löschen':'Delete permanently',
   'Abbrechen':'Cancel',
   'Speichern':'Save',
   'Fokusschutz aktiv':'Focus protection active',
   'Fokusschutz aus':'Focus protection off',
   /* v17.11 Missionen and Verlauf summaries, Mission cards */
   'Focus Missions':'Focus Missions',
   'Missionssystem':'Mission system',
   'aktive Missionen':'active missions',
   'vorbereitet':'prepared',
   'Schritte erledigt':'Steps done',
   'Im Archiv':'Archived',
   'Beide Plätze belegt':'Both slots in use',
   '2 Plätze frei':'2 slots free',
   'Bis zu zwei Missionen gleichzeitig':'Up to two missions at a time',
   'Aktive Mission':'Active mission',
   'Archiviert':'Archived',
   'Plan ansehen':'View plan',
   'Fokus-Bilanz':'Focus balance',
   'gezählte Fokuszeit':'counted focus time',
   'Sitzungen':'Sessions',
   'Teilweise':'Partial',
   'Abgebrochen':'Cancelled'
  });
 }
 const ICON_SVG_PATHS=Object.freeze({
  'timer':'<line x1="10" x2="14" y1="2" y2="2"></line><line x1="12" x2="15" y1="14" y2="11"></line><circle cx="12" cy="14" r="8"></circle>',
  'clipboard-list':'<rect width="8" height="4" x="8" y="2" rx="1" ry="1"></rect><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"></path><path d="M12 11h4"></path><path d="M12 16h4"></path><path d="M8 11h.01"></path><path d="M8 16h.01"></path>',
  'sliders-horizontal':'<line x1="21" x2="14" y1="4" y2="4"></line><line x1="10" x2="3" y1="4" y2="4"></line><line x1="21" x2="12" y1="12" y2="12"></line><line x1="8" x2="3" y1="12" y2="12"></line><line x1="21" x2="16" y1="20" y2="20"></line><line x1="12" x2="3" y1="20" y2="20"></line><line x1="14" x2="14" y1="2" y2="6"></line><line x1="8" x2="8" y1="10" y2="14"></line><line x1="16" x2="16" y1="18" y2="22"></line>',
  'folder-open':'<path d="m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2"></path>',
  'arrow-up-right':'<path d="M7 7h10v10"></path><path d="M7 17 17 7"></path>',
  'star':'<polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>',
  'pencil':'<path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"></path><path d="m15 5 4 4"></path>',
  'archive':'<rect width="20" height="5" x="2" y="3" rx="1"></rect><path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8"></path><path d="M10 12h4"></path>',
  'archive-restore':'<rect width="20" height="5" x="2" y="3" rx="1"></rect><path d="M4 8v11a2 2 0 0 0 2 2h2"></path><path d="M20 8v11a2 2 0 0 1-2 2h-2"></path><path d="m9 15 3-3 3 3"></path><path d="M12 12v9"></path>',
  'rotate-ccw':'<path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path><path d="M3 3v5h5"></path>',
  'trash-2':'<path d="M3 6h18"></path><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path><line x1="10" x2="10" y1="11" y2="17"></line><line x1="14" x2="14" y1="11" y2="17"></line>',
  'x':'<path d="M18 6 6 18"></path><path d="m6 6 12 12"></path>',
  'wifi-off':'<path d="M12 20h.01"></path><path d="M8.5 16.429a5 5 0 0 1 7 0"></path><path d="M5 12.859a10 10 0 0 1 5.17-2.69"></path><path d="M19 12.859a10 10 0 0 0-2.007-1.523"></path><path d="M2 8.82a15 15 0 0 1 4.177-2.643"></path><path d="M22 8.82a15 15 0 0 0-11.288-3.764"></path><path d="m2 2 20 20"></path>',
  'wifi':'<path d="M12 20h.01"></path><path d="M2 8.82a15 15 0 0 1 20 0"></path><path d="M5 12.859a10 10 0 0 1 14 0"></path><path d="M8.5 16.429a5 5 0 0 1 7 0"></path>',
  'calendar-days':'<path d="M8 2v3"></path><path d="M16 2v3"></path><rect x="3" y="3" width="18" height="18" rx="2"></rect><path d="M3 9h18"></path><path d="M8 13h.01"></path><path d="M12 13h.01"></path><path d="M16 13h.01"></path><path d="M8 17h.01"></path><path d="M12 17h.01"></path><path d="M16 17h.01"></path>',
  'calendar-off':'<path d="M16 2v3"></path><path d="m2 2 20 20"></path><path d="M21 9h-5.5"></path><path d="M3 9h6"></path><path d="M3.586 3.586A2 2 0 0 0 3 5v14a2 2 0 0 0 2 2h14a2 2 0 0 0 1.414-.586"></path><path d="M8.656 3H19a2 2 0 0 1 2 2v10.344"></path>',
  'check':'<path d="M20 6 9 17l-5-5"></path>',
  'chevron-right':'<path d="m9 18 6-6-6-6"></path>',
  'chevron-left':'<path d="m15 18-6-6 6-6"></path>',
  'chevron-down':'<path d="m6 9 6 6 6-6"></path>',
  'arrow-right':'<path d="M5 12h14"></path><path d="m12 5 7 7-7 7"></path>',
  'arrow-left':'<path d="m12 19-7-7 7-7"></path><path d="M19 12H5"></path>',
  'plus':'<path d="M5 12h14"></path><path d="M12 5v14"></path>',
  'play':'<polygon points="6 3 20 12 6 21 6 3"></polygon>',
  'target':'<circle cx="12" cy="12" r="10"></circle><circle cx="12" cy="12" r="6"></circle><circle cx="12" cy="12" r="2"></circle>',
  'home':'<path d="M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8"></path><path d="M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>',
  'clock-3':'<circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16.5 12"></polyline>',
  'user':'<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle>',
  'more-horizontal':'<circle cx="12" cy="12" r="1"></circle><circle cx="19" cy="12" r="1"></circle><circle cx="5" cy="12" r="1"></circle>',
  'shield-check':'<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"></path><path d="m9 12 2 2 4-4"></path>',
  'shield-off':'<path d="m2 2 20 20"></path><path d="M5 5a1 1 0 0 0-1 1v7c0 5 3.5 7.5 7.67 8.94a1 1 0 0 0 .67.01c2.35-.82 4.48-1.97 5.9-3.71"></path><path d="M9.309 3.652A12.252 12.252 0 0 0 11.24 2.28a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1v7a9.784 9.784 0 0 1-.08 1.264"></path>',
  'zap':'<path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"></path>',
  'sparkles':'<path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"></path>'
 });
 g.renderFocusIconSvg=function renderFocusIconSvg(name,extraClass){
  const iconName=String(name||'circle').trim();
  const paths=ICON_SVG_PATHS[iconName]||'<circle cx="12" cy="12" r="9"></circle>';
  const cls=('lucide lucide-'+iconName+(extraClass?' '+extraClass:'')).trim();
  return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" data-lucide="'+iconName+'" class="'+cls+'" aria-hidden="true">'+paths+'</svg>';
 };
 function hydrateRemainingLucidePlaceholders(){
  if(typeof document==='undefined')return;
  document.querySelectorAll('i[data-lucide]').forEach(el=>{
   const name=el.getAttribute('data-lucide')||'circle';
   const cls=el.getAttribute('class')||'';
   const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
   svg.setAttribute('xmlns','http://www.w3.org/2000/svg');
   svg.setAttribute('viewBox','0 0 24 24');
   svg.setAttribute('fill','none');
   svg.setAttribute('stroke','currentColor');
   svg.setAttribute('stroke-width','2');
   svg.setAttribute('stroke-linecap','round');
   svg.setAttribute('stroke-linejoin','round');
   svg.setAttribute('data-lucide',name);
   svg.setAttribute('class',('lucide lucide-'+name+' '+cls).trim());
   svg.setAttribute('aria-hidden','true');
   svg.innerHTML=ICON_SVG_PATHS[name]||'<circle cx="12" cy="12" r="9"></circle>';
   el.replaceWith(svg);
  });
 }
 function ensureLucideFallback(){
  const origCreate=g.lucide&&typeof g.lucide.createIcons==='function'?g.lucide.createIcons.bind(g.lucide):null;
  g.lucide=g.lucide||{};
  g.lucide.createIcons=function(){
   try{if(origCreate&&g.lucide.icons)origCreate.apply(g.lucide,arguments);}catch(e){}
   hydrateRemainingLucidePlaceholders();
  };
 }
 ensureLucideFallback();
 function isEnLang(){
  return (typeof currentLanguage==='string'&&currentLanguage==='en')||document.documentElement.lang==='en';
 }
 /* v17.12 connection state — the single owner of online/offline communication.
    Browser connectivity events only (no polling). States of the notice card:
    'offline' (expanded; dismissible, collapses by itself after a while), 'online' (short reconnect
    confirmation) and 'hidden'. One timer at most, for the visible card only.
    The compact offline state is the Home readiness pill (html.fm-is-offline without an open notice).
    Dismissing affects the current message only; the next real offline transition shows it again. */
 const CONNECTION_COPY=Object.freeze({
  de:{offlineTitle:'Du bist offline',offlineSub:'Gespeicherte Inhalte bleiben verfügbar. AI-Funktionen benötigen eine Verbindung.',onlineTitle:'Wieder online',close:'Hinweis schließen'},
  en:{offlineTitle:'You are offline',offlineSub:'Saved content stays available. AI features need a connection.',onlineTitle:'Back online',close:'Dismiss notice'}
 });
 if(typeof I18N==='object'&&I18N.en){
  Object.assign(I18N.en,{
   [CONNECTION_COPY.de.offlineTitle]:CONNECTION_COPY.en.offlineTitle,
   [CONNECTION_COPY.de.offlineSub]:CONNECTION_COPY.en.offlineSub,
   [CONNECTION_COPY.de.onlineTitle]:CONNECTION_COPY.en.onlineTitle,
   [CONNECTION_COPY.de.close]:CONNECTION_COPY.en.close,
   'Ohne Deadline':'No deadline',
   'Schritte':'steps'
  });
 }
 const RECONNECT_VISIBLE_MS=2600;
 // The expanded offline card collapses to the compact state on its own after a while, so it never
 // keeps covering a screen's top controls; the offline state itself stays until 'online'.
 const OFFLINE_EXPANDED_MS=8000;
 const connection={initialized:false,offline:false,notice:'hidden',noticeTimer:null};
 function connectionCopy(){return CONNECTION_COPY[isEnLang()?'en':'de']}
 function setConnectionText(el,text){
  if(!el)return;
  if(el.textContent!==text)el.textContent=text;
  if(el.firstChild)el.firstChild.__deOriginal=CONNECTION_COPY.de[el.dataset.copyKey]||text;
 }
 function renderConnectionNotice(){
  const host=document.getElementById('offlineStatusBanner');
  if(!host)return;
  const copy=connectionCopy();
  const title=document.getElementById('offlineBannerTitle');
  const sub=document.getElementById('offlineBannerSub');
  const close=document.getElementById('connectionNoticeClose');
  // A hiding card keeps its last copy while it fades out.
  if(connection.notice!=='hidden'&&title)title.dataset.copyKey=connection.notice==='online'?'onlineTitle':'offlineTitle';
  if(sub)sub.dataset.copyKey='offlineSub';
  if(title)setConnectionText(title,copy[title.dataset.copyKey||'offlineTitle']);
  setConnectionText(sub,copy.offlineSub);
  if(close){
   if(close.getAttribute('aria-label')!==copy.close)close.setAttribute('aria-label',copy.close);
   close.title=copy.close;close.dataset.deTitle=CONNECTION_COPY.de.close;
  }
  if(host.dataset.state!==connection.notice)host.dataset.state=connection.notice;
  host.setAttribute('aria-hidden',connection.notice==='hidden'?'true':'false');
  if(connection.notice==='hidden')host.setAttribute('inert','');else host.removeAttribute('inert');
  document.documentElement.classList.toggle('fm-is-offline',connection.offline);
  document.documentElement.classList.toggle('fm-offline-notice-open',connection.notice==='offline');
 }
 function announceConnection(text){
  const live=document.getElementById('connectionAnnouncer');
  if(!live)return;
  live.textContent='';
  requestAnimationFrame(()=>{live.textContent=text});
 }
 function clearNoticeTimer(){
  if(connection.noticeTimer){clearTimeout(connection.noticeTimer);connection.noticeTimer=null}
 }
 function setConnectionNotice(next){
  clearNoticeTimer();
  connection.notice=next;
  renderConnectionNotice();
  if(next==='hidden'&&!connection.offline){
   const live=document.getElementById('connectionAnnouncer');
   if(live)live.textContent='';
  }
  if(next!=='hidden'){
   connection.noticeTimer=setTimeout(()=>{connection.noticeTimer=null;if(connection.notice===next)setConnectionNotice('hidden')},next==='online'?RECONNECT_VISIBLE_MS:OFFLINE_EXPANDED_MS);
  }
 }
 function dismissConnectionNotice(){
  if(connection.notice==='hidden')return;
  setConnectionNotice('hidden');
 }
 function syncOfflineStatusBanner(){
  const offline=(typeof navigator!=='undefined'&&navigator.onLine===false);
  if(!connection.initialized){
   connection.initialized=true;
   connection.offline=offline;
   setConnectionNotice(offline?'offline':'hidden');
   if(offline)announceConnection(connectionCopy().offlineTitle+'. '+connectionCopy().offlineSub);
   return;
  }
  if(offline===connection.offline){
   // Same state (language change, repeated event): refresh copy only, never re-open a dismissed notice.
   renderConnectionNotice();
   return;
  }
  connection.offline=offline;
  if(offline){
   setConnectionNotice('offline');
   announceConnection(connectionCopy().offlineTitle+'. '+connectionCopy().offlineSub);
  }else{
   setConnectionNotice('online');
   announceConnection(connectionCopy().onlineTitle);
  }
 }
 g.FocusConnection=Object.freeze({
  isOffline:()=>connection.offline,
  getNoticeState:()=>connection.notice,
  dismiss:dismissConnectionNotice
 });
 /* The goal screen's own offline message (AI service error owner) replaces the expanded notice,
    so one offline message is shown at a time; the compact Home state stays. */
 if(typeof g.showMissionOfflineError==='function'&&!g.showMissionOfflineError.__fmConnectionAware){
  const showMissionOfflineErrorBase=g.showMissionOfflineError;
  const showMissionOfflineErrorAware=function(){
   if(connection.notice==='offline')dismissConnectionNotice();
   return showMissionOfflineErrorBase.apply(this,arguments);
  };
  showMissionOfflineErrorAware.__fmConnectionAware=true;
  g.showMissionOfflineError=showMissionOfflineErrorAware;
 }
 g.syncOfflineStatusBanner=syncOfflineStatusBanner;
 if(typeof g.setLanguage==='function'&&!g.__fmSetLangWrappedV172){
  const origSetLang=g.setLanguage;
  g.setLanguage=function(lang,save){
   const r=origSetLang.apply(this,arguments);
   syncOfflineStatusBanner();
   if(document.getElementById('missionError')?.dataset?.offlineState==='1'){
    g.showMissionOfflineError?.();
   }
   document.title='v17.6 Navigation, Insights & League Polish';
   return r;
  };
  g.__fmSetLangWrappedV172=true;
 }
 if(!g.__fmOfflineEventsBoundV172){
  g.__fmOfflineEventsBoundV172=true;
  g.addEventListener('online',syncOfflineStatusBanner);
  g.addEventListener('offline',syncOfflineStatusBanner);
  document.addEventListener('click',ev=>{
   if(ev.target instanceof Element&&ev.target.closest('#connectionNoticeClose'))dismissConnectionNotice();
  });
 }
 if(A){
  A.version='v17.6 Navigation, Insights & League Polish';
  A.name='Home System';
  if(A.core?.lifecycle){
   A.core.lifecycle.phase='home-system-ready';
   A.core.lifecycle.ready=true;
  }
 }
 document.addEventListener('DOMContentLoaded',()=>{
  document.title='v17.6 Navigation, Insights & League Polish';
  const badge=document.getElementById('dataAppVersionBadge');
  if(badge)badge.textContent='v17.6 Navigation, Insights & League Polish';
  ensureLucideFallback();
  g.lucide?.createIcons?.();
  syncOfflineStatusBanner();
  setTimeout(()=>{
   document.title='v17.6 Navigation, Insights & League Polish';
   syncOfflineStatusBanner();
  },150);
 });
})(window);
