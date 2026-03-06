# 🔌 Connection Analyzer — План разработки Fabric мода

## Цель
Мод для клиента на Fabric 1.21+, который перехватывает и логирует **всё**, что происходит во время подключения к серверу Minecraft, и сохраняет результат в читаемый файл для отправки администратору.

---

## Стек
- **Minecraft:** 1.21+
- **Loader:** Fabric
- **Mappings:** Yarn
- **Build:** Maven
- **Java:** 21
- **Доп. зависимости:** Fabric API

---

## Структура проекта

```
connection-analyzer/
├── pom.xml
├── src/main/
│   ├── java/com/connanalyzer/
│   │   ├── ConnectionAnalyzerMod.java       # точка входа (ModInitializer)
│   │   ├── ConnectionAnalyzerClient.java    # ClientModInitializer
│   │   ├── logger/
│   │   │   └── DiagnosticLogger.java        # запись всего в файл
│   │   ├── collector/
│   │   │   ├── SystemInfoCollector.java     # ОС, Java, память, железо
│   │   │   ├── NetworkInfoCollector.java    # IP, порт, пинг, DNS-резолв
│   │   │   ├── WindowsInfoCollector.java    # Windows-специфичные настройки
│   │   │   └── ModListCollector.java        # список установленных модов
│   │   └── mixin/
│   │       ├── ClientConnectionMixin.java   # перехват сетевых пакетов
│   │       ├── DisconnectScreenMixin.java   # перехват экрана дисконнекта
│   │       └── MultiplayerScreenMixin.java  # момент нажатия "Подключиться"
│   └── resources/
│       ├── fabric.mod.json
│       └── connection-analyzer.mixins.json
```

---

## Что собирает мод

### 1. Системная информация (`SystemInfoCollector`)
- ОС: название, версия, архитектура
- Java: версия, вендор, путь
- Выделенная / доступная память (heap)
- Версия Minecraft и Fabric Loader

### 2. Сетевая информация (`NetworkInfoCollector`)
- Адрес сервера (хост + порт) из конфига подключения
- DNS-резолв хоста: успех/ошибка, полученный IP
- Пинг до сервера (ICMP через `InetAddress.isReachable`)
- TCP-проверка порта (`Socket.connect`)
- Время резолва и время соединения в миллисекундах

### 3. Список модов (`ModListCollector`)
- Все установленные моды: ID, название, версия
- Сортировка по алфавиту

### 4. Перехват подключения (Mixin)
- Момент начала подключения (timestamp)
- Каждый входящий/исходящий пакет во время handshake и login-фазы:
  - Тип пакета (имя класса)
  - Направление (C→S / S→C)
  - Timestamp
- Исключения и ошибки в канале (exceptionCaught)
- Причина дисконнекта (текст с экрана DisconnectScreen)

### 5. Windows-диагностика (`WindowsInfoCollector`)
> Запускается только если `System.getProperty("os.name")` содержит `"Windows"`. На других ОС секция пропускается.

#### 5.1 Сетевые настройки Windows
| Команда | Что выявляет |
|---------|-------------|
| `netsh advfirewall firewall show rule name=all` | Все правила файрвола — ищем блокировку порта 25565 или java.exe |
| `ipconfig /all` | DNS-серверы, MAC, IP, шлюз по умолчанию, DHCP |
| `route print` | Таблица маршрутизации — неправильный шлюз = нет интернета |
| `netstat -an` | Открытые соединения и занятые порты |
| `netsh winsock show catalog` | Состояние Winsock — повреждённый стек = ничего не работает |
| `netsh interface tcp show global` | TCP-параметры (autotuning, chimney offload) |

#### 5.2 Прокси и Internet Settings
Читается из реестра через `reg query`:
```
HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings
```
- `ProxyEnable` — включён ли системный прокси
- `ProxyServer` — адрес прокси (если есть — Java может его подхватить)
- `AutoConfigURL` — PAC-файл (автоматический прокси)

#### 5.3 Хост-файл
- Полное содержимое `C:\Windows\System32\drivers\etc\hosts`
- Если сервер переопределён там — DNS-резолв вернёт неправильный IP

#### 5.4 Антивирус и защитные процессы
Проверка запущенных процессов через `tasklist /fo csv /nh`, ищем известные:
| Процесс | Продукт | Частая проблема |
|---------|---------|-----------------|
| `MsMpEng.exe` | Windows Defender | Блокирует Java-соединения |
| `avp.exe` | Kaspersky | Перехватывает TLS, ломает handshake |
| `ekrn.exe` | ESET NOD32 | Фильтрует пакеты |
| `bdagent.exe` | Bitdefender | SSL-инспекция ломает шифрование |
| `nortonsecurity.exe` | Norton | Блокирует нестандартные порты |
| `avgui.exe` | AVG | Может блокировать Java |
| `avastui.exe` | Avast | Аналогично AVG |
| `openvpn.exe`, `nordvpn.exe`, `expressvpn.exe` | VPN | Меняет маршрутизацию |

#### 5.5 Сетевые адаптеры (VPN/TAP-детект)
Через `ipconfig /all` — ищем адаптеры с именами содержащими:
`TAP`, `TUN`, `VPN`, `Loopback`, `Hamachi`, `ZeroTier`, `Radmin`
Их наличие = возможная проблема с маршрутизацией до сервера

#### 5.6 Статус Windows Defender
```
sc query WinDefend
sc query MpsSvc
```
- `WinDefend` — Defender Antivirus
- `MpsSvc` — Windows Firewall (если остановлен — может быть сторонний файрвол)

#### 5.7 Java в исключениях файрвола
```
netsh advfirewall firewall show rule name="java"
```
Если java.exe не в исключениях — Defender может блокировать исходящие соединения

### 6. Логи Minecraft
- Перехват через кастомный Log4j Appender — всё что пишется в консоль во время сессии подключения попадает в отчёт

---

## Формат выходного файла

Файл сохраняется в:
```
.minecraft/logs/connection-report-<timestamp>.txt
```

Структура файла:
```
========================================
  CONNECTION ANALYZER REPORT
  Generated: 2024-01-15 14:32:10
========================================

[SYSTEM]
OS:          Windows 11 (amd64)
Java:        21.0.2 (Eclipse Adoptium)
Memory:      512 MB allocated / 256 MB used
Minecraft:   1.21
Fabric:      0.15.11

[SERVER]
Address:     play.example.com:25565
DNS Resolve: OK → 185.123.45.67 (12ms)
Ping (ICMP): OK (34ms)
TCP Connect: OK (41ms)

[CONNECTION LOG]
14:32:10.001 | START     | Initiating connection to play.example.com:25565
14:32:10.013 | PACKET ↑  | C2SHandshakePacket
14:32:10.021 | PACKET ↓  | S2CLoginHelloPacket
14:32:10.089 | PACKET ↑  | C2SLoginKeyPacket
14:32:10.102 | ERROR     | io.netty.handler.codec.DecoderException: ...
14:32:10.103 | DISCONNECT| Disconnected: §cFailed to login: ...

[MINECRAFT LOG]
... все строки из log4j во время подключения ...

[INSTALLED MODS] (12 total)
- fabric-api        0.100.1
- lithium           0.12.1
- sodium            0.5.8
...

[WINDOWS DIAGNOSTICS]
Proxy:       DISABLED
Hosts file:  No overrides for play.example.com
Winsock:     OK
TCP Autotuning: normal

Firewall — java.exe rules:
  ALLOW  Java(TM) Platform SE  IN   TCP  Any→Any
  ALLOW  Java(TM) Platform SE  OUT  TCP  Any→Any

Active security software:
  ⚠ MsMpEng.exe   — Windows Defender (RUNNING) — возможна блокировка соединений
  ⚠ avp.exe       — Kaspersky (RUNNING) — TLS-инспекция может сломать handshake

VPN/TAP adapters detected:
  ⚠ TAP-NordVPN Windows Adapter V9 — может менять маршрутизацию

DNS servers (from ipconfig):
  Primary:   8.8.8.8
  Secondary: 8.8.4.4

Route to server (185.123.45.67):
  Gateway: 192.168.1.1 via Ethernet — OK
```

---

## Ключевые технические решения

### Mixin на `ClientConnection`
```java
@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
    @Inject(method = "channelRead0", at = @At("HEAD"))
    private void onPacketReceived(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) { ... }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"))
    private void onPacketSent(Packet<?> packet, CallbackInfo ci) { ... }

    @Inject(method = "exceptionCaught", at = @At("HEAD"))
    private void onException(ChannelHandlerContext ctx, Throwable cause, CallbackInfo ci) { ... }
}
```

### Mixin на `DisconnectScreen`
```java
@Mixin(DisconnectScreen.class)
public abstract class DisconnectScreenMixin extends Screen {
    @Inject(method = "init", at = @At("HEAD"))
    private void onDisconnect(CallbackInfo ci) {
        // захватить this.reason (Text) — причина дисконнекта
    }
}
```

### Log4j Appender
```java
@Plugin(name = "DiagnosticAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class DiagnosticAppender extends AbstractAppender {
    // перехват всех LogEvent во время активной сессии подключения
}
```

### `WindowsInfoCollector` — запуск системных команд
```java
public class WindowsInfoCollector {

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    private String runCommand(String... cmd) {
        try {
            Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(10, TimeUnit.SECONDS);
            return output.trim();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public WindowsDiagnostics collect() {
        return new WindowsDiagnostics(
            runCommand("ipconfig", "/all"),
            runCommand("netsh", "advfirewall", "firewall", "show", "rule", "name=java"),
            runCommand("netsh", "winsock", "show", "catalog"),
            runCommand("tasklist", "/fo", "csv", "/nh"),
            runCommand("reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"),
            readHostsFile()
        );
    }

    private String readHostsFile() {
        try {
            return Files.readString(Path.of("C:\\Windows\\System32\\drivers\\etc\\hosts"),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "ERROR reading hosts: " + e.getMessage();
        }
    }
}
```

**Важно:** все команды выполняются в отдельном потоке с таймаутом 10 секунд каждая, чтобы не подвешивать игру.

---

```
Игрок нажимает "Подключиться"
        ↓
MultiplayerScreenMixin → startSession()
        ↓
SystemInfoCollector.collect()
NetworkInfoCollector.collect(host, port)
WindowsInfoCollector.collect()        ← только если Windows
ModListCollector.collect()
        ↓
ClientConnectionMixin начинает логировать пакеты
Log4jAppender начинает захват логов
        ↓
Подключение успешно / ошибка / дисконнект
        ↓
DisconnectScreenMixin / ClientPlayConnectionEvents.DISCONNECT
        ↓
DiagnosticLogger.writeReport()
→ .minecraft/logs/connection-report-<timestamp>.txt
        ↓
Уведомление игроку в чате/на экране:
"📋 Отчёт сохранён: logs/connection-report-2024-01-15_14-32-10.txt"
```

---

## Этапы разработки

| # | Задача | Приоритет |
|---|--------|-----------|
| 1 | Настройка проекта (pom.xml, fabric.mod.json) | 🔴 Высокий |
| 2 | `DiagnosticLogger` — базовая запись в файл | 🔴 Высокий |
| 3 | `SystemInfoCollector` | 🟡 Средний |
| 4 | `NetworkInfoCollector` (DNS + TCP) | 🔴 Высокий |
| 5 | `ClientConnectionMixin` — перехват пакетов | 🔴 Высокий |
| 6 | `DisconnectScreenMixin` — причина дисконнекта | 🔴 Высокий |
| 7 | Log4j Appender | 🟡 Средний |
| 8 | `WindowsInfoCollector` (файрвол, прокси, антивирус, hosts) | 🔴 Высокий |
| 9 | `ModListCollector` | 🟢 Низкий |
| 10 | Уведомление игроку о сохранённом файле | 🟡 Средний |
| 11 | Тест на реальном сервере с разными ошибками | 🔴 Высокий |