# Focus Mode+ v14.1 — Схема и спецификация хранилища (Storage Schema)

> **Baseline Version:** v14 Recovery Merge  
> **Stable Tag:** `v14-recovery-merge-stable`  
> **Production Branch:** `production`  
> **Stable Commit:** `81809e9d1b784d5cbf2fdef3418c77f7da45ee6c`

Документ описывает все ключи локального хранилища (`localStorage`), форматы сериализации, владельцев, функции доступа, механизмы защиты от сбоев и политику миграций.

---

## 1. Категоризация ключей хранилища

### 1.1. Канонические ключи (Canonical Keys)
Ключи, являющиеся единственным авторитетным источником состояния для текущей версии v14:
* `focus-mode-missions-v11-5` — хранилище многомиссионного состояния;
* `focusmode-active-session-v11` — снимок активной незавершенной сессии;
* `focusmode-last-session-v11` — снимок последней терминальной сессии;
* `focusmode-session-history-v11-1` — постоянное хранилище истории завершенных сессий;
* `focus-mode-language` — пользовательская языковая настройка интерфейса;
* `focus-pwa-help-v11` — отметка о закрытии баннера установки PWA на iOS;
* `focus-mode-last-ai-plan` — последний снимок ответа генерации плана от Worker.

### 1.2. Легаси-ключи совместимости (Legacy Compatibility Keys)
Ключи, используемые для обеспечения обратной совместимости или первичной миграции данных со старых версий:
* `focus-mode-active-mission` — одиночный план миссии v7–v10;
* `focus-mode-mission-progress-v7` — прогресс одиночной миссии v7;
* `focusmode-session-history-v11` — ранняя версия истории сессий без версии схемы.

### 1.3. Флаги миграции (Migration Flags)
* `focus-mode-missions-v11-5-migrated` — флаг успешного завершения переноса данных миссий из v7/v10 в формат v11.5.

### 1.4. Резервные копии и изоляция повреждений (Backup & Quarantine Keys)
* `focus-mode-missions-v11-5-corrupt-backup` — копия поврежденного хранилища миссий;
* `focusmode-active-session-v11-recovery-backup` — аварийная копия активной сессии из Recovery;
* `focusmode-active-session-v11-repository-backup` — аварийная копия активной сессии из Session Repository;
* `focusmode-last-session-v11-repository-backup` — аварийная копия последней сессии;
* `focusmode-session-history-repository-backup` — аварийная копия истории сессий при ошибках Session Repository;
* `focusmode-session-history-corrupt-backup` — аварийная копия истории из легаси-загрузчика.

---

## 2. Детальная спецификация ключей

### 1. `focus-mode-missions-v11-5`
* **Назначение:** Каноническое хранилище всех миссий пользователя (активных, завершенных и архивированных), выбранной и главной миссии.
* **Владелец:** `FocusModeApp.repositories.missions` (`src/repositories/mission-repository.js`).
* **Формат:** JSON-строка (Object).
* **Schema Version:** `1`.
* **Структура схемы:**
  ```json
  {
    "schemaVersion": 1,
    "missions": [
      {
        "id": "mission_uuid",
        "title": "string",
        "subject": "string",
        "type": "exam | assignment | project | custom",
        "deadlineText": "string",
        "status": "active | completed | archived",
        "createdAt": 1700000000000,
        "updatedAt": 1700000000000,
        "completedAt": null,
        "archivedAt": null,
        "currentStepId": "step_uuid",
        "estimate": { "totalMinutes": 60, "difficulty": "string", "explanation": "string" },
        "sourceAnalysis": { "detectedTasks": "string", "detectedPages": "string", "confidence": 0.9 },
        "steps": [
          {
            "id": "step_uuid",
            "title": "string",
            "task": "string",
            "durationMinutes": 20,
            "status": "pending | active | completed",
            "reason": "string",
            "requiredTools": [],
            "recommendedApps": [],
            "createdAt": 1700000000000,
            "updatedAt": 1700000000000,
            "completedAt": null
          }
        ],
        "progress": {
          "completedStepIds": ["step_uuid"],
          "history": [],
          "initialStepCount": 3
        }
      }
    ],
    "selectedMissionId": "mission_uuid | null",
    "primaryMissionId": "mission_uuid | null",
    "updatedAt": 1700000000000
  }
  ```
* **Функции чтения:** `loadMissionStoreV115()`, `missions.loadStore()`, `missions.getAll()`.
* **Функции записи:** `persistMissionStoreV115(store)`, `missions.create()`, `missions.update()`, `missions.delete()`.
* **Backup key:** `focus-mode-missions-v11-5-corrupt-backup`.
* **Migration behavior:** Инициализируется миграцией `migrateLegacyMissionV115()` из ключей `focus-mode-active-mission` и `focus-mode-mission-progress-v7`, если флаг `focus-mode-missions-v11-5-migrated !== '1'`.
* **Последствия потери:** Полная потеря созданных миссий, планов и истории шагов. Приложение сбрасывается к состоянию без миссий.
* **Можно ли удалить безопасно:** **НЕТ.** Удаление уничтожит пользовательский контекст обучения.

---

### 2. `focusmode-active-session-v11`
* **Назначение:** Хранение снимка текущей запущенной или ожидающей оценки/восстановления сессии фокуса.
* **Владелец:** `FocusModeApp.repositories.sessions` (`src/repositories/session-repository.js`).
* **Формат:** JSON-строка (Object).
* **Schema Version:** Поле отсутствует, валидация по `ACTIVE_STATES = ['created','running','paused','awaiting_resume','awaiting_review']`.
* **Структура схемы:**
  ```json
  {
    "id": "session_uuid",
    "type": "quick | mission",
    "status": "created | running | completed | cancelled | interrupted",
    "lifecycleState": "running | awaiting_resume | awaiting_review",
    "title": "string",
    "task": "string",
    "plannedMinutes": 25,
    "actualMinutes": 0,
    "startedAt": 1700000000000,
    "endsAt": 1700001500000,
    "finishedAt": null,
    "lastActiveAt": 1700000000000,
    "lastCheckpointAt": 1700000000000,
    "elapsedMs": 0,
    "missionId": "mission_uuid | null",
    "missionStepId": "step_uuid | null",
    "finalizationState": "saved | null",
    "result": { "reason": null, "outcome": null, "feedback": null },
    "ui": { "modeLabel": "string", "progressCurrent": 1, "progressTotal": 1 }
  }
  ```
* **Функции чтения:** `sessions.loadActive()`, `getActiveSession()`, `readRecoveryCandidateV131()`.
* **Функции записи:** `sessions.saveActive()`, `sessions.checkpoint()`, `sessions.clearActive()`, `setActiveSession()`.
* **Backup key:** `focusmode-active-session-v11-repository-backup`, `focusmode-active-session-v11-recovery-backup`.
* **Migration behavior:** Подвергается нормализации через `migrateActiveSessionV112(raw)` при чтении в потоке восстановления.
* **Последствия потери:** Прерванная сессия не сможет восстановиться после перезагрузки; активная сессия теряется.
* **Можно ли удалить безопасно:** Безопасно удалять **только** в состоянии простоя (когда нет активного таймера).

---

### 3. `focusmode-last-session-v11`
* **Назначение:** Сохранение снимка последней завершенной сессии для отображения результатов, ревью и аварийного восстановления.
* **Владелец:** `FocusModeApp.repositories.sessions`.
* **Формат:** JSON-строка (Object).
* **Функции чтения:** `sessions.loadLast()`, вспомогательные функции ревью.
* **Функции записи:** `sessions.saveLast()`, `saveSessionSnapshot()`.
* **Backup key:** `focusmode-last-session-v11-repository-backup`.
* **Migration behavior:** Сохраняется в неизменном JSON-формате.
* **Последствия потери:** Экран ревью после сессии может потерять метаданные последней сессии при жестком релоаде страницы.
* **Можно ли удалить безопасно:** Да, если сессия уже зафиксирована в истории.

---

### 4. `focusmode-session-history-v11-1`
* **Назначение:** Канонический реестр всех завершенных сессий фокуса (Quick Focus и шагов миссий). Источник данных для экранов History, Stats и счетчиков на Home.
* **Владелец:** `FocusModeApp.repositories.sessions`.
* **Формат:** JSON-строка (Object).
* **Schema Version:** `1`.
* **Структура схемы:**
  ```json
  {
    "schemaVersion": 1,
    "sessions": [
      {
        "id": "session_uuid",
        "type": "quick | mission",
        "status": "completed | cancelled | interrupted",
        "title": "string",
        "task": "string",
        "plannedMinutes": 25,
        "actualMinutes": 25,
        "startedAt": 1700000000000,
        "finishedAt": 1700001500000,
        "missionId": "mission_uuid | null",
        "missionStepId": "step_uuid | null",
        "result": { "outcome": "completed | partial | aborted", "rating": 3, "feedback": "string", "adaptationMode": "skipped | ai" },
        "ui": { "modeLabel": "string" }
      }
    ]
  }
  ```
* **Функции чтения:** `sessions.listHistory()`, `readHistoryStore()`, `loadHistoryStoreV131()`.
* **Функции записи:** `sessions.appendHistory(session)`, `terminalCommitV13(session)`.
* **Backup key:** `focusmode-session-history-repository-backup`, `focusmode-session-history-corrupt-backup`.
* **Migration behavior:** При отсутствии ключа выполняет fallback-чтение из `focusmode-session-history-v11` с автоматической нормализацией к schemaVersion 1.
* **Последствия потери:** Обнуление счетчиков времени на главном экране, очистка экранов истории и недельной аналитики.
* **Можно ли удалить безопасно:** **НЕТ.** Приведет к потере всей пользовательской статистики продуктивности.

---

### 5. `focus-mode-language`
* **Назначение:** Сохранение выбранного языка интерфейса.
* **Владелец:** `i18nScript` (`services.storage`).
* **Формат:** Обычная строка: `'de'` или `'en'`.
* **Функции чтения:** `StorageService.getText('focus-mode-language')`, `detectLanguage()`.
* **Функции записи:** `StorageService.setText('focus-mode-language', lang)`, `setLanguage(lang)`.
* **Backup key:** Отсутствует.
* **Migration behavior:** На старте скрипт `removeRussianLanguage` принудительно заменяет значение `'ru'` на `'de'`. При отсутствии значения вычисляется по языку браузера (`de` или `en`).
* **Последствия потери:** Язык интерфейса сбросится на системный язык браузера по умолчанию.
* **Можно ли удалить безопасно:** Да, произойдет автоопределение.

---

### 6. `focus-pwa-help-v11`
* **Назначение:** Флаг закрытия пользователем карточки-подсказки по добавлению PWA на экран «Домой» в iOS Safari.
* **Владелец:** `pwaBoot` (`services.storage`).
* **Формат:** Строка `'1'` или `'dismissed'`.
* **Функции чтения:** `StorageService.getText('focus-pwa-help-v11')`.
* **Функции записи:** `StorageService.setText('focus-pwa-help-v11', '1')`.
* **Backup key:** Отсутствует.
* **Последствия потери:** Подсказка по установке PWA появится повторно в iOS Safari.
* **Можно ли удалить безопасно:** Да, безвредно.

---

### 7. `focus-mode-last-ai-plan`
* **Назначение:** Промежуточный кэш последнего плана, возвращенного AI-воркером, до нажатия пользователем кнопки «Plan übernehmen».
* **Владелец:** `realAIMissionV6` (`services.storage`).
* **Формат:** JSON-строка (Object плана с полями `goal`, `estimate`, `sessions`, `sourceAnalysis`).
* **Функции чтения:** Восстановление плана при сбоях интерфейса подтверждения.
* **Функции записи:** `StorageService.setJSON('focus-mode-last-ai-plan', pendingAIPlan)`.
* **Backup key:** Отсутствует.
* **Последствия потери:** Если вкладка закрылась на экране подтверждения, план потребуется сгенерировать заново.
* **Можно ли удалить безопасно:** Да, если план уже принят и записан в `focus-mode-missions-v11-5`.

---

### 8. `focus-mode-missions-v11-5-migrated`
* **Назначение:** Флаг-маркер, подтверждающий факт успешной миграции данных со старых ключей v7/v10 в формат v11.5.
* **Владелец:** `multiMissionCoreV115`.
* **Формат:** Строка `'1'`.
* **Функции чтения:** `localStorage.getItem(MISSIONS_MIGRATION_KEY) === '1'`.
* **Функции записи:** `localStorage.setItem(MISSIONS_MIGRATION_KEY, '1')`.
* **Последствия потери:** При наличии старых ключей v7/v10 повторно запустится логика миграции (которая, однако, безопасна при заполненном хранилище v11.5).
* **Можно ли удалить безопасно:** Не рекомендуется.

---

### 9. Легаси-ключи `focus-mode-active-mission` и `focus-mode-mission-progress-v7`
* **Назначение:** Форматы хранения планов и прогресса, использовавшиеся до внедрения Multi-Mission Core в версии 11.5.
* **Владелец:** Легаси-модули v6/v7 (поддерживаются как compatibility-зеркала).
* **Формат:** JSON-строка.
* **Статус:** Deprecated. Записи в них поддерживаются только для совместимости со старыми вкладками.

---

## 3. Сводная таблица правил модификации

> [!CAUTION]
> **Ключи, которые КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО переименовывать или удалять без формального migration plan:**
> 1. `focus-mode-missions-v11-5`
> 2. `focusmode-active-session-v11`
> 3. `focusmode-last-session-v11`
> 4. `focusmode-session-history-v11-1`
> 5. `focus-mode-language`

Любое изменение ключей или структур данных обязано сопровождаться:
1. Созданием миграционного скрипта с проверкой версий схемы (`schemaVersion`);
2. Обеспечением аварийной изоляции старых данных в backup-ключи (`storage.backup()`);
3. Сохранением обратной совместимости чтения на один мажорный релиз назад.
