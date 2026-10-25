/**
 * Physical module: src/features/focus-session/session-controller.js
 */

(function(g){'use strict';const A=g.FocusModeApp,R=A?.repositories?.sessions;if(!A?.core?.registerModule||!R)throw new Error('Session Repository required');if(A.core.hasModule('core.sessionRecovery'))return;const d=Object.freeze({start:g.startSession,checkpoint:g.checkpointActiveSession,finish:g.endSessionByUser,cancel:g.stopFocusSession,startRuntime:g.startSessionRuntime,stopRuntime:g.stopSessionRuntime,read:g.readRecoveryCandidate,resume:g.resumeRecoveredSession,discard:g.discardRecoveredSession,review:g.finishRecoveredSession,render:g.renderSessionRecovery,route:g.routeExistingSessionV1151});const need=['start','checkpoint','finish','cancel','startRuntime','stopRuntime','read','resume','discard','review','render'];const missing=need.filter(k=>typeof d[k]!=='function');if(missing.length)throw new Error('Session Recovery delegates unavailable: '+missing.join(','));const wrap=(fn)=>function(){return fn.apply(this,arguments)};const start=wrap(d.start),checkpoint=wrap(d.checkpoint),finish=wrap(d.finish),cancel=wrap(d.cancel),startRuntime=wrap(d.startRuntime),stopRuntime=wrap(d.stopRuntime),read=wrap(d.read),resume=wrap(d.resume),discard=wrap(d.discard),review=wrap(d.review),render=wrap(d.render),route=typeof d.route==='function'?wrap(d.route):()=>false;const C=Object.freeze({version:'13.2.1',start,checkpoint,finish,cancel,runtime:Object.freeze({start:startRuntime,stop:stopRuntime}),recovery:Object.freeze({read,resume,discard,review,render,routeExisting:route})});A.core.sessionRecovery=A.core.registerModule('core.sessionRecovery',C,{version:'13.2.1',status:'authoritative-controller'});Object.assign(g,{startSession:start,checkpointActiveSession:checkpoint,endSessionByUser:finish,completeLearningSession:finish,stopFocusSession:cancel,startSessionRuntime:startRuntime,stopSessionRuntime:stopRuntime,readRecoveryCandidate:read,getRecoverableSessionV1151:read,resumeRecoveredSession:resume,discardRecoveredSession:discard,finishRecoveredSession:review,renderSessionRecovery:render,routeExistingSessionV1151:route});A.compatibility.sessionRecoveryConsolidation=Object.freeze({version:'13.2.1',storageKeysRenamed:0,formatsChanged:0,uiChanged:false});})(window);

(function(){
  function syncFocusClarityCard(){
    const session=window.activeSession;
    const card=document.getElementById('ucFocusCard');
    if(!session||!card)return;
    const title=document.getElementById('ucTaskCardTitle');
    const description=document.getElementById('timerTaskValue');
    const quick=session.type==='quick';
    card.classList.toggle('is-quick',quick);
    if(title)title.textContent=session.title||'Aktuelle Aufgabe';
    if(description)description.textContent=session.task||'Konzentriert weiterarbeiten.';
    card.setAttribute('aria-label',(session.title||'Aktuelle Aufgabe')+' öffnen');
  }
  const previousSync=window.syncUcData;
  window.syncUcData=function(){
    if(typeof previousSync==='function')previousSync.apply(this,arguments);
    syncFocusClarityCard();
  };
  const previousRender=window.renderSession;
  window.renderSession=function(session){
    const result=previousRender.apply(this,arguments);
    syncFocusClarityCard();
    return result;
  };
  document.getElementById('ucFocusCard')?.addEventListener('keydown',function(event){
    if(event.key==='Enter'||event.key===' '){event.preventDefault();showUcTask();}
  });
  document.title='v15 Modular Baseline';
})();
