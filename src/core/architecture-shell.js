/**
 * Virtual module: src/core/architecture-shell.js
 * Version: v12.2 Architecture Shell
 *
 * This shell introduces the future single-file module namespace only.
 * It does not take ownership of legacy behavior yet.
 */
(function initializeArchitectureShell(global) {
  'use strict';

  if (global.FocusModeApp) {
    console.warn('[Architecture]', {
      event: 'shell_already_initialized',
      version: global.FocusModeApp.version || null
    });
    return;
  }

  const moduleRegistry = new Map();
  const lifecycle = {
    phase: 'legacy-runtime',
    ready: false,
    bootstrapOwner: 'legacy',
    initializedAt: Date.now()
  };

  const architecture = Object.freeze({
    distribution: 'single-html',
    mode: 'compatibility',
    migrationStage: 'architecture-shell',
    legacyRuntimeAuthoritative: true
  });

  function registerModule(name, moduleDefinition, metadata = {}) {
    const moduleName = String(name || '').trim();

    if (!moduleName) {
      throw new Error('Module name is required.');
    }

    if (moduleRegistry.has(moduleName)) {
      throw new Error('Module already registered: ' + moduleName);
    }

    const record = Object.freeze({
      name: moduleName,
      module: moduleDefinition,
      metadata: Object.freeze({ ...metadata }),
      registeredAt: Date.now()
    });

    moduleRegistry.set(moduleName, record);
    return moduleDefinition;
  }

  function getModule(name) {
    return moduleRegistry.get(String(name || '').trim())?.module || null;
  }

  function hasModule(name) {
    return moduleRegistry.has(String(name || '').trim());
  }

  function listModules() {
    return Array.from(moduleRegistry.values()).map(record => ({
      name: record.name,
      metadata: { ...record.metadata },
      registeredAt: record.registeredAt
    }));
  }

  const App = {
    version: '12.2',
    name: 'Architecture Shell',
    architecture,
    config: {},
    core: {
      lifecycle,
      registerModule,
      getModule,
      hasModule,
      listModules
    },
    state: {},
    services: {},
    repositories: {},
    features: {},
    views: {},
    platform: {},
    compatibility: {
      legacyRuntimeAuthoritative: true,
      publicActionsInstalled: false
    }
  };

  Object.defineProperty(App.core, 'modules', {
    enumerable: true,
    configurable: false,
    get() {
      return new Map(moduleRegistry);
    }
  });

  global.FocusModeApp = App;

  if (typeof global.App === 'undefined') {
    global.App = App;
  } else {
    App.compatibility.appAliasConflict = true;
    console.warn('[Architecture]', {
      event: 'app_alias_conflict',
      action: 'FocusModeApp remains available without replacing window.App'
    });
  }

  console.info('[Architecture]', {
    event: 'shell_ready',
    version: App.version,
    distribution: architecture.distribution,
    legacyRuntimeAuthoritative: architecture.legacyRuntimeAuthoritative
  });
})(window);
