/**
 * Physical module: src/features/focus-session/session-runtime.js
 */

function renderSession(s){document.getElementById('activeTaskLabel').textContent=s.title;document.getElementById('timerTaskValue').textContent=s.task;document.getElementById('currentFocusModeLabel').textContent=s.ui.modeLabel;syncUcData()}
function clearActiveSession(){if(timerInterval)clearInterval(timerInterval);timerInterval=null;hideUcTask();setActiveSession(null);currentSessionStartedAt=null;currentSessionPlannedMinutes=0;currentSessionActualMinutes=0;document.getElementById('islandIndicator')?.classList.add('hidden');console.info('[SessionCore]',{event:'session_cleared'})}
function startSession(session){const check=validateSession(session);if(!check.valid){console.error('[SessionCore]',{event:'session_validation_failed',errors:check.errors});alert('Die Fokus-Sitzung konnte nicht gestartet werden.');return false}if(activeSession?.status==='running'){alert('Beende zuerst deine aktuelle Fokus-Sitzung.');return false}const startedAt=Date.now(),endsAt=startedAt+session.plannedMinutes*60000,running={...cloneSession(session),status:'running',startedAt,endsAt};setActiveSession(running);currentSessionStartedAt=running.startedAt;currentSessionPlannedMinutes=running.plannedMinutes;currentSessionActualMinutes=0;timerDuration=running.plannedMinutes*60;originalDuration=timerDuration;ucEndsAt=endsAt;renderSession(running);setAppScreen('timer');document.getElementById('islandIndicator')?.classList.remove('hidden');updateTimerUI();if(timerInterval)clearInterval(timerInterval);timerInterval=setInterval(()=>{timerDuration=Math.max(0,Math.ceil((ucEndsAt-Date.now())/1000));updateTimerUI();if(timerDuration<=0)finishSession({status:'completed',reason:'timer_finished'})},1000);console.info('[SessionCore]',{event:'session_started',sessionId:running.id,type:running.type});return true}

function startConfiguredQuickFocus(){const task=(document.getElementById('quickTaskInput')?.value||'').trim()||'Konzentriert arbeiten';return startSession(createQuickSession({title:task,task,plannedMinutes:quickDuration}))}
function startQuickFocus(){return startSession(createQuickSession({title:'Freier Fokus',task:'Konzentriert arbeiten',plannedMinutes:25}))}
function startFocusSession(){return startQuickFocus()}
function prepareLearningSession(){const step=getCurrentMissionSession();if(!step)return false;try{return startSession(createMissionSession(ensureActiveMissionId(),step.id))}catch(e){console.error('[SessionCore]',{event:'mission_create_failed',message:e.message});alert(e.message);return false}}
function completeLearningSession(){return finishSession({status:'completed',reason:'user_finished'})}
function endSessionByUser(){if(!activeSession||activeSession.status!=='running')return null;return actualSessionMinutes(activeSession)<1?finishSession({status:'cancelled',reason:'ended_too_early'}):finishSession({status:'completed',reason:'user_finished'})}
function stopFocusSession(){return finishSession({status:'cancelled',reason:'user_cancelled'})}

function showUcTask(){const s=window.activeSession;document.getElementById('ucTaskTitle').textContent=s?.title||'Aktuelle Aufgabe';document.getElementById('ucTaskText').textContent=s?.task||'Konzentriert weiterarbeiten.';document.getElementById('ucTaskPopup').classList.add('open')}
const sessionCoreSelectStatus=selectSessionStatus;selectSessionStatus=function(button,status){sessionCoreSelectStatus(button,status);updateLastSessionOutcome(status)};
function startRestoredSessionTimer(){
 if(timerInterval)clearInterval(timerInterval);
 timerInterval=setInterval(()=>{timerDuration=Math.max(0,Math.ceil((ucEndsAt-Date.now())/1000));updateTimerUI();if(timerDuration<=0)finishSession({status:'completed',reason:'timer_finished'})},1000);
}
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


function checkpointActiveSession(reason='interval',persist=true){
 if(!activeSession||activeSession.status!=='running'||activeSession.lifecycleState!=='running')return null;
 const now=Date.now(),elapsed=sessionElapsedMs(activeSession,true),remaining=Math.max(0,Math.ceil((plannedSessionMs(activeSession)-elapsed)/1000));
 activeSession={...activeSession,elapsedMs:elapsed,remainingSeconds:remaining,lastActiveAt:now,lastCheckpointAt:now};window.activeSession=activeSession;
 if(persist)localStorage.setItem(SESSION_CORE_STORAGE_KEY,JSON.stringify(activeSession));
 timerDuration=remaining;updateTimerUI();
 if(persist)recoveryLog('active_session_checkpointed',{sessionId:activeSession.id,reason,elapsedMinutes:Math.floor(elapsed/60000)});
 return cloneSession(activeSession);
}
function stopCheckpointLoop(){if(sessionCheckpointInterval)clearInterval(sessionCheckpointInterval);sessionCheckpointInterval=null}
function startCheckpointLoop(){stopCheckpointLoop();sessionCheckpointInterval=setInterval(()=>checkpointActiveSession('interval',true),5000)}
function stopSessionRuntime(){if(timerInterval)clearInterval(timerInterval);timerInterval=null;stopCheckpointLoop();document.getElementById('islandIndicator')?.classList.add('hidden')}
function startSessionRuntime(){
 stopSessionRuntime();
 timerDuration=Math.max(0,Math.ceil((plannedSessionMs(activeSession)-sessionElapsedMs(activeSession,true))/1000));originalDuration=Math.max(1,activeSession.plannedMinutes*60);updateTimerUI();
 document.getElementById('islandIndicator')?.classList.remove('hidden');startCheckpointLoop();
 timerInterval=setInterval(()=>{const s=checkpointActiveSession('tick',false);if(!s)return;if(s.remainingSeconds<=0)handleElapsedSessionV112()},1000);
}

// Authoritative start and finish paths for v11.2.
startSession=function(session){
 const check=validateSession(session);if(!check.valid){console.error('[SessionCore]',{event:'session_validation_failed',errors:check.errors});alert('Die Fokus-Sitzung konnte nicht gestartet werden.');return false}
 if(activeSession){alert('Beende zuerst deine aktuelle Fokus-Sitzung.');return false}
 const now=Date.now(),running={...cloneSession(session),status:'running',lifecycleState:'running',startedAt:now,elapsedMs:0,remainingSeconds:session.plannedMinutes*60,lastActiveAt:now,lastCheckpointAt:now,endsAt:null,finalizationState:null};setActiveSession(running);currentSessionStartedAt=now;currentSessionPlannedMinutes=running.plannedMinutes;currentSessionActualMinutes=0;renderSession(running);setAppScreen('timer');startSessionRuntime();recoveryLog('session_started',{sessionId:running.id,type:running.type});return true;
};
endSessionByUser=function(){
 if(!activeSession||activeSession.lifecycleState!=='running')return null;checkpointActiveSession('user_end',true);if(Math.floor(activeSession.elapsedMs/60000)<1){stopSessionRuntime();const cancelled={...buildFinalSessionV112(activeSession,'aborted','ended_too_early'),status:'cancelled',result:{...(activeSession.result||{}),reason:'ended_too_early',outcome:'aborted',countAsFocus:false}};sessionRepository.save(cancelled);saveSessionSnapshot(cancelled);setActiveSession(null);setAppScreen('home');return cancelled}
 if(activeSession.type==='mission'){openRecoveredMissionReview(activeSession);return cloneSession(activeSession)}
 const final=buildFinalSessionV112(activeSession,'completed','user_finished');if(!saveFinalSessionOnceV112(final))return null;stopSessionRuntime();setActiveSession(null);setAppScreen('home');return final;
};
completeLearningSession=endSessionByUser;
stopFocusSession=function(){if(!activeSession)return null;checkpointActiveSession('user_cancelled',true);const cancelled={...buildFinalSessionV112(activeSession,'aborted','user_cancelled'),status:'cancelled',result:{...(activeSession.result||{}),reason:'user_cancelled',outcome:'aborted',countAsFocus:false}};sessionRepository.save(cancelled);saveSessionSnapshot(cancelled);stopSessionRuntime();setActiveSession(null);setAppScreen('home');return cancelled};

// Mission result update must finalize the same record, then adaptation remains unchanged.
const recoveryShowPlanUpdate=showPlanUpdate;
showPlanUpdate=async function(){
 if(activeSession?.type==='mission'&&activeSession.lifecycleState==='awaiting_review'){
  const feedback=(document.getElementById('sessionFeedbackText').value||'').trim(),existing=sessionRepository.getById(activeSession.id),patch={result:{...(existing?.result||{}),outcome:selectedSessionStatus,feedback,rating:selectedConfidenceRating,countAsFocus:Number(existing?.actualMinutes||currentSessionActualMinutes)>=1,reason:'user_reviewed'}};
  const saved=sessionRepository.update(activeSession.id,patch);if(!saved)return;saveSessionSnapshot(saved);activeSession={...activeSession,finalizationState:'saved'};window.activeSession=activeSession;localStorage.setItem(SESSION_CORE_STORAGE_KEY,JSON.stringify(activeSession));
 }
 try{return await recoveryShowPlanUpdate.apply(this,arguments)}finally{if(activeSession?.lifecycleState==='awaiting_review'){setActiveSession(null);sessionRecoveryCandidate=null}}
};

// Freeze on real document termination. Backgrounding within the same document continues normally.
window.addEventListener('pagehide',()=>{if(activeSession?.lifecycleState==='running')checkpointActiveSession('pagehide',true)},{capture:true});
document.addEventListener('visibilitychange',()=>{if(document.hidden&&activeSession?.lifecycleState==='running')checkpointActiveSession('visibility_hidden',true)},{capture:true});
