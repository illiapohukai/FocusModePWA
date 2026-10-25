/* v13 Session Persistence Migration
   Routes Session Core persistence writes through FocusModeApp.repositories.sessions.
   Recovery hydration and History UI reads remain compatibility-owned until v13.1. */
(function installSessionPersistenceMigrationV13(global){
  'use strict';
  const App=global.FocusModeApp;
  const sessions=App?.repositories?.sessions;
  if(!sessions)throw new Error('v12.9 Session Repository must be installed before v13 migration.');
  if(App.compatibility?.sessionPersistenceMigration?.version==='13')return;

  const legacyHistory=global.sessionRepository;
  const clone=value=>value==null?value:(typeof global.structuredClone==='function'?global.structuredClone(value):JSON.parse(JSON.stringify(value)));
  const terminalStates=new Set(['completed','cancelled','interrupted']);
  let terminalCommitInFlight=new Set();

  function refreshSessionConsumersV13(){
    try{global.refreshSessionHistoryUI?.()}catch(error){console.warn('[SessionMigration]',{event:'history_ui_refresh_failed',message:error?.message||'unknown'})}
    try{global.refreshHomeStats?.()}catch(error){console.warn('[SessionMigration]',{event:'home_stats_refresh_failed',message:error?.message||'unknown'})}
    try{global.refreshStatsScreen?.()}catch(error){console.warn('[SessionMigration]',{event:'stats_refresh_failed',message:error?.message||'unknown'})}
  }
  function isTerminalSessionV13(session){return Boolean(session&&terminalStates.has(String(session.status||'')))}
  function terminalCommitV13(session){
    if(!isTerminalSessionV13(session))return null;
    const check=sessions.validate(session,{terminal:true});
    if(!check.valid){console.error('[SessionMigration]',{event:'terminal_validation_failed',sessionId:session?.id||null,errors:check.errors});return null}
    const token=String(session.id)+'::'+String(session.status);
    if(terminalCommitInFlight.has(token)){console.info('[SessionMigration]',{event:'terminal_duplicate_blocked',sessionId:session.id,status:session.status});return sessions.listHistory().find(x=>x.id===session.id&&x.status===session.status)||clone(session)}
    terminalCommitInFlight.add(token);
    try{
      const history=sessions.appendHistory(session);
      if(!history)throw new Error('history_append_failed');
      const last=sessions.saveLast(history);
      if(!last)throw new Error('last_session_save_failed');
      if(!sessions.clearActive(session.id))throw new Error('active_session_clear_failed');
      if(global.activeSession?.id===session.id){global.activeSession=null;try{activeSession=null}catch{}}
      refreshSessionConsumersV13();
      console.info('[SessionMigration]',{event:'terminal_commit_succeeded',sessionId:session.id,status:session.status});
      return clone(last);
    }catch(error){
      console.error('[SessionMigration]',{event:'terminal_commit_failed',sessionId:session.id,status:session.status,message:error?.message||'unknown'});
      return null;
    }finally{terminalCommitInFlight.delete(token)}
  }

  /* Canonical active-session writer used by Session Core and existing Recovery code. */
  global.setActiveSession=function setActiveSessionV13(session){
    if(session==null){
      sessions.clearActive(global.activeSession?.id||null);
      try{activeSession=null}catch{}
      global.activeSession=null;
      return null;
    }
    const saved=sessions.saveActive(session);
    if(!saved)return null;
    const cloned=clone(saved);
    try{activeSession=cloned}catch{}
    global.activeSession=cloned;
    return clone(saved);
  };
  global.getActiveSession=function getActiveSessionV13(){return clone(global.activeSession||sessions.loadActive())};
  global.updateActiveSession=function updateActiveSessionV13(patch){const current=global.getActiveSession();return current?global.setActiveSession({...current,...clone(patch)}):null};
  global.saveSessionSnapshot=function saveSessionSnapshotV13(session){return sessions.saveLast(session)};

  /* Checkpoints retain existing elapsed-time calculations, but persistence crosses the repository boundary. */
  const checkpointBeforeV13=global.checkpointActiveSession;
  if(typeof checkpointBeforeV13==='function'){
    global.checkpointActiveSession=function checkpointActiveSessionV13(reason='interval',persist=true){
      const snapshot=checkpointBeforeV13.call(this,reason,false);
      if(!snapshot||!persist)return snapshot;
      const saved=sessions.checkpoint(snapshot);
      if(!saved){console.error('[SessionMigration]',{event:'checkpoint_failed',sessionId:snapshot.id,reason});return null}
      const cloned=clone(saved);
      try{activeSession=cloned}catch{}
      global.activeSession=cloned;
      recoveryLog?.('active_session_checkpointed',{sessionId:saved.id,reason,elapsedMinutes:Math.floor((Number(saved.elapsedMs)||0)/60000)});
      return clone(saved);
    };
  }

  /* Preserve the legacy History API while making its writes repository-owned. */
  if(legacyHistory&&typeof legacyHistory.save==='function'){
    legacyHistory.save=function saveHistoryThroughSessionsV13(session){
      const saved=terminalCommitV13(session);
      return saved?clone(saved):null;
    };
    legacyHistory.getAll=function getAllHistoryThroughSessionsV13(){return sessions.listHistory()};
    legacyHistory.getById=function getByIdHistoryThroughSessionsV13(id){return sessions.listHistory().find(x=>x.id===id)||null};
  }

  /* Recovery's finalization helper now uses the ordered commit without altering recovery hydration. */
  global.saveFinalSessionOnceV112=function saveFinalSessionOnceV13(final){
    const existing=sessions.listHistory().find(x=>x.id===final.id&&x.status===final.status);
    if(existing){sessions.saveLast(existing);sessions.clearActive(final.id);recoveryLog?.('active_session_finalization_reused',{sessionId:final.id});return clone(existing)}
    const saved=terminalCommitV13(final);
    if(!saved)return null;
    recoveryLog?.('active_session_finalization_saved',{sessionId:final.id,type:final.type});
    return clone(saved);
  };

  App.services.sessionPersistence=Object.freeze({terminalCommit:terminalCommitV13,refreshConsumers:refreshSessionConsumersV13});
  App.compatibility.sessionPersistenceMigration=Object.freeze({version:'13',sessionCoreWritesMigrated:true,checkpointWritesMigrated:true,terminalWritesMigrated:true,lastSessionWritesMigrated:true,recoveryReadsMigrated:false,historyUIReadsMigrated:false,keysRenamed:0,formatsChanged:0});
  console.info('[SessionMigration]',{event:'migration_ready',version:'13',repository:'FocusModeApp.repositories.sessions'});
})(window);
