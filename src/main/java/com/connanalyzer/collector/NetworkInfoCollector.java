package com.connanalyzer.collector;

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
     * Собирает полную сетевую диагностику: DNS-резолв, TCP-подключение и пинг.
     *
     * @param host адрес сервера
     * @param port порт сервера
     * @return результаты всех проверок
     */
    public NetworkDiagnostics collect(String host, int port) {
        if (host == null || host.trim().isEmpty()) {
            return new NetworkDiagnostics("invalid", port, 
                NetworkCheckResult.failure("Invalid host: empty or null"),
                NetworkCheckResult.failure("Invalid host: empty or null"),
                NetworkCheckResult.failure("Invalid host: empty or null"),
                0, 0, 0);
        }
        
        if (port < 1 || port > 65535) {
            return new NetworkDiagnostics(host, port,
                NetworkCheckResult.failure("Invalid port: " + port),
                NetworkCheckResult.failure("Invalid port: " + port),
                NetworkCheckResult.failure("Invalid port: " + port),
                0, 0, 0);
        }
        
        LOGGER.info("[ConnAnalyzer] Starting network diagnostics for {}:{}", host, port);
        
        long dnsStart = System.currentTimeMillis();
        NetworkCheckResult dns = resolveDns(host);
        long dnsTime = System.currentTimeMillis() - dnsStart;
        
        long tcpStart = System.currentTimeMillis();
        NetworkCheckResult tcp = checkTcpConnection(host, port);
        long tcpTime = System.currentTimeMillis() - tcpStart;
        
        long pingStart = System.currentTimeMillis();
        NetworkCheckResult ping = checkPing(host);
        long pingTime = System.currentTimeMillis() - pingStart;

        return new NetworkDiagnostics(host, port, dns, tcp, ping, dnsTime, tcpTime, pingTime);
    }

    private NetworkCheckResult resolveDns(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return NetworkCheckResult.success(address.getHostAddress());
        } catch (IOException e) {
            return NetworkCheckResult.failure(e.getMessage());
        }
    }

    private NetworkCheckResult checkTcpConnection(String host, int port) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            long elapsed = System.currentTimeMillis() - start;
            return NetworkCheckResult.success("OK (" + elapsed + "ms)");
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            return NetworkCheckResult.failure(e.getMessage() + " (" + elapsed + "ms)");
        }
    }

    private NetworkCheckResult checkPing(String host) {
        long start = System.currentTimeMillis();
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isReachable(TIMEOUT_MS)) {
                long elapsed = System.currentTimeMillis() - start;
                return NetworkCheckResult.success("OK (" + elapsed + "ms)");
            } else {
                long elapsed = System.currentTimeMillis() - start;
                return NetworkCheckResult.failure("Unreachable (ICMP timeout) (" + elapsed + "ms)");
            }
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            return NetworkCheckResult.failure(e.getMessage() + " (" + elapsed + "ms)");
        }
    }

    public record NetworkCheckResult(boolean success, String result, String error) {
        public static NetworkCheckResult success(String result) {
            return new NetworkCheckResult(true, result, null);
        }
        public static NetworkCheckResult failure(String error) {
            return new NetworkCheckResult(false, null, error);
        }
        
        @Override
        public String toString() {
            return success ? "OK → " + result : "FAIL: " + error;
        }
    }

    public record NetworkDiagnostics(String host, int port,
                                     NetworkCheckResult dns,
                                     NetworkCheckResult tcp,
                                     NetworkCheckResult ping,
                                     long dnsMs, long tcpMs, long pingMs) {
        @Override
        public String toString() {
            return String.format(
                "[SERVER]\n" +
                "Address:     %s:%d\n" +
                "DNS Resolve: %s (%dms)\n" +
                "Ping (ICMP): %s (%dms)\n" +
                "TCP Connect: %s (%dms)\n",
                host, port, dns, dnsMs, ping, pingMs, tcp, tcpMs);
        }
    }
}
