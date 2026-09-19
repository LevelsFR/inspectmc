package com.levelsfr.inspectmc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.levelsfr.inspectmc.InspectMC;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Server-local settings for command defaults and bounded diagnostic output. */
public final class InspectMCConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Path, InspectMCConfig> CACHE = new ConcurrentHashMap<>();

    public int operatorPermissionLevel = 2;
    public int defaultBlockDistance = 20;
    public int defaultEntityDistance = 20;
    public int defaultLocateResults = 8;
    public int searchPageSize = 30;
    public int listPageSize = 50;
    public int loadedEntityPageSize = 25;
    public int maxDumpArchives = 0;

    private InspectMCConfig() {
    }

    public static InspectMCConfig forServer(MinecraftServer server) {
        return forDirectory(server.getServerDirectory());
    }

    public static InspectMCConfig forDirectory(Path directory) {
        Path path = pathFor(directory);
        return CACHE.computeIfAbsent(path, ignored -> read(path));
    }

    public static void forget(MinecraftServer server) {
        CACHE.remove(pathFor(server.getServerDirectory()));
    }

    public void normalize() {
        operatorPermissionLevel = operatorPermissionLevel();
        defaultBlockDistance = defaultBlockDistance();
        defaultEntityDistance = defaultEntityDistance();
        defaultLocateResults = defaultLocateResults();
        searchPageSize = searchPageSize();
        listPageSize = listPageSize();
        loadedEntityPageSize = loadedEntityPageSize();
        maxDumpArchives = maxDumpArchives();
    }

    public void save(Path directory) {
        Path path = pathFor(directory);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
            CACHE.put(path, this);
        } catch (IOException | RuntimeException exception) {
            InspectMC.LOGGER.warn("Could not save InspectMC config {}", path, exception);
        }
    }

    public int operatorPermissionLevel() {
        return clamp(operatorPermissionLevel, 0, 4, 2);
    }

    public int defaultBlockDistance() {
        return clamp(defaultBlockDistance, 1, 128, 20);
    }

    public int defaultEntityDistance() {
        return clamp(defaultEntityDistance, 1, 128, 20);
    }

    public int defaultLocateResults() {
        return clamp(defaultLocateResults, 1, 20, 8);
    }

    public int searchPageSize() {
        return clamp(searchPageSize, 1, 100, 30);
    }

    public int listPageSize() {
        return clamp(listPageSize, 1, 100, 50);
    }

    public int loadedEntityPageSize() {
        return clamp(loadedEntityPageSize, 1, 100, 25);
    }

    public int maxDumpArchives() {
        return clamp(maxDumpArchives, 0, 500, 0);
    }

    private static InspectMCConfig read(Path path) {
        InspectMCConfig config = new InspectMCConfig();
        try {
            if (Files.isRegularFile(path)) {
                InspectMCConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                        InspectMCConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException exception) {
            InspectMC.LOGGER.warn("Could not read InspectMC config {}; using defaults", path, exception);
        }
        return config;
    }

    private static Path pathFor(Path directory) {
        return directory.resolve("config").resolve("inspectmc.json")
                .toAbsolutePath().normalize();
    }

    private static int clamp(int value, int minimum, int maximum, int fallback) {
        return value < minimum || value > maximum ? fallback : value;
    }
}
