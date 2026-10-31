/**
 * Physical module: src/features/optional-adaptation/optional-adaptation.js
 */
(function(g){
'use strict';
const A=g.FocusModeApp,M=A?.repositories?.missions,S=A?.repositories?.sessions;
if(!A?.core?.registerModule||!M||!S)throw new Error('Optional Adaptation dependencies unavailable');
if(A.core.hasModule('features.optionalAdaptation'))return;
const ENDPOINT=g.FOCUS_AI_ADAPT_ENDPOINT||'https://focusmodedemoai.illia-pohukai.workers.dev/adapt-mission';
let state={status:'ready',result:null,missionUpdatedAt:null,controller:null};
const clone=v=>typeof structuredClone==='function'?structuredClone(v):JSON.parse(JSON.stringify(v));
function isEn(){try{return g.document?.documentElement?.lang==='en'||g.localStorage?.getItem('focus-mode-language')==='en'||g.localStorage?.getItem('focus_lang')==='en'}catch{return false}}
function isOfflineErr(e){if(g.navigator?.onLine===false)return true;if(!e||e.name==='AbortError')return false;const m=String(e?.message||e||'');return e instanceof TypeError||/failed to fetch|networkerror|network request failed|load failed|offline|internet/i.test(m)}
function emit(event,result=state.result){console.info('[OptionalAdaptation]',{event,sessionId:result?.sessionId||null,missionId:result?.missionId||null,missionStepId:result?.missionStepId||null,createdAt:Date.now()})}
function sourceSession(){
 const live=g.activeSession;
 if(live?.type==='mission')return clone(live);
 try{const last=A.services.storage.getJSON(g.SESSION_CORE_LAST_KEY||'focusmode-last-session-v11',null);if(last?.type==='mission')return last}catch{}
 const all=typeof g.sessionRepository?.getAll==='function'?g.sessionRepository.getAll():[];
 return all.find(x=>x.type==='mission'&&x.missionId&&x.missionStepId)||null;
}
function readSelectedStatus(){try{return selectedSessionStatus||'partial'}catch{return g.selectedSessionStatus||'partial'}}
function readSelectedRating(){try{return Number(selectedConfidenceRating)||3}catch{return Number(g.selectedConfidenceRating)||3}}
function buildResult(mode){
 const s=sourceSession();if(!s)throw new Error('mission_session_missing');
 const mission=M.getById(s.missionId);if(!mission)throw new Error('mission_missing');
 const step=mission.steps.find(x=>x.id===s.missionStepId);if(!step&&!mission.progress?.history?.some(x=>x.sessionId===s.id))throw new Error('mission_step_missing');
 return Object.freeze({sessionId:s.id,missionId:s.missionId,missionStepId:s.missionStepId,title:s.title,task:s.task,outcome:readSelectedStatus(),rating:readSelectedRating(),feedback:String(document.getElementById('sessionFeedbackText')?.value||'').trim(),plannedMinutes:Number(s.plannedMinutes)||0,actualMinutes:Number(g.currentSessionActualMinutes??s.actualMinutes)||0,completedAt:new Date().toISOString(),adaptationMode:mode});
}
function setBusy(busy,label=''){const root=document.getElementById('app-screen-result'),a=document.getElementById('adaptMissionButton'),b=document.getElementById('skipAdaptationButton');root?.classList.toggle('optional-adaptation-locked',busy);for(const x of[a,b])if(x)x.disabled=busy;if(label&&a){const sp=a.querySelector('span');if(sp)sp.textContent=label}}
function restoreButtons(){const a=document.getElementById('adaptMissionButton'),b=document.getElementById('skipAdaptationButton'),en=isEn();if(a){const sp=a.querySelector('span');if(sp)sp.textContent=en?'Adapt Plan with AI':'Plan mit AI anpassen'}if(b){const sp=b.querySelector('span');if(sp)sp.textContent=en?'Continue without Adaptation':'Ohne Anpassung fortfahren'}setBusy(false)}
function updateSession(result){
 const repo=g.sessionRepository;const existing=repo?.getById?.(result.sessionId);if(!existing)throw new Error('history_session_missing');
 const saved=repo.update(result.sessionId,{result:{...(existing.result||{}),outcome:result.outcome,feedback:result.feedback,rating:result.rating,countAsFocus:Number(existing.actualMinutes||result.actualMinutes)>=1,reason:'user_reviewed',adaptationMode:result.adaptationMode}});
 if(!saved)throw new Error('session_result_save_failed');
 if(typeof g.saveSessionSnapshot==='function')g.saveSessionSnapshot(saved);return saved;
}
function releaseActiveReviewSession(){
 try{g.stopSessionRuntime?.()}catch{}
 try{g.setActiveSession?.(null)}catch{}
 g.sessionRecoveryCandidate=null;
 try{sessionRecoveryCandidate=null}catch{}
 try{g.releaseRecoveryNavigationV112?.()}catch{}
 const router=A.state?.recoveryRouterV1314;
 if(router){
  router.resumeInFlight=false;
  router.resumedSessionId=null;
 }
}
function commitMissionSessionResult(result){
 const before=M.getById(result.missionId);if(!before)throw new Error('mission_missing');
 const already=before.progress?.history?.some(x=>x.sessionId===result.sessionId);
 updateSession(result);
 releaseActiveReviewSession();
 if(already)return{mission:before,duplicatePrevented:true};
 const mission=clone(before),step=mission.steps.find(x=>x.id===result.missionStepId);if(!step)throw new Error('mission_step_missing');
 mission.progress=mission.progress||{completedStepIds:[],history:[],initialStepCount:mission.steps.length};
 mission.progress.history=Array.isArray(mission.progress.history)?mission.progress.history:[];
 mission.progress.completedStepIds=Array.isArray(mission.progress.completedStepIds)?mission.progress.completedStepIds:[];
 mission.progress.history.push({...clone(result),event:'session_result'});
 if(result.outcome==='completed'){
  step.status='completed';step.completedAt=Date.now();
  if(!mission.progress.completedStepIds.includes(step.id))mission.progress.completedStepIds.push(step.id);
  const next=mission.steps.find(x=>x.status!=='completed');
  mission.steps.forEach(x=>{if(x.status!=='completed')x.status=x===next?'active':'pending'});
  mission.currentStepId=next?.id||null;
  if(!next){mission.status='completed';mission.completedAt=Date.now()}
 }else{
  mission.steps.forEach(x=>{if(x.status!=='completed')x.status=x.id===step.id?'active':'pending'});mission.currentStepId=step.id;
 }
 const saved=M.update(mission.id,mission);if(!saved)throw new Error('mission_result_save_failed');
 state.missionUpdatedAt=saved.updatedAt;
 try{g.refreshMissionUIV116?.();g.renderMultiMissionUIV115?.();g.refreshHomeStats?.()}catch{}
 if(result.adaptationMode==='skipped')emit('adaptation_skipped',result);return{mission:saved,duplicatePrevented:false};
}
function clearReview(){
 releaseActiveReviewSession();
 state.status='finished';
}
function renderLocalResult(result,failed=false,offline=false){
 const en=isEn(),mission=M.getById(result.missionId),next=mission?.steps.find(x=>x.id===mission.currentStepId);
 const eyebrow=document.querySelector('#app-screen-update>div:first-child');
 if(eyebrow)eyebrow.textContent=failed?(offline?(en?'NO INTERNET CONNECTION':'KEINE INTERNETVERBINDUNG'):(en?'ADAPTATION UNAVAILABLE':'ANPASSUNG NICHT VERFÜGBAR')):(en?'RESULT SAVED':'ERGEBNIS GESPEICHERT');
 const h3=document.querySelector('#app-screen-update h3');
 if(h3)h3.innerHTML=failed?(offline?(en?'No internet connection.<br>Your progress is safe.':'Keine Internetverbindung.<br>Dein Fortschritt ist sicher.'):(en?'Your progress is<br>still safe.':'Dein Fortschritt ist<br>trotzdem sicher.')):(en?'Your mission is<br>ready for the next step.':'Deine Mission ist<br>bereit für den nächsten Schritt.');
 document.getElementById('adaptationMessage').textContent=failed?(offline?(en?'No internet connection. Your focus session result has been saved locally and your existing plan remains unchanged.':'Keine Internetverbindung. Dein Fokus-Ergebnis wurde lokal gespeichert und dein bisheriger Plan bleibt unverändert.'):(en?'AI adaptation was unavailable. Your progress was saved and your existing plan remains unchanged.':'Die AI-Anpassung war nicht verfügbar. Dein Fortschritt wurde gespeichert und dein bisheriger Plan bleibt unverändert.')):(result.outcome==='completed'?(en?'Step completed. Your mission continues with the next planned step.':'Der Schritt wurde abgeschlossen. Deine Mission geht mit dem nächsten geplanten Schritt weiter.'):(en?'Your result was saved. The current step remains open.':'Dein Ergebnis wurde gespeichert. Der aktuelle Schritt bleibt offen.'));
 document.getElementById('adaptationSummary').textContent=failed?(offline?(en?'You can continue without Adaptation or try again when you are back online.':'Du kannst ohne Anpassung fortfahren oder es erneut versuchen, sobald du wieder online bist.'):(en?'Your remaining mission steps were not changed.':'Deine verbleibenden Missionsschritte wurden nicht verändert.')):(en?'You skipped AI adaptation. Your remaining mission steps were not changed.':'Du hast die AI-Anpassung übersprungen. Deine verbleibenden Missionsschritte wurden nicht verändert.');
 document.getElementById('adaptedNextTitle').textContent=next?.title||(en?'Mission completed':'Mission abgeschlossen');
 document.getElementById('adaptedNextTask').textContent=next?.task||next?.reason||(en?'All planned steps have been processed.':'Alle geplanten Schritte sind bearbeitet.');
 document.getElementById('adaptedNextDuration').textContent=next?next.durationMinutes+(en?' min':' Min.'):(en?'Done':'Fertig');
 const retry=document.getElementById('optionalAdaptationRetryActions'),finish=document.getElementById('optionalAdaptationFinishButton');
 if(retry){
  const btns=retry.querySelectorAll('button');
  if(btns[0])btns[0].textContent=en?'Try Again':'Erneut versuchen';
  if(btns[1])btns[1].textContent=en?'Continue without Adaptation':'Ohne Anpassung fortfahren';
 }
 if(finish)finish.textContent=en?'Back to Dashboard':'Zum Dashboard';
 retry?.classList.toggle('hidden',!failed);finish?.classList.toggle('hidden',failed);g.lucide?.createIcons();g.setAppScreen('update',{immediate:true});
}
function finishOptionalAdaptationFlow(){clearReview();g.refreshMissionUIV116?.();g.renderMultiMissionUIV115?.();g.refreshHomeStats?.();g.setAppScreen('home',{immediate:true});}
async function continueMissionWithoutAdaptation(){
 if(!['ready','adaptation_failed'].includes(state.status))return false;
 const previouslyFailed=state.status==='adaptation_failed',previousResult=state.result;
 try{state.status='committing';setBusy(true,isEn()?'Saving result …':'Ergebnis wird gespeichert …');const result=previouslyFailed&&previousResult?Object.freeze({...clone(previousResult),adaptationMode:'skipped'}):buildResult('skipped');state.result=result;commitMissionSessionResult(result);clearReview();renderLocalResult(result,false,false);return true}catch(e){console.error('[OptionalAdaptation]',{event:'skip_failed',message:e.message});document.getElementById('adaptationError').textContent=isEn()?'The result could not be saved. Please try again.':'Das Ergebnis konnte nicht gespeichert werden. Bitte versuche es erneut.';document.getElementById('adaptationError').classList.remove('hidden');state.status='ready';return false}finally{restoreButtons()}
}
function normalizeUpdatedSessions(items){if(!Array.isArray(items)||!items.length)throw new Error('adaptation_sessions_invalid');return items.map((x,i)=>({id:String(x.id||('adapted_'+Date.now()+'_'+i)),title:String(x.title||('Schritt '+(i+1))),task:String(x.task||''),durationMinutes:Math.max(5,Math.min(60,Number(x.durationMinutes)||20)),reason:String(x.reason||''),requiredTools:Array.isArray(x.requiredTools)?x.requiredTools:[],recommendedApps:Array.isArray(x.recommendedApps)?x.recommendedApps:[]}))}
async function requestAdaptation(result){
 const mission=M.getById(result.missionId);if(!mission)throw new Error('mission_missing');
 state.controller?.abort();state.controller=new AbortController();const timeout=setTimeout(()=>state.controller.abort(),120000);
 const current={id:result.missionStepId,title:result.title,task:result.task,durationMinutes:result.plannedMinutes};
 const payload={missionId:result.missionId,missionUpdatedAt:mission.updatedAt,mission:{title:mission.title,subject:mission.subject,deadlineText:mission.deadlineText},currentSession:current,plannedMinutes:result.plannedMinutes,actualMinutes:result.actualMinutes,status:result.outcome,rating:result.rating,feedback:result.feedback,result:{outcome:result.outcome,rating:result.rating,feedback:result.feedback,plannedMinutes:result.plannedMinutes,actualMinutes:result.actualMinutes},remainingSessions:mission.steps.filter(x=>x.status!=='completed'),history:mission.progress?.history||[]};
 try{const response=await fetch(ENDPOINT,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload),signal:state.controller.signal});let data={};try{data=await response.json()}catch{}if(!response.ok||!data.ok||!data.adaptation)throw new Error(data.error||('HTTP '+response.status));return data.adaptation}finally{clearTimeout(timeout)}
}
function renderAIResult(result,adaptation){const en=isEn(),mission=M.getById(result.missionId),next=mission?.steps.find(x=>x.id===mission.currentStepId);document.querySelector('#app-screen-update>div:first-child').textContent=en?'MISSION UPDATED':'MISSION AKTUALISIERT';document.querySelector('#app-screen-update h3').innerHTML=en?'Your mission adapts<br>to your result.':'Deine Mission passt sich<br>deinem Ergebnis an.';document.getElementById('adaptationMessage').textContent=adaptation.message||(en?'Your feedback was evaluated and open steps were updated.':'Dein Feedback wurde ausgewertet und die offenen Schritte wurden aktualisiert.');document.getElementById('adaptationSummary').textContent=adaptation.summary||adaptation.mainDifficulty||(en?'Only open steps of this mission were adapted.':'Nur die offenen Schritte dieser Mission wurden angepasst.');document.getElementById('adaptedNextTitle').textContent=next?.title||(en?'Mission completed':'Mission abgeschlossen');document.getElementById('adaptedNextTask').textContent=next?.task||(en?'All planned steps have been processed.':'Alle geplanten Schritte sind bearbeitet.');document.getElementById('adaptedNextDuration').textContent=next?next.durationMinutes+(en?' min':' Min.'):(en?'Done':'Fertig');document.getElementById('optionalAdaptationRetryActions')?.classList.add('hidden');document.getElementById('optionalAdaptationFinishButton')?.classList.remove('hidden');g.setAppScreen('update',{immediate:true})}
async function adaptMissionAfterSession(retry=false){
 if(!retry&&!['ready'].includes(state.status))return false;if(retry&&state.status!=='adaptation_failed')return false;
 try{
  setBusy(true,isEn()?'Adapting plan …':'Plan wird angepasst …');
  let result=state.result;
  if(!retry){state.status='committing';result=buildResult('ai');state.result=result;commitMissionSessionResult(result)}
  if(g.navigator?.onLine===false){
   throw new Error('offline');
  }
  state.status='adapting';resetFeedbackRows?.();g.setAppScreen('feedbackprocessing');emit(retry?'adaptation_retry_requested':'adaptation_requested',result);
  const adaptation=await requestAdaptation(result),fresh=M.getById(result.missionId);if(!fresh)throw new Error('mission_missing');
  const updated=normalizeUpdatedSessions(adaptation.updatedSessions);const replaced=M.replaceRemainingSteps(result.missionId,updated,fresh.updatedAt);if(!replaced.ok)throw new Error(replaced.error||'adaptation_save_failed');
  state.status='adapted';emit('adaptation_completed',result);clearReview();renderAIResult(result,adaptation);g.refreshMissionUIV116?.();return true;
 }catch(e){
  console.error('[OptionalAdaptation]',{event:'adaptation_failed',message:e.message});
  state.status='adaptation_failed';emit('adaptation_failed');
  if(state.result)renderLocalResult(state.result,true,isOfflineErr(e));
  return false;
 }finally{restoreButtons()}
}
function retryOptionalAdaptation(){return adaptMissionAfterSession(true)}
function offered(){if(document.getElementById('app-screen-result')?.classList.contains('hidden'))return;state={status:'ready',result:null,missionUpdatedAt:null,controller:null};emit('adaptation_offered',sourceSession()||null);restoreButtons()}
const previousApply=g.applyAppScreenImmediately;g.applyAppScreenImmediately=function(screen){const r=previousApply.apply(this,arguments);if(screen==='result'){offered();setTimeout(offered,0)}return r};
g.adaptMissionAfterSession=adaptMissionAfterSession;g.continueMissionWithoutAdaptation=continueMissionWithoutAdaptation;g.retryOptionalAdaptation=retryOptionalAdaptation;g.finishOptionalAdaptationFlow=finishOptionalAdaptationFlow;g.showPlanUpdate=adaptMissionAfterSession;
const api=Object.freeze({version:'17.2',buildResult,commitMissionSessionResult,adaptMissionAfterSession,continueMissionWithoutAdaptation,retryOptionalAdaptation,inspect:()=>clone({status:state.status,result:state.result})});
A.features.optionalAdaptation=A.core.registerModule('features.optionalAdaptation',api,{virtualPath:'src/features/optional-adaptation/optional-adaptation-controller.js',version:'17.2',status:'authoritative'});
A.compatibility.optionalAdaptation=Object.freeze({skipWithoutFetch:true,idempotencyKey:'sessionId',storageKeysRenamed:0,formatsChanged:0});
document.addEventListener('DOMContentLoaded',()=>{g.lucide?.createIcons();emit('optional_adaptation_ready',null)},{once:true});
})(window);
