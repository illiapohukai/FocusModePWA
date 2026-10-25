const MISSION_THEMES_V116=['violet','aqua'];
function missionViewModelV116(m){
 const step=m?.steps?.find(x=>x.id===m.currentStepId)||null,total=m?.steps?.length||0,done=m?.progress?.completedStepIds?.length||0;
 return{id:m.id,title:m.title,subject:m.subject||'Allgemein',deadlineText:m.deadlineText||'ohne Frist',status:m.status,currentStepTitle:step?.title||'Mission abgeschlossen',currentStepMinutes:step?.durationMinutes||0,completedSteps:done,totalSteps:total,progressPercent:total?Math.min(100,Math.round(done/total*100)):0,isPrimary:m.id===missionRepository.getPrimaryId(),theme:MISSION_THEMES_V116[Math.abs([...m.id].reduce((a,c)=>a+c.charCodeAt(0),0))%MISSION_THEMES_V116.length]};
}
function sortedActiveMissionsV116(){const active=missionRepository.getActive();const primary=missionRepository.getPrimaryId();return active.sort((a,b)=>a.id===primary?-1:b.id===primary?1:a.createdAt-b.createdAt)}
function renderMissionHeroV116(vm){return '<button class="mission-hero-v116 theme-'+vm.theme+'" type="button" aria-label="Mission '+escapeHTML(vm.title)+' öffnen" onclick="openMission(\''+vm.id+'\')"><span class="mh-kicker"><i></i>'+(vm.isPrimary?'Hauptmission':'Focus Mission')+'</span><span class="mh-title">'+escapeHTML(vm.title)+'</span><span class="mh-meta">'+escapeHTML(vm.subject)+' · '+escapeHTML(vm.deadlineText)+'</span><span class="mh-step"><span class="mh-label">Nächster Schritt</span><span class="mh-step-title">'+escapeHTML(vm.currentStepTitle)+'</span>'+(vm.currentStepMinutes?'<span class="mh-duration">'+vm.currentStepMinutes+' Min.</span>':'')+'</span><span class="mh-progress"><span class="mh-progress-row"><span>Mission-Fortschritt</span><span>'+vm.progressPercent+'%</span></span><span class="mh-track"><i style="width:'+vm.progressPercent+'%"></i></span></span></button>'}
function renderHomeCarouselV116(){
 const active=sortedActiveMissionsV116(),old=document.getElementById('missionCarouselV116'),card=document.getElementById('activeMissionCard'),empty=document.getElementById('emptyMissionCard');
 if(active.length<2){old?.remove();renderMultiMissionUIV115();return}
 empty?.classList.add('hidden');card?.classList.add('hidden');let host=old;if(!host){host=document.createElement('div');host.id='missionCarouselV116';card.insertAdjacentElement('afterend',host)}host.innerHTML=active.map(m=>renderMissionHeroV116(missionViewModelV116(m))).join('');host.scrollLeft=0;
}
function focusMissionListV116(){return sortedActiveMissionsV116().map(m=>{const v=missionViewModelV116(m);return '<button class="focus-mission-v116" onclick="openMissionFromFocusV116(\''+m.id+'\')"><span class="fm-icon"><i data-lucide="target"></i></span><span class="fm-copy"><span class="fm-title">'+escapeHTML(v.title)+'</span><span class="fm-meta">'+escapeHTML(v.currentStepTitle)+' · '+v.currentStepMinutes+' Min.</span></span><i class="w-4 h-4 text-slate-300" data-lucide="chevron-right"></i></button>'}).join('')}
function renderFocusChoiceV116(){
 const sheet=document.querySelector('#focusChoiceOverlay .focus-choice-sheet');if(!sheet)return;
 const active=missionRepository.getActive(),full=active.length>=2;
 sheet.innerHTML='<div class="w-10 h-1 bg-slate-200 rounded-full mx-auto mb-4"></div><div class="flex items-start justify-between gap-3"><div><div class="text-[9px] font-black uppercase tracking-[.16em] text-indigo-500">Fokus starten</div><h3 class="text-lg font-black tracking-tight text-slate-950 mt-1">Was möchtest du fokussieren?</h3></div><button class="w-8 h-8 rounded-full bg-slate-100 text-slate-500 flex items-center justify-center" onclick="closeFocusChoice()"><i class="w-4 h-4" data-lucide="x"></i></button></div><div class="focus-section-v116"><div class="focus-section-label-v116">Quick Focus</div><button class="focus-mission-v116" onclick="openQuickSetupFromChoice()"><span class="fm-icon"><i data-lucide="timer"></i></span><span class="fm-copy"><span class="fm-title">Kurzen Fokus starten</span><span class="fm-meta">Aufgabe und Dauer selbst festlegen</span></span><i class="w-4 h-4 text-slate-300" data-lucide="chevron-right"></i></button></div><div class="focus-section-v116"><div class="focus-section-label-v116">Deine Focus Missions</div>'+focusMissionListV116()+'<button class="focus-new-v116" onclick="newMissionFromFocusV116()">'+(full?'Zwei aktive Missionen erreicht':'+ Neue Focus Mission')+'</button><button class="focus-manage-v116" onclick="openMissionManagementV116()">Missionen verwalten</button></div>';
 lucide.createIcons();
}
function openMissionFromFocusV116(id){closeFocusChoice();setTimeout(()=>openMission(id),180)}
function newMissionFromFocusV116(){if(missionRepository.getActive().length>=2){closeFocusChoice();setTimeout(()=>{openMissionManagementV116();showMissionLimitV116()},180);return}closeFocusChoice();setTimeout(()=>openGoalIntake(),180)}
const openFocusChoicePreV116=openFocusChoice;openFocusChoice=function(){if(activeSession||sessionRecoveryCandidate||getRecoverableSessionV1151()){routeExistingSessionV1151();return}renderFocusChoiceV116();return openFocusChoicePreV116.apply(this,arguments)};
const applyScreenBeforeV116=applyAppScreenImmediately;applyAppScreenImmediately=function(screen){const r=applyScreenBeforeV116.apply(this,arguments);if(screen==='home')setTimeout(renderHomeCarouselV116,0);if(screen==='missions')setTimeout(renderMissionManagementV116,0);return r};
document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>{ensureMissionManagementScreenV116();refreshMissionUIV116();console.info('[MissionsUX]',{event:'v11_6_ready',active:missionRepository.getActive().length})},260),{once:true});

/* v11.6.1 Mission Card Paint Fix */
(function(){
  function syncMissionCarouselPaintV1161(){
    const active=(typeof missionRepository!=='undefined'&&missionRepository.getActive)?missionRepository.getActive():[];
    const multi=active.length>1;
    document.body.classList.toggle('mission-carousel-active',multi);
    if(multi){
      const card=document.getElementById('activeMissionCard');
      const empty=document.getElementById('emptyMissionCard');
      if(card)card.classList.add('hidden');
      if(empty)empty.classList.add('hidden');
    }
  }
  document.addEventListener('DOMContentLoaded',()=>setTimeout(syncMissionCarouselPaintV1161,280),{once:true});
  const oldRender=window.renderHomeCarouselV116;
  if(typeof oldRender==='function'){
    window.renderHomeCarouselV116=function(){
      syncMissionCarouselPaintV1161();
      const result=oldRender.apply(this,arguments);
      syncMissionCarouselPaintV1161();
      return result;
    };
  }
  const oldOpen=window.openMission;
  if(typeof oldOpen==='function'){
    window.openMission=function(id){
      syncMissionCarouselPaintV1161();
      /* Do not animate a stale Home frame while the plan is being prepared. */
      const result=oldOpen.call(this,id);
      syncMissionCarouselPaintV1161();
      return result;
    };
  }
})();

/* v11.7 Adaptive Mission Cards */
(function(){
  const SIZES=[30,28,26,24,22];
  let queued=false;
  function lineCount(el){
    const cs=getComputedStyle(el), lh=parseFloat(cs.lineHeight)||parseFloat(cs.fontSize)*1.04;
    return Math.max(1,Math.round(el.getBoundingClientRect().height/lh));
  }
  function fitTitle(el){
    if(!el||!el.isConnected)return;
    const text=(el.textContent||'').trim();
    el.classList.remove('mh-title-short','mh-title-medium','mh-title-long');
    const mode=text.length<=30?'short':text.length<=62?'medium':'long';
    el.classList.add('mh-title-'+mode);
    el.style.setProperty('--mh-title-lines','3');
    let chosen=22;
    for(const size of SIZES){
      el.style.setProperty('--mh-title-size',size+'px');
      const cs=getComputedStyle(el), lh=parseFloat(cs.lineHeight)||size*1.04;
      if(el.scrollHeight<=lh*3+2){chosen=size;break;}
    }
    el.style.setProperty('--mh-title-size',chosen+'px');
    el.dataset.titleLines=String(lineCount(el));
  }
  function fitAll(){queued=false;document.querySelectorAll('.mission-hero-v116 .mh-title').forEach(fitTitle)}
  function schedule(){if(queued)return;queued=true;requestAnimationFrame(()=>requestAnimationFrame(fitAll))}
  const observer=new MutationObserver(schedule);
  document.addEventListener('DOMContentLoaded',()=>{
    observer.observe(document.body,{subtree:true,childList:true,characterData:true});
    if(document.fonts?.ready)document.fonts.ready.then(schedule);else schedule();
  },{once:true});
  addEventListener('resize',schedule,{passive:true});
  window.fitMissionCardTitlesV117=fitAll;
})();

/* v11.8.1 Mission Aura Blend */
(function(){
 const colors={violet:[99,102,241],aqua:[20,184,166],create:[124,58,237]};let raf=0;
 function createSlide(){return '<button class="mission-create-v1181" data-aura="create" type="button" onclick="openGoalIntake()"><span class="mc-icon"><i data-lucide="plus"></i></span><span class="mc-kicker">Neue Focus Mission</span><span class="mc-title">Weitere Mission erstellen</span><span class="mc-copy">Plane eine weitere wichtige Aufgabe und verwandle sie in klare Fokus-Schritte.</span></button>'}
 function mix(a,b,t){return a.map((v,i)=>Math.round(v+(b[i]-v)*t))}
 function paint(host){raf=0;const shell=host?.parentElement;if(!host||!shell)return;const slides=[...host.children];if(!slides.length)return;const center=host.scrollLeft+host.clientWidth/2;const ranked=slides.map(el=>({el,d:Math.abs(el.offsetLeft+el.offsetWidth/2-center)})).sort((a,b)=>a.d-b.d);const a=ranked[0],b=ranked[1]||a,span=Math.max(1,Math.abs((b.el.offsetLeft+b.el.offsetWidth/2)-(a.el.offsetLeft+a.el.offsetWidth/2))),t=Math.min(1,a.d/span),rgb=mix(colors[a.el.dataset.aura]||colors.violet,colors[b.el.dataset.aura]||colors.violet,t).join(',');shell.style.setProperty('--aura',rgb);shell.style.setProperty('--aura-shift',Math.max(-20,Math.min(20,(center-(a.el.offsetLeft+a.el.offsetWidth/2))*.13))+'px');document.querySelector('#app-screen-home .home-scroll-content')?.style.setProperty('--home-aura',rgb)}
 window.renderHomeCarouselV116=function(){const active=sortedActiveMissionsV116();document.getElementById('missionAuraShellV1181')?.remove();const card=document.getElementById('activeMissionCard'),empty=document.getElementById('emptyMissionCard');if(!active.length){document.body.classList.remove('mission-aura-active');renderMultiMissionUIV115();return}document.body.classList.add('mission-aura-active');card?.classList.add('hidden');empty?.classList.add('hidden');const shell=document.createElement('div');shell.id='missionAuraShellV1181';const host=document.createElement('div');host.id='missionCarouselV1181';host.innerHTML=active.map(m=>{const vm=missionViewModelV116(m);return renderMissionHeroV116(vm).replace('class="mission-hero-v116','data-aura="'+vm.theme+'" class="mission-hero-v116')}).join('')+(active.length===1?createSlide():'');shell.append(host);card.insertAdjacentElement('afterend',shell);host.addEventListener('scroll',()=>{if(!raf)raf=requestAnimationFrame(()=>paint(host))},{passive:true});requestAnimationFrame(()=>paint(host));lucide.createIcons();window.fitMissionCardTitlesV117?.()};
 const oldApply=window.applyAppScreenImmediately;window.applyAppScreenImmediately=function(screen){const r=oldApply.apply(this,arguments);if(screen==='home')setTimeout(renderHomeCarouselV116,0);return r};
 const oldRefresh=window.refreshMissionUIV116;if(typeof oldRefresh==='function')window.refreshMissionUIV116=function(){const r=oldRefresh.apply(this,arguments);renderHomeCarouselV116();return r};
 document.addEventListener('DOMContentLoaded',()=>setTimeout(renderHomeCarouselV116,360),{once:true});
})();

/* v13.2.2 Mission Flow Controller */
(function(g){'use strict';const A=g.FocusModeApp,M=A?.repositories?.missions,S=A?.repositories?.sessions;if(!A?.core?.registerModule||!M||!S)throw new Error('Mission and Session repositories required');if(A.core.hasModule('features.missionFlow'))return;const d=Object.freeze({confirm:g.confirmAIPlan,open:g.openMission,startStep:g.prepareLearningSession,adapt:g.showPlanUpdate,finish:g.finishLearningFlow,renderPlan:g.renderAIPlan,renderHome:g.renderHomeCarouselV116,currentStep:g.getCurrentMissionSession,missionId:g.ensureActiveMissionId,createSession:g.createMissionSession});const need=Object.keys(d),missing=need.filter(k=>typeof d[k]!=='function');if(missing.length)throw new Error('Mission Flow delegates unavailable: '+missing.join(','));const wrap=fn=>function(){return fn.apply(this,arguments)};const confirm=wrap(d.confirm),open=wrap(d.open),startStep=wrap(d.startStep),adapt=async function(){return await d.adapt.apply(this,arguments)},finish=wrap(d.finish),renderPlan=wrap(d.renderPlan),renderHome=wrap(d.renderHome),currentStep=wrap(d.currentStep),missionId=wrap(d.missionId),createSession=wrap(d.createSession);const C=Object.freeze({version:'13.2.2',selected:()=>M.getSelected?.()||M.getPrimary?.()||null,confirm,open,startStep,adapt,finish,currentStep,missionId,createSession,render:Object.freeze({plan:renderPlan,home:renderHome})});A.features.missionFlow=A.core.registerModule('features.missionFlow',C,{version:'13.2.2',status:'authoritative-controller'});Object.assign(g,{confirmAIPlan:confirm,openMission:open,prepareLearningSession:startStep,showPlanUpdate:adapt,finishLearningFlow:finish,renderAIPlan:renderPlan,renderHomeCarouselV116:renderHome,getCurrentMissionSession:currentStep,ensureActiveMissionId:missionId,createMissionSession:createSession});A.compatibility.missionFlowConsolidation=Object.freeze({version:'13.2.2',storageKeysRenamed:0,formatsChanged:0,uiChanged:false});})(window);
