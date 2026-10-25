const SESSION_HISTORY_KEY='focusmode-session-history-v11-1';
const SESSION_HISTORY_BACKUP_KEY='focusmode-session-history-corrupt-backup';
const SESSION_HISTORY_SCHEMA=1;
const SESSION_HISTORY_LIMIT=500;
const HISTORY_FINAL_STATUSES=Object.freeze(['completed','cancelled','interrupted']);
let historyLoadedLogged=false;
function historyClone(v){return typeof structuredClone==='function'?structuredClone(v):JSON.parse(JSON.stringify(v))}
function historyStoreEmpty(){return{schemaVersion:SESSION_HISTORY_SCHEMA,sessions:[]}}
function historyLog(event,data={}){console.info('[SessionHistory]',{event,...data})}
function historyWarn(event,data={}){console.warn('[SessionHistory]',{event,...data})}
function persistHistoryStore(store){
 try{localStorage.setItem(SESSION_HISTORY_KEY,JSON.stringify(store));return true}
 catch(error){console.error('[SessionHistory]',{event:'history_persist_failed',reason:error?.name||'unknown'});return false}
}
function loadHistoryStore(){
 const raw=localStorage.getItem(SESSION_HISTORY_KEY);
 if(!raw){const empty=historyStoreEmpty();if(!historyLoadedLogged){historyLoadedLogged=true;historyLog('history_loaded',{count:0})}return empty}
 try{
  const data=JSON.parse(raw);
  if(!data||data.schemaVersion!==SESSION_HISTORY_SCHEMA||!Array.isArray(data.sessions))throw new Error('schema_invalid');
  if(!historyLoadedLogged){historyLoadedLogged=true;historyLog('history_loaded',{count:data.sessions.length})}
  return data;
 }catch(error){
  try{localStorage.setItem(SESSION_HISTORY_BACKUP_KEY,raw)}catch{}
  historyWarn('history_corrupt_reset',{reason:error?.message||'unknown'});
  const empty=historyStoreEmpty();persistHistoryStore(empty);
  if(!historyLoadedLogged){historyLoadedLogged=true;historyLog('history_loaded',{count:0})}
  return empty;
 }
}
function validateHistorySessionInput(s){
 const errors=[];
 if(!s||typeof s!=='object')return{valid:false,errors:['session_missing']};
 if(!String(s.id||'').trim())errors.push('id_missing');
 if(!['quick','mission'].includes(s.type))errors.push('type_invalid');
 if(!HISTORY_FINAL_STATUSES.includes(s.status))errors.push('status_invalid');
 if(!String(s.title||'').trim())errors.push('title_missing');
 if(!String(s.task||'').trim())errors.push('task_missing');
 if(!Number.isFinite(Number(s.plannedMinutes))||Number(s.plannedMinutes)<=0)errors.push('planned_minutes_invalid');
 if(!Number.isFinite(Number(s.actualMinutes))||Number(s.actualMinutes)<0)errors.push('actual_minutes_invalid');
 if(!Number.isFinite(Number(s.startedAt))||Number(s.startedAt)<=0)errors.push('started_at_invalid');
 if(!Number.isFinite(Number(s.finishedAt))||Number(s.finishedAt)<=0)errors.push('finished_at_invalid');
 if(Number.isFinite(Number(s.startedAt))&&Number.isFinite(Number(s.finishedAt))&&Number(s.finishedAt)<Number(s.startedAt))errors.push('finished_before_started');
 if(s.type==='quick'&&(s.missionId!=null||s.missionStepId!=null))errors.push('quick_mission_link_forbidden');
 if(s.type==='mission'&&(!String(s.missionId||'').trim()||!String(s.missionStepId||'').trim()))errors.push('mission_link_required');
 return{valid:!errors.length,errors};
}
function normalizeHistorySession(s){
 const cleanText=(v,max)=>String(v??'').trim().slice(0,max);
 return{id:cleanText(s.id,120),type:s.type,status:s.status,title:cleanText(s.title,180),task:cleanText(s.task,1200),plannedMinutes:Math.round(Number(s.plannedMinutes)),actualMinutes:Math.round(Number(s.actualMinutes)),startedAt:Number(s.startedAt),finishedAt:Number(s.finishedAt),missionId:s.type==='mission'?cleanText(s.missionId,160):null,missionStepId:s.type==='mission'?cleanText(s.missionStepId,160):null,result:{reason:cleanText(s.result?.reason,120)||null,outcome:cleanText(s.result?.outcome,40)||null,feedback:cleanText(s.result?.feedback,1000)||null,rating:Number.isFinite(Number(s.result?.rating))?Math.max(1,Math.min(5,Math.round(Number(s.result.rating)))):null,countAsFocus:typeof s.result?.countAsFocus==='boolean'?s.result.countAsFocus:null},ui:{modeLabel:cleanText(s.ui?.modeLabel,120),progressCurrent:Math.max(1,Math.round(Number(s.ui?.progressCurrent)||1)),progressTotal:Math.max(1,Math.round(Number(s.ui?.progressTotal)||1))},savedAt:Date.now(),schemaVersion:SESSION_HISTORY_SCHEMA};
}
function validateNormalizedHistorySession(s){return validateHistorySessionInput(s)}
function mergeHistoryRecord(oldRecord,newRecord){return normalizeHistorySession({...oldRecord,...newRecord,result:{...(oldRecord.result||{}),...(newRecord.result||{})},ui:{...(oldRecord.ui||{}),...(newRecord.ui||{})}})}
const sessionRepository={
 save(session){
  const inputCheck=validateHistorySessionInput(session);
  if(!inputCheck.valid){console.error('[SessionHistory]',{event:'history_session_invalid',sessionId:session?.id||null,errors:inputCheck.errors});return null}
  const normalized=normalizeHistorySession(session),normalizedCheck=validateNormalizedHistorySession(normalized);
  if(!normalizedCheck.valid){console.error('[SessionHistory]',{event:'history_session_invalid_after_normalize',sessionId:normalized.id||null,errors:normalizedCheck.errors});return null}
  const store=loadHistoryStore(),index=store.sessions.findIndex(x=>x.id===normalized.id);
  if(index>=0){historyLog('history_session_duplicate_prevented',{sessionId:normalized.id});store.sessions[index]=mergeHistoryRecord(store.sessions[index],normalized);historyLog('history_session_updated',{sessionId:normalized.id})}
  else{store.sessions.push(normalized);historyLog('history_session_created',{sessionId:normalized.id,type:normalized.type,status:normalized.status})}
  store.sessions.sort((a,b)=>(Number(b.finishedAt)||0)-(Number(a.finishedAt)||0));
  store.sessions=store.sessions.slice(0,SESSION_HISTORY_LIMIT);
  if(!persistHistoryStore(store))return null;
  globalThis.refreshSessionHistoryUI?.();globalThis.refreshHomeStats?.();globalThis.refreshStatsScreen?.();
  return historyClone(store.sessions.find(x=>x.id===normalized.id));
 },
 getById(id){const found=loadHistoryStore().sessions.find(x=>x.id===id);return found?historyClone(found):null},
 getAll(){return loadHistoryStore().sessions.slice().sort((a,b)=>(Number(b.finishedAt)||0)-(Number(a.finishedAt)||0)).map(historyClone)},
 update(id,patch){const current=this.getById(id);if(!current)return null;return this.save({...current,...patch,result:{...(current.result||{}),...(patch.result||{})},ui:{...(current.ui||{}),...(patch.ui||{})}})},
 updateOutcome(id,outcome){return this.update(id,{result:{outcome}})},
 remove(id){const store=loadHistoryStore();store.sessions=store.sessions.filter(x=>x.id!==id);if(persistHistoryStore(store)){globalThis.refreshSessionHistoryUI?.();globalThis.refreshHomeStats?.();globalThis.refreshStatsScreen?.()}},
 clear(){if(persistHistoryStore(historyStoreEmpty())){globalThis.refreshSessionHistoryUI?.();globalThis.refreshHomeStats?.();globalThis.refreshStatsScreen?.()}},
 getCompleted(){return this.getAll().filter(x=>x.status==='completed')},
 getToday(){const start=new Date();start.setHours(0,0,0,0);return this.getAll().filter(x=>x.finishedAt>=start.getTime())},
 getThisWeek(){const start=new Date();start.setHours(0,0,0,0);start.setDate(start.getDate()-((start.getDay()+6)%7));return this.getAll().filter(x=>x.finishedAt>=start.getTime())}
};
window.sessionRepository=sessionRepository;

/* v12.8 Session Persistence Design
   Design-only contract. Existing Session, Recovery and History runtime behavior remains unchanged. */
(function installSessionPersistenceDesignV128(){
  'use strict';
  const app=window.FocusModeApp=window.FocusModeApp||{};
  app.design=app.design||{};

  const contract={
    version:'12.8',
    status:'design-only',
    owner:'FocusModeApp.repositories.sessions',
    storageService:'FocusModeApp.services.storage',
    currentRuntime:{
      activeSessionKey:'focusmode-active-session-v11',
      historyKey:'focusmode-session-history-v11',
      lastSessionKey:'focusmode-last-session-v11'
    },
    aggregate:{
      name:'PersistedSession',
      identity:'session.id',
      lifecycleStates:['created','running','paused','completed','cancelled'],
      terminalStates:['completed','cancelled'],
      requiredFields:['id','type','title','plannedMinutes','lifecycleState','createdAt'],
      optionalLinks:['missionId','missionStepId']
    },
    repositoryContract:{
      reads:['loadActive','getActive','loadLast','listHistory'],
      writes:['saveActive','checkpoint','complete','cancel','clearActive'],
      diagnostics:['validate','inspect'],
      forbidden:['direct localStorage access outside storage service','UI rendering','timer ownership','mission mutation']
    },
    writePolicy:{
      singleWriter:'session service',
      serialization:'JSON',
      atomicUnit:'whole session snapshot',
      checkpointTriggers:['session-start','pause','resume','visibility-hidden','periodic-safe-point'],
      terminalCommitOrder:['validate terminal snapshot','append history once','save last session','clear active session'],
      duplicateProtection:'session.id plus terminal state',
      failurePolicy:'preserve last valid active snapshot and report structured diagnostic'
    },
    recoveryPolicy:{
      sourcePriority:['valid active snapshot','valid last-session snapshot','history record'],
      invalidSnapshot:'quarantine raw value, do not overwrite a newer valid snapshot',
      clockPolicy:'derive remaining time from persisted timestamps, never from interval tick count',
      missionPolicy:'re-link by missionId and missionStepId without mutating mission progress during hydration'
    },
    migrationPlan:[
      {phase:1,name:'Contract and observability',runtimeChange:false},
      {phase:2,name:'Repository adapter around existing keys',runtimeChange:true},
      {phase:3,name:'Route Session Core writes through repository',runtimeChange:true},
      {phase:4,name:'Route Recovery and History reads through repository',runtimeChange:true},
      {phase:5,name:'Remove remaining direct session-storage calls',runtimeChange:true}
    ],
    nonGoals:['new UI','CSS changes','timer redesign','history schema rewrite','mission repository changes','storage-key migration']
  };

  const deepFreeze=value=>{
    if(value&&typeof value==='object'&&!Object.isFrozen(value)){
      Object.freeze(value);
      Object.values(value).forEach(deepFreeze);
    }
    return value;
  };
  app.design.sessionPersistence=deepFreeze(contract);
})();

/* v12.9 Session Repository
   Compatibility adapter over the existing Session, Recovery and History keys.
   Session Core, Recovery and History UI remain authoritative until v13 migration. */
(function installSessionRepositoryV129(global){
  'use strict';
  const App=global.FocusModeApp;
  const storage=App?.services?.storage;
  if(!App?.core?.registerModule||!storage)throw new Error('Architecture Shell and Storage Service are required before Session Repository.');
  const MODULE_NAME='repositories.sessions';
  if(App.core.hasModule(MODULE_NAME))return;

  const KEYS=Object.freeze({
    active:'focusmode-active-session-v11',
    last:'focusmode-last-session-v11',
    history:'focusmode-session-history-v11-1',
    legacyHistory:'focusmode-session-history-v11',
    activeBackup:'focusmode-active-session-v11-repository-backup',
    lastBackup:'focusmode-last-session-v11-repository-backup',
    historyBackup:'focusmode-session-history-repository-backup'
  });
  const HISTORY_SCHEMA=1;
  const HISTORY_LIMIT=500;
  const ACTIVE_STATES=new Set(['created','running','paused','awaiting_resume','awaiting_review']);
  const TERMINAL_STATES=new Set(['completed','cancelled','interrupted']);
  const clone=value=>value==null?value:(typeof global.structuredClone==='function'?global.structuredClone(value):JSON.parse(JSON.stringify(value)));
  const clean=value=>String(value??'').trim();

  function validationResult(errors){return Object.freeze({valid:errors.length===0,errors:Object.freeze(errors.slice())})}
  function validate(session,options={}){
    const errors=[];
    if(!session||typeof session!=='object'||Array.isArray(session))return validationResult(['session_missing']);
    if(!clean(session.id))errors.push('id_missing');
    if(!['quick','mission'].includes(session.type))errors.push('type_invalid');
    if(!clean(session.title))errors.push('title_missing');
    if(!Number.isFinite(Number(session.plannedMinutes))||Number(session.plannedMinutes)<=0)errors.push('planned_minutes_invalid');
    const state=clean(session.lifecycleState||session.status);
    if(!state)errors.push('state_missing');
    else if(!ACTIVE_STATES.has(state)&&!TERMINAL_STATES.has(state))errors.push('state_invalid');
    if(session.type==='quick'&&(session.missionId!=null||session.missionStepId!=null))errors.push('quick_mission_link_forbidden');
    if(session.type==='mission'&&(!clean(session.missionId)||!clean(session.missionStepId)))errors.push('mission_link_required');
    if(options.terminal===true&&!TERMINAL_STATES.has(clean(session.status)))errors.push('terminal_state_required');
    if(options.active===true&&TERMINAL_STATES.has(state))errors.push('active_state_required');
    if(session.startedAt!=null&&(!Number.isFinite(Number(session.startedAt))||Number(session.startedAt)<=0))errors.push('started_at_invalid');
    if(session.finishedAt!=null&&(!Number.isFinite(Number(session.finishedAt))||Number(session.finishedAt)<=0))errors.push('finished_at_invalid');
    return validationResult(errors);
  }
  function quarantine(sourceKey,backupKey){try{return storage.backup(sourceKey,backupKey)}catch{return false}}
  function safeRead(key,fallback,backupKey){
    const value=storage.getJSON(key,fallback,{backupKey,removeCorrupt:true});
    return clone(value);
  }
  function loadActive(){
    const value=safeRead(KEYS.active,null,KEYS.activeBackup);
    if(value==null)return null;
    const check=validate(value,{active:true});
    if(!check.valid){quarantine(KEYS.active,KEYS.activeBackup);storage.remove(KEYS.active);console.warn('[SessionRepository]',{event:'active_invalid',errors:check.errors});return null}
    return clone(value);
  }
  function saveActive(session){
    const check=validate(session,{active:true});
    if(!check.valid){console.error('[SessionRepository]',{event:'active_rejected',errors:check.errors});return null}
    return storage.setJSON(KEYS.active,session)?clone(session):null;
  }
  function checkpoint(session){
    const snapshot={...clone(session),lastCheckpointAt:Number(session?.lastCheckpointAt)||Date.now()};
    return saveActive(snapshot);
  }
  function loadLast(){
    const value=safeRead(KEYS.last,null,KEYS.lastBackup);
    if(value==null)return null;
    const check=validate(value);
    if(!check.valid){quarantine(KEYS.last,KEYS.lastBackup);storage.remove(KEYS.last);console.warn('[SessionRepository]',{event:'last_invalid',errors:check.errors});return null}
    return clone(value);
  }
  function saveLast(session){
    const check=validate(session);
    if(!check.valid){console.error('[SessionRepository]',{event:'last_rejected',errors:check.errors});return null}
    return storage.setJSON(KEYS.last,session)?clone(session):null;
  }
  function emptyHistory(){return{schemaVersion:HISTORY_SCHEMA,sessions:[]}}
  function normalizeHistoryStore(value){
    if(Array.isArray(value))return{schemaVersion:HISTORY_SCHEMA,sessions:value};
    if(value&&value.schemaVersion===HISTORY_SCHEMA&&Array.isArray(value.sessions))return value;
    return null;
  }
  function readHistoryStore(){
    let raw=safeRead(KEYS.history,null,KEYS.historyBackup),store=normalizeHistoryStore(raw),source=KEYS.history;
    if(!store){raw=safeRead(KEYS.legacyHistory,null,KEYS.historyBackup);store=normalizeHistoryStore(raw);source=KEYS.legacyHistory}
    if(!store){
      if(raw!=null)quarantine(source,KEYS.historyBackup);
      return emptyHistory();
    }
    const sessions=store.sessions.filter(item=>validate(item,{terminal:true}).valid);
    if(sessions.length!==store.sessions.length)console.warn('[SessionRepository]',{event:'history_invalid_records_ignored',ignored:store.sessions.length-sessions.length});
    return{schemaVersion:HISTORY_SCHEMA,sessions:sessions.map(clone)};
  }
  function listHistory(){return readHistoryStore().sessions.slice().sort((a,b)=>(Number(b.finishedAt)||0)-(Number(a.finishedAt)||0)).map(clone)}
  function historyIdentity(session){return clean(session.id)+'::'+clean(session.status)}
  function appendHistory(session){
    const check=validate(session,{terminal:true});
    if(!check.valid){console.error('[SessionRepository]',{event:'history_rejected',errors:check.errors});return null}
    const store=readHistoryStore(),identity=historyIdentity(session),index=store.sessions.findIndex(x=>historyIdentity(x)===identity);
    if(index>=0){
      const old=store.sessions[index];
      store.sessions[index]={...old,...clone(session),result:{...(old.result||{}),...(session.result||{})},ui:{...(old.ui||{}),...(session.ui||{})}};
    }else store.sessions.push(clone(session));
    store.sessions.sort((a,b)=>(Number(b.finishedAt)||0)-(Number(a.finishedAt)||0));
    store.sessions=store.sessions.slice(0,HISTORY_LIMIT);
    if(!storage.setJSON(KEYS.history,store))return null;
    return clone(store.sessions.find(x=>historyIdentity(x)===identity));
  }
  function clearActive(expectedId=null){
    const current=loadActive();
    if(expectedId!=null&&current&&clean(current.id)!==clean(expectedId)){console.warn('[SessionRepository]',{event:'active_clear_id_mismatch',expectedId,actualId:current.id});return false}
    return storage.remove(KEYS.active);
  }
  function inspect(){
    const active=loadActive(),last=loadLast(),history=listHistory();
    return Object.freeze({version:'12.9',keys:KEYS,active:clone(active),last:clone(last),historyCount:history.length,historyIds:Object.freeze(history.map(x=>x.id)),storageAvailable:storage.isAvailable()});
  }
  const repository=Object.freeze({keys:KEYS,loadActive,saveActive,checkpoint,loadLast,saveLast,listHistory,appendHistory,clearActive,validate,inspect});
  App.repositories.sessions=App.core.registerModule(MODULE_NAME,repository,{virtualPath:'src/repositories/session-repository.js',version:'12.9',status:'adapter-not-authoritative',runtimeOwnersUnchanged:true});
  App.compatibility.sessionRepository=Object.freeze({available:true,authoritative:false,sessionCoreMigrated:false,recoveryMigrated:false,historyUIMigrated:false,keysRenamed:0});
  console.info('[SessionRepository]',{event:'repository_ready',version:'12.9',historyCount:repository.listHistory().length});
})(window);
