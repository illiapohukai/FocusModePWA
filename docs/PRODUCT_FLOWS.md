# Focus Mode+ v14.1 — Продуктовые сценарии (Product Flows)

> **Baseline Version:** v14 Recovery Merge  
> **Stable Tag:** `v14-recovery-merge-stable`  
> **Production Branch:** `production`  
> **Stable Commit:** `81809e9d1b784d5cbf2fdef3418c77f7da45ee6c`

Документ описывает фактические поведенческие сценарии стабильной версии v14, правила переходов состояний, задействованные репозитории, терминальные состояния, запрещенные побочные эффекты и риски регрессий.

---

## Flow 1: Quick Focus (Быстрый свободный фокус)

```
[Choice / Home] ──> [Quick Setup] ──> [Timer Screen] ──> [Complete / Cancel] ──> [History / Home]
```

* **Entry point:** Нажатие «Kurzen Fokus starten» в шторке выбора (`#focusChoiceOverlay` $\rightarrow$ `openQuickSetupFromChoice()`) или быстрый старт по умолчанию `startQuickFocus()`.
* **Repositories:** `FocusModeApp.repositories.sessions`.
* **Storage state transitions:**
  1. *До старта:* `focusmode-active-session-v11` отсутствует или равен `null`.
  2. *Старт:* Формируется объект `type: 'quick'`, `plannedMinutes: quickDuration`, `missionId: null`, `missionStepId: null`. Записывается в `focusmode-active-session-v11` со статусом `running`.
  3. *В процессе:* Периодические safe-point чекпоинты обновляют `lastCheckpointAt` и `elapsedMs` в `focusmode-active-session-v11`.
  4. *Завершение:* Записывается в `focusmode-session-history-v11-1`, дублируется в `focusmode-last-session-v11`, ключ `focusmode-active-session-v11` удаляется.
* **Terminal state:** `status: 'completed'` (если фактическое время $\ge$ 1 мин) или `status: 'cancelled'` (если отменено пользователем или длилось < 1 мин). Возврат на экран `home`.
* **Запрещённые side effects:**
  * Запрещено создавать связи `missionId` или `missionStepId`;
  * Запрещено вызывать методы `Mission Repository` или модифицировать `focus-mode-missions-v11-5`;
  * Запрещено изменять выбранную миссию (`selectedMissionId`) или открывать экран `result` для оценки понимания.
* **Regression risks:**
  * Попадание `undefined` или `null` в валидатор сессий;
  * Зависание активной сессии в хранилище при закрытии вкладки;
  * Искажение фонового цвета Safe Area (регулируется классом `.quick-focus-surface`).

---

## Flow 2: Mission Creation (Создание Focus Mission с AI-планированием)

```
[Home / Choice] ──> [Goal Intake] ──> [Analyzing (AI)] ──> [Confirmation] ──> [Plan Screen]
```

* **Entry point:** Нажатие «+ Neue Focus Mission» (`openGoalIntake()`).
* **Repositories:** `FocusModeApp.repositories.missions`.
* **Storage state transitions:**
  1. *Ввод:* Сбор текста цели, контекста, опционального сжатого фото (через Canvas в JPEG).
  2. *Анализ:* Отправка `FormData` на `FOCUS_AI_ENDPOINT`. Временный ответ сохраняется в `focus-mode-last-ai-plan`.
  3. *Подтверждение:* Пользователь нажимает «Plan übernehmen» (`confirmAIPlanV1311`). Вызывается `missions.create()`. Создается новая сущность миссии в `focus-mode-missions-v11-5`.
* **Terminal state:** Миссия сохранена в статусе `active`, `selectedMissionId` указывает на созданную миссию. Переход на экран `plan`.
* **Запрещённые side effects:**
  * Запрещено создавать 3-ю активную миссию (если активных миссий уже 2, вход блокируется);
  * Запрещено стартовать таймер сессии во время подтверждения плана;
  * Запрещено сохранять сырое несжатое фото в `localStorage`.
* **Regression risks:**
  * Сетевой таймаут (120 с) оставляет бесконечный лоадер;
  * Ошибка парсинга JSON ответа Worker;
  * Сброс выбранного типа миссии или уровня уверенности при повторном открытии.

---

## Flow 3: Mission Session (Фокусированная сессия по шагу миссии)

```
[Plan Screen] ──> [Start Step] ──> [Timer Screen] ──> [Session Finished] ──> [Result / Review Screen]
```

* **Entry point:** Клик по кнопке старта шага в плане (`prepareLearningSession(mid, sid)`).
* **Repositories:** `FocusModeApp.repositories.missions`, `FocusModeApp.repositories.sessions`.
* **Storage state transitions:**
  1. *Старт:* Создается сессия `type: 'mission'` с обязательными `missionId` и `missionStepId`. Записывается в `focusmode-active-session-v11`.
  2. *Выполнение:* Таймер тикает по метке `ucEndsAt`.
  3. *Завершение:* Терминальный коммит сохраняет сессию в `focusmode-session-history-v11-1` и `focusmode-last-session-v11`. Активная сессия переходит в состояние `awaiting_review` до завершения оценки.
* **Terminal state:** Экран `app-screen-result` с открытой формой самооценки (статус результата, уровень уверенности, текст обратной связи).
* **Запрещённые side effects:**
  * Запрещен старт шага, не совпадающего с текущим `currentStepId` активной миссии;
  * Запрещен переход на экран `home` минуя оценку `result` при успешном завершении шага миссии.
* **Regression risks:**
  * Старт шага заблокирован остатками старой сессии (предотвращается `Finalized Session Guard`);
  * Несовпадение идентификаторов шага между памятью и репозиторием.

---

## Flow 4: Optional Adaptation (Адаптация оставшихся шагов через AI)

```
[Result Screen] ──> [Adapt Button] ──> [Processing (AI)] ──> [Updated Screen] ──> [Plan / Home]
```

* **Entry point:** Нажатие «Plan mit AI anpassen» (`adaptMissionAfterSession()`).
* **Repositories:** `FocusModeApp.repositories.missions`, `FocusModeApp.repositories.sessions`.
* **Storage state transitions:**
  1. Сессионный результат фиксируется в истории шагов миссии `mission.progress.history`.
  2. Шаг помечается как `completed`, завершенные шаги пушатся в `completedStepIds`.
  3. POST-запрос отправляется на `FOCUS_AI_ADAPT_ENDPOINT`.
  4. Полученные новые шаги нормализуются и подставляются взамен невыполненных через `missions.replaceRemainingSteps()`.
  5. Очищается остаточное состояние ревью сессии (`clearReview()`).
* **Terminal state:** Экран `app-screen-update` с заголовком «MISSION AKTUALISIERT». Обновленная миссия сохранена в `focus-mode-missions-v11-5`.
* **Запрещённые side effects:**
  * Запрещено изменять или удалять ранее завершенные шаги миссии;
  * Запрещено изменять вторую активную миссию (строгая изоляция по `missionId`);
  * Запрещена повторная отправка запроса при двойном клике.
* **Regression risks:**
  * Конфликт версий `expectedUpdatedAt` при параллельных изменениях;
  * Сетевой сбой во время адаптации (должен корректно переводить в `Flow 6`).

---

## Flow 5: Skip Adaptation (Продолжение без адаптации)

```
[Result Screen] ──> [Continue without Adaptation] ──> [Local Saved Screen] ──> [Home Screen]
```

* **Entry point:** Нажатие «Ohne Anpassung fortfahren» (`continueMissionWithoutAdaptation()`).
* **Repositories:** `FocusModeApp.repositories.missions`, `FocusModeApp.repositories.sessions`.
* **Storage state transitions:**
  1. Вызывается `commitMissionSessionResult` с параметром `adaptationMode: 'skipped'`.
  2. В истории шагов миссии сохраняется результат оценки.
  3. Шаг помечается как `completed`, указатель `currentStepId` сдвигается на следующий запланированный шаг исходного плана.
  4. Вызывается `clearReview()`, активная сессия и кандидат восстановления обнуляются.
* **Terminal state:** Экран `app-screen-update` с заголовком «ERGEBNIS GESPEICHERT». Переход на `home`.
* **Запрещённые side effects:**
  * **СТРОГО ЗАПРЕЩЕНО:** Выполнять любые сетевые вызовы к AI или внешним воркерам;
  * Запрещено оставлять текущий шаг активным, если он был завершен;
  * Запрещено оставлять сессию в состоянии `awaiting_review`.
* **Regression risks:**
  * Ложное срабатывание восстановления сессии при следующем старте приложения;
  * Зависание кнопки сохранения без перехода на экран подтверждения.

---

## Flow 6: Adaptation Failure and Retry (Сбой адаптации и повторная попытка)

```
[Result Screen] ──> [Adaptation Failed] ──> [Retry Button (AI)] OR [Skip / Finish Button]
```

* **Entry point:** Ошибка сетевого запроса или таймаут внутри `adaptMissionAfterSession()`.
* **Repositories:** `FocusModeApp.repositories.missions`, `FocusModeApp.repositories.sessions`.
* **Storage state transitions:**
  1. Результат сессии уже надежно сохранен локально (`commitMissionSessionResult` выполнен до сетевого запроса).
  2. Шаги плана остаются в исходном состоянии.
  3. Состояние контроллера переходит в `state.status = 'adaptation_failed'`.
* **Terminal state:** Экран `app-screen-update` в режиме ошибки («ANPASSUNG NICHT VERFÜGBAR») с видимым блоком `#optionalAdaptationRetryActions`.
* **Доступные действия пользователя:**
  * *Повтор:* Нажатие «Erneut versuchen» (`retryOptionalAdaptation()`) $\rightarrow$ повторный запрос к AI с сохраненным `state.result`.
  * *Пропуск:* Нажатие «Ohne Anpassung weiter» (`continueMissionWithoutAdaptation()`) $\rightarrow$ переход на `home` с сохранением текущего плана.
* **Запрещённые side effects:**
  * Запрещена потеря уже сохраненного времени сессии и прогресса шага;
  * Запрещено дублирование записи сессии в историю при повторе.
* **Regression risks:**
  * Зацикливание экрана ошибки без возможности выйти на Home;
  * Потеря ссылки на `state.result`.

---

## Flow 7: Session Recovery (Восстановление прерванной сессии)

```
[Browser Reload / Launch] ──> [Recovery Screen] ──> [Resume to Timer] OR [Discard]
```

* **Entry point:** Запуск приложения при наличии валидной незаконченной сессии в `focusmode-active-session-v11`.
* **Repositories:** `FocusModeApp.repositories.sessions`, `FocusModeApp.repositories.missions`.
* **Storage state transitions:**
  * *Обнаружение:* `authoritativeRecoveryRouterV1314` на старте блокирует переход на `home` и принудительно открывает `app-screen-session-recovery`.
  * *Действие «Продолжить» (`resumeRecoveredSession`):*
    1. Проверяется целостность сессии и существование миссии/шага.
    2. Вычисляется реально затраченное время: `currentSessionActualMinutes = Math.floor(elapsedMs / 60000)`.
    3. Сессия переводится в `status: 'running'`, обновляется `lastActiveAt`.
    4. Осуществляется немедленный переход на экран `timer`, запускается космический холст и интервал.
  * *Действие «Завершить и оценить»:* Сессия финализируется, переход на экран ревью `result`.
  * *Действие «Сбросить»:* Сессия помечается как `interrupted`/`cancelled`, удаляется из активных, переход на `home`.
* **Terminal state:** Экран `timer` (при возобновлении) либо `home` (при сбросе).
* **Запрещённые side effects:**
  * Запрещен сброс таймера на начальное плановое время (потеря отработанных минут);
  * Запрещен непроизвольный редирект на экран `home` во время инициализации приложения.
* **Regression risks:**
  * Зависание экрана восстановления при битом объекте сессии (должна изолироваться в backup-ключ);
  * Конфликт старых обработчиков клика кнопки восстановления.

---

## Flow 8: Multi-Mission Execution (Параллельные миссии и изоляция)

```
[Home Carousel] ──> [Switch Mission] ──> [Plan of Selected Mission] ──> [Step Execution]
```

* **Entry point:** Клик по карточке миссии в карусели `#missionCarouselV116` или переключателю `#missionSwitcherV115`.
* **Repositories:** `FocusModeApp.repositories.missions`.
* **Storage state transitions:**
  1. Вызывается `missions.setSelected(missionId)`.
  2. Значение `selectedMissionId` обновляется в хранилище `focus-mode-missions-v11-5`.
  3. Совместимый объект `activeAIPlan` перепривязывается исключительно к выбранной миссии.
* **Terminal state:** Экран `plan` отображает данные выбранной миссии. Вторая активная миссия остается нетронутой.
* **Запрещённые side effects:**
  * Запрещено автоматическое изменение статусов шагов во второй активной миссии;
  * Запрещено изменение `primaryMissionId` без явного указания пользователя («Als Hauptmission festlegen»).
* **Regression risks:**
  * Случайный запуск шага из неактивной миссии;
  * Ошибочный рендеринг прогресса соседней миссии на карточке Home.

---

## Flow 9: Mission Management (Управление миссиями)

```
[Home / Choice] ──> [Missions Screen] ──> [Mission Actions Sheet] ──> [Rename / Archive / Restore / Delete]
```

* **Entry point:** Кнопка «Missionen verwalten» (`openMissionManagementV116()`).
* **Repositories:** `FocusModeApp.repositories.missions`.
* **Storage state transitions:**
  * *Переименование:* `missions.rename(id, newTitle)` обновляет поле `title` в объекте миссии.
  * *Сделать главной:* `missions.setPrimary(id)` переназначает `primaryMissionId`.
  * *Архивация:* Миссия переводится в `status: 'archived'`, запоминается `archivedCurrentStepId`, `currentStepId` обнуляется.
  * *Восстановление:* Переводит миссию из `archived` в `active` (только если активных миссий < 2).
  * *Удаление:* Полное удаление объекта миссии из массива `missions`.
* **Terminal state:** Обновленный список миссий в `focus-mode-missions-v11-5`, перерисовка экрана управления.
* **Запрещённые side effects:**
  * **СТРОГО ЗАПРЕЩЕНО:** Архивировать или удалять миссию, если по ней в данный момент запущена сессия таймера или активно восстановление;
  * Запрещено восстанавливать миссию из архива, если уже открыты две активные миссии.
* **Regression risks:**
  * Удаление активной сессии из памяти при удалении посторонней миссии;
  * Потеря истории прогресса при восстановлении из архива.
