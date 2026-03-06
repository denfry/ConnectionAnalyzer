# GEMINI.md — Правила написания кода для Connection Analyzer

## Контекст проекта
Fabric мод для Minecraft 1.21+ на Java 21, Maven-сборка.  
Цель: диагностика подключения к серверу. Мод клиентский, устанавливается игроком.

---

## Стиль кода

### Именование
- Классы: `PascalCase` → `DiagnosticLogger`, `NetworkInfoCollector`
- Методы и поля: `camelCase` → `collectNetworkInfo()`, `serverAddress`
- Константы: `SCREAMING_SNAKE_CASE` → `MAX_TIMEOUT_MS`, `LOG_PREFIX`
- Пакеты: только нижний регистр → `com.connanalyzer.collector`
- Mixin-классы: суффикс `Mixin` → `ClientConnectionMixin`
- Никаких аббревиатур кроме общепринятых (`URL`, `DNS`, `TCP`, `IP`)

### Форматирование
- Отступы: **4 пробела** (не табы)
- Максимальная длина строки: **120 символов**
- Открывающая скобка `{` — на той же строке
- Пустая строка между методами
- Один импорт на строку, без `*`-импортов
- Порядок импортов: `java.*` → `javax.*` → сторонние библиотеки → внутренние классы проекта

---

## Архитектурные правила

### Принцип единственной ответственности
Каждый класс делает **одно дело**:
- `SystemInfoCollector` — только сбор системной информации
- `DiagnosticLogger` — только запись в файл
- `NetworkInfoCollector` — только сетевые проверки
- **Нельзя** писать логику сбора данных внутри Mixin-классов

### Mixin-классы
- Mixin содержит **только перехват** и немедленно делегирует в соответствующий коллектор/логгер
- Никакой бизнес-логики внутри `@Inject`-методов
- Пример — правильно:
```java
@Inject(method = "exceptionCaught", at = @At("HEAD"))
private void onException(ChannelHandlerContext ctx, Throwable cause, CallbackInfo ci) {
    ConnectionSession.getInstance().recordException(cause);
}
```
- Пример — неправильно:
```java
@Inject(method = "exceptionCaught", at = @At("HEAD"))
private void onException(ChannelHandlerContext ctx, Throwable cause, CallbackInfo ci) {
    // 50 строк логики прямо здесь — так нельзя
}
```

### Сессия подключения
- Вся информация о текущем подключении хранится в `ConnectionSession` (Singleton)
- `ConnectionSession` сбрасывается в начале каждой новой попытки подключения
- Доступ к сессии только через `ConnectionSession.getInstance()`

---

## Обработка ошибок

### Правила
- **Никогда** не глотать исключения молча (`catch (Exception e) {}`)
- Все исключения логировать через `DiagnosticLogger`, не через `System.out`
- Сетевые операции (DNS, TCP) — всегда `try/catch`, результат записывать как `OK` или `FAIL: <причина>`
- Mixin-методы не должны бросать исключения наружу — оборачивать в `try/catch` внутри

### Шаблон для сетевых операций
```java
public NetworkCheckResult checkTcpConnection(String host, int port) {
    long start = System.currentTimeMillis();
    try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        long elapsed = System.currentTimeMillis() - start;
        return NetworkCheckResult.success(elapsed);
    } catch (IOException e) {
        return NetworkCheckResult.failure(e.getMessage());
    }
}
```

---

## Работа с файлами

- Все отчёты сохраняются в `.minecraft/logs/`
- Имя файла: `connection-report-yyyy-MM-dd_HH-mm-ss.txt`
- Кодировка: **UTF-8** всегда явно
- Закрывать ресурсы через `try-with-resources`
- Никогда не удалять старые отчёты автоматически

---

## Потоки (Threading)

- Сбор системной информации и DNS/TCP проверки выполнять в **отдельном потоке**, не на главном (render) потоке Minecraft
- Использовать `CompletableFuture` или `Thread` с явным именем: `new Thread(task, "conn-analyzer-collector")`
- Запись в файл — синхронная, после завершения всех сборщиков
- Уведомление игрока о готовом отчёте — только на главном потоке через `MinecraftClient.getInstance().execute(...)`

---

## Логирование внутри мода

- Использовать `org.slf4j.Logger` через `LoggerFactory.getLogger(ClassName.class)`
- Не использовать `System.out.println` нигде
- Префикс в логах: `[ConnAnalyzer]`
- В `DEBUG`-уровне — детали пакетов
- В `INFO`-уровне — ключевые события (начало сессии, сохранение файла)
- В `WARN`/`ERROR` — проблемы

---

## Fabric API

- Регистрация событий — только в `onInitializeClient()`
- Использовать события Fabric API где возможно вместо Mixin:
  - `ClientPlayConnectionEvents.DISCONNECT` — вместо Mixin на дисконнект если достаточно
  - `ClientLifecycleEvents` — для инициализации
- Mixin использовать только когда событий Fabric API недостаточно (перехват пакетов, экран дисконнекта)

---

## Что запрещено

- ❌ `static` поля для хранения состояния кроме `ConnectionSession` (Singleton) и Logger
- ❌ Хардкод путей (`C:\Users\...`) — только через `FabricLoader.getInstance().getGameDir()`
- ❌ Блокирующие сетевые вызовы на главном потоке
- ❌ Логика в конструкторах кроме присваивания полей
- ❌ Magic numbers без константы (`connect(host, 5000)` → нужна константа `TIMEOUT_MS = 5000`)
- ❌ Комментарии типа `// делаем подключение` — код должен быть самодокументируемым
- ❌ Методы длиннее 40 строк — разбивать на части

---

## Документация

- Javadoc обязателен для всех `public` классов и методов
- Формат:
```java
/**
 * Проверяет TCP-соединение с сервером.
 *
 * @param host адрес сервера
 * @param port порт сервера
 * @return результат проверки с временем подключения или причиной ошибки
 */
public NetworkCheckResult checkTcpConnection(String host, int port) { ... }
```
- Внутри методов комментарии только если логика нетривиальна
- `TODO` и `FIXME` допустимы с описанием задачи

---

## Структура класса (порядок)

1. Константы (`static final`)
2. Статические поля
3. Поля экземпляра
4. Конструктор(ы)
5. Публичные методы
6. Приватные методы
7. Внутренние классы / `record`-типы

---

## Пример правильно написанного класса

```java
package com.connanalyzer.collector;

import com.connanalyzer.model.NetworkCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Выполняет сетевые диагностические проверки для указанного сервера.
 */
public class NetworkInfoCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkInfoCollector.class);
    private static final int TIMEOUT_MS = 5000;

    /**
     * Собирает полную сетевую диагностику: DNS-резолв и TCP-подключение.
     *
     * @param host адрес сервера
     * @param port порт сервера
     * @return результаты всех проверок
     */
    public NetworkDiagnostics collect(String host, int port) {
        LOGGER.info("[ConnAnalyzer] Starting network diagnostics for {}:{}", host, port);
        NetworkCheckResult dns = resolveDns(host);
        NetworkCheckResult tcp = checkTcpConnection(host, port);
        return new NetworkDiagnostics(host, port, dns, tcp);
    }

    private NetworkCheckResult resolveDns(String host) {
        long start = System.currentTimeMillis();
        try {
            InetAddress address = InetAddress.getByName(host);
            long elapsed = System.currentTimeMillis() - start;
            return NetworkCheckResult.success(address.getHostAddress(), elapsed);
        } catch (IOException e) {
            return NetworkCheckResult.failure(e.getMessage());
        }
    }

    private NetworkCheckResult checkTcpConnection(String host, int port) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            long elapsed = System.currentTimeMillis() - start;
            return NetworkCheckResult.success(elapsed);
        } catch (IOException e) {
            return NetworkCheckResult.failure(e.getMessage());
        }
    }

    public record NetworkDiagnostics(String host, int port,
                                     NetworkCheckResult dns,
                                     NetworkCheckResult tcp) {}
}
```