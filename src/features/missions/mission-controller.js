function continueMissionFromChoice(){
  closeFocusChoice();
  setTimeout(()=>focusMissionCreated ? setAppScreen('plan') : openGoalIntake(),180);
}
function openNewMissionFromChoice(){
  closeFocusChoice();
  setTimeout(()=>openGoalIntake(),180);
}
function selectMissionType(btn){
  btn.parentElement.querySelectorAll('.flow-chip').forEach(x=>x.classList.remove('active'));
  btn.classList.add('active');
  selectedMissionTypeValue=btn.textContent.trim();
  updateMissionPlanButton();
}
function showPlanStreakInfo(){
  const existing=document.getElementById('planStreakToast');
  if(existing) existing.remove();
  const toast=document.createElement('div');
  toast.id='planStreakToast';
  toast.className='absolute left-4 right-4 bottom-6 z-[75] p-3.5 rounded-2xl bg-slate-950 text-white shadow-2xl border border-white/10';
  toast.innerHTML='<div class="flex gap-2.5"><div class="w-8 h-8 rounded-xl bg-orange-500/15 text-orange-300 flex items-center justify-center shrink-0"><i data-lucide="flame" class="w-4 h-4"></i></div><div><div class="text-[10px] font-black">3 Wochen Plan-Serie</div><div class="text-[9px] text-slate-400 leading-relaxed mt-1">Freie Tage unterbrechen deine Serie nicht. Sie wächst, wenn du deinen geplanten Fokus-Rhythmus einhältst.</div></div></div>';
  document.getElementById('screenWrapper').appendChild(toast);
  lucide.createIcons();
  setTimeout(()=>toast.remove(),3800);
}
function updateHomeAfterMissionSession(){
  revealCreatedMission();
  const percent=document.getElementById('homeMissionPercent');
  const bar=document.getElementById('homeMissionProgressBar');
  const next=document.getElementById('homeMissionNextStep');
  const meta=document.getElementById('homeMissionMeta');
  if(percent) percent.textContent='52%';
  if(bar) bar.style.width='52%';
  if(next) next.textContent='Aufgaben 1–10 lösen';
  if(meta) meta.textContent='Praxisblock · ca. 35 Min.';
}

let focusMissionCreated=false;
function revealCreatedMission(){
  focusMissionCreated=true;
  const empty=document.getElementById('emptyMissionCard');
  const active=document.getElementById('activeMissionCard');
  if(empty) empty.classList.add('hidden');
  if(active) active.classList.remove('hidden');
  lucide.createIcons();
}

let selectedMissionTypeValue=null;
let selectedMissionConfidenceValue=null;
let selectedConfidenceRating=3;
function updateMissionPlanButton(){
  const btn=document.getElementById('missionPlanButton');
  if(!btn) return;
  const ready=Boolean(selectedMissionTypeValue&&selectedMissionConfidenceValue);
  btn.disabled=!ready;
  btn.classList.toggle('opacity-40',!ready);
  btn.classList.toggle('cursor-not-allowed',!ready);
}
function selectConfidenceRating(btn,value){
  selectedConfidenceRating=value;
  document.querySelectorAll('#confidenceRatingButtons .flow-chip').forEach(x=>x.classList.remove('active'));
  btn.classList.add('active');
  const label=document.getElementById('confidenceRatingLabel');
  const states={
    1:{text:'Gar nichts verstanden',classes:['text-rose-600']},
    2:{text:'Sehr wenig verstanden',classes:['text-rose-500']},
    3:{text:'Mittel verstanden',classes:['text-amber-600']},
    4:{text:'Ziemlich gut verstanden',classes:['text-emerald-600']},
    5:{text:'Vollständig verstanden',classes:['text-indigo-600']}
  };
  const state=states[value];
  label.textContent=state.text;
  label.className='text-[10px] font-bold '+state.classes.join(' ');
}

let missionImageFile=null;
let missionImagePreviewUrl=null;
let pendingAIPlan=null;
let activeAIPlan=null;

function openRealFilePicker(cameraPreferred){
  const input=document.getElementById('missionFileInput');
  if(!input)return;
  if(cameraPreferred)input.setAttribute('capture','environment'); else input.removeAttribute('capture');
  input.value='';
  input.click();
}

async function handleMissionFile(event){
  clearMissionError();
  const original=event.target.files&&event.target.files[0];
  if(!original)return;
  if(!['image/jpeg','image/png','image/webp'].includes(original.type))return showMissionError('Bitte wähle ein JPEG-, PNG- oder WEBP-Bild aus.');
  try{
    missionImageFile=await compressMissionImage(original);
    if(missionImagePreviewUrl)URL.revokeObjectURL(missionImagePreviewUrl);
    missionImagePreviewUrl=URL.createObjectURL(missionImageFile);
    const card=document.getElementById('selectedFileCard');
    card.classList.remove('hidden');card.classList.add('selected');
    document.getElementById('selectedFileName').textContent=original.name||'Foto.jpg';
    document.getElementById('selectedFileMeta').textContent=formatBytes(missionImageFile.size)+' · bereit für AI-Analyse';
    const iconBox=card.querySelector('.w-11.h-11');
    if(iconBox)iconBox.innerHTML='<img src="'+missionImagePreviewUrl+'" alt="Ausgewähltes Lernmaterial">';
    document.getElementById('goalInputHint').textContent='Foto hinzugefügt';
    lucide.createIcons();
  }catch(err){showMissionError('Das Foto konnte nicht vorbereitet werden. Bitte versuche ein anderes Bild.');}
}

async function compressMissionImage(file){
  if(file.size<=1800000)return file;
  const bitmap=await createImageBitmap(file);
  const maxSide=1800,scale=Math.min(1,maxSide/Math.max(bitmap.width,bitmap.height));
  const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(bitmap.width*scale));canvas.height=Math.max(1,Math.round(bitmap.height*scale));
  canvas.getContext('2d',{alpha:false}).drawImage(bitmap,0,0,canvas.width,canvas.height);
  const blob=await new Promise((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(new Error('compression failed')),'image/jpeg',.82));
  bitmap.close?.();
  return new File([blob],(file.name||'material').replace(/\.[^.]+$/, '')+'.jpg',{type:'image/jpeg',lastModified:Date.now()});
}
function formatBytes(bytes){return bytes<1048576?Math.max(1,Math.round(bytes/1024))+' KB':(bytes/1048576).toFixed(1).replace('.',',')+' MB'}
function showMissionError(message){const box=document.getElementById('missionError');if(!box)return;box.textContent=message;box.classList.remove('hidden');setAppScreen('goal',{immediate:true});}
function clearMissionError(){const box=document.getElementById('missionError');if(box){box.textContent='';box.classList.add('hidden')}}
function resetAnalysisRows(){document.querySelectorAll('#analysisRows .analysis-row').forEach(row=>{row.classList.add('opacity-40');row.classList.remove('done');const icon=row.querySelector('.analysis-icon');if(icon)icon.innerHTML='<span class="flow-loader-dot w-1.5 h-1.5 bg-indigo-300 rounded-full"></span>'});}
function completeAnalysisRow(index){const row=document.querySelectorAll('#analysisRows .analysis-row')[index];if(!row)return;row.classList.remove('opacity-40');row.classList.add('done');const icon=row.querySelector('.analysis-icon');if(icon)icon.innerHTML='<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-400"></i>';lucide.createIcons();}

async function analyzeGoal(){
  if(!selectedMissionTypeValue||!selectedMissionConfidenceValue)return;
  const goalText=(document.getElementById('goalPrompt').value||'').trim();
  if(!goalText&&!missionImageFile)return showMissionError('Beschreibe dein Ziel oder füge ein Foto hinzu.');
  clearMissionError();resetAnalysisRows();setAppScreen('analyzing');
  [450,950].forEach((t,i)=>setTimeout(()=>completeAnalysisRow(i),t));
  const button=document.getElementById('missionPlanButton');button?.classList.add('ai-loading');
  try{
    pendingAIPlan=await createMissionPlan({goalText,missionType:missionTypeForAPI(),confidence:confidenceForAPI(),imageFile:missionImageFile});
    completeAnalysisRow(2);renderAIConfirmation(pendingAIPlan);
    window.FocusModeApp.services.storage.setJSON('focus-mode-last-ai-plan',pendingAIPlan);
    setTimeout(()=>setAppScreen('confirm'),500);
  }catch(error){
    console.error('Focus AI request failed',error);
    const message=error.name==='AbortError'?'Die Analyse hat zu lange gedauert. Bitte versuche es erneut.':'AI-Analyse nicht verfügbar: '+(error.message||'Unbekannter Fehler');
    showMissionError(message);
  }finally{button?.classList.remove('ai-loading')}
}

function escapeHTML(value){return String(value).replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]))}
function renderAIConfirmation(plan){
  const rows=[['Fach',plan.goal.subject],['Thema',plan.goal.title],['Frist',plan.goal.deadlineText||'Nicht angegeben'],['Erkannte Aufgaben',plan.sourceAnalysis.detectedTasks||'Nicht eindeutig erkannt'],['Erkannte Seiten',plan.sourceAnalysis.detectedPages||'Nicht eindeutig erkannt'],['Geschätzter Aufwand',formatMinutes(plan.estimate.totalMinutes)+' · '+plan.sessions.length+' Sitzungen']];
  document.getElementById('aiConfirmFields').innerHTML=rows.map(([l,v])=>'<div class="ai-confirm-row"><div class="ai-confirm-label">'+escapeHTML(l)+'</div><div class="ai-confirm-value">'+escapeHTML(v)+'</div></div>').join('');
  const missing=document.getElementById('aiMissingInfo');if(plan.sourceAnalysis.missingInformation.length){missing.innerHTML='<strong>Bitte prüfen:</strong><br>'+plan.sourceAnalysis.missingInformation.map(escapeHTML).join('<br>');missing.classList.remove('hidden')}else missing.classList.add('hidden');
  lucide.createIcons();
}
function formatMinutes(total){const m=Math.max(0,Math.round(Number(total)||0)),h=Math.floor(m/60),r=m%60;return h?(h+'h'+(r?' '+r+'m':'')):(r+' Min.')}
function missionTypeLabel(type){return({exam:'Prüfung',assignment:'Aufgabe',project:'Projekt',custom:'Mission'})[type]||'Mission'}

document.addEventListener('DOMContentLoaded',()=>{
  const saved=window.FocusModeApp.services.storage.getJSON('focus-mode-active-mission',null,{removeCorrupt:true});
  if(saved){try{activeAIPlan=normalizeAIPlan(saved);pendingAIPlan=activeAIPlan;renderAIPlan(activeAIPlan);revealCreatedMission()}catch(e){window.FocusModeApp.services.storage.remove('focus-mode-active-mission')}}
  const prompt=document.getElementById('goalPrompt');prompt?.addEventListener('input',()=>{document.getElementById('goalInputHint').textContent=prompt.value.trim()?'Text bereit':'Text oder Foto hinzufügen'});
});

let missionProgress={currentSessionIndex:0,completedSessions:[],history:[],initialSessionCount:0};
var currentSessionStartedAt=window.currentSessionStartedAt??null;
var currentSessionPlannedMinutes=window.currentSessionPlannedMinutes??0;
var currentSessionActualMinutes=window.currentSessionActualMinutes??0;
var selectedSessionStatus=window.selectedSessionStatus??'partial';

function loadMissionProgress(){
  try{const saved=window.FocusModeApp.services.storage.getJSON('focus-mode-mission-progress-v7',null);if(saved&&typeof saved==='object')missionProgress={...missionProgress,...saved}}catch{}
}
function saveMissionState(){
  if(activeAIPlan)window.FocusModeApp.services.storage.setJSON('focus-mode-active-mission',activeAIPlan);
  window.FocusModeApp.services.storage.setJSON('focus-mode-mission-progress-v7',missionProgress);
}
function resetMissionProgress(plan){missionProgress={currentSessionIndex:0,completedSessions:[],history:[],initialSessionCount:plan?.sessions?.length||0};saveMissionState()}

function confirmAIPlan(){
  if(!pendingAIPlan)return;
  activeAIPlan=pendingAIPlan;
  resetMissionProgress(activeAIPlan);
  renderAIPlan(activeAIPlan);revealCreatedMission();saveMissionState();setAppScreen('plan');
}

function getCurrentMissionSession(){return activeAIPlan?.sessions?.[missionProgress.currentSessionIndex]||null}
function updateMissionHome(){
  if(!activeAIPlan)return;
  const current=getCurrentMissionSession();
  const total=Math.max(missionProgress.initialSessionCount||activeAIPlan.sessions.length,missionProgress.completedSessions.length+activeAIPlan.sessions.length-missionProgress.currentSessionIndex);
  const completed=missionProgress.completedSessions.length;
  const percent=total?Math.min(100,Math.round(completed/total*100)):0;
  document.getElementById('homeMissionPercent').textContent=percent+'%';
  document.getElementById('homeMissionProgressBar').style.width=percent+'%';
  document.getElementById('homeMissionNextStep').textContent=current?.title||(percent===100?'Mission abgeschlossen':'Kein weiterer Schritt');
  document.getElementById('homeMissionMeta').textContent=current?((current.task||current.reason)+' · ca. '+current.durationMinutes+' Min.'):'Alle Schritte bearbeitet';
}

function selectSessionStatus(button,status){
  selectedSessionStatus=status;
  document.querySelectorAll('#sessionStatusButtons .session-status-option').forEach(b=>b.classList.remove('active'));
  button.classList.add('active');
  if(typeof updateLastSessionOutcome==='function')updateLastSessionOutcome(status);
}
function resetFeedbackRows(){document.querySelectorAll('#feedbackRows .feedback-row').forEach(r=>{r.classList.add('opacity-35');r.classList.remove('done');const i=r.querySelector('.feedback-icon');if(i)i.innerHTML='<span class="flow-loader-dot w-1.5 h-1.5 bg-indigo-200 rounded-full"></span>'})}
function completeFeedbackRow(i){const r=document.querySelectorAll('#feedbackRows .feedback-row')[i];if(!r)return;r.classList.remove('opacity-35');r.classList.add('done');const icon=r.querySelector('.feedback-icon');if(icon)icon.innerHTML='<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-300"></i>';lucide.createIcons()}

async function baseShowPlanUpdateV7(){
  const current=getCurrentMissionSession();if(!current)return;
  try{const last=JSON.parse(localStorage.getItem(SESSION_CORE_LAST_KEY)||'null');if(last?.id)sessionRepository.update(last.id,{result:{outcome:selectedSessionStatus,feedback:(document.getElementById('sessionFeedbackText').value||'').trim(),rating:selectedConfidenceRating}})}catch{}
  const feedback=(document.getElementById('sessionFeedbackText').value||'').trim();
  const button=document.getElementById('adaptMissionButton');button.disabled=true;button.textContent='AI wertet aus …';
  resetFeedbackRows();setAppScreen('feedbackprocessing');[400,1000].forEach((t,i)=>setTimeout(()=>completeFeedbackRow(i),t));
  adaptationController?.abort();adaptationController=new AbortController();const timeout=setTimeout(()=>adaptationController.abort(),120000);
  const historyEntry={sessionId:current.id,title:current.title,plannedMinutes:currentSessionPlannedMinutes,actualMinutes:currentSessionActualMinutes,status:selectedSessionStatus,rating:selectedConfidenceRating,feedback,completedAt:new Date().toISOString()};
  try{
    const payload={mission:{title:activeAIPlan.goal.title,subject:activeAIPlan.goal.subject,deadlineText:activeAIPlan.goal.deadlineText},currentSession:current,plannedMinutes:currentSessionPlannedMinutes,actualMinutes:currentSessionActualMinutes,status:selectedSessionStatus,rating:selectedConfidenceRating,feedback,remainingSessions:activeAIPlan.sessions.slice(missionProgress.currentSessionIndex+1),history:missionProgress.history};
    const response=await fetch(FOCUS_AI_ADAPT_ENDPOINT,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload),signal:adaptationController.signal});
    let data={};try{data=await response.json()}catch{}
    if(!response.ok||!data.ok||!data.adaptation)throw new Error(data.error||('HTTP '+response.status));
    applyAdaptation(data.adaptation,historyEntry,current);completeFeedbackRow(2);setTimeout(()=>setAppScreen('update'),450);
  }catch(error){
    console.error('Mission adaptation failed',error);
    applyFallbackAdaptation(historyEntry,current);
    document.getElementById('adaptationMessage').textContent='Die AI war kurz nicht erreichbar. Dein Ergebnis wurde trotzdem gespeichert und der nächste Schritt lokal vorbereitet.';
    completeFeedbackRow(2);setTimeout(()=>setAppScreen('update'),450);
  }finally{clearTimeout(timeout);button.disabled=false;button.textContent='Ergebnis mit AI auswerten'}
}

function applyAdaptation(adaptation,entry,current){
  missionProgress.history.push(entry);
  const completed=selectedSessionStatus==='completed'&&!adaptation.keepCurrentStep;
  let prefix=activeAIPlan.sessions.slice(0,missionProgress.currentSessionIndex);
  if(completed){missionProgress.completedSessions.push({...current,completedAt:entry.completedAt});prefix=[...prefix,current];missionProgress.currentSessionIndex=prefix.length}
  const updated=Array.isArray(adaptation.updatedSessions)?adaptation.updatedSessions.map((s,i)=>normalizeAIPlan({goal:activeAIPlan.goal,sourceAnalysis:activeAIPlan.sourceAnalysis,estimate:{...activeAIPlan.estimate,totalMinutes:s.durationMinutes},sessions:[s]}).sessions[0]):[];
  const safeRemaining=updated.length?updated:(completed?activeAIPlan.sessions.slice(missionProgress.currentSessionIndex):activeAIPlan.sessions.slice(missionProgress.currentSessionIndex));
  activeAIPlan.sessions=[...prefix,...safeRemaining];
  activeAIPlan.estimate.totalMinutes=activeAIPlan.sessions.slice(missionProgress.currentSessionIndex).reduce((a,s)=>a+s.durationMinutes,0);
  activeAIPlan.estimate.explanation=adaptation.message||adaptation.summary||activeAIPlan.estimate.explanation;
  renderAdaptationResult(adaptation);renderAIPlan(activeAIPlan);saveMissionState();
}
function applyFallbackAdaptation(entry,current){
  missionProgress.history.push(entry);
  if(selectedSessionStatus==='completed'){missionProgress.completedSessions.push({...current,completedAt:entry.completedAt});missionProgress.currentSessionIndex=Math.min(missionProgress.currentSessionIndex+1,activeAIPlan.sessions.length)}
  const now=getCurrentMissionSession();renderAdaptationResult({message:selectedSessionStatus==='completed'?'Der Schritt wurde abgeschlossen. Als Nächstes geht es mit dem folgenden Missionsschritt weiter.':'Der Schritt bleibt offen, damit du ihn später fortsetzen kannst.',summary:'Ergebnis lokal gespeichert.',updatedSessions:now?[now]:[]});renderAIPlan(activeAIPlan);saveMissionState();
}
function renderAdaptationResult(a){const next=(Array.isArray(a.updatedSessions)&&a.updatedSessions[0])||getCurrentMissionSession();document.getElementById('adaptationMessage').textContent=a.message||'Dein Feedback wurde ausgewertet.';document.getElementById('adaptationSummary').textContent=a.summary||a.mainDifficulty||'Der Restplan wurde aktualisiert.';document.getElementById('adaptedNextTitle').textContent=next?.title||'Mission abgeschlossen';document.getElementById('adaptedNextTask').textContent=next?.task||'Alle geplanten Schritte sind bearbeitet.';document.getElementById('adaptedNextDuration').textContent=next?(next.durationMinutes+' Min.'):'Fertig';}
function finishLearningFlow(){learningFlowActive=false;renderAIPlan(activeAIPlan);saveMissionState();setAppScreen('home');lucide.createIcons()}

// Restore v7 progress after the v6 restoration handler has run.
document.addEventListener('DOMContentLoaded',()=>{setTimeout(()=>{loadMissionProgress();if(activeAIPlan){if(!missionProgress.initialSessionCount)missionProgress.initialSessionCount=activeAIPlan.sessions.length;renderAIPlan(activeAIPlan);revealCreatedMission();saveMissionState()}},0)});

// Preserve v11.2 recovery review wrapper around baseShowPlanUpdateV7
showPlanUpdate=async function(){
 if(activeSession?.type==='mission'&&activeSession.lifecycleState==='awaiting_review'){
  const feedback=(document.getElementById('sessionFeedbackText').value||'').trim(),existing=sessionRepository.getById(activeSession.id),patch={result:{...(existing?.result||{}),outcome:selectedSessionStatus,feedback,rating:selectedConfidenceRating,countAsFocus:Number(existing?.actualMinutes||currentSessionActualMinutes)>=1,reason:'user_reviewed'}};
  const saved=sessionRepository.update(activeSession.id,patch);if(!saved)return;saveSessionSnapshot(saved);activeSession={...activeSession,finalizationState:'saved'};window.activeSession=activeSession;localStorage.setItem(SESSION_CORE_STORAGE_KEY,JSON.stringify(activeSession));
 }
 try{return await baseShowPlanUpdateV7.apply(this,arguments)}finally{if(activeSession?.lifecycleState==='awaiting_review'){setActiveSession(null);sessionRecoveryCandidate=null}}
};

/* v11.5 Multi-Mission Core Controller */
function bindMissionCompatibilityV115(m){activeAIPlan=missionToLegacyV115(m);pendingAIPlan=activeAIPlan;if(!m){missionProgress={currentSessionIndex:0,completedSessions:[],history:[],initialSessionCount:0};return}const index=Math.max(0,m.steps.findIndex(x=>x.id===m.currentStepId));missionProgress={currentSessionIndex:m.currentStepId?index:m.steps.length,completedSessions:m.steps.filter(x=>x.status==='completed').map(missionClone),history:missionClone(m.progress.history),initialSessionCount:m.progress.initialStepCount||m.steps.length};focusMissionCreated=Boolean(m)}
function openMission(id){const m=missionRepository.getById(id);if(!m)return false;missionRepository.setSelected(id);bindMissionCompatibilityV115(m);renderAIPlan(activeAIPlan);renderMultiMissionUIV115();setAppScreen('plan');return true}window.openMission=openMission;
confirmAIPlan=function(){if(!pendingAIPlan)return;const result=missionRepository.create({title:pendingAIPlan.goal.title,subject:pendingAIPlan.goal.subject,type:pendingAIPlan.goal.type,deadlineText:pendingAIPlan.goal.deadlineText,estimate:pendingAIPlan.estimate,sourceAnalysis:pendingAIPlan.sourceAnalysis,steps:pendingAIPlan.sessions});if(!result.ok){showMissionError('Du kannst momentan bis zu zwei aktive Focus Missions haben. Schließe eine Mission ab oder archiviere sie, bevor du eine neue erstellst.');return}openMission(result.mission.id)};
const oldOpenGoalV115=openGoalIntake;openGoalIntake=function(){if(missionRepository.getActive().length>=2){alert('Du kannst momentan bis zu zwei aktive Focus Missions haben. Schließe eine Mission ab oder archiviere sie, bevor du eine neue erstellst.');renderMultiMissionUIV115();return}return oldOpenGoalV115.apply(this,arguments)};
getCurrentMissionSession=function(id=missionRepository.getSelectedId()){const m=missionRepository.getById(id);return m?.steps.find(x=>x.id===m.currentStepId)||null};
ensureActiveMissionId=function(){return missionRepository.getSelectedId()||missionRepository.getPrimaryId()};
createMissionSession=function(mid,sid){const m=missionRepository.getById(mid);if(!m||m.status!=='active')throw new Error('Diese Mission konnte nicht gefunden werden.');const s=m.steps.find(x=>x.id===sid);if(!s||m.currentStepId!==sid)throw new Error('Dieser Missionsschritt kann gerade nicht gestartet werden.');const idx=m.steps.findIndex(x=>x.id===sid);return makeSession({type:'mission',title:s.title,task:s.task||s.reason||s.title,plannedMinutes:s.durationMinutes,missionId:mid,missionStepId:sid,ui:{modeLabel:m.subject||'Focus Mission',progressCurrent:idx+1,progressTotal:m.steps.length}})};
prepareLearningSession=function(mid=missionRepository.getSelectedId(),sid=null){const m=missionRepository.getById(mid),step=sid?m?.steps.find(x=>x.id===sid):m?.steps.find(x=>x.id===m.currentStepId);if(!m||!step)return false;try{return startSession(createMissionSession(mid,step.id))}catch(e){alert(e.message);return false}};
const oldShowUpdateV115=showPlanUpdate;showPlanUpdate=async function(){const last=JSON.parse(localStorage.getItem(SESSION_CORE_LAST_KEY)||'null'),mid=last?.missionId||activeSession?.missionId||missionRepository.getSelectedId(),before=missionRepository.getById(mid);if(!before)return oldShowUpdateV115.apply(this,arguments);missionRepository.setSelected(mid);bindMissionCompatibilityV115(before);const r=await oldShowUpdateV115.apply(this,arguments);const after=activeAIPlan;if(after?.id===mid){const completedIds=missionProgress.completedSessions.map(x=>x.id);const steps=after.sessions.map((x,i)=>({...x,status:completedIds.includes(x.id)?'completed':i===missionProgress.currentSessionIndex?'active':'pending',completedAt:completedIds.includes(x.id)?Date.now():null}));const current=steps.find(x=>x.status==='active');missionRepository.update(mid,{steps,currentStepId:current?.id||null,status:current?'active':'completed',completedAt:current?null:Date.now(),estimate:after.estimate,progress:{completedStepIds:completedIds,history:missionProgress.history,initialStepCount:missionProgress.initialSessionCount}})}renderMultiMissionUIV115();return r};
const oldRecoveryRenderV115=renderSessionRecovery;renderSessionRecovery=function(s){if(s.type==='mission'){const m=missionRepository.getById(s.missionId),step=m?.steps.find(x=>x.id===s.missionStepId);if(m&&step){s={...s,title:step.title,task:step.task||step.reason||step.title,ui:{...s.ui,modeLabel:m.subject}}}}return oldRecoveryRenderV115(s)};
const oldResumeV115=resumeRecoveredSession;resumeRecoveredSession=function(){const s=sessionRecoveryCandidate||activeSession;if(s?.type==='mission'&&missionRepository.getById(s.missionId))missionRepository.setSelected(s.missionId);return oldResumeV115.apply(this,arguments)};

/* v13.1.1 Critical Flow Fix — Plan übernehmen */
(function installCriticalFlowFixConfirmV1311(global){
  'use strict';
  const App=global.FocusModeApp;
  const storage=App?.services?.storage;
  const sessions=App?.repositories?.sessions;
  const missions=App?.repositories?.missions;
  if(!storage||!sessions||!missions)throw new Error('v13.1 repositories are required.');

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
})(window);

/* v13.3.8 Mission Type Carousel & Confidence Track UI */
(function(){
 const typeNames=['Prüfung','Aufgabe','Projekt','Eigene Mission'];
 function syncTypeUI(btn){
  document.querySelectorAll('#missionTypeCarousel .mg-type-card').forEach((x,i)=>x.classList.toggle('active',x===btn));
  const ix=Number(btn?.dataset.index||0);document.querySelectorAll('.mg-dots i').forEach((d,i)=>d.classList.toggle('active',i===ix));
  const carousel=document.getElementById('missionTypeCarousel');
  if(carousel&&btn){
   const target=Math.max(0,btn.offsetLeft-(carousel.clientWidth-btn.offsetWidth)/2);
   carousel.scrollTo({left:target,behavior:'smooth'});
  }
 }
 const oldType=window.selectMissionType;window.selectMissionType=function(btn){oldType.call(this,btn);syncTypeUI(btn)};
 const oldConfidence=window.selectConfidence;window.selectConfidence=function(btn){oldConfidence.call(this,btn);const fill=document.querySelector('.mg-confidence-track i'),level=btn?.dataset?.confidence||'uncertain';if(fill){fill.style.removeProperty('width');fill.dataset.level=level}lucide.createIcons()};
 document.addEventListener('DOMContentLoaded',()=>{const carousel=document.getElementById('missionTypeCarousel');if(carousel){let timer;carousel.addEventListener('scroll',()=>{clearTimeout(timer);timer=setTimeout(()=>{const center=carousel.scrollLeft+carousel.clientWidth/2,cards=[...carousel.children],best=cards.reduce((a,b)=>Math.abs(b.offsetLeft+b.offsetWidth/2-center)<Math.abs(a.offsetLeft+a.offsetWidth/2-center)?b:a);if(best&&!best.classList.contains('active'))window.selectMissionType(best)},110)},{passive:true})}lucide.createIcons()});
 document.title='v15 Modular Baseline';
})();
