/* v13.1.5: a finalized/reviewed session may never block the next mission step. */
(function installFinalizedSessionGuardV1315(global){
  'use strict';
  const App=global.FocusModeApp;
  const sessions=App?.repositories?.sessions;
  const missions=App?.repositories?.missions;
  if(!sessions||!missions)throw new Error('Session and mission repositories are required.');

  function memorySessionV1315(){
    try{return global.activeSession||activeSession||global.sessionRecoveryCandidate||sessionRecoveryCandidate||null}
    catch{return global.activeSession||global.sessionRecoveryCandidate||null}
  }
  function historyHasSessionV1315(id){
    return Boolean(id&&sessions.listHistory().some(item=>item.id===id&&['completed','cancelled','interrupted'].includes(item.status)));
  }
  function missionMovedPastSessionV1315(s){
    if(!s||s.type!=='mission')return false;
    const mission=missions.getById(s.missionId);
    if(!mission)return true;
    const step=mission.steps?.find(item=>item.id===s.missionStepId);
    if(!step)return true;
    return step.status==='completed'||mission.currentStepId!==s.missionStepId||mission.status!=='active';
  }
  function isFinalizedResidueV1315(s){
    if(!s)return false;
    return historyHasSessionV1315(s.id)||
      s.finalizationState==='saved'||
      (s.lifecycleState==='awaiting_review'&&missionMovedPastSessionV1315(s))||
      missionMovedPastSessionV1315(s);
  }
  function clearResidueV1315(reason='finalized_residue'){
    const memory=memorySessionV1315();
    const stored=sessions.loadActive();
    const stale=isFinalizedResidueV1315(memory)?memory:(isFinalizedResidueV1315(stored)?stored:null);
    if(!stale)return false;
    try{global.stopSessionRuntime?.()}catch{}
    sessions.clearActive(stale.id);
    try{activeSession=null;sessionRecoveryCandidate=null;recoveryNavigationLockedV112=false}catch{}
    global.activeSession=null;
    global.sessionRecoveryCandidate=null;
    global.releaseRecoveryNavigationV112?.();
    const router=App.state?.recoveryRouterV1314;
    if(router){router.resumeInFlight=false;router.resumedSessionId=null}
    console.info('[FinalizedSessionGuard]',{event:'stale_session_removed',sessionId:stale.id,reason});
    return true;
  }

  /* Required invariant before every new quick-focus or mission-step start. */
  const startBeforeV1315=global.startSession;
  global.startSession=function startSessionV1315(session){
    clearResidueV1315('before_new_session');
    return startBeforeV1315.apply(this,arguments);
  };

  /* Saving/adapting a review is a commit boundary, not a resumable state. */
  const updateBeforeV1315=global.showPlanUpdate;
  if(typeof updateBeforeV1315==='function'){
    global.showPlanUpdate=async function showPlanUpdateV1315(){
      try{return await updateBeforeV1315.apply(this,arguments)}
      finally{clearResidueV1315('review_committed')}
    };
  }
  const finishBeforeV1315=global.finishLearningFlow;
  if(typeof finishBeforeV1315==='function'){
    global.finishLearningFlow=function finishLearningFlowV1315(){
      clearResidueV1315('dashboard_after_review');
      return finishBeforeV1315.apply(this,arguments);
    };
  }

  /* Repairs existing devices that already contain the stale step-1 snapshot. */
  document.addEventListener('DOMContentLoaded',()=>{
    setTimeout(()=>clearResidueV1315('startup_reconciliation'),40);
  },{once:true});

  App.compatibility.finalizedSessionGuard=Object.freeze({
    version:'13.1.5',
    historyAware:true,
    missionScoped:true,
    activeMissionLimit:2,
    storageKeysRenamed:0
  });
})(window);

/* v13.2.1 Session Recovery Controller */
(function(g){'use strict';const A=g.FocusModeApp,R=A?.repositories?.sessions;if(!A?.core?.registerModule||!R)throw new Error('Session Repository required');if(A.core.hasModule('core.sessionRecovery'))return;const d=Object.freeze({start:g.startSession,checkpoint:g.checkpointActiveSession,finish:g.endSessionByUser,cancel:g.stopFocusSession,startRuntime:g.startSessionRuntime,stopRuntime:g.stopSessionRuntime,read:g.readRecoveryCandidate,resume:g.resumeRecoveredSession,discard:g.discardRecoveredSession,review:g.finishRecoveredSession,render:g.renderSessionRecovery,route:g.routeExistingSessionV1151});const need=['start','checkpoint','finish','cancel','startRuntime','stopRuntime','read','resume','discard','review','render'];const missing=need.filter(k=>typeof d[k]!=='function');if(missing.length)throw new Error('Session Recovery delegates unavailable: '+missing.join(','));const wrap=(fn)=>function(){return fn.apply(this,arguments)};const start=wrap(d.start),checkpoint=wrap(d.checkpoint),finish=wrap(d.finish),cancel=wrap(d.cancel),startRuntime=wrap(d.startRuntime),stopRuntime=wrap(d.stopRuntime),read=wrap(d.read),resume=wrap(d.resume),discard=wrap(d.discard),review=wrap(d.review),render=wrap(d.render),route=typeof d.route==='function'?wrap(d.route):()=>false;const C=Object.freeze({version:'13.2.1',start,checkpoint,finish,cancel,runtime:Object.freeze({start:startRuntime,stop:stopRuntime}),recovery:Object.freeze({read,resume,discard,review,render,routeExisting:route})});A.core.sessionRecovery=A.core.registerModule('core.sessionRecovery',C,{version:'13.2.1',status:'authoritative-controller'});Object.assign(g,{startSession:start,checkpointActiveSession:checkpoint,endSessionByUser:finish,completeLearningSession:finish,stopFocusSession:cancel,startSessionRuntime:startRuntime,stopSessionRuntime:stopRuntime,readRecoveryCandidate:read,getRecoverableSessionV1151:read,resumeRecoveredSession:resume,discardRecoveredSession:discard,finishRecoveredSession:review,renderSessionRecovery:render,routeExistingSessionV1151:route});A.compatibility.sessionRecoveryConsolidation=Object.freeze({version:'13.2.1',storageKeysRenamed:0,formatsChanged:0,uiChanged:false});})(window);
