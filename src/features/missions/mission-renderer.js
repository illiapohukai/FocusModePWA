/**
 * Physical module: src/features/missions/mission-renderer.js
 */

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
  const inFlight=typeof window.isMissionAnalyzeInFlight==='function'&&window.isMissionAnalyzeInFlight();
  const scheduleOk=typeof window.validateMissionScheduleState==='function'?window.validateMissionScheduleState().valid:true;
  const ready=Boolean(selectedMissionTypeValue&&selectedMissionConfidenceValue&&scheduleOk&&!inFlight);
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

function confirmAIPlan(){if(!pendingAIPlan)return;activeAIPlan=pendingAIPlan;renderAIPlan(activeAIPlan);revealCreatedMission();window.FocusModeApp.services.storage.setJSON('focus-mode-active-mission',activeAIPlan);setAppScreen('plan');}
function formatMinutes(total){const m=Math.max(0,Math.round(Number(total)||0)),h=Math.floor(m/60),r=m%60;return h?(h+'h'+(r?' '+r+'m':'')):(r+' Min.')}
function missionTypeLabel(type){return({exam:'Prüfung',assignment:'Aufgabe',project:'Projekt',custom:'Mission'})[type]||'Mission'}
function renderAIPlan(plan){
  document.getElementById('aiPlanEyebrow').textContent=plan.goal.subject+' · '+missionTypeLabel(plan.goal.type);
  document.getElementById('aiPlanTitle').textContent=plan.goal.title;
  document.getElementById('aiPlanDeadline').textContent=plan.goal.deadlineText||'Nicht angegeben';
  document.getElementById('aiPlanTotal').textContent=formatMinutes(plan.estimate.totalMinutes);
  document.getElementById('aiPlanSessionCount').textContent=plan.sessions.length+' empfohlen';
  document.getElementById('aiPlanExplanation').innerHTML='<strong>Warum so?</strong> '+escapeHTML(plan.estimate.explanation);
  document.getElementById('aiPlanSteps').innerHTML=plan.sessions.map((s,i)=>'<div class="flow-step-line flex gap-3 '+(i<plan.sessions.length-1?'pb-3':'')+'"><div class="w-7 h-7 rounded-full '+(i===0?'bg-indigo-600 text-white':'bg-white border border-slate-200 text-slate-500')+' flex items-center justify-center text-[9px] font-black shrink-0">'+(i+1)+'</div><div class="flex-1 pt-0.5"><div class="flex justify-between gap-2"><div class="text-[10px] font-bold text-slate-800">'+escapeHTML(s.title)+'</div><span class="text-[9px] font-bold '+(i===0?'text-indigo-600':'text-slate-400')+'">'+s.durationMinutes+' Min.</span></div><div class="text-[9px] text-slate-400 mt-1">'+escapeHTML(s.task||s.reason)+'</div></div></div>').join('');
  const homeTitle=document.querySelector('#activeMissionCard h3');if(homeTitle)homeTitle.textContent=plan.goal.title;
  const homeMeta=document.querySelector('#activeMissionCard h3 + p');if(homeMeta)homeMeta.textContent=plan.goal.subject+' · '+(plan.goal.deadlineText||'ohne Frist');
  document.getElementById('homeMissionNextStep').textContent=plan.sessions[0].title;
  document.getElementById('homeMissionMeta').textContent=plan.sessions[0].task+' · ca. '+plan.sessions[0].durationMinutes+' Min.';
  lucide.createIcons();
}


document.addEventListener('DOMContentLoaded',()=>{
  const saved=window.FocusModeApp.services.storage.getJSON('focus-mode-active-mission',null,{removeCorrupt:true});
  if(saved){try{activeAIPlan=normalizeAIPlan(saved);pendingAIPlan=activeAIPlan;renderAIPlan(activeAIPlan);revealCreatedMission()}catch(e){window.FocusModeApp.services.storage.remove('focus-mode-active-mission')}}
  const prompt=document.getElementById('goalPrompt');prompt?.addEventListener('input',()=>{document.getElementById('goalInputHint').textContent=prompt.value.trim()?'Text bereit':'Text oder Foto hinzufügen'});
});

let missionProgress={currentSessionIndex:0,completedSessions:[],history:[],initialSessionCount:0};
let currentSessionStartedAt=null;
let currentSessionPlannedMinutes=0;
let currentSessionActualMinutes=0;
let selectedSessionStatus='partial';
let adaptationController=null;

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

function renderAIPlan(plan){
  document.getElementById('aiPlanEyebrow').textContent=plan.goal.subject+' · '+missionTypeLabel(plan.goal.type);
  document.getElementById('aiPlanTitle').textContent=plan.goal.title;
  document.getElementById('aiPlanDeadline').textContent=plan.goal.deadlineText||'Nicht angegeben';
  const remaining=plan.sessions.slice(missionProgress.currentSessionIndex);
  const remainingMinutes=remaining.reduce((a,s)=>a+(Number(s.durationMinutes)||0),0);
  document.getElementById('aiPlanTotal').textContent=formatMinutes(remainingMinutes);
  document.getElementById('aiPlanSessionCount').textContent=remaining.length+' offen';
  document.getElementById('aiPlanExplanation').innerHTML='<strong>Warum so?</strong> '+escapeHTML(plan.estimate?.explanation||'Der Plan wird nach jeder Sitzung angepasst.');
  document.getElementById('aiPlanSteps').innerHTML=plan.sessions.map((s,i)=>{
    const done=i<missionProgress.currentSessionIndex;
    const active=i===missionProgress.currentSessionIndex;
    const bubble=done?'<i data-lucide="check" class="w-3.5 h-3.5"></i>':String(i+1);
    return '<div class="flow-step-line flex gap-3 '+(i<plan.sessions.length-1?'pb-3 ':'')+(done?'mission-step-complete ':active?'mission-step-active ':'')+'"><div class="step-bubble w-7 h-7 rounded-full bg-white border border-slate-200 text-slate-500 flex items-center justify-center text-[9px] font-black shrink-0">'+bubble+'</div><div class="flex-1 pt-0.5"><div class="flex justify-between gap-2"><div class="text-[10px] font-bold '+(done?'text-slate-500 line-through':'text-slate-800')+'">'+escapeHTML(s.title)+'</div><span class="text-[9px] font-bold '+(active?'text-indigo-600':'text-slate-400')+'">'+s.durationMinutes+' Min.</span></div><div class="text-[9px] text-slate-400 mt-1">'+escapeHTML(s.task||s.reason)+'</div></div></div>';
  }).join('');
  const homeTitle=document.querySelector('#activeMissionCard h3');if(homeTitle)homeTitle.textContent=plan.goal.title;
  const homeMeta=document.querySelector('#activeMissionCard h3 + p');if(homeMeta)homeMeta.textContent=plan.goal.subject+' · '+(plan.goal.deadlineText||'ohne Frist');
  const startButton=document.getElementById('missionStartButton');
  if(startButton){const current=getCurrentMissionSession();startButton.disabled=!current;startButton.innerHTML=current?'<i class="w-4 h-4" data-lucide="play"></i> Schritt '+(missionProgress.currentSessionIndex+1)+' starten':'<i class="w-4 h-4" data-lucide="check"></i> Mission abgeschlossen';}
  updateMissionHome();lucide.createIcons();
}


function selectSessionStatus(button,status){selectedSessionStatus=status;document.querySelectorAll('#sessionStatusButtons .session-status-option').forEach(b=>b.classList.remove('active'));button.classList.add('active')}
function resetFeedbackRows(){document.querySelectorAll('#feedbackRows .feedback-row').forEach(r=>{r.classList.add('opacity-35');r.classList.remove('done');const i=r.querySelector('.feedback-icon');if(i)i.innerHTML='<span class="flow-loader-dot w-1.5 h-1.5 bg-indigo-200 rounded-full"></span>'})}
function completeFeedbackRow(i){const r=document.querySelectorAll('#feedbackRows .feedback-row')[i];if(!r)return;r.classList.remove('opacity-35');r.classList.add('done');const icon=r.querySelector('.feedback-icon');if(icon)icon.innerHTML='<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-300"></i>';lucide.createIcons()}

async function showPlanUpdate(){
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

let expandedFutureStepV81=null;
function toggleFutureStepV81(index){expandedFutureStepV81=expandedFutureStepV81===index?null:index;if(activeAIPlan)renderAIPlan(activeAIPlan)}
function renderAIPlan(plan){
 document.getElementById('aiPlanEyebrow').textContent=plan.goal.subject+' · '+missionTypeLabel(plan.goal.type);
 document.getElementById('aiPlanTitle').textContent=plan.goal.title;
 document.getElementById('aiPlanDeadline').textContent=plan.goal.deadlineText||'Nicht angegeben';
 const remaining=plan.sessions.slice(missionProgress.currentSessionIndex),minutes=remaining.reduce((a,s)=>a+(Number(s.durationMinutes)||0),0);
 document.getElementById('aiPlanTotal').textContent=formatMinutes(minutes);document.getElementById('aiPlanSessionCount').textContent=remaining.length+' offen';
 document.getElementById('aiPlanExplanation').innerHTML='<strong>Warum so?</strong> '+escapeHTML(plan.estimate?.explanation||'Der Plan wird nach jeder Sitzung angepasst.');
 document.getElementById('aiPlanSteps').innerHTML=plan.sessions.map((s,i)=>{const done=i<missionProgress.currentSessionIndex,active=i===missionProgress.currentSessionIndex,future=i>missionProgress.currentSessionIndex,expanded=future&&expandedFutureStepV81===i;const state=done?'mission-step-complete':active?'mission-step-active':'mission-step-future'+(expanded?' expanded':'');const click=future?' onclick="toggleFutureStepV81('+i+')"':'';const bubble=done?'<i data-lucide="check" class="w-3.5 h-3.5"></i>':String(i+1);return '<div class="flow-step-line '+state+' flex"'+click+'><div class="step-bubble rounded-full bg-white border border-slate-200 text-slate-500 flex items-center justify-center font-black shrink-0">'+bubble+'</div><div class="step-content flex-1 min-w-0"><div class="step-head flex justify-between gap-3"><div class="step-title font-bold">'+escapeHTML(s.title)+'</div><span class="step-time font-bold">'+s.durationMinutes+' Min.</span></div><div class="step-task" style="-webkit-line-clamp:'+(expanded?'unset':'2')+'">'+escapeHTML(s.task||s.reason)+'</div></div></div>'}).join('');
 const homeTitle=document.querySelector('#activeMissionCard h3');if(homeTitle)homeTitle.textContent=plan.goal.title;const homeMeta=document.querySelector('#activeMissionCard h3 + p');if(homeMeta)homeMeta.textContent=plan.goal.subject+' · '+(plan.goal.deadlineText||'ohne Frist');
 const current=getCurrentMissionSession();document.getElementById('homeMissionNextStep').textContent=current?.title||'Mission abgeschlossen';document.getElementById('homeMissionMeta').textContent=current?((current.task||current.reason)+' · ca. '+current.durationMinutes+' Min.'):'Alle Schritte bearbeitet';
 const start=document.getElementById('missionStartButton');if(start){start.disabled=!current;start.innerHTML=current?'<i class="w-4 h-4" data-lucide="play"></i> Schritt '+(missionProgress.currentSessionIndex+1)+' starten':'<i class="w-4 h-4" data-lucide="check"></i> Mission abgeschlossen'}
 updateMissionHome();lucide.createIcons();
}
// Real week card only. Rank and Plan-Serie stay intact.
const oldFinishV81=finishLearningFlow;finishLearningFlow=function(){oldFinishV81();setTimeout(refreshHomeStats,20)};
document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>{refreshHomeStats();if(activeAIPlan)renderAIPlan(activeAIPlan)},30));

function syncHomeHeroV103(){
  if(typeof window.renderHomeCarouselV116==='function'&&typeof missionRepository!=='undefined'){
    window.renderHomeCarouselV116();
    return;
  }
  const empty=document.getElementById('emptyMissionCard'),active=document.getElementById('activeMissionCard'),current=(activeAIPlan&&typeof getCurrentMissionSession==='function')?getCurrentMissionSession():null,has=Boolean(activeAIPlan&&current);
  empty?.classList.toggle('hidden',has);active?.classList.toggle('hidden',!has);
  if(!has){lucide.createIcons();return}
  const title=document.getElementById('homeMissionTitle')||document.querySelector('#activeMissionCard h3'),meta=document.getElementById('homeMissionSubtitle')||document.querySelector('#activeMissionCard h3+p'),next=document.getElementById('homeMissionNextStep'),task=document.getElementById('homeMissionMeta'),durVal=document.getElementById('homeMissionDurationValue');
  if(title){title.textContent=activeAIPlan.goal?.title||'Focus Mission'}
  if(meta)meta.textContent=(activeAIPlan.goal?.subject||'Allgemein')+' · '+(activeAIPlan.goal?.deadlineText||'ohne Frist');
  if(next)next.textContent=current.title||'Nächster Schritt';
  if(task)task.textContent=current.task||current.reason||'Nächsten Schritt bearbeiten';
  if(durVal)durVal.textContent=String(current.durationMinutes||25);
  lucide.createIcons();
}
const oldU103=updateMissionHome;updateMissionHome=function(){oldU103.apply(this,arguments);syncHomeHeroV103()};const oldR103=revealCreatedMission;revealCreatedMission=function(){oldR103.apply(this,arguments);syncHomeHeroV103()};const oldA103=applyAppScreenImmediately;applyAppScreenImmediately=function(screen){const r=oldA103.apply(this,arguments);if(screen==='home')syncHomeHeroV103();return r};document.addEventListener('DOMContentLoaded',()=>setTimeout(syncHomeHeroV103,70));

function applyMissionDepthV105(center=false){const list=document.getElementById('aiPlanSteps');if(!list)return;const cards=[...list.querySelectorAll('.flow-step-line')],ai=cards.findIndex(c=>c.classList.contains('mission-step-active'));cards.forEach((c,i)=>{c.classList.remove('mission-depth-near','mission-depth-far','mission-depth-before','mission-depth-after');if(ai<0||i===ai)return;c.classList.add(Math.abs(i-ai)===1?'mission-depth-near':'mission-depth-far',i<ai?'mission-depth-before':'mission-depth-after')});const active=cards[ai];if(active&&!active.querySelector('.mission-active-start')){const b=document.createElement('button');b.type='button';b.className='mission-active-start';b.innerHTML='<i data-lucide="play"></i><span>Schritt '+(ai+1)+' starten</span>';b.onclick=e=>{e.stopPropagation();prepareLearningSession()};active.appendChild(b)}lucide.createIcons();if(center&&active)requestAnimationFrame(()=>setTimeout(()=>active.scrollIntoView({behavior:'smooth',block:'center'}),80))}
const renderBeforeDepthV105=renderAIPlan;renderAIPlan=function(){const r=renderBeforeDepthV105.apply(this,arguments);applyMissionDepthV105(false);return r};const applyBeforeDepthV105=applyAppScreenImmediately;applyAppScreenImmediately=function(screen){const r=applyBeforeDepthV105.apply(this,arguments);if(screen==='plan')setTimeout(()=>applyMissionDepthV105(true),30);return r};document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>applyMissionDepthV105(false),90));

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
 document.title='v17.4.1 Mission Generation Repair';
})();

/* ============================================================================
   v17.4.1 Mission Scheduling UI, Viewport-Fixed Calendar & Generation Integration
   ============================================================================ */
(function(){
  'use strict';

  function registerScheduleI18N() {
    if (typeof I18N !== 'undefined' && I18N.en) {
      Object.assign(I18N.en, {
        'ZEITPLAN': 'SCHEDULE',
        'OPTIONAL': 'OPTIONAL',
        'Optional': 'Optional',
        'Plane, wann du beginnen möchtest und bis wann dein Ziel erreicht sein soll.': 'Plan when you want to start and when your goal should be reached.',
        'ZIELTERMIN': 'DEADLINE',
        'Wann ist deine Prüfung oder Abgabe?': 'When is your exam or deadline?',
        'Wähle ein Datum, um deinen Vorbereitungszeitraum sichtbar zu planen.': 'Choose a date to visualize your preparation window.',
        'Zurücksetzen': 'Reset',
        'Auswählen': 'Select',
        'Ändern': 'Change',
        'PRÜFUNG / ABGABE': 'EXAM / DEADLINE',
        'Datum auswählen': 'Select date',
        'START DER VORBEREITUNG': 'PREPARATION START',
        'Sofort': 'Immediately',
        '1 Woche vorher': '1 week before',
        '2 Wochen vorher': '2 weeks before',
        'Eigene Wahl': 'Custom date',
        'Empfohlen': 'Recommended',
        'Wähle zuerst einen Prüfungstermin oder eine Abgabe.': 'Select an exam date or deadline first.',
        'START': 'START',
        'ZIEL': 'GOAL',
        'PRÜFUNG': 'DEADLINE',
        'Lernrhythmus anpassen': 'Adjust study rhythm',
        'WANN KANNST DU LERNEN?': 'WHEN CAN YOU STUDY?',
        'Keine Auswahl bedeutet, dass dein Plan flexibel verteilt werden kann.': 'No selection means your plan can be distributed flexibly.',
        'Unter der Woche': 'Weekdays',
        'Wochenende': 'Weekend',
        'Flexibel': 'Flexible',
        'BEVORZUGTE SITZUNGSDAUER': 'PREFERRED SESSION LENGTH',
        '20 Min.': '20 min',
        '30 Min.': '30 min',
        '45 Min.': '45 min',
        'AI wählt': 'AI picks',
        'AI entscheidet': 'AI decides',
        'Kurz & fokussiert': 'Short & focused',
        'Ausgewogen': 'Balanced',
        'Tiefer Fokus': 'Deep focus',
        'Übernehmen': 'Apply',
        'Abbrechen': 'Cancel',
        'Erneut versuchen': 'Try Again',
        'Schließen': 'Dismiss',
        'Foto entfernen': 'Remove photo'
      });
    }
  }
  registerScheduleI18N();

  const missionScheduleState = {
    deadlineDate: null,           // ISO string YYYY-MM-DD or null
    preparationStartDate: null,   // ISO string YYYY-MM-DD or null
    startMode: 'immediate',       // "immediate" | "one_week_before" | "two_weeks_before" | "custom"
    preferredWeekdays: [],        // e.g. [1, 2, 3, 4, 5] for Mon-Fri
    preferredSessionMinutes: null // 20 | 30 | 45 | null (AI decides)
  };
  window.missionScheduleState = missionScheduleState;

  let userManuallyChoseStartMode = false;
  let startClampedNotice = false;
  let prefsExpanded = false;
  let prefsUserInteracted = false;
  let calendarSheetMode = 'deadline'; // 'deadline' | 'start'
  let calendarViewYear = new Date().getFullYear();
  let calendarViewMonth = new Date().getMonth(); // 0..11
  let calendarPendingDate = null;
  let lastConfiguredScheduleSnapshot = null;

  /* Calendar Viewport Scroll-Lock & Focus Restoration State */
  let calendarCloseTimer = null;
  let calendarReturnFocusEl = null;
  let savedGoalScrollTop = 0;
  let savedPageScrollTop = 0;
  let savedWindowScrollX = 0;
  let savedWindowScrollY = 0;
  let savedGoalOverflowY = '';
  let savedGoalTouchAction = '';
  let calendarScrollLocked = false;
  let visualViewportCleanup = null;

  function getActiveLang() {
    const saved = window.FocusModeApp?.services?.storage?.getText('focus-mode-language', '');
    const raw = (saved || document.documentElement.lang || 'de').toLowerCase();
    return raw === 'en' ? 'en' : 'de';
  }

  function toLocalISODate(d) {
    if (!(d instanceof Date) || isNaN(d.getTime())) return null;
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + day;
  }

  function parseLocalISODate(iso) {
    if (!iso || typeof iso !== 'string') return null;
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso.trim());
    if (!m) return null;
    const y = Number(m[1]);
    const mo = Number(m[2]) - 1;
    const d = Number(m[3]);
    const dt = new Date(y, mo, d, 0, 0, 0, 0);
    if (dt.getFullYear() !== y || dt.getMonth() !== mo || dt.getDate() !== d) return null;
    return dt;
  }

  function getTodayISO() {
    const now = new Date();
    now.setHours(0, 0, 0, 0);
    return toLocalISODate(now);
  }

  function addDaysISO(iso, deltaDays) {
    const dt = parseLocalISODate(iso);
    if (!dt) return null;
    dt.setDate(dt.getDate() + Number(deltaDays || 0));
    return toLocalISODate(dt);
  }

  function diffCalendarDays(startISO, endISO) {
    const a = parseLocalISODate(startISO);
    const b = parseLocalISODate(endISO);
    if (!a || !b) return 0;
    const utcA = Date.UTC(a.getFullYear(), a.getMonth(), a.getDate());
    const utcB = Date.UTC(b.getFullYear(), b.getMonth(), b.getDate());
    return Math.round((utcB - utcA) / 86400000);
  }

  function getISOWeekday(iso) {
    const dt = parseLocalISODate(iso);
    if (!dt) return 1;
    return ((dt.getDay() + 6) % 7) + 1; // 1 = Monday .. 7 = Sunday
  }

  function formatLongDate(iso) {
    const dt = parseLocalISODate(iso);
    if (!dt) return '';
    const locale = getActiveLang() === 'en' ? 'en-US' : 'de-DE';
    try {
      return new Intl.DateTimeFormat(locale, {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric'
      }).format(dt);
    } catch {
      return iso;
    }
  }

  function formatShortDate(iso, allowTodayLabel) {
    const dt = parseLocalISODate(iso);
    if (!dt) return '';
    const todayISO = getTodayISO();
    const lang = getActiveLang();
    if (allowTodayLabel && iso === todayISO) {
      return lang === 'en' ? 'Today' : 'Heute';
    }
    const locale = lang === 'en' ? 'en-US' : 'de-DE';
    try {
      return new Intl.DateTimeFormat(locale, {
        day: '2-digit',
        month: 'short'
      }).format(dt);
    } catch {
      return iso;
    }
  }

  function getRecommendedStartMode(deadlineISO) {
    if (!deadlineISO) return 'immediate';
    const days = diffCalendarDays(getTodayISO(), deadlineISO);
    if (days <= 7) return 'immediate';
    if (days <= 21) return 'one_week_before';
    if (days <= 60) return 'two_weeks_before';
    return 'custom';
  }

  function getModeLabel(mode) {
    const isEn = getActiveLang() === 'en';
    switch (mode) {
      case 'immediate': return isEn ? 'Immediately' : 'Sofort';
      case 'one_week_before': return isEn ? '1 week before' : '1 Woche vorher';
      case 'two_weeks_before': return isEn ? '2 weeks before' : '2 Wochen vorher';
      case 'custom': return isEn ? 'Custom date' : 'Eigene Wahl';
      default: return isEn ? 'Immediately' : 'Sofort';
    }
  }

  function recomputePreparationStartDate() {
    startClampedNotice = false;
    const deadlineISO = missionScheduleState.deadlineDate;
    if (!deadlineISO) {
      missionScheduleState.preparationStartDate = null;
      return;
    }
    const todayISO = getTodayISO();
    const mode = missionScheduleState.startMode || 'immediate';
    if (mode === 'immediate') {
      missionScheduleState.preparationStartDate = todayISO;
      return;
    }
    if (mode === 'one_week_before' || mode === 'two_weeks_before') {
      const offset = mode === 'one_week_before' ? -7 : -14;
      const candidate = addDaysISO(deadlineISO, offset);
      if (diffCalendarDays(todayISO, candidate) < 0) {
        missionScheduleState.preparationStartDate = todayISO;
        startClampedNotice = true;
      } else {
        missionScheduleState.preparationStartDate = candidate;
      }
      return;
    }
    if (mode === 'custom') {
      if (!missionScheduleState.preparationStartDate) {
        const fallback = addDaysISO(deadlineISO, -14);
        missionScheduleState.preparationStartDate = diffCalendarDays(todayISO, fallback) >= 0 ? fallback : todayISO;
      }
    }
  }

  function hasMatchingWeekdayInRange(startISO, endISO, weekdays) {
    if (!Array.isArray(weekdays) || weekdays.length === 0) return true;
    const span = diffCalendarDays(startISO, endISO);
    if (span < 0) return false;
    const maxCheck = Math.min(span, 7);
    const set = new Set(weekdays.map(Number));
    for (let i = 0; i <= maxCheck; i++) {
      const cur = addDaysISO(startISO, i);
      if (set.has(getISOWeekday(cur))) return true;
    }
    return false;
  }

  function validateMissionScheduleState() {
    const isEn = getActiveLang() === 'en';
    const deadlineISO = missionScheduleState.deadlineDate;
    if (!deadlineISO) {
      return { valid: true, level: null, message: null };
    }
    const todayISO = getTodayISO();
    const daysToDeadline = diffCalendarDays(todayISO, deadlineISO);
    if (daysToDeadline < 0) {
      return {
        valid: false,
        level: 'error',
        message: isEn
          ? 'The exam or deadline date cannot be in the past.'
          : 'Das Prüfungs- oder Abgabedatum darf nicht in der Vergangenheit liegen.'
      };
    }

    const startISO = missionScheduleState.preparationStartDate || todayISO;
    const prepSpan = diffCalendarDays(startISO, deadlineISO);
    if (prepSpan < 0) {
      return {
        valid: false,
        level: 'error',
        message: isEn
          ? 'Preparation start must be on or before the exam or deadline.'
          : 'Der Vorbereitungsstart muss vor der Prüfung oder Abgabe liegen.'
      };
    }

    if (missionScheduleState.preferredWeekdays.length > 0) {
      if (!hasMatchingWeekdayInRange(startISO, deadlineISO, missionScheduleState.preferredWeekdays)) {
        return {
          valid: false,
          level: 'error',
          message: isEn
            ? 'No selected study day falls within the chosen period.'
            : 'Im gewählten Zeitraum liegt kein ausgewählter Lerntag.'
        };
      }
    }

    if (daysToDeadline === 0) {
      return {
        valid: true,
        level: 'info',
        message: isEn ? 'The schedule starts today.' : 'Der Zeitplan beginnt heute.'
      };
    }

    if (startClampedNotice) {
      return {
        valid: true,
        level: 'info',
        message: isEn
          ? 'Start date was adjusted to today.'
          : 'Der Zeitplan beginnt heute.'
      };
    }

    if (prepSpan <= 2) {
      return {
        valid: true,
        level: 'warn',
        message: isEn ? 'Very little preparation time.' : 'Sehr wenig Vorbereitungszeit.'
      };
    }

    return { valid: true, level: null, message: null };
  }
  window.validateMissionScheduleState = validateMissionScheduleState;

  function formatWeekdayList(weekdays, isEn) {
    const wk = [...new Set((weekdays || []).map(Number).filter(n => n >= 1 && n <= 7))].sort((a, b) => a - b);
    if (wk.length === 0) return isEn ? 'Flexible' : 'Flexibel';
    const joined = wk.join(',');
    if (joined === '1,2,3,4,5') return isEn ? 'Weekdays' : 'Unter der Woche';
    if (joined === '6,7') return isEn ? 'Weekend' : 'Wochenende';
    const names = isEn ? ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] : ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
    return wk.map(d => names[d - 1]).join(', ');
  }

  function renderScheduleTrajectoryNodes(startISO, endISO, isEn) {
    const container = document.getElementById('mgTrajectoryNodes');
    if (!container) return;
    const span = diffCalendarDays(startISO, endISO);
    if (span < 0) {
      container.replaceChildren();
      return;
    }
    const maxNodes = 7;
    const count = Math.min(maxNodes, Math.max(2, span + 1));
    const sampledISOs = [];
    for (let i = 0; i < count; i++) {
      const offset = count === 1 ? 0 : Math.round((i * span) / (count - 1));
      sampledISOs.push(addDaysISO(startISO, offset));
    }
    const dayLabels = isEn ? ['M', 'T', 'W', 'T', 'F', 'S', 'S'] : ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
    const prefSet = new Set(missionScheduleState.preferredWeekdays.map(Number));

    container.replaceChildren();
    sampledISOs.forEach((iso, idx) => {
      const wd = getISOWeekday(iso);
      const isEndpoint = idx === 0;
      const isDeadline = idx === sampledISOs.length - 1;
      const isAvailable = prefSet.size === 0 || prefSet.has(wd);
      const step = document.createElement('span');
      step.className = 'mg-traj-step'
        + (isAvailable ? ' is-available' : '')
        + (isEndpoint ? ' is-endpoint' : '')
        + (isDeadline ? ' is-deadline' : '');
      const dot = document.createElement('span');
      dot.className = 'mg-traj-dot';
      const lbl = document.createElement('span');
      lbl.className = 'mg-traj-lbl';
      lbl.textContent = dayLabels[wd - 1] || '';
      step.appendChild(dot);
      step.appendChild(lbl);
      container.appendChild(step);
    });
  }

  function updatePrefsAccordionUI() {
    const toggleBtn = document.getElementById('mgPrefsToggleBtn');
    const panel = document.getElementById('mgPrefsPanel');
    if (!toggleBtn || !panel) return;
    panel.classList.toggle('hidden', !prefsExpanded);
    toggleBtn.setAttribute('aria-expanded', prefsExpanded ? 'true' : 'false');
  }

  function toggleMissionSchedulePrefs(forceState) {
    if (typeof forceState === 'boolean') {
      prefsExpanded = forceState;
    } else {
      prefsUserInteracted = true;
      prefsExpanded = !prefsExpanded;
    }
    updatePrefsAccordionUI();
  }
  window.toggleMissionSchedulePrefs = toggleMissionSchedulePrefs;

  function syncMissionScheduleUI() {
    const isEn = getActiveLang() === 'en';
    const board = document.getElementById('mgScheduleBoard');
    const deadlineTrigger = document.getElementById('mgDeadlineTrigger');
    const deadlineVal = document.getElementById('mgDeadlineValue');
    const ctaTag = document.getElementById('mgDeadlineCtaTag');
    const clearBtn = document.getElementById('mgDeadlineClearBtn');
    const prepBlock = document.getElementById('mgPrepStartBlock');
    const recBadge = document.getElementById('mgStartRecBadge');
    const timelineEmpty = document.getElementById('mgTimelineEmpty');
    const timelineActive = document.getElementById('mgTimelineActive');
    const startLabel = document.getElementById('mgTimelineStartLabel');
    const endLabel = document.getElementById('mgTimelineEndLabel');
    const daysBadge = document.getElementById('mgTimelineDaysBadge');
    const liveSummary = document.getElementById('mgScheduleLiveSummary');
    const prefsSummary = document.getElementById('mgPrefsSummary');
    const noticeBox = document.getElementById('mgScheduleNotice');
    const noticeText = document.getElementById('mgScheduleNoticeText');

    const hasDeadline = Boolean(missionScheduleState.deadlineDate);

    if (board) {
      board.classList.toggle('has-deadline', hasDeadline);
    }
    if (deadlineTrigger) {
      deadlineTrigger.classList.toggle('has-value', hasDeadline);
    }
    if (deadlineVal) {
      if (hasDeadline) {
        deadlineVal.textContent = formatLongDate(missionScheduleState.deadlineDate);
        deadlineVal.classList.remove('is-placeholder');
      } else {
        deadlineVal.textContent = isEn ? 'Select date' : 'Datum auswählen';
        deadlineVal.classList.add('is-placeholder');
      }
    }
    if (ctaTag) {
      ctaTag.textContent = hasDeadline ? (isEn ? 'Change' : 'Ändern') : (isEn ? 'Select' : 'Auswählen');
    }
    if (clearBtn) {
      clearBtn.classList.toggle('hidden', !hasDeadline);
    }
    if (daysBadge) {
      daysBadge.classList.toggle('hidden', !hasDeadline);
    }
    if (prepBlock) {
      prepBlock.classList.toggle('hidden', !hasDeadline);
    }

    const recommendedMode = getRecommendedStartMode(missionScheduleState.deadlineDate);
    if (recBadge && hasDeadline) {
      recBadge.textContent = (isEn ? 'Recommended: ' : 'Empfohlen: ') + getModeLabel(recommendedMode);
    }

    document.querySelectorAll('#mgStartModeChips .mg-start-chip').forEach(btn => {
      const mode = btn.getAttribute('data-start-mode');
      const isSelected = mode === missionScheduleState.startMode;
      const isRec = hasDeadline && mode === recommendedMode;
      btn.classList.toggle('active', isSelected);
      btn.classList.toggle('is-recommended', isRec);
      btn.setAttribute('aria-pressed', isSelected ? 'true' : 'false');
      const labelEl = btn.querySelector('.mg-start-chip-label') || btn.querySelector('span');
      if (labelEl && mode === 'custom') {
        if (missionScheduleState.startMode === 'custom' && missionScheduleState.preparationStartDate) {
          labelEl.textContent = (isEn ? 'Custom · ' : 'Eigene Wahl · ') + formatShortDate(missionScheduleState.preparationStartDate, false);
        } else {
          labelEl.textContent = isEn ? 'Custom date' : 'Eigene Wahl';
        }
      }
      const recMark = btn.querySelector('.mg-start-rec-mark');
      if (recMark) {
        recMark.classList.toggle('hidden', !isRec);
        recMark.textContent = isEn ? 'Recommended' : 'Empfohlen';
      }
    });

    if (timelineEmpty && timelineActive) {
      timelineEmpty.classList.toggle('hidden', hasDeadline);
      timelineActive.classList.toggle('hidden', !hasDeadline);
    }

    const rhythmTxt = formatWeekdayList(missionScheduleState.preferredWeekdays, isEn);
    const durTxt = missionScheduleState.preferredSessionMinutes
      ? (missionScheduleState.preferredSessionMinutes + (isEn ? ' min' : ' Min.'))
      : (isEn ? 'AI picks' : 'AI wählt');

    if (prefsSummary) {
      prefsSummary.textContent = rhythmTxt + ' · ' + durTxt;
    }

    if (hasDeadline) {
      const startISO = missionScheduleState.preparationStartDate || getTodayISO();
      const endISO = missionScheduleState.deadlineDate;
      if (startLabel) startLabel.textContent = formatShortDate(startISO, true);
      if (endLabel) endLabel.textContent = formatShortDate(endISO, false);
      const rawDiff = diffCalendarDays(startISO, endISO);
      const prepDays = Math.max(0, rawDiff);
      if (daysBadge) {
        if (rawDiff < 0) {
          daysBadge.textContent = isEn ? 'INVALID PERIOD' : 'UNGÜLTIGER ZEITRAUM';
        } else if (prepDays === 0) {
          daysBadge.textContent = isEn ? 'TODAY · 1 DAY PREP' : 'HEUTE · 1 TAG VORBEREITUNG';
        } else if (prepDays === 1) {
          daysBadge.textContent = isEn ? '1 DAY PREPARATION' : '1 TAG VORBEREITUNG';
        } else {
          daysBadge.textContent = prepDays + (isEn ? ' DAYS PREPARATION' : ' TAGE VORBEREITUNG');
        }
      }
      renderScheduleTrajectoryNodes(startISO, endISO, isEn);
      if (liveSummary) {
        liveSummary.textContent = rhythmTxt + ' · ' + (missionScheduleState.preferredSessionMinutes
          ? (missionScheduleState.preferredSessionMinutes + (isEn ? ' min sessions' : ' Min.-Blöcke'))
          : (isEn ? 'AI picks session length' : 'AI wählt Sitzungsdauer'));
      }
    } else if (liveSummary) {
      liveSummary.textContent = '';
    }

    // Weekday buttons & presets
    const wkLabels = isEn ? ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] : ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
    const wkSet = new Set(missionScheduleState.preferredWeekdays.map(Number));
    document.querySelectorAll('#mgWeekdayRow .mg-weekday-btn').forEach(btn => {
      const dayNum = Number(btn.getAttribute('data-weekday'));
      const active = wkSet.has(dayNum);
      if (wkLabels[dayNum - 1]) btn.textContent = wkLabels[dayNum - 1];
      btn.classList.toggle('active', active);
      btn.setAttribute('aria-pressed', active ? 'true' : 'false');
    });

    const sortedWk = [...wkSet].sort((a, b) => a - b).join(',');
    const isWeekdaysPreset = sortedWk === '1,2,3,4,5';
    const isWeekendPreset = sortedWk === '6,7';
    const isFlexiblePreset = wkSet.size === 0;
    document.querySelectorAll('#mgWeekdayPresets .mg-preset-chip').forEach(chip => {
      const preset = chip.getAttribute('data-preset');
      const active =
        (preset === 'weekdays' && isWeekdaysPreset) ||
        (preset === 'weekend' && isWeekendPreset) ||
        (preset === 'flexible' && isFlexiblePreset);
      chip.classList.toggle('active', active);
      chip.setAttribute('aria-pressed', active ? 'true' : 'false');
    });

    // Preferred session duration chips
    document.querySelectorAll('#mgDurationPrefChips .mg-dur-chip').forEach(chip => {
      const val = chip.getAttribute('data-minutes');
      const active = val === 'auto'
        ? missionScheduleState.preferredSessionMinutes === null
        : Number(val) === missionScheduleState.preferredSessionMinutes;
      chip.classList.toggle('active', active);
      chip.setAttribute('aria-pressed', active ? 'true' : 'false');
    });

    const hasCustomPrefs = missionScheduleState.preferredWeekdays.length > 0 || missionScheduleState.preferredSessionMinutes !== null;
    if (hasCustomPrefs && !prefsUserInteracted) {
      prefsExpanded = true;
    }
    updatePrefsAccordionUI();

    // Validation & Notice
    const validation = validateMissionScheduleState();
    if (noticeBox && noticeText) {
      if (validation.message) {
        noticeBox.classList.remove('hidden', 'is-info', 'is-warn', 'is-error');
        noticeBox.classList.add('is-' + (validation.level || 'info'));
        noticeText.textContent = validation.message;
      } else {
        noticeBox.classList.add('hidden');
        noticeText.textContent = '';
      }
    }

    if (typeof window.updateMissionPlanButton === 'function') {
      window.updateMissionPlanButton();
    }

    if (typeof window.lucide?.createIcons === 'function') {
      window.lucide.createIcons();
    }
  }
  window.syncMissionScheduleUI = syncMissionScheduleUI;

  function selectDeadlineDate(iso) {
    const parsed = parseLocalISODate(iso);
    if (!parsed) return false;
    const todayISO = getTodayISO();
    if (diffCalendarDays(todayISO, iso) < 0) return false;

    missionScheduleState.deadlineDate = iso;
    const recommended = getRecommendedStartMode(iso);
    if (!userManuallyChoseStartMode) {
      missionScheduleState.startMode = recommended === 'custom' ? 'two_weeks_before' : recommended;
    }
    recomputePreparationStartDate();
    syncMissionScheduleUI();
    return true;
  }

  function clearMissionDeadline(ev) {
    if (ev && typeof ev.stopPropagation === 'function') ev.stopPropagation();
    missionScheduleState.deadlineDate = null;
    missionScheduleState.preparationStartDate = null;
    missionScheduleState.startMode = 'immediate';
    userManuallyChoseStartMode = false;
    startClampedNotice = false;
    syncMissionScheduleUI();
  }
  window.clearMissionDeadline = clearMissionDeadline;

  function selectMissionStartMode(mode) {
    if (!['immediate', 'one_week_before', 'two_weeks_before', 'custom'].includes(mode)) return;
    userManuallyChoseStartMode = true;
    missionScheduleState.startMode = mode;
    if (mode === 'custom') {
      if (!missionScheduleState.preparationStartDate) {
        recomputePreparationStartDate();
      }
      syncMissionScheduleUI();
      openMissionCalendarSheet('start', document.querySelector('#mgStartModeChips [data-start-mode="custom"]'));
      return;
    }
    recomputePreparationStartDate();
    syncMissionScheduleUI();
  }
  window.selectMissionStartMode = selectMissionStartMode;

  function toggleMissionWeekday(dayNum) {
    const n = Number(dayNum);
    if (n < 1 || n > 7) return;
    const idx = missionScheduleState.preferredWeekdays.indexOf(n);
    if (idx >= 0) {
      missionScheduleState.preferredWeekdays.splice(idx, 1);
    } else {
      missionScheduleState.preferredWeekdays.push(n);
      missionScheduleState.preferredWeekdays.sort((a, b) => a - b);
    }
    syncMissionScheduleUI();
  }
  window.toggleMissionWeekday = toggleMissionWeekday;

  function applyMissionWeekdayPreset(preset) {
    if (preset === 'weekdays') {
      missionScheduleState.preferredWeekdays = [1, 2, 3, 4, 5];
    } else if (preset === 'weekend') {
      missionScheduleState.preferredWeekdays = [6, 7];
    } else {
      missionScheduleState.preferredWeekdays = [];
    }
    syncMissionScheduleUI();
  }
  window.applyMissionWeekdayPreset = applyMissionWeekdayPreset;

  function selectMissionPreferredDuration(minutes) {
    if (minutes === null || minutes === undefined || minutes === 'auto') {
      missionScheduleState.preferredSessionMinutes = null;
    } else {
      const m = Number(minutes);
      missionScheduleState.preferredSessionMinutes = [20, 30, 45].includes(m) ? m : null;
    }
    syncMissionScheduleUI();
  }
  window.selectMissionPreferredDuration = selectMissionPreferredDuration;

  /* Viewport-Level Calendar Bottom Sheet */
  function ensureCalendarMountedToBody(sheet) {
    if (sheet && document.body && sheet.parentElement !== document.body) {
      document.body.appendChild(sheet);
    }
  }

  function syncVisualViewportInset(sheet) {
    if (!sheet) return;
    const vv = window.visualViewport;
    if (!vv) {
      sheet.style.setProperty('--mg-cal-vv-bottom', '0px');
      return;
    }
    const bottomInset = Math.max(0, Math.round(window.innerHeight - (vv.offsetTop + vv.height)));
    sheet.style.setProperty('--mg-cal-vv-bottom', bottomInset + 'px');
  }

  function bindVisualViewportListener(sheet) {
    if (visualViewportCleanup) {
      visualViewportCleanup();
      visualViewportCleanup = null;
    }
    const update = () => syncVisualViewportInset(sheet);
    update();
    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', update, { passive: true });
      window.visualViewport.addEventListener('scroll', update, { passive: true });
    }
    window.addEventListener('resize', update, { passive: true });
    visualViewportCleanup = () => {
      if (window.visualViewport) {
        window.visualViewport.removeEventListener('resize', update);
        window.visualViewport.removeEventListener('scroll', update);
      }
      window.removeEventListener('resize', update);
      sheet?.style?.removeProperty('--mg-cal-vv-bottom');
    };
  }

  function lockBackgroundScrollForCalendar() {
    if (calendarScrollLocked) return;
    const goalScreen = document.getElementById('app-screen-goal');
    const pageEl = document.querySelector('main.page');
    savedGoalScrollTop = goalScreen ? goalScreen.scrollTop : 0;
    savedPageScrollTop = pageEl ? pageEl.scrollTop : 0;
    savedWindowScrollX = window.scrollX || window.pageXOffset || 0;
    savedWindowScrollY = window.scrollY || window.pageYOffset || 0;

    if (goalScreen) {
      savedGoalOverflowY = goalScreen.style.overflowY || '';
      savedGoalTouchAction = goalScreen.style.touchAction || '';
      goalScreen.style.overflowY = 'hidden';
      goalScreen.style.touchAction = 'none';
      goalScreen.scrollTop = savedGoalScrollTop;
    }
    document.documentElement.classList.add('mg-cal-scroll-locked');
    document.body.classList.add('mg-cal-scroll-locked');
    calendarScrollLocked = true;
  }

  function unlockBackgroundScrollForCalendar() {
    if (!calendarScrollLocked) return;
    document.documentElement.classList.remove('mg-cal-scroll-locked');
    document.body.classList.remove('mg-cal-scroll-locked');

    const goalScreen = document.getElementById('app-screen-goal');
    const pageEl = document.querySelector('main.page');
    if (goalScreen) {
      goalScreen.style.overflowY = savedGoalOverflowY;
      goalScreen.style.touchAction = savedGoalTouchAction;
      goalScreen.scrollTop = savedGoalScrollTop;
    }
    if (pageEl) {
      pageEl.scrollTop = savedPageScrollTop;
    }
    try {
      window.scrollTo(savedWindowScrollX, savedWindowScrollY);
    } catch (_) {}

    requestAnimationFrame(() => {
      if (goalScreen) goalScreen.scrollTop = savedGoalScrollTop;
      if (pageEl) pageEl.scrollTop = savedPageScrollTop;
      try {
        window.scrollTo(savedWindowScrollX, savedWindowScrollY);
      } catch (_) {}
    });

    calendarScrollLocked = false;
  }

  function renderMissionCalendarGrid() {
    const titleEl = document.getElementById('mgCalMonthTitle');
    const kickerEl = document.getElementById('mgCalKicker');
    const gridEl = document.getElementById('mgCalGrid');
    const prevBtn = document.getElementById('mgCalPrevBtn');
    const weekdaysEl = document.getElementById('mgCalWeekdays');
    if (!gridEl || !titleEl) return;

    const isEn = getActiveLang() === 'en';
    const locale = isEn ? 'en-US' : 'de-DE';

    if (kickerEl) {
      kickerEl.textContent = calendarSheetMode === 'start'
        ? (isEn ? 'PREPARATION START' : 'START DER VORBEREITUNG')
        : (isEn ? 'EXAM / DEADLINE' : 'PRÜFUNG / ABGABE');
    }

    if (weekdaysEl) {
      const names = isEn ? ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] : ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
      weekdaysEl.innerHTML = names.map(n => '<span>' + n + '</span>').join('');
    }

    const viewDate = new Date(calendarViewYear, calendarViewMonth, 1);
    try {
      const formattedMonth = new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(viewDate);
      titleEl.textContent = formattedMonth.charAt(0).toUpperCase() + formattedMonth.slice(1);
    } catch {
      titleEl.textContent = (calendarViewMonth + 1) + ' / ' + calendarViewYear;
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const todayISO = toLocalISODate(today);

    if (prevBtn) {
      const isCurrentOrPastMonth =
        calendarViewYear < today.getFullYear() ||
        (calendarViewYear === today.getFullYear() && calendarViewMonth <= today.getMonth());
      prevBtn.disabled = isCurrentOrPastMonth;
    }

    const firstDayOfMonth = new Date(calendarViewYear, calendarViewMonth, 1);
    const leadingEmptyCount = (firstDayOfMonth.getDay() + 6) % 7; // Monday = 0 .. Sunday = 6
    const daysInMonth = new Date(calendarViewYear, calendarViewMonth + 1, 0).getDate();

    const rangeStartISO = calendarSheetMode === 'start'
      ? calendarPendingDate
      : (missionScheduleState.preparationStartDate || todayISO);
    const rangeEndISO = calendarSheetMode === 'deadline'
      ? calendarPendingDate
      : missionScheduleState.deadlineDate;

    gridEl.replaceChildren();

    for (let i = 0; i < leadingEmptyCount; i++) {
      const emptyCell = document.createElement('span');
      emptyCell.className = 'mg-cal-day is-empty';
      emptyCell.setAttribute('aria-hidden', 'true');
      gridEl.appendChild(emptyCell);
    }

    for (let day = 1; day <= daysInMonth; day++) {
      const cellDate = new Date(calendarViewYear, calendarViewMonth, day, 0, 0, 0, 0);
      const cellISO = toLocalISODate(cellDate);
      const isPast = diffCalendarDays(todayISO, cellISO) < 0;
      const isToday = cellISO === todayISO;
      const isSelected = cellISO === calendarPendingDate;
      const isInRange = Boolean(
        rangeStartISO &&
        rangeEndISO &&
        diffCalendarDays(rangeStartISO, cellISO) > 0 &&
        diffCalendarDays(cellISO, rangeEndISO) > 0
      );

      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'mg-cal-day'
        + (isPast ? ' is-disabled' : '')
        + (isToday ? ' is-today' : '')
        + (isSelected ? ' is-selected' : '')
        + (isInRange ? ' is-in-range' : '');
      btn.textContent = String(day);
      btn.dataset.iso = cellISO;
      btn.disabled = isPast;
      btn.setAttribute('aria-selected', isSelected ? 'true' : 'false');
      if (!isPast) {
        btn.onclick = function () {
          calendarPendingDate = cellISO;
          renderMissionCalendarGrid();
        };
      }
      gridEl.appendChild(btn);
    }
  }

  function openMissionCalendarSheet(mode, triggerEl) {
    calendarSheetMode = mode === 'start' ? 'start' : 'deadline';
    const todayISO = getTodayISO();
    const initialISO = calendarSheetMode === 'start'
      ? (missionScheduleState.preparationStartDate || todayISO)
      : (missionScheduleState.deadlineDate || todayISO);
    const dt = parseLocalISODate(initialISO) || new Date();
    calendarViewYear = dt.getFullYear();
    calendarViewMonth = dt.getMonth();
    calendarPendingDate = initialISO;

    const sheet = document.getElementById('missionCalendarSheet');
    if (!sheet) return;

    if (calendarCloseTimer) {
      clearTimeout(calendarCloseTimer);
      calendarCloseTimer = null;
    }

    ensureCalendarMountedToBody(sheet);

    // Dismiss active keyboard focus on Goal textarea/inputs without jumping scroll
    const activeEl = document.activeElement;
    if (activeEl && activeEl !== document.body && typeof activeEl.blur === 'function' && activeEl.matches('input, textarea, select')) {
      activeEl.blur();
    }

    calendarReturnFocusEl = (triggerEl instanceof HTMLElement && triggerEl.isConnected)
      ? triggerEl
      : (activeEl instanceof HTMLElement && activeEl !== document.body && !activeEl.matches('input, textarea, select')
        ? activeEl
        : document.getElementById('mgDeadlineTrigger'));

    lockBackgroundScrollForCalendar();
    renderMissionCalendarGrid();
    bindVisualViewportListener(sheet);

    sheet.classList.remove('hidden', 'is-closing', 'is-open');
    void sheet.offsetWidth;
    requestAnimationFrame(() => {
      if (!sheet.classList.contains('hidden')) {
        sheet.classList.add('is-open');
      }
    });

    if (typeof window.lucide?.createIcons === 'function') {
      window.lucide.createIcons();
    }
  }
  window.openMissionCalendarSheet = openMissionCalendarSheet;

  function closeMissionCalendarSheet(options = {}) {
    const sheet = document.getElementById('missionCalendarSheet');
    if (!sheet) {
      unlockBackgroundScrollForCalendar();
      return;
    }

    if (calendarCloseTimer) {
      clearTimeout(calendarCloseTimer);
      calendarCloseTimer = null;
    }

    if (visualViewportCleanup) {
      visualViewportCleanup();
      visualViewportCleanup = null;
    }

    const prefersReduced = typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const isAlreadyHidden = sheet.classList.contains('hidden');
    const immediate = Boolean(options?.immediate) || prefersReduced || isAlreadyHidden;

    const finalizeClose = () => {
      sheet.classList.remove('is-open', 'is-closing');
      sheet.classList.add('hidden');
      unlockBackgroundScrollForCalendar();
      if (!options?.skipFocusRestore) {
        const focusTarget = (calendarReturnFocusEl && calendarReturnFocusEl.isConnected)
          ? calendarReturnFocusEl
          : document.getElementById('mgDeadlineTrigger');
        if (focusTarget && typeof focusTarget.focus === 'function') {
          try { focusTarget.focus({ preventScroll: true }); } catch (_) { focusTarget.focus(); }
        }
      }
    };

    if (immediate) {
      finalizeClose();
      return;
    }

    sheet.classList.remove('is-open');
    sheet.classList.add('is-closing');
    calendarCloseTimer = setTimeout(() => {
      calendarCloseTimer = null;
      finalizeClose();
    }, 240);
  }
  window.closeMissionCalendarSheet = closeMissionCalendarSheet;

  function shiftMissionCalendarMonth(delta) {
    const d = new Date(calendarViewYear, calendarViewMonth + Number(delta || 0), 1);
    const today = new Date();
    const minMonth = new Date(today.getFullYear(), today.getMonth(), 1);
    if (d < minMonth) return;
    calendarViewYear = d.getFullYear();
    calendarViewMonth = d.getMonth();
    renderMissionCalendarGrid();
  }
  window.shiftMissionCalendarMonth = shiftMissionCalendarMonth;

  function applyMissionCalendarSelection() {
    if (!calendarPendingDate) {
      closeMissionCalendarSheet();
      return;
    }
    const todayISO = getTodayISO();
    if (diffCalendarDays(todayISO, calendarPendingDate) < 0) {
      return;
    }
    if (calendarSheetMode === 'start') {
      userManuallyChoseStartMode = true;
      missionScheduleState.startMode = 'custom';
      missionScheduleState.preparationStartDate = calendarPendingDate;
      startClampedNotice = false;
      syncMissionScheduleUI();
    } else {
      selectDeadlineDate(calendarPendingDate);
    }
    closeMissionCalendarSheet();
  }
  window.applyMissionCalendarSelection = applyMissionCalendarSelection;

  function resetMissionScheduleState() {
    missionScheduleState.deadlineDate = null;
    missionScheduleState.preparationStartDate = null;
    missionScheduleState.startMode = 'immediate';
    missionScheduleState.preferredWeekdays = [];
    missionScheduleState.preferredSessionMinutes = null;
    userManuallyChoseStartMode = false;
    startClampedNotice = false;
    prefsExpanded = false;
    prefsUserInteracted = false;
    closeMissionCalendarSheet({ immediate: true, skipFocusRestore: true });
    syncMissionScheduleUI();
  }
  window.resetMissionScheduleState = resetMissionScheduleState;

  function getMissionScheduleState() {
    return {
      deadlineDate: missionScheduleState.deadlineDate,
      preparationStartDate: missionScheduleState.preparationStartDate,
      startMode: missionScheduleState.startMode,
      preferredWeekdays: [...missionScheduleState.preferredWeekdays],
      preferredSessionMinutes: missionScheduleState.preferredSessionMinutes
    };
  }
  window.getMissionScheduleState = getMissionScheduleState;

  function setMissionScheduleState(patch) {
    if (!patch || typeof patch !== 'object') return getMissionScheduleState();
    if ('deadlineDate' in patch) {
      missionScheduleState.deadlineDate = patch.deadlineDate || null;
    }
    if ('startMode' in patch && ['immediate', 'one_week_before', 'two_weeks_before', 'custom'].includes(patch.startMode)) {
      missionScheduleState.startMode = patch.startMode;
      userManuallyChoseStartMode = true;
    }
    if ('preparationStartDate' in patch) {
      missionScheduleState.preparationStartDate = patch.preparationStartDate || null;
    } else if (missionScheduleState.deadlineDate && missionScheduleState.startMode !== 'custom') {
      recomputePreparationStartDate();
    }
    if (Array.isArray(patch.preferredWeekdays)) {
      missionScheduleState.preferredWeekdays = [...new Set(patch.preferredWeekdays
        .map(Number)
        .filter(n => n >= 1 && n <= 7))]
        .sort((a, b) => a - b);
    }
    if ('preferredSessionMinutes' in patch) {
      const m = patch.preferredSessionMinutes === null ? null : Number(patch.preferredSessionMinutes);
      missionScheduleState.preferredSessionMinutes = [20, 30, 45].includes(m) ? m : null;
    }
    if (missionScheduleState.preferredWeekdays.length > 0 || missionScheduleState.preferredSessionMinutes !== null) {
      prefsExpanded = true;
    }
    syncMissionScheduleUI();
    return getMissionScheduleState();
  }
  window.setMissionScheduleState = setMissionScheduleState;

  /* Optional Schedule Summary Preview on #app-screen-plan */
  function syncPlanSchedulePreview() {
    const existing = document.getElementById('aiPlanSchedulePreview');
    const snap = (typeof activeAIPlan === 'object' && activeAIPlan?.schedule)
      ? activeAIPlan.schedule
      : lastConfiguredScheduleSnapshot;
    const hasConfigured = Boolean(
      snap && (snap.deadlineDate || (Array.isArray(snap.preferredWeekdays) && snap.preferredWeekdays.length > 0) || snap.preferredSessionMinutes !== null && snap.preferredSessionMinutes !== undefined)
    );
    if (!hasConfigured) {
      existing?.remove();
      return;
    }
    const planScreen = document.getElementById('app-screen-plan');
    const anchor = document.getElementById('missionProtectionCard') || document.getElementById('aiPlanSteps');
    if (!planScreen || !anchor) return;

    let banner = existing;
    if (!banner) {
      banner = document.createElement('div');
      banner.id = 'aiPlanSchedulePreview';
      banner.className = 'ai-plan-schedule-preview';
      anchor.insertAdjacentElement('beforebegin', banner);
    }
    const isEn = getActiveLang() === 'en';
    const parts = [];
    if (snap.deadlineDate) {
      const startTxt = formatShortDate(snap.preparationStartDate || getTodayISO(), true);
      const endTxt = formatShortDate(snap.deadlineDate, false);
      parts.push(startTxt + ' → ' + endTxt);
    }
    if (Array.isArray(snap.preferredWeekdays) && snap.preferredWeekdays.length > 0) {
      const dayNames = isEn ? ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] : ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
      parts.push(snap.preferredWeekdays.map(d => dayNames[d - 1]).join(', '));
    }
    if (snap.preferredSessionMinutes) {
      parts.push(snap.preferredSessionMinutes + (isEn ? ' min' : ' Min.'));
    }
    const warningHtml = (typeof activeAIPlan === 'object' && activeAIPlan?.scheduleWarning)
      ? '<div class="text-[10px] font-semibold text-amber-700 mt-0.5">' + escapeHTML(activeAIPlan.scheduleWarning) + '</div>'
      : '';
    banner.innerHTML =
      '<span class="ai-plan-schedule-preview-icon"><i data-lucide="calendar-check"></i></span>' +
      '<div class="min-w-0 flex-1">' +
        '<div class="text-[9px] font-black uppercase tracking-[0.11em] text-[#6D28D9]">' + (isEn ? 'SCHEDULE PREVIEW' : 'ZEITPLAN-VORSCHAU') + '</div>' +
        '<div class="text-[11.5px] font-extrabold text-[#1D1A17] truncate">' + escapeHTML(parts.join(' · ')) + '</div>' +
        warningHtml +
      '</div>';
    if (typeof window.lucide?.createIcons === 'function') {
      window.lucide.createIcons();
    }
  }

  /* Pre-validation hook on analyzeGoal preserving Promise return and user inputs */
  const origAnalyzeGoal = window.analyzeGoal;
  if (typeof origAnalyzeGoal === 'function') {
    window.analyzeGoal = function () {
      const check = validateMissionScheduleState();
      if (!check.valid) {
        syncMissionScheduleUI();
        const noticeEl = document.getElementById('mgScheduleNotice');
        noticeEl?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        return Promise.resolve(false);
      }
      lastConfiguredScheduleSnapshot = getMissionScheduleState();
      return origAnalyzeGoal.apply(this, arguments);
    };
  }

  /* Reset schedule, errors, and temporary image cleanly when opening a fresh Mission creation intake */
  const origOpenGoalIntake = window.openGoalIntake;
  if (typeof origOpenGoalIntake === 'function') {
    window.openGoalIntake = function () {
      resetMissionScheduleState();
      if (typeof window.clearMissionImage === 'function') window.clearMissionImage();
      if (typeof window.clearMissionError === 'function') window.clearMissionError();
      return origOpenGoalIntake.apply(this, arguments);
    };
  }

  /* Hook renderAIPlan for optional schedule preview */
  const origRenderAIPlanV174 = window.renderAIPlan;
  if (typeof origRenderAIPlanV174 === 'function') {
    window.renderAIPlan = function () {
      const res = origRenderAIPlanV174.apply(this, arguments);
      syncPlanSchedulePreview();
      return res;
    };
  }

  /* Hook setLanguage so Home date header and Schedule UI stay localized */
  const origSetLanguageV174 = window.setLanguage;
  if (typeof origSetLanguageV174 === 'function') {
    window.setLanguage = function () {
      const res = origSetLanguageV174.apply(this, arguments);
      if (typeof window.updateHomeDateHeaderV173 === 'function') {
        window.updateHomeDateHeaderV173();
      }
      syncMissionScheduleUI();
      return res;
    };
  }

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      const sheet = document.getElementById('missionCalendarSheet');
      if (sheet && !sheet.classList.contains('hidden')) {
        e.stopImmediatePropagation?.();
        e.preventDefault();
        closeMissionCalendarSheet();
      }
    }
  }, true);

  document.addEventListener('DOMContentLoaded', function () {
    registerScheduleI18N();
    const sheet = document.getElementById('missionCalendarSheet');
    ensureCalendarMountedToBody(sheet);
    if (typeof window.setLanguage === 'function') {
      window.setLanguage(getActiveLang(), false);
    }
    syncMissionScheduleUI();
    if (typeof window.updateHomeDateHeaderV173 === 'function') {
      window.updateHomeDateHeaderV173();
    }
    if (typeof window.confirmAIPlan === 'function' && !window.confirmAIPlan.__v1741Wrapped) {
      const origConfirm = window.confirmAIPlan;
      window.confirmAIPlan = function () {
        const res = origConfirm.apply(this, arguments);
        const planScreen = document.getElementById('app-screen-plan');
        if (planScreen && !planScreen.classList.contains('hidden') && typeof window.clearMissionImage === 'function') {
          window.clearMissionImage();
        }
        return res;
      };
      window.confirmAIPlan.__v1741Wrapped = true;
    }
  });
})();
