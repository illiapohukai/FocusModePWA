/**
 * Physical module: src/features/recovery/recovery-router.js
 */

/* v13.1.2 Session Flow Stabilization */
(function installSessionFlowStabilizationV1312(global){
  'use strict';
  const App=global.FocusModeApp;
  const sessions=App?.repositories?.sessions;
  const missions=App?.repositories?.missions;
  if(!sessions||!missions)throw new Error('v13.1 repositories are required.');
  const clone=v=>v==null?v:(typeof global.structuredClone==='function'?global.structuredClone(v):JSON.parse(JSON.stringify(v)));

  function clearRuntimeSessionV1312(expectedId=null){
    stopSessionRuntime?.();
    sessions.clearActive(expectedId);
    try{activeSession=null}catch{}
    try{sessionRecoveryCandidate=null}catch{}
    global.activeSession=null;
    global.sessionRecoveryCandidate=null;
    try{recoveryNavigationLockedV112=false}catch{}
    global.releaseRecoveryNavigationV112?.();
  }

  function readResumeSnapshotV1312(){
    const stored=sessions.loadActive();
    if(!stored)return null;
    if(stored.status!=='running')return null;
    const migrated=typeof migrateActiveSessionV112==='function'?migrateActiveSessionV112(stored):stored;
    if(!migrated)return null;
    const check=typeof validateActiveSessionV112==='function'?validateActiveSessionV112(migrated):sessions.validate(migrated,{active:true});
    return check.valid?clone(migrated):null;
  }

  function resumeToTimerV1312(){
    const snapshot=readResumeSnapshotV1312();
    if(!snapshot){
      clearRuntimeSessionV1312();
      global.applyAppScreenImmediately?.('home');
      return false;
    }
    if(snapshot.type==='mission'){
      const mission=missions.getById(snapshot.missionId);
      const step=mission?.steps?.find(x=>x.id===snapshot.missionStepId);
      if(!mission||!step){
        try{sessionRecoveryCandidate=clone(snapshot)}catch{}
        global.sessionRecoveryCandidate=clone(snapshot);
        global.renderSessionRecovery?.(snapshot);
        global.applyAppScreenImmediately?.('session-recovery');
        const copy=document.getElementById('recoveryCopy');
        if(copy)copy.textContent='Die Mission oder der Missionsschritt wurde nicht gefunden. Deine Fokuszeit bleibt gespeichert.';
        return false;
      }
      missions.setSelected(mission.id);
      global.bindMissionCompatibilityV115?.(mission);
    }
    const now=Date.now();
    const resumed={...snapshot,status:'running',lifecycleState:'running',lastActiveAt:now,lastCheckpointAt:now};
    const saved=sessions.saveActive(resumed);
    if(!saved)return false;
    try{activeSession=clone(saved);sessionRecoveryCandidate=null;recoveryNavigationLockedV112=false}catch{}
    global.activeSession=clone(saved);
    global.sessionRecoveryCandidate=null;
    global.releaseRecoveryNavigationV112?.();
    currentSessionStartedAt=saved.startedAt;
    currentSessionPlannedMinutes=saved.plannedMinutes;
    currentSessionActualMinutes=Math.floor((Number(saved.elapsedMs)||0)/60000);
    global.renderSession?.(saved);
    global.applyAppScreenImmediately?.('timer');
    global.startSessionRuntime?.();
    recoveryLog?.('active_session_resumed',{sessionId:saved.id,type:saved.type,source:'v13.1.2'});
    return true;
  }
  global.resumeRecoveredSession=resumeToTimerV1312;

  /* Inline onclick is supplemented by a capture handler, so cached/legacy wrappers cannot route Home. */
  document.addEventListener('click',event=>{
    const button=event.target.closest('#app-screen-session-recovery .recovery-primary');
    if(!button)return;
    event.preventDefault();event.stopImmediatePropagation();
    resumeToTimerV1312();
  },true);

  function finalizeReviewStateV1312(){
    const stale=global.activeSession||(()=>{try{return activeSession}catch{return null}})();
    if(stale?.lifecycleState==='awaiting_review'||stale?.finalizationState==='saved')clearRuntimeSessionV1312(stale.id);
    else{
      try{sessionRecoveryCandidate=null}catch{}
      global.sessionRecoveryCandidate=null;
    }
  }

  /* After feedback/adaptation, the reviewed session must never guard the next start. */
  const showPlanUpdateBeforeV1312=global.showPlanUpdate;
  if(typeof showPlanUpdateBeforeV1312==='function'){
    global.showPlanUpdate=async function showPlanUpdateV1312(){
      try{return await showPlanUpdateBeforeV1312.apply(this,arguments)}
      finally{finalizeReviewStateV1312()}
    };
  }
  const finishLearningFlowBeforeV1312=global.finishLearningFlow;
  if(typeof finishLearningFlowBeforeV1312==='function'){
    global.finishLearningFlow=function finishLearningFlowV1312(){
      finalizeReviewStateV1312();
      return finishLearningFlowBeforeV1312.apply(this,arguments);
    };
  }

  /* Ignore an orphaned reviewed snapshot when the user starts the next focus. */
  const startSessionBeforeV1312=global.startSession;
  global.startSession=function startSessionV1312(session){
    const memory=global.activeSession||(()=>{try{return activeSession}catch{return null}})();
    if(memory?.lifecycleState==='awaiting_review'||memory?.finalizationState==='saved')clearRuntimeSessionV1312(memory.id);
    return startSessionBeforeV1312.call(this,session);
  };

  App.compatibility.sessionFlowStabilization=Object.freeze({version:'13.1.2',resumeToTimerFixed:true,reviewStateCleanupFixed:true,nextMissionStepGuardFixed:true});
})(window);

/* v13.1.4: one owner for recovery resume and the final screen commit. */
(function installAuthoritativeRecoveryRouterV1314(global){
  'use strict';
  const App=global.FocusModeApp;
  const sessions=App?.repositories?.sessions;
  const state={bootEpoch:0,resumeInFlight:false,resumedSessionId:null,route:'boot'};
  App.state=App.state||{};
  App.state.recoveryRouterV1314=state;

  const physicalApply=global.applyAppScreenImmediately;
  function memorySession(){
    try{return global.activeSession||activeSession||null}catch{return global.activeSession||null}
  }
  function homeBlocked(){
    const s=memorySession();
    return state.resumeInFlight||Boolean(state.resumedSessionId&&s?.id===state.resumedSessionId&&s?.lifecycleState==='running');
  }
  global.applyAppScreenImmediately=function applyAppScreenImmediatelyV1314(screenName){
    if(screenName==='home'&&homeBlocked()){
      console.info('[RecoveryRouter]',{event:'stale_home_commit_blocked',sessionId:state.resumedSessionId});
      return false;
    }
    const result=physicalApply.apply(this,arguments);
    state.route=screenName;
    return result;
  };

  const routedSet=global.setAppScreen;
  global.setAppScreen=function setAppScreenV1314(screenName,options={}){
    if(screenName==='home'&&homeBlocked()){
      console.info('[RecoveryRouter]',{event:'stale_home_route_blocked',sessionId:state.resumedSessionId});
      return Promise.resolve(false);
    }
    return routedSet.apply(this,arguments);
  };

  function recoverableSnapshot(){
    const inMemory=global.sessionRecoveryCandidate||memorySession();
    if(inMemory?.status==='running')return cloneSession(inMemory);
    const stored=sessions?.loadActive?.();
    if(!stored||stored.status!=='running')return null;
    const migrated=typeof migrateActiveSessionV112==='function'?migrateActiveSessionV112(stored):stored;
    return migrated&&validateActiveSessionV112(migrated).valid?cloneSession(migrated):null;
  }
  function persistRunning(snapshot){
    const now=Date.now();
    const running={...snapshot,status:'running',lifecycleState:'running',lastActiveAt:now,lastCheckpointAt:now};
    sessions?.saveActive?.(running);
    setActiveSession(running);
    global.activeSession=running;
    try{activeSession=running}catch{}
    global.sessionRecoveryCandidate=null;
    try{sessionRecoveryCandidate=null}catch{}
    return running;
  }
  function resumeAuthoritativelyV1314(){
    if(state.resumeInFlight)return false;
    const snapshot=recoverableSnapshot();
    if(!snapshot)return false;
    state.resumeInFlight=true;
    state.bootEpoch++;
    state.resumedSessionId=snapshot.id;
    try{
      if(snapshot.type==='mission'){
        const mission=global.missionRepository?.getById?.(snapshot.missionId);
        const step=mission?.steps?.find(x=>x.id===snapshot.missionStepId);
        if(!mission||!step)throw new Error('mission_link_missing');
        global.missionRepository.setSelected(mission.id);
        global.bindMissionCompatibilityV115?.(mission);
      }
      global.releaseRecoveryNavigationV112?.();
      const running=persistRunning(snapshot);
      currentSessionStartedAt=running.startedAt;
      currentSessionPlannedMinutes=running.plannedMinutes;
      currentSessionActualMinutes=Math.floor((Number(running.elapsedMs)||0)/60000);
      global.renderSession?.(running);
      global.applyAppScreenImmediately('timer');
      global.startSessionRuntime?.();
      state.route='timer';
      console.info('[RecoveryRouter]',{event:'resume_committed',sessionId:running.id,version:'13.1.4'});
      return true;
    }catch(error){
      state.resumedSessionId=null;
      const rollback={...snapshot,lifecycleState:'awaiting_resume',lastActiveAt:null,lastCheckpointAt:Date.now()};
      sessions?.saveActive?.(rollback);setActiveSession(rollback);
      global.sessionRecoveryCandidate=rollback;
      try{sessionRecoveryCandidate=rollback}catch{}
      global.renderSessionRecovery?.(rollback);
      physicalApply.call(global,'session-recovery');
      console.error('[RecoveryRouter]',{event:'resume_rollback',message:error?.message||'unknown'});
      return false;
    }finally{state.resumeInFlight=false}
  }
  global.resumeRecoveredSession=resumeAuthoritativelyV1314;

  /* Window capture runs before the older document capture/delegated handlers. */
  global.addEventListener('click',event=>{
    const button=event.target?.closest?.('#app-screen-session-recovery .recovery-primary');
    if(!button)return;
    event.preventDefault();event.stopImmediatePropagation();
    resumeAuthoritativelyV1314();
  },true);

  /* Invalidate every route decision made before recovery resolution. */
  document.addEventListener('DOMContentLoaded',()=>{
    const epoch=++state.bootEpoch;
    queueMicrotask(()=>{
      if(epoch!==state.bootEpoch)return;
      const snapshot=recoverableSnapshot();
      if(snapshot?.lifecycleState==='awaiting_resume'){
        global.sessionRecoveryCandidate=snapshot;
        try{sessionRecoveryCandidate=snapshot}catch{}
        global.renderSessionRecovery?.(snapshot);
        global.applyAppScreenImmediately('session-recovery');
        state.route='session-recovery';
      }
    });
  },{once:true});

  App.compatibility.authoritativeRecoveryRouter=Object.freeze({version:'13.1.4',singleResumeOwner:true,physicalHomeGuard:true,bootInvalidation:true,repositoryLegacySync:true,watchdogRequired:false});
})(window);
