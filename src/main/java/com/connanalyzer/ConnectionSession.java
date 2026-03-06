package com.connanalyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Хранит информацию о текущей сессии подключения.
 * Все данные для отчета аккумулируются здесь.
 */
public class ConnectionSession {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionSession.class);
    private static ConnectionSession instance;

    private String serverAddress;
    private int serverPort;
    private final List<String> connectionLogs = new ArrayList<>();
    private String disconnectReason;
    private boolean active = false;

    private String systemInfo;
    private String networkInfo;
    private String modList;
    private String windowsInfo;

    private ConnectionSession() {}

    /**
     * Возвращает единственный экземпляр сессии.
     *
     * @return экземпляр сессии
     */
    public static synchronized ConnectionSession getInstance() {
        if (instance == null) {
            instance = new ConnectionSession();
        }
        return instance;
    }

    /**
     * Сбрасывает сессию для нового подключения.
     *
     * @param host адрес сервера
     * @param port порт сервера
     */
    public void startNewSession(String host, int port) {
        LOGGER.info("[ConnAnalyzer] Starting new session for {}:{}", host, port);
        this.serverAddress = host;
        this.serverPort = port;
        this.connectionLogs.clear();
        this.disconnectReason = null;
        this.active = true;
        
        this.systemInfo = null;
        this.networkInfo = null;
        this.modList = null;
        this.windowsInfo = null;
        
        addLog("START", "Initiating connection to " + host + ":" + port);
    }

    public void setSystemInfo(String info) { this.systemInfo = info; }
    public void setNetworkInfo(String info) { this.networkInfo = info; }
    public void setModList(String info) { this.modList = info; }
    public void setWindowsInfo(String info) { this.windowsInfo = info; }

    public String getSystemInfo() { return systemInfo; }
    public String getNetworkInfo() { return networkInfo; }
    public String getModList() { return modList; }
    public String getWindowsInfo() { return windowsInfo; }

    /**
     * Завершает текущую сессию.
     */
    public void endSession() {
        if (active) {
            LOGGER.info("[ConnAnalyzer] Ending session for {}:{}", serverAddress, serverPort);
            this.active = false;
        }
    }

    /**
     * Добавляет запись в лог подключения.
     *
     * @param type тип события (PACKET, ERROR, и т.д.)
     * @param message сообщение
     */
    public void addLog(String type, String message) {
        if (!active || type == null || message == null) return;
        String logEntry = String.format("%s | %-10s | %s", 
            new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()),
            type, message);
        synchronized (connectionLogs) {
            connectionLogs.add(logEntry);
        }
    }

    public void recordException(Throwable cause) {
        addLog("ERROR", cause.toString());
    }

    public void setDisconnectReason(String reason) {
        this.disconnectReason = reason;
        addLog("DISCONNECT", reason);
    }

    public String getServerAddress() { return serverAddress; }
    public int getServerPort() { return serverPort; }
    public List<String> getConnectionLogs() { 
        synchronized (connectionLogs) {
            return new ArrayList<>(connectionLogs);
        }
    }
    public String getDisconnectReason() { return disconnectReason; }
    public boolean isActive() { return active; }
}
