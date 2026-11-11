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
* **Клиентская функция:** `analyzeGoal()` (скрипт `realAIMissionV6`, строки 2637–2659).
* **HTTP Метод:** `POST`.
* **Тип содержимого (Payload Type):** `multipart/form-data` (объект `FormData`).
* **Таймаут:** 120 000 мс (120 секунд) через нативный `AbortController`.

### 2.2. Формат входящего запроса (Client Payload)
Формируется в функции `analyzeGoal()`:
* `goalText` *(string, required if no image)*: текст описания цели, введенный пользователем в поле `#goalPrompt`.
* `missionType` *(string, required)*: нормализованный тип миссии (`'exam' | 'assignment' | 'project' | 'custom'`), преобразованный функцией `missionTypeForAPI()`.
* `confidence` *(string, required)*: нормализованный уровень уверенности (`'uncertain' | 'partial' | 'confident'`), преобразованный функцией `confidenceForAPI()`.
* `image` *(File, optional)*: файл изображения учебного материала (JPEG, сжатый клиентом через Canvas до максимальной стороны 1800px, качество 0.82).

### 2.3. Ожидаемый ответ сервера (Response Shape)
* **HTTP Status:** `200 OK`.
* **Формат:** `application/json`.
* **Ожидаемая JSON-структура:**
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
      "sessions": [
        {
          "id": "session-1",
          "title": "string",
          "task": "string",
          "durationMinutes": 25,
          "reason": "string",
          "requiredTools": ["string"],
          "recommendedApps": ["string"]
        }
      ]
    }
  }
  ```

### 2.4. Клиентская нормализация (`normalizeAIPlan`)
Ответ сервера считается недоверенным и подвергается строгой санитизации в клиенте:
* Массив `sessions` обрезается максимум до 6 элементов (`slice(0, 6)`);
* Если массив `sessions` пуст или отсутствует, выбрасывается исключение `'Der AI-Plan enthält keine Sitzungen'`;
* Длительность каждого шага принудительно ограничивается диапазоном от 5 до 60 минут:  
  `Math.max(5, Math.min(60, Number(s.durationMinutes) || 20))`;
* Строковые поля защищаются fallback-значениями по умолчанию;
* Поля HTML-экранируются функцией `escapeHTML()` перед рендерингом в DOM для защиты от XSS.

### 2.5. Обработка ошибок и Fallback
* При отмене по таймауту (`AbortError`) выводится сообщение: *«Die Analyse hat zu lange gedauert. Bitte versuche es erneut.»*.
* При ошибках сети или `ok !== true` выводится: *«AI-Analyse nicht verfügbar: [error.message]»*.
* Пользователь возвращается на экран ввода цели `app-screen-goal`, введенный текст и прикрепленный файл сохраняются.

### 2.6. Границы валидации
* **Подтверждено клиентским кодом:**
  * Проверка наличия хотя бы одного источника данных (текст цели или файл);
  * Валидация MIME-типов (`image/jpeg`, `image/png`, `image/webp`);
  * Клиентское сжатие картинок крупнее 1.8 МБ;
  * Таймаут 120 секунд.
* **Требует аудита Cloudflare Worker (не подтверждено в клиенте):**
  * Максимальный допустимый размер тела запроса (body limit);
  * Наличие rate limiting / защиты от DDoS;
  * Модель LLM (Gemini, Claude, GPT), системный промпт и температура генерации;
  * Формат промпта при анализе изображений (multimodal OCR).

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
