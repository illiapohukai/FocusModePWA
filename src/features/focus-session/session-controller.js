function setActiveSession(session){activeSession=session?cloneSession(session):null;window.activeSession=activeSession;if(activeSession)localStorage.setItem(SESSION_CORE_STORAGE_KEY,JSON.stringify(activeSession));else localStorage.removeItem(SESSION_CORE_STORAGE_KEY);return activeSession}
function getActiveSession(){return activeSession?cloneSession(activeSession):null}
function updateActiveSession(patch){return activeSession?setActiveSession({...activeSession,...patch}):null}
function clearActiveSession(){if(timerInterval)clearInterval(timerInterval);timerInterval=null;if(typeof hideUcTask==='function')hideUcTask();setActiveSession(null);currentSessionStartedAt=null;currentSessionPlannedMinutes=0;currentSessionActualMinutes=0;document.getElementById('islandIndicator')?.classList.add('hidden');console.info('[SessionCore]',{event:'session_cleared'})}
function saveSessionSnapshot(s){localStorage.setItem(SESSION_CORE_LAST_KEY,JSON.stringify(s))}
function handleQuickSessionResult(s){saveSessionSnapshot(s);sessionRepository.save(s);setAppScreen('home')}
function handleMissionSessionResult(s){saveSessionSnapshot(s);sessionRepository.save(s);if(s.status!=='completed'){setAppScreen('home');return}const current=getCurrentMissionSession();if(!activeAIPlan||s.missionId!==ensureActiveMissionId()||!current||current.id!==s.missionStepId){console.error('[SessionCore]',{event:'mission_link_invalid',sessionId:s.id});const interrupted={...s,status:'interrupted',result:{...(s.result||{}),reason:'mission_link_invalid'}};saveSessionSnapshot(interrupted);sessionRepository.save(interrupted);setAppScreen('home');return}currentSessionActualMinutes=s.actualMinutes;const ratio=s.plannedMinutes?s.actualMinutes/s.plannedMinutes:0;selectedSessionStatus=ratio<.2?'aborted':'partial';document.querySelectorAll('#sessionStatusButtons .session-status-option').forEach(b=>b.classList.toggle('active',b.dataset.status===selectedSessionStatus));document.getElementById('sessionTimeSummary').textContent='Geplant: '+s.plannedMinutes+' Min. · gearbeitet: '+s.actualMinutes+' Min. Früher fertig zu sein ist völlig in Ordnung.';document.getElementById('sessionFeedbackText').value='';document.getElementById('adaptationError').classList.add('hidden');setAppScreen('result')}
function finishSession(result={}){if(!activeSession||activeSession.status!=='running')return null;if(timerInterval)clearInterval(timerInterval);timerInterval=null;const status=SESSION_STATUSES.includes(result.status)?result.status:'completed',finished={...cloneSession(activeSession),status,actualMinutes:actualSessionMinutes(activeSession),finishedAt:Date.now(),result:{reason:result.reason||'user_finished',outcome:result.outcome??null,feedback:result.feedback??null}};setActiveSession(finished);console.info('[SessionCore]',{event:'session_'+status,sessionId:finished.id,type:finished.type});finished.type==='mission'?handleMissionSessionResult(finished):handleQuickSessionResult(finished);setActiveSession(null);document.getElementById('islandIndicator')?.classList.add('hidden');return cloneSession(finished)}
function updateLastSessionOutcome(outcome){try{const s=JSON.parse(localStorage.getItem(SESSION_CORE_LAST_KEY)||'null');if(!s)return;s.result={...(s.result||{}),outcome};localStorage.setItem(SESSION_CORE_LAST_KEY,JSON.stringify(s));sessionRepository.updateOutcome(s.id,outcome)}catch{}}
function prepareLearningSession(){const step=getCurrentMissionSession();if(!step)return false;try{return startSession(createMissionSession(ensureActiveMissionId(),step.id))}catch(e){console.error('[SessionCore]',{event:'mission_create_failed',message:e.message});alert(e.message);return false}}
const sessionCoreSelectStatus=selectSessionStatus;selectSessionStatus=function(button,status){sessionCoreSelectStatus(button,status);updateLastSessionOutcome(status)};
function restoreRunningSession(){
 let s=null;try{s=JSON.parse(localStorage.getItem(SESSION_CORE_STORAGE_KEY)||'null')}catch{localStorage.removeItem(SESSION_CORE_STORAGE_KEY);console.warn('[SessionCore]',{event:'restore_failed'});return}
 if(!s||s.status!=='running')return;
 const planned=Math.max(1,Math.round(Number(s.plannedMinutes)||1));
 const endsAt=Number(s.endsAt)||(Number(s.startedAt)+planned*60000);
 if(!Number.isFinite(endsAt)||!Number.isFinite(Number(s.startedAt))){localStorage.removeItem(SESSION_CORE_STORAGE_KEY);return}
 s={...s,endsAt};setActiveSession(s);currentSessionStartedAt=s.startedAt;currentSessionPlannedMinutes=planned;currentSessionActualMinutes=actualSessionMinutes(s);originalDuration=planned*60;ucEndsAt=endsAt;
 if(Date.now()>=endsAt){timerDuration=0;const finished={...s,status:'completed',actualMinutes:planned,finishedAt:endsAt,result:{...(s.result||{}),reason:'timer_finished_while_away',outcome:null,feedback:null}};saveSessionSnapshot(finished);sessionRepository.save(finished);setActiveSession(null);console.info('[SessionCore]',{event:'session_completed_while_away',sessionId:finished.id,type:finished.type});return}
 timerDuration=Math.max(0,Math.ceil((endsAt-Date.now())/1000));
 document.addEventListener('DOMContentLoaded',()=>{renderSession(s);setAppScreen('timer',{immediate:true});document.getElementById('islandIndicator')?.classList.remove('hidden');updateTimerUI();startRestoredSessionTimer();console.info('[SessionCore]',{event:'session_resumed',sessionId:s.id,type:s.type})},{once:true});
}
restoreRunningSession();

function saveFinalSessionOnceV112(final){
 const existing=sessionRepository.getById(final.id);if(existing){recoveryLog('active_session_finalization_reused',{sessionId:final.id});return existing}
 const saved=sessionRepository.save(final);if(!saved){console.error('[SessionRecovery]',{event:'active_session_finalization_failed',sessionId:final.id});return null}
 saveSessionSnapshot(saved);recoveryLog('active_session_finalization_saved',{sessionId:final.id,type:final.type});return saved;
}
function openRecoveredMissionReview(source=activeSession){
 if(!source)return;stopSessionRuntime();
 const frozen={...cloneSession(source),lifecycleState:'awaiting_review',elapsedMs:sessionElapsedMs(source,source.lifecycleState==='running'),lastActiveAt:null,lastCheckpointAt:Date.now()};setActiveSession(frozen);sessionRecoveryCandidate=frozen;
 const final=buildFinalSessionV112(frozen,null,'awaiting_user_review');if(!saveFinalSessionOnceV112(final))return;
 currentSessionStartedAt=frozen.startedAt;currentSessionPlannedMinutes=frozen.plannedMinutes;currentSessionActualMinutes=final.actualMinutes;selectedSessionStatus=final.actualMinutes/frozen.plannedMinutes<.2?'aborted':'partial';
 document.querySelectorAll('#sessionStatusButtons .session-status-option').forEach(b=>b.classList.toggle('active',b.dataset.status===selectedSessionStatus));document.getElementById('sessionTimeSummary').textContent='Geplant: '+frozen.plannedMinutes+' Min. · gearbeitet: '+final.actualMinutes+' Min. Deine Fokuszeit bleibt erhalten.';document.getElementById('sessionFeedbackText').value='';document.getElementById('adaptationError').classList.add('hidden');setAppScreen('result',{immediate:true});recoveryLog('active_session_review_opened',{sessionId:frozen.id,type:frozen.type});
}
function handleElapsedSessionV112(){
 if(!activeSession)return;checkpointActiveSession('timer_finished',true);stopSessionRuntime();
 if(activeSession.type==='mission'){openRecoveredMissionReview(activeSession);return}
 const final=buildFinalSessionV112(activeSession,'completed','timer_finished');if(!saveFinalSessionOnceV112(final))return;setActiveSession(null);setAppScreen('home',{immediate:true});
}

// Authoritative start and finish paths for v11.2.
function startSession(session){
 const check=validateSession(session);if(!check.valid){console.error('[SessionCore]',{event:'session_validation_failed',errors:check.errors});alert('Die Fokus-Sitzung konnte nicht gestartet werden.');return false}
 if(activeSession){alert('Beende zuerst deine aktuelle Fokus-Sitzung.');return false}
 const now=Date.now(),running={...cloneSession(session),status:'running',lifecycleState:'running',startedAt:now,elapsedMs:0,remainingSeconds:session.plannedMinutes*60,lastActiveAt:now,lastCheckpointAt:now,endsAt:null,finalizationState:null};setActiveSession(running);currentSessionStartedAt=now;currentSessionPlannedMinutes=running.plannedMinutes;currentSessionActualMinutes=0;renderSession(running);setAppScreen('timer');startSessionRuntime();recoveryLog('session_started',{sessionId:running.id,type:running.type});return true;
}
function endSessionByUser(){
 if(!activeSession||activeSession.lifecycleState!=='running')return null;checkpointActiveSession('user_end',true);if(Math.floor(activeSession.elapsedMs/60000)<1){stopSessionRuntime();const cancelled={...buildFinalSessionV112(activeSession,'aborted','ended_too_early'),status:'cancelled',result:{...(activeSession.result||{}),reason:'ended_too_early',outcome:'aborted',countAsFocus:false}};sessionRepository.save(cancelled);saveSessionSnapshot(cancelled);setActiveSession(null);setAppScreen('home');return cancelled}
 if(activeSession.type==='mission'){openRecoveredMissionReview(activeSession);return cloneSession(activeSession)}
 const final=buildFinalSessionV112(activeSession,'completed','user_finished');if(!saveFinalSessionOnceV112(final))return null;stopSessionRuntime();setActiveSession(null);setAppScreen('home');return final;
}
let completeLearningSession=endSessionByUser;
function stopFocusSession(){if(!activeSession)return null;checkpointActiveSession('user_cancelled',true);const cancelled={...buildFinalSessionV112(activeSession,'aborted','user_cancelled'),status:'cancelled',result:{...(activeSession.result||{}),reason:'user_cancelled',outcome:'aborted',countAsFocus:false}};sessionRepository.save(cancelled);saveSessionSnapshot(cancelled);stopSessionRuntime();setActiveSession(null);setAppScreen('home');return cancelled}
