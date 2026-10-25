/**
 * Physical module: src/features/focus-session/session-finalization.js
 */

function actualSessionMinutes(s){const elapsed=s.startedAt?Math.max(0,Math.floor((Date.now()-s.startedAt)/60000)):0,planned=Math.max(1,Math.round(Number(s.plannedMinutes)||1));return Math.min(planned,elapsed)}
function saveSessionSnapshot(s){localStorage.setItem(SESSION_CORE_LAST_KEY,JSON.stringify(s))}
function handleQuickSessionResult(s){saveSessionSnapshot(s);sessionRepository.save(s);setAppScreen('home')}
function handleMissionSessionResult(s){saveSessionSnapshot(s);sessionRepository.save(s);if(s.status!=='completed'){setAppScreen('home');return}const current=getCurrentMissionSession();if(!activeAIPlan||s.missionId!==ensureActiveMissionId()||!current||current.id!==s.missionStepId){console.error('[SessionCore]',{event:'mission_link_invalid',sessionId:s.id});const interrupted={...s,status:'interrupted',result:{...(s.result||{}),reason:'mission_link_invalid'}};saveSessionSnapshot(interrupted);sessionRepository.save(interrupted);setAppScreen('home');return}currentSessionActualMinutes=s.actualMinutes;const ratio=s.plannedMinutes?s.actualMinutes/s.plannedMinutes:0;selectedSessionStatus=ratio<.2?'aborted':'partial';document.querySelectorAll('#sessionStatusButtons .session-status-option').forEach(b=>b.classList.toggle('active',b.dataset.status===selectedSessionStatus));document.getElementById('sessionTimeSummary').textContent='Geplant: '+s.plannedMinutes+' Min. · gearbeitet: '+s.actualMinutes+' Min. Früher fertig zu sein ist völlig in Ordnung.';document.getElementById('sessionFeedbackText').value='';document.getElementById('adaptationError').classList.add('hidden');setAppScreen('result')}
function finishSession(result={}){if(!activeSession||activeSession.status!=='running')return null;if(timerInterval)clearInterval(timerInterval);timerInterval=null;const status=SESSION_STATUSES.includes(result.status)?result.status:'completed',finished={...cloneSession(activeSession),status,actualMinutes:actualSessionMinutes(activeSession),finishedAt:Date.now(),result:{reason:result.reason||'user_finished',outcome:result.outcome??null,feedback:result.feedback??null}};setActiveSession(finished);console.info('[SessionCore]',{event:'session_'+status,sessionId:finished.id,type:finished.type});finished.type==='mission'?handleMissionSessionResult(finished):handleQuickSessionResult(finished);setActiveSession(null);document.getElementById('islandIndicator')?.classList.add('hidden');return cloneSession(finished)}
function calculateInterruptedMinutes(session,finishedAt){const started=Number(session?.startedAt)||0,planned=Math.max(0,Math.round(Number(session?.plannedMinutes)||0));if(!started||finishedAt<started)return 0;const elapsed=Math.max(0,Math.round((finishedAt-started)/60000));return planned?Math.min(elapsed,planned):elapsed}
function updateLastSessionOutcome(outcome){try{const s=JSON.parse(localStorage.getItem(SESSION_CORE_LAST_KEY)||'null');if(!s)return;s.result={...(s.result||{}),outcome};localStorage.setItem(SESSION_CORE_LAST_KEY,JSON.stringify(s));sessionRepository.updateOutcome(s.id,outcome)}catch{}}

function buildFinalSessionV112(s,outcome,reason){
 const elapsed=Math.min(plannedSessionMs(s),sessionElapsedMs(s,s.lifecycleState==='running')),minutes=Math.floor(elapsed/60000);
 return{...cloneSession(s),status:'completed',lifecycleState:undefined,elapsedMs:undefined,remainingSeconds:undefined,lastActiveAt:undefined,lastCheckpointAt:undefined,endsAt:undefined,finalizationState:undefined,actualMinutes:minutes,finishedAt:Date.now(),result:{...(s.result||{}),reason,outcome,feedback:s.result?.feedback||null,countAsFocus:minutes>=1}};
}
function saveFinalSessionOnceV112(final){
 const existing=sessionRepository.getById(final.id);if(existing){recoveryLog('active_session_finalization_reused',{sessionId:final.id});return existing}
 const saved=sessionRepository.save(final);if(!saved){console.error('[SessionRecovery]',{event:'active_session_finalization_failed',sessionId:final.id});return null}
 saveSessionSnapshot(saved);recoveryLog('active_session_finalization_saved',{sessionId:final.id,type:final.type});return saved;
}
function finishRecoveredSession(){
 const s=activeSession||sessionRecoveryCandidate;if(!s)return;
 const minutes=Math.floor(sessionElapsedMs(s,s.lifecycleState==='running')/60000);if(minutes<1)return;
 if(s.type==='mission'){releaseRecoveryNavigationV112();openRecoveredMissionReview(s);return}
 releaseRecoveryNavigationV112();
 const final=buildFinalSessionV112(s,'completed','recovery_confirmed');if(!saveFinalSessionOnceV112(final))return;stopSessionRuntime();setActiveSession(null);sessionRecoveryCandidate=null;setAppScreen('home',{immediate:true});
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

restoreRunningSession();

/* v13 Session Persistence Migration
   Routes Session Core persistence writes through FocusModeApp.repositories.sessions.
   Recovery hydration and History UI reads remain compatibility-owned until v13.1. */
(function installSessionPersistenceMigrationV13(global){
  'use strict';
  const App=global.FocusModeApp;
  const sessions=App?.repositories?.sessions;
  if(!sessions)throw new Error('v12.9 Session Repository must be installed before v13 migration.');
  if(App.compatibility?.sessionPersistenceMigration?.version==='13')return;

  const legacyHistory=global.sessionRepository;
  const clone=value=>value==null?value:(typeof global.structuredClone==='function'?global.structuredClone(value):JSON.parse(JSON.stringify(value)));
  const terminalStates=new Set(['completed','cancelled','interrupted']);
  let terminalCommitInFlight=new Set();

  function refreshSessionConsumersV13(){
    try{global.refreshSessionHistoryUI?.()}catch(error){console.warn('[SessionMigration]',{event:'history_ui_refresh_failed',message:error?.message||'unknown'})}
    try{global.refreshHomeStats?.()}catch(error){console.warn('[SessionMigration]',{event:'home_stats_refresh_failed',message:error?.message||'unknown'})}
    try{global.refreshStatsScreen?.()}catch(error){console.warn('[SessionMigration]',{event:'stats_refresh_failed',message:error?.message||'unknown'})}
  }
  function isTerminalSessionV13(session){return Boolean(session&&terminalStates.has(String(session.status||'')))}
  function terminalCommitV13(session){
    if(!isTerminalSessionV13(session))return null;
    const check=sessions.validate(session,{terminal:true});
    if(!check.valid){console.error('[SessionMigration]',{event:'terminal_validation_failed',sessionId:session?.id||null,errors:check.errors});return null}
    const token=String(session.id)+'::'+String(session.status);
    if(terminalCommitInFlight.has(token)){console.info('[SessionMigration]',{event:'terminal_duplicate_blocked',sessionId:session.id,status:session.status});return sessions.listHistory().find(x=>x.id===session.id&&x.status===session.status)||clone(session)}
    terminalCommitInFlight.add(token);
    try{
      const history=sessions.appendHistory(session);
      if(!history)throw new Error('history_append_failed');
      const last=sessions.saveLast(history);
      if(!last)throw new Error('last_session_save_failed');
      if(!sessions.clearActive(session.id))throw new Error('active_session_clear_failed');
      if(global.activeSession?.id===session.id){global.activeSession=null;try{activeSession=null}catch{}}
      refreshSessionConsumersV13();
      console.info('[SessionMigration]',{event:'terminal_commit_succeeded',sessionId:session.id,status:session.status});
      return clone(last);
    }catch(error){
      console.error('[SessionMigration]',{event:'terminal_commit_failed',sessionId:session.id,status:session.status,message:error?.message||'unknown'});
      return null;
    }finally{terminalCommitInFlight.delete(token)}
  }

  /* Canonical active-session writer used by Session Core and existing Recovery code. */
  global.setActiveSession=function setActiveSessionV13(session){
    if(session==null){
      sessions.clearActive(global.activeSession?.id||null);
      try{activeSession=null}catch{}
      global.activeSession=null;
      return null;
    }
    const saved=sessions.saveActive(session);
    if(!saved)return null;
    try{activeSession=clone(saved)}catch{}
    global.activeSession=clone(saved);
    return clone(saved);
  };
  global.getActiveSession=function getActiveSessionV13(){return clone(global.activeSession||sessions.loadActive())};
  global.updateActiveSession=function updateActiveSessionV13(patch){const current=global.getActiveSession();return current?global.setActiveSession({...current,...clone(patch)}):null};
  global.saveSessionSnapshot=function saveSessionSnapshotV13(session){return sessions.saveLast(session)};

  /* Checkpoints retain existing elapsed-time calculations, but persistence crosses the repository boundary. */
  const checkpointBeforeV13=global.checkpointActiveSession;
  if(typeof checkpointBeforeV13==='function'){
    global.checkpointActiveSession=function checkpointActiveSessionV13(reason='interval',persist=true){
      const snapshot=checkpointBeforeV13.call(this,reason,false);
      if(!snapshot||!persist)return snapshot;
      const saved=sessions.checkpoint(snapshot);
      if(!saved){console.error('[SessionMigration]',{event:'checkpoint_failed',sessionId:snapshot.id,reason});return null}
      try{activeSession=clone(saved)}catch{}
      global.activeSession=clone(saved);
      recoveryLog?.('active_session_checkpointed',{sessionId:saved.id,reason,elapsedMinutes:Math.floor((Number(saved.elapsedMs)||0)/60000)});
      return clone(saved);
    };
  }

  /* Preserve the legacy History API while making its writes repository-owned. */
  if(legacyHistory&&typeof legacyHistory.save==='function'){
    legacyHistory.save=function saveHistoryThroughSessionsV13(session){
      const saved=terminalCommitV13(session);
      return saved?clone(saved):null;
    };
    legacyHistory.getAll=function getAllHistoryThroughSessionsV13(){return sessions.listHistory()};
    legacyHistory.getById=function getByIdHistoryThroughSessionsV13(id){return sessions.listHistory().find(x=>x.id===id)||null};
  }

  /* Recovery's finalization helper now uses the ordered commit without altering recovery hydration. */
  global.saveFinalSessionOnceV112=function saveFinalSessionOnceV13(final){
    const existing=sessions.listHistory().find(x=>x.id===final.id&&x.status===final.status);
    if(existing){sessions.saveLast(existing);sessions.clearActive(final.id);recoveryLog?.('active_session_finalization_reused',{sessionId:final.id});return clone(existing)}
    const saved=terminalCommitV13(final);
    if(!saved)return null;
    recoveryLog?.('active_session_finalization_saved',{sessionId:final.id,type:final.type});
    return clone(saved);
  };

  App.services.sessionPersistence=Object.freeze({terminalCommit:terminalCommitV13,refreshConsumers:refreshSessionConsumersV13});
  App.compatibility.sessionPersistenceMigration=Object.freeze({version:'13',sessionCoreWritesMigrated:true,checkpointWritesMigrated:true,terminalWritesMigrated:true,lastSessionWritesMigrated:true,recoveryReadsMigrated:false,historyUIReadsMigrated:false,keysRenamed:0,formatsChanged:0});
  console.info('[SessionMigration]',{event:'migration_ready',version:'13',repository:'FocusModeApp.repositories.sessions'});
})(window);
