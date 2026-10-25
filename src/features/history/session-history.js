/**
 * Physical module: src/features/history/session-history.js
 */
function countedFocus(s){if(Number(s.actualMinutes)<1)return false;if(s.result?.countAsFocus===false)return false;if(s.result?.countAsFocus===true)return true;return s.status==='completed'}
function formatHistoryDuration(minutes){const m=Math.max(0,Math.round(Number(minutes)||0));if(m===0)return'< 1 Min.';return m<60?m+' Min.':Math.floor(m/60)+'h '+(m%60?m%60+'m':'')}
function formatHistoryRange(s){const a=new Date(s.startedAt),b=new Date(s.finishedAt);if(Number.isNaN(a.getTime())||Number.isNaN(b.getTime()))return'Zeit unbekannt';const f=d=>d.toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit'});return f(a)+'–'+f(b)}
function startOfHistoryWeek(){const d=new Date();d.setHours(0,0,0,0);d.setDate(d.getDate()-((d.getDay()+6)%7));return d.getTime()}
let activeHistoryFilter='today';
function setHistoryFilter(filter,button){activeHistoryFilter=['today','week','all'].includes(filter)?filter:'today';document.querySelectorAll('.history-filter').forEach(b=>b.classList.toggle('active',b===button||b.dataset.filter===activeHistoryFilter));renderSessionHistory(activeHistoryFilter)}
function historyStatusView(s){if(s.status==='cancelled')return['Abgebrochen','bg-rose-50 text-rose-600'];if(s.status==='interrupted')return['Unterbrochen','bg-amber-50 text-amber-700'];if(s.result?.outcome==='partial')return['Teilweise abgeschlossen','bg-amber-50 text-amber-700'];if(s.result?.outcome==='aborted')return['Nicht abgeschlossen','bg-rose-50 text-rose-600'];return['Abgeschlossen','bg-emerald-50 text-emerald-700']}
function createHistoryCard(s){
 const card=document.createElement('article');card.className='history-card '+s.status;
 const top=document.createElement('div');top.className='flex items-start justify-between gap-3';
 const left=document.createElement('div');left.className='min-w-0';
 const range=document.createElement('div');range.className='text-[10px] font-bold text-slate-500';range.textContent=formatHistoryRange(s);
 const type=document.createElement('div');type.className='history-type mt-2 '+(s.type==='mission'?'text-violet-600':'text-indigo-600');type.textContent=s.type==='mission'?'Focus Mission':'Quick Focus';
 const title=document.createElement('div');title.className='history-title';title.textContent=s.title;left.append(range,type,title);
 if(s.type==='mission'){const meta=document.createElement('div');meta.className='history-meta';meta.textContent=(s.ui?.modeLabel||'Mission')+' · Schritt '+(s.ui?.progressCurrent||1);left.append(meta)}
 const duration=document.createElement('div');duration.className='shrink-0 text-[12px] font-black text-slate-800';duration.textContent=formatHistoryDuration(s.actualMinutes);top.append(left,duration);
 const view=historyStatusView(s),status=document.createElement('span');status.className='history-status '+view[1];status.textContent=view[0];card.append(top,status);return card;
}
function createHistoryEmpty(filter,allCount){
 const wrap=document.createElement('div');wrap.className='history-empty';
 const icon=document.createElement('div');icon.className='history-empty-icon';icon.innerHTML='<i data-lucide="clock-3"></i>';
 const title=document.createElement('div');title.className='text-[15px] font-black text-slate-900';
 const copy=document.createElement('div');copy.className='text-[11px] leading-relaxed mt-2 max-w-[240px]';
 if(allCount===0){title.textContent='Dein Sitzungsverlauf ist noch leer.';copy.textContent='Deine Fokus-Sitzungen erscheinen hier.'}
 else if(filter==='today'){title.textContent='Heute noch keine Sitzung';copy.textContent='Starte einen Quick Focus oder setze deine Mission fort.'}
 else if(filter==='week'){title.textContent='Diese Woche noch keine Sitzung';copy.textContent='Deine nächste abgeschlossene Sitzung erscheint hier.'}
 else{title.textContent='Keine Sitzung gefunden';copy.textContent='Für diesen Filter gibt es noch keine Einträge.'}
 const action=document.createElement('button');action.type='button';action.className='history-empty-action';action.textContent=allCount===0?'Erste Sitzung starten':'Fokus starten';action.onclick=()=>{setAppScreen('home',{immediate:true});setTimeout(openFocusChoice,80)};
 wrap.append(icon,title,copy,action);return wrap;
}
function renderSessionHistory(filter=activeHistoryFilter){
 const all=sessionRepository.getAll(),now=new Date();now.setHours(0,0,0,0);const start=filter==='today'?now.getTime():filter==='week'?startOfHistoryWeek():0,items=all.filter(x=>(Number(x.finishedAt)||0)>=start),list=document.getElementById('sessionHistoryList');if(!list)return;
 list.replaceChildren();const label=document.getElementById('historySectionLabel');if(label)label.textContent=filter==='today'?'Heute':filter==='week'?'Diese Woche':'Alle Sitzungen';
 const counted=items.filter(countedFocus),minutes=counted.reduce((n,x)=>n+Number(x.actualMinutes||0),0);document.getElementById('historySummary').textContent=counted.length+' · '+formatHistoryDuration(minutes);
 if(!items.length)list.append(createHistoryEmpty(filter,all.length));else items.forEach(x=>list.append(createHistoryCard(x)));
 lucide.createIcons();historyLog('history_filtered',{filter,count:items.length});
}
function refreshSessionHistoryUI(){if(!document.getElementById('app-screen-history')?.classList.contains('hidden'))renderSessionHistory(activeHistoryFilter)}
function refreshHomeStats(){
 const today=sessionRepository.getToday().filter(countedFocus),week=sessionRepository.getThisWeek().filter(countedFocus),todayM=today.reduce((n,x)=>n+Number(x.actualMinutes||0),0),weekM=week.reduce((n,x)=>n+Number(x.actualMinutes||0),0);
 const td=document.getElementById('homeTodayFocus');if(td)td.innerHTML=escapeHTML(String(todayM))+' <span class="text-[8px] font-semibold text-slate-400">Min.</span>';
 const ws=document.getElementById('homeWeekSessions');if(ws)ws.textContent=week.length+' von 4 Sitzungen';
 const wf=document.getElementById('homeWeekFocus');if(wf)wf.textContent=formatHistoryDuration(weekM);
 const bar=document.getElementById('homeWeekProgress');if(bar)bar.style.width=Math.min(100,week.length/4*100)+'%';
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
 document.querySelectorAll('#statsWeekChart [data-history-day]').forEach((day,index)=>{const minutes=days[index]||0,bar=day.querySelector('div:nth-last-child(2)'),label=day.querySelector('span:last-child');let tip=day.querySelector('.focus-tooltip');if(!tip){tip=document.createElement('div');tip.className='focus-tooltip';day.prepend(tip)}tip.textContent=minutes+' Min.';if(bar){bar.style.height=(minutes?Math.max(8,Math.round(minutes/max*100)):2)+'%';bar.className='w-full rounded-t-lg '+(index===todayIndex?'bg-indigo-600':minutes?'bg-indigo-300':'bg-slate-100')}if(label){label.classList.toggle('text-indigo-600',index===todayIndex);label.classList.toggle('font-semibold',index===todayIndex)}});
 historyLog('stats_refreshed',{todayMinutes,totalMinutes,completedSessions:completed.length});
}
const historyApplyScreen=applyAppScreenImmediately;applyAppScreenImmediately=function(screen){const result=historyApplyScreen.apply(this,arguments);if(screen==='history')renderSessionHistory(activeHistoryFilter);if(screen==='home')refreshHomeStats();if(screen==='stats')refreshStatsScreen();return result};
document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>{loadHistoryStore();refreshHomeStats();refreshStatsScreen();renderSessionHistory(activeHistoryFilter)},60));
