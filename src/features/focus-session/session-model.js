/**
 * Physical module: src/features/focus-session/session-model.js
 */

const SESSION_CORE_STORAGE_KEY='focusmode-active-session-v11';
const SESSION_CORE_LAST_KEY='focusmode-last-session-v11';
const SESSION_TYPES=Object.freeze(['quick','mission']);
const SESSION_STATUSES=Object.freeze(['created','running','completed','cancelled','interrupted']);
let activeSession=null;window.activeSession=null;
function createSessionId(){return globalThis.crypto?.randomUUID?crypto.randomUUID():'session_'+Date.now()+'_'+Math.random().toString(36).slice(2,10)}
function cloneSession(v){return typeof structuredClone==='function'?structuredClone(v):JSON.parse(JSON.stringify(v))}
function setActiveSession(session){activeSession=session?cloneSession(session):null;window.activeSession=activeSession;if(activeSession)localStorage.setItem(SESSION_CORE_STORAGE_KEY,JSON.stringify(activeSession));else localStorage.removeItem(SESSION_CORE_STORAGE_KEY);return activeSession}
function getActiveSession(){return activeSession?cloneSession(activeSession):null}
function updateActiveSession(patch){return activeSession?setActiveSession({...activeSession,...patch}):null}
function validateSession(s){const e=[];if(!s||typeof s!=='object')return{valid:false,errors:['session_missing']};if(!s.id)e.push('id_missing');if(!SESSION_TYPES.includes(s.type))e.push('type_invalid');if(s.status!=='created')e.push('status_must_be_created');if(!String(s.title||'').trim())e.push('title_missing');if(!String(s.task||'').trim())e.push('task_missing');if(!Number.isFinite(Number(s.plannedMinutes))||Number(s.plannedMinutes)<=0)e.push('duration_invalid');if(Number(s.actualMinutes)!==0)e.push('actual_minutes_must_be_zero');if(s.startedAt!==null)e.push('started_at_must_be_null');if(s.finishedAt!==null)e.push('finished_at_must_be_null');if(s.type==='quick'&&(s.missionId!==null||s.missionStepId!==null))e.push('quick_mission_link_forbidden');if(s.type==='mission'&&(!s.missionId||!s.missionStepId))e.push('mission_link_required');return{valid:!e.length,errors:e}}
function makeSession(x){return{id:createSessionId(),type:x.type,status:'created',title:String(x.title||'').trim(),task:String(x.task||'').trim(),plannedMinutes:Math.round(Number(x.plannedMinutes)||0),actualMinutes:0,startedAt:null,finishedAt:null,missionId:x.missionId??null,missionStepId:x.missionStepId??null,result:{reason:null,outcome:null,feedback:null},ui:{modeLabel:String(x.ui?.modeLabel||''),progressCurrent:Math.max(1,Number(x.ui?.progressCurrent)||1),progressTotal:Math.max(1,Number(x.ui?.progressTotal)||1)}}}
function createQuickSession(input={}){const task=String(input.task||'').trim()||'Konzentriert arbeiten',minutes=Math.max(1,Math.round(Number(input.plannedMinutes)||25));return makeSession({type:'quick',title:String(input.title||'').trim()||task,task,plannedMinutes:minutes,missionId:null,missionStepId:null,ui:{modeLabel:'Kurzer Fokus',progressCurrent:1,progressTotal:1}})}
function ensureActiveMissionId(){if(!activeAIPlan)return null;if(!activeAIPlan.id){activeAIPlan.id='mission_'+Date.now()+'_'+Math.random().toString(36).slice(2,8);saveMissionState()}return activeAIPlan.id}
function createMissionSession(missionId,stepId){if(!activeAIPlan)throw new Error('Diese Mission konnte nicht gefunden werden.');const realId=ensureActiveMissionId();if(missionId!==realId)throw new Error('Diese Mission konnte nicht gefunden werden.');const index=activeAIPlan.sessions.findIndex(s=>s.id===stepId);if(index<0)throw new Error('Dieser Missionsschritt ist nicht mehr verfügbar.');if(index!==missionProgress.currentSessionIndex)throw new Error('Dieser Missionsschritt kann gerade nicht gestartet werden.');const s=activeAIPlan.sessions[index];return makeSession({type:'mission',title:s.title,task:s.task||s.reason||s.title,plannedMinutes:s.durationMinutes,missionId:realId,missionStepId:s.id,ui:{modeLabel:activeAIPlan.goal?.subject||'Focus Mission',progressCurrent:index+1,progressTotal:activeAIPlan.sessions.length}})}
function currentUcSession(){return window.activeSession}

const ACTIVE_SESSION_STATES=Object.freeze(['running','awaiting_resume','awaiting_review']);
const ACTIVE_SESSION_BACKUP_KEY='focusmode-active-session-v11-recovery-backup';
let sessionCheckpointInterval=null;
let sessionRecoveryCandidate=null;
let sessionRecoveryBootResolved=false;

function recoveryLog(event,data={}){console.info('[SessionRecovery]',{event,...data})}
function plannedSessionMs(s){return Math.max(1,Math.round(Number(s?.plannedMinutes)||1))*60000}
function sessionElapsedMs(s,includeLive=true){
 const cap=plannedSessionMs(s),base=Math.max(0,Number(s?.elapsedMs)||0);
 const live=includeLive&&s?.lifecycleState==='running'&&Number.isFinite(Number(s?.lastActiveAt))?Math.max(0,Date.now()-Number(s.lastActiveAt)):0;
 return Math.min(cap,base+live);
}
function validateActiveSessionV112(s){
 const errors=[];
 if(!s||typeof s!=='object')return{valid:false,errors:['session_missing']};
 if(!String(s.id||'').trim())errors.push('id_missing');
 if(!SESSION_TYPES.includes(s.type))errors.push('type_invalid');
 if(!ACTIVE_SESSION_STATES.includes(s.lifecycleState))errors.push('lifecycle_invalid');
 if(!Number.isFinite(Number(s.plannedMinutes))||Number(s.plannedMinutes)<=0)errors.push('duration_invalid');
 if(!Number.isFinite(Number(s.startedAt))||Number(s.startedAt)<=0)errors.push('started_at_invalid');
 if(!Number.isFinite(Number(s.elapsedMs))||Number(s.elapsedMs)<0||Number(s.elapsedMs)>plannedSessionMs(s))errors.push('elapsed_invalid');
 if(s.type==='quick'&&(s.missionId!=null||s.missionStepId!=null))errors.push('quick_mission_link_forbidden');
 if(s.type==='mission'&&(!String(s.missionId||'').trim()||!String(s.missionStepId||'').trim()))errors.push('mission_link_required');
 return{valid:!errors.length,errors};
}
function migrateActiveSessionV112(raw){
 if(!raw||raw.status!=='running')return null;
 const planned=plannedSessionMs(raw),legacyElapsed=Math.max(0,Math.min(planned,Date.now()-(Number(raw.startedAt)||Date.now())));
 const elapsed=Number.isFinite(Number(raw.elapsedMs))?Math.max(0,Math.min(planned,Number(raw.elapsedMs))):legacyElapsed;
 return{...raw,status:'running',lifecycleState:'awaiting_resume',elapsedMs:elapsed,remainingSeconds:Math.max(0,Math.ceil((planned-elapsed)/1000)),lastActiveAt:null,lastCheckpointAt:Number(raw.lastCheckpointAt)||Date.now(),endsAt:null,finalizationState:raw.finalizationState||null};
}
