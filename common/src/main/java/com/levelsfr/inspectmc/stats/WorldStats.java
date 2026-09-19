package com.levelsfr.inspectmc.stats;

import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import com.levelsfr.inspectmc.util.MinecraftCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldStats {
    private WorldStats() {
    }

    public static List<Component> blocks(ServerLevel level, BlockPos center, int radius) {
        Map<String, Integer> counts = new HashMap<>();
        long sampled = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                for (int y = Math.max(MinecraftCompat.minBuildHeight(level), center.getY() - radius); y <= Math.min(MinecraftCompat.maxBuildHeight(level) - 1, center.getY() + radius); y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    cursor.set(x, y, z);
                    if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                        continue;
                    }
                    String id = IdentifierCompat.value(BuiltInRegistries.BLOCK.getKey(level.getBlockState(cursor).getBlock()));
                    counts.merge(id, 1, Integer::sum);
                    sampled++;
                }
            }
        }

        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Sample"));
        lines.add(TextUtil.line("Radius", radius));
        lines.add(TextUtil.line("Sampled blocks", sampled));
        lines.add(TextUtil.section("Most frequent blocks"));
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(30)
                .forEach(entry -> lines.add(TextUtil.resourceLine("  " + entry.getValue() + "×", entry.getKey(), "block")));
        if (counts.size() > 30) {
            lines.add(TextUtil.hint("Showing the 30 most frequent blocks out of " + counts.size() + "."));
        }
        return lines;
    }

    public static List<Component> biomes(ServerLevel level, BlockPos center, int radius, int step) {
        Map<String, Integer> counts = new HashMap<>();
        int sampled = 0;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x += step) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += step) {
                BlockPos pos = new BlockPos(x, center.getY(), z);
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                Holder<Biome> biome = level.getBiome(pos);
                String id = biome.unwrapKey().map(IdentifierCompat::resourceKeyId).orElse("<direct>");
                counts.merge(id, 1, Integer::sum);
                sampled++;
            }
        }

        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Sample"));
        lines.add(TextUtil.line("Radius", radius));
        lines.add(TextUtil.line("Sample step", step));
        lines.add(TextUtil.line("Biome samples", sampled));
        lines.add(TextUtil.section("Observed biomes"));
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    String id = entry.getKey();
                    if (!id.contains(":")) {
                        lines.add(TextUtil.line("  Biome", entry.getKey() + " — " + entry.getValue() + "×"));
                    } else {
                        lines.add(TextUtil.resourceLine("  " + entry.getValue() + "×", id, "worldgen/biome"));
                    }
                });
        return lines;
    }
}
