/**
 * Physical module: src/features/missions/mission-management.js
 */

function openMission(id,source){const m=missionRepository.getById(id);if(!m)return false;const cur=document.querySelector('.app-screen:not(.hidden)')?.id?.replace('app-screen-','')||'';if(typeof window.setMissionPlanSource==='function'){if(source==='home'||source==='missions')window.setMissionPlanSource(source);else if(cur==='home'||cur==='missions')window.setMissionPlanSource(cur);else if(cur!=='plan')window.setMissionPlanSource(null)}missionRepository.setSelected(id);bindMissionCompatibilityV115(m);renderAIPlan(activeAIPlan);renderMultiMissionUIV115();setAppScreen('plan',{immediate:true});return true}window.openMission=openMission;
function renderMultiMissionUIV115(){const active=missionRepository.getActive(),primary=missionRepository.getPrimary(),selected=missionRepository.getSelected()||primary;const home=primary||active[0]||null;if(home){document.getElementById('emptyMissionCard')?.classList.add('hidden');document.getElementById('activeMissionCard')?.classList.remove('hidden');const hs=home.steps.find(x=>x.id===home.currentStepId);const ht=document.querySelector('#activeMissionCard h3'),hm=document.querySelector('#activeMissionCard h3+p');if(ht)ht.textContent=home.title;if(hm)hm.textContent=home.subject+' · '+(home.deadlineText||'ohne Frist');const hn=document.getElementById('homeMissionNextStep'),hmeta=document.getElementById('homeMissionMeta');if(hn)hn.textContent=hs?.title||'Mission abgeschlossen';if(hmeta)hmeta.textContent=hs?((hs.task||hs.reason)+' · ca. '+hs.durationMinutes+' Min.'):'Alle Schritte bearbeitet';const pct=home.steps.length?Math.round(home.progress.completedStepIds.length/home.steps.length*100):0;document.getElementById('homeMissionPercent').textContent=pct+'%';document.getElementById('homeMissionProgressBar').style.width=pct+'%'}else{bindMissionCompatibilityV115(null);document.getElementById('emptyMissionCard')?.classList.remove('hidden');document.getElementById('activeMissionCard')?.classList.add('hidden')}const card=document.getElementById('activeMissionCard');if(card&&home)card.onclick=e=>{if(!e.target.closest('button')||e.target.closest('.active-mission-step'))openMission(home.id)};let second=document.getElementById('secondaryMissionCard');if(!second){second=document.createElement('button');second.id='secondaryMissionCard';second.type='button';card?.insertAdjacentElement('afterend',second)}const other=active.find(x=>x.id!==home?.id);if(other){const step=other.steps.find(x=>x.id===other.currentStepId),done=other.progress.completedStepIds.length;second.classList.remove('hidden');second.innerHTML='<div class="mm-row"><span class="mm-icon"><i data-lucide="layers-3"></i></span><span class="mm-copy"><span class="mm-label">Weitere Mission</span><span class="mm-title">'+escapeHTML(other.title)+'</span><span class="mm-meta">'+escapeHTML(other.subject)+' · Schritt '+Math.min(done+1,other.steps.length)+' von '+other.steps.length+'</span></span><i data-lucide="chevron-right"></i></div>';second.onclick=()=>openMission(other.id)}else second?.classList.add('hidden');const plan=document.getElementById('app-screen-plan'),anchor=plan?.children[1];let switcher=document.getElementById('missionSwitcherV115');if(active.length>1&&anchor){if(!switcher){switcher=document.createElement('div');switcher.id='missionSwitcherV115';anchor.insertAdjacentElement('afterend',switcher)}switcher.innerHTML=active.map(m=>'<button class="'+(m.id===selected?.id?'active':'')+'" onclick="openMission(\''+m.id+'\')">'+escapeHTML(m.title)+'</button>').join('')}else switcher?.remove();lucide.createIcons()}
const legacyRenderPlanV115=renderAIPlan;renderAIPlan=function(plan){const selected=missionRepository.getSelected();if(selected&&(!plan||plan.id!==selected.id)){bindMissionCompatibilityV115(selected);plan=activeAIPlan}return legacyRenderPlanV115.call(this,plan)};
confirmAIPlan=function(){if(!pendingAIPlan)return;const result=missionRepository.create({title:pendingAIPlan.goal.title,subject:pendingAIPlan.goal.subject,type:pendingAIPlan.goal.type,deadlineText:pendingAIPlan.goal.deadlineText,estimate:pendingAIPlan.estimate,sourceAnalysis:pendingAIPlan.sourceAnalysis,steps:pendingAIPlan.sessions});if(!result.ok){showMissionError('Du kannst momentan bis zu zwei aktive Focus Missions haben. Schließe eine Mission ab oder archiviere sie, bevor du eine neue erstellst.');return}openMission(result.mission.id)};
const oldOpenGoalV115=openGoalIntake;openGoalIntake=function(){if(missionRepository.getActive().length>=2){alert('Du kannst momentan bis zu zwei aktive Focus Missions haben. Schließe eine Mission ab oder archiviere sie, bevor du eine neue erstellst.');renderMultiMissionUIV115();return}return oldOpenGoalV115.apply(this,arguments)};
getCurrentMissionSession=function(id=missionRepository.getSelectedId()){const m=missionRepository.getById(id);return m?.steps.find(x=>x.id===m.currentStepId)||null};
ensureActiveMissionId=function(){return missionRepository.getSelectedId()||missionRepository.getPrimaryId()};
createMissionSession=function(mid,sid){const m=missionRepository.getById(mid);if(!m||m.status!=='active')throw new Error('Diese Mission konnte nicht gefunden werden.');const s=m.steps.find(x=>x.id===sid);if(!s||m.currentStepId!==sid)throw new Error('Dieser Missionsschritt kann gerade nicht gestartet werden.');const idx=m.steps.findIndex(x=>x.id===sid);return makeSession({type:'mission',title:s.title,task:s.task||s.reason||s.title,plannedMinutes:s.durationMinutes,missionId:mid,missionStepId:sid,ui:{modeLabel:m.subject||'Focus Mission',progressCurrent:idx+1,progressTotal:m.steps.length}})};
prepareLearningSession=function(mid=missionRepository.getSelectedId(),sid=null){const m=missionRepository.getById(mid),step=sid?m?.steps.find(x=>x.id===sid):m?.steps.find(x=>x.id===m.currentStepId);if(!m||!step)return false;try{return startSession(createMissionSession(mid,step.id))}catch(e){alert(e.message);return false}};
const oldShowUpdateV115=showPlanUpdate;showPlanUpdate=async function(){const last=JSON.parse(localStorage.getItem(SESSION_CORE_LAST_KEY)||'null'),mid=last?.missionId||activeSession?.missionId||missionRepository.getSelectedId(),before=missionRepository.getById(mid);if(!before)return oldShowUpdateV115.apply(this,arguments);missionRepository.setSelected(mid);bindMissionCompatibilityV115(before);const r=await oldShowUpdateV115.apply(this,arguments);const after=activeAIPlan;if(after?.id===mid){const completedIds=missionProgress.completedSessions.map(x=>x.id);const steps=after.sessions.map((x,i)=>({...x,status:completedIds.includes(x.id)?'completed':i===missionProgress.currentSessionIndex?'active':'pending',completedAt:completedIds.includes(x.id)?Date.now():null}));const current=steps.find(x=>x.status==='active');missionRepository.update(mid,{steps,currentStepId:current?.id||null,status:current?'active':'completed',completedAt:current?null:Date.now(),estimate:after.estimate,progress:{completedStepIds:completedIds,history:missionProgress.history,initialStepCount:missionProgress.initialSessionCount}})}renderMultiMissionUIV115();return r};
const oldRecoveryRenderV115=renderSessionRecovery;renderSessionRecovery=function(s){if(s.type==='mission'){const m=missionRepository.getById(s.missionId),step=m?.steps.find(x=>x.id===s.missionStepId);if(m&&step){s={...s,title:step.title,task:step.task||step.reason||step.title,ui:{...s.ui,modeLabel:m.subject}}}}return oldRecoveryRenderV115(s)};
const oldResumeV115=resumeRecoveredSession;resumeRecoveredSession=function(){const s=sessionRecoveryCandidate||activeSession;if(s?.type==='mission'&&missionRepository.getById(s.missionId))missionRepository.setSelected(s.missionId);return oldResumeV115.apply(this,arguments)};
const oldApplyScreenV115=applyAppScreenImmediately;applyAppScreenImmediately=function(screen){if(screen==='plan'){const m=missionRepository.getSelected();if(m){bindMissionCompatibilityV115(m);legacyRenderPlanV115(activeAIPlan)}}const r=oldApplyScreenV115.apply(this,arguments);if(screen==='home'||screen==='plan')setTimeout(renderMultiMissionUIV115,0);return r};
document.addEventListener('DOMContentLoaded',()=>{migrateLegacyMissionV115();const st=loadMissionStoreV115(),initial=missionRepository.getById(st.selectedMissionId)||missionRepository.getPrimary();bindMissionCompatibilityV115(initial);if(initial)legacyRenderPlanV115(activeAIPlan);renderMultiMissionUIV115();console.info('[Missions]',{event:'v11_5_ready',active:missionRepository.getActive().length,selected:st.selectedMissionId,primary:st.primaryMissionId})},{once:true});

function buildMissionNodesHTMLV173(steps){
  if(!Array.isArray(steps)||!steps.length)return '';
  return steps.map((s,idx)=>{
    const num=String(idx+1).padStart(2,'0');
    const stateClass=s.isCompleted?'completed':s.isCurrent?'active':'pending';
    const dot=s.isCurrent?'<i class="mh-node-dot"></i>':'';
    const line=idx<steps.length-1?'<span class="mh-node-line '+(s.isCompleted?'completed':'')+'"></span>':'';
    return '<span class="mh-node '+stateClass+'"><span>'+num+'</span>'+dot+'</span>'+line;
  }).join('');
}

function renderMultiMissionUIV115(){
  document.getElementById('secondaryMissionCard')?.remove();
  const active=missionRepository.getActive();
  const primary=missionRepository.getPrimary();
  const selected=missionRepository.getSelected()||primary;
  const home=primary||active[0]||null;
  const empty=document.getElementById('emptyMissionCard');
  const card=document.getElementById('activeMissionCard');
  if(home){
    const vm=missionViewModelV116(home);
    empty?.classList.add('hidden');
    if(!document.getElementById('missionAuraShellV1181'))card?.classList.remove('hidden');
    const roleEl=document.getElementById('homeMissionRoleLabel');
    const stepCountEl=document.getElementById('homeMissionStepCounter');
    const title=document.getElementById('homeMissionTitle')||document.querySelector('#activeMissionCard h3');
    const meta=document.getElementById('homeMissionSubtitle')||document.querySelector('#activeMissionCard h3+p');
    const durVal=document.getElementById('homeMissionDurationValue');
    const next=document.getElementById('homeMissionNextStep');
    const detail=document.getElementById('homeMissionMeta');
    const nodesRow=document.getElementById('homeMissionNodesRow');
    const percent=document.getElementById('homeMissionPercent');
    const bar=document.getElementById('homeMissionProgressBar');
    const startBtn=document.getElementById('homeMissionStartStepBtn');
    if(roleEl)roleEl.textContent=vm.isPrimary?'HAUPTMISSION':'AKTIVE MISSION';
    if(stepCountEl)stepCountEl.textContent='SCHRITT '+String(vm.currentStepIndex).padStart(2,'0')+' / '+String(vm.totalSteps).padStart(2,'0');
    if(title)title.textContent=vm.title;
    if(meta)meta.textContent=vm.subject+' · '+vm.deadlineText;
    if(durVal)durVal.textContent=String(vm.currentStepMinutes||25);
    if(next)next.textContent=vm.currentStepTitle;
    if(detail)detail.textContent=vm.currentStepTask;
    if(nodesRow)nodesRow.innerHTML=buildMissionNodesHTMLV173(vm.steps);
    if(percent)percent.textContent=vm.progressPercent+'%';
    if(bar)bar.style.width=vm.progressPercent+'%';
    if(startBtn){
      startBtn.onclick=e=>{
        e.stopPropagation();
        prepareLearningSession(vm.id,vm.currentStepId);
      };
    }
    if(card)card.onclick=e=>{if(!e.target.closest('button')||e.target.closest('.active-mission-step'))openMission(home.id,'home')};
  }else{
    empty?.classList.remove('hidden');card?.classList.add('hidden');
  }
  const plan=document.getElementById('app-screen-plan'),anchor=plan?.children[1];
  let switcher=document.getElementById('missionSwitcherV115');
  if(active.length>1&&anchor){
    if(!switcher){switcher=document.createElement('div');switcher.id='missionSwitcherV115';anchor.insertAdjacentElement('afterend',switcher)}
    switcher.innerHTML=active.map(m=>'<button class="'+(m.id===selected?.id?'active':'')+'" onclick="openMission(\''+m.id+'\')">'+escapeHTML(m.title)+'</button>').join('');
  }else switcher?.remove();
  lucide.createIcons();
}

const MISSION_THEMES_V116=['violet','aqua'];
let missionManagementFilterV116='active';
function missionViewModelV116(m){
 const stepsArr=Array.isArray(m?.steps)?m.steps:[];
 const completedIds=Array.isArray(m?.progress?.completedStepIds)?m.progress.completedStepIds:[];
 const stepIdx=stepsArr.findIndex(x=>x.id===m?.currentStepId);
 const step=stepIdx>=0?stepsArr[stepIdx]:(stepsArr.find(x=>!completedIds.includes(x.id))||null);
 const resolvedIdx=stepIdx>=0?stepIdx:(step?stepsArr.findIndex(x=>x.id===step.id):-1);
 const total=stepsArr.length||0,done=completedIds.length||0;
 const currentStepIndex=resolvedIdx>=0?(resolvedIdx+1):(total?Math.min(total,done+1):1);
 const mappedSteps=stepsArr.map((s,idx)=>({
   id:s.id,
   index:idx+1,
   title:s.title,
   durationMinutes:s.durationMinutes||25,
   isCompleted:completedIds.includes(s.id)||s.status==='completed',
   isCurrent:Boolean(step&&s.id===step.id)
 }));
 return{
   id:m.id,
   title:m.title,
   subject:m.subject||'Allgemein',
   deadlineText:m.deadlineText||'ohne Frist',
   status:m.status,
   currentStepId:step?.id||m.currentStepId||null,
   currentStepIndex,
   currentStepTitle:step?.title||'Mission abgeschlossen',
   currentStepTask:step?(step.task||step.reason||'Fokus-Schritt bearbeiten'):'Alle Schritte bearbeitet',
   currentStepMinutes:step?.durationMinutes||0,
   completedSteps:done,
   totalSteps:total,
   progressPercent:total?Math.min(100,Math.round(done/total*100)):0,
   isPrimary:m.id===missionRepository.getPrimaryId(),
   steps:mappedSteps,
   createdAt:m.createdAt||Date.now(),
   updatedAt:m.updatedAt||m.createdAt||Date.now(),
   completedAt:m.completedAt||null,
   archivedAt:m.archivedAt||null,
   theme:MISSION_THEMES_V116[Math.abs([...m.id].reduce((a,c)=>a+c.charCodeAt(0),0))%MISSION_THEMES_V116.length]
 };
}
function sortedActiveMissionsV116(){const active=missionRepository.getActive();const primary=missionRepository.getPrimaryId();return active.sort((a,b)=>a.id===primary?-1:b.id===primary?1:a.createdAt-b.createdAt)}
function renderMissionHeroV116(vm){
 const stepBadge='SCHRITT '+String(vm.currentStepIndex).padStart(2,'0')+' / '+String(vm.totalSteps).padStart(2,'0');
 const roleLabel=vm.isPrimary?'HAUPTMISSION':'AKTIVE MISSION';
 const durVal=vm.currentStepMinutes?String(vm.currentStepMinutes):'✓';
 const durUnit=vm.currentStepMinutes?'MIN':'DONE';
 const nodesHTML=buildMissionNodesHTMLV173(vm.steps);
 const startCall=vm.currentStepId?('event.stopPropagation();prepareLearningSession(\''+vm.id+'\',\''+vm.currentStepId+'\')'):('event.stopPropagation();openMission(\''+vm.id+'\',\'home\')');
 return '<article class="mission-hero-v116 theme-'+vm.theme+'" role="button" tabindex="0" aria-label="Mission '+escapeHTML(vm.title)+' öffnen" onclick="if(!event.target.closest(\'.mh-start-btn\'))openMission(\''+vm.id+'\',\'home\')" onkeydown="if(event.key===\'Enter\'||event.key===\' \'){event.preventDefault();openMission(\''+vm.id+'\',\'home\')}">'
   +'<div class="mh-top-row"><span class="mh-kicker"><i></i><span>'+roleLabel+'</span></span><span class="mh-step-counter">'+stepBadge+'</span></div>'
   +'<h3 class="mh-title">'+escapeHTML(vm.title)+'</h3>'
   +'<p class="mh-meta">'+escapeHTML(vm.subject)+' · '+escapeHTML(vm.deadlineText)+'</p>'
   +'<div class="mh-step">'
     +'<div class="mh-duration-box"><span class="mh-duration-val">'+escapeHTML(durVal)+'</span><span class="mh-duration-unit">'+durUnit+'</span></div>'
     +'<div class="mh-step-copy">'
       +'<span class="mh-label">NÄCHSTER SCHRITT</span>'
       +'<span class="mh-step-title">'+escapeHTML(vm.currentStepTitle)+'</span>'
       +'<span class="mh-step-desc">'+escapeHTML(vm.currentStepTask)+'</span>'
     +'</div>'
   +'</div>'
   +'<div class="mh-nodes-row">'+nodesHTML+'</div>'
   +'<div class="mh-bottom-row">'
     +'<button class="mh-start-btn" type="button" onclick="'+startCall+'"><span>Schritt starten</span><i data-lucide="play" class="w-3.5 h-3.5 fill-current"></i></button>'
     +'<div class="mh-progress-readout"><span class="mh-progress-pct">'+vm.progressPercent+'%</span><span class="mh-progress-lbl">MISSION</span></div>'
   +'</div>'
 +'</article>';
}
function renderHomeCarouselV116(){
 const active=sortedActiveMissionsV116(),old=document.getElementById('missionCarouselV116'),card=document.getElementById('activeMissionCard'),empty=document.getElementById('emptyMissionCard');
 if(active.length<2){old?.remove();renderMultiMissionUIV115();return}
 empty?.classList.add('hidden');card?.classList.add('hidden');let host=old;if(!host){host=document.createElement('div');host.id='missionCarouselV116';card.insertAdjacentElement('afterend',host)}host.innerHTML=active.map(m=>renderMissionHeroV116(missionViewModelV116(m))).join('');host.scrollLeft=0;
}
function focusMissionListV116(){return sortedActiveMissionsV116().map(m=>{const v=missionViewModelV116(m);return '<button class="focus-mission-v116" onclick="openMissionFromFocusV116(\''+m.id+'\')"><span class="fm-icon"><i data-lucide="target"></i></span><span class="fm-copy"><span class="fm-title">'+escapeHTML(v.title)+'</span><span class="fm-meta">'+escapeHTML(v.currentStepTitle)+' · '+v.currentStepMinutes+' Min.</span></span><i class="w-4 h-4 text-slate-300" data-lucide="chevron-right"></i></button>'}).join('')}
function renderFocusChoiceV116(){
 const sheet=document.querySelector('#focusChoiceOverlay .focus-choice-sheet');if(!sheet)return;
 const all=missionRepository.getAll(),active=missionRepository.getActive(),activeCount=active.length,totalCount=all.length,full=activeCount>=2;
 const lang=(window.FocusModeApp?.services?.storage?.getText('focus-mode-language','de')||document.documentElement.lang||'de')==='en'?'en':'de';
 const countLabel=lang==='en'?(activeCount+' active · '+totalCount+' total'):(activeCount+' aktive · '+totalCount+' insgesamt');
 const limitBadge=full?'<span class="fm-limit-badge-v17"><i data-lucide="info"></i><span>'+(lang==='en'?'2 active Missions reached':'2 aktive Missionen erreicht')+'</span></span>':'';
 const createBtn=!full?'<button class="focus-new-v116" type="button" onclick="newMissionFromFocusV116()">+ Neue Focus Mission</button>':'';
 sheet.innerHTML='<div class="w-10 h-1 bg-slate-200 rounded-full mx-auto mb-4"></div><div class="flex items-start justify-between gap-3"><div><div class="text-[9px] font-black uppercase tracking-[.16em] text-indigo-500">Fokus starten</div><h3 class="text-lg font-black tracking-tight text-slate-950 mt-1">Was möchtest du fokussieren?</h3></div><button class="w-8 h-8 rounded-full bg-slate-100 text-slate-500 flex items-center justify-center" type="button" aria-label="Schließen" onclick="closeFocusChoice()"><i class="w-4 h-4" data-lucide="x"></i></button></div><div class="focus-section-v116"><div class="focus-section-label-v116">Quick Focus</div><button class="focus-mission-v116" type="button" onclick="openQuickSetupFromChoice()"><span class="fm-icon"><i data-lucide="timer"></i></span><span class="fm-copy"><span class="fm-title">Kurzen Fokus starten</span><span class="fm-meta">Aufgabe und Dauer selbst festlegen</span></span><i class="w-4 h-4 text-slate-300" data-lucide="chevron-right"></i></button></div><div class="focus-section-v116"><div class="focus-section-label-v116">Deine Focus Missions</div>'+focusMissionListV116()+createBtn+'<button class="focus-manage-v116 focus-manage-card-v17" type="button" onclick="openMissionManagementV116()"><span class="fm-manage-main"><span class="fm-manage-icon"><i data-lucide="layers-3"></i></span><span class="fm-manage-copy"><span class="fm-manage-title">Missionen verwalten</span><span class="fm-manage-sub" data-dynamic-mission-counts="true">'+escapeHTML(countLabel)+'</span></span><i class="fm-manage-chevron" data-lucide="chevron-right"></i></span>'+limitBadge+'</button></div>';
 if(typeof translateTree==='function')translateTree(sheet,lang);
 const subEl=sheet.querySelector('[data-dynamic-mission-counts="true"]');
 if(subEl)subEl.textContent=countLabel;
 lucide.createIcons();
}
function openMissionFromFocusV116(id){closeFocusChoice();setTimeout(()=>openMission(id),180)}
function newMissionFromFocusV116(){if(missionRepository.getActive().length>=2){closeFocusChoice();setTimeout(()=>{openMissionManagementV116();showMissionLimitV116()},180);return}closeFocusChoice();setTimeout(()=>openGoalIntake({parent:'home',rootSection:'home',reopenFocusChoice:true}),180)}
const openFocusChoicePreV116=openFocusChoice;openFocusChoice=function(){if(activeSession||sessionRecoveryCandidate||getRecoverableSessionV1151()){routeExistingSessionV1151();return}renderFocusChoiceV116();const res=openFocusChoicePreV116.apply(this,arguments);window.hideBottomNavigation?.();return res};

function ensureMissionManagementScreenV116(){
 if(document.getElementById('app-screen-missions'))return;
 const screen=document.createElement('div');
 screen.id='app-screen-missions';
 screen.className='app-screen hidden flex flex-col flex-1 min-h-0';
 screen.innerHTML='<div class="flex items-center justify-between mb-3.5 shrink-0">'
   +'<div><div class="text-[9px] font-extrabold uppercase tracking-[.16em] text-[#6D28D9]">Focus Missions</div><h3 class="text-[22px] font-black tracking-tight text-[#1D1A17] mt-0.5">Deine Missionen</h3></div>'
   +'<button class="w-10 h-10 rounded-full bg-[#6D28D9] text-white flex items-center justify-center shadow-md shrink-0" type="button" aria-label="Neue Focus Mission" onclick="newMissionFromManagerV116()"><i class="w-4 h-4" data-lucide="plus"></i></button>'
   +'</div>'
   +'<div id="missionOverviewCardV173" class="dark-summary-card rounded-[22px] p-4 mb-3.5 shrink-0">'
     +'<div class="flex items-center justify-between gap-3 mb-3">'
       +'<div>'
         +'<div class="text-[9px] font-extrabold uppercase tracking-[0.14em] text-white/60">MISSIONSSYSTEM</div>'
         +'<div class="text-[22px] font-black tracking-tight text-white leading-none mt-1" id="missionOverviewActiveSlot">0 / 2 aktiv</div>'
       +'</div>'
       +'<div class="text-right">'
         +'<div class="text-[10px] font-bold text-emerald-400" id="missionOverviewCapacityText">Slot frei</div>'
         +'<div class="text-[9px] font-medium text-white/55 mt-0.5">Max. 2 parallel</div>'
       +'</div>'
     +'</div>'
     +'<div class="grid grid-cols-3 gap-2 pt-2.5 border-t border-white/10">'
       +'<div class="dark-summary-stat"><span class="dark-summary-val" id="missionOverviewActiveCount">0</span><span class="dark-summary-lbl">AKTIV</span></div>'
       +'<div class="dark-summary-stat"><span class="dark-summary-val text-emerald-400" id="missionOverviewCompletedCount">0</span><span class="dark-summary-lbl">FERTIG</span></div>'
       +'<div class="dark-summary-stat"><span class="dark-summary-val text-purple-300" id="missionOverviewStepsDoneCount">0</span><span class="dark-summary-lbl">SCHRITTE</span></div>'
     +'</div>'
   +'</div>'
   +'<div class="mission-tabs-v116 mb-3.5 shrink-0">'
     +'<button class="mission-tab-v116 active" type="button" data-filter="active" onclick="setMissionFilterV116(\'active\',this)">Aktiv</button>'
     +'<button class="mission-tab-v116" type="button" data-filter="completed" onclick="setMissionFilterV116(\'completed\',this)">Abgeschlossen</button>'
     +'<button class="mission-tab-v116" type="button" data-filter="archived" onclick="setMissionFilterV116(\'archived\',this)">Archiv</button>'
   +'</div>'
   +'<div id="missionManagementListV116" class="flex-1 space-y-2.5 pb-6 overflow-y-auto"></div>';
 document.getElementById('appBody').appendChild(screen);ensureMissionSheetV116();lucide.createIcons();
}
function ensureMissionSheetV116(){if(document.getElementById('missionSheetV116'))return;const sheet=document.createElement('div');sheet.id='missionSheetV116';sheet.onclick=e=>{if(e.target===sheet)closeMissionSheetV116()};sheet.innerHTML='<div class="mission-sheet-card-v116" id="missionSheetCardV116"></div>';document.getElementById('screenWrapper').appendChild(sheet)}
function openMissionSheetV116(content){ensureMissionSheetV116();document.getElementById('missionSheetCardV116').innerHTML=content;document.getElementById('missionSheetV116').classList.add('open');window.hideBottomNavigation?.();lucide.createIcons()}
function closeMissionSheetV116(){document.getElementById('missionSheetV116')?.classList.remove('open');window.syncBottomNavigationForScreen?.()}
function missionHasActiveSessionV116(id){const s=window.activeSession||sessionRecoveryCandidate;return Boolean(s?.type==='mission'&&s.missionId===id&&ACTIVE_SESSION_STATES.includes(s.lifecycleState))}
function renameMissionV116(id,title){const clean=String(title||'').trim().slice(0,80);if(!clean)return{ok:false,error:'title_invalid'};const m=missionRepository.update(id,{title:clean});return m?{ok:true,mission:m}:{ok:false,error:'mission_not_found'}}
function restoreMissionV116(id){if(missionRepository.getActive().length>=2)return{ok:false,error:'active_mission_limit_reached'};const m=missionRepository.getById(id);if(!m||m.status!=='archived')return{ok:false,error:'mission_not_found'};const stepId=m.archivedCurrentStepId||m.steps.find(x=>x.status!=='completed')?.id||null;const updated=missionRepository.update(id,{status:stepId?'active':'completed',archivedAt:null,currentStepId:stepId,archivedCurrentStepId:null});if(updated?.status==='active'&&!missionRepository.getPrimaryId())missionRepository.setPrimary(id);return updated?{ok:true,mission:updated}:{ok:false,error:'restore_failed'}}
function archiveMissionV116(id){if(missionHasActiveSessionV116(id))return{ok:false,error:'active_session_exists'};const m=missionRepository.getById(id);if(!m)return{ok:false,error:'mission_not_found'};return missionRepository.archive(id,{archivedCurrentStepId:m.currentStepId})}
const repoArchiveV116=missionRepository.archive.bind(missionRepository);missionRepository.archive=function(id){const m=this.getById(id);if(!m)return{ok:false,error:'mission_not_found'};if(missionHasActiveSessionV116(id))return{ok:false,error:'active_session_exists'};const r=this.update(id,{status:'archived',archivedAt:Date.now(),archivedCurrentStepId:m.currentStepId,currentStepId:null});return r?{ok:true,mission:r}:{ok:false,error:'mission_not_found'}};
missionRepository.rename=renameMissionV116;missionRepository.restore=restoreMissionV116;missionRepository.getCompleted=function(){return this.getAll().filter(x=>x.status==='completed')};missionRepository.getArchived=function(){return this.getAll().filter(x=>x.status==='archived')};
function setMissionFilterV116(filter,button){missionManagementFilterV116=filter;document.querySelectorAll('.mission-tab-v116').forEach(x=>x.classList.toggle('active',x===button));renderMissionManagementV116()}
function formatMissionShortDateV173(ts){
 if(!ts)return '';
 try{return new Intl.DateTimeFormat('de-DE',{day:'2-digit',month:'2-digit',year:'numeric'}).format(new Date(ts));}catch{return '';}
}
function renderMissionManagementV116(){
 ensureMissionManagementScreenV116();
 const list=document.getElementById('missionManagementListV116');
 const all=missionRepository.getAll();
 const activeList=all.filter(x=>x.status==='active');
 const completedList=all.filter(x=>x.status==='completed');
 const archivedList=all.filter(x=>x.status==='archived');
 const totalStepsDone=all.reduce((acc,m)=>acc+(m.progress?.completedStepIds?.length||0),0);
 const slotEl=document.getElementById('missionOverviewActiveSlot');
 const capEl=document.getElementById('missionOverviewCapacityText');
 const actCountEl=document.getElementById('missionOverviewActiveCount');
 const compCountEl=document.getElementById('missionOverviewCompletedCount');
 const stepsDoneEl=document.getElementById('missionOverviewStepsDoneCount');
 if(slotEl)slotEl.textContent=activeList.length+' / 2 aktiv';
 if(capEl){
   capEl.textContent=activeList.length>=2?'Beide Plätze belegt':(2-activeList.length)+' Platz frei';
   capEl.className='text-[10px] font-bold '+(activeList.length>=2?'text-amber-300':'text-emerald-400');
 }
 if(actCountEl)actCountEl.textContent=String(activeList.length);
 if(compCountEl)compCountEl.textContent=String(completedList.length);
 if(stepsDoneEl)stepsDoneEl.textContent=String(totalStepsDone);

 const items=all.filter(x=>x.status===missionManagementFilterV116);
 list.replaceChildren();
 if(!items.length){
   const emptyCopy={
     active:{title:'Keine aktiven Missionen',sub:'Erstelle eine neue Focus Mission, um dein Ziel in klare Schritte aufzuteilen.',cta:true},
     completed:{title:'Noch keine abgeschlossenen Missionen',sub:'Abgeschlossene Missionen erscheinen hier, sobald alle Schritte erledigt sind.',cta:false},
     archived:{title:'Archiv ist leer',sub:'Archivierte Missionen werden hier gespeichert und können jederzeit wiederhergestellt werden.',cta:false}
   }[missionManagementFilterV116]||{title:'Keine Missionen',sub:'In diesem Bereich gibt es noch keine Mission.',cta:false};
   const e=document.createElement('div');
   e.className='missions-empty-v116';
   e.innerHTML='<i data-lucide="folder-open"></i><div class="text-[14px] font-black text-[#1D1A17]">'+emptyCopy.title+'</div><div class="text-[11px] text-[#766E65] leading-relaxed mt-1.5">'+emptyCopy.sub+'</div>'+(emptyCopy.cta?'<button type="button" class="mt-3.5 px-4 py-2 rounded-full bg-[#6D28D9] text-white text-[11px] font-extrabold shadow-sm" onclick="newMissionFromManagerV116()">+ Neue Mission starten</button>':'');
   list.append(e);
   lucide.createIcons();
   return;
 }
 items.sort((a,b)=>a.id===missionRepository.getPrimaryId()?-1:b.id===missionRepository.getPrimaryId()?1:(b.updatedAt||0)-(a.updatedAt||0)).forEach(m=>{
   const v=missionViewModelV116(m);
   const card=document.createElement('article');
   card.className='mission-manage-card-v116';
   const dateStr=v.status==='completed'?('Abgeschlossen · '+formatMissionShortDateV173(v.completedAt||v.updatedAt)):v.status==='archived'?('Archiviert · '+formatMissionShortDateV173(v.archivedAt||v.updatedAt)):(v.deadlineText||'ohne Frist');
   const badgeHTML=v.status==='active'
     ?(v.isPrimary?'<span class="mmc-primary">Hauptmission</span>':'<span class="mmc-badge-secondary">Aktive Mission</span>')
     :(v.status==='completed'?'<span class="mmc-badge-completed">Abgeschlossen</span>':'<span class="mmc-badge-archived">Archiviert</span>');
   const stepInfo=v.status==='active'
     ?('Nächster Schritt: '+escapeHTML(v.currentStepTitle)+(v.currentStepMinutes?(' · '+v.currentStepMinutes+' Min.'):''))
     :('Alle '+v.completedSteps+' von '+v.totalSteps+' Schritten gespeichert');
   const quickBtn=v.status==='active'&&v.currentStepId
     ?'<button type="button" class="mmc-action-btn primary" onclick="event.stopPropagation();prepareLearningSession(\''+m.id+'\',\''+v.currentStepId+'\')"><i data-lucide="play" class="w-3 h-3 fill-current"></i><span>Schritt starten</span></button>'
     :v.status==='archived'
       ?'<button type="button" class="mmc-action-btn" onclick="event.stopPropagation();restoreMissionActionV116(\''+m.id+'\')"><i data-lucide="rotate-ccw" class="w-3 h-3"></i><span>Wiederherstellen</span></button>'
       :'';
   card.innerHTML='<div class="mmc-top">'
     +'<span class="mmc-icon"><i data-lucide="'+(v.status==='completed'?'check-circle-2':v.status==='archived'?'archive':'target')+'"></i></span>'
     +'<span class="mmc-copy">'
       +'<span class="mmc-badges-row">'+badgeHTML+'<span class="mmc-subject-pill">'+escapeHTML(v.subject)+'</span></span>'
       +'<span class="mmc-title">'+escapeHTML(v.title)+'</span>'
       +'<span class="mmc-meta">'+escapeHTML(dateStr)+' · '+v.completedSteps+' von '+v.totalSteps+' Schritten</span>'
     +'</span>'
     +'<button class="mmc-menu" type="button" aria-label="Mission verwalten"><i data-lucide="ellipsis"></i></button>'
   +'</div>'
   +'<div class="mmc-track"><i style="width:'+v.progressPercent+'%"></i></div>'
   +'<div class="mmc-foot"><span class="truncate">'+stepInfo+'</span><span class="font-extrabold text-[#1D1A17] shrink-0">'+v.progressPercent+'%</span></div>'
   +'<div class="mmc-actions-row">'
     +'<button type="button" class="mmc-action-btn outline" onclick="event.stopPropagation();openMission(\''+m.id+'\',\'missions\')">Mission öffnen</button>'
     +quickBtn
   +'</div>';
   card.querySelector('.mmc-copy').onclick=()=>openMission(m.id,'missions');
   card.querySelector('.mmc-menu').onclick=e=>{e.stopPropagation();openMissionActionsV116(m.id)};
   list.append(card);
 });
 lucide.createIcons();
}
function openMissionManagementV116(){closeFocusChoice();ensureMissionManagementScreenV116();renderMissionManagementV116();if(typeof navigateToRoot==='function')navigateToRoot('missions');else setAppScreen('missions',{immediate:true})}
function openMissionManagementFromSettings(){openMissionManagementV116()}
window.openMissionManagementV116=openMissionManagementV116;
window.openMissionManagementFromSettings=openMissionManagementFromSettings;
function newMissionFromManagerV116(){if(missionRepository.getActive().length>=2){showMissionLimitV116();return}if(typeof navigateForward==='function')navigateForward('goal',{parent:'missions',rootSection:'missions'});else setAppScreen('goal')}
function showMissionLimitV116(){openMissionSheetV116('<h3>2 aktive Missionen erreicht</h3><p>Archiviere oder schließe zuerst eine aktive Mission ab, bevor du eine neue erstellst.</p><button class="mission-sheet-action-v116 primary" type="button" onclick="closeMissionSheetV116()">Verstanden</button>')}
function openMissionActionsV116(id){const m=missionRepository.getById(id);if(!m)return;let actions='<h3>'+escapeHTML(m.title)+'</h3><p>Wähle eine Aktion für diese Mission.</p><button class="mission-sheet-action-v116 primary" type="button" onclick="closeMissionSheetV116();openMission(\''+id+'\')">Öffnen</button>';if(m.status==='active'&&!missionViewModelV116(m).isPrimary)actions+='<button class="mission-sheet-action-v116" type="button" onclick="makePrimaryV116(\''+id+'\')">Als Hauptmission festlegen</button>';actions+='<button class="mission-sheet-action-v116" type="button" onclick="openRenameMissionV116(\''+id+'\')">Umbenennen</button>';if(m.status!=='archived')actions+='<button class="mission-sheet-action-v116" type="button" onclick="confirmArchiveV116(\''+id+'\')">Archivieren</button>';else actions+='<button class="mission-sheet-action-v116" type="button" onclick="restoreMissionActionV116(\''+id+'\')">Wiederherstellen</button>';actions+='<button class="mission-sheet-action-v116 danger" type="button" onclick="confirmDeleteV116(\''+id+'\')">Löschen</button><button class="mission-sheet-action-v116" type="button" onclick="closeMissionSheetV116()">Abbrechen</button>';openMissionSheetV116(actions)}
function makePrimaryV116(id){missionRepository.setPrimary(id);closeMissionSheetV116();refreshMissionUIV116()}
function openRenameMissionV116(id){const m=missionRepository.getById(id);openMissionSheetV116('<h3>Mission umbenennen</h3><p>Der Plan und die bisherige Historie bleiben unverändert.</p><input class="mission-sheet-input-v116" id="renameMissionInputV116" maxlength="80" value="'+escapeHTML(m?.title||'')+'"><button class="mission-sheet-action-v116 primary" type="button" onclick="saveRenameMissionV116(\''+id+'\')">Speichern</button><button class="mission-sheet-action-v116" type="button" onclick="closeMissionSheetV116()">Abbrechen</button>')}
function saveRenameMissionV116(id){const r=renameMissionV116(id,document.getElementById('renameMissionInputV116')?.value);if(!r.ok)return;closeMissionSheetV116();refreshMissionUIV116()}
function confirmArchiveV116(id){if(missionHasActiveSessionV116(id)){openMissionSheetV116('<h3>Mission wird gerade verwendet</h3><p>Beende oder verwirf zuerst die laufende Fokus-Sitzung.</p><button class="mission-sheet-action-v116 primary" type="button" onclick="closeMissionSheetV116();routeExistingSessionV1151()">Zur Sitzung</button>');return}openMissionSheetV116('<h3>Mission archivieren?</h3><p>Der Fortschritt bleibt gespeichert und kann später wiederhergestellt werden.</p><button class="mission-sheet-action-v116 danger" type="button" onclick="archiveMissionActionV116(\''+id+'\')">Archivieren</button><button class="mission-sheet-action-v116" type="button" onclick="closeMissionSheetV116()">Abbrechen</button>')}
function archiveMissionActionV116(id){const r=missionRepository.archive(id);if(!r.ok)return;closeMissionSheetV116();refreshMissionUIV116()}
function restoreMissionActionV116(id){const r=restoreMissionV116(id);if(!r.ok){showMissionLimitV116();return}closeMissionSheetV116();refreshMissionUIV116()}
function confirmDeleteV116(id){if(missionHasActiveSessionV116(id)){confirmArchiveV116(id);return}openMissionSheetV116('<h3>Mission endgültig löschen?</h3><p>Der Missionsplan wird entfernt. Bereits gespeicherte Fokus-Sitzungen bleiben im Verlauf.</p><button class="mission-sheet-action-v116 danger" type="button" onclick="deleteMissionActionV116(\''+id+'\')">Endgültig löschen</button><button class="mission-sheet-action-v116" type="button" onclick="closeMissionSheetV116()">Abbrechen</button>')}
function deleteMissionActionV116(id){const r=missionRepository.delete(id);if(!r.ok)return;closeMissionSheetV116();refreshMissionUIV116()}
function refreshMissionUIV116(){renderMultiMissionUIV115();renderHomeCarouselV116();renderFocusChoiceV116();if(document.getElementById('app-screen-missions'))renderMissionManagementV116();const selected=missionRepository.getSelected();if(selected){bindMissionCompatibilityV115(selected);if(!document.getElementById('app-screen-plan').classList.contains('hidden'))renderAIPlan(activeAIPlan)}}
const applyScreenBeforeV116=applyAppScreenImmediately;applyAppScreenImmediately=function(screen){const r=applyScreenBeforeV116.apply(this,arguments);if(screen==='home')setTimeout(renderHomeCarouselV116,0);if(screen==='missions')setTimeout(renderMissionManagementV116,0);return r};
const createBeforeV116=missionRepository.create.bind(missionRepository);missionRepository.create=function(input){const r=createBeforeV116(input);if(r.ok)setTimeout(refreshMissionUIV116,0);return r};
document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>{ensureMissionManagementScreenV116();refreshMissionUIV116();console.info('[MissionsUX]',{event:'v17_3_ready',active:missionRepository.getActive().length})},260),{once:true});
