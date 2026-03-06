package com.connanalyzer.logger;

import com.connanalyzer.ConnectionSession;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Отвечает за запись результатов диагностики в файл.
 */
public class DiagnosticLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticLogger.class);
    private static final String LOG_PREFIX = "[ConnAnalyzer]";

    /**
     * Создает и сохраняет отчет на основе текущей сессии.
     */
    public static void writeReport() {
        ConnectionSession session = ConnectionSession.getInstance();
        if (!session.isActive() && session.getConnectionLogs().isEmpty()) {
            LOGGER.warn("{} No active session or logs to write.", LOG_PREFIX);
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String fileName = "connection-report-" + timestamp + ".txt";
        Path logsDir = FabricLoader.getInstance().getGameDir().resolve("logs");
        Path reportFile = logsDir.resolve(fileName);

        try {
            if (!Files.exists(logsDir)) {
                Files.createDirectories(logsDir);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8)) {
                writeHeader(writer, timestamp);
                writeServerSection(writer, session);
                
                if (session.getSystemInfo() != null) {
                    writer.write(session.getSystemInfo()); writer.newLine();
                }
                
                if (session.getNetworkInfo() != null) {
                    writer.write(session.getNetworkInfo()); writer.newLine();
                }

                writeConnectionLogSection(writer, session);
                
                if (session.getModList() != null) {
                    writer.write(session.getModList()); writer.newLine();
                }
                
                if (session.getWindowsInfo() != null) {
                    writer.write(session.getWindowsInfo()); writer.newLine();
                }
            }

            LOGGER.info("{} Report saved: {}", LOG_PREFIX, reportFile.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("{} Failed to write diagnostic report: {}", LOG_PREFIX, e.getMessage());
        }
    }

    private static void writeHeader(BufferedWriter writer, String timestamp) throws IOException {
        writer.write("========================================"); writer.newLine();
        writer.write("  CONNECTION ANALYZER REPORT"); writer.newLine();
        writer.write("  Generated: " + timestamp); writer.newLine();
        writer.write("========================================"); writer.newLine();
        writer.newLine();
    }

    private static void writeServerSection(BufferedWriter writer, ConnectionSession session) throws IOException {
        writer.write("[SERVER]"); writer.newLine();
        writer.write("Address:     " + session.getServerAddress() + ":" + session.getServerPort()); writer.newLine();
        writer.newLine();
    }

    private static void writeConnectionLogSection(BufferedWriter writer, ConnectionSession session) throws IOException {
        writer.write("[CONNECTION LOG]"); writer.newLine();
        for (String log : session.getConnectionLogs()) {
            writer.write(log); writer.newLine();
        }
        writer.newLine();
    }
}
