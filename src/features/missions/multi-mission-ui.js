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
      const result=oldOpen.apply(this,arguments);
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
 const colors={violet:[109,40,217],aqua:[124,58,237],create:[109,40,217]};let raf=0;
 function mix(a,b,t){return a.map((v,i)=>Math.round(v+(b[i]-v)*t))}
 function paint(host){
  raf=0;
  const shell=host?.parentElement;
  if(!host||!shell)return;
  const slides=[...host.children];
  if(!slides.length)return;
  const padLeft=parseFloat(getComputedStyle(host).paddingLeft)||18;
  const snapX=host.scrollLeft+padLeft;
  const isMulti=slides.length>1&&host.classList.contains('multi-mission');
  const reducedMotion=typeof window.matchMedia==='function'&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const ranked=slides.map(el=>({el,d:Math.abs(el.offsetLeft-snapX)})).sort((a,b)=>a.d-b.d);
  const a=ranked[0],b=ranked[1]||a,span=Math.max(1,Math.abs(b.el.offsetLeft-a.el.offsetLeft)),t=Math.min(1,a.d/span),rgb=mix(colors[a.el.dataset.aura]||colors.violet,colors[b.el.dataset.aura]||colors.violet,t).join(',');
  shell.style.setProperty('--aura',rgb);
  shell.style.setProperty('--aura-shift',Math.max(-20,Math.min(20,(snapX-a.el.offsetLeft)*.13))+'px');
  document.querySelector('#app-screen-home .home-scroll-content')?.style.setProperty('--home-aura',rgb);
  if(isMulti){
    slides.forEach(el=>{
      const distRatio=Math.max(0,Math.min(1,Math.abs(el.offsetLeft-snapX)/span));
      const isActive=el===a.el&&distRatio<0.5;
      el.classList.toggle('is-carousel-active',isActive);
      el.classList.toggle('is-carousel-inactive',!isActive);
      if(reducedMotion){
        el.style.transform='none';
        el.style.opacity=isActive?'1':'0.90';
      }else if(distRatio<=0.005){
        el.style.transform='translate3d(0px, 0px, 0px) scale(1)';
        el.style.opacity='1';
      }else{
        const scale=+(1-0.028*distRatio).toFixed(4);
        const ty=+(4.5*distRatio).toFixed(2);
        const op=+(1-0.10*distRatio).toFixed(3);
        el.style.transform='translate3d(0px, '+ty+'px, 0px) scale('+scale+')';
        el.style.opacity=String(op);
      }
    });
  }else{
    slides.forEach(el=>{
      el.classList.remove('is-carousel-active','is-carousel-inactive');
      el.style.transform='';
      el.style.opacity='';
    });
  }
 }
 window.renderHomeCarouselV116=function(){
   const active=sortedActiveMissionsV116();
   document.getElementById('missionAuraShellV1181')?.remove();
   const card=document.getElementById('activeMissionCard'),empty=document.getElementById('emptyMissionCard');
   if(!active.length){
     document.body.classList.remove('mission-aura-active','mission-carousel-active');
     renderMultiMissionUIV115();
     return;
   }
   document.body.classList.add('mission-aura-active');
   document.body.classList.toggle('mission-carousel-active',active.length>1);
   card?.classList.add('hidden');
   empty?.classList.add('hidden');
   const shell=document.createElement('div');
   shell.id='missionAuraShellV1181';
   shell.className=active.length===1?'single-mission-shell':'multi-mission-shell';
   shell.setAttribute('data-block-swipe-back','true');
   const host=document.createElement('div');
   host.id='missionCarouselV1181';
   host.className=active.length===1?'single-mission':'multi-mission';
   host.setAttribute('data-block-swipe-back','true');
   host.innerHTML=active.map(m=>{
     const vm=missionViewModelV116(m);
     return renderMissionHeroV116(vm).replace('class="mission-hero-v116','data-aura="'+vm.theme+'" class="mission-hero-v116');
   }).join('');
   shell.append(host);
   card.insertAdjacentElement('afterend',shell);
   if(active.length>1){
     host.addEventListener('scroll',()=>{if(!raf)raf=requestAnimationFrame(()=>paint(host))},{passive:true});
   }
   paint(host);
   requestAnimationFrame(()=>paint(host));
   lucide.createIcons();
   window.fitMissionCardTitlesV117?.();
 };
 const oldApply=window.applyAppScreenImmediately;window.applyAppScreenImmediately=function(screen){const r=oldApply.apply(this,arguments);if(screen==='home')setTimeout(renderHomeCarouselV116,0);return r};
 const oldRefresh=window.refreshMissionUIV116;if(typeof oldRefresh==='function')window.refreshMissionUIV116=function(){const r=oldRefresh.apply(this,arguments);renderHomeCarouselV116();return r};
 document.addEventListener('DOMContentLoaded',()=>setTimeout(renderHomeCarouselV116,360),{once:true});
})();
