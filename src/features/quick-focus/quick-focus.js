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
function selectQuickDuration(btn,value){quickDuration=value;updateQuickRing()}
function updateQuickRing(){
  const v=document.getElementById('quickMinuteValue'),r=document.getElementById('quickRingProgress');
  if(v)v.textContent=quickDuration;
  const homeLbl=document.getElementById('homeQuickDurationLabel');
  if(homeLbl)homeLbl.textContent=quickDuration+' MIN';
  if(r){const c=2*Math.PI*69,p=Math.max(0,Math.min(1,(quickDuration-5)/55));r.style.strokeDasharray=c;r.style.strokeDashoffset=c*(1-p)}
  document.querySelectorAll('#quickDurationButtons .quick-option').forEach(b=>b.classList.toggle('active',parseInt(b.textContent,10)===quickDuration));
}
function adjustQuickDuration(d){quickDuration=Math.max(5,Math.min(60,quickDuration+d));updateQuickRing()}
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

document.addEventListener("DOMContentLoaded",()=>{quickBreak=0;updateQuickRing();updateProtectionSummary()});

(function(){
  const previousApply=window.applyAppScreenImmediately;
  window.applyAppScreenImmediately=function(screenName){
    const result=previousApply.apply(this,arguments);
    document.documentElement.classList.toggle('quick-focus-surface',screenName==='quicksetup');
    if(screenName==='timer')syncTimerProtectionBadge();
    if(screenName==='distractions'){syncDistractionPickerV178(getSelectedDistractions());if(window.lucide)lucide.createIcons()}
    return result;
  };
  document.title='v17.6 Navigation, Insights & League Polish';
})();
