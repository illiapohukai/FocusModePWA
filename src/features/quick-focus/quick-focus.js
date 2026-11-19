/**
 * Physical module: src/features/quick-focus/quick-focus.js
 */

let quickDuration=25, quickBreak=5, distractionReturnScreen='quicksetup';
function openQuickSetupFromChoice(){
  const navState=window.__navigationStateV17;
  const currentScreen=navState?.currentScreen||'home';
  const currentRoot=navState?.currentRoot||'home';
  closeFocusChoice();
  if(typeof navigateForward==='function'){
    navigateForward('quicksetup',{parent:currentScreen,rootSection:currentRoot,reopenFocusChoice:false});
  }else{
    setAppScreen('quicksetup');
  }
}
function openQuickSetupFromHome(){
  if(window.activeSession||window.sessionRecoveryCandidate||(typeof getRecoverableSessionV1151==='function'&&getRecoverableSessionV1151())){
    if(typeof routeExistingSessionV1151==='function'){routeExistingSessionV1151();return;}
  }
  updateQuickRing();
  updateProtectionSummary();
  if(typeof navigateForward==='function'){
    navigateForward('quicksetup',{parent:'home',rootSection:'home',reopenFocusChoice:false});
  }else{
    setAppScreen('quicksetup');
  }
}
window.openQuickSetupFromHome=openQuickSetupFromHome;
/* v17.10: quickDuration stays the single duration owner. v17.16: the circular dial is the only
   visible duration input; it and the hidden range input (keyboard / assistive technology) write
   through setQuickDuration(). Supported range is unchanged: 5–60 minutes in 5-minute steps. */
const QUICK_DURATION_MIN=5,QUICK_DURATION_MAX=60,QUICK_DURATION_STEP=5;
function normalizeQuickDuration(v){const n=Math.round(Number(v)/QUICK_DURATION_STEP)*QUICK_DURATION_STEP;return Number.isFinite(n)?Math.max(QUICK_DURATION_MIN,Math.min(QUICK_DURATION_MAX,n)):quickDuration}
function setQuickDuration(v){const next=normalizeQuickDuration(v);if(next===quickDuration)return false;quickDuration=next;updateQuickRing();return true}
function quickDurationIntensity(v){return v<=15?1:v<=30?2:v<=45?3:4}
function updateQuickRing(){
  const v=document.getElementById('quickMinuteValue'),r=document.getElementById('quickRingProgress'),thumb=document.getElementById('quickDialThumb'),input=document.getElementById('quickDurationInput'),dial=document.getElementById('quickDial');
  const en=document.documentElement.lang==='en';
  /* Static MM:00 before launch; the real countdown only exists once the Session has started. */
  if(v)v.textContent=String(quickDuration).padStart(2,'0')+':00';
  const homeLbl=document.getElementById('homeQuickDurationLabel');
  if(homeLbl)homeLbl.textContent=quickDuration+' MIN';
  /* The arc uses pathLength=60, so one unit is one minute of a full 60-minute turn. */
  if(r)r.style.strokeDashoffset=String(QUICK_DURATION_MAX-quickDuration);
  if(thumb)thumb.style.transform='rotate('+(quickDuration/QUICK_DURATION_MAX*360)+'deg)';
  if(input){input.value=String(quickDuration);input.setAttribute('aria-valuetext',quickDuration+(en?' minutes':' Minuten'))}
  /* Stored Focus energy: one discrete tier for layered detail and one continuous 0–1 value. */
  if(dial){dial.dataset.intensity=String(quickDurationIntensity(quickDuration));dial.style.setProperty('--qb-energy',((quickDuration-QUICK_DURATION_MIN)/(QUICK_DURATION_MAX-QUICK_DURATION_MIN)).toFixed(3))}
}
/* Circular drag: absolute angle on a 60-minute clock face, snapped to 5-minute steps. The 12 o'clock
   seam is never crossed in one move, so a drag past 60 holds at 60 and a drag below 5 holds at 5. */
function quickDialValueFromPoint(face,x,y){
  const b=face.getBoundingClientRect(),cx=b.left+b.width/2,cy=b.top+b.height/2,dx=x-cx,dy=y-cy,R=b.width/2;
  const dist=Math.hypot(dx,dy);
  return{dist:dist/R,minutes:((Math.atan2(dx,-dy)*180/Math.PI+360)%360)/360*QUICK_DURATION_MAX};
}
function quickDialApplyMinutes(raw){
  let m=raw;
  if(quickDuration>=45&&m<15)m=QUICK_DURATION_MAX;
  else if(quickDuration<=15&&m>45)m=QUICK_DURATION_MIN;
  return setQuickDuration(m);
}
function initQuickDurationDial(){
  const dial=document.getElementById('quickDial'),face=document.getElementById('quickDialFace'),input=document.getElementById('quickDurationInput');
  if(!dial||!face||dial.__qbDialBound)return;
  dial.__qbDialBound=true;
  let pointerId=null;
  const end=()=>{if(pointerId===null)return;try{face.releasePointerCapture(pointerId)}catch(_){}pointerId=null;dial.classList.remove('is-dragging')};
  face.addEventListener('pointerdown',e=>{
    if(pointerId!==null||(e.pointerType==='mouse'&&e.button!==0))return;
    const hit=quickDialValueFromPoint(face,e.clientX,e.clientY);
    /* Only the ring band owns the gesture; the centre readout is not a drag handle. */
    if(hit.dist<.52||hit.dist>1.12)return;
    e.preventDefault();
    pointerId=e.pointerId;
    try{face.setPointerCapture(pointerId)}catch(_){}
    dial.classList.add('is-dragging');
    input?.focus({preventScroll:true});
    quickDialApplyMinutes(hit.minutes);
  });
  face.addEventListener('pointermove',e=>{if(e.pointerId!==pointerId)return;e.preventDefault();quickDialApplyMinutes(quickDialValueFromPoint(face,e.clientX,e.clientY).minutes)});
  face.addEventListener('pointerup',e=>{if(e.pointerId===pointerId)end()});
  face.addEventListener('pointercancel',e=>{if(e.pointerId===pointerId)end()});
  face.addEventListener('lostpointercapture',e=>{if(e.pointerId===pointerId)end()});
  input?.addEventListener('input',()=>{dial.classList.remove('is-dragging');setQuickDuration(input.value)});
}
/* v17.16 launch, phase A: freeze the configured values and lock the setup, then start the Session
   once through the existing owner (startConfiguredQuickFocus → startSession). The visual launch is
   owned by MotionController (quicksetup → timer). If the Session does not start, the setup is
   restored unchanged and the existing error stays visible. */
let quickLaunchPending=false;
function setQuickSetupLocked(locked){
  const screen=document.getElementById('app-screen-quicksetup');
  if(!screen)return;
  screen.classList.toggle('is-launching',locked);
  const main=screen.querySelector('.qb-main');
  if(main)main.inert=locked;
  if(locked)document.getElementById('quickDial')?.classList.remove('is-dragging');
}
function launchQuickFocusV1716(){
  if(quickLaunchPending)return false;
  quickLaunchPending=true;
  setQuickSetupLocked(true);
  let started=false;
  try{started=startConfiguredQuickFocus()===true}
  catch(err){console.error('[QuickFocus]',{event:'launch_failed',message:err?.message||'unknown'})}
  finally{quickLaunchPending=false}
  if(!started)setQuickSetupLocked(false);
  return started;
}
window.launchQuickFocusV1716=launchQuickFocusV1716;
function selectQuickBreak(btn,value){quickBreak=value;document.querySelectorAll('#quickBreakButtons .quick-option').forEach(x=>x.classList.remove('active'));btn.classList.add('active')}
function openDistractionPicker(returnScreen){distractionReturnScreen=returnScreen||'quicksetup';setAppScreen('distractions')}
function returnFromDistractionPicker(){setAppScreen(distractionReturnScreen)}
function toggleDistraction(row){const on=row.classList.toggle('selected');row.setAttribute('aria-pressed',on?'true':'false');updateProtectionSummary()}
/* v17.8 picker selection control: select all when any app is unselected, otherwise clear all. */
function toggleAllDistractions(){const rows=[...document.querySelectorAll('#distractionList .distraction-row')];const selectAll=rows.some(r=>!r.classList.contains('selected'));rows.forEach(r=>{r.classList.toggle('selected',selectAll);r.setAttribute('aria-pressed',selectAll?'true':'false')});updateProtectionSummary()}
function isEnglishUIV178(){return document.documentElement.lang==='en'}
/* Live summary on the Fokus-Schutz screen; reflects only the real selection state. */
function syncDistractionPickerV178(apps){
 const en=isEnglishUIV178(),count=apps.length,all=document.querySelectorAll('#distractionList .distraction-row').length;
 const countEl=document.getElementById('distractionSummaryCount'),stateEl=document.getElementById('distractionSummaryState'),icons=document.getElementById('distractionSummaryIcons'),bulk=document.getElementById('distractionBulkToggle');
 if(countEl)countEl.textContent=count===0?(en?'No apps selected':'Keine Apps ausgewählt'):(count+' '+(en?(count===1?'app selected':'apps selected'):(count===1?'App ausgewählt':'Apps ausgewählt')));
 if(stateEl)stateEl.textContent=count===0?(en?'Focus protection is off':'Fokus-Schutz ist aus'):(en?'Focus protection ready':'Fokus-Schutz bereit');
 if(icons){icons.innerHTML='';[...document.querySelectorAll('#distractionList .distraction-row.selected .app-dot')].slice(0,4).forEach(d=>icons.appendChild(d.cloneNode(true)));if(count>4){const more=document.createElement('span');more.className='fs-summary-more';more.textContent='+'+(count-4);icons.appendChild(more)}if(!count){const off=document.createElement('span');off.className='fs-summary-empty';off.innerHTML='<i data-lucide="shield-off"></i>';icons.appendChild(off)}}
 if(bulk)bulk.textContent=count<all?(en?'Select all':'Alle auswählen'):(en?'Deselect all':'Alle abwählen');
}
function getSelectedDistractions(){return [...document.querySelectorAll('#distractionList .distraction-row.selected')].map(x=>x.dataset.name)}
function syncTimerProtectionBadge(){
 const badge=document.querySelector('#app-screen-timer .uc-protect');
 if(!badge)return false;
 const apps=getSelectedDistractions();
 const active=apps.length>0;
 const lang=(window.FocusModeApp?.services?.storage?.getText('focus-mode-language','de')||document.documentElement.lang||'de')==='en'?'en':'de';
 badge.classList.toggle('is-active',active);
 badge.classList.toggle('is-inactive',!active);
 badge.setAttribute('data-protection-active',active?'true':'false');
 const labelText=active?(lang==='en'?'Focus protection active':'Fokusschutz aktiv'):(lang==='en'?'Focus protection off':'Fokusschutz aus');
 const chevronHtml=typeof window.renderFocusIconSvg==='function'?window.renderFocusIconSvg('chevron-right'):'<i data-lucide="chevron-right"></i>';
 badge.innerHTML='<span class="uc-protect-dot" aria-hidden="true"></span>'+labelText+chevronHtml;
 if(window.lucide)lucide.createIcons();
 return active;
}
window.syncTimerProtectionBadge=syncTimerProtectionBadge;
function updateProtectionSummary(){
 const apps=getSelectedDistractions(),count=apps.length;
 document.querySelectorAll('.blockedCount').forEach(x=>x.textContent=count);
 const shown=apps.slice(0,3).join(' · '),rest=Math.max(0,count-3);
 document.querySelectorAll('.blockedSummary').forEach(x=>x.textContent=count>0?(shown+(rest?' +'+rest:'')):((document.documentElement.lang==='en')?'No apps blocked':'Keine Apps blockiert'));
 const s=document.getElementById('quickSelectedAppIcons');if(s){s.innerHTML='';document.querySelectorAll('#distractionList .distraction-row.selected').forEach((r,i)=>{if(i<3){const d=r.querySelector('.app-dot');if(d)s.appendChild(d.cloneNode(true))}})}
 document.querySelectorAll('[data-protection-summary]').forEach(el=>{el.dataset.protection=count>0?'ready':'off';const unit=el.querySelector('.mp-protect-unit');if(unit)unit.textContent=isEnglishUIV178()?(count===1?'distraction selected':'distractions selected'):(count===1?'Ablenkung ausgewählt':'Ablenkungen ausgewählt')});
 syncDistractionPickerV178(apps);
 if(window.lucide)lucide.createIcons();
 syncTimerProtectionBadge();
}
function saveDistractions(){updateProtectionSummary();setAppScreen(distractionReturnScreen)}
function showBlockedAppsPopup(){
 const popup=document.getElementById('blockedAppsPopup'),card=document.getElementById('blockedAppsPopupCard'),box=document.getElementById('blockedAppsChips');
 box.innerHTML=getSelectedDistractions().map(x=>'<span class="px-2.5 py-1.5 rounded-full bg-rose-50 text-rose-600 text-[9px] font-bold">'+x+'</span>').join('');
 popup.classList.remove('hidden');requestAnimationFrame(()=>{popup.classList.remove('opacity-0');card.classList.remove('translate-y-6','opacity-0')});
}
function hideBlockedAppsPopup(){const popup=document.getElementById('blockedAppsPopup'),card=document.getElementById('blockedAppsPopupCard');popup.classList.add('opacity-0');card.classList.add('translate-y-6','opacity-0');setTimeout(()=>popup.classList.add('hidden'),250)}

document.addEventListener("DOMContentLoaded",()=>{
  /* v17.10 strings for the duration dial (I18N is defined by bootstrap.js, loaded later). */
  if(typeof I18N==='object'&&I18N.en)Object.assign(I18N.en,{'Dauer':'Duration','Dauer in Minuten':'Duration in minutes','Fokus-Schutz':'Focus protection','Apps blockiert':'apps blocked'});
  quickBreak=0;initQuickDurationDial();updateQuickRing();updateProtectionSummary()});

(function(){
  const previousApply=window.applyAppScreenImmediately;
  window.applyAppScreenImmediately=function(screenName){
    const result=previousApply.apply(this,arguments);
    document.documentElement.classList.toggle('quick-focus-surface',screenName==='quicksetup');
    /* A setup that was left by a launch is interactive again on its next visit. */
    if(screenName==='quicksetup'&&!quickLaunchPending)setQuickSetupLocked(false);
    if(screenName==='timer')syncTimerProtectionBadge();
    if(screenName==='distractions'){syncDistractionPickerV178(getSelectedDistractions());if(window.lucide)lucide.createIcons()}
    return result;
  };
  document.title='v17.6 Navigation, Insights & League Polish';
})();
