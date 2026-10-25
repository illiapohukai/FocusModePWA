/**
 * Physical module: src/features/missions/mission-controller.js
 */

/* v13.1.1 Critical Flow Fix */
(function installCriticalFlowFixV1311(global){
  'use strict';
  const App=global.FocusModeApp;
  const storage=App?.services?.storage;
  const sessions=App?.repositories?.sessions;
  const missions=App?.repositories?.missions;
  if(!storage||!sessions||!missions)throw new Error('v13.1 repositories are required.');
  const clone=v=>v==null?v:(typeof global.structuredClone==='function'?global.structuredClone(v):JSON.parse(JSON.stringify(v)));

  /* Root cause 1: Mission Store used the nonexistent StorageService.get(). */
  global.loadMissionStoreV115=function loadMissionStoreV1311(){
    try{
      const data=storage.getJSON(MISSIONS_STORE_KEY,null,{backupKey:MISSIONS_STORE_KEY+'-corrupt-backup',removeCorrupt:false});
      if(!data)return emptyMissionStore();
      if(validateMissionStoreV115(data))return data;
      storage.backup(MISSIONS_STORE_KEY,MISSIONS_STORE_KEY+'-corrupt-backup');
      console.warn('[CriticalFlowFix]',{event:'mission_store_invalid'});
    }catch(error){console.error('[CriticalFlowFix]',{event:'mission_store_load_failed',message:error?.message||'unknown'})}
    return emptyMissionStore();
  };

  /* Reinstall the final Plan übernehmen handler after repairing the store loader. */
  global.confirmAIPlan=function confirmAIPlanV1311(){
    if(!pendingAIPlan){showMissionError?.('Der AI-Plan ist nicht mehr verfügbar. Bitte starte die Analyse erneut.');return false}
    const result=missions.create({title:pendingAIPlan.goal.title,subject:pendingAIPlan.goal.subject,type:pendingAIPlan.goal.type,deadlineText:pendingAIPlan.goal.deadlineText,estimate:pendingAIPlan.estimate,sourceAnalysis:pendingAIPlan.sourceAnalysis,steps:pendingAIPlan.sessions});
    if(!result?.ok){
      showMissionError?.(result?.error==='active_mission_limit_reached'?'Du kannst momentan bis zu zwei aktive Focus Missions haben. Schließe eine Mission ab oder archiviere sie.':'Die Mission konnte nicht gespeichert werden. Bitte versuche es erneut.');
      return false;
    }
    missions.setSelected(result.mission.id);
    if(!missions.getPrimaryId())missions.setPrimary(result.mission.id);
    bindMissionCompatibilityV115(result.mission);
    renderAIPlan(activeAIPlan);
    revealCreatedMission();
    renderMultiMissionUIV115();
    setAppScreen('plan',{immediate:true});
    console.info('[CriticalFlowFix]',{event:'mission_plan_accepted',missionId:result.mission.id});
    return true;
  };

  /* Root cause 2: the v13.1 wrapper re-entered the legacy recovery chain.
     Resume once from the canonical snapshot and route directly to Timer. */
  const unusedResumeV1311=function resumeRecoveredSessionV1311(){
    const raw=sessions.loadActive();
    if(!raw){releaseRecoveryNavigationV112?.();setAppScreen('home',{immediate:true});return false}
    const snapshot=typeof migrateActiveSessionV112==='function'?migrateActiveSessionV112(raw):raw;
    const check=snapshot&&(typeof validateActiveSessionV112==='function'?validateActiveSessionV112(snapshot):sessions.validate(snapshot,{active:true}));
    if(!snapshot||!check.valid){renderSessionRecovery?.(raw);return false}
    if(snapshot.type==='mission'){
      const mission=missions.getById(snapshot.missionId);
      const step=mission?.steps?.find(x=>x.id===snapshot.missionStepId);
      if(!mission||!step){
        sessionRecoveryCandidate=clone(snapshot);
        renderSessionRecovery(snapshot);
        const copy=document.getElementById('recoveryCopy');
        if(copy){copy.textContent='Die zugehörige Mission oder der Missionsschritt wurde nicht gefunden. Deine Fokuszeit bleibt gespeichert.';copy.classList.add('text-rose-600')}
        return false;
      }
      missions.setSelected(mission.id);bindMissionCompatibilityV115(mission);
    }
    const now=Date.now();
    const resumed={...clone(snapshot),status:'running',lifecycleState:'running',lastActiveAt:now,lastCheckpointAt:now};
    const saved=sessions.saveActive(resumed);
    if(!saved)return false;
    activeSession=clone(saved);global.activeSession=clone(saved);sessionRecoveryCandidate=null;global.sessionRecoveryCandidate=null;
    currentSessionStartedAt=saved.startedAt;currentSessionPlannedMinutes=saved.plannedMinutes;currentSessionActualMinutes=Math.floor((Number(saved.elapsedMs)||0)/60000);
    releaseRecoveryNavigationV112();
    renderSession(saved);
    applyAppScreenImmediately('timer');
    startSessionRuntime();
    recoveryLog?.('active_session_resumed',{sessionId:saved.id,type:saved.type,source:'v13.1.1'});
    return true;
  };
  App.compatibility.criticalFlowFix=Object.freeze({version:'13.1.1',missionPlanAcceptFixed:true,recoveryResumeToTimerFixed:true,keysRenamed:0,formatsChanged:0});
})(window);

(function(g){'use strict';const A=g.FocusModeApp,M=A?.repositories?.missions,S=A?.repositories?.sessions;if(!A?.core?.registerModule||!M||!S)throw new Error('Mission and Session repositories required');if(A.core.hasModule('features.missionFlow'))return;const d=Object.freeze({confirm:g.confirmAIPlan,open:g.openMission,startStep:g.prepareLearningSession,adapt:g.showPlanUpdate,finish:g.finishLearningFlow,renderPlan:g.renderAIPlan,renderHome:g.renderHomeCarouselV116,currentStep:g.getCurrentMissionSession,missionId:g.ensureActiveMissionId,createSession:g.createMissionSession});const need=Object.keys(d),missing=need.filter(k=>typeof d[k]!=='function');if(missing.length)throw new Error('Mission Flow delegates unavailable: '+missing.join(','));const wrap=fn=>function(){return fn.apply(this,arguments)};const confirm=wrap(d.confirm),open=wrap(d.open),startStep=wrap(d.startStep),adapt=async function(){return await d.adapt.apply(this,arguments)},finish=wrap(d.finish),renderPlan=wrap(d.renderPlan),renderHome=wrap(d.renderHome),currentStep=wrap(d.currentStep),missionId=wrap(d.missionId),createSession=wrap(d.createSession);const C=Object.freeze({version:'13.2.2',selected:()=>M.getSelected?.()||M.getPrimary?.()||null,confirm,open,startStep,adapt,finish,currentStep,missionId,createSession,render:Object.freeze({plan:renderPlan,home:renderHome})});A.features.missionFlow=A.core.registerModule('features.missionFlow',C,{version:'13.2.2',status:'authoritative-controller'});Object.assign(g,{confirmAIPlan:confirm,openMission:open,prepareLearningSession:startStep,showPlanUpdate:adapt,finishLearningFlow:finish,renderAIPlan:renderPlan,renderHomeCarouselV116:renderHome,getCurrentMissionSession:currentStep,ensureActiveMissionId:missionId,createMissionSession:createSession});A.compatibility.missionFlowConsolidation=Object.freeze({version:'13.2.2',storageKeysRenamed:0,formatsChanged:0,uiChanged:false});})(window);

(function restorePlanDurationAndProtectionOrder(global){
  'use strict';
  if(global.__planDurationProtectionRestoreV13315)return;
  global.__planDurationProtectionRestoreV13315=true;

  function arrangePlanSupportBlocks(){
    const explanationText=document.getElementById('aiPlanExplanation');
    const explanationCard=explanationText?.parentElement;
    const protectionCard=document.getElementById('missionProtectionCard');
    if(!explanationCard||!protectionCard||explanationCard===protectionCard)return false;
    if(explanationCard.parentElement!==protectionCard.parentElement)return false;
    explanationCard.classList.add('mission-explanation-card-v1332');
    if(protectionCard.nextElementSibling!==explanationCard){
      protectionCard.parentElement.insertBefore(explanationCard,protectionCard.nextElementSibling);
    }
    return true;
  }

  function refreshPlanSupportLayout(){
    arrangePlanSupportBlocks();
    requestAnimationFrame(arrangePlanSupportBlocks);
  }

  function wrapAfter(name, callback){
    const original=global[name];
    if(typeof original!=='function'||original.__planRestoreWrapped)return false;
    function wrapped(){
      const result=original.apply(this,arguments);
      callback.apply(this,arguments);
      return result;
    }
    wrapped.__planRestoreWrapped=true;
    wrapped.__planRestoreOriginal=original;
    global[name]=wrapped;
    return true;
  }

  wrapAfter('applyAppScreenImmediately',function(screen){
    if(screen==='plan')refreshPlanSupportLayout();
  });
  wrapAfter('renderAIPlan',refreshPlanSupportLayout);
  wrapAfter('renderMissionPlan',refreshPlanSupportLayout);

  document.addEventListener('DOMContentLoaded',refreshPlanSupportLayout,{once:true});
  if(document.readyState!=='loading')refreshPlanSupportLayout();

  const App=global.FocusModeApp;
  if(App?.core?.registerModule&&!App.core.hasModule('features.planDurationProtectionRestore')){
    App.features.planDurationProtectionRestore=App.core.registerModule(
      'features.planDurationProtectionRestore',
      Object.freeze({version:'13.3.15',arrangePlanSupportBlocks,refreshPlanSupportLayout}),
      {virtualPath:'src/features/ui/plan-duration-protection-restore.js',version:'13.3.15',status:'compatibility-restore'}
    );
  }
})(window);
