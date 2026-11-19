/**
 * Physical module: src/features/missions/mission-management.js
 */

function openMission(id,source){const m=missionRepository.getById(id);if(!m)return false;const cur=document.querySelector('.app-screen:not(.hidden)')?.id?.replace('app-screen-','')||'';if(typeof window.setMissionPlanSource==='function'){if(source==='home'||source==='missions')window.setMissionPlanSource(source);else if(cur==='home'||cur==='missions')window.setMissionPlanSource(cur);else if(cur!=='plan')window.setMissionPlanSource(null)}missionRepository.setSelected(id);bindMissionCompatibilityV115(m);renderAIPlan(activeAIPlan);renderMultiMissionUIV115();setAppScreen('plan',{immediate:true});return true}window.openMission=openMission;
/* v17.8: switching between two Mission plans keeps the authoritative openMission path and adds one short crossfade. */
function switchMissionPlanV178(id){if(!id||id===missionRepository.getSelectedId())return false;const ok=openMission(id);if(ok)window.MotionController?.animateMissionPlanSwitch?.();return ok}window.switchMissionPlanV178=switchMissionPlanV178;
function renderMultiMissionUIV115(){const active=missionRepository.getActive(),primary=missionRepository.getPrimary(),selected=missionRepository.getSelected()||primary;const home=primary||active[0]||null;if(home){document.getElementById('emptyMissionCard')?.classList.add('hidden');document.getElementById('activeMissionCard')?.classList.remove('hidden');const hs=home.steps.find(x=>x.id===home.currentStepId);const ht=document.querySelector('#activeMissionCard h3'),hm=document.querySelector('#activeMissionCard h3+p');if(ht)ht.textContent=home.title;if(hm)hm.textContent=home.subject+' · '+missionDeadlineV1712(home.deadlineText).label;const hn=document.getElementById('homeMissionNextStep'),hmeta=document.getElementById('homeMissionMeta');if(hn)hn.textContent=hs?.title||'Mission abgeschlossen';if(hmeta)hmeta.textContent=hs?((hs.task||hs.reason)+' · ca. '+hs.durationMinutes+' Min.'):'Alle Schritte bearbeitet';const pct=home.steps.length?Math.round(home.progress.completedStepIds.length/home.steps.length*100):0;document.getElementById('homeMissionPercent').textContent=pct+'%';document.getElementById('homeMissionProgressBar').style.width=pct+'%'}else{bindMissionCompatibilityV115(null);document.getElementById('emptyMissionCard')?.classList.remove('hidden');document.getElementById('activeMissionCard')?.classList.add('hidden')}const card=document.getElementById('activeMissionCard');if(card&&home)card.onclick=e=>{if(!e.target.closest('button')||e.target.closest('.active-mission-step'))openMission(home.id)};let second=document.getElementById('secondaryMissionCard');if(!second){second=document.createElement('button');second.id='secondaryMissionCard';second.type='button';card?.insertAdjacentElement('afterend',second)}const other=active.find(x=>x.id!==home?.id);if(other){const step=other.steps.find(x=>x.id===other.currentStepId),done=other.progress.completedStepIds.length;second.classList.remove('hidden');second.innerHTML='<div class="mm-row"><span class="mm-icon"><i data-lucide="layers-3"></i></span><span class="mm-copy"><span class="mm-label">Weitere Mission</span><span class="mm-title">'+escapeHTML(other.title)+'</span><span class="mm-meta">'+escapeHTML(other.subject)+' · Schritt '+Math.min(done+1,other.steps.length)+' von '+other.steps.length+'</span></span><i data-lucide="chevron-right"></i></div>';second.onclick=()=>openMission(other.id)}else second?.classList.add('hidden');const plan=document.getElementById('app-screen-plan'),anchor=plan?.children[1];let switcher=document.getElementById('missionSwitcherV115');if(active.length>1&&anchor){if(!switcher){switcher=document.createElement('div');switcher.id='missionSwitcherV115';anchor.insertAdjacentElement('afterend',switcher)}switcher.innerHTML=active.map(m=>'<button class="'+(m.id===selected?.id?'active':'')+'" onclick="openMission(\''+m.id+'\')">'+escapeHTML(m.title)+'</button>').join('')}else switcher?.remove();lucide.createIcons()}
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
  applyHomeEmptyStateV1716();
  const plan=document.getElementById('app-screen-plan'),anchor=document.getElementById('aiPlanHero');
  let switcher=document.getElementById('missionSwitcherV115');
  if(active.length>1&&anchor){
    // v17.8: neutral segmented selector above the Mission hero; the hero carries the color identity.
    if(!switcher){switcher=document.createElement('div');switcher.id='missionSwitcherV115';switcher.className='mp-switcher';switcher.setAttribute('role','group');switcher.setAttribute('aria-label','Mission auswählen');anchor.insertAdjacentElement('beforebegin',switcher)}
    const sameSet=[...switcher.children].map(b=>b.dataset.missionId).join('|')===active.map(m=>m.id).join('|');
    if(!sameSet)switcher.innerHTML=active.map(m=>'<button type="button" class="mp-switch theme-'+getMissionThemeV177(m.id)+'" data-mission-id="'+escapeHTML(m.id)+'" onclick="switchMissionPlanV178(this.dataset.missionId)"><span class="mp-switch-dot" aria-hidden="true"></span><span class="mp-switch-copy"><span class="mp-switch-title"></span><span class="mp-switch-meta"></span></span></button>').join('');
    // Update in place so the selected state can transition instead of being re-created.
    active.forEach((m,i)=>{const b=switcher.children[i];if(!b)return;const on=m.id===selected?.id;b.classList.toggle('active',on);b.setAttribute('aria-pressed',on?'true':'false');b.querySelector('.mp-switch-title').textContent=m.title;b.querySelector('.mp-switch-meta').textContent=m.progress.completedStepIds.length+'/'+m.steps.length+' erledigt'});
  }else switcher?.remove();
  applyMissionThemeClassV177(plan,selected?.id||null);
  applyMissionThemeClassV177(anchor,selected?.id||null);
  lucide.createIcons();
}
/* v17.16: the one Home empty-state classifier. The large onboarding card is only for a user who has
   never created a Mission. Evidence of an earlier Mission (any stored Mission in any status, or a
   Mission Session in History) marks the user as returning once, and that mark is kept so deleting
   the last Mission never brings the onboarding card back. The mark is a single UI preference
   (registered in schema-versions.js); Mission and History data are only read, never changed. */
function resolveHomeEmptyStateV1716(){
 const storage=window.FocusModeApp?.services?.storage;
 const key=window.FocusModeApp?.data?.schema?.keys?.homeOnboardingDone||'focus-mode-home-onboarding-v17-16';
 try{if(storage?.getText(key,null)==='1')return 'returning'}catch(_){}
 let evidence=false;
 try{evidence=missionRepository.getAll().length>0}catch(_){}
 if(!evidence){try{evidence=typeof sessionRepository!=='undefined'&&sessionRepository.getAll().some(s=>s&&s.type==='mission')}catch(_){}}
 if(!evidence)return 'onboarding';
 try{storage?.setText(key,'1')}catch(_){}
 return 'returning';
}
function applyHomeEmptyStateV1716(){
 const home=document.getElementById('app-screen-home');
 if(!home)return;
 const state=resolveHomeEmptyStateV1716();
 if(home.dataset.homeEmpty!==state)home.dataset.homeEmpty=state;
}
window.resolveHomeEmptyStateV1716=resolveHomeEmptyStateV1716;
document.addEventListener('DOMContentLoaded',()=>{if(typeof I18N==='object'&&I18N.en)Object.assign(I18N.en,{'Bereit für ein neues Ziel?':'Ready for a new goal?','Plane es als Mission oder starte direkt einen Quick Focus.':'Plan it as a Mission or start a Quick Focus right away.','Neue Mission':'New Mission','Mission öffnen':'Open Mission'});applyHomeEmptyStateV1716()},{once:true});
function applyMissionThemeClassV177(el,missionId){
  if(!el)return;
  const theme=missionId?getMissionThemeV177(missionId):'aubergine';
  MISSION_THEMES_V177.forEach(t=>el.classList.toggle('theme-'+t,t===theme));
  el.classList.add('mission-theme');
  el.dataset.missionTheme=theme;
}

/* v17.7: stable Mission identity colors. Derived from the Mission ID (no stored field);
   a Mission only avoids a tone already held by an earlier Mission that was still running when it was created. */
const MISSION_THEMES_V177=Object.freeze(['aubergine','forest','ember','burgundy']);
function missionThemeHashV177(id){let h=2166136261;for(const c of String(id||'')){h^=c.charCodeAt(0);h=Math.imul(h,16777619)>>>0}return h%MISSION_THEMES_V177.length}
function resolveMissionThemesV177(draft){
 const all=(typeof missionRepository!=='undefined'?missionRepository.getAll():[]).slice();
 if(draft&&draft.id&&!all.some(x=>x.id===draft.id))all.push(draft);
 all.sort((a,b)=>(Number(a.createdAt)||0)-(Number(b.createdAt)||0)||String(a.id).localeCompare(String(b.id)));
 const map={},held=[];
 for(const m of all){
  const born=Number(m.createdAt)||0,taken=new Set(held.filter(h=>h.end>born).map(h=>h.idx));
  let idx=missionThemeHashV177(m.id);
  for(let i=0;i<MISSION_THEMES_V177.length&&taken.has(idx);i++)idx=(idx+1)%MISSION_THEMES_V177.length;
  map[m.id]=MISSION_THEMES_V177[idx];
  held.push({idx,end:m.status==='active'?Infinity:(Number(m.completedAt||m.archivedAt||m.updatedAt)||0)});
 }
 return map;
}
function getMissionThemeV177(id){return id?(resolveMissionThemesV177()[id]||MISSION_THEMES_V177[missionThemeHashV177(id)]):MISSION_THEMES_V177[0]}
function previewMissionThemeV177(draftId){return draftId?resolveMissionThemesV177({id:draftId,createdAt:Date.now(),status:'active'})[draftId]:MISSION_THEMES_V177[0]}
window.getMissionThemeV177=getMissionThemeV177;window.previewMissionThemeV177=previewMissionThemeV177;
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
   deadlineText:missionDeadlineV1712(m.deadlineText).label,
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
   theme:getMissionThemeV177(m.id)
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
   /* v17.16: progress reads first (left), the primary action closes the row (right). */
   +'<div class="mh-bottom-row">'
     +'<div class="mh-progress-readout"><span class="mh-progress-pct">'+vm.progressPercent+'%</span><span class="mh-progress-lbl">MISSION</span></div>'
     +'<button class="mh-start-btn" type="button" onclick="'+startCall+'"><span>'+(vm.currentStepId?'Schritt starten':'Mission öffnen')+'</span><i data-lucide="'+(vm.currentStepId?'play':'arrow-right')+'" class="w-3.5 h-3.5'+(vm.currentStepId?' fill-current':'')+'"></i></button>'
   +'</div>'
 +'</article>';
}
function renderHomeCarouselV116(){
 const active=sortedActiveMissionsV116(),old=document.getElementById('missionCarouselV116'),card=document.getElementById('activeMissionCard'),empty=document.getElementById('emptyMissionCard');
 if(active.length<2){old?.remove();renderMultiMissionUIV115();return}
 empty?.classList.add('hidden');card?.classList.add('hidden');let host=old;if(!host){host=document.createElement('div');host.id='missionCarouselV116';card.insertAdjacentElement('afterend',host)}host.innerHTML=active.map(m=>renderMissionHeroV116(missionViewModelV116(m))).join('');host.scrollLeft=0;
}
function focusMissionListV116(){
 const active=sortedActiveMissionsV116();
 const lang=(window.FocusModeApp?.services?.storage?.getText('focus-mode-language','de')||document.documentElement.lang||'de')==='en'?'en':'de';
 if(!active.length){
   return '<div class="fm-plus-mission-empty" role="status">'
     +'<span class="fm-plus-empty-icon" aria-hidden="true"><i data-lucide="target"></i></span>'
     +'<span class="fm-plus-empty-text">'+(lang==='en'?'No active mission yet.':'Noch keine aktive Mission.')+'</span>'
   +'</div>';
 }
 return active.map(m=>{
   const v=missionViewModelV116(m);
   const stepMeta=v.currentStepMinutes
     ?(escapeHTML(v.currentStepTitle)+' · '+v.currentStepMinutes+(lang==='en'?' min':' Min.'))
     :escapeHTML(v.currentStepTitle);
   const rolePill=v.isPrimary
     ?'<span class="fm-plus-primary-pill">'+(lang==='en'?'PRIMARY':'HAUPT')+'</span>'
     :'';
   const iconName=v.isPrimary?'star':'target';
   return '<button class="focus-mission-v116 fm-plus-mission-row" type="button" onclick="openMissionFromFocusV116(\''+m.id+'\')">'
     +'<span class="fm-icon fm-plus-mission-icon'+(v.isPrimary?' is-primary':'')+'"><i data-lucide="'+iconName+'"></i></span>'
     +'<span class="fm-copy fm-plus-mission-copy">'
       +'<span class="fm-plus-mission-title-row"><span class="fm-title">'+escapeHTML(v.title)+'</span>'+rolePill+'</span>'
       +'<span class="fm-meta">'+stepMeta+'</span>'
       +'<span class="fm-plus-mission-progress-row">'
         +'<span class="fm-plus-mission-bar"><i style="width:'+v.progressPercent+'%"></i></span>'
         +'<strong class="fm-plus-mission-pct">'+v.progressPercent+'%</strong>'
       +'</span>'
     +'</span>'
     +'<span class="fm-plus-mission-arrow" aria-hidden="true"><i data-lucide="chevron-right"></i></span>'
   +'</button>';
 }).join('');
}
function renderFocusChoiceV116(){
 const sheet=document.querySelector('#focusChoiceOverlay .focus-choice-sheet');if(!sheet)return;
 const all=missionRepository.getAll(),active=missionRepository.getActive(),activeCount=active.length,totalCount=all.length,full=activeCount>=2;
 const lang=(window.FocusModeApp?.services?.storage?.getText('focus-mode-language','de')||document.documentElement.lang||'de')==='en'?'en':'de';
 const manageSub=lang==='en'?'Active, completed and archived':'Aktiv, abgeschlossen und archiviert';
 const limitBadge=full?'<span class="fm-limit-badge-v17"><i data-lucide="info"></i><span>'+(lang==='en'?'2 active Missions reached':'2 aktive Missionen erreicht')+'</span></span>':'';
 sheet.innerHTML='<div class="fm-plus-handle" aria-hidden="true"></div>'
   +'<div class="fm-plus-header">'
     +'<div class="fm-plus-header-copy">'
       +'<h3 class="fm-plus-title">Was möchtest du fokussieren?</h3>'
       +'<p class="fm-plus-subtitle">Starte direkt oder arbeite an einer Mission weiter.</p>'
     +'</div>'
     +'<button class="fm-plus-close-btn" type="button" aria-label="'+(lang==='en'?'Close':'Schließen')+'" onclick="closeFocusChoice()"><i data-lucide="x"></i></button>'
   +'</div>'
   +'<div class="fm-plus-primary-grid" role="group" aria-label="Fokus-Modi">'
     +'<button class="fm-plus-card fm-plus-card-quick" id="plusQuickFocusCard" type="button" onclick="openQuickSetupFromChoice()">'
       +'<div class="fm-plus-card-top">'
         +'<span class="fm-plus-card-icon"><i data-lucide="timer"></i></span>'
         +'<span class="fm-plus-card-arrow" aria-hidden="true"><i data-lucide="arrow-up-right"></i></span>'
       +'</div>'
       +'<div class="fm-plus-card-body">'
         +'<strong class="fm-plus-card-title">Kurzer Fokus</strong>'
         +'<span class="fm-plus-card-desc">Eine Aufgabe, direkt starten.</span>'
       +'</div>'
     +'</button>'
     +'<button class="fm-plus-card fm-plus-card-mission'+(full?' is-limit-reached':'')+'" id="plusFocusMissionCard" type="button" onclick="newMissionFromFocusV116()">'
       +'<div class="fm-plus-card-top">'
         +'<span class="fm-plus-card-icon"><i data-lucide="clipboard-list"></i></span>'
         +'<span class="fm-plus-card-arrow" aria-hidden="true"><i data-lucide="plus"></i></span>'
       +'</div>'
       +'<div class="fm-plus-card-body">'
         +'<strong class="fm-plus-card-title">Focus Mission</strong>'
         +'<span class="fm-plus-card-desc">Ein Ziel planen und in Schritte teilen.</span>'
       +'</div>'
     +'</button>'
   +'</div>'
   +'<div class="fm-plus-footer-section">'
     +'<button class="focus-manage-v116 focus-manage-card-v17 fm-plus-manage-row" id="plusManageMissionsBtn" type="button" onclick="openMissionManagementV116()">'
       +'<span class="fm-manage-main">'
         +'<span class="fm-manage-icon"><i data-lucide="sliders-horizontal"></i></span>'
         +'<span class="fm-manage-copy">'
           +'<span class="fm-manage-title">Missionen verwalten</span>'
           +'<span class="fm-manage-sub">'+escapeHTML(manageSub)+'</span>'
         +'</span>'
         +'<span class="fm-plus-manage-meta" data-dynamic-mission-counts="true">'+activeCount+' · '+totalCount+'</span>'
         +'<i class="fm-manage-chevron" data-lucide="chevron-right"></i>'
       +'</span>'
       +limitBadge
     +'</button>'
   +'</div>';
 if(typeof translateTree==='function')translateTree(sheet,lang);
 const subEl=sheet.querySelector('[data-dynamic-mission-counts="true"]');
 if(subEl)subEl.textContent=activeCount+' · '+totalCount;
 lucide.createIcons();
}
function openMissionFromFocusV116(id){
 closeFocusChoice({immediate:true});
 openMission(id);
}
function newMissionFromFocusV116(){
 if(missionRepository.getActive().length>=2){
   closeFocusChoice({immediate:true});
   openMissionManagementV116();
   setTimeout(()=>showMissionLimitV116(),90);
   return;
 }
 const navState=window.__navigationStateV17;
 const currentScreen=navState?.currentScreen||'home';
 const currentRoot=navState?.currentRoot||'home';
 closeFocusChoice({immediate:true});
 openGoalIntake({parent:currentScreen,rootSection:currentRoot,reopenFocusChoice:false});
}
const openFocusChoicePreV116=openFocusChoice;
openFocusChoice=function(){
 if(activeSession||sessionRecoveryCandidate||getRecoverableSessionV1151()){routeExistingSessionV1151();return}
 const overlay=document.getElementById('focusChoiceOverlay');
 if(overlay&&!overlay.classList.contains('hidden')&&!overlay.classList.contains('fm-sheet-closing')){
   closeFocusChoice();
   return;
 }
 renderFocusChoiceV116();
 const res=openFocusChoicePreV116.apply(this,arguments);
 /* Keep floating bottom navigation sharp and visible while Plus panel is open */
 window.showBottomNavigation?.();
 document.getElementById('bottomNavigationV17')?.classList.add('fm-plus-dock-active');
 return res;
};
const closeFocusChoicePreV175=closeFocusChoice;
closeFocusChoice=function(){
 document.getElementById('bottomNavigationV17')?.classList.remove('fm-plus-dock-active');
 const res=closeFocusChoicePreV175.apply(this,arguments);
 window.syncBottomNavigationForScreen?.();
 return res;
};

function ensureMissionManagementScreenV116(){
 if(document.getElementById('app-screen-missions'))return;
 const screen=document.createElement('div');
 screen.id='app-screen-missions';
 screen.className='app-screen hidden flex flex-col flex-1 min-h-0';
 screen.innerHTML='<header class="st-header fm-root-header shrink-0">'
   +'<div class="st-header-copy"><span class="st-kicker">Focus Missions</span><h2 class="st-title">Deine Missionen</h2></div>'
   +'<button class="fm-root-header-btn is-primary" type="button" aria-label="Neue Focus Mission" title="Neue Focus Mission" onclick="newMissionFromManagerV116()"><i data-lucide="plus"></i></button>'
   +'</header>'
   /* v17.11 Missions summary on the shared clean hero surface; filled from real data below. */
   +'<section id="missionOverviewCardV173" class="fm-hero shrink-0" aria-labelledby="missionOverviewKicker">'
     +'<div class="fm-hero-head">'
       +'<span class="fm-hero-kicker" id="missionOverviewKicker"><i data-lucide="target" aria-hidden="true"></i>Missionssystem</span>'
       +'<span class="fm-hero-chip" id="missionOverviewCapacity" data-state="open"><span class="fm-hero-dot" aria-hidden="true"></span><span id="missionOverviewCapacityText">2 Plätze frei</span></span>'
     +'</div>'
     +'<div class="fm-hero-main">'
       +'<div class="fm-hero-figure"><p class="fm-hero-value"><span id="missionOverviewActiveSlot">0</span><small>/ 2</small></p><p class="fm-hero-label">aktive Missionen</p></div>'
       +'<div class="fm-hero-aside hidden" id="missionOverviewPrep"><strong id="missionOverviewPrepValue">0%</strong><span>vorbereitet</span></div>'
     +'</div>'
     +'<p class="fm-hero-sub" id="missionOverviewNext"></p>'
     +'<div class="fm-hero-stats">'
       +'<div><strong id="missionOverviewStepsDoneCount">0</strong><span>Schritte erledigt</span></div>'
       +'<div class="is-ok"><strong id="missionOverviewCompletedCount">0</strong><span>Abgeschlossen</span></div>'
       +'<div><strong id="missionOverviewArchivedCount">0</strong><span>Im Archiv</span></div>'
     +'</div>'
   +'</section>'
   +'<div class="mission-tabs-v116 mb-3.5 shrink-0">'
     +'<button class="mission-tab-v116 active" type="button" data-filter="active" onclick="setMissionFilterV116(\'active\',this)">Aktiv</button>'
     +'<button class="mission-tab-v116" type="button" data-filter="completed" onclick="setMissionFilterV116(\'completed\',this)">Abgeschlossen</button>'
     +'<button class="mission-tab-v116" type="button" data-filter="archived" onclick="setMissionFilterV116(\'archived\',this)">Archiv</button>'
   +'</div>'
   +'<div id="missionManagementListV116" class="mmc-list"></div>';
 document.getElementById('appBody').appendChild(screen);ensureMissionSheetV116();lucide.createIcons();
}
let lastMissionSheetTriggerV175=null;
function renderMissionSheetActionItemV175(opts){
 const cls=['mission-sheet-action-v116',opts.variant||''].filter(Boolean).join(' ');
 const iconName=opts.icon||'circle';
 const iconHtml=typeof window.renderFocusIconSvg==='function'?window.renderFocusIconSvg(iconName):'<i data-lucide="'+iconName+'"></i>';
 const trailingHtml=opts.trailingIcon?(typeof window.renderFocusIconSvg==='function'?window.renderFocusIconSvg(opts.trailingIcon,'mission-sheet-trailing-icon'):'<i data-lucide="'+opts.trailingIcon+'" class="mission-sheet-trailing-icon"></i>'):'';
 return '<button class="'+cls+'" type="button" data-mission-action="'+(opts.actionKey||iconName)+'" onclick="'+opts.onclick+'">'
   +'<span class="mission-sheet-action-icon" aria-hidden="true">'+iconHtml+'</span>'
   +'<span class="mission-sheet-action-label">'+escapeHTML(opts.label||'')+'</span>'
   +trailingHtml
 +'</button>';
}
function ensureMissionSheetV116(){if(document.getElementById('missionSheetV116'))return;const sheet=document.createElement('div');sheet.id='missionSheetV116';sheet.setAttribute('role','dialog');sheet.setAttribute('aria-modal','true');sheet.onclick=e=>{if(e.target===sheet)closeMissionSheetV116()};sheet.innerHTML='<div class="mission-sheet-card-v116" id="missionSheetCardV116"></div>';document.getElementById('screenWrapper').appendChild(sheet)}
function openMissionSheetV116(content,triggerEl){
 ensureMissionSheetV116();
 if(triggerEl instanceof HTMLElement){
   lastMissionSheetTriggerV175=triggerEl;
 }else if(document.activeElement instanceof HTMLElement&&document.activeElement!==document.body){
   lastMissionSheetTriggerV175=document.activeElement;
 }
 const card=document.getElementById('missionSheetCardV116');
 const sheet=document.getElementById('missionSheetV116');
 card.innerHTML='<div class="mission-sheet-handle-v175" aria-hidden="true"></div>'+content;
 const lang=(window.FocusModeApp?.services?.storage?.getText('focus-mode-language','de')||document.documentElement.lang||'de')==='en'?'en':'de';
 if(typeof translateTree==='function')translateTree(card,lang);
 sheet.classList.add('open');
 lucide.createIcons();
}
function closeMissionSheetV116(){
 const sheet=document.getElementById('missionSheetV116');
 sheet?.classList.remove('open');
 window.syncBottomNavigationForScreen?.();
 if(lastMissionSheetTriggerV175&&document.contains(lastMissionSheetTriggerV175)){
   const target=lastMissionSheetTriggerV175;
   lastMissionSheetTriggerV175=null;
   setTimeout(()=>{try{target.focus({preventScroll:true})}catch{}},40);
 }
}
function missionHasActiveSessionV116(id){const s=window.activeSession||sessionRecoveryCandidate;return Boolean(s?.type==='mission'&&s.missionId===id&&ACTIVE_SESSION_STATES.includes(s.lifecycleState))}
function renameMissionV116(id,title){const clean=String(title||'').trim().slice(0,80);if(!clean)return{ok:false,error:'title_invalid'};const m=missionRepository.update(id,{title:clean});return m?{ok:true,mission:m}:{ok:false,error:'mission_not_found'}}
function restoreMissionV116(id){if(missionRepository.getActive().length>=2)return{ok:false,error:'active_mission_limit_reached'};const m=missionRepository.getById(id);if(!m||m.status!=='archived')return{ok:false,error:'mission_not_found'};const stepId=m.archivedCurrentStepId||m.steps.find(x=>x.status!=='completed')?.id||null;const updated=missionRepository.update(id,{status:stepId?'active':'completed',archivedAt:null,currentStepId:stepId,archivedCurrentStepId:null});if(updated?.status==='active'&&!missionRepository.getPrimaryId())missionRepository.setPrimary(id);return updated?{ok:true,mission:updated}:{ok:false,error:'restore_failed'}}
function archiveMissionV116(id){if(missionHasActiveSessionV116(id))return{ok:false,error:'active_session_exists'};const m=missionRepository.getById(id);if(!m)return{ok:false,error:'mission_not_found'};return missionRepository.archive(id,{archivedCurrentStepId:m.currentStepId})}
const repoArchiveV116=missionRepository.archive.bind(missionRepository);missionRepository.archive=function(id){const m=this.getById(id);if(!m)return{ok:false,error:'mission_not_found'};if(missionHasActiveSessionV116(id))return{ok:false,error:'active_session_exists'};const r=this.update(id,{status:'archived',archivedAt:Date.now(),archivedCurrentStepId:m.currentStepId,currentStepId:null});return r?{ok:true,mission:r}:{ok:false,error:'mission_not_found'}};
missionRepository.rename=renameMissionV116;missionRepository.restore=restoreMissionV116;missionRepository.getCompleted=function(){return this.getAll().filter(x=>x.status==='completed')};missionRepository.getArchived=function(){return this.getAll().filter(x=>x.status==='archived')};
function syncMissionFilterTabsV1751(){
 const valid=['active','completed','archived'];
 if(!valid.includes(missionManagementFilterV116))missionManagementFilterV116='active';
 document.querySelectorAll('.mission-tab-v116').forEach(btn=>{
   btn.classList.toggle('active',btn.dataset.filter===missionManagementFilterV116);
 });
}
function resetMissionManagementFilterToActiveV1751(){
 missionManagementFilterV116='active';
 syncMissionFilterTabsV1751();
}
window.resetMissionManagementFilterToActiveV1751=resetMissionManagementFilterToActiveV1751;
function setMissionFilterV116(filter,button){missionManagementFilterV116=filter;syncMissionFilterTabsV1751();renderMissionManagementV116()}
function formatMissionShortDateV173(ts){
 if(!ts)return '';
 try{return new Intl.DateTimeFormat('de-DE',{day:'2-digit',month:'2-digit',year:'numeric'}).format(new Date(ts));}catch{return '';}
}
function renderMissionManagementV116(){
 ensureMissionManagementScreenV116();
 syncMissionFilterTabsV1751();
 const list=document.getElementById('missionManagementListV116');
 const all=missionRepository.getAll();
 const activeList=all.filter(x=>x.status==='active');
 const completedList=all.filter(x=>x.status==='completed');
 const archivedList=all.filter(x=>x.status==='archived');
 const totalStepsDone=all.reduce((acc,m)=>acc+(m.progress?.completedStepIds?.length||0),0);
 const setText=(id,v)=>{const el=document.getElementById(id);if(el)el.textContent=v};
 setText('missionOverviewActiveSlot',String(activeList.length));
 const capacity=document.getElementById('missionOverviewCapacity');
 if(capacity)capacity.dataset.state=activeList.length>=2?'full':'open';
 setText('missionOverviewCapacityText',activeList.length>=2?'Beide Plätze belegt':(2-activeList.length)===1?'1 Platz frei':'2 Plätze frei');
 setText('missionOverviewCompletedCount',String(completedList.length));
 setText('missionOverviewArchivedCount',String(archivedList.length));
 setText('missionOverviewStepsDoneCount',String(totalStepsDone));
 /* Share of the active Missions' steps that are done (same counts the cards show). */
 const activeTotals=activeList.reduce((acc,m)=>{const v=missionViewModelV116(m);acc.done+=v.completedSteps;acc.total+=v.totalSteps;return acc},{done:0,total:0});
 const prep=document.getElementById('missionOverviewPrep');
 if(prep)prep.classList.toggle('hidden',!activeTotals.total);
 setText('missionOverviewPrepValue',activeTotals.total?Math.min(100,Math.round(activeTotals.done/activeTotals.total*100))+'%':'');
 /* Nearest real deadline: only stored deadline texts that hold an explicit calendar date. */
 const parse=window.MissionCalendar?.parseDeadlineText;
 const today=new Date();const todayKey=today.getFullYear()+'-'+String(today.getMonth()+1).padStart(2,'0')+'-'+String(today.getDate()).padStart(2,'0');
 const next=typeof parse==='function'?activeList.map(m=>({m,key:parse(m.deadlineText)})).filter(x=>x.key&&x.key>=todayKey).sort((a,b)=>a.key.localeCompare(b.key))[0]:null;
 let nextText=activeList.length?'':'Bis zu zwei Missionen gleichzeitig';
 if(next){const[y,mo,d]=next.key.split('-').map(Number);let when='';try{when=new Intl.DateTimeFormat('de-DE',{day:'numeric',month:'short'}).format(new Date(y,mo-1,d,12))}catch{when=next.key}nextText='Nächste Frist: '+when+' · '+next.m.title}
 setText('missionOverviewNext',nextText);

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
   const isActive=v.status==='active',isDone=v.status==='completed',isArchived=v.status==='archived';
   card.className='mmc-card theme-'+v.theme+' is-'+(isActive?'active':isDone?'completed':'archived');
   card.dataset.missionId=m.id;
   const statusLabel=isActive?'Aktive Mission':isDone?'Abgeschlossen':'Archiviert';
   /* State is named in text, never by colour alone. */
   card.setAttribute('aria-label',v.title+' · '+statusLabel);
   const dateStr=isDone?formatMissionShortDateV173(v.completedAt||v.updatedAt):isArchived?formatMissionShortDateV173(v.archivedAt||v.updatedAt):'';
   /* Identity band: Mission tone, subject and title; finished states are named there in text. */
   const chip=isActive?'':'<span class="mmc-chip"><i data-lucide="'+(isDone?'check':'archive')+'" aria-hidden="true"></i>'+statusLabel+(dateStr?' · '+escapeHTML(dateStr):'')+'</span>';
   /* v17.12 one compact summary row: progress, step count and (active only) the real deadline or
      the stable "Ohne Deadline" label from the single deadline helper. */
   const deadline=isActive?missionDeadlineV1712(m.deadlineText):null;
   const deadlineHTML=deadline
     ?'<span class="mmc-deadline'+(deadline.hasDeadline?'':' is-none')+'">'
       +'<i data-lucide="'+(deadline.hasDeadline?'calendar-days':'calendar-off')+'" aria-hidden="true"></i>'
       +(deadline.hasDeadline?'<span class="mmc-sr">Deadline:</span> ':'')
       +(deadline.iso?'<time datetime="'+deadline.iso+'">'+escapeHTML(deadline.short)+'</time>':'<span>'+escapeHTML(deadline.short)+'</span>')
     +'</span>'
     :'';
   /* Step route: one segment per step (completed, current, upcoming); a plain bar for long plans. */
   const route=v.totalSteps&&v.totalSteps<=14
     ?v.steps.map(x=>'<i class="'+(x.isCompleted?'is-done':(isActive&&x.isCurrent?'is-current':''))+'"></i>').join('')
     :'<b style="width:'+v.progressPercent+'%"></b>';
   const progressCopy=isDone?'Alle '+v.totalSteps+' Schritte erledigt':v.completedSteps+' von '+v.totalSteps+' Schritten';
   const stepsHTML=isDone
     ?'<span class="mmc-steps">'+escapeHTML(progressCopy)+'</span>'
     :'<span class="mmc-steps"><b>'+v.completedSteps+'/'+v.totalSteps+'</b> Schritte</span>';
   const next=isActive&&v.currentStepId
     ?'<div class="mmc-next">'
       +'<span class="mmc-next-index" aria-hidden="true">'+String(v.currentStepIndex).padStart(2,'0')+'</span>'
       +'<span class="mmc-next-copy"><small>Nächster Schritt'+(v.currentStepMinutes?' · '+v.currentStepMinutes+' Min.':'')+'</small><strong>'+escapeHTML(v.currentStepTitle)+'</strong></span>'
     +'</div>'
     :'';
   const primary=isActive&&v.currentStepId
     ?'<button type="button" class="mmc-btn is-primary" onclick="event.stopPropagation();prepareLearningSession(\''+m.id+'\',\''+v.currentStepId+'\')"><i data-lucide="play" aria-hidden="true"></i><span>Schritt starten</span></button>'
     :isArchived
       ?'<button type="button" class="mmc-btn is-primary is-neutral" onclick="event.stopPropagation();restoreMissionActionV116(\''+m.id+'\')"><i data-lucide="rotate-ccw" aria-hidden="true"></i><span>Wiederherstellen</span></button>'
       :'';
   card.innerHTML='<div class="mmc-band">'
       +'<span class="mmc-emblem" aria-hidden="true"><i data-lucide="'+(isDone?'check':isArchived?'archive':'target')+'"></i></span>'
       +'<button type="button" class="mmc-headline" aria-label="Plan von '+escapeHTML(v.title)+' öffnen">'
         +'<span class="mmc-subject">'+escapeHTML(v.subject)+'</span>'
         +'<span class="mmc-title">'+escapeHTML(v.title)+'</span>'
       +'</button>'
       +'<button class="mmc-menu" type="button" aria-label="Mission verwalten"><i data-lucide="more-horizontal"></i></button>'
       +chip
     +'</div>'
     +'<div class="mmc-body">'
       +'<div class="mmc-summary"><strong class="mmc-pct">'+v.progressPercent+'%</strong>'+stepsHTML+deadlineHTML+'</div>'
       +'<div class="mmc-route'+(v.totalSteps&&v.totalSteps<=14?'':' is-bar')+'" role="progressbar" aria-label="Fortschritt" aria-valuemin="0" aria-valuemax="100" aria-valuenow="'+v.progressPercent+'" aria-valuetext="'+escapeHTML(v.progressPercent+'% · '+progressCopy)+'">'+route+'</div>'
       +next
       +'<div class="mmc-actions">'
         +primary
         +'<button type="button" class="mmc-btn is-quiet" onclick="event.stopPropagation();openMission(\''+m.id+'\',\'missions\')"><span>Plan ansehen</span><i data-lucide="arrow-right" aria-hidden="true"></i></button>'
       +'</div>'
     +'</div>';
   card.querySelector('.mmc-headline').onclick=()=>openMission(m.id,'missions');
   const menuBtn=card.querySelector('.mmc-menu');
   menuBtn.onclick=e=>{e.stopPropagation();openMissionActionsV116(m.id,menuBtn)};
   list.append(card);
 });
 lucide.createIcons();
}
function openMissionManagementV116(){
 closeFocusChoice({immediate:true});
 ensureMissionManagementScreenV116();
 resetMissionManagementFilterToActiveV1751();
 renderMissionManagementV116();
 if(typeof navigateToRoot==='function')navigateToRoot('missions');
 else setAppScreen('missions',{immediate:true});
}
function openMissionManagementFromSettings(){openMissionManagementV116()}
window.openMissionManagementV116=openMissionManagementV116;
window.openMissionManagementFromSettings=openMissionManagementFromSettings;
function newMissionFromManagerV116(){if(missionRepository.getActive().length>=2){showMissionLimitV116();return}if(typeof navigateForward==='function')navigateForward('goal',{parent:'missions',rootSection:'missions'});else setAppScreen('goal')}
function showMissionLimitV116(){
 openMissionSheetV116(
   '<h3>2 aktive Missionen erreicht</h3>'
   +'<p>Archiviere oder schließe zuerst eine aktive Mission ab, bevor du eine neue erstellst.</p>'
   +'<div class="mission-sheet-actions-group">'
     +renderMissionSheetActionItemV175({variant:'primary',icon:'check',actionKey:'understood',label:'Verstanden',onclick:'closeMissionSheetV116()'})
   +'</div>'
 );
}
function openMissionActionsV116(id,triggerEl){
 const m=missionRepository.getById(id);if(!m)return;
 const v=missionViewModelV116(m);
 let listHtml='<div class="mission-sheet-actions-group">';
 listHtml+=renderMissionSheetActionItemV175({
   variant:'primary',
   icon:'folder-open',
   actionKey:'open',
   label:'Öffnen',
   trailingIcon:'arrow-up-right',
   onclick:'closeMissionSheetV116();openMission(\''+id+'\')'
 });
 if(m.status==='active'&&!v.isPrimary){
   listHtml+=renderMissionSheetActionItemV175({
     variant:'',
     icon:'star',
     actionKey:'make-primary',
     label:'Als Hauptmission festlegen',
     onclick:'makePrimaryV116(\''+id+'\')'
   });
 }
 listHtml+=renderMissionSheetActionItemV175({
   variant:'',
   icon:'pencil',
   actionKey:'rename',
   label:'Umbenennen',
   onclick:'openRenameMissionV116(\''+id+'\')'
 });
 if(m.status!=='archived'){
   listHtml+=renderMissionSheetActionItemV175({
     variant:'archive-neutral',
     icon:'archive',
     actionKey:'archive',
     label:'Archivieren',
     onclick:'confirmArchiveV116(\''+id+'\')'
   });
 }else{
   listHtml+=renderMissionSheetActionItemV175({
     variant:'',
     icon:'rotate-ccw',
     actionKey:'restore',
     label:'Wiederherstellen',
     onclick:'restoreMissionActionV116(\''+id+'\')'
   });
 }
 listHtml+='<div class="mission-sheet-separator-v116" role="separator"></div>';
 listHtml+=renderMissionSheetActionItemV175({
   variant:'danger',
   icon:'trash-2',
   actionKey:'delete',
   label:'Löschen',
   onclick:'confirmDeleteV116(\''+id+'\')'
 });
 listHtml+=renderMissionSheetActionItemV175({
   variant:'cancel',
   icon:'x',
   actionKey:'cancel',
   label:'Abbrechen',
   onclick:'closeMissionSheetV116()'
 });
 listHtml+='</div>';
 const headerHtml='<div class="mission-sheet-head-v175">'
   +'<div class="mission-sheet-head-copy">'
     +'<h3>'+escapeHTML(m.title)+'</h3>'
     +'<p>Wähle eine Aktion für diese Mission.</p>'
   +'</div>'
   +'<button type="button" class="mission-sheet-close-btn" aria-label="Schließen" onclick="closeMissionSheetV116()"><i data-lucide="x"></i></button>'
 +'</div>';
 openMissionSheetV116(headerHtml+listHtml,triggerEl);
}
function makePrimaryV116(id){missionRepository.setPrimary(id);closeMissionSheetV116();refreshMissionUIV116()}
function openRenameMissionV116(id){
 const m=missionRepository.getById(id);
 openMissionSheetV116(
   '<div class="mission-sheet-head-v175"><div class="mission-sheet-head-copy"><h3>Mission umbenennen</h3><p>Der Plan und die bisherige Historie bleiben unverändert.</p></div><button type="button" class="mission-sheet-close-btn" aria-label="Schließen" onclick="closeMissionSheetV116()"><i data-lucide="x"></i></button></div>'
   +'<input class="mission-sheet-input-v116" id="renameMissionInputV116" maxlength="80" value="'+escapeHTML(m?.title||'')+'">'
   +'<div class="mission-sheet-actions-group">'
     +renderMissionSheetActionItemV175({variant:'primary',icon:'check',actionKey:'save-rename',label:'Speichern',onclick:'saveRenameMissionV116(\''+id+'\')'})
     +renderMissionSheetActionItemV175({variant:'cancel',icon:'x',actionKey:'cancel',label:'Abbrechen',onclick:'closeMissionSheetV116()'})
   +'</div>'
 );
 setTimeout(()=>{const inp=document.getElementById('renameMissionInputV116');if(inp){inp.focus();inp.select?.()}},60);
}
function saveRenameMissionV116(id){const r=renameMissionV116(id,document.getElementById('renameMissionInputV116')?.value);if(!r.ok)return;closeMissionSheetV116();refreshMissionUIV116()}
function confirmArchiveV116(id){
 if(missionHasActiveSessionV116(id)){
   openMissionSheetV116(
     '<h3>Mission wird gerade verwendet</h3>'
     +'<p>Beende oder verwirf zuerst die laufende Fokus-Sitzung.</p>'
     +'<div class="mission-sheet-actions-group">'
       +renderMissionSheetActionItemV175({variant:'primary',icon:'play',actionKey:'go-session',label:'Zur Sitzung',onclick:'closeMissionSheetV116();routeExistingSessionV1151()'})
       +renderMissionSheetActionItemV175({variant:'cancel',icon:'x',actionKey:'cancel',label:'Abbrechen',onclick:'closeMissionSheetV116()'})
     +'</div>'
   );
   return;
 }
 openMissionSheetV116(
   '<h3>Mission archivieren?</h3>'
   +'<p>Der Fortschritt bleibt gespeichert und kann später wiederhergestellt werden.</p>'
   +'<div class="mission-sheet-actions-group">'
     +renderMissionSheetActionItemV175({variant:'archive-confirm',icon:'archive',actionKey:'confirm-archive',label:'Archivieren',onclick:'archiveMissionActionV116(\''+id+'\')'})
     +renderMissionSheetActionItemV175({variant:'cancel',icon:'x',actionKey:'cancel',label:'Abbrechen',onclick:'closeMissionSheetV116()'})
   +'</div>'
 );
}
function archiveMissionActionV116(id){const r=missionRepository.archive(id);if(!r.ok)return;closeMissionSheetV116();refreshMissionUIV116()}
function restoreMissionActionV116(id){const r=restoreMissionV116(id);if(!r.ok){showMissionLimitV116();return}closeMissionSheetV116();refreshMissionUIV116()}
function confirmDeleteV116(id){
 if(missionHasActiveSessionV116(id)){confirmArchiveV116(id);return}
 openMissionSheetV116(
   '<h3>Mission endgültig löschen?</h3>'
   +'<p>Der Missionsplan wird entfernt. Bereits gespeicherte Fokus-Sitzungen bleiben im Verlauf.</p>'
   +'<div class="mission-sheet-actions-group">'
     +renderMissionSheetActionItemV175({variant:'danger',icon:'trash-2',actionKey:'confirm-delete',label:'Endgültig löschen',onclick:'deleteMissionActionV116(\''+id+'\')'})
     +renderMissionSheetActionItemV175({variant:'cancel',icon:'x',actionKey:'cancel',label:'Abbrechen',onclick:'closeMissionSheetV116()'})
   +'</div>'
 );
}
function deleteMissionActionV116(id){const r=missionRepository.delete(id);if(!r.ok)return;closeMissionSheetV116();refreshMissionUIV116()}
function refreshMissionUIV116(){renderMultiMissionUIV115();renderHomeCarouselV116();renderFocusChoiceV116();if(document.getElementById('app-screen-missions'))renderMissionManagementV116();const selected=missionRepository.getSelected();if(selected){bindMissionCompatibilityV115(selected);if(!document.getElementById('app-screen-plan').classList.contains('hidden'))renderAIPlan(activeAIPlan)}}
const applyScreenBeforeV116=applyAppScreenImmediately;
applyAppScreenImmediately=function(screen){
 if(screen==='missions'){
   ensureMissionManagementScreenV116();
   const list=document.getElementById('missionManagementListV116');
   if(!list||!list.childNodes.length)renderMissionManagementV116();
 }
 const r=applyScreenBeforeV116.apply(this,arguments);
 if(screen==='home')setTimeout(renderHomeCarouselV116,0);
 return r;
};
const createBeforeV116=missionRepository.create.bind(missionRepository);missionRepository.create=function(input){const r=createBeforeV116(input);if(r.ok)setTimeout(refreshMissionUIV116,0);return r};
document.addEventListener('DOMContentLoaded',()=>{ensureMissionManagementScreenV116();setTimeout(()=>{refreshMissionUIV116();console.info('[MissionsUX]',{event:'v17_5_1_ready',active:missionRepository.getActive().length})},60)},{once:true});
