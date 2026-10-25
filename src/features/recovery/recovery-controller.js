function readRecoveryCandidate(){
 let raw=null;try{raw=JSON.parse(localStorage.getItem(SESSION_CORE_STORAGE_KEY)||'null')}catch(error){try{localStorage.setItem(ACTIVE_SESSION_BACKUP_KEY,localStorage.getItem(SESSION_CORE_STORAGE_KEY)||'')}catch{}localStorage.removeItem(SESSION_CORE_STORAGE_KEY);return null}
 if(!raw)return null;
 const migrated=migrateActiveSessionV112(raw);
 if(!migrated){localStorage.removeItem(SESSION_CORE_STORAGE_KEY);return null}
 const check=validateActiveSessionV112(migrated);
 if(!check.valid){try{localStorage.setItem(ACTIVE_SESSION_BACKUP_KEY,JSON.stringify(raw))}catch{}localStorage.removeItem(SESSION_CORE_STORAGE_KEY);console.warn('[SessionRecovery]',{event:'active_session_invalid',errors:check.errors});return null}
 setActiveSession(migrated);sessionRecoveryCandidate=cloneSession(migrated);recoveryLog('active_session_recovery_detected',{sessionId:migrated.id,type:migrated.type});return migrated;
}
function renderSessionRecovery(s){
 const elapsed=Math.floor((Number(s.elapsedMs)||0)/60000),planned=Math.max(1,Math.round(Number(s.plannedMinutes)||1)),remaining=Math.max(0,planned-elapsed),pct=Math.min(100,Math.round((Number(s.elapsedMs)||0)/plannedSessionMs(s)*100)),mission=s.type==='mission';
 document.getElementById('recoveryTypeLabel').textContent=mission?'Focus Mission · Schritt '+(s.ui?.progressCurrent||1):'Kurzer Fokus';
 document.getElementById('recoveryHeading').textContent=mission?'Mission-Sitzung fortsetzen?':'Fokus-Sitzung fortsetzen?';
 document.getElementById('recoveryCopy').textContent='Du hast bereits '+elapsed+' von '+planned+' Minuten fokussiert. Der Timer wurde beim Schließen angehalten.';
 document.getElementById('recoveryTitle').textContent=s.title;document.getElementById('recoveryTask').textContent=s.task;
 document.getElementById('recoveryElapsed').textContent=elapsed+' Min.';document.getElementById('recoveryRemaining').textContent=remaining+' Min.';document.getElementById('recoveryProgressBar').style.width=pct+'%';
 const review=document.getElementById('recoveryReviewButton');review.textContent=mission?'Ergebnis jetzt bewerten':'Fokuszeit speichern';review.disabled=elapsed<1;review.classList.toggle('opacity-40',elapsed<1);
 lucide.createIcons();recoveryLog('active_session_recovery_opened',{sessionId:s.id,type:s.type});
}
function resolveInitialAppStateV112(){
 if(sessionRecoveryBootResolved)return;sessionRecoveryBootResolved=true;
 const recovered=sessionRecoveryCandidate||readRecoveryCandidate();
 if(recovered){renderSessionRecovery(recovered);setAppScreen('session-recovery',{immediate:true});return}
 setAppScreen('home',{immediate:true});
}
function resumeRecoveredSession(){
 const stored=readRecoveryCandidate();if(!stored){releaseRecoveryNavigationV112();return resolveInitialAppStateV112()}releaseRecoveryNavigationV112();
 const resumed={...stored,lifecycleState:'running',lastActiveAt:Date.now(),lastCheckpointAt:Date.now()};setActiveSession(resumed);sessionRecoveryCandidate=null;currentSessionStartedAt=resumed.startedAt;currentSessionPlannedMinutes=resumed.plannedMinutes;currentSessionActualMinutes=Math.floor(resumed.elapsedMs/60000);renderSession(resumed);setAppScreen('timer',{immediate:true});startSessionRuntime();recoveryLog('active_session_resumed',{sessionId:resumed.id,type:resumed.type});
}
function discardRecoveredSession(){
 releaseRecoveryNavigationV112();
 const id=activeSession?.id||sessionRecoveryCandidate?.id||null;stopSessionRuntime();setActiveSession(null);sessionRecoveryCandidate=null;recoveryLog('active_session_discarded',{sessionId:id});setAppScreen('home',{immediate:true});
}
function finishRecoveredSession(){
 const s=activeSession||sessionRecoveryCandidate;if(!s)return;
 const minutes=Math.floor(sessionElapsedMs(s,s.lifecycleState==='running')/60000);if(minutes<1)return;
 if(s.type==='mission'){releaseRecoveryNavigationV112();openRecoveredMissionReview(s);return}
 releaseRecoveryNavigationV112();
 const final=buildFinalSessionV112(s,'completed','recovery_confirmed');if(!saveFinalSessionOnceV112(final))return;stopSessionRuntime();setActiveSession(null);sessionRecoveryCandidate=null;setAppScreen('home',{immediate:true});
}

// Mission result update must finalize the same record, then adaptation remains unchanged.
const recoveryShowPlanUpdate=showPlanUpdate;
showPlanUpdate=async function(){
 if(activeSession?.type==='mission'&&activeSession.lifecycleState==='awaiting_review'){
  const feedback=(document.getElementById('sessionFeedbackText').value||'').trim(),existing=sessionRepository.getById(activeSession.id),patch={result:{...(existing?.result||{}),outcome:selectedSessionStatus,feedback,rating:selectedConfidenceRating,countAsFocus:Number(existing?.actualMinutes||currentSessionActualMinutes)>=1,reason:'user_reviewed'}};
  const saved=sessionRepository.update(activeSession.id,patch);if(!saved)return;saveSessionSnapshot(saved);activeSession={...activeSession,finalizationState:'saved'};window.activeSession=activeSession;localStorage.setItem(SESSION_CORE_STORAGE_KEY,JSON.stringify(activeSession));
 }
 try{return await recoveryShowPlanUpdate.apply(this,arguments)}finally{if(activeSession?.lifecycleState==='awaiting_review'){setActiveSession(null);sessionRecoveryCandidate=null}}
};

/* v11.5.1: narrow regression fix. Recovery UI and timer design remain unchanged. */
function getRecoverableSessionV1151(){
  const candidate=sessionRecoveryCandidate||activeSession;
  if(candidate&&candidate.status==='running'&&ACTIVE_SESSION_STATES.includes(candidate.lifecycleState))return cloneSession(candidate);
  try{
    const stored=JSON.parse(localStorage.getItem(SESSION_CORE_STORAGE_KEY)||'null');
    if(!stored||stored.status!=='running')return null;
    const migrated=migrateActiveSessionV112(stored);
    const check=validateActiveSessionV112(migrated);
    return check.valid?cloneSession(migrated):null;
  }catch(error){
    console.error('[SessionRecovery]',{event:'resume_snapshot_read_failed',message:error?.message||'unknown'});
    return null;
  }
}
function showRecoveryErrorV1151(message){
  const copy=document.getElementById('recoveryCopy');
  if(copy){copy.textContent=message;copy.classList.add('text-rose-600')}
  setAppScreen('session-recovery',{immediate:true});
}
resumeRecoveredSession=function(){
  const snapshot=getRecoverableSessionV1151();
  if(!snapshot){
    releaseRecoveryNavigationV112();
    setActiveSession(null);sessionRecoveryCandidate=null;
    setAppScreen('home',{immediate:true});
    return false;
  }
  if(snapshot.type==='mission'){
    const mission=missionRepository.getById(snapshot.missionId);
    const step=mission?.steps.find(x=>x.id===snapshot.missionStepId);
    if(!mission||!step){
      sessionRecoveryCandidate=snapshot;setActiveSession(snapshot);
      showRecoveryErrorV1151('Die zugehörige Mission oder der Missionsschritt wurde nicht gefunden. Deine gespeicherte Fokuszeit bleibt erhalten.');
      return false;
    }
    missionRepository.setSelected(mission.id);
    if(typeof bindMissionCompatibilityV115==='function')bindMissionCompatibilityV115(mission);
  }
  const resumed={...snapshot,lifecycleState:'running',lastActiveAt:Date.now(),lastCheckpointAt:Date.now()};
  try{
    releaseRecoveryNavigationV112();
    setActiveSession(resumed);
    currentSessionStartedAt=resumed.startedAt;
    currentSessionPlannedMinutes=resumed.plannedMinutes;
    currentSessionActualMinutes=Math.floor((Number(resumed.elapsedMs)||0)/60000);
    renderSession(resumed);
    applyAppScreenImmediately('timer');
    startSessionRuntime();
    sessionRecoveryCandidate=null;
    recoveryLog('active_session_resumed',{sessionId:resumed.id,type:resumed.type,source:'v11.5.1'});
    return true;
  }catch(error){
    stopSessionRuntime();
    const rollback={...snapshot,lifecycleState:'awaiting_resume',lastActiveAt:null,lastCheckpointAt:Date.now()};
    setActiveSession(rollback);sessionRecoveryCandidate=rollback;
    recoveryNavigationLockedV112=true;
    renderSessionRecovery(rollback);
    showRecoveryErrorV1151('Die Sitzung konnte nicht geöffnet werden. Sie wurde nicht gelöscht und kann erneut fortgesetzt werden.');
    console.error('[SessionRecovery]',{event:'resume_failed_rolled_back',sessionId:snapshot.id,message:error?.message||'unknown'});
    return false;
  }
};
function routeExistingSessionV1151(){
  const s=activeSession||sessionRecoveryCandidate||getRecoverableSessionV1151();
  if(!s)return false;
  if(s.lifecycleState==='awaiting_review'){
    releaseRecoveryNavigationV112();
    setAppScreen('result',{immediate:true});
    return true;
  }
  if(s.lifecycleState==='running'){
    releaseRecoveryNavigationV112();
    renderSession(s);applyAppScreenImmediately('timer');startSessionRuntime();
    return true;
  }
  sessionRecoveryCandidate=cloneSession(s);setActiveSession(s);
  recoveryNavigationLockedV112=true;
  renderSessionRecovery(s);applyAppScreenImmediately('session-recovery');
  return true;
}
const startSessionBeforeGuardV1151=startSession;
startSession=function(session){
  if(activeSession||sessionRecoveryCandidate||getRecoverableSessionV1151()){
    routeExistingSessionV1151();
    return false;
  }
  return startSessionBeforeGuardV1151.call(this,session);
};

/* v13.1 Recovery Repository Migration
   Recovery and History reads now cross the Session Repository boundary.
   Existing keys, schemas, UI and timer behavior remain unchanged. */
(function installRecoveryRepositoryMigrationV131(global){
  'use strict';
  const App=global.FocusModeApp;
  const sessions=App?.repositories?.sessions;
  if(!sessions)throw new Error('Session Repository is required before v13.1.');
  if(App.compatibility?.recoveryRepositoryMigration?.version==='13.1')return;

  const clone=value=>value==null?value:(typeof global.structuredClone==='function'?global.structuredClone(value):JSON.parse(JSON.stringify(value)));

  function repositoryRecoveryCandidateV131(){
    const raw=sessions.loadActive();
    if(!raw)return null;
    const migrated=typeof global.migrateActiveSessionV112==='function'?global.migrateActiveSessionV112(raw):raw;
    if(!migrated){
      sessions.clearActive(raw.id||null);
      return null;
    }
    const check=typeof global.validateActiveSessionV112==='function'
      ?global.validateActiveSessionV112(migrated)
      :sessions.validate(migrated,{active:true});
    if(!check.valid){
      console.warn('[RecoveryMigration]',{event:'active_session_invalid',sessionId:raw.id||null,errors:check.errors});
      sessions.clearActive(raw.id||null);
      return null;
    }
    const saved=sessions.saveActive(migrated);
    if(!saved)return null;
    const clonedSaved=clone(saved);
    try{activeSession=clonedSaved}catch{}
    global.activeSession=clonedSaved;
    const clonedCandidate=clone(saved);
    try{sessionRecoveryCandidate=clonedCandidate}catch{}
    global.sessionRecoveryCandidate=clonedCandidate;
    recoveryLog?.('active_session_recovery_detected',{sessionId:saved.id,type:saved.type,source:'repository-v13.1'});
    return clone(saved);
  }

  global.readRecoveryCandidate=function readRecoveryCandidateV131(){return repositoryRecoveryCandidateV131()};
  global.getRecoverableSessionV1151=function getRecoverableSessionV131(){
    const memory=global.sessionRecoveryCandidate||global.activeSession;
    if(memory&&memory.status==='running'&&ACTIVE_SESSION_STATES.includes(memory.lifecycleState))return clone(memory);
    return repositoryRecoveryCandidateV131();
  };

  /* Recovery resume rereads the latest repository snapshot instead of parsing localStorage. */
  const resumeBeforeV131=global.resumeRecoveredSession;
  global.resumeRecoveredSession=function resumeRecoveredSessionV131(){
    const stored=repositoryRecoveryCandidateV131();
    if(!stored){
      global.releaseRecoveryNavigationV112?.();
      try{sessionRecoveryCandidate=null}catch{}
      global.sessionRecoveryCandidate=null;
      global.setActiveSession?.(null);
      global.setAppScreen?.('home',{immediate:true});
      return false;
    }
    return resumeBeforeV131.apply(this,arguments);
  };

  /* History compatibility API is now read-through repository. */
  const history=global.sessionRepository;
  if(history){
    history.getAll=function getAllHistoryV131(){return sessions.listHistory()};
    history.getById=function getHistoryByIdV131(id){return sessions.listHistory().find(x=>x.id===id)||null};
    history.getCompleted=function getCompletedHistoryV131(){return sessions.listHistory().filter(x=>x.status==='completed')};
    history.getToday=function getTodayHistoryV131(){const start=new Date();start.setHours(0,0,0,0);return sessions.listHistory().filter(x=>Number(x.finishedAt)>=start.getTime())};
    history.getThisWeek=function getThisWeekHistoryV131(){const start=new Date();start.setHours(0,0,0,0);start.setDate(start.getDate()-((start.getDay()+6)%7));return sessions.listHistory().filter(x=>Number(x.finishedAt)>=start.getTime())};
  }

  /* The legacy store loader remains callable, but its read source is canonical. */
  global.loadHistoryStore=function loadHistoryStoreV131(){return{schemaVersion:1,sessions:sessions.listHistory()}};

  function inspectRecoveryV131(){
    const report=sessions.inspect();
    return Object.freeze({version:'13.1',active:clone(report.active),last:clone(report.last),historyCount:report.historyCount,recoveryCandidate:clone(global.sessionRecoveryCandidate||null)});
  }

  App.services.recoveryPersistence=Object.freeze({readCandidate:repositoryRecoveryCandidateV131,inspect:inspectRecoveryV131});
  App.compatibility.recoveryRepositoryMigration=Object.freeze({version:'13.1',recoveryReadsMigrated:true,historyReadsMigrated:true,directRecoveryJSONParsingAuthoritative:false,storageKeysRenamed:0,formatsChanged:0});

  /* Re-resolve startup after all legacy scripts and this override are installed. */
  document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>{
    const candidate=repositoryRecoveryCandidateV131();
    if(candidate){
      try{sessionRecoveryCandidate=clone(candidate)}catch{}
      global.sessionRecoveryCandidate=clone(candidate);
      try{recoveryNavigationLockedV112=true}catch{}
      global.renderSessionRecovery?.(candidate);
      global.applyAppScreenImmediately?.('session-recovery');
    }
    global.refreshSessionHistoryUI?.();
    global.refreshHomeStats?.();
    global.refreshStatsScreen?.();
  },220),{once:true});

  console.info('[RecoveryMigration]',{event:'migration_ready',version:'13.1',readOwner:'FocusModeApp.repositories.sessions'});
})(window);

// Read before DOMContentLoaded listeners run, so legacy startup handlers cannot force Home.
readRecoveryCandidate();
let recoveryNavigationLockedV112=Boolean(sessionRecoveryCandidate);
const setAppScreenBeforeRecoveryPriorityV112=setAppScreen;
setAppScreen=function(screenName,options={}){
 if(recoveryNavigationLockedV112&&screenName!=='session-recovery'){
  recoveryLog('startup_navigation_blocked',{screen:screenName});
  return Promise.resolve(false);
 }
 return setAppScreenBeforeRecoveryPriorityV112.apply(this,arguments);
};
function releaseRecoveryNavigationV112(){
 recoveryNavigationLockedV112=false;
 ++appScreenTransitionToken;
 if(appScreenAnimation){appScreenAnimation.cancel();appScreenAnimation=null}
 const body=document.getElementById('appBody');
 if(body){body.style.opacity='';body.style.transform='';body.style.filter=''}
}
document.addEventListener('DOMContentLoaded',()=>setTimeout(resolveInitialAppStateV112,120),{once:true});
document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>{
  if(sessionRecoveryCandidate||activeSession?.lifecycleState==='awaiting_resume'){
    const s=sessionRecoveryCandidate||activeSession;renderSessionRecovery(s);applyAppScreenImmediately('session-recovery');
  }
},180),{once:true});
