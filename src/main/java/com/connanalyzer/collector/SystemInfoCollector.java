package com.connanalyzer.collector;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Собирает информацию об ОС, Java, памяти и версиях игры/лоадера.
 */
public class SystemInfoCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemInfoCollector.class);

    /**
     * Собирает текущую системную информацию.
     *
     * @return структурированный отчет о системе
     */
    public SystemInfo collect() {
        LOGGER.info("[ConnAnalyzer] Collecting system info...");
        
        String os = String.format("%s (%s, %s)", 
            System.getProperty("os.name"), 
            System.getProperty("os.version"), 
            System.getProperty("os.arch"));
        
        String java = String.format("%s (%s)", 
            System.getProperty("java.version"), 
            System.getProperty("java.vendor"));
        
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long allocatedMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = allocatedMemory - freeMemory;
        
        String memory = String.format("%d MB allocated / %d MB used (Max: %d MB)", 
            allocatedMemory, usedMemory, maxMemory);
        
        String mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
            .map(m -> m.getMetadata().getVersion().getFriendlyString())
            .orElse("Unknown");
            
        String fabricVersion = FabricLoader.getInstance().getModContainer("fabricloader")
            .map(m -> m.getMetadata().getVersion().getFriendlyString())
            .orElse("Unknown");

        return new SystemInfo(os, java, memory, mcVersion, fabricVersion);
    }

    public record SystemInfo(String os, String java, String memory, 
                             String minecraft, String fabric) {
        @Override
        public String toString() {
            return String.format(
                "[SYSTEM]\n" +
                "OS:          %s\n" +
                "Java:        %s\n" +
                "Memory:      %s\n" +
                "Minecraft:   %s\n" +
                "Fabric:      %s\n",
                os, java, memory, minecraft, fabric);
        }
    }
}
