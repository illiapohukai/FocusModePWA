/**
 * Physical module: src/services/ai-service.js
 */
const FOCUS_AI_ADAPT_ENDPOINT='https://focusmodedemoai.illia-pohukai.workers.dev/adapt-mission';
const FOCUS_AI_ENDPOINT='https://focusmodedemoai.illia-pohukai.workers.dev/create-mission';
let missionImageFile=null;
let missionImagePreviewUrl=null;
let pendingAIPlan=null;
let activeAIPlan=null;
let aiRequestController=null;

function openRealFilePicker(cameraPreferred){
  const input=document.getElementById('missionFileInput');
  if(!input)return;
  if(cameraPreferred)input.setAttribute('capture','environment'); else input.removeAttribute('capture');
  input.value='';
  input.click();
}

async function handleMissionFile(event){
  clearMissionError();
  const original=event.target.files&&event.target.files[0];
  if(!original)return;
  if(!['image/jpeg','image/png','image/webp'].includes(original.type))return showMissionError('Bitte wähle ein JPEG-, PNG- oder WEBP-Bild aus.');
  try{
    missionImageFile=await compressMissionImage(original);
    if(missionImagePreviewUrl)URL.revokeObjectURL(missionImagePreviewUrl);
    missionImagePreviewUrl=URL.createObjectURL(missionImageFile);
    const card=document.getElementById('selectedFileCard');
    card.classList.remove('hidden');card.classList.add('selected');
    document.getElementById('selectedFileName').textContent=original.name||'Foto.jpg';
    document.getElementById('selectedFileMeta').textContent=formatBytes(missionImageFile.size)+' · bereit für AI-Analyse';
    const iconBox=card.querySelector('.w-11.h-11');
    if(iconBox)iconBox.innerHTML='<img src="'+missionImagePreviewUrl+'" alt="Ausgewähltes Lernmaterial">';
    document.getElementById('goalInputHint').textContent='Foto hinzugefügt';
    lucide.createIcons();
  }catch(err){showMissionError('Das Foto konnte nicht vorbereitet werden. Bitte versuche ein anderes Bild.');}
}

async function compressMissionImage(file){
  if(file.size<=1800000)return file;
  const bitmap=await createImageBitmap(file);
  const maxSide=1800,scale=Math.min(1,maxSide/Math.max(bitmap.width,bitmap.height));
  const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(bitmap.width*scale));canvas.height=Math.max(1,Math.round(bitmap.height*scale));
  canvas.getContext('2d',{alpha:false}).drawImage(bitmap,0,0,canvas.width,canvas.height);
  const blob=await new Promise((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(new Error('compression failed')),'image/jpeg',.82));
  bitmap.close?.();
  return new File([blob],(file.name||'material').replace(/\.[^.]+$/, '')+'.jpg',{type:'image/jpeg',lastModified:Date.now()});
}
function formatBytes(bytes){return bytes<1048576?Math.max(1,Math.round(bytes/1024))+' KB':(bytes/1048576).toFixed(1).replace('.',',')+' MB'}
function showMissionError(message){const box=document.getElementById('missionError');if(!box)return;box.textContent=message;box.classList.remove('hidden');setAppScreen('goal',{immediate:true});}
function clearMissionError(){const box=document.getElementById('missionError');if(box){box.textContent='';box.classList.add('hidden')}}
function missionTypeForAPI(){const map={'Prüfung':'exam','Aufgabe':'assignment','Projekt':'project','Eigene Mission':'custom','Exam':'exam','Task':'assignment','Project':'project','Custom mission':'custom'};return map[selectedMissionTypeValue]||'custom'}
function confidenceForAPI(){const v=(selectedMissionConfidenceValue||'').toLowerCase();if(v.includes('unsicher')||v.includes('uncertain'))return'uncertain';if(v==='sicher'||v.includes('confident'))return'confident';return'partial'}
function resetAnalysisRows(){document.querySelectorAll('#analysisRows .analysis-row').forEach(row=>{row.classList.add('opacity-40');row.classList.remove('done');const icon=row.querySelector('.analysis-icon');if(icon)icon.innerHTML='<span class="flow-loader-dot w-1.5 h-1.5 bg-indigo-300 rounded-full"></span>'});}
function completeAnalysisRow(index){const row=document.querySelectorAll('#analysisRows .analysis-row')[index];if(!row)return;row.classList.remove('opacity-40');row.classList.add('done');const icon=row.querySelector('.analysis-icon');if(icon)icon.innerHTML='<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-400"></i>';lucide.createIcons();}

async function analyzeGoal(){
  if(!selectedMissionTypeValue||!selectedMissionConfidenceValue)return;
  const goalText=(document.getElementById('goalPrompt').value||'').trim();
  if(!goalText&&!missionImageFile)return showMissionError('Beschreibe dein Ziel oder füge ein Foto hinzu.');
  clearMissionError();resetAnalysisRows();setAppScreen('analyzing');
  [450,950].forEach((t,i)=>setTimeout(()=>completeAnalysisRow(i),t));
  const button=document.getElementById('missionPlanButton');button?.classList.add('ai-loading');
  aiRequestController?.abort();aiRequestController=new AbortController();
  const timeout=setTimeout(()=>aiRequestController.abort(),120000);
  try{
    const form=new FormData();form.append('goalText',goalText);form.append('missionType',missionTypeForAPI());form.append('confidence',confidenceForAPI());if(missionImageFile)form.append('image',missionImageFile,missionImageFile.name);
    const response=await fetch(FOCUS_AI_ENDPOINT,{method:'POST',body:form,signal:aiRequestController.signal});
    let data={};try{data=await response.json()}catch{}
    if(!response.ok||!data.ok||!data.plan)throw new Error(data.error||('HTTP '+response.status));
    pendingAIPlan=normalizeAIPlan(data.plan);completeAnalysisRow(2);renderAIConfirmation(pendingAIPlan);
    window.FocusModeApp.services.storage.setJSON('focus-mode-last-ai-plan',pendingAIPlan);
    setTimeout(()=>setAppScreen('confirm'),500);
  }catch(error){
    console.error('Focus AI request failed',error);
    const message=error.name==='AbortError'?'Die Analyse hat zu lange gedauert. Bitte versuche es erneut.':'AI-Analyse nicht verfügbar: '+(error.message||'Unbekannter Fehler');
    showMissionError(message);
  }finally{clearTimeout(timeout);button?.classList.remove('ai-loading')}
}

function normalizeAIPlan(plan){
  const sessions=Array.isArray(plan.sessions)?plan.sessions.slice(0,6).map((s,i)=>({id:s.id||'session-'+(i+1),title:String(s.title||('Schritt '+(i+1))),task:String(s.task||''),durationMinutes:Math.max(5,Math.min(60,Number(s.durationMinutes)||20)),reason:String(s.reason||''),requiredTools:Array.isArray(s.requiredTools)?s.requiredTools:[],recommendedApps:Array.isArray(s.recommendedApps)?s.recommendedApps:[]})):[];
  if(!sessions.length)throw new Error('Der AI-Plan enthält keine Sitzungen.');
  return {goal:{title:String(plan.goal?.title||'Focus Mission'),subject:String(plan.goal?.subject||'Allgemein'),type:String(plan.goal?.type||'custom'),deadlineText:String(plan.goal?.deadlineText||'Nicht angegeben')},sourceAnalysis:{detectedTasks:String(plan.sourceAnalysis?.detectedTasks||''),detectedPages:String(plan.sourceAnalysis?.detectedPages||''),confidence:Number(plan.sourceAnalysis?.confidence)||0,missingInformation:Array.isArray(plan.sourceAnalysis?.missingInformation)?plan.sourceAnalysis.missingInformation:[],facts:Array.isArray(plan.sourceAnalysis?.facts)?plan.sourceAnalysis.facts:[],estimates:Array.isArray(plan.sourceAnalysis?.estimates)?plan.sourceAnalysis.estimates:[]},estimate:{totalMinutes:Number(plan.estimate?.totalMinutes)||sessions.reduce((a,s)=>a+s.durationMinutes,0),difficulty:String(plan.estimate?.difficulty||'unknown'),explanation:String(plan.estimate?.explanation||'Die Dauer ist eine erste Schätzung und wird nach deinen Sitzungen angepasst.')},sessions};
}
function escapeHTML(value){return String(value).replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]))}
function renderAIConfirmation(plan){
  const rows=[['Fach',plan.goal.subject],['Thema',plan.goal.title],['Frist',plan.goal.deadlineText||'Nicht angegeben'],['Erkannte Aufgaben',plan.sourceAnalysis.detectedTasks||'Nicht eindeutig erkannt'],['Erkannte Seiten',plan.sourceAnalysis.detectedPages||'Nicht eindeutig erkannt'],['Geschätzter Aufwand',formatMinutes(plan.estimate.totalMinutes)+' · '+plan.sessions.length+' Sitzungen']];
  document.getElementById('aiConfirmFields').innerHTML=rows.map(([l,v])=>'<div class="ai-confirm-row"><div class="ai-confirm-label">'+escapeHTML(l)+'</div><div class="ai-confirm-value">'+escapeHTML(v)+'</div></div>').join('');
  const missing=document.getElementById('aiMissingInfo');if(plan.sourceAnalysis.missingInformation.length){missing.innerHTML='<strong>Bitte prüfen:</strong><br>'+plan.sourceAnalysis.missingInformation.map(escapeHTML).join('<br>');missing.classList.remove('hidden')}else missing.classList.add('hidden');
  lucide.createIcons();
}
function formatMinutes(total){const m=Math.max(0,Math.round(Number(total)||0)),h=Math.floor(m/60),r=m%60;return h?(h+'h'+(r?' '+r+'m':'')):(r+' Min.')}
function missionTypeLabel(type){return({exam:'Prüfung',assignment:'Aufgabe',project:'Projekt',custom:'Mission'})[type]||'Mission'}
(function registerAIService(global){const App=global.FocusModeApp;if(App?.core?.registerModule&&!App.core.hasModule('services.ai')){App.services.ai=App.core.registerModule('services.ai',Object.freeze({endpoint:FOCUS_AI_ENDPOINT,adaptEndpoint:FOCUS_AI_ADAPT_ENDPOINT,timeoutMs:120000,analyzeGoal,normalizeAIPlan,missionTypeForAPI,confidenceForAPI,compressMissionImage}),{virtualPath:'src/services/ai-service.js',version:'15.4',status:'ai-boundary'});}})(window);
