package com.connanalyzer;

import com.connanalyzer.logger.DiagnosticAppender;
import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Главная точка входа мода Connection Analyzer.
 */
public class ConnectionAnalyzerMod implements ModInitializer {

    public static final String MOD_ID = "connection-analyzer";
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionAnalyzerMod.class);

    @Override
    public void onInitialize() {
        LOGGER.info("[ConnAnalyzer] Initializing Connection Analyzer...");
        registerLogAppender();
    }

    private void registerLogAppender() {
        try {
            final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            final Configuration config = ctx.getConfiguration();

            DiagnosticAppender appender = DiagnosticAppender.createAppender("DiagnosticAppender", null, null);
            appender.start();
            config.addAppender(appender);

            for (LoggerConfig loggerConfig : config.getLoggers().values()) {
                loggerConfig.addAppender(appender, Level.ALL, null);
            }
            config.getRootLogger().addAppender(appender, Level.ALL, null);

            ctx.updateLoggers();
            LOGGER.info("[ConnAnalyzer] Log4j Appender registered successfully.");
        } catch (Exception e) {
            LOGGER.error("[ConnAnalyzer] Failed to register Log4j Appender: {}", e.getMessage());
        }
    }
}
