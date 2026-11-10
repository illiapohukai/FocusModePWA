/**
 * Physical module: src/features/history/session-history.js
 */
function countedFocus(s){if(Number(s.actualMinutes)<1)return false;if(s.result?.countAsFocus===false)return false;if(s.result?.countAsFocus===true)return true;return s.status==='completed'}
function formatHistoryDuration(minutes){const m=Math.max(0,Math.round(Number(minutes)||0));if(m===0)return'0 Min.';return m<60?m+' Min.':Math.floor(m/60)+'h '+(m%60?m%60+'m':'')}
function formatHistoryRange(s){const a=new Date(s.startedAt),b=new Date(s.finishedAt);if(Number.isNaN(a.getTime())||Number.isNaN(b.getTime()))return'Zeit unbekannt';const f=d=>d.toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit'});return f(a)+'–'+f(b)}
function formatStartTimeHHMM(s){const d=new Date(s.startedAt||s.finishedAt);if(Number.isNaN(d.getTime()))return'--:--';return d.toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit'})}
function startOfHistoryWeek(){const d=new Date();d.setHours(0,0,0,0);d.setDate(d.getDate()-((d.getDay()+6)%7));return d.getTime()}
let activeHistoryFilter='today';
function setHistoryFilter(filter,button){activeHistoryFilter=['today','week','all'].includes(filter)?filter:'today';document.querySelectorAll('.history-filter').forEach(b=>b.classList.toggle('active',b===button||b.dataset.filter===activeHistoryFilter));renderSessionHistory(activeHistoryFilter)}
function classifySessionOutcomeV173(s){
 if(s.status==='cancelled')return{key:'aborted',label:'Abgebrochen',shortLabel:'Abgebrochen',badgeClass:'status-aborted',icon:'x'};
 if(s.status==='interrupted')return{key:'aborted',label:'Unterbrochen',shortLabel:'Unterbrochen',badgeClass:'status-aborted',icon:'pause'};
 if(s.result?.outcome==='partial')return{key:'partial',label:'Teilweise abgeschlossen',shortLabel:'Teilweise',badgeClass:'status-partial',icon:'pie-chart'};
 if(s.result?.outcome==='aborted')return{key:'aborted',label:'Nicht abgeschlossen',shortLabel:'Abgebrochen',badgeClass:'status-aborted',icon:'x'};
 return{key:'completed',label:'Abgeschlossen',shortLabel:'Abgeschlossen',badgeClass:'status-completed',icon:'check'};
}
function historyStatusView(s){const info=classifySessionOutcomeV173(s);return[info.label,info.badgeClass]}
function createHistoryCard(s){
 const info=classifySessionOutcomeV173(s);
 const card=document.createElement('article');card.className='history-card '+s.status+' outcome-'+info.key;
 const top=document.createElement('div');top.className='flex items-start justify-between gap-3';
 const left=document.createElement('div');left.className='min-w-0 flex-1';
 const metaTop=document.createElement('div');metaTop.className='flex items-center gap-2 flex-wrap';
 const range=document.createElement('span');range.className='text-[10px] font-bold text-[#766E65] tabular-nums';range.textContent=formatHistoryRange(s);
 const type=document.createElement('span');type.className='history-type '+(s.type==='mission'?'type-mission':'type-quick');type.textContent=s.type==='mission'?'Focus Mission':'Quick Focus';
 metaTop.append(range,type);
 const title=document.createElement('div');title.className='history-title mt-1.5';title.textContent=s.title;
 left.append(metaTop,title);
 if(s.type==='mission'){
   const meta=document.createElement('div');meta.className='history-meta';
   meta.textContent=(s.ui?.modeLabel||'Mission')+' · Schritt '+String(s.ui?.progressCurrent||1).padStart(2,'0');
   left.append(meta);
 }
 const rightCol=document.createElement('div');rightCol.className='shrink-0 text-right flex flex-col items-end gap-1.5';
 const duration=document.createElement('div');duration.className='text-[13px] font-black text-[#1D1A17] tabular-nums';
 const mins=Math.max(0,Math.round(Number(s.actualMinutes)||0));
 duration.textContent=mins===0?'< 1 Min.':formatHistoryDuration(mins);
 const status=document.createElement('span');status.className='history-status '+info.badgeClass;
 status.innerHTML='<i data-lucide="'+info.icon+'" class="w-2.5 h-2.5"></i><span>'+escapeHTML(info.shortLabel)+'</span>';
 rightCol.append(duration,status);
 top.append(left,rightCol);
 card.append(top);
 return card;
}
function createHistoryEmpty(filter,allCount){
 const wrap=document.createElement('div');wrap.className='history-empty';
 const icon=document.createElement('div');icon.className='history-empty-icon';icon.innerHTML='<i data-lucide="clock-3"></i>';
 const title=document.createElement('div');title.className='text-[15px] font-black text-[#1D1A17]';
 const copy=document.createElement('div');copy.className='text-[11px] text-[#766E65] leading-relaxed mt-1.5 max-w-[240px]';
 if(allCount===0){title.textContent='Dein Sitzungsverlauf ist noch leer.';copy.textContent='Deine Fokus-Sitzungen erscheinen hier.'}
 else if(filter==='today'){title.textContent='Heute noch keine Sitzung';copy.textContent='Starte einen Quick Focus oder setze deine Mission fort.'}
 else if(filter==='week'){title.textContent='Diese Woche noch keine Sitzung';copy.textContent='Deine nächste abgeschlossene Sitzung erscheint hier.'}
 else{title.textContent='Keine Sitzung gefunden';copy.textContent='Für diesen Filter gibt es noch keine Einträge.'}
 const action=document.createElement('button');action.type='button';action.className='history-empty-action';action.textContent=allCount===0?'Erste Sitzung starten':'Fokus starten';action.onclick=()=>{if(typeof navigateToRoot==='function')navigateToRoot('home');else setAppScreen('home',{immediate:true});setTimeout(openFocusChoice,80)};
 wrap.append(icon,title,copy,action);return wrap;
}
function renderSessionHistory(filter=activeHistoryFilter){
 const all=sessionRepository.getAll(),now=new Date();now.setHours(0,0,0,0);
 const start=filter==='today'?now.getTime():filter==='week'?startOfHistoryWeek():0;
 const items=all.filter(x=>(Number(x.finishedAt)||0)>=start);
 const list=document.getElementById('sessionHistoryList');if(!list)return;
 list.replaceChildren();
 const label=document.getElementById('historySectionLabel');
 if(label)label.textContent=filter==='today'?'Heute':filter==='week'?'Diese Woche':'Alle Sitzungen';
 const counted=items.filter(countedFocus);
 const minutes=counted.reduce((n,x)=>n+Number(x.actualMinutes||0),0);
 const summaryEl=document.getElementById('historySummary');
 if(summaryEl)summaryEl.textContent=counted.length+' · '+formatHistoryDuration(minutes);

 // Update dark overview block in History
 const weekCounted=sessionRepository.getThisWeek().filter(countedFocus);
 const weekMinutes=weekCounted.reduce((n,x)=>n+Number(x.actualMinutes||0),0);
 const totalFocusEl=document.getElementById('historyTotalFocusTime');
 const weekSubEl=document.getElementById('historyWeekSummaryText');
 const totalCountEl=document.getElementById('historyTotalSessionsCount');
 const compCountEl=document.getElementById('historyCompletedCount');
 const partCountEl=document.getElementById('historyPartialCount');
 const abortCountEl=document.getElementById('historyAbortedCount');
 if(totalFocusEl)totalFocusEl.textContent=formatHistoryDuration(minutes);
 if(weekSubEl)weekSubEl.textContent='Diese Woche: '+formatHistoryDuration(weekMinutes);
 let cDone=0,cPart=0,cAbort=0;
 items.forEach(s=>{
   const k=classifySessionOutcomeV173(s).key;
   if(k==='completed')cDone++;
   else if(k==='partial')cPart++;
   else cAbort++;
 });
 if(totalCountEl)totalCountEl.textContent=String(items.length);
 if(compCountEl)compCountEl.textContent=String(cDone);
 if(partCountEl)partCountEl.textContent=String(cPart);
 if(abortCountEl)abortCountEl.textContent=String(cAbort);

 if(!items.length)list.append(createHistoryEmpty(filter,all.length));
 else items.forEach(x=>list.append(createHistoryCard(x)));
 lucide.createIcons();
 historyLog('history_filtered',{filter,count:items.length});
}
function refreshSessionHistoryUI(){if(!document.getElementById('app-screen-history')?.classList.contains('hidden'))renderSessionHistory(activeHistoryFilter)}
function renderHomeActivityTimelineV173(){
 const host=document.getElementById('homeActivityList');
 if(!host)return;
 const all=sessionRepository.getAll();
 host.replaceChildren();
 if(!all.length){
   const empty=document.createElement('div');
   empty.className='home-activity-empty';
   empty.innerHTML='<div class="home-activity-empty-icon"><i data-lucide="clock-3" class="w-4 h-4"></i></div>'
     +'<div class="min-w-0">'
       +'<div class="text-[12px] font-bold text-[#1D1A17]">Noch keine Aktivität</div>'
       +'<div class="text-[10px] text-[#766E65] leading-relaxed mt-0.5">Abgeschlossene und teilweise Fokus-Sitzungen erscheinen hier chronologisch.</div>'
     +'</div>';
   host.append(empty);
   return;
 }
 const recent=all.slice(0,6);
 recent.forEach((s,idx)=>{
   const info=classifySessionOutcomeV173(s);
   const row=document.createElement('div');
   row.className='home-activity-row';
   const timeStr=formatStartTimeHHMM(s);
   const mins=Math.max(1,Math.round(Number(s.actualMinutes||s.plannedMinutes)||25));
   const modePart=s.type==='mission'?('Mission Schritt '+String(s.ui?.progressCurrent||1).padStart(2,'0')):'Quick Focus';
   const statusPart=info.key==='completed'?'':(' · '+info.shortLabel);
   const subText=modePart+' · '+mins+' Min.'+statusPart;
   const isLast=idx===recent.length-1;
   row.innerHTML='<div class="home-activity-time"><span>'+escapeHTML(timeStr)+'</span></div>'
     +'<div class="home-activity-rail">'
       +'<span class="home-activity-dot '+info.badgeClass+'"><i data-lucide="'+info.icon+'" class="w-2.5 h-2.5"></i></span>'
       +(!isLast?'<span class="home-activity-line"></span>':'')
     +'</div>'
     +'<div class="home-activity-card">'
       +'<div class="home-activity-title">'+escapeHTML(s.title||'Fokus-Sitzung')+'</div>'
       +'<div class="home-activity-sub">'+escapeHTML(subText)+'</div>'
     +'</div>';
   host.append(row);
 });
}
function updateHomeDateHeaderV173(){
 const dateEl=document.getElementById('homeCurrentDate');
 if(!dateEl)return;
 const lang=(window.FocusModeApp?.services?.storage?.getText('focus-mode-language','de')||document.documentElement.lang||'de')==='en'?'en-US':'de-DE';
 try{
   const formatted=new Intl.DateTimeFormat(lang,{weekday:'long',day:'numeric',month:'long'}).format(new Date());
   dateEl.textContent=formatted;
 }catch{
   dateEl.textContent='';
 }
}
function refreshHomeStats(){
 updateHomeDateHeaderV173();
 const today=sessionRepository.getToday().filter(countedFocus);
 const week=sessionRepository.getThisWeek().filter(countedFocus);
 const todayM=today.reduce((n,x)=>n+Number(x.actualMinutes||0),0);
 const weekM=week.reduce((n,x)=>n+Number(x.actualMinutes||0),0);
 const td=document.getElementById('homeTodayFocus');
 if(td)td.textContent=todayM+'m';
 const ts=document.getElementById('homeTodaySessions');
 if(ts)ts.textContent=String(today.length);
 const tsl=document.getElementById('homeTodaySessionsLabel');
 if(tsl){
   const isEn=(document.documentElement.lang||'de')==='en';
   tsl.textContent=isEn?(today.length===1?'SESSION':'SESSIONS'):(today.length===1?'SITZUNG':'SITZUNGEN');
 }
 const ws=document.getElementById('homeWeekSessions');if(ws)ws.textContent=week.length+' von 4 Sitzungen';
 const wf=document.getElementById('homeWeekFocus');if(wf)wf.textContent=formatHistoryDuration(weekM);
 const bar=document.getElementById('homeWeekProgress');if(bar)bar.style.width=Math.min(100,week.length/4*100)+'%';
 renderHomeActivityTimelineV173();
 lucide.createIcons();
 historyLog('home_stats_refreshed',{todayMinutes:todayM,weekMinutes:weekM,weekSessions:week.length});
}
function getWeekDailyMinutes(){
 const monday=startOfHistoryWeek(),days=Array(7).fill(0);
 sessionRepository.getThisWeek().filter(countedFocus).forEach(s=>{const d=new Date(s.finishedAt);d.setHours(0,0,0,0);const index=Math.floor((d.getTime()-monday)/86400000);if(index>=0&&index<7)days[index]+=Number(s.actualMinutes)||0});
 return days;
}
function refreshStatsScreen(){
 const completed=sessionRepository.getCompleted().filter(countedFocus),today=sessionRepository.getToday().filter(countedFocus),todayMinutes=today.reduce((n,s)=>n+Number(s.actualMinutes||0),0),totalMinutes=completed.reduce((n,s)=>n+Number(s.actualMinutes||0),0),missionCount=completed.filter(s=>s.type==='mission').length;
 const set=(id,value)=>{const el=document.getElementById(id);if(el)el.textContent=value};
 set('statsTodayFocus',formatHistoryDuration(todayMinutes));set('statsMissionSteps',missionCount+' abgeschlossen');set('statsTotalFocus',formatHistoryDuration(totalMinutes));set('statsCompletedSessions',completed.length+' abgeschlossen');
 const days=getWeekDailyMinutes(),max=Math.max(1,...days),todayIndex=(new Date().getDay()+6)%7;
 document.querySelectorAll('#statsWeekChart [data-history-day]').forEach((day,index)=>{const minutes=days[index]||0,bar=day.querySelector('div:nth-last-child(2)'),label=day.querySelector('span:last-child');let tip=day.querySelector('.focus-tooltip');if(!tip){tip=document.createElement('div');tip.className='focus-tooltip';day.prepend(tip)}tip.textContent=minutes+' Min.';if(bar){bar.style.height=(minutes?Math.max(8,Math.round(minutes/max*100)):2)+'%';bar.className='w-full rounded-t-lg '+(index===todayIndex?'bg-[#6D28D9]':minutes?'bg-purple-300':'bg-stone-200')}if(label){label.classList.toggle('text-[#6D28D9]',index===todayIndex);label.classList.toggle('font-semibold',index===todayIndex)}});
 historyLog('stats_refreshed',{todayMinutes,totalMinutes,completedSessions:completed.length});
}
const historyApplyScreen=applyAppScreenImmediately;applyAppScreenImmediately=function(screen){const result=historyApplyScreen.apply(this,arguments);if(screen==='history')renderSessionHistory(activeHistoryFilter);if(screen==='home')refreshHomeStats();if(screen==='stats')refreshStatsScreen();return result};
document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>{loadHistoryStore();refreshHomeStats();refreshStatsScreen();renderSessionHistory(activeHistoryFilter)},60));
