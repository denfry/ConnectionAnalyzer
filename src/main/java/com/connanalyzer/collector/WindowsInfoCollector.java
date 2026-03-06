package com.connanalyzer.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Собирает специфичную для Windows диагностическую информацию.
 */
public class WindowsInfoCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(WindowsInfoCollector.class);

    /**
     * Проверяет, запущена ли игра в ОС Windows.
     *
     * @return true, если Windows
     */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    /**
     * Собирает диагностику Windows.
     *
     * @return отчет по Windows
     */
    public String collect() {
        if (!isWindows()) return "[WINDOWS DIAGNOSTICS] Skipped (Non-Windows OS)";

        LOGGER.info("[ConnAnalyzer] Running Windows diagnostics...");
        StringBuilder report = new StringBuilder();
        report.append("[WINDOWS DIAGNOSTICS]\n");

        report.append("\n--- IPConfig ---\n");
        report.append(runCommand("ipconfig", "/all"));

        report.append("\n--- Firewall Rules (Java) ---\n");
        report.append(runCommand("netsh", "advfirewall", "firewall", "show", "rule", "name=java"));

        report.append("\n--- Proxy settings (Registry) ---\n");
        report.append(runCommand("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"));

        report.append("\n--- Task list (Security processes) ---\n");
        report.append(runCommand("tasklist", "/fo", "csv", "/nh"));

        report.append("\n--- Winsock status ---\n");
        report.append(runCommand("netsh", "winsock", "show", "catalog"));

        report.append("\n--- Hosts file ---\n");
        report.append(readHostsFile());

        return report.toString();
    }

    private String runCommand(String... cmd) {
        try {
            Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
            
            // Читаем вывод с таймаутом
            byte[] outputBytes = process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            
            String output = new String(outputBytes, StandardCharsets.UTF_8);
            return finished ? output.trim() : "TIMEOUT: Command did not finish in 10s: " + String.join(" ", cmd);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String readHostsFile() {
        try {
            Path hostsPath = Path.of(System.getenv("SystemRoot"), "System32", "drivers", "etc", "hosts");
            if (Files.exists(hostsPath)) {
                return Files.readString(hostsPath, StandardCharsets.UTF_8);
            } else {
                return "Hosts file not found at: " + hostsPath.toAbsolutePath();
            }
        } catch (IOException e) {
            return "ERROR reading hosts: " + e.getMessage();
        }
    }
}
