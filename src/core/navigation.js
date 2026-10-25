/**
 * Physical module: src/core/navigation.js
 */

let currentSlide = 1;
   const totalSlides = 11;
   let timerInterval = null;
   let timerDuration = 25 * 60; 
   let originalDuration = 25 * 60;
   
   // НОВАЯ ПЕРЕМЕННАЯ: Статус премиума
   let isPremiumUser = false;

   document.addEventListener("DOMContentLoaded", () => {
     lucide.createIcons();
     updateStatusBarTime();
     setInterval(updateStatusBarTime, 1000);
     setAppScreen('home');
   });

   function updateStatusBarTime() {
     const now = new Date();
     let hours = now.getHours().toString().padStart(2, '0');
     let minutes = now.getMinutes().toString().padStart(2, '0');
     document.getElementById('statusBarTime').textContent = `${hours}:${minutes}`;
     const choiceClock=document.getElementById('choiceStatusTime'); if(choiceClock) choiceClock.textContent=`${hours}:${minutes}`;
   }

   function changeSlide(directionOrNumber) {
     let targetSlide = currentSlide;
     if (typeof directionOrNumber === 'number') {
       targetSlide = directionOrNumber;
     } else if (directionOrNumber === 'next') {
       targetSlide = currentSlide < totalSlides ? currentSlide + 1 : totalSlides;
     } else if (directionOrNumber === 'prev') {
       targetSlide = currentSlide > 1 ? currentSlide - 1 : 1;
     }

     for (let i = 1; i <= totalSlides; i++) {
       const slide = document.getElementById(`slide-${i}`);
       if (slide) slide.classList.add('hidden');
     }

     const targetElement = document.getElementById(`slide-${targetSlide}`);
     if (targetElement) targetElement.classList.remove('hidden');

     currentSlide = targetSlide;
     document.getElementById('slideCounter').textContent = `${currentSlide} / ${totalSlides}`;
     syncIphoneWithSlide(currentSlide);
   }

   function nextSlide() { changeSlide('next'); }
   function prevSlide() { changeSlide('prev'); }

   function syncIphoneWithSlide(slideNumber) {
     if (document.body.classList.contains('zen-active')) {
       exitZenMode();
     }

     switch(slideNumber) {
       case 1: setAppScreen('home'); break;
       case 2: setAppScreen('stats'); break;
       case 3: setAppScreen('home'); break;
       case 4: startFocusSession(false); break;
       case 5: 
         startFocusSession(false);
         setTimeout(() => triggerAppAblenkung(), 400);
         break;
       case 6: setAppScreen('score'); break;
       case 7: setAppScreen('premium'); break;
       case 8: setAppScreen('settings'); break;
       case 9: setAppScreen('home'); break;
     }
   }

   function applyAppScreenImmediately(screenName) {
     const screens = document.querySelectorAll('.app-screen');
     screens.forEach(s => s.classList.add('hidden'));

     const target = document.getElementById(`app-screen-${screenName}`);
     if (target) {
         target.classList.remove('hidden');
         
         // ЛОГИКА ДЛЯ ЧАТА
         if (screenName === 'chat') {
           const lockedState = document.getElementById('chat-locked-state');
           const activeState = document.getElementById('chat-active-state');
           if (isPremiumUser) {
             lockedState.classList.add('hidden');
             activeState.classList.remove('hidden');
           } else {
             lockedState.classList.remove('hidden');
             activeState.classList.add('hidden');
           }
         }
         lucide.createIcons();
     }

     const appBody = document.getElementById('appBody');
     const statusBar = document.getElementById('statusBar');
     const homeBar = document.getElementById('homeBar');
     const screenWrapper = document.getElementById('screenWrapper');

     const aiImmersiveScreens = ['analyzing','feedbackprocessing','update'];
     if (screenWrapper) screenWrapper.classList.toggle('ai-immersive', aiImmersiveScreens.includes(screenName));

     if (screenName === 'premium') {
       appBody.classList.remove('bg-slate-50', 'text-slate-900');
       appBody.classList.add('bg-slate-900', 'text-white');
       statusBar.classList.remove('bg-white', 'text-slate-800');
       statusBar.classList.add('bg-slate-900', 'text-white');
       homeBar.classList.remove('bg-white');
       homeBar.classList.add('bg-slate-900');
       homeBar.querySelector('div').classList.add('bg-slate-700');
       homeBar.querySelector('div').classList.remove('bg-slate-300');
       if(screenWrapper) {
         screenWrapper.classList.remove('bg-slate-50');
         screenWrapper.classList.add('bg-slate-900');
       }
     } else {
       appBody.classList.add('bg-slate-50', 'text-slate-900');
       appBody.classList.remove('bg-slate-900', 'text-white');
       statusBar.classList.add('bg-white', 'text-slate-800');
       statusBar.classList.remove('bg-slate-900', 'text-white');
       homeBar.classList.add('bg-white');
       homeBar.classList.remove('bg-slate-900');
       homeBar.querySelector('div').classList.remove('bg-slate-700');
       homeBar.querySelector('div').classList.add('bg-slate-300');
       if(screenWrapper) {
         screenWrapper.classList.add('bg-slate-50');
         screenWrapper.classList.remove('bg-slate-900');
       }
     }

     const buttons = document.querySelectorAll('.app-nav-btn');
     buttons.forEach(btn => {
       btn.classList.remove('bg-indigo-600', 'text-white');
       btn.classList.add('text-slate-600');
     });

     const map = { 'home': 0, 'timer': 1, 'stats': 2, 'score': 3, 'settings': 4, 'premium': 5, 'chat': -1 };
     if (map[screenName] !== undefined && map[screenName] >= 0 && buttons[map[screenName]]) {
       buttons[map[screenName]].classList.add('bg-indigo-600', 'text-white');
       buttons[map[screenName]].classList.remove('text-slate-600', 'bg-slate-50', 'dark:bg-neutral-800');
     }
   }

   /* =========================================
      НОВАЯ ЛОГИКА: Покупка Премиума
      ========================================= */
   function simulatePremiumPurchase() {
     const popup = document.getElementById('screen-popup-success');
     const content = document.getElementById('popupContentSuccess');
     
     // Показываем поп-ап
     popup.classList.remove('hidden');
     requestAnimationFrame(() => {
       popup.classList.add('opacity-100'); popup.classList.remove('opacity-0');
       content.classList.add('scale-100', 'opacity-100'); content.classList.remove('scale-90', 'opacity-0');
     });

     // Через 1.5 секунды устанавливаем премиум и уходим на Home
     setTimeout(() => {
       isPremiumUser = true;
       
       // Скрываем поп-ап
       popup.classList.add('opacity-0'); popup.classList.remove('opacity-100');
       content.classList.add('scale-90', 'opacity-0'); content.classList.remove('scale-100', 'opacity-100');
       
       setTimeout(() => {
         popup.classList.add('hidden');
         setAppScreen('home'); // Переход на Home
       }, 300);
     }, 1800);
   }

   /* =========================================
      НОВАЯ ЛОГИКА: AI Чат
      ========================================= */
   function triggerChatAnalysis() {
     const chatMessages = document.getElementById('chatMessages');
     const promptBtn = document.getElementById('chatPromptBtn');
     
     // Отключаем кнопку, чтобы не нажать дважды
     promptBtn.disabled = true;
     promptBtn.classList.add('opacity-50');

     // 1. Добавляем сообщение пользователя
     const userMsgHtml = `
       <div class="flex justify-end mb-4">
         <div class="bg-indigo-600 text-white p-3.5 rounded-2xl rounded-tr-none shadow-xs text-xs leading-relaxed max-w-[85%]">
           Was kannst du mir empfehlen?
         </div>
       </div>
     `;
     chatMessages.insertAdjacentHTML('beforeend', userMsgHtml);
     scrollToBottom();

     // Функция задержки
     const wait = (ms) => new Promise(resolve => setTimeout(resolve, ms));

     // Секвенция ответов бота
     const runSequence = async () => {
       // --- Сообщение 1 ---
       showTyping();
       await wait(1500);
       hideTyping();
       const botMsg1 = `
         <div class="flex gap-2.5 mb-4">
           <div class="w-7 h-7 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center shrink-0">
             <i data-lucide="bot" class="w-4 h-4"></i>
           </div>
           <div class="bg-white p-3.5 rounded-2xl rounded-tl-none border border-slate-200/60 shadow-xs text-xs text-slate-600 leading-relaxed w-full">
             <p class="mb-2">Basierend auf deinen Daten habe ich folgendes Muster erkannt:</p>
             <p class="font-bold text-slate-800">Du verlierst deinen Fokus meistens zwischen 18:00 und 20:00 Uhr.</p>
             
             <!-- ГРАФИК -->
             <div class="mt-3 bg-slate-50 rounded-xl p-3 border border-slate-100 flex items-end gap-1.5 h-24 relative overflow-hidden">
               <div class="flex-1 bg-indigo-200 h-[80%] rounded-t-sm transition-all"></div>
               <div class="flex-1 bg-indigo-400 h-[90%] rounded-t-sm transition-all"></div>
               <div class="flex-1 bg-rose-400 h-[30%] rounded-t-sm relative transition-all">
                 <div class="absolute -top-5 left-1/2 -translate-x-1/2 text-[8px] bg-rose-100 text-rose-600 px-1 rounded font-bold">18:00</div>
               </div>
               <div class="flex-1 bg-rose-400 h-[20%] rounded-t-sm relative transition-all">
                 <div class="absolute -top-5 left-1/2 -translate-x-1/2 text-[8px] bg-rose-100 text-rose-600 px-1 rounded font-bold">20:00</div>
               </div>
               <div class="flex-1 bg-indigo-300 h-[70%] rounded-t-sm transition-all"></div>
             </div>
             <p class="text-[9px] text-slate-400 mt-1 text-center">Fokus-Niveau im Tagesverlauf</p>
           </div>
         </div>
       `;
       chatMessages.insertAdjacentHTML('beforeend', botMsg1);
       lucide.createIcons();
       scrollToBottom();

       // --- Сообщение 2 ---
       showTyping();
       await wait(2000);
       hideTyping();
       const botMsg2 = `
         <div class="flex gap-2.5 mb-4">
           <div class="w-7 h-7 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center shrink-0">
             <i data-lucide="bot" class="w-4 h-4"></i>
           </div>
           <div class="bg-white p-3.5 rounded-2xl rounded-tl-none border border-slate-200/60 shadow-xs text-xs text-slate-600 leading-relaxed">
             Deine erfolgreichsten Sitzungen dauern exakt <strong class="text-indigo-600">35 Minuten</strong>. 
             <br><br>
             💡 <strong>Mein Tipp:</strong> Versuche kürzere Pausen nach intensiven Lernphasen einzuplanen und reduziere die Sitzungsdauer am Abend.
           </div>
         </div>
       `;
       chatMessages.insertAdjacentHTML('beforeend', botMsg2);
       lucide.createIcons();
       scrollToBottom();
     };

     runSequence();
   }

   function showTyping() {
     const typingIndicator = document.getElementById('typingIndicator');
     typingIndicator.classList.remove('hidden');
     scrollToBottom();
   }

   function hideTyping() {
     const typingIndicator = document.getElementById('typingIndicator');
     typingIndicator.classList.add('hidden');
   }

   function scrollToBottom() {
     const chatMessages = document.getElementById('chatMessages');
     chatMessages.scrollTo({ top: chatMessages.scrollHeight, behavior: 'smooth' });
   }


   /* =========================================
      Существующие функции
      ========================================= */
   let learningFlowActive = false;
let selectedTaskCount = 3;
function openFocusChoice(){
  learningFlowActive=false;
  const overlay=document.getElementById('focusChoiceOverlay');
  overlay.classList.remove('hidden','focus-choice-leave');
  overlay.classList.add('focus-choice-enter');
  document.getElementById('screenWrapper').classList.add('focus-choice-open');
  lucide.createIcons();
}
function closeFocusChoice(){
  const overlay=document.getElementById('focusChoiceOverlay');
  overlay.classList.remove('focus-choice-enter');
  overlay.classList.add('focus-choice-leave');
  document.getElementById('screenWrapper').classList.remove('focus-choice-open');
  setTimeout(()=>overlay.classList.add('hidden'),240);
}
function startQuickFocusFromChoice(){ closeFocusChoice(); setTimeout(()=>startQuickFocus(),180); }
function openAdaptiveFromChoice(){ closeFocusChoice(); setTimeout(()=>openGoalIntake({parent:'home',rootSection:'home',reopenFocusChoice:true}),180); }
function openGoalIntake(context={}){ learningFlowActive=false; if(typeof navigateForward==='function') navigateForward('goal',context); else setAppScreen('goal'); }
function showDemoFilePicker(){ if(typeof navigateForward==='function') navigateForward('filepicker',{parent:'goal'}); else setAppScreen('filepicker'); }
function selectDemoFile(){
  const card=document.getElementById('selectedFileCard');
  if(card){ card.classList.remove('hidden'); card.classList.add('selected'); }
  setAppScreen('goal',{immediate:true});
  requestAnimationFrame(()=>lucide.createIcons());
}
function mockVoiceInput(){
  const area=document.getElementById('goalPrompt'); area.focus();
  const old=area.value; area.value=''; let i=0;
  const t=setInterval(()=>{ area.value=old.slice(0,++i); if(i>=old.length) clearInterval(t); },8);
}
function selectConfidence(btn){ btn.parentElement.querySelectorAll('.flow-chip').forEach(x=>x.classList.remove('active')); btn.classList.add('active'); selectedMissionConfidenceValue=btn.textContent.trim(); updateMissionPlanButton(); }
function analyzeGoal(){
  if(!selectedMissionTypeValue||!selectedMissionConfidenceValue) return;
  setAppScreen('analyzing');
  const rows=[...document.querySelectorAll('#analysisRows .analysis-row')];
  const analysisTimes=[650,1750,3750];
  rows.forEach((r,i)=>setTimeout(()=>{
    r.classList.remove('opacity-40'); r.classList.add('done');
    const icon=r.querySelector('.analysis-icon'); icon.innerHTML='<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-400"></i>'; lucide.createIcons();
  },analysisTimes[i]));
  setTimeout(()=>{ revealCreatedMission(); setAppScreen('plan'); },5350);
}
function selectTaskCount(btn,count){ selectedTaskCount=count; document.querySelectorAll('#taskCountButtons .flow-chip').forEach(x=>x.classList.remove('active')); btn.classList.add('active'); }
function showPlanUpdate(){
  setAppScreen('feedbackprocessing');
  const rows=[...document.querySelectorAll('#feedbackRows .feedback-row')];
  const feedbackTimes=[650,1800,3300];
  rows.forEach((r,i)=>setTimeout(()=>{
    r.classList.remove('opacity-35'); r.classList.add('done');
    const icon=r.querySelector('.feedback-icon');
    icon.innerHTML='<i data-lucide="check" class="w-3.5 h-3.5 text-emerald-300"></i>';
    lucide.createIcons();
  },feedbackTimes[i]));
  setTimeout(()=>setAppScreen('update'),4700);
}
function finishLearningFlow(){
  updateHomeAfterMissionSession();
  learningFlowActive=false;
  const homeBtn=document.querySelector('#app-screen-home > button:last-child');
  if(homeBtn) homeBtn.innerHTML='<i class="w-4 h-4" data-lucide="play"></i> Nächste Mission fortsetzen';
  setAppScreen('home'); lucide.createIcons();
}

   
   function updateTimerUI() {
     const totalSeconds=Math.max(0,timerDuration);
     const hours=Math.floor(totalSeconds/3600);
     const min=Math.floor((totalSeconds%3600)/60).toString().padStart(2,'0');
     const sec=(totalSeconds%60).toString().padStart(2,'0');
     document.getElementById('timerDigits').textContent=hours>0?`${hours}:${min}:${sec}`:`${min}:${sec}`;
     const ratio=originalDuration>0?Math.max(0,Math.min(1,timerDuration/originalDuration)):0;
     const orbit=document.getElementById('timerCircleProgress');
     if(orbit) orbit.style.strokeDashoffset=498-(ratio*498);
     const dot=document.getElementById('orbitProgressDot');
     if(dot){
       const angle=(-Math.PI/2)+(Math.PI*2*(1-ratio));
       dot.setAttribute('cx',(120+105*Math.cos(angle)).toFixed(2));
       dot.setAttribute('cy',(88+45*Math.sin(angle)).toFixed(2));
     }
   }

   function triggerAppAblenkung() {
     const popup = document.getElementById('screen-popup-ablenkung');
     const content = document.getElementById('popupContentAblenkung');
     popup.classList.remove('hidden');
     requestAnimationFrame(() => {
       popup.classList.add('opacity-100'); popup.classList.remove('opacity-0');
       content.classList.add('scale-100', 'opacity-100'); content.classList.remove('scale-95', 'opacity-0');
     });
   }

   function triggerAchievementPopup() {
     const popup = document.getElementById('screen-popup-achievement');
     const content = document.getElementById('popupContentAchievement');
     popup.classList.remove('hidden');
     requestAnimationFrame(() => {
       popup.classList.add('opacity-100'); popup.classList.remove('opacity-0');
       content.classList.add('scale-100', 'opacity-100'); content.classList.remove('scale-90', 'opacity-0');
     });
   }

   function closePopup(type, returnToTimer = false) {
     const popup = document.getElementById(`screen-popup-${type}`);
     const content = document.getElementById(type === 'ablenkung' ? 'popupContentAblenkung' : 'popupContentAchievement');
     
     popup.classList.add('opacity-0'); popup.classList.remove('opacity-100');
     content.classList.add(type === 'ablenkung' ? 'scale-95' : 'scale-90', 'opacity-0'); 
     content.classList.remove('scale-100', 'opacity-100');
     
     setTimeout(() => {
       popup.classList.add('hidden');
       if (type === 'ablenkung') {
         if (returnToTimer) setAppScreen('timer');
         else stopFocusSession();
       }
     }, 300);
   }

   function toggleTheme() {
     document.documentElement.classList.toggle('dark');
   }

   function enterZenMode() {
     document.body.classList.add('zen-active');
     setAppScreen('home');
   }

   function exitZenMode() {
     document.body.classList.remove('zen-active');
   }

let appScreenTransitionToken=0,appScreenAnimation=null;function getAppTransitionDuration(){if(window.matchMedia('(prefers-reduced-motion: reduce)').matches)return 0;return window.matchMedia('(max-width: 980px)').matches?760:520}function animateAppBody(frames,duration,easing){const body=document.getElementById('appBody');if(!body||duration===0)return Promise.resolve();if(appScreenAnimation)appScreenAnimation.cancel();appScreenAnimation=body.animate(frames,{duration,easing,fill:'forwards'});return appScreenAnimation.finished.catch(()=>{})}async function setAppScreen(screenName,options={}){const target=document.getElementById(`app-screen-${screenName}`);if(!target)return;const token=++appScreenTransitionToken,duration=options.immediate?0:getAppTransitionDuration(),visible=document.querySelector('.app-screen:not(.hidden)'),current=visible?.id?.replace('app-screen-','');if(duration===0||!visible||current===screenName){if(appScreenAnimation)appScreenAnimation.cancel();applyAppScreenImmediately(screenName);const body=document.getElementById('appBody');if(body){body.style.opacity='';body.style.transform='';body.style.filter=''}return}const out=Math.round(duration*.44),inside=duration-out;await animateAppBody([{opacity:1,transform:'translate3d(0,0,0) scale(1)',filter:'blur(0px)'},{opacity:.18,transform:'translate3d(0,-10px,0) scale(.992)',filter:'blur(5px)'}],out,'cubic-bezier(.4,0,.6,1)');if(token!==appScreenTransitionToken)return;applyAppScreenImmediately(screenName);await animateAppBody([{opacity:.18,transform:'translate3d(0,12px,0) scale(.992)',filter:'blur(5px)'},{opacity:1,transform:'translate3d(0,0,0) scale(1)',filter:'blur(0px)'}],inside,'cubic-bezier(.16,1,.3,1)');if(token!==appScreenTransitionToken)return;const body=document.getElementById('appBody');if(body){body.style.opacity='';body.style.transform='';body.style.filter=''}}

const chapters=[...document.querySelectorAll('.chapter')],mainMount=document.getElementById('mainMount'),modalMount=document.getElementById('modalMount'),device=document.getElementById('iphoneFrame');let last='home';
function dismissDistractionPopup(){const p=document.getElementById('screen-popup-ablenkung'),c=document.getElementById('popupContentAblenkung');if(!p||p.classList.contains('hidden'))return;p.classList.add('opacity-0');p.classList.remove('opacity-100');if(c){c.classList.add('scale-95','opacity-0');c.classList.remove('scale-100','opacity-100')}setTimeout(()=>p.classList.add('hidden'),300)}
function resetLandingDemo(){
 const overlay=document.getElementById('focusChoiceOverlay');
 const wrapper=document.getElementById('screenWrapper');
 if(overlay){
   overlay.classList.add('hidden');
   overlay.classList.remove('focus-choice-enter','focus-choice-leave');
 }
 if(wrapper) wrapper.classList.remove('focus-choice-open');
 dismissDistractionPopup();
}
function syncLandingDemo(s){
 const mode=s.dataset.demo||'home';
 resetLandingDemo();
 last=(mode==='choice'||mode==='barrier')?'home':mode;
 if(mode==='choice'){
   setAppScreen('home',{immediate:true});
   requestAnimationFrame(()=>requestAnimationFrame(()=>openFocusChoice()));
   return;
 }
 if(mode==='barrier'){learningFlowActive=false;startFocusSession(false);setTimeout(()=>triggerAppAblenkung(),420);return}
 if(mode==='chat'){isPremiumUser=true;setAppScreen('chat');return}
 if(mode==='update'){setAppScreen('update');return}
 setAppScreen(mode);
}
function showLandingBarrier(){learningFlowActive=false;startFocusSession(false);setTimeout(()=>triggerAppAblenkung(),220)}
function activate(s){chapters.forEach(x=>x.classList.toggle('active',x===s));document.querySelectorAll('[data-link],[data-dot]').forEach(a=>a.classList.toggle('active',(a.dataset.link||a.dataset.dot)===s.id));syncLandingDemo(s)}
let activeChapterIndex=-1;
let chapterUpdateQueued=false;
function updateActiveChapter(){
 chapterUpdateQueued=false;
 const focusLine=window.scrollY+(window.innerHeight*.52);
 let nextIndex=0;
 for(let i=0;i<chapters.length;i++){
  const top=chapters[i].offsetTop;
  const bottom=top+chapters[i].offsetHeight;
  if(focusLine>=top&&focusLine<bottom){nextIndex=i;break}
  if(focusLine>=bottom)nextIndex=Math.min(i+1,chapters.length-1);
 }
 if(nextIndex===activeChapterIndex)return;
 activeChapterIndex=nextIndex;
 activate(chapters[nextIndex]);
}
function queueChapterUpdate(){
 if(chapterUpdateQueued)return;
 chapterUpdateQueued=true;
 requestAnimationFrame(updateActiveChapter);
}
window.addEventListener('scroll',queueChapterUpdate,{passive:true});
window.addEventListener('resize',queueChapterUpdate,{passive:true});
queueChapterUpdate();
function openProto(screen='home'){modalMount.appendChild(device);document.getElementById('modal').classList.add('open');document.body.style.overflow='hidden';device.style.opacity='1';device.style.filter='none';if(screen==='chat')isPremiumUser=true;setAppScreen(screen);lucide.createIcons()}
function closeProto(){mainMount.appendChild(device);device.style.opacity='';device.style.filter='';document.getElementById('modal').classList.remove('open');document.body.style.overflow='';setAppScreen(last)}
function go(id){document.getElementById(id).scrollIntoView({behavior:'smooth'})}
function keepPhoneScrollInside(e){const s=e.target.closest('#app-screen-score,#app-screen-premium,#chatMessages,#chat-locked-state,.flow-screen');if(!s||s.scrollHeight<=s.clientHeight+1)return;const top=s.scrollTop<=0,bottom=s.scrollTop+s.clientHeight>=s.scrollHeight-1;if((e.deltaY<0&&!top)||(e.deltaY>0&&!bottom))e.stopPropagation()}
document.addEventListener('wheel',keepPhoneScrollInside,{passive:true});document.addEventListener('keydown',e=>{if(e.key==='Escape')closeProto()});lucide.createIcons();

(function(){
const standalone=matchMedia('(display-mode: standalone)').matches||navigator.standalone===true;
const darkScreens=new Set(['timer','premium','analyzing','feedbackprocessing','update']);
function syncPWAChrome(screen){
 const dark=darkScreens.has(screen),root=document.documentElement;
 root.classList.toggle('pwa-dark-status',dark);
 root.style.setProperty('--pwa-status-bg',dark?'#0f1328':'#f8fafc');
 const theme=document.querySelector('meta[name="theme-color"]');if(theme)theme.content=dark?'#0f1328':'#f8fafc';
 const apple=document.getElementById('appleStatusBarStyle');if(apple)apple.content=dark?'black-translucent':'default';
}
document.addEventListener('DOMContentLoaded',()=>{
 document.body.classList.add('pwa-app');syncPWAChrome('home');
 const original=window.applyAppScreenImmediately;
 if(typeof original==='function'){window.applyAppScreenImmediately=function(screen){const result=original.apply(this,arguments);syncPWAChrome(screen);return result}}
 // Initial screen is resolved once by Session Recovery bootstrap.
 const h=document.getElementById('pwaInstallHelp'),c=document.getElementById('pwaInstallClose');
 if(/iPad|iPhone|iPod/.test(navigator.userAgent)&&!standalone&&window.FocusModeApp.services.storage.getText('focus-pwa-help-v11')!=='1')setTimeout(()=>h.classList.add('open'),700);
 c.onclick=()=>{h.classList.remove('open');window.FocusModeApp.services.storage.setText('focus-pwa-help-v11','1')};
});
if('serviceWorker'in navigator)addEventListener('load',()=>navigator.serviceWorker.register('./service-worker.js'));
})();

let ucFrame=null,ucState=null,ucActive=false,ucEndsAt=null;
function syncUcData(){
 const s=window.activeSession,title=document.getElementById('activeTaskLabel'),task=document.getElementById('timerTaskValue'),mode=document.getElementById('currentFocusModeLabel'),dur=document.getElementById('ucDuration'),prog=document.getElementById('ucProgressText'),bar=document.getElementById('ucProgressBar');
 if(!s)return;
 title.textContent=s.title;task.textContent=s.task;mode.textContent=s.ui.modeLabel;dur.textContent=s.plannedMinutes+' Min.';
 const current=s.ui.progressCurrent,total=s.ui.progressTotal;prog.textContent=current+' / '+total;bar.style.width=Math.min(100,current/total*100)+'%';
}
function hideUcTask(){document.getElementById('ucTaskPopup')?.classList.remove('open')}
function seedOrbit(n,front){const out=[];for(let i=0;i<n;i++){const a=Math.PI*2*i/n+(Math.random()-.5)*.035;out.push({a,offset:(Math.random()-.5)*13,size:.35+Math.random()*1.25,alpha:.18+Math.random()*.66,tw:Math.random()*6.28,front})}return out}
function seedGalaxy(n){const out=[];for(let i=0;i<n;i++){const arm=i%3,r=7+Math.pow(Math.random(),.72)*83,a=arm*Math.PI*2/3+r*.055+(Math.random()-.5)*.55;out.push({r,a,size:.3+Math.random()*.85,alpha:.08+Math.random()*.33})}return out}
function initUc(){const scene=document.getElementById('ucScene'),back=document.getElementById('ucBackCanvas'),front=document.getElementById('ucFrontCanvas');if(!scene||!back||!front)return;const rect=scene.getBoundingClientRect(),dpr=Math.min(devicePixelRatio||1,2),reduced=matchMedia('(prefers-reduced-motion: reduce)').matches,small=rect.width<390;for(const c of [back,front]){c.width=Math.round(rect.width*dpr);c.height=Math.round(rect.height*dpr);c.style.width=rect.width+'px';c.style.height=rect.height+'px';c.getContext('2d').setTransform(dpr,0,0,dpr,0,0)}ucState={rect,back:back.getContext('2d'),front:front.getContext('2d'),dust:seedOrbit(reduced?120:(small?220:290)),galaxy:seedGalaxy(reduced?60:(small?105:145)),stars:Array.from({length:small?9:12},()=>({x:.08+Math.random()*.84,y:.14+Math.random()*.64,size:.7+Math.random()*1.55,alpha:.25+Math.random()*.55,tw:Math.random()*6.28})),start:performance.now(),last:performance.now(),reduced};if(ucFrame)cancelAnimationFrame(ucFrame);drawUc(performance.now())}
function dot(ctx,x,y,size,alpha,blur=5,color='221,198,255'){ctx.beginPath();ctx.fillStyle=`rgba(${color},${alpha})`;ctx.shadowColor=`rgba(${color},.8)`;ctx.shadowBlur=blur;ctx.arc(x,y,size,0,Math.PI*2);ctx.fill()}
function drawUc(now){const s=ucState;if(!s||!ucActive)return;const{rect,back,front}=s;back.clearRect(0,0,rect.width,rect.height);front.clearRect(0,0,rect.width,rect.height);const cx=rect.width*.5,cy=rect.height*.48,planet=Math.min(rect.width*.56,225),elapsed=(now-s.start)/1000,slow=s.reduced?0:elapsed*Math.PI*2/92,gal=s.reduced?0:elapsed*Math.PI*2/230;
// Local galaxy, strictly behind planet and only inside scene.
for(const g of s.galaxy){const a=g.a+gal,r=g.r,x=cx+66+Math.cos(a)*r*1.15,y=cy-50+Math.sin(a)*r*.48;const fade=Math.max(0,1-r/98);dot(back,x,y,g.size,g.alpha*(.35+.65*fade),3,'180,155,255')}
// Only a few calm background stars.
for(const st of s.stars){st.tw+=.012;dot(back,rect.width*st.x,rect.height*st.y,st.size,st.alpha*(.72+.28*Math.sin(st.tw)),7)}
// One coherent particle orbit. Back half is dim, front half is bright.
for(const p of s.dust){p.tw+=.015;const a=p.a+slow,rx=rect.width*.43+p.offset,ry=rect.height*.175+p.offset*.18,dustCy=cy-18,x=cx+Math.cos(a)*rx,y=dustCy+Math.sin(a)*ry;const isFront=Math.sin(a)>0,ctx=isFront?front:back,depth=isFront?(.68+.32*Math.sin(a)):(.07+.08*Math.abs(Math.sin(a))),cluster=.55+.45*Math.pow(Math.max(0,Math.cos(a-.8)),2),twinkle=.82+.18*Math.sin(p.tw);dot(ctx,x,y,p.size*(isFront?1.05:.78),p.alpha*depth*cluster*twinkle,isFront?5:2.5)}
s.last=now;ucFrame=requestAnimationFrame(drawUc)}
function startUc(){ucActive=true;syncUcData();requestAnimationFrame(initUc)}function stopUc(){ucActive=false;if(ucFrame){cancelAnimationFrame(ucFrame);ucFrame=null}}
const oldApplyUc=applyAppScreenImmediately;applyAppScreenImmediately=function(screen){const r=oldApplyUc.apply(this,arguments);screen==='timer'?startUc():stopUc();return r};



document.addEventListener('visibilitychange',()=>{if(document.hidden)stopUc();else if(!document.getElementById('app-screen-timer')?.classList.contains('hidden')){if(ucEndsAt&&timerInterval){timerDuration=Math.max(0,Math.ceil((ucEndsAt-Date.now())/1000));updateTimerUI();if(timerDuration<=0)finishSession({status:'completed',reason:'timer_finished'})}startUc()}});window.addEventListener('resize',()=>{if(ucActive)requestAnimationFrame(initUc)},{passive:true});

/* =========================================
   V17 NAVIGATION CLARITY CONTROLLER
   ========================================= */
(function installNavigationControllerV17(global) {
  'use strict';

  const ROOT_SECTIONS = Object.freeze(['home', 'missions', 'history', 'settings']);
  const SCREEN_TO_ROOT_SECTION = Object.freeze({
    home: 'home',
    missions: 'missions',
    plan: 'missions',
    history: 'history',
    stats: 'history',
    settings: 'settings',
    data: 'settings',
    score: 'history'
  });

  const LOGICAL_PARENT_MAP = Object.freeze({
    data: 'settings',
    stats: 'history',
    plan: 'missions',
    confirm: 'goal',
    filepicker: 'goal',
    distractions: 'quicksetup',
    quicksetup: 'home',
    goal: 'home',
    score: 'home',
    chat: 'home',
    premium: 'settings'
  });

  const navState = {
    currentScreen: 'home',
    currentRootSection: 'home',
    previousScreen: null,
    navigationStack: [],
    activeBottomNavigationSection: 'home'
  };

  function getVisibleScreenName() {
    const visible = global.document?.querySelector('.app-screen:not(.hidden)');
    if (!visible || !visible.id) return navState.currentScreen || 'home';
    return visible.id.replace('app-screen-', '');
  }

  function isCriticalModalOrSheetOpen() {
    const doc = global.document;
    if (!doc) return false;
    const wrapper = doc.getElementById('screenWrapper');
    const choiceOverlay = doc.getElementById('focusChoiceOverlay');
    if (wrapper?.classList.contains('focus-choice-open') || (choiceOverlay && !choiceOverlay.classList.contains('hidden'))) {
      return true;
    }
    const missionSheet = doc.getElementById('missionSheetV116');
    if (missionSheet?.classList.contains('open')) {
      return true;
    }
    const dataScreen = doc.getElementById('app-screen-data');
    if (dataScreen && !dataScreen.classList.contains('hidden')) {
      const importPreview = doc.getElementById('dataImportPreviewCard');
      const resetConfirm = doc.getElementById('dataResetConfirmCard');
      if ((importPreview && !importPreview.classList.contains('hidden')) ||
          (resetConfirm && !resetConfirm.classList.contains('hidden'))) {
        return true;
      }
    }
    const popups = [
      'blockedAppsPopup',
      'screen-popup-ablenkung',
      'screen-popup-achievement',
      'screen-popup-success'
    ];
    for (const id of popups) {
      const el = doc.getElementById(id);
      if (el && !el.classList.contains('hidden')) return true;
    }
    const ucTask = doc.getElementById('ucTaskPopup');
    if (ucTask?.classList.contains('open')) return true;
    return false;
  }

  function showBottomNavigation(activeSection = navState.currentRootSection || 'home') {
    const resolvedSection = ROOT_SECTIONS.includes(activeSection) ? activeSection : 'home';
    navState.activeBottomNavigationSection = resolvedSection;

    const doc = global.document;
    const nav = doc?.getElementById('bottomNavigationV17');
    const wrapper = doc?.getElementById('screenWrapper');
    if (wrapper) wrapper.classList.add('has-bottom-nav');
    doc?.body?.classList.add('has-bottom-nav');

    if (!nav) return;
    nav.classList.remove('hidden');
    nav.removeAttribute('aria-hidden');
    nav.removeAttribute('inert');

    nav.querySelectorAll('.fm-nav-item').forEach(btn => {
      const isMatch = btn.dataset.navSection === resolvedSection;
      btn.classList.toggle('active', isMatch);
      if (isMatch) {
        btn.setAttribute('aria-current', 'page');
      } else {
        btn.removeAttribute('aria-current');
      }
    });
  }

  function hideBottomNavigation() {
    const doc = global.document;
    const nav = doc?.getElementById('bottomNavigationV17');
    const wrapper = doc?.getElementById('screenWrapper');
    if (wrapper) wrapper.classList.remove('has-bottom-nav');
    doc?.body?.classList.remove('has-bottom-nav');

    if (!nav) return;
    nav.classList.add('hidden');
    nav.setAttribute('aria-hidden', 'true');
    nav.setAttribute('inert', '');
  }

  function resolveActiveRootForScreen(screen) {
    if (screen === 'score') {
      return navState.currentRootSection === 'history' ? 'history' : 'home';
    }
    return SCREEN_TO_ROOT_SECTION[screen] || null;
  }

  function syncBottomNavigationForScreen(screen) {
    const actualScreen = screen || getVisibleScreenName();
    navState.currentScreen = actualScreen;

    if (isCriticalModalOrSheetOpen()) {
      hideBottomNavigation();
      return false;
    }

    const activeSection = resolveActiveRootForScreen(actualScreen);
    if (!activeSection) {
      hideBottomNavigation();
      return false;
    }

    navState.currentRootSection = activeSection;
    showBottomNavigation(activeSection);
    return true;
  }

  function hasRunningFocusSessionOnTimer() {
    const s = global.activeSession;
    return Boolean(s && s.status === 'running' && (getVisibleScreenName() === 'timer' || navState.pendingScreen === 'timer'));
  }

  function navigateToRoot(section) {
    if (!ROOT_SECTIONS.includes(section)) return false;
    if (hasRunningFocusSessionOnTimer()) {
      return false;
    }

    const choiceOverlay = global.document?.getElementById('focusChoiceOverlay');
    if (choiceOverlay && !choiceOverlay.classList.contains('hidden') && typeof global.closeFocusChoice === 'function') {
      global.closeFocusChoice();
    }
    if (typeof global.closeMissionSheetV116 === 'function') {
      global.closeMissionSheetV116();
    }

    if (section === 'missions') {
      if (typeof global.ensureMissionManagementScreenV116 === 'function') {
        global.ensureMissionManagementScreenV116();
      }
      if (typeof global.renderMissionManagementV116 === 'function') {
        global.renderMissionManagementV116();
      }
    }

    const fromScreen = getVisibleScreenName();
    navState.previousScreen = fromScreen;
    navState.currentRootSection = section;
    navState.activeBottomNavigationSection = section;
    navState.navigationStack = [];

    if (fromScreen === section) {
      syncBottomNavigationForScreen(section);
      return true;
    }

    global.setAppScreen(section, { immediate: true, __fromNavController: true });
    syncBottomNavigationForScreen(section);
    return true;
  }

  function navigateForward(screen, context = {}) {
    if (!screen) return false;
    const fromScreen = context.parent || getVisibleScreenName();
    const rootSection = context.rootSection || resolveActiveRootForScreen(screen) || navState.currentRootSection || 'home';

    if (fromScreen && fromScreen !== screen) {
      const top = navState.navigationStack[navState.navigationStack.length - 1];
      if (!top || top.screen !== fromScreen) {
        navState.navigationStack.push({
          screen: fromScreen,
          rootSection: navState.currentRootSection || 'home',
          reopenFocusChoice: Boolean(context.reopenFocusChoice)
        });
      }
      navState.previousScreen = fromScreen;
    }

    if (ROOT_SECTIONS.includes(rootSection)) {
      navState.currentRootSection = rootSection;
    }

    if (screen === 'missions') {
      global.ensureMissionManagementScreenV116?.();
      global.renderMissionManagementV116?.();
    }

    const options = { immediate: true, ...(context.options || {}), __fromNavController: true };
    global.setAppScreen(screen, options);
    return true;
  }

  function replaceScreen(screen, context = {}) {
    if (!screen) return false;
    const rootSection = context.rootSection || resolveActiveRootForScreen(screen) || navState.currentRootSection || 'home';
    if (ROOT_SECTIONS.includes(rootSection)) {
      navState.currentRootSection = rootSection;
    }
    const options = { immediate: true, ...(context.options || {}), __fromNavController: true };
    global.setAppScreen(screen, options);
    return true;
  }

  function navigateBack() {
    const current = getVisibleScreenName();

    // Active session guard: never silently leave the running timer
    if (current === 'timer') {
      if (global.activeSession?.status === 'running') {
        if (typeof global.triggerAppAblenkung === 'function') {
          global.triggerAppAblenkung();
        }
        return false;
      }
      return navigateToRoot('home');
    }

    // Root screens do not use Back navigation
    if (ROOT_SECTIONS.includes(current)) {
      return false;
    }

    // Explicit logical parent contracts (Section 11)
    if (current === 'data') {
      if (typeof global.cancelPendingDataImport === 'function') global.cancelPendingDataImport();
      if (typeof global.hideResetConfirmCard === 'function') global.hideResetConfirmCard();
      return navigateToRoot('settings');
    }

    if (current === 'stats') {
      return navigateToRoot('history');
    }

    if (current === 'confirm' || current === 'filepicker') {
      global.setAppScreen('goal', { immediate: true, __fromNavController: true });
      return true;
    }

    if (current === 'distractions') {
      if (typeof global.returnFromDistractionPicker === 'function') {
        global.returnFromDistractionPicker();
        return true;
      }
      global.setAppScreen('quicksetup', { immediate: true, __fromNavController: true });
      return true;
    }

    if (current === 'quicksetup') {
      navState.navigationStack = [];
      global.setAppScreen('home', { immediate: true, __fromNavController: true });
      setTimeout(() => {
        if (typeof global.openFocusChoice === 'function') {
          global.openFocusChoice();
        }
      }, 30);
      return true;
    }

    if (current === 'plan') {
      return navigateToRoot('missions');
    }

    // Stack-based return for goal, score, premium, chat, etc.
    if (navState.navigationStack.length > 0) {
      const prevEntry = navState.navigationStack.pop();
      if (prevEntry && prevEntry.screen) {
        navState.previousScreen = current;
        if (ROOT_SECTIONS.includes(prevEntry.rootSection)) {
          navState.currentRootSection = prevEntry.rootSection;
        }
        if (prevEntry.screen === 'missions') {
          return navigateToRoot('missions');
        }
        global.setAppScreen(prevEntry.screen, { immediate: true, __fromNavController: true });
        if (prevEntry.reopenFocusChoice && prevEntry.screen === 'home') {
          setTimeout(() => global.openFocusChoice?.(), 30);
        }
        return true;
      }
    }

    const fallbackParent = LOGICAL_PARENT_MAP[current] || 'home';
    if (ROOT_SECTIONS.includes(fallbackParent)) {
      return navigateToRoot(fallbackParent);
    }
    global.setAppScreen(fallbackParent, { immediate: true, __fromNavController: true });
    return true;
  }

  function wrapApplyScreenV17() {
    const currentApply = global.applyAppScreenImmediately;
    if (typeof currentApply === 'function' && !currentApply.__v17NavWrapped) {
      function applyWithNavSyncV17(screenName) {
        const prevVisible = getVisibleScreenName();
        const result = currentApply.apply(this, arguments);
        const actualScreen = getVisibleScreenName();
        if (prevVisible && prevVisible !== actualScreen) {
          navState.previousScreen = prevVisible;
        }
        navState.currentScreen = actualScreen;
        navState.pendingScreen = null;
        syncBottomNavigationForScreen(actualScreen);
        return result;
      }
      applyWithNavSyncV17.__v17NavWrapped = true;
      global.applyAppScreenImmediately = applyWithNavSyncV17;
    }

    const currentSet = global.setAppScreen;
    if (typeof currentSet === 'function' && !currentSet.__v17SetWrapped) {
      function setAppScreenWithNavV17(screenName, options = {}) {
        navState.pendingScreen = screenName;
        if (!resolveActiveRootForScreen(screenName)) {
          hideBottomNavigation();
        }
        return currentSet.apply(this, arguments);
      }
      setAppScreenWithNavV17.__v17SetWrapped = true;
      global.setAppScreen = setAppScreenWithNavV17;
    }

    const currentOpenQuick = global.openQuickSetupFromChoice;
    if (typeof currentOpenQuick === 'function' && !currentOpenQuick.__v17QuickWrapped) {
      function openQuickSetupFromChoiceV17() {
        navState.pendingScreen = 'quicksetup';
        hideBottomNavigation();
        global.closeFocusChoice?.();
        setTimeout(() => {
          navigateForward('quicksetup', {
            parent: 'home',
            rootSection: 'home',
            reopenFocusChoice: true,
            options: { immediate: true }
          });
        }, 180);
      }
      openQuickSetupFromChoiceV17.__v17QuickWrapped = true;
      global.openQuickSetupFromChoice = openQuickSetupFromChoiceV17;
    }
  }

  wrapApplyScreenV17();

  // Wrap openFocusChoice and closeFocusChoice to hide/restore bottom navigation
  const origOpenChoice = global.openFocusChoice;
  if (typeof origOpenChoice === 'function') {
    global.openFocusChoice = function openFocusChoiceWithNavV17() {
      const res = origOpenChoice.apply(this, arguments);
      hideBottomNavigation();
      return res;
    };
  }

  const origCloseChoice = global.closeFocusChoice;
  if (typeof origCloseChoice === 'function') {
    global.closeFocusChoice = function closeFocusChoiceWithNavV17() {
      const res = origCloseChoice.apply(this, arguments);
      setTimeout(() => {
        if (navState.pendingScreen && !resolveActiveRootForScreen(navState.pendingScreen)) {
          hideBottomNavigation();
          return;
        }
        syncBottomNavigationForScreen(getVisibleScreenName());
      }, 250);
      return res;
    };
  }

  global.navigateToRoot = navigateToRoot;
  global.navigateForward = navigateForward;
  global.navigateBack = navigateBack;
  global.replaceScreen = replaceScreen;
  global.showBottomNavigation = showBottomNavigation;
  global.hideBottomNavigation = hideBottomNavigation;
  global.syncBottomNavigationForScreen = syncBottomNavigationForScreen;
  global.installNavApplyHookV17 = wrapApplyScreenV17;
  global.__navigationStateV17 = navState;
})(window);

