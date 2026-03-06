package com.connanalyzer.logger;

import com.connanalyzer.ConnectionSession;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;

/**
 * Перехватывает все логи Log4j во время активной сессии подключения.
 */
@Plugin(name = "DiagnosticAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class DiagnosticAppender extends AbstractAppender {

    protected DiagnosticAppender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout, true, null);
    }

    @Override
    public void append(LogEvent event) {
        try {
            ConnectionSession session = ConnectionSession.getInstance();
            if (session.isActive()) {
                String message = event.getMessage().getFormattedMessage();
                String level = event.getLevel().toString();
                session.addLog("LOG-" + level, message);
            }
        } catch (Exception e) {
            // Silently ignore errors in appender to prevent logging loops
        }
    }

    @PluginFactory
    public static DiagnosticAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<? extends Serializable> layout) {
        return new DiagnosticAppender(name, filter, layout);
    }
}
