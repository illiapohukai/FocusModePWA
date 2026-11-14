# Focus Mode+ v14.1 Product Baseline — Feature Inventory

> **Baseline Version:** v14 Recovery Merge  
> **Stable Tag:** `v14-recovery-merge-stable`  
> **Production Branch:** `production`  
> **Stable Commit:** `81809e9d1b784d5cbf2fdef3418c77f7da45ee6c`  
> **Documentation Scope:** v14.1 Baseline Specification (Часть 1)

---

## 1. Архитектурный обзор и статусная классификация

Приложение Focus Mode+ представляет собой гибридный автономный PWA (Single File Application с Service Worker и Web App Manifest), объединяющий структурированные фокусированные сессии, адаптивное AI-планирование задач и барьеры цифрового самоконтроля.

### Матрица классификации компонентов

| Компонент / Подсистема | Статус | Runtime Owner | Хранилище / Ключи | Назначение |
| :--- | :--- | :--- | :--- | :--- |
| **Architecture Shell** | Working | `FocusModeApp.core` | В памяти (`FocusModeApp`) | Модульная шина и реестр ядра |
| **Storage Service** | Working | `FocusModeApp.services.storage` | `localStorage` | Изолированная граница работы с хранилищем |
| **Session Repository** | Working | `FocusModeApp.repositories.sessions` | `focusmode-active-session-v11`, `focusmode-last-session-v11`, `focusmode-session-history-v11-1` | Единственный шлюз персистентности сессий |
| **Mission Repository** | Working | `FocusModeApp.repositories.missions` | `focus-mode-missions-v11-5` | Репозиторий многомиссионного состояния |
| **Session Core** | Working | `FocusModeApp.core.sessionRecovery` | Сессионные хранилища | Жизненный цикл сессий фокуса |
| **Session History** | Working | `FocusModeApp.repositories.sessions` | `focusmode-session-history-v11-1` | Учет и отображение завершенных сессий |
| **Home Statistics** | Working | Session UI / History Aggregator | `focusmode-session-history-v11-1` | Агрегация статистики на главном экране |
| **Session Recovery** | Working | `FocusModeApp.core.sessionRecovery` | `focusmode-active-session-v11` | Восстановление незавершенных сессий |
| **Recovery Router** | Working | `FocusModeApp.state.recoveryRouterV1314` | В памяти / `sessions.saveActive` | Маршрутизация при возобновлении сессии |
| **Finalized Session Guard** | Working | `FocusModeApp.compatibility.finalizedSessionGuard` | `focusmode-active-session-v11` | Защита от блокировки новыми шагами остатками завершенных сессий |
| **Quick Focus** | Working | Session Core Runtime | Сессионные ключи | Быстрая сессия без привязки к миссии |
| **Mission Session** | Working | `FocusModeApp.features.missionFlow` | Сессионные и миссионные ключи | Фокусированный шаг конкретной миссии |
| **Multi-Mission Core** | Working | `FocusModeApp.repositories.missions` | `focus-mode-missions-v11-5` | Параллельное ведение до 2 активных миссий |
| **Multi-Mission Home Carousel** | Working | Multi-Mission UX layer | `focus-mode-missions-v11-5` | Карусель активных миссий на Home |
| **Mission Management** | Working | Mission Management UX | `focus-mode-missions-v11-5` | Управление: архив, переименование, удаление |
| **Mission Creation** | Working | Intake & Confidence Logic | В памяти / Canvas | Ввод цели, контекста, загрузка фото |
| **AI Mission Generation** | Working | `realAIMissionV6` / Cloudflare Worker | `focus-mode-last-ai-plan` | Генерация плана через Cloudflare Worker |
| **Optional Adaptation** | Working | `FocusModeApp.features.optionalAdaptation` | `focus-mode-missions-v11-5` | Опциональная AI-адаптация после шага |
| **Continue without Adaptation** | Working | `FocusModeApp.features.optionalAdaptation` | `focus-mode-missions-v11-5` | Локальный переход к шагу без вызова AI |
| **AI Adaptation Retry** | Working | `FocusModeApp.features.optionalAdaptation` | `focus-mode-missions-v11-5` | Повторный вызов AI при сбое сети |
| **Focus Protection** | Prototype | `focusProtectionLogic` | В памяти (DOM state) | UI-прототип барьера отвлечений |
| **Cosmic Timer** | Working | `ultraCosmicV91` | В памяти / Canvas 2D | Визуализация таймера и космической сцены |
| **PWA** | Working | `pwaBoot` / Service Worker | `focus-pwa-help-v11`, Cache API | Автономный запуск, кэш, статус-бар |
| **Language Selection** | Working | `i18nScript` | `focus-mode-language` | Переключение DE/EN (RU исключен) |
| **Premium** | Mock | In-memory simulation | В памяти (`isPremiumUser`) | Симуляция подписки/покупки |
| **AI Coach** | Mock | In-memory demo script | В памяти | Скриптованный демонстрационный диалог |
| **League & Achievements** | Mock | Static demo view | Статический HTML | Демонстрационная лига и бейджи |
| **Native App Blocking** | Planned | Отсутствует | Отсутствует | Системная блокировка сторонних приложений |
| **Accounts / Sync / Supabase** | Planned | Отсутствует | Отсутствует | Облачная синхронизация и профили |

---

## 2. Подробная инвентаризация функций и модулей

### 1. Architecture Shell
* **Статус:** Working.
* **Авторитетный runtime owner:** `FocusModeApp.core` (модуль `src/core/architecture-shell.js`, скрипт `architectureShellV122`, стабилизирован в `architectureStabilizationV1325`).
* **Основные функции / модули:** `window.FocusModeApp`, `registerModule(name, def, meta)`, `getModule(name)`, `hasModule(name)`, `listModules()`, `lifecycle`.
* **Связанные экраны:** Базовая инфраструктура для всех экранов приложения (`#app-screen-*`).
* **Storage keys:** Не использует напрямую; предоставляет контекст для сервисов.
* **Основные зависимости:** Нативный браузерный JavaScript (ES6+), выполняется до загрузки зависимых функциональных скриптов.
* **Критические regression-сценарии:**
  * Повторная регистрация существующего модуля вызывает исключение;
  * Нарушение доступности `window.FocusModeApp` блокирует инициализацию всех последующих подсистем;
  * Нарушение контракта жизненного цикла `lifecycle.phase = 'architecture-stable'`.
* **Известные ограничения:** Модули регистрируются в едином глобальном пространстве имен внутри одного HTML-файла; легаси-функции продолжают частично сосуществовать в виде compatibility-оберток.

---

### 2. Storage Service
* **Статус:** Working.
* **Авторитетный runtime owner:** `FocusModeApp.services.storage` (модуль `src/services/storage-service.js`, скрипт `storageServiceV123`).
* **Основные функции / модули:** `isAvailable()`, `getText(key, fallback)`, `setText(key, value)`, `getJSON(key, fallback, options)`, `setJSON(key, value)`, `remove(key)`, `has(key)`, `backup(sourceKey, backupKey)`.
* **Связанные экраны:** Инфраструктурный слой, задействованный на всех экранах с сохранением состояния.
* **Storage keys:** Инкапсулирует доступ ко всем ключам `localStorage` с автоматическим клонированием (`structuredClone`), проверкой доступности и созданием аварийных бэкапов поврежденных данных.
* **Основные зависимости:** `FocusModeApp.core.registerModule`, `window.localStorage`.
* **Критические regression-сценарии:**
  * Падение при невалидном JSON (должен изолировать поврежденный ключ через `backup` и возвращать fallback);
  * Мутация возвращаемого объекта по ссылке в обход хранилища (предотвращается клонированием);
  * Превышение лимита квоты кэша (`QuotaExceededError`).
* **Известные ограничения:** Синхронное локальное хранилище `localStorage` (~5 МБ на домен); отсутствие асинхронного IndexedDB.

---

### 3. Session Repository
* **Статус:** Working.
* **Авторитетный runtime owner:** `FocusModeApp.repositories.sessions` (модуль `src/repositories/session-repository.js`, скрипты `sessionRepositoryV129`, `sessionPersistenceMigrationV13`, `recoveryRepositoryMigrationV131`).
* **Основные функции / модули:** `loadActive()`, `saveActive(session)`, `checkpoint(session)`, `loadLast()`, `saveLast(session)`, `listHistory()`, `appendHistory(session)`, `clearActive(expectedId)`, `validate(session, options)`, `inspect()`.
* **Связанные экраны:** `app-screen-timer`, `app-screen-session-recovery`, `app-screen-result`, `app-screen-history`, `app-screen-home`, `app-screen-stats`.
* **Storage keys:**
  * `focusmode-active-session-v11` (активная сессия);
  * `focusmode-last-session-v11` (снимок последней завершенной сессии);
  * `focusmode-session-history-v11-1` (хранилище истории сессий, schemaVersion: 1);
  * `focusmode-session-history-v11` (legacy-ключ чтения при миграции);
  * `focusmode-active-session-v11-repository-backup`, `focusmode-last-session-v11-repository-backup`, `focusmode-session-history-repository-backup` (аварийные копии).
* **Основные зависимости:** `FocusModeApp.services.storage`, политика валидации состояний (`ACTIVE_STATES`, `TERMINAL_STATES`).
* **Критические regression-сценарии:**
  * Дублирование записей истории при повторном коммите терминального состояния (идентификация строго по `id + '::' + status`);
  * Очистка активной сессии с несовпадающим `expectedId`;
  * Сохранение сессии с нарушенными инвариантами (Quick Focus со ссылкой на миссию или Mission Session без шага).
* **Известные ограничения:** Максимальная глубина локальной истории ограничена 500 записями (`HISTORY_LIMIT = 500`).

---

### 4. Mission Repository
* **Статус:** Working.
* **Авторитетный runtime owner:** `FocusModeApp.repositories.missions` (модуль `src/repositories/mission-repository.js`, скрипт `multiMissionCoreV115`, compatibility-алиас `window.missionRepository`).
* **Основные функции / модули:** `loadStore()`, `getAll()`, `getActive()`, `getById(id)`, `getSelected()`, `getPrimary()`, `create(input)`, `save(mission)`, `update(id, patch)`, `setSelected(id)`, `setPrimary(id)`, `completeStep(id, sid, result)`, `replaceRemainingSteps(id, steps, expectedUpdatedAt)`, `archive(id)`, `delete(id)`, `rename(id, title)`, `restore(id)`.
* **Связанные экраны:** `app-screen-plan`, `app-screen-home`, `app-screen-missions`, `app-screen-goal`, `app-screen-confirm`, `app-screen-result`, `app-screen-update`.
* **Storage keys:**
  * `focus-mode-missions-v11-5` (основное хранилище миссий);
  * `focus-mode-missions-v11-5-corrupt-backup` (бэкап при нарушении схемы);
  * `focus-mode-missions-v11-5-migrated` (флаг миграции с v7/v10);
  * `focus-mode-active-mission`, `focus-mode-mission-progress-v7` (источники первичной миграции).
* **Основные зависимости:** `FocusModeApp.services.storage`.
* **Критические regression-сценарии:**
  * Попытка создания более двух активных миссий (строгий возврат ошибки `active_mission_limit_reached`);
  * Удаление или архивация миссии при наличии запущенной сессии по этой миссии (`active_session_exists`);
  * Рассинхронизация версии миссии при AI-адаптации (`expectedUpdatedAt` предотвращает перезапись изменений).
* **Известные ограничения:** Допустимо максимум 2 активные миссии одновременно.

---

### 5. Session Core
* **Статус:** Working.
* **Авторитетный runtime owner:** `FocusModeApp.core.sessionRecovery` / `FocusModeApp.core.runtimeOwnership` (скрипты `sessionCoreV11`, `sessionPersistenceMigrationV13`, `runtimeOwnershipV132`, `sessionRecoveryControllerV1321`).
* **Основные функции / модули:** `startSession(session)`, `checkpointActiveSession(reason, persist)`, `endSessionByUser()`, `completeLearningSession()`, `stopFocusSession()`, `startSessionRuntime()`, `stopSessionRuntime()`, `actualSessionMinutes(s)`, `validateSession(s)`, `makeSession(x)`.
* **Связанные экраны:** `app-screen-timer`, `app-screen-quicksetup`, `app-screen-plan`, `app-screen-result`, `app-screen-home`.
* **Storage keys:** Маршрутизирует запись строго через Session Repository (`focusmode-active-session-v11`, `focusmode-last-session-v11`).
* **Основные зависимости:** `FocusModeApp.repositories.sessions`, Cosmic Timer, Screen Navigation.
* **Критические regression-сценарии:**
  * Старт новой сессии поверх уже работающей (`status === 'running'`);
  * Неправильный расчет времени при досрочном выходе (< 1 минуты засчитывается как `cancelled`);
  * Многократный вызов финализации при срабатывании таймера и пользовательском нажатии.
* **Известные ограничения:** Таймер работает на основе вычисления разницы временных меток (`endsAt - Date.now()`), а не непрерывного фонового потока service worker.

---

### 6. Session History
* **Статус:** Working (полностью реализована, не является прототипом).
* **Авторитетный runtime owner:** `FocusModeApp.repositories.sessions` + UI-контроллер истории (`sessionHistoryCoreV111`, `recoveryRepositoryMigrationV131`).
* **Основные функции / модули:** `renderSessionHistory(filter)`, `refreshSessionHistoryUI()`, `loadHistoryStore()`, `createHistoryCard(session)`, `createHistoryEmpty(filter, totalCount)`, `countedFocus(session)`, `formatHistoryDuration(minutes)`.
* **Связанные экраны:** `app-screen-history` (вкладки фильтрации: 'today', 'week', 'all').
* **Storage keys:** `focusmode-session-history-v11-1`, аварийный бэкап `focusmode-session-history-corrupt-backup`.
* **Основные зависимости:** `FocusModeApp.repositories.sessions.listHistory()`.
* **Критические regression-сценарии:**
  * Нарушение фильтрации сессий по календарному началу недели/дня;
  * Попадание незавершенных/активных сессий в отображение истории;
  * Некорректный подсчет сессий с `actualMinutes < 1`.
* **Известные ограничения:** Перерисовка списка выполняется непосредственно в DOM на основе выборки из репозитория.

---

### 7. Home Statistics
* **Статус:** Working.
* **Авторитетный runtime owner:** UI-слой статистики Home и Session History (`sessionHistoryCoreV111`, `sessionPersistenceMigrationV13`, `sessionFlowStabilizationV1312`).
* **Основные функции / модули:** `refreshHomeStats()`, `refreshStatsScreen()`, `getWeekDailyMinutes()`.
* **Связанные экраны:** `app-screen-home` (карточки прогресса), `app-screen-stats` (детальные графики недели).
* **Storage keys:** Чтение через Session Repository из `focusmode-session-history-v11-1`.
* **Основные зависимости:** `FocusModeApp.repositories.sessions`, DOM-элементы `#homeTodayFocus`, `#homeWeekSessions`, `#homeWeekFocus`, `#homeWeekProgress`, `#statsWeekChart`.
* **Критические regression-сценарии:**
  * Устаревание счетчиков после завершения сессии (требует обязательного вызова `refreshSessionConsumersV13()`);
  * Смещение индексов дней недели при расчете недельного графика (`(day + 6) % 7`).
* **Известные ограничения:** Норма недельной цели жестко задана в интерфейсе (4 сессии в неделю).

---

### 8. Session Recovery
* **Статус:** Working (полностью реализован, не является прототипом).
* **Авторитетный runtime owner:** `FocusModeApp.core.sessionRecovery` (скрипты `sessionRecoveryV112`, `sessionRecoveryAndHomeHotfixV1151`, `recoveryRepositoryMigrationV131`, контроллер `sessionRecoveryControllerV1321`).
* **Основные функции / модули:** `readRecoveryCandidate()`, `getRecoverableSessionV1151()`, `renderSessionRecovery(candidate)`, `resumeRecoveredSession()`, `discardRecoveredSession()`, `finishRecoveredSession()`, `routeExistingSessionV1151()`.
* **Связанные экраны:** `app-screen-session-recovery`.
* **Storage keys:** `focusmode-active-session-v11`, резервная копия `focusmode-active-session-v11-recovery-backup`.
* **Основные зависимости:** `FocusModeApp.repositories.sessions`, Authoritative Recovery Router, Finalized Session Guard, Mission Repository.
* **Критические regression-сценарии:**
  * Потеря фактически затраченного времени (`elapsedMs`) при восстановлении сессии после перезагрузки;
  * Возобновление сессии миссии, которая была удалена пользователем (должна блокироваться с сохранением времени);
  * Зацикливание экрана восстановления при пустом или поврежденном снимке.
* **Известные ограничения:** Восстановление рассчитано строго на одну прерванную сессию.

---

### 9. Recovery Router
* **Статус:** Working.
* **Авторитетный runtime owner:** `FocusModeApp.state.recoveryRouterV1314` (скрипт `authoritativeRecoveryRouterV1314`).
* **Основные функции / модули:** `resumeAuthoritativelyV1314()`, `applyAppScreenImmediatelyV1314(screenName)`, `setAppScreenV1314(screenName, options)`, `recoverableSnapshot()`, `persistRunning(snapshot)`, capture-обработчик на `#app-screen-session-recovery .recovery-primary`.
* **Связанные экраны:** `app-screen-session-recovery` -> `app-screen-timer`.
* **Storage keys:** Синхронизирует `focusmode-active-session-v11` через `sessions.saveActive`.
* **Основные зависимости:** `FocusModeApp.repositories.sessions`, физический переход `applyAppScreenImmediately`.
* **Критические regression-сценарии:**
  * Попытка стартового кода перейти на экран Home во время активного восстановления (блокируется функцией `homeBlocked()`);
  * Повторный клик по кнопке «Продолжить» во время выполнения перехода (`state.resumeInFlight`);
  * Конфликт старых легаси-обработчиков клика благодаря приоритетному capture-слушателю на `window`.
* **Известные ограничения:** Завязан на физический перехват событий клика на уровне фазы захвата окна.

---

### 10. Finalized Session Guard
* **Статус:** Working.
* **Авторитетный runtime owner:** `FocusModeApp.compatibility.finalizedSessionGuard` (скрипт `finalizedSessionGuardV1315`).
* **Основные функции / модули:** `clearResidueV1315(reason)`, `isFinalizedResidueV1315(session)`, `historyHasSessionV1315(id)`, `missionMovedPastSessionV1315(session)`.
* **Связанные экраны:** `app-screen-session-recovery`, `app-screen-result`, `app-screen-plan`, `app-screen-timer`, `app-screen-home`.
* **Storage keys:** Очищает остаточный ключ `focusmode-active-session-v11`.
* **Основные зависимости:** `FocusModeApp.repositories.sessions`, `FocusModeApp.repositories.missions`.
* **Критические regression-сценарии:**
  * Завершенная сессия шага 1 блокирует запуск шага 2 или повторно открывает экран восстановления;
  * Ошибочное удаление активной сессии, которая действительно находится в процессе выполнения.
* **Известные ограничения:** Выполняет аудит состояния на ключевых стыках жизненного цикла: перед стартом новой сессии, при сохранении ревью, после завершения потока и на старте приложения.

---

### 11. Quick Focus
* **Статус:** Working.
* **Авторитетный runtime owner:** Session Core (`sessionCoreV11`, `cleanQuickFocusV13313Logic`, `focusClarityLogicV13314`).
* **Основные функции / модули:** `createQuickSession(input)`, `startQuickFocus()`, `startConfiguredQuickFocus()`, `selectQuickDuration(btn, value)`, `adjustQuickDuration(delta)`, `updateQuickRing()`, `openQuickSetupFromChoice()`, `syncFocusClarityCard()`.
* **Связанные экраны:** `app-screen-quicksetup`, `app-screen-timer`, `app-screen-home`.
* **Storage keys:** `focusmode-active-session-v11`, `focusmode-last-session-v11`, `focusmode-session-history-v11-1`.
* **Основные зависимости:** `FocusModeApp.core.sessionRecovery`, `FocusModeApp.repositories.sessions`, Cosmic Timer.
* **Критические regression-сценарии:**
  * Появление связей с миссией (`missionId` или `missionStepId` обязаны быть `null`);
  * Попытка изменения текущей выбранной миссии при запуске быстрого фокуса;
  * Визуальное искажение подложки Safe Area (регулируется классом `.quick-focus-surface`).
* **Известные ограничения:** Длительность настраивается в диапазоне от 5 до 60 минут с шагом 5 минут.

---

### 12. Mission Session
* **Статус:** Working.
* **Авторитетный runtime owner:** `FocusModeApp.features.missionFlow` (скрипты `sessionCoreV11`, `multiMissionCoreV115`, `missionFlowControllerV1322`, `optionalAdaptationControllerV133`).
* **Основные функции / модули:** `createMissionSession(missionId, stepId)`, `prepareLearningSession(missionId, stepId)`, `handleMissionSessionResult(session)`, `completeLearningSession()`.
* **Связанные экраны:** `app-screen-plan`, `app-screen-timer`, `app-screen-result`.
* **Storage keys:** `focusmode-active-session-v11`, `focus-mode-missions-v11-5`, `focusmode-last-session-v11`, `focusmode-session-history-v11-1`.
* **Основные зависимости:** `FocusModeApp.repositories.missions`, `FocusModeApp.repositories.sessions`, Optional Adaptation Controller.
* **Критические regression-сценарии:**
  * Старт шага миссии без валидных `missionId` и `missionStepId` (строго запрещено валидатором);
  * Старт шага, не являющегося текущим активным шагом миссии (`currentStepId !== stepId`);
  * Повторное начисление прогресса за уже выполненный шаг при сбоях навигации.
* **Известные ограничения:** Одновременно может выполняться только один шаг активной миссии.

---

### 13. Multi-Mission Core
* **Статус:** Working (полностью реализован в v11.5+, не является будущей функцией).
* **Авторитетный runtime owner:** `FocusModeApp.repositories.missions` (скрипты `multiMissionCoreV115`, `missionFlowControllerV1322`).
* **Основные функции / модули:** `loadMissionStoreV115()`, `persistMissionStoreV115(store)`, `normalizeMissionV115(input)`, `validateMissionStoreV115(store)`, `resolvePrimaryMissionIdV115(store)`, `bindMissionCompatibilityV115(mission)`.
* **Связанные экраны:** Основа для экранов `app-screen-plan`, `app-screen-home`, `app-screen-missions`, `app-screen-goal`.
* **Storage keys:** `focus-mode-missions-v11-5`, резервные ключи `focus-mode-missions-v11-5-corrupt-backup`, `focus-mode-missions-v11-5-migrated`.
* **Основные зависимости:** `FocusModeApp.services.storage`, `FocusModeApp.core.registerModule`.
* **Критические regression-сценарии:**
  * Превышение лимита в 2 активные миссии;
  * Назначение завершенной или архивированной миссии в качестве основной (`primaryMissionId`);
  * Перезапись структуры миссии некорректным форматом без полей `steps` и `progress`.
* **Известные ограничения:** Локальное хранилище на устройстве, синхронизация между устройствами отсутствует.

---

### 14. Multi-Mission Home Carousel
* **Статус:** Working.
* **Авторитетный runtime owner:** Слой Multi-Mission UX (`multiMissionUXV116`, `adaptiveMissionCardsV117`, `missionAuraBlendV1181`).
* **Основные функции / модули:** `renderHomeCarouselV116()`, `sortedActiveMissionsV116()`, `missionViewModelV116(mission)`, `renderMissionHeroV116(vm)`, `renderMultiMissionUIV115()`.
* **Связанные экраны:** `app-screen-home` (контейнеры `#missionCarouselV116`, `#activeMissionCard`, `#secondaryMissionCard`).
* **Storage keys:** Читает данные через `FocusModeApp.repositories.missions`.
* **Основные зависимости:** Mission Repository, Lucide Icons, CSS Aura Blend стили.
* **Критические regression-сценарии:**
  * Отображение карусели при наличии только одной миссии (должна отображаться одиночная карточка);
  * Нарушение скролла страницы при горизонтальном свайпе карусели;
  * Рассинхронизация процента выполнения и названия следующего шага на карточке миссии.
* **Известные ограничения:** Темы оформления карточек ('violet', 'aqua') вычисляются детерминированно по хэшу ID миссии.

---

### 15. Mission Management
* **Статус:** Working.
* **Авторитетный runtime owner:** Слой Mission Management UX (`multiMissionUXV116`).
* **Основные функции / модули:** `openMissionManagementV116()`, `renderMissionManagementV116()`, `setMissionFilterV116(filter, btn)`, `openMissionActionsV116(id)`, `openRenameMissionV116(id)`, `saveRenameMissionV116(id)`, `confirmArchiveV116(id)`, `archiveMissionActionV116(id)`, `restoreMissionActionV116(id)`, `makePrimaryV116(id)`.
* **Связанные экраны:** `app-screen-missions`, модальный лист `#missionSheetV116`.
* **Storage keys:** Модифицирует `focus-mode-missions-v11-5` через `missionRepository`.
* **Основные зависимости:** `FocusModeApp.repositories.missions`, Screen Transition Manager.
* **Критические regression-сценарии:**
  * Попытка архивировать или удалить миссию, по которой в данный момент идет таймер сессии;
  * Восстановление миссии из архива, когда уже открыты две активные миссии;
  * Переименование миссии в пустую строку.
* **Известные ограничения:** Экран управления генерируется динамически в DOM, если отсутствует в исходной статической разметке.

---

### 16. Mission Creation
* **Статус:** Working.
* **Авторитетный runtime owner:** Контроллеры ввода и уверенности (`missionInputAndConfidenceLogic`, `missionDepthV1338Logic`, `realAIMissionV6`).
* **Основные функции / модули:** `openGoalIntake()`, `selectMissionType(btn)`, `selectConfidence(btn)`, `handleMissionFile(event)`, `compressMissionImage(file)`, `openRealFilePicker(cameraPreferred)`, `clearMissionError()`, `showMissionError(msg)`.
* **Связанные экраны:** `app-screen-goal`, `app-screen-filepicker`.
* **Storage keys:** Не сохраняет напрямую; готовит данные для передачи в AI-генератор.
* **Основные зависимости:** HTML Canvas API (компрессия изображений), DOM-элементы ввода.
* **Критические regression-сценарии:**
  * Открытие экрана ввода цели при уже имеющихся 2 активных миссиях (должен выводиться запрет);
  * Загрузка тяжелых изображений без компрессии (сжатие до 1800px / JPEG 0.82 гарантирует стабильность мобильного браузера);
  * Отправка запроса с пустой целью и отсутствующим изображением.
* **Известные ограничения:** Поддерживаются форматы JPEG, PNG, WEBP.

---

### 17. AI Mission Generation
* **Статус:** Working (активный сервис через Cloudflare Worker).
* **Авторитетный runtime owner:** `realAIMissionV6`, стабилизирован в `criticalFlowFixV1311` и `missionFlowControllerV1322`.
* **Основные функции / модули:** `analyzeGoal()`, `confirmAIPlan()`, `renderAIPlan(plan)`, `resetAnalysisRows()`, `completeAnalysisRow(idx)`, `missionTypeForAPI()`, `confidenceForAPI()`.
* **Связанные экраны:** `app-screen-goal`, `app-screen-analyzing`, `app-screen-confirm`, `app-screen-plan`.
* **Storage keys:** `focus-mode-last-ai-plan` (снимок ответа), фиксируется в `focus-mode-missions-v11-5`.
* **Основные зависимости:** Внешний endpoint `FOCUS_AI_ENDPOINT` (`https://focusmodedemoai.illia-pohukai.workers.dev/create-mission`), `AbortController` (таймаут 120 с), `FocusModeApp.repositories.missions`.
* **Критические regression-сценарии:**
  * Зависание интерфейса при сетевой ошибке или таймауте worker;
  * Нажатие кнопки «Plan übernehmen» при достижении лимита двух миссий;
  * Потеря плана при переходе между шагами анализа.
* **Известные ограничения:** Требует обязательного интернет-соединения; локального оффлайн-генератора планов нет.

---

### 18. Optional Adaptation
* **Статус:** Working (полностью реализована в v14 / v13.3).
* **Авторитетный runtime owner:** `FocusModeApp.features.optionalAdaptation` (модуль `src/features/optional-adaptation/optional-adaptation-controller.js`, скрипт `optionalAdaptationControllerV133`).
* **Основные функции / модули:** `adaptMissionAfterSession(retry)`, `buildResult(mode)`, `commitMissionSessionResult(result)`, `updateSession(result)`, `requestAdaptation(result)`, `renderAIResult(result, adaptation)`.
* **Связанные экраны:** `app-screen-result`, `app-screen-feedbackprocessing`, `app-screen-update`.
* **Storage keys:** Обновляет `focus-mode-missions-v11-5`, `focusmode-session-history-v11-1`, `focusmode-last-session-v11`.
* **Основные зависимости:** `FocusModeApp.repositories.missions`, `FocusModeApp.repositories.sessions`, endpoint `FOCUS_AI_ADAPT_ENDPOINT` (`https://focusmodedemoai.illia-pohukai.workers.dev/adapt-mission`).
* **Критические regression-сценарии:**
  * Превращение адаптации в принудительный шаг (адаптация строго опциональна);
  * Дублирование отправки запроса при повторном клике по кнопке (блокируется через `setBusy`);
  * Потеря ранее завершенных шагов при вызове `replaceRemainingSteps` (заменяются только оставшиеся шаги).
* **Известные ограничения:** Заменяет только будущие невыполненные шаги миссии; завершенные шаги остаются неизменными в истории.

---

### 19. Continue without Adaptation
* **Статус:** Working (полностью реализована в v14 / v13.3).
* **Авторитетный runtime owner:** `FocusModeApp.features.optionalAdaptation` (скрипт `optionalAdaptationControllerV133`).
* **Основные функции / модули:** `continueMissionWithoutAdaptation()`, `renderLocalResult(result, failed)`, `finishOptionalAdaptationFlow()`.
* **Связанные экраны:** `app-screen-result` -> `app-screen-update` -> `app-screen-home`.
* **Storage keys:** Обновляет `focus-mode-missions-v11-5`, `focusmode-session-history-v11-1`, `focusmode-last-session-v11` (с атрибутом `adaptationMode: 'skipped'`).
* **Основные зависимости:** `FocusModeApp.repositories.missions`, `FocusModeApp.repositories.sessions`.
* **Критические regression-сценарии:**
  * Вызов сетевого запроса к AI при выборе «Ohne Anpassung fortfahren» (СТРОГОЕ ПРАВИЛО: вызов AI запрещен);
  * Ошибка продвижения миссии на следующий шаг при успешном завершении сессии;
  * Неочищенный статус ревью, вызывающий ложное срабатывание восстановления.
* **Известные ограничения:** Оставляет неизменными шаги миссии, сгенерированные исходным планом.

---

### 20. AI Adaptation Retry
* **Статус:** Working.
* **Авторитетный runtime owner:** `FocusModeApp.features.optionalAdaptation` (скрипт `optionalAdaptationControllerV133`).
* **Основные функции / модули:** `retryOptionalAdaptation()`, `adaptMissionAfterSession(true)`.
* **Связанные экраны:** `app-screen-update` (блок кнопок повтора `#optionalAdaptationRetryActions`).
* **Storage keys:** Использует сохраненное в состоянии ревью сессии `state.result`.
* **Основные зависимости:** `FOCUS_AI_ADAPT_ENDPOINT`, `requestAdaptation`.
* **Критические regression-сценарии:**
  * Повторная фиксация сессии в истории при повторном запросе адаптации (результат сессии должен коммититься однократно);
  * Зависание интерфейса при повторном падении соединения (должен возвращать локальный экран с возможностью продолжить без адаптации).
* **Известные ограничения:** Требует стабильного подключения к интернету.

---

### 21. Focus Protection
* **Статус:** Prototype (UI-прототип в PWA, не является системной блокировкой).
* **Авторитетный runtime owner:** `focusProtectionLogic` (строки 2536–2561).
* **Основные функции / модули:** `openDistractionPicker(returnScreen)`, `returnFromDistractionPicker()`, `toggleDistraction(row)`, `getSelectedDistractions()`, `updateProtectionSummary()`, `saveDistractions()`, `showBlockedAppsPopup()`, `hideBlockedAppsPopup()`.
* **Связанные экраны:** `app-screen-quicksetup`, экран выбора отвлечений, модальное окно `#blockedAppsPopup`.
* **Storage keys:** Состояние хранится исключительно в разметке DOM (атрибуты классов и элементов списка).
* **Основные зависимости:** DOM-дерево списка приложений (`#distractionList`), Lucide Icons.
* **Критические regression-сценарии:**
  * Сбой счетчика заблокированных приложений в сводке на экране быстрого старта;
  * Нарушение возврата на исходный экран после закрытия диалога выбора.
* **Известные ограничения:** В веб-среде PWA невозможно заблокировать нативные приложения ОС (Instagram, TikTok и т.д.); функция демонстрирует продуктовый сценарий намеренного барьера.

---

### 22. Cosmic Timer
* **Статус:** Working.
* **Авторитетный runtime owner:** `ultraCosmicV91` (строки 3027–3055), интегрирован со скриптами `sessionCoreV11` и `focusClarityLogicV13314`.
* **Основные функции / модули:** `initUc()`, `drawUc(now)`, `startUc()`, `stopUc()`, `syncUcData()`, `seedOrbit(n, front)`, `seedGalaxy(n)`, `updateTimerUI()`, `startRestoredSessionTimer()`.
* **Связанные экраны:** `app-screen-timer` (`#ucScene`, холсты `#ucBackCanvas`, `#ucFrontCanvas`, карточка `#ucFocusCard`).
* **Storage keys:** Не хранит состояние напрямую; визуализирует `window.activeSession` и целевое время окончания `ucEndsAt`.
* **Основные зависимости:** HTML5 Canvas 2D, `requestAnimationFrame`, события `visibilitychange` и `resize`.
* **Критические regression-сценарии:**
  * Рассинхронизация секундного таймера при сворачивании и возврате вкладки (компенсируется вычислением `ucEndsAt - Date.now()`);
  * Повышенный расход аккумулятора на мобильных устройствах (поддерживается режим `prefers-reduced-motion`);
  * Искажение пропорций холста при изменении плотности пикселей экрана (DPR).
* **Известные ограничения:** Запрещено изменять логику и верстку Cosmic Timer без отдельной специализированной задачи.

---

### 23. PWA (Progressive Web App)
* **Статус:** Working.
* **Авторитетный runtime owner:** Модуль загрузки PWA (`pwaBoot`, строки 2562–2582), файлы `manifest.webmanifest` и `service-worker.js`.
* **Основные функции / модули:** Регистрация Service Worker (`navigator.serviceWorker.register`), подсказка по установке для iOS Safari (`#pwaInstallHelp`), синхронизация темы статус-бара `syncPWAChrome(screen)`.
* **Связанные экраны:** Оболочка всего приложения, баннер подсказки установки.
* **Storage keys:** `focus-pwa-help-v11` (отметка о прочтении подсказки установки).
* **Основные зависимости:** `manifest.webmanifest`, `service-worker.js`, Cache API браузера (`focus-mode-plus-v11-8-1-aura-blend`).
* **Критические regression-сценарии:**
  * Неправильное кэширование, препятствующее обновлению `index.html` при навигации;
  * Белый статус-бар на темных экранах таймера или анализа в режиме standalone.
* **Известные ограничения:** Оффлайн-кэш охватывает ядро интерфейса, но сетевые AI-запросы требуют подключения к интернету.

---

### 24. Language Selection (Выбор языка)
* **Статус:** Working.
* **Авторитетный runtime owner:** `i18nScript` (строки 2457–2458), предохранитель `removeRussianLanguage` (строка 629).
* **Основные функции / модули:** `setLanguage(lang, save)`, `detectLanguage()`, `translateTree(root, lang)`, `translateNode(node, lang)`, выпадающее меню `#langPicker`.
* **Связанные экраны:** Затрагивает весь лендинг и все экраны внутреннего приложения.
* **Storage keys:** `focus-mode-language` ('de' | 'en') через `FocusModeApp.services.storage`.
* **Основные зависимости:** Словарь `I18N` в памяти, DOM `MutationObserver` для динамических узлов.
* **Критические regression-сценарии:**
  * Случайная активация русского языка (русский язык принудительно мигрируется в 'de' на старте);
  * Пропуск перевода динамически добавленных элементов интерфейса.
* **Известные ограничения:** Исходный язык приложения — немецкий (`de`), поддерживаемый дополнительный язык — английский (`en`).

---

### 25. Premium
* **Статус:** Simulation / Mock.
* **Авторитетный runtime owner:** Обработчик симуляции покупки (строка 2138, функция `simulatePremiumPurchase`).
* **Основные функции / модули:** `simulatePremiumPurchase()`.
* **Связанные экраны:** `app-screen-premium`, диалог `#screen-popup-success`, ссылка в `app-screen-settings`.
* **Storage keys:** Отсутствуют (состояние хранится во временной переменной в памяти: `isPremiumUser = true`).
* **Основные зависимости:** CSS-анимации всплывающего окна успеха, навигация экранов.
* **Критические regression-сценарии:**
  * Сбой перехода на Home после завершения симуляции покупки;
  * Зависание модального окна подтверждения.
* **Известные ограничения:** Настоящие платежные шлюзы, серверная подписка и валидация чеков отсутствуют.

---

### 26. AI Coach
* **Статус:** Prototype / Mock (скриптованный демонстрационный сценарий).
* **Авторитетный runtime owner:** Скрипт демонстрационного чата (строки 2167–2260).
* **Основные функции / модули:** `triggerChatAnalysis()`, `showTyping()`, `hideTyping()`, `scrollToBottom()`.
* **Связанные экраны:** `app-screen-chat`.
* **Storage keys:** Отсутствуют (статическая демонстрация в памяти).
* **Основные зависимости:** Lucide Icons, таймеры задержки ответов (`setTimeout`).
* **Критические regression-сценарии:**
  * Двойной запуск сценария при повторном клике (предотвращается установкой `disabled = true` на кнопке подсказки);
  * Нарушение автоскролла диалога вниз.
* **Известные ограничения:** Отсутствует интеграция с LLM; выдаются строго захардкоженные сообщения и статический график продуктивности.

---

### 27. League and Achievements (Лига и достижения)
* **Статус:** Prototype / Mock (статические демонстрационные данные).
* **Авторитетный runtime owner:** Статическая верстка экрана `app-screen-score`.
* **Основные функции / модули:** Навигация на экран через `setAppScreen('score')`.
* **Связанные экраны:** `app-screen-score`.
* **Storage keys:** Отсутствуют.
* **Основные зависимости:** Tailwind CSS, Lucide Icons.
* **Критические regression-сценарии:**
  * Нарушение верстки лидерборда и отображения бейджей.
* **Известные ограничения:** Таблица лидеров, места (Platz 12) и очки (684 Focus Score) являются демонстрационными макетами без сетевого бэкенда.

---

### 28. Native App Blocking (Системная блокировка приложений)
* **Статус:** Planned / Not Implemented.
* **Авторитетный runtime owner:** Отсутствует.
* **Основные функции / модули:** Отсутствуют.
* **Связанные экраны:** Концептуально отражен только в UI-прототипе Focus Protection.
* **Storage keys:** Отсутствуют.
* **Основные зависимости:** Требует нативных биндингов (iOS Screen Time / FamilyControls API, Android Accessibility / UsageStats API).
* **Критические regression-сценарии:** Неприменимо.
* **Известные ограничения:** Браузерное веб-приложение PWA изолировано песочницей браузера и принципиально не имеет системных полномочий блокировать другие приложения ОС.

---

## 3. Отсутствующие подсистемы (Архитектурные границы)

Следующие подсистемы **полностью отсутствуют** в текущей стабильной версии v14 Recovery Merge:
1. **Пользовательские аккаунты (User Accounts & Auth):** профили пользователей, регистрация, логин и токены авторизации не реализованы.
2. **Supabase / Серверная база данных:** интеграция с внешними базами данных не используется; приложение функционирует автономно на локальном устройстве.
3. **Облачная синхронизация (Cloud Sync):** межсессионный обмен данными между устройствами (телефон/ноутбук) отсутствует.
