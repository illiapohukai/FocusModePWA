/**
 * Physical module: src/features/missions/multi-mission-ui.js
 */

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
