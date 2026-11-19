/**
 * Virtual module: src/services/storage-service.js
 * Version: v12.3 Storage Boundary
 *
 * This module establishes the only future persistence boundary.
 * Legacy code remains authoritative during this checkpoint and is not rewired yet.
 */
(function initializeStorageService(global) {
  'use strict';

  const App = global.FocusModeApp;

  if (!App?.core?.registerModule) {
    throw new Error('Architecture Shell must be initialized before Storage Service.');
  }

  const SERVICE_NAME = 'services.storage';

  if (App.core.hasModule(SERVICE_NAME)) {
    console.warn('[StorageService]', {
      event: 'module_already_registered',
      module: SERVICE_NAME
    });
    return;
  }

  /* v17.17: last failed write (code/key/name only, never values) for user-facing quota messages. */
  let lastWriteError = null;

  /* All keys this app owns. Used for predictable full deletion; nothing else is touched. */
  const APP_KEY_PREFIXES = Object.freeze(['focus-mode', 'focusmode-', 'focus-pwa-', 'focus_lang']);

  function storageAvailable() {
    try {
      const storage = global.localStorage;
      return Boolean(storage && typeof storage.getItem === 'function');
    } catch (error) {
      return false;
    }
  }

  function normalizeKey(key) {
    const normalized = String(key ?? '').trim();

    if (!normalized) {
      throw new TypeError('Storage key must be a non-empty string.');
    }

    return normalized;
  }

  function cloneValue(value) {
    if (value === undefined || value === null) {
      return value;
    }

    if (typeof global.structuredClone === 'function') {
      return global.structuredClone(value);
    }

    return JSON.parse(JSON.stringify(value));
  }

  function result(ok, value = null, error = null) {
    return Object.freeze({ ok, value, error });
  }

  function readRaw(key) {
    const normalizedKey = normalizeKey(key);

    if (!storageAvailable()) {
      return result(false, null, {
        code: 'storage_unavailable',
        key: normalizedKey
      });
    }

    try {
      return result(true, global.localStorage.getItem(normalizedKey), null);
    } catch (error) {
      return result(false, null, {
        code: 'storage_read_failed',
        key: normalizedKey,
        name: error?.name || 'Error'
      });
    }
  }

  function writeRaw(key, value) {
    const normalizedKey = normalizeKey(key);

    if (!storageAvailable()) {
      return result(false, null, {
        code: 'storage_unavailable',
        key: normalizedKey
      });
    }

    try {
      global.localStorage.setItem(normalizedKey, String(value));
      return result(true, true, null);
    } catch (error) {
      const failure = {
        code: isQuotaError(error) ? 'storage_quota_exceeded' : 'storage_write_failed',
        key: normalizedKey,
        name: error?.name || 'Error'
      };
      lastWriteError = Object.freeze({ ...failure, at: Date.now() });
      return result(false, null, failure);
    }
  }

  /* v17.17: quota errors are named differently across engines (Chromium/WebKit/Gecko). */
  function isQuotaError(error) {
    if (!error) return false;
    const name = String(error.name || '');
    return name === 'QuotaExceededError' ||
      name === 'NS_ERROR_DOM_QUOTA_REACHED' ||
      error.code === 22 ||
      error.code === 1014;
  }

  function getText(key, fallback = null) {
    const readResult = readRaw(key);

    if (!readResult.ok || readResult.value === null) {
      return cloneValue(fallback);
    }

    return readResult.value;
  }

  function setText(key, value) {
    return writeRaw(key, value).ok;
  }

  function getJSON(key, fallback = null, options = {}) {
    const normalizedKey = normalizeKey(key);
    const readResult = readRaw(normalizedKey);

    if (!readResult.ok || readResult.value === null) {
      return cloneValue(fallback);
    }

    try {
      return cloneValue(JSON.parse(readResult.value));
    } catch (error) {
      if (options.backupKey) {
        backup(normalizedKey, options.backupKey);
      }

      if (options.removeCorrupt === true) {
        remove(normalizedKey);
      }

      console.warn('[StorageService]', {
        event: 'json_parse_failed',
        key: normalizedKey,
        error: error?.name || 'SyntaxError'
      });

      return cloneValue(fallback);
    }
  }

  function setJSON(key, value) {
    const normalizedKey = normalizeKey(key);

    try {
      return writeRaw(normalizedKey, JSON.stringify(value)).ok;
    } catch (error) {
      console.error('[StorageService]', {
        event: 'json_serialize_failed',
        key: normalizedKey,
        error: error?.name || 'Error'
      });
      return false;
    }
  }

  function remove(key) {
    const normalizedKey = normalizeKey(key);

    if (!storageAvailable()) {
      return false;
    }

    try {
      global.localStorage.removeItem(normalizedKey);
      return true;
    } catch (error) {
      console.error('[StorageService]', {
        event: 'storage_remove_failed',
        key: normalizedKey,
        error: error?.name || 'Error'
      });
      return false;
    }
  }

  function has(key) {
    const readResult = readRaw(key);
    return readResult.ok && readResult.value !== null;
  }

  function backup(sourceKey, backupKey) {
    const normalizedSource = normalizeKey(sourceKey);
    const normalizedBackup = normalizeKey(backupKey);
    const readResult = readRaw(normalizedSource);

    if (!readResult.ok || readResult.value === null) {
      return false;
    }

    return writeRaw(normalizedBackup, readResult.value).ok;
  }

  function listAppKeys() {
    if (!storageAvailable()) return [];
    const keys = [];
    try {
      const storage = global.localStorage;
      for (let i = 0; i < storage.length; i += 1) {
        const key = storage.key(i);
        if (typeof key === 'string' && APP_KEY_PREFIXES.some(prefix => key.startsWith(prefix))) {
          keys.push(key);
        }
      }
    } catch (error) {
      return [];
    }
    return keys;
  }

  function getLastWriteError() {
    return lastWriteError;
  }

  const storageService = Object.freeze({
    isAvailable: storageAvailable,
    isQuotaError,
    listAppKeys,
    getLastWriteError,
    getText,
    setText,
    getJSON,
    setJSON,
    remove,
    has,
    backup
  });

  App.services.storage = App.core.registerModule(
    SERVICE_NAME,
    storageService,
    {
      virtualPath: 'src/services/storage-service.js',
      version: '16.0',
      status: 'canonical-storage-boundary',
      legacyRuntimeAuthoritative: true
    }
  );

  App.compatibility.storageBoundary = Object.freeze({
    serviceAvailable: true,
    legacyCallsMigrated: 16,
    legacyCallsRemaining: 30,
    storageKeysRenamed: 0,
    v16DataStabilityReady: true
  });

  console.info('[StorageService]', {
    event: 'boundary_ready',
    version: '16.0',
    available: storageService.isAvailable(),
    legacyCallsMigrated: 0
  });
})(window);
