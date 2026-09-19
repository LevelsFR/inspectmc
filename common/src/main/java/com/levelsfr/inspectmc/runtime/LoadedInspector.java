package com.levelsfr.inspectmc.runtime;

import com.levelsfr.inspectmc.config.InspectMCConfig;
import com.levelsfr.inspectmc.output.ChunkMapRenderer;
import com.levelsfr.inspectmc.output.CommandOutput;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.MinecraftCompat;
import net.minecraft.core.BlockPos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LoadedInspector {
    private LoadedInspector() {
    }

    public static List<Component> entities(ServerLevel level) {
        return entities(level, 1);
    }

    public static List<Component> dimensions(MinecraftServer server) {
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Loaded dimensions"));
        int totalEntities = 0;
        int dimensionCount = 0;
        for (ServerLevel level : server.getAllLevels()) {
            dimensionCount++;
            int entities = 0;
            for (Entity ignored : level.getAllEntities()) {
                entities++;
            }
            totalEntities += entities;
            lines.add(TextUtil.resourceLine("Dimension", IdentifierCompat.resourceKeyId(level.dimension()), "dimension"));
            lines.add(TextUtil.line("  Players", level.players().size()));
            lines.add(TextUtil.line("  Loaded entities", entities));
            lines.add(TextUtil.line("  Forced chunks", MinecraftCompat.forcedChunks(level).size()));
        }
        lines.add(TextUtil.line("Dimensions", dimensionCount));
        lines.add(TextUtil.line("Loaded entities", totalEntities));
        return lines;
    }

    public static List<Component> entities(ServerLevel level, int requestedPage) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (Entity entity : level.getAllEntities()) {
            total++;
            String id = IdentifierCompat.value(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
            counts.merge(id, 1, Integer::sum);
        }

        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Summary"));
        lines.add(TextUtil.resourceLine("Dimension", IdentifierCompat.resourceKeyId(level.dimension()), "dimension"));
        lines.add(TextUtil.line("Loaded entities", total));
        lines.add(TextUtil.section("Most loaded types"));
        List<Map.Entry<String, Integer>> sorted = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();
        int pageSize = InspectMCConfig.forServer(level.getServer()).loadedEntityPageSize();
        int pages = Math.max(1, (sorted.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(requestedPage, 1), pages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, sorted.size());
        sorted.subList(start, end).stream()
                .forEach(entry -> lines.add(TextUtil.resourceLine("  " + entry.getValue() + "×", entry.getKey(), "entity_type")));
        if (pages > 1) {
            lines.add(TextUtil.hint("Showing entity types " + (start + 1) + "–" + end + " out of " + counts.size() + "."));
        }
        CommandOutput.addPaging(lines, page, pages,
                "/inspectmc loaded entities " + (page - 1),
                "/inspectmc loaded entities " + (page + 1));
        return lines;
    }

    public static LoadedChunkSnapshot chunks(ServerLevel level) {
        LongOpenHashSet positions = new LongOpenHashSet();
        int viewDistance = level.getServer().getPlayerList().getViewDistance();

        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();
            for (int x = MinecraftCompat.chunkX(center) - viewDistance; x <= MinecraftCompat.chunkX(center) + viewDistance; x++) {
                for (int z = MinecraftCompat.chunkZ(center) - viewDistance; z <= MinecraftCompat.chunkZ(center) + viewDistance; z++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk != null) {
                        positions.add(MinecraftCompat.chunkLong(x, z));
                    }
                }
            }
        }

        for (long chunkLong : MinecraftCompat.forcedChunks(level)) {
            ChunkPos pos = MinecraftCompat.chunkFromLong(chunkLong);
            if (level.getChunkSource().getChunkNow(MinecraftCompat.chunkX(pos), MinecraftCompat.chunkZ(pos)) != null) {
                positions.add(chunkLong);
            }
        }

        int blockEntities = 0;
        for (long packed : positions) {
            ChunkPos pos = MinecraftCompat.chunkFromLong(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(MinecraftCompat.chunkX(pos), MinecraftCompat.chunkZ(pos));
            if (chunk != null) {
                blockEntities += chunk.getBlockEntities().size();
            }
        }
        List<ChunkPos> chunkPositions = new ArrayList<>();
        for (long packed : positions) {
            chunkPositions.add(MinecraftCompat.chunkFromLong(packed));
        }
        chunkPositions.sort(java.util.Comparator.comparingInt(MinecraftCompat::chunkZ).thenComparingInt(MinecraftCompat::chunkX));
        return new LoadedChunkSnapshot(positions.size(), blockEntities, List.copyOf(chunkPositions));
    }

    public record LoadedChunkSnapshot(int chunks, int blockEntities, List<ChunkPos> positions) {
        public List<Component> toLines(ServerLevel level, BlockPos center, int mapRadiusChunks) {
            List<Component> lines = new ArrayList<>();
            lines.add(TextUtil.section("Summary"));
            lines.add(TextUtil.resourceLine("Dimension", IdentifierCompat.resourceKeyId(level.dimension()), "dimension"));
            lines.add(TextUtil.line("Observed loaded chunks", chunks));
            lines.add(TextUtil.line("Block entities in observed chunks", blockEntities));
            lines.add(TextUtil.line("Map radius", mapRadiusChunks + " chunks"));
            lines.add(TextUtil.hint("Includes player view-distance chunks and forced chunks without loading new ones."));
            lines.addAll(ChunkMapRenderer.loadedChunks(center, positions, mapRadiusChunks, IdentifierCompat.resourceKeyId(level.dimension())));
            return lines;
        }
    }
}
