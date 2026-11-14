# Focus Mode+ v14.1 — Контракты взаимодействия с API (API Contracts)

> **Baseline Version:** v14 Recovery Merge  
> **Stable Tag:** `v14-recovery-merge-stable`  
> **Production Branch:** `production`  
> **Stable Commit:** `81809e9d1b784d5cbf2fdef3418c77f7da45ee6c`

Документ описывает внешние интеграции клиентской части Focus Mode+ с AI-микросервисами, развернутыми на платформе Cloudflare Workers. 

> [!IMPORTANT]
> Настоящий документ строго разделяет то, что **подтверждено клиентским кодом** в текущем репозитории, и то, что **требует отдельного аудита исходного кода Cloudflare Worker**, так как серверная реализация Worker находится в отдельном репозитории/деплое. Никакие секретные API-ключи в клиенте не хранятся.

---

## 1. Обзор архитектуры взаимодействия с AI

Клиент Focus Mode+ взаимодействует с AI без промежуточного классического бэкенда. Запросы направляются напрямую на специализированные edge-эндпоинты Cloudflare Workers:
1. `FOCUS_AI_ENDPOINT` — генерация первоначального плана миссии на основе цели, контекста и фотоматериала.
2. `FOCUS_AI_ADAPT_ENDPOINT` — переоценка оставшихся шагов миссии на основе фактических результатов сессии и субъективного фидбека.

---

## 2. Спецификация контракта: `create-mission`

### 2.1. Метаданные интеграции
* **URL эндпоинта:** `https://focusmodedemoai.illia-pohukai.workers.dev/create-mission` (константа `FOCUS_AI_ENDPOINT`).
* **Клиентская функция:** `analyzeGoal()` (`src/services/ai-service.js`).
* **HTTP Метод:** `POST`.
* **Тип содержимого (Payload Type):** `multipart/form-data` (объект `FormData`).
* **Таймаут (v17.4.1):** `38 000` мс для текстовых запросов и `52 000` мс при наличии изображения (через `AbortController`, с защитой от повторных одновременных отправок `isMissionAnalyzeInFlight` и явной отменой через `cancelMissionAnalysis()`).

### 2.2. Формат входящего запроса (Client Payload)
Формируется в функции `analyzeGoal()`:
* `goalText` *(string, required if no image)*: текст описания цели из поля `#goalPrompt`.
* `missionType` *(string, required)*: `exam | assignment | project | custom` (`missionTypeForAPI()`).
* `confidence` *(string, required)*: `uncertain | partial | confident` (`confidenceForAPI()`).
* `schedule` *(JSON string, optional — отправляется только при выбранном `deadlineDate`)*:
  ```json
  {
    "currentLocalDate": "YYYY-MM-DD",
    "deadlineDate": "YYYY-MM-DD",
    "preparationStartDate": "YYYY-MM-DD",
    "preparationDays": 14,
    "startMode": "immediate | one_week_before | two_weeks_before | custom",
    "timezone": "Europe/Berlin",
    "preferredWeekdays": [1, 3, 5],
    "preferredSessionMinutes": 30
  }
  ```
  (`preferredWeekdays` не отправляется, если список пуст; `preferredSessionMinutes` не отправляется, если выбрано `AI wählt` / `null`).
* `image` *(File, optional)*: файл изображения (`image/jpeg`, `image/png`, `image/webp`; исходный файл до 20 МБ, клиентское сжатие в JPEG до 1800px и `<= 1.5 MB` с учетом EXIF-ориентации).

### 2.3. Ожидаемый ответ сервера (Response Shape)
* **HTTP Status:** `200 OK` (при ошибке: `4xx`/`5xx` с `{ "ok": false, "error": { "code": "...", "message": "...", "retryable": true|false } }`).
* **Формат:** `application/json`.
* **Ожидаемая JSON-структура (успех):**
  ```json
  {
    "ok": true,
    "plan": {
      "goal": {
        "title": "string",
        "subject": "string",
        "type": "exam | assignment | project | custom",
        "deadlineText": "string"
      },
      "sourceAnalysis": {
        "detectedTasks": "string",
        "detectedPages": "string",
        "confidence": 0.85,
        "missingInformation": ["string"],
        "facts": ["string"],
        "estimates": ["string"]
      },
      "estimate": {
        "totalMinutes": 120,
        "difficulty": "leicht | mittel | anspruchsvoll | unknown",
        "explanation": "string"
      },
      "scheduleWarning": "string (optional)",
      "sessions": [
        {
          "id": "session-1",
          "title": "string",
          "task": "string",
          "durationMinutes": 25,
          "scheduledDate": "YYYY-MM-DD (optional)",
          "reason": "string",
          "requiredTools": ["string"],
          "recommendedApps": ["string"]
        }
      ]
    }
  }
  ```

### 2.4. Клиентская нормализация (`normalizeAIPlan`)
* Проверяется наличие непустых `goal.title` и от 1 до 6 корректных `sessions` (с непустыми `title`, `task`/`reason` и положительным `durationMinutes`).
* Длительность каждого шага ограничивается диапазоном от 5 до 60 минут.
* Если у шага указана дата `scheduledDate` и в запросе был `deadlineDate`, проверяется `scheduledDate <= deadlineDate`.
* Поля экранируются функцией `escapeHTML()` перед рендерингом в DOM.

### 2.5. Обработка ошибок и Fallback
* `parseMissionErrorResponse()` корректно обрабатывает как структурированный `{ error: { code, message, retryable } }`, так и строковые/HTTP ошибки (без `[object Object]`).
* При отмене по таймауту (`AbortError`) или статусах `504`/`524`, `429`, `502`/`503` выводится понятное сообщение и кнопка повторной попытки; при `AbortError` от ручной отмены (`cancelMissionAnalysis()`) пользователь возвращается на `#app-screen-goal` без ошибки.
* Введенный текст, выбранный `schedule` и прикрепленное изображение сохраняются при любой ошибке.

### 2.6. Границы валидации
* **Подтверждено клиентским кодом:** single-flight lock, валидация MIME/размера и сжатие изображений до 1.5 МБ, валидация `schedule`, таймауты `38s`/`52s`, строгая нормализация ответа.
* **Требует исходников Cloudflare Worker (`WORKER_REQUIRED.md`):** см. `WORKER_REQUIRED.md` для списка отсутствующих серверных файлов и требуемых правок Worker.

---

## 3. Спецификация контракта: `adapt-mission`

### 3.1. Метаданные интеграции
* **URL эндпоинта:** `https://focusmodedemoai.illia-pohukai.workers.dev/adapt-mission` (константа `FOCUS_AI_ADAPT_ENDPOINT` или `ENDPOINT`).
* **Клиентская функция:** `requestAdaptation(result)` (скрипт `optionalAdaptationControllerV133`, строки 5259–5265).
* **HTTP Метод:** `POST`.
* **Тип содержимого (Payload Type):** `application/json; charset=utf-8`.
* **Таймаут:** 120 000 мс (120 секунд) через нативный `AbortController`.

### 3.2. Формат входящего запроса (Client Payload)
Формируется в функции `requestAdaptation()`:
```json
{
  "missionId": "mission_uuid",
  "missionUpdatedAt": 1700000000000,
  "mission": {
    "title": "string",
    "subject": "string",
    "deadlineText": "string"
  },
  "currentSession": {
    "id": "step_uuid",
    "title": "string",
    "task": "string",
    "durationMinutes": 25
  },
  "plannedMinutes": 25,
  "actualMinutes": 23,
  "status": "completed | partial | aborted",
  "rating": 4,
  "feedback": "string",
  "result": {
    "outcome": "completed | partial | aborted",
    "rating": 4,
    "feedback": "string",
    "plannedMinutes": 25,
    "actualMinutes": 23
  },
  "remainingSessions": [
    {
      "id": "step_uuid_2",
      "title": "string",
      "task": "string",
      "durationMinutes": 30,
      "status": "pending"
    }
  ],
  "history": [
    {
      "sessionId": "session_uuid",
      "outcome": "completed",
      "rating": 4,
      "actualMinutes": 23
    }
  ]
}
```

### 3.3. Ожидаемый ответ сервера (Response Shape)
* **HTTP Status:** `200 OK`.
* **Формат:** `application/json`.
* **Ожидаемая JSON-структура:**
  ```json
  {
    "ok": true,
    "adaptation": {
      "message": "string",
      "summary": "string",
      "mainDifficulty": "string",
      "updatedSessions": [
        {
          "id": "adapted_timestamp_0",
          "title": "string",
          "task": "string",
          "durationMinutes": 20,
          "reason": "string",
          "requiredTools": ["string"],
          "recommendedApps": ["string"]
        }
      ]
    }
  }
  ```

### 3.4. Клиентская нормализация (`normalizeUpdatedSessions`)
Ответ сервера санитизируется функцией `normalizeUpdatedSessions`:
* Проверяется наличие непустого массива `updatedSessions`;
* Если массив пуст или некорректен, выбрасывается исключение `'adaptation_sessions_invalid'`;
* Каждый элемент массива мапится к строгому формату:
  * `id`: строка (если отсутствует, генерируется `'adapted_' + Date.now() + '_' + index`);
  * `durationMinutes`: принудительно ограничивается `Math.max(5, Math.min(60, Number(x.durationMinutes) || 20))`;
  * Инструменты и приложения валидируются как массивы;
* Новые шаги подставляются **строго вместо еще не выполненных шагов** через `missions.replaceRemainingSteps()`.

### 3.5. Обработка ошибок и Fallback
* До вызова API результат сессии гарантированно закоммичен локально функцией `commitMissionSessionResult()`.
* При сетевом сбое, таймауте или невалидном ответе:
  1. Вызывается `renderLocalResult(state.result, true)`;
  2. Открывается экран `update` с заголовком «ANPASSUNG NICHT VERFÜGBAR» и сообщением о том, что прогресс сохранен;
  3. Показываются кнопки повтора («Erneut versuchen») и продолжения без адаптации («Ohne Anpassung weiter»).
* Пользователь никогда не теряет отработанное время или завершенный шаг из-за падения AI-сервера.

### 3.6. Границы валидации
* **Подтверждено клиентским кодом:**
  * Проверка существования миссии в локальном репозитории перед отправкой;
  * Проверка неизменности метки времени миссии (`expectedUpdatedAt`);
  * Предотвращение гонки запросов через `AbortController`;
  * Полная изоляция: при сбое адаптации сохраняется исходный план.
* **Требует аудита Cloudflare Worker (не подтверждено в клиенте):**
  * Проверка валидности структуры входящего JSON на стороне воркера;
  * Промпты адаптации и алгоритм распределения оставшегося времени;
  * Обработка краевых случаев (когда пользователь сообщил о полном непонимании материала при оценке 1/5).
