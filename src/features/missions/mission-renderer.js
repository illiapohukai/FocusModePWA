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

function syncHomeHeroV103(){const empty=document.getElementById('emptyMissionCard'),active=document.getElementById('activeMissionCard'),current=(activeAIPlan&&typeof getCurrentMissionSession==='function')?getCurrentMissionSession():null,has=Boolean(activeAIPlan&&current);empty?.classList.toggle('hidden',has);active?.classList.toggle('hidden',!has);if(!has){lucide.createIcons();return}const title=document.querySelector('#activeMissionCard h3'),meta=document.querySelector('#activeMissionCard h3+p'),next=document.getElementById('homeMissionNextStep'),task=document.getElementById('homeMissionMeta'),step=document.querySelector('#activeMissionCard .active-mission-step');if(title){title.textContent=activeAIPlan.goal?.title||'Focus Mission';title.style.fontSize=title.textContent.length>76?'17.5px':title.textContent.length>58?'18.5px':''}if(meta)meta.textContent=(activeAIPlan.goal?.subject||'Allgemein')+' · '+(activeAIPlan.goal?.deadlineText||'ohne Frist');if(next)next.textContent=current.title||'Nächster Schritt';if(task)task.textContent=current.task||current.reason||'Nächsten Schritt bearbeiten';let b=document.getElementById('homeMissionDurationV103');if(!b&&step){b=document.createElement('span');b.id='homeMissionDurationV103';step.appendChild(b)}if(b)b.textContent=current.durationMinutes+' Min.';lucide.createIcons()}
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
 document.title='v15 Modular Baseline';
})();
