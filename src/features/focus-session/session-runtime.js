function currentUcSession(){return window.activeSession}
function syncFocusClarityCard(){
  const session=window.activeSession;
  const card=document.getElementById('ucFocusCard');
  if(!session||!card)return;
  const title=document.getElementById('ucTaskCardTitle');
  const description=document.getElementById('timerTaskValue');
  const quick=session.type==='quick';
  card.classList.toggle('is-quick',quick);
  if(title)title.textContent=session.title||'Aktuelle Aufgabe';
  if(description)description.textContent=session.task||'Konzentriert weiterarbeiten.';
  card.setAttribute('aria-label',(session.title||'Aktuelle Aufgabe')+' öffnen');
}
function renderSession(s){
  document.getElementById('activeTaskLabel').textContent=s.title;
  document.getElementById('timerTaskValue').textContent=s.task;
  document.getElementById('currentFocusModeLabel').textContent=s.ui.modeLabel;
  if(typeof syncUcData==='function')syncUcData();
  syncFocusClarityCard();
}
function showUcTask(){
  const s=window.activeSession;
  document.getElementById('ucTaskTitle').textContent=s?.title||'Aktuelle Aufgabe';
  document.getElementById('ucTaskText').textContent=s?.task||'Konzentriert weiterarbeiten.';
  document.getElementById('ucTaskPopup').classList.add('open');
}
function checkpointActiveSession(reason='interval',persist=true){
  if(!activeSession||activeSession.status!=='running'||activeSession.lifecycleState!=='running')return null;
  const now=Date.now(),elapsed=sessionElapsedMs(activeSession,true),remaining=Math.max(0,Math.ceil((plannedSessionMs(activeSession)-elapsed)/1000));
  activeSession={...activeSession,elapsedMs:elapsed,remainingSeconds:remaining,lastActiveAt:now,lastCheckpointAt:now};window.activeSession=activeSession;
  if(persist)localStorage.setItem(SESSION_CORE_STORAGE_KEY,JSON.stringify(activeSession));
  timerDuration=remaining;updateTimerUI();
  if(persist)recoveryLog('active_session_checkpointed',{sessionId:activeSession.id,reason,elapsedMinutes:Math.floor(elapsed/60000)});
  return cloneSession(activeSession);
}
function stopCheckpointLoop(){if(sessionCheckpointInterval)clearInterval(sessionCheckpointInterval);sessionCheckpointInterval=null}
function startCheckpointLoop(){stopCheckpointLoop();sessionCheckpointInterval=setInterval(()=>checkpointActiveSession('interval',true),5000)}
function stopSessionRuntime(){if(timerInterval)clearInterval(timerInterval);timerInterval=null;stopCheckpointLoop();document.getElementById('islandIndicator')?.classList.add('hidden')}
function startSessionRuntime(){
  stopSessionRuntime();
  timerDuration=Math.max(0,Math.ceil((plannedSessionMs(activeSession)-sessionElapsedMs(activeSession,true))/1000));originalDuration=Math.max(1,activeSession.plannedMinutes*60);updateTimerUI();
  document.getElementById('islandIndicator')?.classList.remove('hidden');startCheckpointLoop();
  timerInterval=setInterval(()=>{const s=checkpointActiveSession('tick',false);if(!s)return;if(s.remainingSeconds<=0)handleElapsedSessionV112()},1000);
}
function startRestoredSessionTimer(){
  if(timerInterval)clearInterval(timerInterval);
  timerInterval=setInterval(()=>{timerDuration=Math.max(0,Math.ceil((ucEndsAt-Date.now())/1000));updateTimerUI();if(timerDuration<=0)finishSession({status:'completed',reason:'timer_finished'})},1000);
}

(function bindFocusClarityAndLifecycleEvents(){
  if(typeof window.syncUcData==='function'){
    const previousSync=window.syncUcData;
    window.syncUcData=function(){
      previousSync.apply(this,arguments);
      syncFocusClarityCard();
    };
  }
  document.getElementById('ucFocusCard')?.addEventListener('keydown',function(event){
    if(event.key==='Enter'||event.key===' '){event.preventDefault();showUcTask();}
  });
  window.addEventListener('pagehide',()=>{if(activeSession?.lifecycleState==='running')checkpointActiveSession('pagehide',true)},{capture:true});
  document.addEventListener('visibilitychange',()=>{if(document.hidden&&activeSession?.lifecycleState==='running')checkpointActiveSession('visibility_hidden',true)},{capture:true});
})();
