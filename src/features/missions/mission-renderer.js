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

/* v11.5 / v11.5.1 Multi-Mission Plan & Home Renderer */
const legacyRenderPlanV115=renderAIPlan;renderAIPlan=function(plan){const selected=missionRepository.getSelected();if(selected&&(!plan||plan.id!==selected.id)){bindMissionCompatibilityV115(selected);plan=activeAIPlan}return legacyRenderPlanV115.call(this,plan)};

function renderMultiMissionUIV115(){
  document.getElementById('secondaryMissionCard')?.remove();
  const active=missionRepository.getActive();
  const primary=missionRepository.getPrimary();
  const selected=missionRepository.getSelected()||primary;
  const home=primary||active[0]||null;
  const empty=document.getElementById('emptyMissionCard');
  const card=document.getElementById('activeMissionCard');
  if(home){
    empty?.classList.add('hidden');card?.classList.remove('hidden');
    const step=home.steps.find(x=>x.id===home.currentStepId);
    const title=document.querySelector('#activeMissionCard h3');
    const meta=document.querySelector('#activeMissionCard h3+p');
    if(title)title.textContent=home.title;
    if(meta)meta.textContent=home.subject+' · '+(home.deadlineText||'ohne Frist');
    const next=document.getElementById('homeMissionNextStep');
    const detail=document.getElementById('homeMissionMeta');
    if(next)next.textContent=step?.title||'Mission abgeschlossen';
    if(detail)detail.textContent=step?((step.task||step.reason)+' · ca. '+step.durationMinutes+' Min.'):'Alle Schritte bearbeitet';
    const pct=home.steps.length?Math.round(home.progress.completedStepIds.length/home.steps.length*100):0;
    const percent=document.getElementById('homeMissionPercent');
    const bar=document.getElementById('homeMissionProgressBar');
    if(percent)percent.textContent=pct+'%';if(bar)bar.style.width=pct+'%';
    if(card)card.onclick=e=>{if(!e.target.closest('button')||e.target.closest('.active-mission-step'))openMission(home.id)};
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

const oldApplyScreenV115=applyAppScreenImmediately;applyAppScreenImmediately=function(screen){if(screen==='plan'){const m=missionRepository.getSelected();if(m){bindMissionCompatibilityV115(m);legacyRenderPlanV115(activeAIPlan)}}const r=oldApplyScreenV115.apply(this,arguments);if(screen==='home'||screen==='plan')setTimeout(renderMultiMissionUIV115,0);return r};
document.addEventListener('DOMContentLoaded',()=>{migrateLegacyMissionV115();const st=loadMissionStoreV115(),initial=missionRepository.getById(st.selectedMissionId)||missionRepository.getPrimary();bindMissionCompatibilityV115(initial);if(initial)legacyRenderPlanV115(activeAIPlan);renderMultiMissionUIV115();console.info('[Missions]',{event:'v11_5_ready',active:missionRepository.getActive().length,selected:st.selectedMissionId,primary:st.primaryMissionId})},{once:true});
document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>{
  document.getElementById('secondaryMissionCard')?.remove();
},180),{once:true});

/* v13.3.15 Plan Duration & Protection Restore Runtime */
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
