package com.connanalyzer.collector;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Собирает список установленных модов.
 */
public class ModListCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModListCollector.class);

    /**
     * Собирает список установленных модов, отсортированный по алфавиту.
     *
     * @return отчет по модам
     */
    public String collect() {
        LOGGER.info("[ConnAnalyzer] Collecting mod list...");
        
        List<ModContainer> mods = FabricLoader.getInstance().getAllMods().stream()
            .sorted(Comparator.comparing(m -> m.getMetadata().getId()))
            .collect(Collectors.toList());

        StringBuilder report = new StringBuilder();
        report.append("[INSTALLED MODS] (").append(mods.size()).append(" total)\n");

        for (ModContainer mod : mods) {
            ModMetadata meta = mod.getMetadata();
            report.append("- ").append(String.format("%-20s %s\n", 
                meta.getId(), 
                meta.getVersion().getFriendlyString()));
        }

        return report.toString();
    }
}
