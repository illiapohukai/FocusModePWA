const FOCUS_AI_ENDPOINT='https://focusmodedemoai.illia-pohukai.workers.dev/create-mission';
const FOCUS_AI_ADAPT_ENDPOINT='https://focusmodedemoai.illia-pohukai.workers.dev/adapt-mission';
const FOCUS_AI_TIMEOUT_MS=120000;
let aiRequestController=null;
let adaptationController=null;

function missionTypeForAPI(){
  const val=typeof selectedMissionTypeValue!=='undefined'?selectedMissionTypeValue:globalThis.selectedMissionTypeValue;
  const map={'Prüfung':'exam','Aufgabe':'assignment','Projekt':'project','Eigene Mission':'custom','Exam':'exam','Task':'assignment','Project':'project','Custom mission':'custom'};
  return map[val]||'custom';
}
function confidenceForAPI(){
  const raw=typeof selectedMissionConfidenceValue!=='undefined'?selectedMissionConfidenceValue:globalThis.selectedMissionConfidenceValue;
  const v=(raw||'').toLowerCase();
  if(v.includes('unsicher')||v.includes('uncertain'))return'uncertain';
  if(v==='sicher'||v.includes('confident'))return'confident';
  return'partial';
}
function normalizeAIPlan(plan){
  const sessions=Array.isArray(plan?.sessions)?plan.sessions.slice(0,6).map((s,i)=>({id:s.id||'session-'+(i+1),title:String(s.title||('Schritt '+(i+1))),task:String(s.task||''),durationMinutes:Math.max(5,Math.min(60,Number(s.durationMinutes)||20)),reason:String(s.reason||''),requiredTools:Array.isArray(s.requiredTools)?s.requiredTools:[],recommendedApps:Array.isArray(s.recommendedApps)?s.recommendedApps:[]})):[];
  if(!sessions.length)throw new Error('Der AI-Plan enthält keine Sitzungen.');
  return {goal:{title:String(plan.goal?.title||'Focus Mission'),subject:String(plan.goal?.subject||'Allgemein'),type:String(plan.goal?.type||'custom'),deadlineText:String(plan.goal?.deadlineText||'Nicht angegeben')},sourceAnalysis:{detectedTasks:String(plan.sourceAnalysis?.detectedTasks||''),detectedPages:String(plan.sourceAnalysis?.detectedPages||''),confidence:Number(plan.sourceAnalysis?.confidence)||0,missingInformation:Array.isArray(plan.sourceAnalysis?.missingInformation)?plan.sourceAnalysis.missingInformation:[],facts:Array.isArray(plan.sourceAnalysis?.facts)?plan.sourceAnalysis.facts:[],estimates:Array.isArray(plan.sourceAnalysis?.estimates)?plan.sourceAnalysis.estimates:[]},estimate:{totalMinutes:Number(plan.estimate?.totalMinutes)||sessions.reduce((a,s)=>a+s.durationMinutes,0),difficulty:String(plan.estimate?.difficulty||'unknown'),explanation:String(plan.estimate?.explanation||'Die Dauer ist eine erste Schätzung und wird nach deinen Sitzungen angepasst.')},sessions};
}
function normalizeUpdatedSessions(items){
  if(!Array.isArray(items)||!items.length)throw new Error('adaptation_sessions_invalid');
  return items.map((x,i)=>({id:String(x.id||('adapted_'+Date.now()+'_'+i)),title:String(x.title||('Schritt '+(i+1))),task:String(x.task||''),durationMinutes:Math.max(5,Math.min(60,Number(x.durationMinutes)||20)),reason:String(x.reason||''),requiredTools:Array.isArray(x.requiredTools)?x.requiredTools:[],recommendedApps:Array.isArray(x.recommendedApps)?x.recommendedApps:[]}));
}

async function createMissionPlan({goalText='',missionType=missionTypeForAPI(),confidence=confidenceForAPI(),imageFile=null}={}){
  aiRequestController?.abort();
  aiRequestController=new AbortController();
  const timeout=setTimeout(()=>aiRequestController.abort(),FOCUS_AI_TIMEOUT_MS);
  try{
    const form=new FormData();
    form.append('goalText',goalText);
    form.append('missionType',missionType);
    form.append('confidence',confidence);
    if(imageFile)form.append('image',imageFile,imageFile.name);
    const response=await fetch(FOCUS_AI_ENDPOINT,{method:'POST',body:form,signal:aiRequestController.signal});
    let data={};try{data=await response.json()}catch{}
    if(!response.ok||!data.ok||!data.plan)throw new Error(data.error||('HTTP '+response.status));
    return normalizeAIPlan(data.plan);
  }finally{
    clearTimeout(timeout);
  }
}

async function requestMissionAdaptation(payload){
  adaptationController?.abort();
  adaptationController=new AbortController();
  const timeout=setTimeout(()=>adaptationController.abort(),FOCUS_AI_TIMEOUT_MS);
  try{
    const response=await fetch(FOCUS_AI_ADAPT_ENDPOINT,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload),signal:adaptationController.signal});
    let data={};try{data=await response.json()}catch{}
    if(!response.ok||!data.ok||!data.adaptation)throw new Error(data.error||('HTTP '+response.status));
    return data.adaptation;
  }finally{
    clearTimeout(timeout);
  }
}

(function installAIService(global){
  'use strict';
  const App=global.FocusModeApp;
  if(!App?.core?.registerModule)return;
  if(App.core.hasModule('services.ai'))return;
  const service=Object.freeze({
    endpoints:Object.freeze({createMission:FOCUS_AI_ENDPOINT,adaptMission:FOCUS_AI_ADAPT_ENDPOINT}),
    timeoutMs:FOCUS_AI_TIMEOUT_MS,
    missionTypeForAPI,
    confidenceForAPI,
    normalizeAIPlan,
    normalizeUpdatedSessions,
    createMissionPlan,
    requestMissionAdaptation
  });
  App.services.ai=App.core.registerModule('services.ai',service,{virtualPath:'src/services/ai-service.js',version:'15.4',status:'authoritative'});
})(window);
