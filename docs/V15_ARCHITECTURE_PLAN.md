# Focus Mode+ — Архитектурный план модульного разделения (v15 Modular Extraction)

> **Baseline Version:** v14 Recovery Merge  
> **Stable Tag:** `v14-recovery-merge-stable`  
> **Stable Commit:** `81809e9d1b784d5cbf2fdef3418c77f7da45ee6c`  
> **Stable Branch:** `production`  
> **Target Milestone:** `v15 Modular Extraction`  
> **Главный архитектурный принцип:** `v15` **не является rewrite**. `v15` физически разделяет уже существующую и стабилизированную архитектуру `v14 Recovery Merge` на ES-модули и таблицы стилей при 100% сохранении пользовательского поведения, контрактов `window.FocusModeApp`, DOM ID, inline-обработчиков и схем `localStorage`. Существующие функции запрещено реализовывать заново.

---

# 1. Current architecture summary

## 1.1. Single-file distribution
В базовой версии `v14 Recovery Merge` всё приложение поставляется как монолитный файл `index.html` (~519 КБ, ~5465 строк), объединяющий:
* **Внешнюю презентационную оболочку (Landing & Interactive Story):** главы `01 / INTRO` — `11 / NEXT STEP`, верхний бар `.topbar`, липкий контейнер `.phone-side` и полноэкранное модальное окно прототипа `#modal`.
* **Внутренний мобильный контейнер (`#iphoneFrame` / `#appBody`):** 18 изолированных экранных секций `#app-screen-{name}`, переключаемых классом `.hidden`.
* **Встроенные каскадные таблицы стилей (`<style>`):** базовый дизайн-токен блок и 15 именованных патч-блоков (`balanced-screen-v1336`, `quickFocusRoutingFixV1337`, `missionDepthV1338`, `singleScreenMissionV1339`, `stableMissionScrollV13310`, `headerBadgeFitV13311`, `confidenceGlowV13312`, `focusClarityV13314`, `multiMissionV115Styles`, `multiMissionUXV116Styles`, `missionCardLayoutFixV1161`, `adaptiveMissionCardsV117Styles`, `missionAuraBlendV1181Styles`, `optionalAdaptationV133Styles`, `cleanQuickFocusV13313`, `planDurationProtectionRestoreV13315`).
* **Последовательные inline-скрипты (`<script>`):** 34 скрипта (от `removeRussianLanguage` на строке 629 до `planDurationProtectionRestoreRuntimeV13315` на строке 5417), которые исполняются синхронно сверху вниз по мере парсинга документа.

## 1.2. Пространство имён `window.FocusModeApp`
Инициализируется скриптом `architectureShellV122` (строка 904) и стабилизируется в `architectureStabilizationV1325` (строка 5143). Является каноническим реестром виртуальных модулей:
* `FocusModeApp.version` (`"13.2.5"`) и `FocusModeApp.name` (`"Architecture Stabilization"`);
* `FocusModeApp.core`: реестр модулей (`modules: Map`, `registerModule`, `getModule`, `hasModule`, `listModules`), `lifecycle` (`phase: "architecture-stable"`, `ready: true`), `runtime` (`core.runtimeOwnership`), `sessionRecovery` (`core.sessionRecovery`), `legacyCleanupGate`, `architectureStabilization`;
* `FocusModeApp.state`: оперативное состояние роутера восстановления `recoveryRouterV1314` (`bootEpoch`, `resumeInFlight`, `resumedSessionId`, `route`);
* `FocusModeApp.services`: `storage` (`StorageService`), `sessionPersistence`, `recoveryPersistence`;
* `FocusModeApp.repositories`: `sessions` (`SessionRepository`), `missions` (`MissionRepository`);
* `FocusModeApp.features`: `missionFlow`, `optionalAdaptation`, `planDurationProtectionRestore`;
* `FocusModeApp.compatibility`: замороженные диагностические манифесты (`sessionRepository`, `sessionPersistenceMigration`, `recoveryRepositoryMigration`, `authoritativeRecoveryRouter`, `finalizedSessionGuard`, `sessionRecoveryConsolidation`, `missionFlowConsolidation`, `legacyCleanupGate`, `architectureStabilization`).

## 1.3. Runtime Ownership (`core.runtimeOwnership`)
Скрипт `runtimeOwnershipV132` (строка 5103) фиксирует единый контракт владения рантайм-вызовами по четырём доменам через динамическую диспетчеризацию `call(fnName)`:
* `navigation`: `go` (`setAppScreen`), `apply` (`applyAppScreenImmediately`);
* `sessions`: `start` (`startSession`), `checkpoint` (`checkpointActiveSession`), `finish` (`endSessionByUser`), `cancel` (`stopFocusSession`);
* `recovery`: `read` (`readRecoveryCandidate`), `resume` (`resumeRecoveredSession`), `discard` (`discardRecoveredSession`), `review` (`finishRecoveredSession`);
* `missions`: `confirm` (`confirmAIPlan`), `open` (`openMission`), `startStep` (`prepareLearningSession`), `adapt` (`showPlanUpdate`), `finish` (`finishLearningFlow`), `renderPlan` (`renderAIPlan`), `renderHome` (`renderHomeCarouselV116`).

## 1.4. Repositories (`FocusModeApp.repositories`)
1. **`repositories.sessions` (`sessionRepositoryV129`, `sessionPersistenceMigrationV13`, `recoveryRepositoryMigrationV131`):**
   * Управляет ключами `focusmode-active-session-v11`, `focusmode-last-session-v11`, `focusmode-session-history-v11-1`.
   * Экспортирует API: `loadActive()`, `saveActive(s)`, `clearActive()`, `loadLast()`, `saveLast(s)`, `listHistory()`, `saveHistory(list)`, `appendHistory(s)`, `getById(id)`, `save(s)`, `inspect()`, `isRecoverableSession(s)`.
2. **`repositories.missions` (`multiMissionCoreV115`):**
   * Управляет ключом `focus-mode-missions-v11-5` и прозрачной миграцией из легаси-ключей `focus-mode-active-mission` и `focus-mode-mission-progress-v7`.
   * Экспортирует API: `loadStore()`, `getAll()`, `getActive()`, `getArchived()`, `getCompleted()`, `getById(id)`, `getSelected()`, `getSelectedId()`, `getPrimary()`, `create(input)`, `save(m)`, `update(id, patch)`, `setSelected(id)`, `setPrimary(id)`, `completeStep(id, sid, result)`, `replaceRemainingSteps(id, steps, expectedUpdatedAt)`, `archive(id)`, `restore(id)`, `delete(id)`, `rename(id, title)`.

## 1.5. Services (`FocusModeApp.services`)
1. **`services.storage` (`storageServiceV123`, строка 1028):**
   * Безопасная обёртка над `window.localStorage` с глубоким клонированием (`structuredClone` / JSON fallback) и карантином битых JSON-значений (`backup(sourceKey, backupKey)`).
   * Методы: `isAvailable()`, `getText(key, fallback)`, `setText(key, value)`, `getJSON(key, fallback, options)`, `setJSON(key, value)`, `remove(key)`, `has(key)`, `backup(sourceKey, backupKey)`.
2. **`services.sessionPersistence` (`sessionPersistenceMigrationV13`, строка 4455):**
   * Обеспечивает идемпотентный `terminalCommit(session)` и синхронное обновление всех UI-потребителей истории и статистики (`refreshConsumers()`).
3. **`services.recoveryPersistence` (`recoveryRepositoryMigrationV131`, строка 4571):**
   * Предоставляет `readCandidate()` и `inspect()` поверх `SessionRepository`.

## 1.6. Controllers (`FocusModeApp.core` / `FocusModeApp.features`)
* **`core.sessionRecovery` (`sessionRecoveryControllerV1321`, строка 5105):** консолидирует управление сессией и восстановлением (`start`, `checkpoint`, `finish`, `cancel`, `runtime.start`, `runtime.stop`, `recovery.read`, `recovery.resume`, `recovery.discard`, `recovery.review`, `recovery.render`, `recovery.routeExisting`).
* **`features.missionFlow` (`missionFlowControllerV1322`, строка 5107):** консолидирует управление миссиями (`selected`, `confirm`, `open`, `startStep`, `adapt`, `finish`, `currentStep`, `missionId`, `createSession`, `render.plan`, `render.home`).
* **`features.optionalAdaptation` (`optionalAdaptationControllerV133`, строка 5174):** управляет пост-сессионным экраном оценки (`adaptMissionAfterSession`, `continueMissionWithoutAdaptation`, `retryOptionalAdaptation`, `finishOptionalAdaptationFlow`).
* **`features.planDurationProtectionRestore` (`planDurationProtectionRestoreRuntimeV13315`, строка 5417):** поддерживает порядок карточек `#missionProtectionCard` и `#aiPlanExplanation` и позиционирование бейджей `.step-time`.

## 1.7. Compatibility aliases
В глобальной области `window` поддерживаются двусторонние ссылки для совместимости с inline HTML-атрибутами `onclick="..."` и ранними скриптами:
* `window.missionRepository` $\leftrightarrow$ `FocusModeApp.repositories.missions`
* `window.activeSession` $\leftrightarrow$ `FocusModeApp.repositories.sessions.loadActive()`
* `window.pendingAIPlan` $\leftrightarrow$ кэш сгенерированного плана до подтверждения (`focus-mode-last-ai-plan`)
* `window.activeAIPlan`, `window.missionProgress` $\leftrightarrow$ проекция текущей выбранной миссии `missionRepository.getSelected()` в формат v7/v10 (`bindMissionCompatibilityV115`)
* `window.sessionRecoveryCandidate` $\leftrightarrow$ текущий кандидат на восстановление сессии
* Публичные функции на `window`: `setAppScreen`, `applyAppScreenImmediately`, `startSession`, `checkpointActiveSession`, `endSessionByUser`, `completeLearningSession`, `stopFocusSession`, `readRecoveryCandidate`, `getRecoverableSessionV1151`, `resumeRecoveredSession`, `discardRecoveredSession`, `finishRecoveredSession`, `renderSessionRecovery`, `routeExistingSessionV1151`, `confirmAIPlan`, `openMission`, `prepareLearningSession`, `showPlanUpdate`, `finishLearningFlow`, `renderAIPlan`, `renderHomeCarouselV116`, `adaptMissionAfterSession`, `continueMissionWithoutAdaptation`, `retryOptionalAdaptation`, `finishOptionalAdaptationFlow`.

## 1.8. Порядок загрузки и late runtime patches
Загрузка в `index.html` происходит строго линейно в 14 фазах:
1. `<head>`: Tailwind CDN (`https://cdn.tailwindcss.com`), Lucide CDN (`https://unpkg.com/lucide@latest`), Google Fonts (`Inter`), базовый `<style>`.
2. Строка 629: `<script id="removeRussianLanguage">` + серия CSS-патчей `v1336`–`v13314`.
3. Строка 904: `<script id="architectureShellV122">` (создание `window.FocusModeApp`).
4. Строка 1028: `<script id="storageServiceV123">` (регистрация `services.storage`).
5. Строки 1150–2050: HTML-разметка лендинга и всех экранов `#app-screen-*` внутри `#iphoneFrame`.
6. Строки 2051–2456: Базовый inline-скрипт прототипа (`applyAppScreenImmediately`, `openFocusChoice`, `selectQuickDuration`, `adjustQuickDuration`, `simulatePremiumPurchase`, `triggerChatAnalysis`, базовый таймер и навигация глав лендинга).
7. Строки 2457–2582: `realTimeScreenTransitions` (`setAppScreen`), `i18nScript` (`setLanguage`, словарь DE/EN), `focusMissionProductLogic`, `cleanMissionHomeLogic`, `missionInputAndConfidenceLogic`, `focusProtectionLogic`, `pwaBoot`.
8. Строки 2583–2831: `realAIMissionV6`, `adaptiveMissionV7`, `mobileUIV81`.
9. Строки 2832–3223: `sessionHistoryCoreV111`, `sessionCoreV11`, `ultraCosmicV91`, `homeHeroV103`, `missionDepthV105`, `sessionRecoveryV112`.
10. Строки 3227–3700: `multiMissionCoreV115`, `sessionRecoveryAndHomeHotfixV1151`, `multiMissionUXV116`, `missionCardPaintFixV1161`, `adaptiveMissionCardsV117`, `missionAuraBlendV1181`.
11. Строки 4220–5014: `sessionPersistenceDesignV128`, `sessionRepositoryV129`, `sessionPersistenceMigrationV13`, `recoveryRepositoryMigrationV131`, `criticalFlowFixV1311`, `sessionFlowStabilizationV1312`, `authoritativeRecoveryRouterV1314`.
12. Строки 5015–5170: `finalizedSessionGuardV1315`, `runtimeOwnershipV132`, `sessionRecoveryControllerV1321`, `missionFlowControllerV1322`, `legacyCleanupGateV1323`, `architectureStabilizationV1325`.
13. Строки 5174–5280: `optionalAdaptationControllerV133`.
14. Строки 5281–5465 (Late runtime patches): безымянный DOMContentLoaded-инициализатор кольца Quick Focus (5281), `missionDepthV1338Logic` (5281), `cleanQuickFocusV13313Logic` (5355), `focusClarityLogicV13314` (5367), `planDurationProtectionRestoreRuntimeV13315` (5417).

## 1.9. Текущие глобальные переменные
Скрипты разделяют мутабельное состояние через глобальные лексические (`let`/`var`) и `window.*` переменные:
* **Сессии и таймер:** `activeSession`, `sessionRecoveryCandidate`, `timerInterval`, `timerDuration`, `originalDuration`, `ucEndsAt`, `ucRaf`, `ucRunning`, `currentSessionStartedAt`, `currentSessionPlannedMinutes`, `currentSessionActualMinutes`, `sessionCheckpointInterval`, `quickDuration`, `quickBreak`.
* **Миссии и AI:** `pendingAIPlan`, `activeAIPlan`, `missionProgress`, `learningFlowActive`, `selectedMissionType`, `selectedConfidence`, `selectedMissionFile`, `missionImageDataUrl`, `selectedSessionStatus`, `selectedConfidenceRating`.
* **UI и оболочка:** `appScreenTransitionToken`, `appScreenAnimation`, `activeHistoryFilter`, `isPremiumUser`, `last` (последний экран прототипа).

## 1.10. Текущие цепочки function wrapping (Monkey-Patching Chains)
В `v14` расширение поведения реализовано через последовательное оборачивание глобальных функций (`const prev = window.fn; window.fn = function(...) { ... prev.apply(this, arguments); }`):
1. **`applyAppScreenImmediately` (12 уровней обёрток):**
   Базовое переключение `.hidden` (2063) $\rightarrow$ `pwaBoot` (`syncPWAChrome`, 2575) $\rightarrow$ `sessionHistoryCoreV111` (рендер History/HomeStats/Stats, 2971) $\rightarrow$ `ultraCosmicV91` (`startUc`/`stopUc`, 3049) $\rightarrow$ `homeHeroV103` (3058) $\rightarrow$ `missionDepthV105` (3062) $\rightarrow$ `multiMissionCoreV115` (привязка выбранной миссии на `plan` и `renderMultiMissionUIV115`, 3280) $\rightarrow$ `multiMissionUXV116` (`renderHomeCarouselV116` / `renderMissionManagementV116`, 3484) $\rightarrow$ `missionAuraBlendV1181` (3699) $\rightarrow$ `authoritativeRecoveryRouterV1314` (блокировка ухода на `home` при активном Recovery, 4905) $\rightarrow$ `optionalAdaptationControllerV133` (подготовка экрана `result`, 5273) $\rightarrow$ `cleanQuickFocusV13313Logic` (переключение класса `html.quick-focus-surface`, 5358) $\rightarrow$ `planDurationProtectionRestoreRuntimeV13315` (`refreshPlanSupportLayout`, 5417).
2. **`setAppScreen` (4 уровня):**
   `realTimeScreenTransitions` (2457) $\rightarrow$ `sessionRecoveryV112` (3208) $\rightarrow$ `authoritativeRecoveryRouterV1314` (4916, финальный владелец).
3. **`startSession` (6 уровней):**
   `sessionCoreV11` (2993) $\rightarrow$ `sessionRecoveryV112` (3177) $\rightarrow$ `sessionRecoveryAndHomeHotfixV1151` (3367) $\rightarrow$ `sessionFlowStabilizationV1312` (4874) $\rightarrow$ `finalizedSessionGuardV1315` (5065) $\rightarrow$ `sessionRecoveryControllerV1321` (5105).
4. **`resumeRecoveredSession` (7 уровней):**
   `sessionRecoveryV112` (3139) $\rightarrow$ `multiMissionCoreV115` (3279) $\rightarrow$ `sessionRecoveryAndHomeHotfixV1151` (3305) $\rightarrow$ `recoveryRepositoryMigrationV131` (4619) $\rightarrow$ `criticalFlowFixV1311` (4729) $\rightarrow$ `sessionFlowStabilizationV1312` (4837) $\rightarrow$ `authoritativeRecoveryRouterV1314` (4981) $\rightarrow$ `sessionRecoveryControllerV1321` (5105).
5. **`showPlanUpdate` (6 уровней):**
   Базовый стаб (2313) $\rightarrow$ `adaptiveMissionV7` (2764) $\rightarrow$ `sessionRecoveryV112` (3192) $\rightarrow$ `multiMissionCoreV115` (3277) $\rightarrow$ `sessionFlowStabilizationV1312` (4859) $\rightarrow$ `finalizedSessionGuardV1315` (5073) $\rightarrow$ `missionFlowControllerV1322` (5107) $\rightarrow$ **полная замена** в `optionalAdaptationControllerV133` (`g.showPlanUpdate = adaptMissionAfterSession`, 5274).
6. **`renderAIPlan` (6 уровней):**
   `realAIMissionV6` (2676) $\rightarrow$ `adaptiveMissionV7` (2737) $\rightarrow$ `mobileUIV81` (2813) $\rightarrow$ `missionDepthV105` (3062) $\rightarrow$ `multiMissionCoreV115` (3270) $\rightarrow$ `missionFlowControllerV1322` (5107) $\rightarrow$ `planDurationProtectionRestoreRuntimeV13315` (5417).

---

# 2. Architectural invariants

Ниже перечислены 17 нерушимых архитектурных и продуктовых инвариантов `v14 Recovery Merge`. Любой этап `v15.1`–`v15.12`, нарушающий хотя бы один из этих пунктов, считается провалившимся и подлежит немедленному откату.

| № | Инвариант | Механизм обеспечения в коде v14 | Способ верификации в v15 |
| :--- | :--- | :--- | :--- |
| **1** | **Quick Focus не изменяет Mission** | Сессии `type: 'quick'` создаются с `missionId: null, missionStepId: null` и никогда не вызывают `MissionRepository.update` / `completeStep` и не меняют `selectedMissionId`. | Сравнение снимка `localStorage['focus-mode-missions-v11-5']` до и после полного цикла Quick Focus (байт-в-байт идентичность). |
| **2** | **Mission Session содержит `missionId` и `missionStepId`** | `createMissionSession(mid, sid)` проверяет существование активной миссии и совпадение `m.currentStepId === sid`; `validateSession` в `SessionRepository` отклоняет `type === 'mission'` без непустых `missionId` и `missionStepId`. | Unit-проверка `validateSession({ type: 'mission', missionId: null }) === false` + проверка `focusmode-active-session-v11` при старте шага. |
| **3** | **Одна сессия финализируется максимум один раз** | Идемпотентный `terminalCommitV13` в `sessionPersistenceMigrationV13` проверяет токен `session.id + '::' + session.status` в множестве `committedTokens` + защита `finalizedSessionGuardV1315`. | Искусственный двойной вызов `endSessionByUser()` и истечения таймера не создаёт повторного коммита. |
| **4** | **История содержит максимум одну запись для `session.id`** | `SessionRepository.appendHistory(session)` дедуплицирует массив `sessions` в `focusmode-session-history-v11-1` по уникальному `session.id`, объединяя обновлённые поля `result`. | Проверка `new Set(history.map(s => s.id)).size === history.length` после сессии, адаптации и ретрая. |
| **5** | **Recovery сохраняет фактическое время** | `checkpointActiveSessionV13` сохраняет `elapsedMs`; при перезагрузке `resumeAuthoritativelyV1314` вычисляет остаток времени `remainingMs = plannedMs - elapsedMs` и устанавливает `ucEndsAt = Date.now() + remainingMs`. | Перезагрузка вкладки через 5 минут после старта 25-минутной сессии: на экране Recovery 5 мин, после Resume таймер стартует с 20:00. |
| **6** | **Recovery открывает правильную Mission** | При восстановлении сессии типа `mission` роутер проверяет `missionRepository.getById(s.missionId)`, вызывает `missionRepository.setSelected(s.missionId)` и синхронизирует контекст именно этой миссии. | Прерывание сессии по Миссии Б при главной Миссии А $\rightarrow$ перезагрузка $\rightarrow$ Resume и последующий Review относятся строго к Миссии Б. |
| **7** | **Optional Adaptation остаётся необязательной** | На экране `app-screen-result` всегда рендерятся обе кнопки: основная «Plan mit AI anpassen» и вторичная «Ohne Anpassung fortfahren». | Визуальная и DOM-проверка наличия и активности `#continueWithoutAdaptationBtn` на экране `result`. |
| **8** | **Skip Adaptation не вызывает AI** | `continueMissionWithoutAdaptation()` выполняет только локальный `commitMissionSessionResult` с `adaptationMode: 'skipped'` и `renderLocalResult`, не обращаясь к `fetch`. | Вкладка Network в DevTools при нажатии «Ohne Anpassung fortfahren»: 0 запросов к `adapt-mission`. |
| **9** | **Retry не дублирует результат** | `commitMissionSessionResult` фиксирует прогресс шага и историю сессии **до** сетевого запроса и кеширует объект в `state.result`. `retryOptionalAdaptation()` вызывает `adaptMissionAfterSession(true)`, пропуская повторный коммит. | Имитация Offline на первой попытке адаптации $\rightarrow$ Online $\rightarrow$ «Erneut versuchen»: шаг продвинут 1 раз, в истории 1 запись. |
| **10** | **Адаптация изменяет только выбранную Mission** | `missions.replaceRemainingSteps(missionId, updatedSteps, expectedUpdatedAt)` модифицирует строго элемент с `id === missionId` и сверяет `expectedUpdatedAt`. | При двух активных миссиях адаптация Миссии А оставляет массив `steps` и `updatedAt` Миссии Б неизменными. |
| **11** | **До двух активных Mission** | `MissionRepository.create()` и `restore()` проверяют `getActive().length < 2`; при достижении лимита возвращают `{ ok: false, reason: 'active_mission_limit_reached' }`. | Попытка создания 3-й активной миссии или разархивации при 2 активных блокируется с показом немецкого уведомления. |
| **12** | **Cosmic Timer визуально и функционально не меняется** | Скрипт `ultraCosmicV91` (Canvas 2D `#ucCanvas`, геометрия частиц, кольцо прогресса, карточка `#ucFocusCard`, метка `ucEndsAt`) переносится без изменения алгоритмов рендеринга. | Визуальное сличение `app-screen-timer`, проверка `requestAnimationFrame` и компенсации времени после сворачивания вкладки. |
| **13** | **Safe area не меняется** | При входе на `quicksetup` на `document.documentElement` выставляется класс `quick-focus-surface`, окрашивающий `html`, `body`, `#screenWrapper`, `#appBody` в `#f8fafc`; `syncPWAChrome` синхронизирует `meta[name="theme-color"]`. | Проверка в мобильном viewport / iOS Standalone: отсутствие серых полос в верхней и нижней зонах Safe Area. |
| **14** | **Plan duration badges не меняются** | Стили `#planDurationProtectionRestoreV13315` фиксируют `.step-time` (`position: absolute; top: 13px; right: 13px`) и `padding-right: 76px` (94px для активного шага) у `.step-title`. | Проверка `app-screen-plan` на ширинах 320px, 375px, 430px: текст шага не наезжает на бейдж длительности. |
| **15** | **Mission Protection и explanation card сохраняют порядок** | `arrangePlanSupportBlocks()` в `planDurationProtectionRestoreRuntimeV13315` гарантирует, что `#missionProtectionCard` идёт сразу после `#aiPlanSteps`, а `.mission-explanation-card-v1332` (`#aiPlanExplanation`) — строго под ним без `line-clamp`. | Проверка `protectionCard.nextElementSibling === explanationCard` в DOM экрана `plan`. |
| **16** | **Storage keys и форматы не меняются без migration plan** | Все 7 канонических ключей, 3 легаси-ключа, флаг миграции и 6 backup-ключей сохраняют точные имена и JSON-схемы (`schemaVersion: 1`). | Прогон Gate B на предзаполненном `localStorage` со слепком данных пользователя `v14`. |
| **17** | **GitHub Pages и PWA продолжают работать** | Относительные пути `./`, `./manifest.webmanifest`, `./service-worker.js`, `./icons/*`, корректный `base: '/FocusModePWA/'` (или `./`), обновление кэша Service Worker. | Сборка production-бандла, раздача из подпапки `/FocusModePWA/`, проверка установки PWA и перезагрузки в режиме Offline. |

---

# 3. Dependency graph

Ниже представлена полная карта зависимостей всех 20 доменов архитектуры `v14 Recovery Merge` с указанием владельца, входных/исходящих связей, хранилищ, DOM-узлов, слушателей событий, таймеров, обёрток совместимости и позиции в порядке загрузки.

### 1. Домен: `bootstrap` (Architecture Shell, Language Sanitizer & Stabilization)
* **Текущий runtime owner:** `FocusModeApp.core` (`removeRussianLanguage` [629], `architectureShellV122` [904], `legacyCleanupGateV1323` [5109], `architectureStabilizationV1325` [5143]).
* **Входные функции:** `FocusModeApp.core.registerModule(name, def, meta)`, `getModule(name)`, `hasModule(name)`, `listModules()`, `assertReady()`, `assertStable()`.
* **Исходящие зависимости:** Нативный `Map`, `Object.freeze`, `window.localStorage` (только в `removeRussianLanguage`).
* **Storage dependencies:** `focus-mode-language` (принудительная замена `'ru'` $\rightarrow$ `'de'` на старте).
* **DOM dependencies:** `document.title` (устанавливается в `'v14 Recovery Merge'`).
* **Event listeners:** `DOMContentLoaded` (`{ once: true }`) для `assertReady` и `assertStable`.
* **Timers and schedulers:** `setTimeout(assertReady, 0)`, `setTimeout(assertStable, 0)`.
* **Compatibility wrappers:** Инициализирует `window.FocusModeApp` и поддеревья `core`, `state`, `services`, `repositories`, `features`, `compatibility`.
* **Порядок загрузки:** Фаза 1–2 (до всех сервисов и репозиториев, строки 629 и 904) + финальный аудит в Фазе 12 (строки 5109–5170).

### 2. Домен: `navigation` (Screen Transitions & Immediate Screen Switch)
* **Текущий runtime owner:** `FocusModeApp.core.runtime.navigation` (`realTimeScreenTransitions` [2457], базовый `applyAppScreenImmediately` [2063], `authoritativeRecoveryRouterV1314` [4905–4916]).
* **Входные функции:** `setAppScreen(screenName, options)`, `applyAppScreenImmediately(screenName)`, `getAppTransitionDuration()`, `animateAppBody()`, `openProto(screen)`, `closeProto()`.
* **Исходящие зависимости:** Вызывает цепочку подписчиков переключения экрана (`syncPWAChrome`, `renderSessionHistory`, `refreshHomeStats`, `refreshStatsScreen`, `startUc`/`stopUc`, `renderMultiMissionUIV115`, `renderHomeCarouselV116`, `renderMissionManagementV116`, `offered`, `refreshPlanSupportLayout`), `lucide.createIcons()`.
* **Storage dependencies:** Нет прямых (косвенно через `authoritativeRecoveryRouterV1314` проверяет наличие активного кандидата восстановления).
* **DOM dependencies:** `#appBody`, `#screenWrapper`, `.app-screen`, `#app-screen-${screenName}`, `#modal`, `#mainMount`, `#modalMount`, `#iphoneFrame`, `document.documentElement` (`.quick-focus-surface`).
* **Event listeners:** `window.resize`, `document.wheel` (`keepPhoneScrollInside`), `document.keydown` (`Escape` $\rightarrow$ `closeProto`).
* **Timers and schedulers:** Web Animations API (`body.animate`), `requestAnimationFrame`.
* **Compatibility wrappers:** Глобальные `window.setAppScreen` и `window.applyAppScreenImmediately` с 12-слойной цепочкой обёрток.
* **Порядок загрузки:** Базовая функция — Фаза 6 (строка 2063); анимации — Фаза 7 (строка 2457); защитный перехват роутера — Фаза 11 (строка 4905); UI-хуки — Фаза 14 (строки 5358, 5417).

### 3. Домен: `storage` (Storage Service & I18n Persistence)
* **Текущий runtime owner:** `FocusModeApp.services.storage` (`storageServiceV123` [1028]) + `i18nScript` [2457].
* **Входные функции:** `isAvailable()`, `getText(key, fallback)`, `setText(key, value)`, `getJSON(key, fallback, options)`, `setJSON(key, value)`, `remove(key)`, `has(key)`, `backup(sourceKey, backupKey)`, `setLanguage(lang, save)`, `detectLanguage()`.
* **Исходящие зависимости:** `FocusModeApp.core.registerModule`, `window.localStorage`, `structuredClone`.
* **Storage dependencies:** Инкапсулирует все ключи `localStorage`; напрямую владеет `focus-mode-language`.
* **DOM dependencies:** `#langPicker`, `#langToggle`, `#langMenu`, текстовые узлы документа (для `i18nScript`).
* **Event listeners:** `DOMContentLoaded`, `click` по переключателю языка, `MutationObserver` на `document.body` (автоперевод динамических узлов в `i18nScript`).
* **Timers and schedulers:** Нет.
* **Compatibility wrappers:** Регистрируется как `FocusModeApp.services.storage`.
* **Порядок загрузки:** Фаза 4 (строка 1028) — строго сразу после `architectureShellV122` и до любых репозиториев.

### 4. Домен: `Session Repository`
* **Текущий runtime owner:** `FocusModeApp.repositories.sessions` (`sessionPersistenceDesignV128` [4220], `sessionRepositoryV129` [4313], `sessionPersistenceMigrationV13` [4455], `recoveryRepositoryMigrationV131` [4571]).
* **Входные функции:** `loadActive()`, `saveActive(session)`, `clearActive()`, `loadLast()`, `saveLast(session)`, `listHistory()`, `saveHistory(list)`, `appendHistory(session)`, `getById(id)`, `save(session)`, `inspect()`, `isRecoverableSession(session)`.
* **Исходящие зависимости:** `FocusModeApp.services.storage`, `FocusModeApp.core.registerModule`.
* **Storage dependencies:**
  * Канонические: `focusmode-active-session-v11`, `focusmode-last-session-v11`, `focusmode-session-history-v11-1`;
  * Легаси fallback: `focusmode-session-history-v11`;
  * Карантин/бэкапы: `focusmode-active-session-v11-repository-backup`, `focusmode-last-session-v11-repository-backup`, `focusmode-session-history-repository-backup`.
* **DOM dependencies:** Нет (чистый слой данных).
* **Event listeners:** Нет.
* **Timers and schedulers:** Нет.
* **Compatibility wrappers:** Синхронизирует `window.activeSession` и `window.sessionRepository`.
* **Порядок загрузки:** Фаза 11 (строки 4220–4680), до контроллеров v13.2.

### 5. Домен: `Mission Repository`
* **Текущий runtime owner:** `FocusModeApp.repositories.missions` (`multiMissionCoreV115` [3227]).
* **Входные функции:** `loadStore()`, `getAll()`, `getActive()`, `getArchived()`, `getCompleted()`, `getById(id)`, `getSelected()`, `getSelectedId()`, `getPrimary()`, `create(input)`, `save(mission)`, `update(id, patch)`, `setSelected(id)`, `setPrimary(id)`, `completeStep(id, sid, result)`, `replaceRemainingSteps(id, steps, expectedUpdatedAt)`, `archive(id)`, `restore(id)`, `delete(id)`, `rename(id, title)`.
* **Исходящие зависимости:** `FocusModeApp.services.storage`, `FocusModeApp.core.registerModule`, `bindMissionCompatibilityV115(mission)`.
* **Storage dependencies:**
  * Канонический: `focus-mode-missions-v11-5`;
  * Флаг миграции: `focus-mode-missions-v11-5-migrated`;
  * Легаси-источники и зеркала: `focus-mode-active-mission`, `focus-mode-mission-progress-v7`;
  * Карантин: `focus-mode-missions-v11-5-corrupt-backup`.
* **DOM dependencies:** Вызывает UI-обновление `renderMultiMissionUIV115()` в обёртках совместимости.
* **Event listeners:** `DOMContentLoaded` (первичная синхронизация выбранной миссии).
* **Timers and schedulers:** `setTimeout(renderMultiMissionUIV115, 0)`.
* **Compatibility wrappers:** Экспортирует `window.missionRepository`, поддерживает актуальность `window.activeAIPlan` и `window.missionProgress`.
* **Порядок загрузки:** Фаза 10 (строка 3227).

### 6. Домен: `Session Core`
* **Текущий runtime owner:** `FocusModeApp.core.sessionRecovery` (`sessionCoreV11` [2974], `sessionPersistenceMigrationV13` [4455], `sessionRecoveryControllerV1321` [5105]).
* **Входные функции:** `makeSession(input)`, `validateSession(session)`, `createQuickSession(input)`, `createMissionSession(missionId, stepId)`, `startSession(session)`, `finishSession(outcome)`, `endSessionByUser()`, `stopFocusSession()`, `actualSessionMinutes(session)`.
* **Исходящие зависимости:** `FocusModeApp.repositories.sessions`, `FocusModeApp.repositories.missions`, `setAppScreen`, `renderSession`, `updateTimerUI`.
* **Storage dependencies:** Через `SessionRepository` (`focusmode-active-session-v11`, `focusmode-last-session-v11`, `focusmode-session-history-v11-1`).
* **DOM dependencies:** `#islandIndicator`, `#timerDisplay`, `#timerModeLabel`, `#timerTaskTitle`, `#timerTaskValue`, `#timerProgressText`.
* **Event listeners:** Нет прямых (вызывается из UI и контроллеров).
* **Timers and schedulers:** Управляет `timerInterval` (1000 мс).
* **Compatibility wrappers:** `window.startSession`, `window.endSessionByUser`, `window.completeLearningSession`, `window.stopFocusSession`.
* **Порядок загрузки:** Базовая логика — Фаза 9 (строка 2974); миграция персистентности — Фаза 11 (строка 4455); финальный контроллер — Фаза 12 (строка 5105).

### 7. Домен: `Session Runtime`
* **Текущий runtime owner:** `FocusModeApp.core.sessionRecovery.runtime` (`sessionRecoveryV112` [3063], `sessionPersistenceMigrationV13` [4455], `sessionFlowStabilizationV1312` [4767], `focusClarityLogicV13314` [5367]).
* **Входные функции:** `startSessionRuntime()`, `stopSessionRuntime()`, `checkpointActiveSession(reason, persist)`, `renderSession(session)`, `updateTimerUI()`, `syncFocusClarityCard()`.
* **Исходящие зависимости:** `FocusModeApp.repositories.sessions.saveActive`, `stopUc()`.
* **Storage dependencies:** Периодическая запись чекпоинтов в `focusmode-active-session-v11`.
* **DOM dependencies:** `#ucFocusCard`, `#ucTaskCardTitle`, `#timerTaskValue`, `#islandIndicator`.
* **Event listeners:** `visibilitychange`, `pagehide`, `beforeunload` (сохраняют чекпоинт при сворачивании вкладки или закрытии страницы), `keydown` на `#ucFocusCard`.
* **Timers and schedulers:** `sessionCheckpointInterval` (`setInterval` каждые 5000 мс во время активной сессии).
* **Compatibility wrappers:** `window.checkpointActiveSession`, `window.startSessionRuntime`, `window.stopSessionRuntime`, `window.renderSession`.
* **Порядок загрузки:** Фаза 9 (строка 3063) $\rightarrow$ Фаза 11 (строка 4521) $\rightarrow$ Фаза 14 (строка 5367).

### 8. Домен: `History`
* **Текущий runtime owner:** `FocusModeApp.repositories.sessions` + UI-рендерер истории (`sessionHistoryCoreV111` [2832], `sessionPersistenceMigrationV13` [4455]).
* **Входные функции:** `renderSessionHistory(filter)`, `setHistoryFilter(filter, btn)`, `refreshSessionHistoryUI()`, `createHistoryCard(session)`, `createHistoryEmpty(filter, totalCount)`, `formatHistoryDuration(minutes)`, `countedFocus(session)`.
* **Исходящие зависимости:** `FocusModeApp.repositories.sessions.listHistory()`, `lucide.createIcons()`.
* **Storage dependencies:** `focusmode-session-history-v11-1` (чтение через репозиторий), `focusmode-session-history-corrupt-backup`.
* **DOM dependencies:** `#app-screen-history`, `#historySessionList`, `#historyTotalMinutes`, `#historySessionCount`, кнопки фильтров `[data-history-filter]`.
* **Event listeners:** `DOMContentLoaded`, клики по фильтрам `'today' | 'week' | 'all'`.
* **Timers and schedulers:** Нет.
* **Compatibility wrappers:** Подключён к цепочке `applyAppScreenImmediately('history')` и `refreshSessionConsumersV13()`.
* **Порядок загрузки:** Фаза 9 (строка 2832) + подключение к репозиторию в Фазе 11 (строка 4455).

### 9. Домен: `Statistics`
* **Текущий runtime owner:** Агрегатор статистики в `sessionHistoryCoreV111` [2832] и `sessionPersistenceMigrationV13` [4455].
* **Входные функции:** `refreshHomeStats()`, `refreshStatsScreen()`, `getWeekDailyMinutes()`, `refreshSessionConsumersV13()`.
* **Исходящие зависимости:** `FocusModeApp.repositories.sessions.listHistory()`.
* **Storage dependencies:** `focusmode-session-history-v11-1`.
* **DOM dependencies:** `#homeTodayFocus`, `#homeWeekSessions`, `#homeWeekFocus`, `#homeWeekProgress`, `#statsWeekChart`, `#statsTotalMinutes`, `#statsCompletedCount`.
* **Event listeners:** Вызывается при `DOMContentLoaded`, при переходе на `home` или `stats`, а также синхронно после каждого `terminalCommitV13`.
* **Timers and schedulers:** `setTimeout(refreshHomeStats, 20)` в легаси-хуке `mobileUIV81`.
* **Compatibility wrappers:** `window.refreshHomeStats`, `window.refreshStatsScreen`, `FocusModeApp.services.sessionPersistence.refreshConsumers`.
* **Порядок загрузки:** Фаза 9 (строка 2832) $\rightarrow$ Фаза 11 (строка 4455).

### 10. Домен: `Recovery` (Session Recovery Logic & UI)
* **Текущий runtime owner:** `FocusModeApp.core.sessionRecovery.recovery` (`sessionRecoveryV112` [3063], `sessionRecoveryAndHomeHotfixV1151` [3284], `recoveryRepositoryMigrationV131` [4571], `criticalFlowFixV1311` [4685], `sessionFlowStabilizationV1312` [4767], `sessionRecoveryControllerV1321` [5105]).
* **Входные функции:** `readRecoveryCandidate()`, `getRecoverableSessionV1151()`, `renderSessionRecovery(candidate)`, `resumeRecoveredSession()`, `discardRecoveredSession()`, `finishRecoveredSession()`.
* **Исходящие зависимости:** `FocusModeApp.repositories.sessions`, `FocusModeApp.repositories.missions`, `setAppScreen`, `startSessionRuntime`, `terminalCommitV13`.
* **Storage dependencies:** `focusmode-active-session-v11`, `focusmode-active-session-v11-recovery-backup`.
* **DOM dependencies:** `#app-screen-session-recovery`, `#recoverySessionTitle`, `#recoverySessionMeta`, `#recoveryElapsedValue`, `#recoveryRemainingValue`, `#recoveryResumeBtn`, `#recoveryFinishBtn`, `#recoveryDiscardBtn`.
* **Event listeners:** `DOMContentLoaded`, `pageshow`.
* **Timers and schedulers:** `setInterval` таймера после `resumeRecoveredSession`.
* **Compatibility wrappers:** `window.readRecoveryCandidate`, `window.getRecoverableSessionV1151`, `window.resumeRecoveredSession`, `window.discardRecoveredSession`, `window.finishRecoveredSession`.
* **Порядок загрузки:** Фазы 9, 10, 11, 12 (строки 3063 $\rightarrow$ 3284 $\rightarrow$ 4571 $\rightarrow$ 4685 $\rightarrow$ 4767 $\rightarrow$ 5105).

### 11. Домен: `Recovery Router` (Authoritative Recovery Router)
* **Текущий runtime owner:** `FocusModeApp.state.recoveryRouterV1314` (`authoritativeRecoveryRouterV1314` [4887]).
* **Входные функции:** `applyAppScreenImmediatelyV1314(screenName)`, `setAppScreenV1314(screenName, options)`, `resumeAuthoritativelyV1314()`, `routeExistingSessionV1151()`.
* **Исходящие зависимости:** `FocusModeApp.repositories.sessions`, `FocusModeApp.repositories.missions`, `startUc()`, `startSessionRuntime()`.
* **Storage dependencies:** `focusmode-active-session-v11`.
* **DOM dependencies:** `#app-screen-session-recovery`, `#recoveryResumeBtn`, `[onclick*="resumeRecoveredSession"]`, `#app-screen-timer`.
* **Event listeners:** Capture-перехватчик `document.addEventListener('click', ..., true)` на кнопку возобновления сессии (гарантирует однократный запуск без конфликта старых обработчиков), `DOMContentLoaded`.
* **Timers and schedulers:** Управляет флагом `resumeInFlight` и перезапуском секундного `timerInterval`.
* **Compatibility wrappers:** Финальная обёртка для `window.setAppScreen` и `window.resumeRecoveredSession`.
* **Порядок загрузки:** Фаза 11 (строка 4887), строго после `sessionFlowStabilizationV1312`.

### 12. Домен: `Finalized Session Guard`
* **Текущий runtime owner:** `FocusModeApp.compatibility.finalizedSessionGuard` (`finalizedSessionGuardV1315` [5015]).
* **Входные функции:** `purgeFinalizedActiveSessionV1315(reason)`, `startSessionV1315(session)`, `showPlanUpdateV1315()`, `finishLearningFlowV1315()`.
* **Исходящие зависимости:** `FocusModeApp.repositories.sessions` (`loadActive`, `clearActive`, `listHistory`), `FocusModeApp.repositories.missions`.
* **Storage dependencies:** Очищает зависшие или уже попавшие в `focusmode-session-history-v11-1` сессии из `focusmode-active-session-v11`.
* **DOM dependencies:** Нет прямых.
* **Event listeners:** `DOMContentLoaded` (аудит на старте).
* **Timers and schedulers:** Нет.
* **Compatibility wrappers:** Оборачивает `startSession`, `showPlanUpdate`, `finishLearningFlow`.
* **Порядок загрузки:** Фаза 12 (строка 5015), непосредственно перед `runtimeOwnershipV132`.

### 13. Домен: `Mission Flow` (Mission Lifecycle & Intake)
* **Текущий runtime owner:** `FocusModeApp.features.missionFlow` (`focusMissionProductLogic` [2458], `cleanMissionHomeLogic` [2496], `missionInputAndConfidenceLogic` [2507], `multiMissionCoreV115` [3227], `criticalFlowFixV1311` [4685], `missionFlowControllerV1322` [5107], `missionDepthV1338Logic` [5281]).
* **Входные функции:** `openGoalIntake()`, `selectMissionType(btn)`, `selectConfidence(btn)`, `handleMissionFile(event)`, `compressMissionImage(file)`, `confirmAIPlan()`, `openMission(id)`, `prepareLearningSession(mid, sid)`, `finishLearningFlow()`.
* **Исходящие зависимости:** `FocusModeApp.repositories.missions`, `FocusModeApp.repositories.sessions`, `startSession`, `renderAIPlan`, `setAppScreen`.
* **Storage dependencies:** `focus-mode-missions-v11-5`, `focus-mode-last-ai-plan`.
* **DOM dependencies:** `#app-screen-goal`, `#goalPrompt`, `#missionTypeCarousel`, `.mg-confidence-track`, `#app-screen-filepicker`, `#app-screen-confirm`, `#app-screen-plan`.
* **Event listeners:** `scroll` на `#missionTypeCarousel` (debounced 110ms), `change` на file input, `DOMContentLoaded`.
* **Timers and schedulers:** `setTimeout` debounce скролла карусели типов миссии (110 мс).
* **Compatibility wrappers:** `window.confirmAIPlan`, `window.openMission`, `window.prepareLearningSession`, `window.finishLearningFlow`.
* **Порядок загрузки:** Фазы 7, 10, 11, 12, 14.

### 14. Домен: `Multi-Mission UX` (Home Carousel, Aura Blend & Mission Management)
* **Текущий runtime owner:** Слой Multi-Mission UX (`multiMissionUXV116` [3427], `missionCardPaintFixV1161` [3551], `adaptiveMissionCardsV117` [3639], `missionAuraBlendV1181` [3692]).
* **Входные функции:** `renderHomeCarouselV116()`, `sortedActiveMissionsV116()`, `missionViewModelV116(m)`, `renderMissionHeroV116(vm)`, `openMissionManagementV116()`, `renderMissionManagementV116()`, `openMissionActionsV116(id)`, `openRenameMissionV116(id)`, `saveRenameMissionV116(id)`, `confirmArchiveV116(id)`, `archiveMissionActionV116(id)`, `restoreMissionActionV116(id)`, `confirmDeleteV116(id)`, `makePrimaryV116(id)`, `fitMissionCardTitlesV117()`.
* **Исходящие зависимости:** `FocusModeApp.repositories.missions`, `FocusModeApp.repositories.sessions` (проверка отсутствия активной сессии перед архивацией/удалением), `setAppScreen`, `lucide.createIcons()`.
* **Storage dependencies:** Чтение/запись через `MissionRepository` (`focus-mode-missions-v11-5`).
* **DOM dependencies:** `#missionAuraShellV1181`, `#missionCarouselV1181`, `#activeMissionCard`, `#emptyMissionCard`, `#app-screen-missions`, `#missionSheetV116`, `#missionSheetCardV116`.
* **Event listeners:** `scroll` (`{ passive: true }`) на `#missionCarouselV1181` для плавного перетекания ауры, `resize` для `fitMissionCardTitlesV117`.
* **Timers and schedulers:** `requestAnimationFrame` при скролле карусели, `setTimeout(..., 180)` при переходе из модального окна выбора.
* **Compatibility wrappers:** `window.renderHomeCarouselV116`, `window.openMissionManagementV116` и inline-обработчики действий шторки `#missionSheetV116`.
* **Порядок загрузки:** Фаза 10 (строки 3427–3700) + регистрация в `missionFlowControllerV1322` (строка 5107).

### 15. Домен: `Quick Focus`
* **Текущий runtime owner:** Session Core + Quick Focus UI (`sessionCoreV11` [2974], базовый скрипт [2270–2310], `cleanQuickFocusV13313Logic` [5355], `focusClarityLogicV13314` [5367]).
* **Входные функции:** `openFocusChoice()`, `closeFocusChoice()`, `openQuickSetupFromChoice()`, `selectQuickDuration(btn, val)`, `adjustQuickDuration(delta)`, `updateQuickRing()`, `startQuickFocus()`, `startConfiguredQuickFocus()`, `createQuickSession(input)`.
* **Исходящие зависимости:** `startSession`, `routeExistingSessionV1151`, `setAppScreen`.
* **Storage dependencies:** Только сессионные ключи через `SessionRepository`. **Никаких обращений к `MissionRepository`.**
* **DOM dependencies:** `#app-screen-quicksetup`, `#quickFocusModal`, `#quickRingProgress`, `#quickDurationValue`, `.qb-task`, `.qb-app`, `document.documentElement.classList` (`.quick-focus-surface`).
* **Event listeners:** `DOMContentLoaded` (инициализация `quickBreak = 0; updateQuickRing()`).
* **Timers and schedulers:** Нет собственных (передаёт управление в `Session Core`).
* **Compatibility wrappers:** `window.openFocusChoice`, `window.startConfiguredQuickFocus`, `window.selectQuickDuration`, `window.adjustQuickDuration`.
* **Порядок загрузки:** Фаза 6 (строка 2270) $\rightarrow$ Фаза 9 (строка 2974) $\rightarrow$ Фаза 10 (строка 3450) $\rightarrow$ Фаза 14 (строки 5281, 5355, 5367).

### 16. Домен: `Optional Adaptation` (Controller, Skip & Retry)
* **Текущий runtime owner:** `FocusModeApp.features.optionalAdaptation` (`optionalAdaptationControllerV133` [5174]).
* **Входные функции:** `adaptMissionAfterSession(retry)`, `continueMissionWithoutAdaptation()`, `retryOptionalAdaptation()`, `finishOptionalAdaptationFlow()`, `commitMissionSessionResult(result)`, `offered()`.
* **Исходящие зависимости:** `FocusModeApp.repositories.missions` (`completeStep`, `replaceRemainingSteps`), `FocusModeApp.repositories.sessions` (`loadLast`, `saveLast`, `appendHistory`, `clearActive`), `requestAdaptation(result)`, `setAppScreen`, `refreshSessionConsumersV13()`.
* **Storage dependencies:** `focus-mode-missions-v11-5`, `focusmode-last-session-v11`, `focusmode-session-history-v11-1`, `focusmode-active-session-v11`.
* **DOM dependencies:** `#app-screen-result`, `#sessionStatusButtons`, `#confidenceRatingButtons`, `#sessionFeedbackText`, `#app-screen-feedbackprocessing`, `#app-screen-update`, `#optionalAdaptationRetryActions`.
* **Event listeners:** `DOMContentLoaded` (монтирование кнопок и обработчиков).
* **Timers and schedulers:** `setTimeout(offered, 0)` при входе на экран `result`.
* **Compatibility wrappers:** Переназначает `window.showPlanUpdate = adaptMissionAfterSession`, экспортирует `adaptMissionAfterSession`, `continueMissionWithoutAdaptation`, `retryOptionalAdaptation`, `finishOptionalAdaptationFlow`.
* **Порядок загрузки:** Фаза 13 (строка 5174), после всех контроллеров `v13.2`.

### 17. Домен: `AI generation` (`create-mission` client)
* **Текущий runtime owner:** `realAIMissionV6` [2583], стабилизирован в `criticalFlowFixV1311` [4685].
* **Входные функции:** `analyzeGoal()`, `normalizeAIPlan(raw)`, `missionTypeForAPI()`, `confidenceForAPI()`, `resetAnalysisRows()`, `completeAnalysisRow(idx)`, `escapeHTML(str)`.
* **Исходящие зависимости:** `fetch(FOCUS_AI_ENDPOINT)` (`https://focusmodedemoai.illia-pohukai.workers.dev/create-mission`), `FocusModeApp.services.storage.setJSON('focus-mode-last-ai-plan', ...)`, `setAppScreen`.
* **Storage dependencies:** `focus-mode-last-ai-plan`.
* **DOM dependencies:** `#app-screen-analyzing`, `#app-screen-confirm`, `#goalPrompt`, `#confirmGoalTitle`, `#confirmTotalTime`, `#confirmSessionsPreview`.
* **Event listeners:** Нет прямых.
* **Timers and schedulers:** `AbortController` с `setTimeout(..., 120000)` (таймаут 120 с) + пошаговые `setTimeout` анимации строк анализа.
* **Compatibility wrappers:** Записывает результат в `window.pendingAIPlan`.
* **Порядок загрузки:** Фаза 8 (строка 2583) + патч в Фазе 11 (строка 4685).

### 18. Домен: `AI adaptation` (`adapt-mission` client)
* **Текущий runtime owner:** Внутренний клиент адаптации внутри `optionalAdaptationControllerV133` [5250–5268].
* **Входные функции:** `requestAdaptation(result)`, `normalizeUpdatedSessions(raw)`.
* **Исходящие зависимости:** `fetch(FOCUS_AI_ADAPT_ENDPOINT)` (`https://focusmodedemoai.illia-pohukai.workers.dev/adapt-mission`), `FocusModeApp.repositories.missions.getById`, `FocusModeApp.repositories.sessions.listHistory`.
* **Storage dependencies:** Читает контекст миссии и историю через репозитории; запись новых шагов делегирует в `optionalAdaptationControllerV133` (`missions.replaceRemainingSteps`).
* **DOM dependencies:** Нет прямых (чистый сетевой + нормализационный слой внутри контроллера).
* **Event listeners:** Нет.
* **Timers and schedulers:** `AbortController` с `setTimeout(..., 120000)` (таймаут 120 с).
* **Compatibility wrappers:** Вызывается исключительно из `adaptMissionAfterSession`.
* **Порядок загрузки:** Фаза 13 (строка 5174).

### 19. Домен: `Cosmic Timer`
* **Текущий runtime owner:** `ultraCosmicV91` [3027] + `focusClarityLogicV13314` [5367].
* **Входные функции:** `initUcCanvas()`, `startUc()`, `stopUc()`, `drawUc(ts)`, `syncUcData()`, `showUcTask()`, `hideUcTask()`.
* **Исходящие зависимости:** Читает глобальные `ucEndsAt`, `timerDuration`, `originalDuration`, `activeSession`.
* **Storage dependencies:** Нет.
* **DOM dependencies:** `#app-screen-timer`, `canvas#ucCanvas`, `#ucTime`, `#ucProgress`, `#ucFocusCard`, `#ucTaskModal`.
* **Event listeners:** `window.resize` (пересчёт DPI и размеров `canvas`), `keydown` на `#ucFocusCard`.
* **Timers and schedulers:** Цикл `requestAnimationFrame(drawUc)` (активен только когда открыт экран `timer`).
* **Compatibility wrappers:** Подключён к `applyAppScreenImmediately` (`screen === 'timer' ? startUc() : stopUc()`).
* **Порядок загрузки:** Фаза 9 (строка 3027) + расширение доступности карточки в Фазе 14 (строка 5367).

### 20. Домен: `PWA bootstrap`
* **Текущий runtime owner:** `pwaBoot` [2562], `manifest.webmanifest`, `service-worker.js`.
* **Входные функции:** `syncPWAChrome(screen)`, регистрация `navigator.serviceWorker.register('./service-worker.js')`.
* **Исходящие зависимости:** `FocusModeApp.services.storage` (`focus-pwa-help-v11`), Cache API браузера.
* **Storage dependencies:** `focus-pwa-help-v11`.
* **DOM dependencies:** `#pwaInstallHelp`, `#pwaInstallClose`, `meta[name="theme-color"]`, `meta#appleStatusBarStyle`.
* **Event listeners:** `window.load` (регистрация SW), `click` на `#pwaInstallClose`.
* **Timers and schedulers:** Нет.
* **Compatibility wrappers:** Оборачивает `applyAppScreenImmediately` для динамической смены цвета статус-бара между тёмными и светлыми экранами.
* **Порядок загрузки:** Фаза 7 (строка 2562).

---

# 4. Authoritative owner map

Для каждой из 18 критических функций `v14 Recovery Merge` ниже зафиксированы: точка первого объявления, полная цепочка последующих обёрток (wrappers), фактическая финальная реализация, политика сохранения и удаления обёрток, а также уровень регрессионного риска при переносе.

### 1. `setAppScreen(screenName, options)`
* **Где впервые появляется:** `realTimeScreenTransitions` (строка 2457) — асинхронная анимация перехода через Web Animations API и вызов `applyAppScreenImmediately`.
* **Какие wrappers применяются позже:**
  1. `sessionRecoveryV112` (строка 3208) — ранняя проверка кандидата восстановления;
  2. `authoritativeRecoveryRouterV1314` (строка 4916, `setAppScreenV1314`) — перехватывает навигацию на `'home'`, если существует валидная незавершённая сессия и не идёт `resumeInFlight`, перенаправляя на `'session-recovery'`.
* **Финальная авторитетная реализация:** `setAppScreenV1314` в `authoritativeRecoveryRouterV1314` (строка 4916), диспетчеризуемая через `FocusModeApp.core.runtime.navigation.go`.
* **Какие wrappers должны временно сохраниться:** Обёртка `authoritativeRecoveryRouterV1314` вокруг `realTimeScreenTransitions`.
* **Условие удаления wrappers:** В `v15.10 (Navigation and Bootstrap)`, когда роутинг-гард восстановления будет встроен как явный pre-hook внутри `src/core/navigation.js`, а мёртвая промежуточная обёртка из `sessionRecoveryV112` будет отцеплена.
* **Regression risk при переносе:** **Critical** (ошибка порядка вызовов приведёт к проскоку на `home` при перезагрузке во время активной сессии).

### 2. `applyAppScreenImmediately(screenName)`
* **Где впервые появляется:** Базовый inline-скрипт (строка 2063) — скрывает все `.app-screen` и снимает `.hidden` с `#app-screen-${screenName}`.
* **Какие wrappers применяются позже (в порядке наслоения):**
  1. `pwaBoot` (2575) — `syncPWAChrome(screen)`;
  2. `sessionHistoryCoreV111` (2971) — рендер `history` / `home` / `stats`;
  3. `ultraCosmicV91` (3049) — `screen === 'timer' ? startUc() : stopUc()`;
  4. `homeHeroV103` (3058) — `syncHomeHeroV103()`;
  5. `missionDepthV105` (3062) — `applyMissionDepthV105(true)`;
  6. `multiMissionCoreV115` (3280) — привязка выбранной миссии на `plan` и `renderMultiMissionUIV115`;
  7. `multiMissionUXV116` (3484) — `renderHomeCarouselV116` / `renderMissionManagementV116`;
  8. `missionAuraBlendV1181` (3699) — повторный триггер `renderHomeCarouselV116`;
  9. `authoritativeRecoveryRouterV1314` (4905) — жёсткий редирект `home` $\rightarrow$ `session-recovery` при наличии активного кандидата;
  10. `optionalAdaptationControllerV133` (5273) — `setTimeout(offered, 0)` для `result`;
  11. `cleanQuickFocusV13313Logic` (5358) — `document.documentElement.classList.toggle('quick-focus-surface', screenName === 'quicksetup')`;
  12. `planDurationProtectionRestoreRuntimeV13315` (5417) — `refreshPlanSupportLayout()` для `plan`.
* **Финальная авторитетная реализация:** Внешняя обёртка в `planDurationProtectionRestoreRuntimeV13315` (строка 5417), вызывающая всю цепочку через `authoritativeRecoveryRouterV1314` до базового переключателя DOM.
* **Какие wrappers должны временно сохраниться:** Все активные post-hooks (`pwaBoot`, `sessionHistoryCoreV111`, `ultraCosmicV91`, `multiMissionCoreV115`, `multiMissionUXV116`, `authoritativeRecoveryRouterV1314`, `optionalAdaptationControllerV133`, `cleanQuickFocusV13313Logic`, `planDurationProtectionRestoreRuntimeV13315`).
* **Условие удаления wrappers:** В `v15.10`, после замены цепочки monkey-patching на детерминированный массив слушателей `onScreenApplied(screenName)` в `src/core/navigation.js` с сохранением строго того же порядка вызова.
* **Regression risk при переносе:** **Critical** (потеря любого хука ломает Safe Area, Cosmic Timer, карусель миссий или порядок карточек плана).

### 3. `startSession(session)`
* **Где впервые появляется:** `sessionCoreV11` (строка 2993).
* **Какие wrappers применяются позже:**
  1. `sessionRecoveryV112` (3177) — запуск `startSessionRuntime()`;
  2. `sessionRecoveryAndHomeHotfixV1151` (3367) — проверка существующей сессии;
  3. `sessionFlowStabilizationV1312` (4874) — очистка зависшего `awaiting_review`;
  4. `finalizedSessionGuardV1315` (5065) — `purgeFinalizedActiveSessionV1315('before_start')`;
  5. `sessionRecoveryControllerV1321` (5105) — делегирующая обёртка контроллера.
* **Финальная авторитетная реализация:** `FocusModeApp.core.sessionRecovery.start` (`sessionRecoveryControllerV1321`, строка 5105), делегирующая в `startSessionV1315` (`finalizedSessionGuardV1315`, строка 5065).
* **Какие wrappers должны временно сохраниться:** Цепочка `sessionRecoveryControllerV1321` $\rightarrow$ `finalizedSessionGuardV1315` $\rightarrow$ `sessionFlowStabilizationV1312` $\rightarrow$ `sessionCoreV11`.
* **Условие удаления wrappers:** В `v15.4 (Session Runtime Extraction)`, когда pre-flight очистка `purgeFinalizedActiveSessionV1315` будет включена в начало единого метода `startSession` внутри `src/features/focus-session/session-runtime.js`.
* **Regression risk при переносе:** **High**.

### 4. `checkpointActiveSession(reason, persist)`
* **Где впервые появляется:** `sessionRecoveryV112` (строка 3105).
* **Какие wrappers применяются позже:**
  1. `sessionPersistenceMigrationV13` (строка 4521, `checkpointActiveSessionV13`) — полная замена на расчёт `elapsedMs` по timestamps и запись через `SessionRepository.saveActive`;
  2. `sessionRecoveryControllerV1321` (строка 5105) — экспорт в фасад контроллера.
* **Финальная авторитетная реализация:** `checkpointActiveSessionV13` в `sessionPersistenceMigrationV13` (строка 4521), экспортированная через `sessionRecoveryControllerV1321`.
* **Какие wrappers должны временно сохраниться:** Делегат `sessionRecoveryControllerV1321` и глобальный алиас `window.checkpointActiveSession`.
* **Условие удаления wrappers:** Ранняя реализация из `sessionRecoveryV112` уже перекрыта; глобальный алиас сохраняется до `v15.12`.
* **Regression risk при переносе:** **High** (ошибка приведёт к потере отработанных минут при перезагрузке вкладки).

### 5. `endSessionByUser()`
* **Где впервые появляется:** `sessionCoreV11` (строка 3006).
* **Какие wrappers применяются позже:**
  1. `sessionRecoveryV112` (строка 3182) — учёт `elapsedMs`, правило `< 1 мин` $\rightarrow$ `cancelled` / $\ge 1$ мин $\rightarrow$ `completed`, вызов `terminalCommitV13` (через патч `sessionPersistenceMigrationV13`);
  2. `sessionRecoveryControllerV1321` (строка 5105) — экспорт в `window.endSessionByUser` и `window.completeLearningSession`.
* **Финальная авторитетная реализация:** Реализация `sessionRecoveryV112` + `sessionPersistenceMigrationV13`, обёрнутая в `sessionRecoveryControllerV1321` (строка 5105).
* **Какие wrappers должны временно сохраниться:** Оба глобальных имени `window.endSessionByUser` и `window.completeLearningSession` (используются в `onclick` кнопок таймера).
* **Условие удаления wrappers:** В `v15.4` логика объединяется в один метод `endSessionByUser` модуля `focus-session`, глобальные алиасы остаются до удаления inline `onclick`.
* **Regression risk при переносе:** **High**.

### 6. `stopFocusSession()`
* **Где впервые появляется:** `sessionCoreV11` (строка 3007).
* **Какие wrappers применяются позже:**
  1. `sessionRecoveryV112` (строка 3188) — сохранение отменённой сессии (`outcome: 'aborted'`, `countAsFocus: false`), остановка рантайма, очистка `activeSession`, переход на `'home'`;
  2. `sessionRecoveryControllerV1321` (строка 5105) — экспорт в `FocusModeApp.core.sessionRecovery.cancel`.
* **Финальная авторитетная реализация:** `sessionRecoveryV112` (строка 3188) под управлением `sessionRecoveryControllerV1321` (строка 5105).
* **Какие wrappers должны временно сохраниться:** `window.stopFocusSession`.
* **Условие удаления wrappers:** Консолидация внутри `v15.4`.
* **Regression risk при переносе:** **Medium**.

### 7. `readRecoveryCandidate()`
* **Где впервые появляется:** `sessionRecoveryV112` (строка 3096).
* **Какие wrappers применяются позже:**
  1. `recoveryRepositoryMigrationV131` (строка 4610, `readRecoveryCandidateV131`) — полная замена на чтение из `SessionRepository` с фильтрацией финальных сессий;
  2. `sessionRecoveryControllerV1321` (строка 5105) — назначает ту же функцию также на `window.getRecoverableSessionV1151`.
* **Финальная авторитетная реализация:** `readRecoveryCandidateV131` в `recoveryRepositoryMigrationV131` (строка 4610).
* **Какие wrappers должны временно сохраниться:** Алиас `window.getRecoverableSessionV1151 = readRecoveryCandidate`.
* **Условие удаления wrappers:** В `v15.5 (Recovery Extraction)` ранняя функция из `v112` удаляется, алиас `getRecoverableSessionV1151` остаётся в адаптере совместимости.
* **Regression risk при переносе:** **High**.

### 8. `resumeRecoveredSession()`
* **Где впервые появляется:** `sessionRecoveryV112` (строка 3139).
* **Какие wrappers применяются позже:**
  1. `multiMissionCoreV115` (3279);
  2. `sessionRecoveryAndHomeHotfixV1151` (3305);
  3. `recoveryRepositoryMigrationV131` (4619);
  4. `criticalFlowFixV1311` (4729);
  5. `sessionFlowStabilizationV1312` (4837, `resumeToTimerV1312`);
  6. `authoritativeRecoveryRouterV1314` (4981, `resumeAuthoritativelyV1314`);
  7. `sessionRecoveryControllerV1321` (5105).
* **Финальная авторитетная реализация:** `resumeAuthoritativelyV1314` в `authoritativeRecoveryRouterV1314` (строка 4981), обёрнутая в `sessionRecoveryControllerV1321` (строка 5105) и дополнительно защищённая capture-обработчиком клика.
* **Какие wrappers должны временно сохраниться:** Только `resumeAuthoritativelyV1314` и делегат `sessionRecoveryControllerV1321`. Промежуточные версии `v112`, `v1151`, `v131`, `v1311`, `v1312` полностью перекрыты (`resumeAuthoritativelyV1314` не вызывает `prev`, а выполняет всю процедуру атомарно сама).
* **Условие удаления wrappers:** В `v15.5 (Recovery Extraction)` все 5 перекрытых промежуточных реализаций безопасно архивируются и удаляются, остаётся только реализация `v1314`.
* **Regression risk при переносе:** **Critical** (ключевая точка `v14 Recovery Merge`).

### 9. `finishRecoveredSession()`
* **Где впервые появляется:** `sessionRecoveryV112` (строка 3156).
* **Какие wrappers применяются позже:**
  1. `recoveryRepositoryMigrationV131` (строка 4645) — маршрутизация через `SessionRepository` и `terminalCommitV13`;
  2. `sessionRecoveryControllerV1321` (строка 5105) — экспорт как `recovery.review` и `window.finishRecoveredSession`.
* **Финальная авторитетная реализация:** Реализация `recoveryRepositoryMigrationV131` под управлением `sessionRecoveryControllerV1321`.
* **Какие wrappers должны временно сохраниться:** Глобальный `window.finishRecoveredSession` (вызывается из `onclick` кнопки «Завершить и оценить» на экране Recovery).
* **Условие удаления wrappers:** Консолидация в `v15.5`.
* **Regression risk при переносе:** **High**.

### 10. `prepareLearningSession(mid, sid)`
* **Где впервые появляется:** `sessionCoreV11` (строка 3004).
* **Какие wrappers применяются позже:**
  1. `multiMissionCoreV115` (строка 3276) — принимает `(mid = missionRepository.getSelectedId(), sid = null)`, находит активный шаг в `MissionRepository`, создаёт сессию через `createMissionSession(mid, step.id)` и вызывает `startSession`;
  2. `missionFlowControllerV1322` (строка 5107) — экспортирует как `features.missionFlow.startStep` и `window.prepareLearningSession`.
* **Финальная авторитетная реализация:** Реализация `multiMissionCoreV115` (строка 3276), обёрнутая в `missionFlowControllerV1322` (строка 5107).
* **Какие wrappers должны временно сохраниться:** `window.prepareLearningSession` (вызывается из динамической разметки карточек шагов и карусели миссий).
* **Условие удаления wrappers:** В `v15.6 (Mission Flow Extraction)` ранняя версия из `sessionCoreV11` удаляется, остаётся реализация `v115/v1322`.
* **Regression risk при переносе:** **High**.

### 11. `createMissionSession(missionId, stepId)`
* **Где впервые появляется:** `sessionCoreV11` (строка 2989).
* **Какие wrappers применяются позже:**
  1. `multiMissionCoreV115` (строка 3275) — полная замена: валидирует миссию через `missionRepository.getById(mid)`, проверяет `m.status === 'active'` и `m.currentStepId === sid`, формирует объект сессии через `makeSession`;
  2. `missionFlowControllerV1322` (строка 5107) — экспорт в `features.missionFlow.createSession`.
* **Финальная авторитетная реализация:** Реализация `multiMissionCoreV115` (строка 3275), обёрнутая в `missionFlowControllerV1322`.
* **Какие wrappers должны временно сохраниться:** `window.createMissionSession`.
* **Условие удаления wrappers:** В `v15.6` старая версия из `sessionCoreV11` удаляется.
* **Regression risk при переносе:** **High** (защищает инвариант №2 и №8).

### 12. `confirmAIPlan()`
* **Где впервые появляется:** `realAIMissionV6` (строка 2673).
* **Какие wrappers применяются позже:**
  1. `adaptiveMissionV7` (2717);
  2. `multiMissionCoreV115` (3271);
  3. `criticalFlowFixV1311` (4709, `confirmAIPlanV1311`) — полная замена: читает `pendingAIPlan` или `StorageService.getJSON('focus-mode-last-ai-plan', null)`, вызывает `missionRepository.create(...)`, проверяет лимит 2 активных миссий и вызывает `openMission(result.mission.id)`;
  4. `missionFlowControllerV1322` (5107) — экспорт в `features.missionFlow.confirm`.
* **Финальная авторитетная реализация:** `confirmAIPlanV1311` в `criticalFlowFixV1311` (строка 4709), обёрнутая в `missionFlowControllerV1322`.
* **Какие wrappers должны временно сохраниться:** `window.confirmAIPlan`. Перекрытые реализации в `v6`, `v7`, `v115` не вызываются.
* **Условие удаления wrappers:** В `v15.6` перекрытые определения `v6/v7/v115` удаляются, остаётся реализация `v1311`.
* **Regression risk при переносе:** **High**.

### 13. `showPlanUpdate()`
* **Где впервые появляется:** Базовый стаб (строка 2313).
* **Какие wrappers применяются позже:**
  1. `adaptiveMissionV7` (2764);
  2. `sessionRecoveryV112` (3192);
  3. `multiMissionCoreV115` (3277);
  4. `sessionFlowStabilizationV1312` (4859);
  5. `finalizedSessionGuardV1315` (5073);
  6. `missionFlowControllerV1322` (5107);
  7. `optionalAdaptationControllerV133` (строка 5274) — **полностью заменяет** `window.showPlanUpdate = adaptMissionAfterSession`.
* **Финальная авторитетная реализация:** `adaptMissionAfterSession` в `optionalAdaptationControllerV133` (строка 5174 / 5274).
* **Какие wrappers должны временно сохраниться:** Алиас `window.showPlanUpdate = adaptMissionAfterSession` и обновление ссылки в `FocusModeApp.features.missionFlow.adapt`. Все ранние цепочки `v7`–`v1315` для `showPlanUpdate` фактически заменены самодостаточным контроллером `optionalAdaptationControllerV133`.
* **Условие удаления wrappers:** В `v15.8 (Optional Adaptation Extraction)` мёртвая цепочка старого `showPlanUpdate` удаляется, остаётся прямой делегат на `adaptMissionAfterSession`.
* **Regression risk при переносе:** **High**.

### 14. `finishLearningFlow()`
* **Где впервые появляется:** Базовый скрипт (строка 2325).
* **Какие wrappers применяются позже:**
  1. `adaptiveMissionV7` (2804);
  2. `mobileUIV81` (2827);
  3. `sessionFlowStabilizationV1312` (4866, `finishLearningFlowV1312`);
  4. `finalizedSessionGuardV1315` (5080, `finishLearningFlowV1315`);
  5. `missionFlowControllerV1322` (5107).
* **Финальная авторитетная реализация:** `finishLearningFlowV1315` (`finalizedSessionGuardV1315`, строка 5080), обёрнутая в `missionFlowControllerV1322` (а для экрана `update` также `finishOptionalAdaptationFlow` в `optionalAdaptationControllerV133`).
* **Какие wrappers должны временно сохраниться:** `window.finishLearningFlow` и `window.finishOptionalAdaptationFlow`.
* **Условие удаления wrappers:** Консолидация в `v15.6` и `v15.8`.
* **Regression risk при переносе:** **Medium**.

### 15. `renderAIPlan(plan)`
* **Где впервые появляется:** `realAIMissionV6` (строка 2676).
* **Какие wrappers применяются позже:**
  1. `adaptiveMissionV7` (2737);
  2. `mobileUIV81` (2813) — полная перерисовка карточек шагов с прогрессом;
  3. `missionDepthV105` (3062) — `applyMissionDepthV105(false)`;
  4. `multiMissionCoreV115` (3270) — синхронизация с `missionRepository.getSelected()` через `bindMissionCompatibilityV115`;
  5. `missionFlowControllerV1322` (5107);
  6. `planDurationProtectionRestoreRuntimeV13315` (5417) — `refreshPlanSupportLayout()` (расстановка `#missionProtectionCard` и `#aiPlanExplanation`).
* **Финальная авторитетная реализация:** Цепочка `planDurationProtectionRestoreRuntimeV13315` $\rightarrow$ `missionFlowControllerV1322` $\rightarrow$ `multiMissionCoreV115` $\rightarrow$ `missionDepthV105` $\rightarrow$ `mobileUIV81`.
* **Какие wrappers должны временно сохраниться:** Вся активная цепочка от `mobileUIV81` до `planDurationProtectionRestoreRuntimeV13315` (также алиас `window.renderMissionPlan`).
* **Условие удаления wrappers:** В `v15.6`, когда `src/ui/mission-renderer.js` объединит рендер `mobileUIV81`, привязку `multiMissionCoreV115` и вызов `refreshPlanSupportLayout()` в одну функцию без потери DOM-структуры.
* **Regression risk при переносе:** **High** (влияет на инварианты №14 и №15).

### 16. `renderHomeCarouselV116()`
* **Где впервые появляется:** `multiMissionUXV116` (строка 3436).
* **Какие wrappers применяются позже:**
  1. `missionCardPaintFixV1161` (3567);
  2. `missionAuraBlendV1181` (3698) — полная замена: создаёт `#missionAuraShellV1181` и `#missionCarouselV1181` с атрибутами `data-aura`, вешает scroll-слушатель и вызывает `fitMissionCardTitlesV117()`;
  3. `missionFlowControllerV1322` (5107) — экспорт в `features.missionFlow.render.home`.
* **Финальная авторитетная реализация:** Реализация в `missionAuraBlendV1181` (строка 3698), обёрнутая в `missionFlowControllerV1322` (строка 5107).
* **Какие wrappers должны временно сохраниться:** `window.renderHomeCarouselV116` (использует вспомогательные функции `sortedActiveMissionsV116`, `missionViewModelV116`, `renderMissionHeroV116` из `multiMissionUXV116` и `fitMissionCardTitlesV117` из `adaptiveMissionCardsV117`).
* **Условие удаления wrappers:** В `v15.7 (Multi-Mission UX Extraction)` перекрытые версии `3436` и `3567` удаляются, остаётся финальная реализация `3698` со своими хелперами.
* **Regression risk при переносе:** **Medium**.

### 17. `openMission(id)`
* **Где впервые появляется:** `multiMissionCoreV115` (строка 3268).
* **Какие wrappers применяются позже:**
  1. `missionCardPaintFixV1161` (строка 3576) — устанавливает `missionRepository.setSelected(id)`, вызывает `bindMissionCompatibilityV115(m)`, `renderAIPlan(activeAIPlan)`, `renderMultiMissionUIV115()`, переключает экран `setAppScreen('plan')` и сбрасывает `scrollTop = 0`;
  2. `missionFlowControllerV1322` (строка 5107) — экспорт в `features.missionFlow.open`.
* **Финальная авторитетная реализация:** Реализация `missionCardPaintFixV1161` (строка 3576), обёрнутая в `missionFlowControllerV1322`.
* **Какие wrappers должны временно сохраниться:** `window.openMission` (широко используется в inline `onclick` карточек карусели и шторки действий).
* **Условие удаления wrappers:** Консолидация в `v15.6`, глобальный алиас сохраняется до `v15.12`.
* **Regression risk при переносе:** **High**.

### 18. `openFocusChoice()`
* **Где впервые появляется:** Базовый скрипт (строка 2270) — открывает модальное окно выбора режима `#quickFocusModal`.
* **Какие wrappers применяются позже:**
  1. `multiMissionUXV116` (строка 3450) — проверяет `activeSession || sessionRecoveryCandidate || getRecoverableSessionV1151()`: если есть незавершённая сессия, вызывает `routeExistingSessionV1151()` и блокирует открытие модалки; иначе вызывает `renderFocusChoiceV116()` и открывает модалку.
* **Финальная авторитетная реализация:** Обёртка в `multiMissionUXV116` (строка 3450).
* **Какие wrappers должны временно сохраниться:** `window.openFocusChoice`.
* **Условие удаления wrappers:** В `v15.7` объединяется в единую функцию `openFocusChoice` с обязательной проверкой `routeExistingSessionV1151()` в начале.
* **Regression risk при переносе:** **Medium**.

---

# 5. Migration strategy

Переход от монолита `index.html` к физическим ES-модулям разделён на **12 атомарных версий (`v15.1` – `v15.12`)**. Каждый этап завершается коммитом-чекпоинтом и обязательным прохождением Testing Gates (Раздел 9).

---

## v15.1 Build Foundation
* **Точная цель:** Подключить минимальный сборочный конвейер Vite и точку входа `<script type="module" src="/src/main.js"></script>` без выноса бизнес-логики и без изменения работы `index.html`, PWA и GitHub Pages.
* **Разрешённые файлы:** `package.json` (создание), `vite.config.js` (создание), `src/main.js` (создание), `index.html` (добавление ровно одного тега `<script type="module" src="/src/main.js"></script>` перед `</body>`).
* **Запрещённые изменения:** Удаление или перемещение любого существующего `<script>` или `<style>` из `index.html`, изменение `manifest.webmanifest`, `service-worker.js`, изменение ключей `localStorage`.
* **Переносимые script ID:** Нет (0 скриптов удаляется из `index.html`).
* **Создаваемые модули:** `src/main.js` (диагностический bootstrap-импорт, проверяющий `window.FocusModeApp.core.architectureStabilization.assertStable()`).
* **Временные compatibility adapters:** Не требуются; весь монолит исполняется как раньше.
* **Критерии завершения:** `npm run dev`, `npm run build`, `npm run preview` отрабатывают без ошибок; собранный `dist/` идентично рендерит все 18 экранов.
* **Unit/static checks:** Gate A (сборка Vite без ошибок, проверка `assertStable().stable === true`).
* **Ручные regression tests:** Gate C (Quick Focus, Mission Session), Gate F (PWA, Service Worker, GitHub Pages base path).
* **Rollback point:** `git checkout v14-recovery-merge-stable`.
* **Ориентировочный риск:** **Low**.

---

## v15.2 Storage Completion
* **Точная цель:** Вынести `architectureShellV122`, `removeRussianLanguage` и `storageServiceV123` в первые физические ES-модули `src/core/runtime-ownership.js` (shell) и `src/services/storage-service.js`.
* **Разрешённые файлы:** `src/main.js`, `src/core/bootstrap.js`, `src/services/storage-service.js`, `src/repositories/settings-repository.js`, `index.html`.
* **Запрещённые изменения:** Изменение логики репозиториев сессий и миссий, изменение ключей `localStorage`, изменение UI.
* **Переносимые script ID:** `removeRussianLanguage` (629), `architectureShellV122` (904), `storageServiceV123` (1028).
* **Создаваемые модули:**
  * `src/core/bootstrap.js` (инициализация `window.FocusModeApp` и санитизация языка `'ru' -> 'de'`);
  * `src/services/storage-service.js` (`StorageService`);
  * `src/repositories/settings-repository.js` (обёртка чтения/записи `focus-mode-language` и `focus-pwa-help-v11`).
* **Временные compatibility adapters:** Так как inline-скрипты в `index.html` исполняются синхронно при парсинге, а `<script type="module">` по умолчанию `defer`, на этапе гибридной миграции bootstrap-скрипт в `<head>` подключается либо синхронным inline-бриджем, либо все зависимые скрипты переводятся на очередь инициализации после импорта `storage-service.js` (подробно в Разделе 7.11). `window.FocusModeApp.services.storage` обязан быть доступен до старта `realAIMissionV6` и `multiMissionCoreV115`.
* **Критерии завершения:** `FocusModeApp.services.storage` регистрируется из физического файла `src/services/storage-service.js`; все 7 методов (`getText`, `setText`, `getJSON`, `setJSON`, `remove`, `has`, `backup`) проходят проверку `assertStable()`.
* **Unit/static checks:** Gate A + unit-тесты `StorageService` (чтение, запись, глубокое клонирование, карантин битого JSON в backup-ключ).
* **Ручные regression tests:** Gate B (миграция старых данных, обработка битого JSON), смена языка DE/EN.
* **Rollback point:** Тег/коммит `v15.1-checkpoint`.
* **Ориентировочный риск:** **Low**.

---

## v15.3 Repository Extraction
* **Точная цель:** Вынести `SessionRepository` и `MissionRepository` в физические модули `src/repositories/session-repository.js` и `src/repositories/mission-repository.js`.
* **Разрешённые файлы:** `src/repositories/session-repository.js`, `src/repositories/mission-repository.js`, `src/core/app-state.js`, `src/main.js`, `index.html`.
* **Запрещённые изменения:** Изменение сигнатур методов репозиториев, изменение лимита 2 активных миссий, изменение логики таймера или UI.
* **Переносимые script ID:** `sessionPersistenceDesignV128` (4220), `sessionRepositoryV129` (4313), чистая CRUD-часть `multiMissionCoreV115` (3227–3267).
* **Создаваемые модули:**
  * `src/repositories/session-repository.js`;
  * `src/repositories/mission-repository.js`;
  * `src/core/app-state.js` (синхронизатор compatibility-ссылок `window.activeSession`, `window.activeAIPlan`, `window.missionProgress`).
* **Временные compatibility adapters:** Экспорт `window.missionRepository = FocusModeApp.repositories.missions`, сохранение глобальных функций `loadMissionStoreV115`, `persistMissionStoreV115`, `bindMissionCompatibilityV115`.
* **Критерии завершения:** Оба репозитория работают из `src/repositories/*`; `FocusModeApp.repositories.sessions` и `FocusModeApp.repositories.missions` проходят `assertStable()`.
* **Unit/static checks:** Gate A + unit-тесты `MissionRepository` (лимит 2 миссий, `completeStep`, `replaceRemainingSteps` с `expectedUpdatedAt`, миграция v7 $\rightarrow$ v11.5) и `SessionRepository` (дедупликация `appendHistory`, валидация `missionId`/`missionStepId`).
* **Ручные regression tests:** Gate B, Gate C, Gate D (две активные миссии, переключение, изоляция прогресса).
* **Rollback point:** Тег/коммит `v15.2-checkpoint`.
* **Ориентировочный риск:** **Medium**.

---

## v15.4 Session Runtime Extraction
* **Точная цель:** Вынести жизненный цикл фокус-сессии, Quick Focus, History и Statistics в модули `src/features/focus-session/`, `src/features/quick-focus/` и `src/features/history/`.
* **Разрешённые файлы:** `src/features/focus-session/*`, `src/features/quick-focus/*`, `src/features/history/*`, `src/main.js`, `index.html`.
* **Запрещённые изменения:** Изменение визуализации Cosmic Timer, изменение маршрутизации Recovery Router, изменение Optional Adaptation.
* **Переносимые script ID:** `sessionHistoryCoreV111` (2832), `sessionCoreV11` (2974), `sessionPersistenceMigrationV13` (4455), `finalizedSessionGuardV1315` (5015), `cleanQuickFocusV13313Logic` (5355), `focusClarityLogicV13314` (5367).
* **Создаваемые модули:**
  * `src/features/focus-session/session-runtime.js` (`startSession`, `checkpointActiveSession`, `endSessionByUser`, `stopFocusSession`, `terminalCommitV13`, `purgeFinalizedActiveSessionV1315`);
  * `src/features/quick-focus/quick-focus.js` (`createQuickSession`, `startConfiguredQuickFocus`, `selectQuickDuration`, `adjustQuickDuration`, `updateQuickRing`);
  * `src/features/history/history-controller.js` (`renderSessionHistory`, `refreshHomeStats`, `refreshStatsScreen`).
* **Временные compatibility adapters:** Все публичные функции пробрасываются в `window.*` и `FocusModeApp.core.sessionRecovery`.
* **Критерии завершения:** Сессии Quick Focus и Mission запускаются, чекпоинтятся каждые 5 с и однократно коммитятся в историю через вынесенные модули.
* **Unit/static checks:** Gate A + тест однократной финализации (`terminalCommitV13`) и изоляции Quick Focus от миссий.
* **Ручные regression tests:** Gate B, Gate C (Quick Focus, Mission Session, History, Realtime Home Stats), проверка Safe Area на `quicksetup`.
* **Rollback point:** Тег/коммит `v15.3-checkpoint`.
* **Ориентировочный риск:** **High**.

---

## v15.5 Recovery Extraction
* **Точная цель:** Вынести подсистему восстановления сессий и `Authoritative Recovery Router` в `src/features/recovery/`, консолидировав 7 исторических слоёв `resumeRecoveredSession` в единый авторитетный модуль на базе `v13.1.4` и `v13.2.1`.
* **Разрешённые файлы:** `src/features/recovery/recovery-controller.js`, `src/features/recovery/recovery-router.js`, `src/main.js`, `index.html`.
* **Запрещённые изменения:** Изменение формулы расчёта `elapsedMs` и `remainingMs`, удаление capture-перехватчика клика на кнопку Resume.
* **Переносимые script ID:** `sessionRecoveryV112` (3063), `sessionRecoveryAndHomeHotfixV1151` (3284), `recoveryRepositoryMigrationV131` (4571), `sessionFlowStabilizationV1312` (4767), `authoritativeRecoveryRouterV1314` (4887), `sessionRecoveryControllerV1321` (5105).
* **Создаваемые модули:**
  * `src/features/recovery/recovery-controller.js` (`readRecoveryCandidate`, `renderSessionRecovery`, `finishRecoveredSession`, `discardRecoveredSession`);
  * `src/features/recovery/recovery-router.js` (`resumeAuthoritativelyV1314`, навигационный фильтр `setAppScreenV1314` / `applyAppScreenImmediatelyV1314`, capture-listener кнопки Resume).
* **Временные compatibility adapters:** Сохранение `window.readRecoveryCandidate`, `window.getRecoverableSessionV1151`, `window.resumeRecoveredSession`, `window.finishRecoveredSession`, `window.discardRecoveredSession`, `window.routeExistingSessionV1151`, `FocusModeApp.state.recoveryRouterV1314`.
* **Критерии завершения:** Перезагрузка страницы при активной сессии мгновенно блокирует `home`, открывает `app-screen-session-recovery`, показывает точное затраченное время и по клику «Продолжить» возобновляет таймер с правильной миссией.
* **Unit/static checks:** Gate A + unit-тесты расчёта `elapsedMs` и фильтрации завершённых сессий.
* **Ручные regression tests:** Gate C (полный цикл Recovery: Resume, Finish & Review, Discard, перезагрузка при двух миссиях).
* **Rollback point:** Тег/коммит `v15.4-checkpoint`.
* **Ориентировочный риск:** **Critical**.

---

## v15.6 Mission Flow Extraction
* **Точная цель:** Вынести контроллер потока миссий, создание сессии шага миссии и рендерер экрана плана (включая защиту порядка карточек `v13.3.15`) в `src/features/missions/` и `src/ui/mission-renderer.js`.
* **Разрешённые файлы:** `src/features/missions/mission-flow.js`, `src/ui/mission-renderer.js`, `src/main.js`, `index.html`.
* **Запрещённые изменения:** Изменение верстки шагов плана, бейджей `.step-time`, порядка `#missionProtectionCard` и `#aiPlanExplanation`.
* **Переносимые script ID:** `focusMissionProductLogic` (2458), `cleanMissionHomeLogic` (2496), `missionInputAndConfidenceLogic` (2507), `adaptiveMissionV7` (2699), `mobileUIV81` (2810), `homeHeroV103` (3056), `missionDepthV105` (3060), UI-часть `multiMissionCoreV115` (3268–3283), `criticalFlowFixV1311` (4685), `missionFlowControllerV1322` (5107), `missionDepthV1338Logic` (5281), `planDurationProtectionRestoreRuntimeV13315` (5417).
* **Создаваемые модули:**
  * `src/features/missions/mission-flow.js` (`confirmAIPlan`, `openMission`, `prepareLearningSession`, `createMissionSession`, `finishLearningFlow`, ввод цели и компрессия изображений);
  * `src/ui/mission-renderer.js` (`renderAIPlan`, `arrangePlanSupportBlocks`, `refreshPlanSupportLayout`).
* **Временные compatibility adapters:** Экспорт `window.confirmAIPlan`, `window.openMission`, `window.prepareLearningSession`, `window.createMissionSession`, `window.renderAIPlan`, `FocusModeApp.features.missionFlow`, `FocusModeApp.features.planDurationProtectionRestore`.
* **Критерии завершения:** Подтверждение плана, открытие миссии, старт шага и рендер карточек плана работают из ES-модулей; порядок `#missionProtectionCard` над `#aiPlanExplanation` сохранён.
* **Unit/static checks:** Gate A + DOM-проверка порядка карточек плана и бейджей времени.
* **Ручные regression tests:** Gate C, Gate D, пункты 17 и 18 из `REGRESSION_CHECKLIST.md` (Plan Duration Badges & Protection Order).
* **Rollback point:** Тег/коммит `v15.5-checkpoint`.
* **Ориентировочный риск:** **High**.

---

## v15.7 Multi-Mission UX Extraction
* **Точная цель:** Вынести карусель активных миссий на Home (`Aura Blend`), шторку действий и экран управления миссиями (`Rename`, `Primary`, `Archive`, `Restore`, `Delete`) в `src/features/missions/multi-mission-ux.js`.
* **Разрешённые файлы:** `src/features/missions/multi-mission-ux.js`, `src/ui/mission-renderer.js`, `src/main.js`, `index.html`.
* **Запрещённые изменения:** Изменение тем `'violet' | 'aqua'`, удаление блокировки архивации/удаления миссии при запущенной сессии.
* **Переносимые script ID:** `multiMissionUXV116` (3427), `missionCardPaintFixV1161` (3551), `adaptiveMissionCardsV117` (3639), `missionAuraBlendV1181` (3692).
* **Создаваемые модули:** `src/features/missions/multi-mission-ux.js`.
* **Временные compatibility adapters:** Экспорт `window.renderHomeCarouselV116`, `window.openMissionManagementV116`, `window.openMissionActionsV116`, `window.makePrimaryV116`, `window.openRenameMissionV116`, `window.saveRenameMissionV116`, `window.confirmArchiveV116`, `window.archiveMissionActionV116`, `window.restoreMissionActionV116`, `window.confirmDeleteV116`, `window.closeMissionSheetV116`.
* **Критерии завершения:** Карусель Home и экран `app-screen-missions` полностью управляются из `src/features/missions/multi-mission-ux.js`.
* **Unit/static checks:** Gate A.
* **Ручные regression tests:** Gate D (2 активные миссии, переключение, переименование, архивация, запрет архивации во время активного таймера).
* **Rollback point:** Тег/коммит `v15.6-checkpoint`.
* **Ориентировочный риск:** **Medium**.

---

## v15.8 Optional Adaptation Extraction
* **Точная цель:** Вынести контроллер `optionalAdaptationControllerV133` в `src/features/optional-adaptation/`.
* **Разрешённые файлы:** `src/features/optional-adaptation/optional-adaptation-controller.js`, `src/main.js`, `index.html`.
* **Запрещённые изменения:** Вызов сети при Skip Adaptation, повторный коммит сессии при Retry, модификация невыбранной миссии.
* **Переносимые script ID:** `optionalAdaptationControllerV133` (5174) — за исключением чистого сетевого вызова `fetch`, который на следующем шаге `v15.9` перейдёт в `ai-service.js`.
* **Создаваемые модули:** `src/features/optional-adaptation/optional-adaptation-controller.js`.
* **Временные compatibility adapters:** Экспорт `window.adaptMissionAfterSession`, `window.continueMissionWithoutAdaptation`, `window.retryOptionalAdaptation`, `window.finishOptionalAdaptationFlow`, `window.showPlanUpdate = adaptMissionAfterSession`, регистрация `FocusModeApp.features.optionalAdaptation`.
* **Критерии завершения:** Все три ветки после завершения шага миссии (Adapt, Skip, Failure + Retry) работают из вынесенного модуля.
* **Unit/static checks:** Gate A + unit-тест идемпотентности `commitMissionSessionResult`.
* **Ручные regression tests:** Gate E (Adapt, Skip с проверкой 0 сетевых запросов, Retry после Offline).
* **Rollback point:** Тег/коммит `v15.7-checkpoint`.
* **Ориентировочный риск:** **Medium**.

---

## v15.9 AI Service Extraction
* **Точная цель:** Консолидировать сетевые вызовы к Cloudflare Workers (`create-mission` и `adapt-mission`), таймауты `AbortController` (120 с) и функции нормализации ответов (`normalizeAIPlan`, `normalizeUpdatedSessions`) в единый сервис `src/services/ai-service.js`.
* **Разрешённые файлы:** `src/services/ai-service.js`, `src/features/missions/mission-flow.js`, `src/features/optional-adaptation/optional-adaptation-controller.js`, `src/main.js`, `index.html`.
* **Запрещённые изменения:** Изменение URL эндпоинтов (`FOCUS_AI_ENDPOINT`, `FOCUS_AI_ADAPT_ENDPOINT`), изменение структуры `FormData` или JSON payload, изменение лимитов нормализации (макс. 6 сессий, длительность 5–60 мин), добавление клиентских API-ключей.
* **Переносимые script ID:** `realAIMissionV6` (2583) и сетевая часть `requestAdaptation` из `optionalAdaptationControllerV133`.
* **Создаваемые модули:** `src/services/ai-service.js` (регистрируется как `FocusModeApp.services.ai`).
* **Временные compatibility adapters:** Сохранение глобальных `window.analyzeGoal`, `window.FOCUS_AI_ENDPOINT`, `window.FOCUS_AI_ADAPT_ENDPOINT`.
* **Критерии завершения:** Генерация плана миссии и AI-адаптация выполняются через `FocusModeApp.services.ai`.
* **Unit/static checks:** Gate A + unit-тесты `normalizeAIPlan` и `normalizeUpdatedSessions` на невалидных, пустых и выходящих за границы (duration < 5 или > 60, > 6 шагов) ответах.
* **Ручные regression tests:** Создание миссии с текстом и фото, Gate E (Adapt, Skip, Retry, обработка ошибки сети).
* **Rollback point:** Тег/коммит `v15.8-checkpoint`.
* **Ориентировочный риск:** **Medium**.

---

## v15.10 Navigation and Bootstrap
* **Точная цель:** Вынести навигацию экранов, Cosmic Timer, i18n, PWA bootstrap и оставшиеся UI-скрипты в `src/core/navigation.js`, `src/core/bootstrap.js`, `src/ui/screens.js`, `src/ui/timer-renderer.js`, полностью очистив `index.html` от inline `<script>` (кроме `<script type="module" src="/src/main.js"></script>` и конфигурации Tailwind в `<head>`).
* **Разрешённые файлы:** `src/core/navigation.js`, `src/core/bootstrap.js`, `src/core/runtime-ownership.js`, `src/ui/screens.js`, `src/ui/timer-renderer.js`, `src/main.js`, `index.html`.
* **Запрещённые изменения:** Изменение геометрии и визуализации `ultraCosmicV91`, изменение порядка срабатывания post-hooks при переключении экранов.
* **Переносимые script ID:** Базовый inline-скрипт (2051), скрипт скролла глав лендинга (2400), `realTimeScreenTransitions` (2457), `i18nScript` (2457), `focusProtectionLogic` (2536), `pwaBoot` (2562), `ultraCosmicV91` (3027), `runtimeOwnershipV132` (5103), `legacyCleanupGateV1323` (5109), `architectureStabilizationV1325` (5143).
* **Создаваемые модули:**
  * `src/core/navigation.js`;
  * `src/core/runtime-ownership.js`;
  * `src/ui/screens.js`;
  * `src/ui/timer-renderer.js` (`ultraCosmicV91`).
* **Временные compatibility adapters:** Централизованный регистратор `window.*` алиасов для всех inline `onclick` в `index.html`.
* **Критерии завершения:** В теле `index.html` не осталось бизнес-скриптов; приложение полностью инициализируется через `src/main.js`.
* **Unit/static checks:** Gate A (`assertStable()` и `assertReady()` возвращают 0 блокеров).
* **Ручные regression tests:** Полный прогон Gate A, B, C, D, E, F (включая Cosmic Timer, i18n DE/EN и PWA статус-бар).
* **Rollback point:** Тег/коммит `v15.9-checkpoint`.
* **Ориентировочный риск:** **High**.

---

## v15.11 Style Extraction
* **Точная цель:** Вынести все 16 встроенных блоков `<style>` из `index.html` в физические CSS-файлы в `src/styles/` со строгим сохранением каскадного порядка объявления.
* **Разрешённые файлы:** `src/styles/tokens.css`, `src/styles/base.css`, `src/styles/screens.css`, `src/styles/missions.css`, `src/styles/quick-focus.css`, `src/styles/timer.css`, `src/styles/recovery.css`, `src/main.js`, `index.html`.
* **Запрещённые изменения:** Переупорядочивание CSS-правил между блоками, удаление `!important`, изменение медиа-запросов `@media (max-width: 370px)` или селекторов `#aiPlanSteps .step-time`, `#missionProtectionCard`, `.mission-explanation-card-v1332`, `html.quick-focus-surface`.
* **Переносимые style ID (строго в исходном порядке):**
  1. Базовый `<style>` из `<head>` $\rightarrow$ `tokens.css` + `base.css`;
  2. `balanced-screen-v1336` $\rightarrow$ `screens.css`;
  3. `quickFocusRoutingFixV1337` $\rightarrow$ `quick-focus.css`;
  4. `missionDepthV1338`, `singleScreenMissionV1339`, `stableMissionScrollV13310`, `headerBadgeFitV13311`, `confidenceGlowV13312` $\rightarrow$ `missions.css`;
  5. `focusClarityV13314` $\rightarrow$ `timer.css`;
  6. `multiMissionV115Styles`, `multiMissionUXV116Styles`, `missionCardLayoutFixV1161`, `adaptiveMissionCardsV117Styles`, `missionAuraBlendV1181Styles` $\rightarrow$ `missions.css`;
  7. `optionalAdaptationV133Styles` $\rightarrow$ `recovery.css` / `missions.css`;
  8. `cleanQuickFocusV13313` $\rightarrow$ `quick-focus.css`;
  9. `planDurationProtectionRestoreV13315` $\rightarrow$ подключается **последним** в `missions.css` (чтобы гарантированно переопределять ранние правила плана).
* **Создаваемые модули:** 7 CSS-файлов в `src/styles/`, импортируемых в `src/main.js` в фиксированном порядке.
* **Временные compatibility adapters:** Не требуются (все CSS-классы и ID остаются глобальными).
* **Критерии завершения:** В `index.html` отсутствуют inline `<style>` блоки; визуальное отображение на desktop и mobile (320px–430px) попиксельно соответствует `v14`.
* **Unit/static checks:** Gate A (сборка CSS бандла без предупреждений).
* **Ручные regression tests:** Gate F + пункты 16, 17, 18 из `REGRESSION_CHECKLIST.md` (Safe Area, Plan Duration Badges, Mission Protection & Explanation Order).
* **Rollback point:** Тег/коммит `v15.10-checkpoint`.
* **Ориентировочный риск:** **Medium**.

---

## v15.12 Modular Baseline
* **Точная цель:** Финализировать модульную архитектуру `v15`: обновить `service-worker.js` для кэширования собранных ассетов Vite (или подключить генерацию precache-манифеста), удалить мёртвые промежуточные обёртки, закрытые адаптерами, провести полный регрессионный аудит и зафиксировать стабильный тег `v15-modular-baseline-stable`.
* **Разрешённые файлы:** `src/**`, `service-worker.js`, `manifest.webmanifest`, `docs/ARCHITECTURE.md`.
* **Запрещённые изменения:** Удаление публичных контрактов `window.FocusModeApp` или изменение ключей `localStorage`.
* **Переносимые script ID:** Все перенесены на предыдущих этапах.
* **Создаваемые модули:** Финализация `src/main.js` и обновление `service-worker.js`.
* **Временные compatibility adapters:** Удаляются только внутренние дублирующие monkey-patch обёртки (`oldResumeV115`, `oldShowUpdateV115` и др.); публичный фасад `window.FocusModeApp` и глобальные обработчики для HTML `onclick` остаются замороженными.
* **Критерии завершения:** Пройдены все 20 сценариев `docs/REGRESSION_CHECKLIST.md` и все Gates A–F; приложение работает оффлайн после сборки `npm run build`.
* **Unit/static checks:** Полный прогон unit/static тестов по репозиториям, сервисам и нормализаторам.
* **Ручные regression tests:** Полный чек-лист Gate A – Gate F (100% сценариев `REGRESSION_CHECKLIST.md`).
* **Rollback point:** Тег/коммит `v15.11-checkpoint`.
* **Ориентировочный риск:** **Low**.

---

# 6. First extraction candidate

После завершения инфраструктурного шага `v15.1 Build Foundation` необходимо выбрать **первый бизнес-модуль для физического выноса (`v15.2`)**. Ниже приведено инженерное сравнение пяти кандидатов по 7 критериям.

## 6.1. Сравнительная матрица кандидатов

| Критерий | 1. Storage Service (`storageServiceV123`) | 2. Mission Repository (`multiMissionCoreV115`) | 3. Session Repository (`sessionRepositoryV129`) | 4. AI Service (`realAIMissionV6` + adapt) | 5. Quick Focus (`sessionCoreV11` + UI) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Количество зависимостей** | **Минимальное (1):** зависит только от `FocusModeApp.core.registerModule` и нативного `localStorage`. | **Среднее (3):** зависит от `StorageService`, легаси-ключей v7/v10 и UI-функции `renderMultiMissionUIV115`. | **Низкое (2):** зависит от `StorageService` и `FocusModeApp.core.registerModule`. | **Среднее (4):** зависит от `StorageService`, `MissionRepository`, `SessionRepository`, DOM-лоадера и `fetch`. | **Высокое (5):** зависит от `SessionCore`, `SessionRepository`, `RecoveryRouter`, Cosmic Timer и DOM кольца. |
| **Прямые DOM-вызовы** | **0 (отсутствуют):** чистый инфраструктурный модуль без единого обращения к `document`. | **Есть в том же скрипте:** `multiMissionCoreV115` совмещает репозиторий с DOM-обёртками `renderAIPlan` и `openMission`. | **0 в самом `v129`**, но тесно связан с `v13` (`refreshSessionConsumersV13` трогает DOM Home/History). | **Много в `realAIMissionV6`:** управляет `#app-screen-analyzing`, `#goalPrompt`, ошибками и превью. | **Много:** управляет `#quickRingProgress`, `#quickFocusModal`, `html.quick-focus-surface`. |
| **Storage side effects** | **Изолированные:** сам по себе не пишет ключи, пока его не вызовут; содержит защитный `backup()`. | **Высокие:** при загрузке сразу запускает `migrateLegacyMissionV115()` и модифицирует `focus-mode-missions-v11-5`. | **Средние:** управляет карантином и миграцией `focusmode-session-history-v11` $\rightarrow$ `v11-1`. | **Низкие:** пишет только временный `focus-mode-last-ai-plan`. | **Высокие:** создаёт и завершает `focusmode-active-session-v11`. |
| **Порядок загрузки** | **Самый ранний (строка 1028):** загружается сразу после `architectureShellV122` и не требует никаких других модулей. | **Средний (строка 3227):** требует уже готовый `StorageService` и ранние функции `renderAIPlan`. | **Поздний (строка 4313):** загружается после `sessionCoreV11` и `sessionRecoveryV112`. | **Ранний/Смешанный:** размазан между строкой 2583 (`create-mission`) и строкой 5250 (`adapt-mission`). | **Размазанный:** строки 2270, 2974, 3450, 5281, 5355. |
| **Риск потери данных** | **Низкий** (при сохранении сигнатур методов `getJSON`/`setJSON`/`backup`). | **Высокий** при ошибке в логике первичной миграции или нормализации шагов. | **Высокий** при ошибке дедупликации истории или чтения активной сессии. | **Низкий**, но ошибки ломают генерацию новых миссий. | **Средний** (риск затереть активную сессию). |
| **Простота тестирования** | **Идеальная:** 100% детерминированные чистые функции поверх `localStorage`, тестируются за миллисекунды без DOM. | **Средняя:** требует фикстур хранилища миссий и моков совместимости `activeAIPlan`. | **Высокая:** хорошо покрывается unit-тестами после выноса `StorageService`. | **Средняя:** требует моков `fetch` и `AbortController`. | **Низкая:** требует полноценного DOM-окружения и таймеров. |
| **Возможность временного compatibility adapter** | **Тривиальная:** регистрация в `FocusModeApp.services.storage` полностью удовлетворяет все остальные скрипты. | Требует синхронизации `window.missionRepository`, `activeAIPlan` и `missionProgress`. | Требует синхронизации `window.activeSession` и делегатов `v13`/`v13.1`. | Требует расщепления UI-кода `realAIMissionV6` и сетевого кода. | Требует сохранения 6 глобальных `window.*` функций для inline `onclick`. |

## 6.2. Итоговая рекомендация
**Первым кандидатом на вынос после Build Foundation однозначно выбирается `Storage Service` (`src/services/storage-service.js` в связке с `architectureShellV122` в `v15.2`), а сразу за ним вторым шагом (`v15.3`) — `Session Repository` и `Mission Repository`.**

**Обоснование:**
1. `StorageService` (`storageServiceV123`, строка 1028) уже спроектирован в `v14` как самодостаточный модуль с виртуальным путём `src/services/storage-service.js`, не имеет **ни одной** зависимости от DOM или других бизнес-доменов и стоит в самом начале цепочки загрузки.
2. Он предоставляет чистый контракт из 8 методов (`isAvailable`, `getText`, `setText`, `getJSON`, `setJSON`, `remove`, `has`, `backup`), который проверяется встроенным гейтом `architectureStabilizationV1325`.
3. Вынос `StorageService` первым создаёт фундамент для чистых unit-тестов и позволяет на следующем шаге (`v15.3`) импортировать его напрямую через `import { storageService } from '../services/storage-service.js'` в `SessionRepository` и `MissionRepository`.

---

# 7. Build Foundation design (`v15.1`)

Ниже представлен детальный технический проект этапа `v15.1 Build Foundation` (только архитектурная спецификация; файлы на текущем этапе планирования не создаются).

## 7.1. Нужен ли Vite и почему
**Да, используется Vite (vanilla JS mode без фреймворков).**
* В `v15` категорически запрещён переход на React/Vue/Svelte, но для сборки ~18 ES-модулей и 7 CSS-файлов с корректным разрешением путей на GitHub Pages (`/FocusModePWA/`) и детерминированным порядком CSS-каскада необходим лёгкий сборщик.
* Vite нативно поддерживает корневой `index.html` как точку входа, умеет оставлять внешние CDN-скрипты нетронутыми и копирует статические PWA-файлы из `public/` (или корня) без переписывания кода приложения.

## 7.2. Минимальный `package.json`
Файл содержит исключительно `vite` в `devDependencies` и встроенный тест-раннер Node.js (`node --test`) для Gate A / Gate B без лишних библиотек:
```json
{
  "name": "focus-mode-pwa",
  "version": "15.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --port 3000",
    "build": "vite build",
    "preview": "vite preview --port 4173",
    "test:unit": "node --test tests/unit/**/*.test.js"
  },
  "devDependencies": {
    "vite": "^6.0.0"
  }
}
```

## 7.3. Минимальный `vite.config.js`
Конфигурация использует относительный базовый путь `base: './'`, благодаря чему собранный бандл одинаково работает:
* на GitHub Pages по адресу `https://<user>.github.io/FocusModePWA/`;
* на любом локальном статическом сервере (`vite preview`);
* в корне домена или при установке PWA.

```javascript
import { defineConfig } from 'vite';

export default defineConfig({
  base: './',
  publicDir: 'public',
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    emptyOutDir: true,
    sourcemap: true,
    target: 'es2022',
    cssCodeSplit: false
  },
  server: {
    port: 3000,
    strictPort: true
  }
});
```
> **Важная деталь (`cssCodeSplit: false`):** все CSS-файлы в `v15.11` должны собираться в один CSS-файл со строгим сохранением порядка импортов из `src/main.js`, чтобы исключить изменение специфичности и порядка правил `v13.3.13` и `v13.3.15`.

## 7.4. Entry point (`src/main.js`)
В `v15.1` точка входа подключается в конец `index.html`:
```html
<script type="module" src="./src/main.js"></script>
```
На шаге `v15.1` файл `src/main.js` содержит только проверку готовности оболочки:
```javascript
// src/main.js (v15.1 Build Foundation)
if (typeof window !== 'undefined' && window.FocusModeApp?.core?.architectureStabilization) {
  window.FocusModeApp.core.architectureStabilization.assertStable();
}
```

## 7.5. Base path для GitHub Pages проекта `FocusModePWA`
* Репозиторий публикуется на GitHub Pages по пути `/FocusModePWA/`.
* Использование `base: './'` в `vite.config.js` и относительных ссылок `./manifest.webmanifest`, `./service-worker.js`, `./icons/...` гарантирует, что браузер разрешает все ресурсы относительно `/FocusModePWA/index.html`, не обращаясь в корень домена `https://<user>.github.io/`.

## 7.6. Работа `manifest.webmanifest`, `service-worker.js` и `icons/`
Чтобы Vite включил PWA-артефакты в корень `dist/` без хеширования их имён:
1. Файлы `manifest.webmanifest`, `service-worker.js` и папка `icons/` (`icon-192.png`, `icon-512.png`, `icon-maskable-512.png`, `apple-touch-icon.png`) размещаются в директории `public/` (или копируются плагином при сборке один-в-один в корень `dist/`), при этом на этапах `v15.1`–`v15.11` исходные файлы в корне остаются нетронутыми.
2. В `index.html` ссылка `<link rel="manifest" href="./manifest.webmanifest"/>` и `<link rel="apple-touch-icon" href="./icons/apple-touch-icon.png"/>` остаются с относительными путями `./`.
3. Регистрация `navigator.serviceWorker.register('./service-worker.js')` продолжает указывать на `./service-worker.js` в той же директории (`scope: './'`).

## 7.7. Сохранение CDN-зависимостей (Tailwind, Lucide, Google Fonts)
В `<head>` файла `index.html` **без изменений** сохраняются внешние CDN-теги:
* `<script src="https://cdn.tailwindcss.com"></script>` и `<script>tailwind.config={darkMode:'class'}</script>`;
* `<script src="https://unpkg.com/lucide@latest"></script>`;
* `<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap" rel="stylesheet"/>`.
Vite не бандлит внешние `https://` скрипты. Это гарантирует 100% совпадение генерации классов Tailwind Play CDN и иконок `lucide.createIcons()` между `v14` и `v15`.

## 7.8. Способ постепенного подключения ES modules (Hybrid Execution Bridge)
Критическая техническая особенность браузеров: обычные теги `<script>` без атрибутов выполняются **синхронно во время парсинга HTML**, тогда как `<script type="module">` всегда выполняется **отложенно (`defer`) после парсинга документа**, но до события `DOMContentLoaded`.
Чтобы на промежуточных этапах `v15.2`–`v15.9` модули, вынесенные в `src/`, были гарантированно доступны для ещё оставшихся в `index.html` скриптов:
1. **Правило порядка:** Скрипты, которые зависят от уже вынесенного ES-модуля, либо сами импортируются в `src/main.js`, либо переводятся на `<script type="module">` в том же порядке объявления (поскольку несколько `<script type="module">` в HTML выполняются строго последовательно в порядке появления в документе!).
2. **Альтернатива для `v15.2`–`v15.9`:** При первом же выносе базового модуля (`v15.2`) оставшиеся inline-скрипты в `index.html` получают атрибут `type="module"` (с явной привязкой к `window.*`), либо выносятся снизу вверх/сверху вниз через `src/main.js`, чтобы все скрипты работали в единой очереди выполнения ES-модулей строго в исходном порядке строк 629 $\rightarrow$ 5417.

## 7.9. Команды и GitHub Pages Deployment Strategy
* **Dev command:** `npm run dev` (запускает `vite --port 3000`).
* **Build command:** `npm run build` (формирует статическую директорию `dist/`).
* **Output directory:** `dist/`.
* **Preview command:** `npm run preview` (запускает локальный сервер поверх `dist/` на порту `4173`).
* **GitHub Pages deployment strategy:** GitHub Actions workflow (`.github/workflows/deploy-pages.yml`) выполняет `npm ci && npm run build` и публикует артефакт `dist/` в GitHub Pages (`actions/upload-pages-artifact` + `actions/deploy-pages`), либо деплоит содержимое `dist/` в ветку `gh-pages`.

---

# 8. Compatibility policy

В течение всей миграции `v15.1`–`v15.12` действует строгая политика обратной совместимости. Ни один контракт не удаляется в том же шаге, где создаётся новый модуль.

| № | Тип совместимости | Правило поддержки во время `v15.1`–`v15.12` | Условие, после которого слой можно удалить |
| :--- | :--- | :--- | :--- |
| **1** | **Legacy globals (`let`/`var` состояния)** | Переменные `activeSession`, `sessionRecoveryCandidate`, `pendingAIPlan`, `activeAIPlan`, `missionProgress`, `ucEndsAt`, `timerDuration` синхронизируются через геттеры/сеттеры или явный `syncLegacyGlobals()` в `src/core/app-state.js`. | Только после `v15.10`, когда все 34 inline-скрипта перенесены в ES-модули и обращаются к `appState` напрямую. Ссылки `window.activeSession` и `window.activeAIPlan` сохраняются до `v16`. |
| **2** | **Window aliases (`window.*` функции)** | Все 25+ функций, вызываемых из HTML или внешних модулей (`setAppScreen`, `startSession`, `openMission`, `prepareLearningSession`, `confirmAIPlan` и др.), экспортируются на `window` в `src/core/runtime-ownership.js`. | Только после полной замены всех inline `onclick="..."` в HTML на `addEventListener` / делегирование событий (за пределами `v15`, в `v16+`). В рамках `v15` **не удаляются**. |
| **3** | **`FocusModeApp` module registry** | `window.FocusModeApp` и все его ветви (`core`, `state`, `services`, `repositories`, `features`, `compatibility`) сохраняют 100% обратную совместимость; каждый новый ES-модуль вызывает `FocusModeApp.core.registerModule(...)`. | **Никогда не удаляется в `v15`.** Является официальным публичным API архитектуры и проверяется гейтами `v13.2.4` и `v13.2.5`. |
| **4** | **Inline `onclick` handlers** | Все атрибуты `onclick="..."` в статическом HTML (`index.html`) и в динамических шаблонах (`renderMissionHeroV116`, `openMissionActionsV116`, `renderAIPlan`) продолжают работать через глобальные `window.*` алиасы. | Только в будущем этапе после `v15.12`, когда динамические шаблоны перейдут на `data-action` делегирование. В `v15` сохраняются полностью. |
| **5** | **Wrapper functions (Monkey-patching)** | При выносе домена внешняя точка входа сохраняет финальное поведение всей цепочки обёрток. | Внутренние перекрытые обёртки одного домена (например, 5 промежуточных версий `resumeRecoveredSession`) удаляются внутри этапа этого домена (`v15.5`) после прохождения Gate C. Междоменные обёртки (`applyAppScreenImmediately`) заменяются на хуки навигации в `v15.10`. |
| **6** | **DOM IDs (`#app-screen-*`, `#ucCanvas` и др.)** | Все существующие `id`, `class` и `data-*` атрибуты HTML-элементов заморожены. | **Запрещено удалять или переименовывать в `v15`.** |
| **7** | **Storage keys (`localStorage`)** | Все 7 канонических ключей, 3 легаси-ключа, флаг `focus-mode-missions-v11-5-migrated` и 6 backup-ключей заморожены по именам и схемам. | Канонические ключи **не удаляются**. Легаси-ключи (`focus-mode-active-mission`, `focus-mode-mission-progress-v7`, `focusmode-session-history-v11`) сохраняются минимум до `v16` для поддержки миграции со старых устройств. |
| **8** | **Old saved data (v7/v10/v11)** | `migrateLegacyMissionV115()` и fallback-чтение `focusmode-session-history-v11` в `SessionRepository` выполняются автоматически при старте. | Сохраняются на всём протяжении `v15`. |
| **9** | **Service Worker cache** | В `v15.1`–`v15.11` `service-worker.js` не меняется. В `v15.12` константа `CACHE` получает новую версию (`focus-mode-plus-v15-0-0-modular`), а массив `CORE` расширяется путями к собранным модулям/стилям, при этом старый кэш автоматически очищается в событии `activate`. | Старое имя кэша `focus-mode-plus-v11-8-1-aura-blend` удаляется самим Service Worker в обработчике `activate` после релиза `v15.12`. |
| **10** | **PWA installation** | `manifest.webmanifest`, `start_url`, `scope: "./"`, `icons/*`, `apple-mobile-web-app-*` мета-теги и `#pwaInstallHelp` остаются неизменными. | **Не удаляются.** Установленное на домашний экран iOS/Android приложение должно обновиться до `v15` без переустановки и без потери `localStorage`. |

---

# 9. Testing gates

Ни один шаг миграции (`v15.1` – `v15.12`) не может считаться завершённым без прохождения обязательных проверочных ворот (**Gates A–F**).

## Gate A: Static Checks, Imports, Syntax & Duplicate Identifiers
Выполняется **после каждого изменения кода**:
1. **Сборка и синтаксис:** `npm run build` завершается с кодом `0`, без ошибок парсинга ES-модулей, неразрешённых импортов или циклических зависимостей, ломающих инициализацию.
2. **Отсутствие дублирующихся идентификаторов:** Внутри каждого модуля и в глобальной области проверено отсутствие конфликтующих повторных объявлений `let`/`const`/`function`.
3. **Runtime Stabilization Gate:** В консоли браузера при загрузке страницы выполняются встроенные инспекторы:
   * `window.FocusModeApp.core.architectureStabilization.inspect()` $\rightarrow$ `{ stable: true, blockers: [] }`;
   * `window.FocusModeApp.core.legacyCleanupGate.inspect()` $\rightarrow$ `{ safeForPhysicalDeletion: true, blockers: [] }`.

## Gate B: Storage Compatibility, Old User Data & Corrupted JSON Fallback
Выполняется **после этапов `v15.2`, `v15.3`, `v15.4`, `v15.5`, `v15.6`, `v15.8`, `v15.12`**:
1. **Чтение существующих данных `v14`:** Загрузка приложения с эталонным дампом `localStorage` от `v14` (2 активные миссии, 1 архивная, 5 записей в `focusmode-session-history-v11-1`) отображает все миссии и статистику без изменений.
2. **Миграция со старых ключей (`v7`/`v10`):** При очищенном `focus-mode-missions-v11-5` и заполненных `focus-mode-active-mission` + `focus-mode-mission-progress-v7` загрузка страницы автоматически мигрирует данные в `focus-mode-missions-v11-5` и выставляет `focus-mode-missions-v11-5-migrated = '1'`.
3. **Изоляция повреждённого JSON:** Запись невалидной строки `"{broken_json"` в `focus-mode-missions-v11-5`, `focusmode-active-session-v11` или `focusmode-session-history-v11-1` не вызывает необработанного исключения: повреждённое значение копируется в соответствующий `*-corrupt-backup` / `*-repository-backup` ключ, а приложение инициализируется с чистым дефолтным состоянием.

## Gate C: Core Session Flows (Quick Focus, Mission Session, History, Recovery)
Выполняется **после каждого этапа с `v15.1` по `v15.12`**:
1. **Quick Focus:** Настройка длительности (например, 15 мин) $\rightarrow$ старт $\rightarrow$ работа $\ge 1$ мин $\rightarrow$ завершение. В `focusmode-session-history-v11-1` добавляется ровно 1 запись `type: 'quick'`, `missionId: null`. Счётчик «Heute gesammelt» на Home мгновенно увеличивается. Состояние `focus-mode-missions-v11-5` не изменилось.
2. **Mission Session:** Открытие активной миссии $\rightarrow$ старт текущего активного шага $\rightarrow$ проверка наличия `missionId` и `missionStepId` в `focusmode-active-session-v11` $\rightarrow$ завершение сессии $\rightarrow$ переход на экран самооценки `app-screen-result`.
3. **History:** Экран `app-screen-history` содержит строго по 1 карточке на уникальный `session.id`; фильтры «Heute», «Diese Woche», «Alle» корректно фильтруют список.
4. **Recovery:** Старт 25-минутной сессии $\rightarrow$ имитация 5 минут работы (`elapsedMs = 300000`) $\rightarrow$ жёсткая перезагрузка страницы (`F5`). Переход на `home` заблокирован, открыт `app-screen-session-recovery`, показано 5 минут затраченного времени. Нажатие «Продолжить» открывает `timer` на 20:00 и продолжает обратный отсчёт.

## Gate D: Multi-Mission Isolation & Management
Выполняется **после этапов `v15.3`, `v15.5`, `v15.6`, `v15.7`, `v15.8`, `v15.12`**:
1. **Лимит двух миссий:** При наличии 2 активных миссий попытка создать 3-ю миссию или восстановить миссию из архива блокируется с корректным сообщением.
2. **Переключение миссий:** Переключение между Миссией А и Миссией Б в карусели Home меняет `selectedMissionId` и открывает соответствующий план, не затрагивая `primaryMissionId`.
3. **Независимый прогресс и правильный `missionId`:** Выполнение шага во второй (не главной) миссии продвигает `currentStepId` и `completedStepIds` только у выбранной миссии; первая миссия остаётся нетронутой.
4. **Защита активной сессии:** Попытка архивировать или удалить миссию, по которой запущена активная сессия или висит Recovery, отклоняется (`active_session_exists`).

## Gate E: Optional Adaptation (Adapt, Skip, Retry, AI Failure)
Выполняется **после этапов `v15.6`, `v15.8`, `v15.9`, `v15.12`**:
1. **Adapt:** На экране `result` нажатие «Plan mit AI anpassen» коммитит шаг, отправляет POST на `/adapt-mission`, заменяет только оставшиеся `pending` шаги выбранной миссии и открывает экран `update` («MISSION AKTUALISIERT»).
2. **Skip:** Нажатие «Ohne Anpassung fortfahren» продвигает шаг локально, открывает «ERGEBNIS GESPEICHERT» и **не совершает ни одного сетевого запроса** к `/adapt-mission`.
3. **AI Failure & Retry:** При отключённом интернете нажатие «Plan mit AI anpassen» сохраняет локальный результат сессии и открывает экран ошибки «ANPASSUNG NICHT VERFÜGBAR» с кнопками повтора и продолжения без адаптации. После включения сети нажатие «Erneut versuchen» успешно адаптирует план **без дублирования записи сессии в истории и без повторного сдвига шага**.

## Gate F: GitHub Pages, PWA, Service Worker, Offline Reload, Safe Area & Cosmic Timer
Выполняется **после этапов `v15.1`, `v15.10`, `v15.11`, `v15.12`**:
1. **GitHub Pages Base Path:** Раздача собранной папки `dist/` по префиксу `/FocusModePWA/` загружает все скрипты, стили, иконки и манифест без единой ошибки `404`.
2. **PWA & Service Worker:** Манифест валиден, `service-worker.js` переходит в состояние `activated and running`.
3. **Offline Reload:** Переключение DevTools Network в режим `Offline` и перезагрузка страницы (`F5`) мгновенно открывают приложение из кэша Service Worker с полным сохранением миссий, истории и работающим Quick Focus.
4. **Safe Area & Layout Restore:** На экране `quicksetup` активен `html.quick-focus-surface` (`#f8fafc`); на экране `plan` бейджи `.step-time` не перекрывают заголовки шагов на ширине 320px, а `#missionProtectionCard` расположен строго над `#aiPlanExplanation`.
5. **Cosmic Timer:** На экране `timer` анимируется `canvas#ucCanvas`, при уходе с экрана `timer` цикл `requestAnimationFrame` останавливается (`stopUc()`), при сворачивании вкладки на 30 секунд и возврате таймер компенсирует время по `ucEndsAt`.

---

# 10. Rollback strategy

## 10.1. Базовая точка отсчёта и ветвление
* **Исходный stable tag:** `v14-recovery-merge-stable` (коммит `81809e9d1b784d5cbf2fdef3418c77f7da45ee6c`, ветка `production`).
* **Рабочая ветка разработки:** `dev` (или изолированная ветка `feat/v15-modular-extraction`, ответвлённая от `dev` на коммите `81809e9d1b784d5cbf2fdef3418c77f7da45ee6c`).
* **Ветка `production` неприкосновенна** до полного завершения `v15.12` и прохождения всех Gates A–F.

## 10.2. Таблица контрольных точек (Checkpoints) по этапам

| Этап | Checkpoint Tag / Commit | Команда быстрого отката этапа | Безопасность пользовательских данных при откате |
| :--- | :--- | :--- | :--- |
| **До начала** | `v14-recovery-merge-stable` (`81809e9`) | `git reset --hard v14-recovery-merge-stable` | 100% совместимость |
| **v15.1** | `v15.1-build-foundation` | `git revert HEAD` или `git reset --hard v14-recovery-merge-stable` | 100% (ключи `localStorage` не менялись) |
| **v15.2** | `v15.2-storage-completion` | `git reset --hard v15.1-build-foundation` | 100% (форматы `StorageService` идентичны `v14`) |
| **v15.3** | `v15.3-repositories` | `git reset --hard v15.2-storage-completion` | 100% (схемы `v11-5` и `v11-1` неизменны) |
| **v15.4** | `v15.4-session-runtime` | `git reset --hard v15.3-repositories` | 100% (`focusmode-active-session-v11` совместим с `v14`) |
| **v15.5** | `v15.5-recovery` | `git reset --hard v15.4-session-runtime` | 100% |
| **v15.6** | `v15.6-mission-flow` | `git reset --hard v15.5-recovery` | 100% |
| **v15.7** | `v15.7-multi-mission-ux` | `git reset --hard v15.6-mission-flow` | 100% |
| **v15.8** | `v15.8-optional-adaptation` | `git reset --hard v15.7-multi-mission-ux` | 100% |
| **v15.9** | `v15.9-ai-service` | `git reset --hard v15.8-optional-adaptation` | 100% |
| **v15.10** | `v15.10-navigation-bootstrap` | `git reset --hard v15.9-ai-service` | 100% |
| **v15.11** | `v15.11-style-extraction` | `git reset --hard v15.10-navigation-bootstrap` | 100% |
| **v15.12** | `v15-modular-baseline-stable` | `git reset --hard v15.11-style-extraction` | 100% |

## 10.3. Безусловные условия отката (Rollback Triggers)
Немедленный откат к предыдущему checkpoint-коммиту выполняется, если:
1. `FocusModeApp.core.architectureStabilization.inspect().stable === false` или `legacyCleanupGate.inspect().safeForPhysicalDeletion === false`;
2. Любой из тестов **Gate B, Gate C, Gate D, Gate E** завершился ошибкой и причина не устранена локальным хотфиксом в течение 15 минут;
3. Обнаружено повреждение или потеря данных в ключах `focus-mode-missions-v11-5` или `focusmode-session-history-v11-1`;
4. Нарушен инвариант однократной финализации сессии или изоляции Quick Focus от миссий.

## 10.4. Сохранность пользовательских данных при откате
Поскольку на всём протяжении `v15.1`–`v15.12` **строго запрещено** менять имена ключей `localStorage` и структуру JSON-объектов (`schemaVersion: 1`), откат кода с любого этапа `v15.x` обратно на `v14-recovery-merge-stable` **полностью сохраняет**:
* все активные, завершённые и архивированные миссии (`focus-mode-missions-v11-5`);
* всю историю сессий и статистику (`focusmode-session-history-v11-1`);
* текущую незавершённую сессию (`focusmode-active-session-v11`) и последнюю сессию (`focusmode-last-session-v11`);
* выбранный язык интерфейса (`focus-mode-language`).

## 10.5. Правило двухфазного удаления («Create First, Prune Later»)
**Категорически запрещено в одном и том же коммите создавать новый модуль и безвозвратно удалять старый код или файлы без промежуточной проверки:**
1. **Фаза А (Подключение):** Создаётся новый ES-модуль (например, `src/repositories/mission-repository.js`), подключается к `FocusModeApp`, а старый `<script id="...">` в `index.html` сначала отключается (или переводится в режим чтения из нового модуля) и проверяется прогоном Gates A–F.
2. **Фаза Б (Очистка):** Только после успешного прохождения всех гейтов старый inline-блок удаляется из `index.html`.
3. Файлы `index.html`, `manifest.webmanifest`, `service-worker.js` и иконки `icons/*` **никогда не удаляются**.

---

# 11. Risk register

Ниже приведён полный реестр из 15 архитектурных рисков миграции `v15` с оценкой вероятности, влияния, способом обнаружения, мерами предотвращения (mitigation) и целевым регрессионным тестом.

| № | Риск | Вероятность | Влияние | Обнаружение | Mitigation (Предотвращение) | Regression Test |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | **Global function override order** (нарушение порядка переопределения глобальных функций при переходе на `import`) | High | Critical | Вызов старой версии `startSession` или `resumeRecoveredSession` вместо финальной `v13.1.4` / `v13.1.5`; сбой инспектора `architectureStabilization`. | Консолидировать цепочки обёрток каждого домена в одну детерминированную функцию согласно Разделу 4 (Authoritative Owner Map) и соблюдать строгий порядок импортов в `src/main.js`. | Gate A (`assertStable`) + Gate C (Recovery & Session Start). |
| **2** | **Inline `onclick` dependency** (`ReferenceError: fn is not defined` при клике на кнопки в HTML и динамических шаблонах) | High | High | Ошибки `Uncaught ReferenceError` в консоли при клике на карточки миссий, шторку `#missionSheetV116` или кнопки таймера. | Централизованно экспортировать все публичные обработчики в `window.*` через `src/core/runtime-ownership.js` до момента рендера экранов. | Сквозной проклик всех интерактивных элементов на всех 18 экранах (Gate C, D, E). |
| **3** | **DOM ID dependency** (обращение скрипта к `#app-screen-*` или `#ucCanvas` до их появления в DOM или по изменённому ID) | Medium | High | Возврат `null` из `document.getElementById(...)`, поломка верстки плана или пустой экран. | Полный запрет на переименование любых `id` в `index.html`; выполнение ES-модулей после парсинга тела документа (`type="module"` гарантирует готовность статического DOM). | Gate A + проверка `arrangePlanSupportBlocks() === true`. |
| **4** | **Multiple `DOMContentLoaded` listeners** (пропуск или гонка 10+ слушателей `DOMContentLoaded`, если модуль загрузился при `document.readyState !== 'loading'`) | High | High | Не инициализируется `quickBreak = 0`, не срабатывает `refreshPlanSupportLayout()` или `assertStable()` после упаковки в бандл. | Заменить прямые `document.addEventListener('DOMContentLoaded', fn)` на хелпер `onDomReady(fn)`: `if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn, { once: true }); else queueMicrotask(fn);`. | Gate A + проверка инициализации кольца Quick Focus и экрана Plan при холодном и горячем старте. |
| **5** | **Animation race conditions** (гонка между асинхронным `setAppScreen` Web Animations API и синхронными `setTimeout(..., 0)` рендерами) | Medium | Medium | Мерцание экрана, зависший `opacity: 0.18` на `#appBody` или рендер карусели в скрытый контейнер. | Сохранить токен отмены `appScreenTransitionToken` и вызов `appScreenAnimation.cancel()` при `options.immediate: true` в `src/core/navigation.js`. | Быстрое переключение между `home`, `plan`, `missions` и `quicksetup` (Gate C, Gate D). |
| **6** | **Recovery navigation ownership** (проскок на экран `home` при перезагрузке страницы с активной сессией) | Medium | Critical | При `F5` во время работающего таймера открывается `home` вместо `app-screen-session-recovery`. | Встроить проверку `authoritativeRecoveryRouterV1314` непосредственно в ядро `setAppScreen` и `applyAppScreenImmediately` в `src/core/navigation.js` + сохранить capture-listener на кнопке Resume. | Gate C / Чек-лист п. 11 (перезагрузка на 5-й минуте 25-минутной сессии). |
| **7** | **Session finalization duplication** (двойная запись сессии при одновременном истечении таймера и клике пользователя) | Medium | Critical | Две строки с одинаковым или разным ID в `app-screen-history`, удвоение минут в «Heute gesammelt». | Сохранить идемпотентный фильтр `committedTokens` (`id::status`) в `terminalCommitV13`, дедупликацию по `id` в `SessionRepository.appendHistory` и `finalizedSessionGuardV1315`. | Gate C / Чек-лист п. 12 (History Integrity). |
| **8** | **Multi-Mission `selected` / `primary` confusion** (случайная перезапись главной миссии при просмотре второй миссии или адаптация не той миссии) | Medium | High | Переключение карточки в карусели меняет бейдж «Hauptmission» или обновляет шаги соседней миссии. | Строго разграничивать `missions.setSelected(id)` (контекст текущего экрана) и `missions.setPrimary(id)` (только по явному действию в шторке управления); передавать явный `missionId` в сессию и адаптацию. | Gate D / Чек-лист п. 6 и п. 8 (Mission Switching & Isolation). |
| **9** | **Optional Adaptation retry duplication** (повторное списание шага или дублирование записи истории при нажатии «Erneut versuchen») | Medium | High | После сетевого сбоя и успешного Retry миссия перескакивает через два шага вперёд. | Фиксировать локальный коммит сессии (`commitMissionSessionResult`) строго один раз до вызова сети и сохранять его в `state.result`; при `retry = true` отправлять только сетевой запрос `requestAdaptation(state.result)`. | Gate E / Чек-лист п. 10 (Adaptation Failure & Retry). |
| **10** | **Storage schema compatibility** (нарушение формата JSON при рефакторинге репозиториев) | Low | Critical | Потеря существующих миссий или истории у пользователей при обновлении с `v14` до `v15`. | Полный запрет на изменение структуры полей и `schemaVersion: 1`; автоматические unit-тесты на реальных JSON-слепках `v14` в Gate B. | Gate B (Storage Compatibility). |
| **11** | **Service Worker stale cache** (Service Worker отдаёт старый монолитный `index.html` или не находит новые JS/CSS чанки в оффлайне) | High | High | Белый экран в режиме Offline или загрузка старой версии `v11.8.1`/`v14` после деплоя `v15`. | Не менять SW до `v15.12`; в `v15.12` обновить имя кэша `CACHE` и список кэшируемых ассетов сборки, обеспечив удаление старого кэша в `activate`. | Gate F / Чек-лист п. 20 (Offline Reload в DevTools). |
| **12** | **GitHub Pages base path** (абсолютные пути `/src/...` или `/assets/...` ломаются при публикации в `/FocusModePWA/`) | High | High | Ошибки `404 Not Found` для JS, CSS, `manifest.webmanifest` или `icons/*` на GitHub Pages. | Использовать `base: './'` в `vite.config.js` и строго относительные пути `./manifest.webmanifest`, `./service-worker.js`, `./icons/...`. | Gate F (проверка сборки на локальном сервере с префиксом `/FocusModePWA/`). |
| **13** | **CDN dependency** (блокировка или задержка загрузки Tailwind Play CDN, Lucide или Google Fonts) | Low | Medium | Отсутствие утилитарных классов Tailwind или иконок при первом холодном старте без сети. | Сохранить штатные теги CDN в `<head>` в том же порядке и защиту `window.lucide?.createIcons?.()` через optional chaining во всех рендерерах. | Gate F (проверка рендера иконок и стилей). |
| **14** | **Large CSS cascade** (изменение визуального вида карточек плана или Safe Area при разделении `<style>` на файлы) | High | High | Бейдж времени `.step-time` налезает на заголовок шага, `#aiPlanExplanation` обрезает текст или оказывается выше `#missionProtectionCard`, в `quicksetup` пропадает фон `#f8fafc`. | Импортировать CSS-файлы в `src/main.js` строго в порядке их следования в `index.html`, отключить `cssCodeSplit` в Vite и держать правила `v13313` и `v13315` в самом конце каскада. | Gate F + Чек-лист п. 16, 17, 18. |
| **15** | **Migration from old `localStorage` values** (сбой при наличии на устройстве старых ключей `focus-mode-active-mission`, `focus-mode-mission-progress-v7` или `'ru'` в `focus-mode-language`) | Low | High | Исключение при старте или отображение пустого списка миссий у давнего пользователя. | Сохранить `removeRussianLanguage` в `bootstrap.js`, `migrateLegacyMissionV115()` в `mission-repository.js` и fallback `focusmode-session-history-v11` в `session-repository.js`. | Gate B / Чек-лист п. 19 (Storage Migration from Legacy Keys). |

---

# 12. Recommended execution order

## 12.1. Рекомендуемый порядок выполнения `v15`
Разделение выполняется строго последовательно по цепочке зависимостей **снизу вверх (Bottom-Up Infrastructure $\rightarrow$ Repositories $\rightarrow$ Core Runtime $\rightarrow$ Feature Controllers $\rightarrow$ UI & Styles)**:
1. **`v15.1` Build Foundation** — каркас Vite (`base: './'`) + пустой `src/main.js`.
2. **`v15.2` Storage Completion** — вынос `architectureShellV122`, `removeRussianLanguage`, `storageServiceV123` и `settings-repository.js`.
3. **`v15.3` Repository Extraction** — вынос `session-repository.js` и `mission-repository.js` + unit-тесты схем хранения.
4. **`v15.4` Session Runtime Extraction** — вынос `focus-session` (`startSession`, `checkpointActiveSession`, `endSessionByUser`, `finalizedSessionGuard`), `quick-focus` и `history`.
5. **`v15.5` Recovery Extraction** — вынос `recovery-controller.js` и `authoritativeRecoveryRouterV1314` (`recovery-router.js`).
6. **`v15.6` Mission Flow Extraction** — вынос `mission-flow.js` и `mission-renderer.js` (включая `planDurationProtectionRestoreRuntimeV13315`).
7. **`v15.7` Multi-Mission UX Extraction** — вынос карусели `Aura Blend` и экрана управления миссиями (`multi-mission-ux.js`).
8. **`v15.8` Optional Adaptation Extraction** — вынос `optional-adaptation-controller.js` (Adapt / Skip / Retry).
9. **`v15.9` AI Service Extraction** — консолидация сетевых вызовов `create-mission` и `adapt-mission` в `src/services/ai-service.js`.
10. **`v15.10` Navigation and Bootstrap** — вынос `navigation.js`, `runtime-ownership.js`, `screens.js` и `timer-renderer.js` (`ultraCosmicV91`).
11. **`v15.11` Style Extraction** — перенос 16 блоков `<style>` в 7 файлов `src/styles/*.css` без изменения порядка каскада.
12. **`v15.12` Modular Baseline** — обновление `service-worker.js`, финальная зачистка внутренних дублей и полный прогон `REGRESSION_CHECKLIST.md`.

## 12.2. Первые три технические задачи (First 3 Actionable Tasks)
1. **Задача 1 (`v15.1`):** Создать минимальные `package.json`, `vite.config.js` (`base: './'`, `cssCodeSplit: false`) и `src/main.js`, подключить `<script type="module" src="./src/main.js"></script>` в конец `index.html` и подтвердить прохождение Gate A и Gate F без единого изменения существующих скриптов.
2. **Задача 2 (`v15.2`):** Вынести `architectureShellV122` и `storageServiceV123` в `src/core/bootstrap.js` и `src/services/storage-service.js`, покрыть `StorageService` автоматическими unit-тестами (`node --test`: чтение/запись, `structuredClone`, `backup` битого JSON) и подтвердить прохождение `assertStable()`.
3. **Задача 3 (`v15.3`):** Вынести `SessionRepository` (`src/repositories/session-repository.js`) и `MissionRepository` (`src/repositories/mission-repository.js`), покрыть их unit-тестами на инварианты (лимит 2 активных миссий, обязательные `missionId`/`missionStepId`, единственность `session.id` в истории, миграция с `v7`) и пройти Gate B и Gate D.

## 12.3. Что категорически НЕ нужно делать одновременно (Strict Anti-Parallelism Rules)
* **НЕ выносить JS-логику и CSS-стили в одном коммите:** разделение стилей (`v15.11`) выполняется только тогда, когда вся JS-логика (`v15.1`–`v15.10`) уже стабилизирована.
* **НЕ менять `Recovery Router` одновременно с `Optional Adaptation` или `Mission Repository`:** этап `v15.5 (Recovery Extraction)` должен выполняться в полной изоляции.
* **НЕ удалять глобальные `window.*` алиасы одновременно с переносом функции в ES-модуль:** модуль сначала экспортирует функцию и вешает её на `window`, и только после проверки всех inline `onclick` фиксируется чекпоинт.
* **НЕ трогать `service-worker.js` на промежуточных шагах `v15.1`–`v15.11`:** во время разработки Service Worker кэширование не должно маскировать ошибки загрузки новых модулей.
* **НЕ совмещать физическое разделение файлов с рефакторингом алгоритмов, переименованием DOM ID или изменением текстов интерфейса.**

## 12.4. Рекомендуемое распределение задач по моделям ИИ

### Задачи для **Gemini Flash** (быстрые, детерминированные, низкорисковые операции):
* **`v15.1` Build Foundation:** создание шаблонных `package.json`, `vite.config.js`, базового `src/main.js`.
* **`v15.2` Storage Completion:** механический перенос уже изолированного `storageServiceV123` и написание unit-тестов для `StorageService`.
* **`v15.11` Style Extraction:** механическое копирование 16 блоков `<style id="...">` в файлы `src/styles/*.css` строго в исходном порядке без модификации селекторов.
* Запуск статических проверок Gate A (`npm run build`, `node --test`).

### Задачи для **Argon** (сложная архитектурная координация, распутывание цепочек обёрток, контроль инвариантов):
* **`v15.3` Repository Extraction:** аккуратное отделение CRUD-ядра `MissionRepository` (`multiMissionCoreV115`) от DOM-обёрток с сохранением `bindMissionCompatibilityV115`.
* **`v15.4` Session Runtime Extraction** и **`v15.5` Recovery Extraction:** консолидация 7 слоёв `resumeRecoveredSession` и 6 слоёв `startSession`, сохранение контрактов `authoritativeRecoveryRouterV1314` и `finalizedSessionGuardV1315`.
* **`v15.6` Mission Flow Extraction** и **`v15.8` Optional Adaptation Extraction:** сохранение точного взаимодействия между `MissionRepository`, `SessionRepository` и `planDurationProtectionRestoreRuntimeV13315`.
* **`v15.10` Navigation and Bootstrap:** замена 12-слойного monkey-patching `applyAppScreenImmediately` на детерминированный конвейер хуков навигации.

### Задачи, которые при необходимости можно отдать **Claude Sonnet**:
* **`v15.7` Multi-Mission UX Extraction** и **`v15.9` AI Service Extraction:** вынос UI-рендереров карусели `Aura Blend`, модальной шторки управления миссиями и клиентской нормализации `normalizeAIPlan` / `normalizeUpdatedSessions`.
* Написание расширенных интеграционных и регрессионных тестов для Gate B, Gate D и Gate E.

### Где **Claude Opus** НЕ нужен:
* **На всём протяжении `v15 Modular Extraction` (`v15.1`–`v15.12`) использование Claude Opus избыточно и не требуется.** Архитектура, границы доменов, схемы `localStorage` и финальные владельцы всех 18 критических функций уже полностью определены и стабилизированы в `v14 Recovery Merge` и в настоящем плане. Задача `v15` сводится к дисциплинированному пошаговому физическому разделению существующего кода по чек-листам без изобретения новой архитектуры.
