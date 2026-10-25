let quickDuration=25, quickBreak=5, distractionReturnScreen='quicksetup';
function openQuickSetupFromChoice(){closeFocusChoice();setTimeout(()=>setAppScreen('quicksetup'),180)}
function selectQuickDuration(btn,value){quickDuration=value;updateQuickRing()}
function updateQuickRing(){const v=document.getElementById('quickMinuteValue'),r=document.getElementById('quickRingProgress');if(v)v.textContent=quickDuration;if(r){const c=2*Math.PI*69,p=Math.max(0,Math.min(1,(quickDuration-5)/55));r.style.strokeDasharray=c;r.style.strokeDashoffset=c*(1-p)}document.querySelectorAll('#quickDurationButtons .quick-option').forEach(b=>b.classList.toggle('active',parseInt(b.textContent,10)===quickDuration))}
function adjustQuickDuration(d){quickDuration=Math.max(5,Math.min(60,quickDuration+d));updateQuickRing()}
function selectQuickBreak(btn,value){quickBreak=value;document.querySelectorAll('#quickBreakButtons .quick-option').forEach(x=>x.classList.remove('active'));btn.classList.add('active')}
function openDistractionPicker(returnScreen){distractionReturnScreen=returnScreen||'quicksetup';setAppScreen('distractions')}
function returnFromDistractionPicker(){setAppScreen(distractionReturnScreen)}
function toggleDistraction(row){row.classList.toggle('selected');lucide.createIcons()}
function getSelectedDistractions(){return [...document.querySelectorAll('#distractionList .distraction-row.selected')].map(x=>x.dataset.name)}
function updateProtectionSummary(){
 const apps=getSelectedDistractions(),count=apps.length;
 document.querySelectorAll('.blockedCount').forEach(x=>x.textContent=count);
 const shown=apps.slice(0,3).join(' · '),rest=Math.max(0,count-3);
 document.querySelectorAll('.blockedSummary').forEach(x=>x.textContent=shown+(rest?' +'+rest:''));
 const s=document.getElementById('quickSelectedAppIcons');if(s){s.innerHTML='';document.querySelectorAll('#distractionList .distraction-row.selected').forEach((r,i)=>{if(i<3){const d=r.querySelector('.app-dot');if(d)s.appendChild(d.cloneNode(true))}});if(window.lucide)lucide.createIcons()}
}
function saveDistractions(){updateProtectionSummary();setAppScreen(distractionReturnScreen)}
function showBlockedAppsPopup(){
 const popup=document.getElementById('blockedAppsPopup'),card=document.getElementById('blockedAppsPopupCard'),box=document.getElementById('blockedAppsChips');
 box.innerHTML=getSelectedDistractions().map(x=>'<span class="px-2.5 py-1.5 rounded-full bg-rose-50 text-rose-600 text-[9px] font-bold">'+x+'</span>').join('');
 popup.classList.remove('hidden');requestAnimationFrame(()=>{popup.classList.remove('opacity-0');card.classList.remove('translate-y-6','opacity-0')});
}
function hideBlockedAppsPopup(){const popup=document.getElementById('blockedAppsPopup'),card=document.getElementById('blockedAppsPopupCard');popup.classList.add('opacity-0');card.classList.add('translate-y-6','opacity-0');setTimeout(()=>popup.classList.add('hidden'),250)}
function startConfiguredQuickFocus(){const task=(document.getElementById('quickTaskInput')?.value||'').trim()||'Konzentriert arbeiten';return startSession(createQuickSession({title:task,task,plannedMinutes:quickDuration}))}
function startQuickFocus(){return startSession(createQuickSession({title:'Freier Fokus',task:'Konzentriert arbeiten',plannedMinutes:25}))}
function startFocusSession(){return startQuickFocus()}

document.addEventListener("DOMContentLoaded",()=>{quickBreak=0;updateQuickRing();updateProtectionSummary()});

(function installQuickFocusSurfaceAndModule(global){
  const previousApply=global.applyAppScreenImmediately;
  if(typeof previousApply==='function'){
    global.applyAppScreenImmediately=function(screenName){
      const result=previousApply.apply(this,arguments);
      document.documentElement.classList.toggle('quick-focus-surface',screenName==='quicksetup');
      return result;
    };
  }
  const App=global.FocusModeApp;
  if(App?.core?.registerModule&&!App.core.hasModule('features.quickFocus')){
    App.features.quickFocus=App.core.registerModule('features.quickFocus',Object.freeze({
      openQuickSetupFromChoice,
      selectQuickDuration,
      adjustQuickDuration,
      updateQuickRing,
      selectQuickBreak,
      startConfiguredQuickFocus,
      startQuickFocus,
      startFocusSession
    }),{virtualPath:'src/features/quick-focus/quick-focus.js',version:'15.4',status:'authoritative'});
  }
})(window);
