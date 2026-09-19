package com.levelsfr.inspectmc.output;

import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.MinecraftCompat;
import com.levelsfr.inspectmc.util.ChatCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compact, loader-neutral chunk map for chat output. */
public final class ChunkMapRenderer {
    private static final int MAX_MAP_SIZE = 13;

    private ChunkMapRenderer() {
    }

    public static List<Component> entities(BlockPos center, List<? extends Entity> entities, int radius,
                                           String dimension) {
        List<Marker> markers = entities.stream()
                .map(entity -> new Marker(entity.blockPosition(), entity.getName().getString()))
                .toList();
        return render(center, markers, Math.max(1, (radius + 15) / 16), dimension, "matching entity");
    }

    public static List<Component> blockEntities(BlockPos center, List<BlockPos> positions, int radius,
                                                String dimension) {
        List<Marker> markers = positions.stream().map(pos -> new Marker(pos, "block entity")).toList();
        return render(center, markers, Math.max(1, (radius + 15) / 16), dimension, "matching block entity");
    }

    public static List<Component> loadedChunks(BlockPos center, List<ChunkPos> chunks, int radiusChunks,
                                               String dimension) {
        List<Marker> markers = chunks.stream()
                .filter(chunk -> Math.abs(MinecraftCompat.chunkX(chunk) - (center.getX() >> 4)) <= radiusChunks
                        && Math.abs(MinecraftCompat.chunkZ(chunk) - (center.getZ() >> 4)) <= radiusChunks)
                .map(chunk -> new Marker(
                        new BlockPos(chunk.getMinBlockX() + 8, center.getY(), chunk.getMinBlockZ() + 8),
                        "loaded chunk " + MinecraftCompat.chunkX(chunk) + ", " + MinecraftCompat.chunkZ(chunk)))
                .toList();
        return render(center, markers, radiusChunks, dimension, "loaded chunk");
    }

    private static List<Component> render(BlockPos center, List<Marker> markers, int chunkRadius,
                                          String dimension, String markerName) {
        int requestedDiameter = chunkRadius * 2 + 1;
        int mapSize = Math.min(MAX_MAP_SIZE, Math.max(5, requestedDiameter));
        if ((mapSize & 1) == 0) {
            mapSize--;
        }
        int chunksPerCell = Math.max(1, (int) Math.ceil(requestedDiameter / (double) mapSize));
        int half = mapSize / 2;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;

        Map<Long, List<Marker>> cells = new HashMap<>();
        for (Marker marker : markers) {
            int dx = (marker.pos().getX() >> 4) - centerChunkX;
            int dz = (marker.pos().getZ() >> 4) - centerChunkZ;
            if (Math.abs(dx) > chunkRadius || Math.abs(dz) > chunkRadius) {
                continue;
            }
            int cellX = half + (int) Math.round(dx / (double) chunksPerCell);
            int cellZ = half + (int) Math.round(dz / (double) chunksPerCell);
            if (cellX >= 0 && cellX < mapSize && cellZ >= 0 && cellZ < mapSize) {
                cells.computeIfAbsent(cellKey(cellX, cellZ), ignored -> new ArrayList<>()).add(marker);
            }
        }

        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Chunk map"));
        lines.add(TextUtil.hint("North (-Z) ↑  P = you  1-9 = matches  + = 10+"));
        for (int z = 0; z < mapSize; z++) {
            MutableComponent row = Component.literal("  ");
            for (int x = 0; x < mapSize; x++) {
                List<Marker> cellMarkers = cells.getOrDefault(cellKey(x, z), List.of());
                boolean playerCell = x == half && z == half;
                row.append(cell(playerCell, cellMarkers, dimension, markerName, centerChunkX, centerChunkZ,
                        x - half, z - half, chunksPerCell));
                if (x + 1 < mapSize) {
                    row.append(Component.literal(" "));
                }
            }
            lines.add(row);
        }
        lines.add(TextUtil.hint("Scale: 1 cell = " + chunksPerCell + "×" + chunksPerCell
                + " chunk(s); markers are grouped to keep chat compact."));
        return lines;
    }

    private static Component cell(boolean playerCell, List<Marker> markers, String dimension,
                                  String markerName, int centerChunkX, int centerChunkZ,
                                  int relativeCellX, int relativeCellZ, int chunksPerCell) {
        if (markers.isEmpty() && !playerCell) {
            return Component.literal("·").withStyle(ChatFormatting.DARK_GRAY);
        }

        String symbol;
        ChatFormatting color;
        if (playerCell) {
            symbol = "P";
            color = markers.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE;
        } else if (markers.size() >= 10) {
            symbol = "+";
            color = ChatFormatting.GOLD;
        } else {
            symbol = Integer.toString(markers.size());
            color = markers.size() == 1 ? ChatFormatting.YELLOW : ChatFormatting.GOLD;
        }

        int approximateChunkX = centerChunkX + relativeCellX * chunksPerCell;
        int approximateChunkZ = centerChunkZ + relativeCellZ * chunksPerCell;
        StringBuilder hover = new StringBuilder();
        if (playerCell) {
            hover.append("Your chunk: ").append(centerChunkX).append(", ").append(centerChunkZ);
        } else {
            hover.append("Chunk area around: ").append(approximateChunkX).append(", ").append(approximateChunkZ);
        }
        if (!markers.isEmpty()) {
            hover.append("\n").append(markers.size()).append(" ").append(markerName)
                    .append(markers.size() == 1 ? "" : "s");
            markers.stream().limit(3).forEach(marker -> hover.append("\n• ")
                    .append(marker.label()).append(" @ ").append(marker.pos().toShortString()));
            if (markers.size() > 3) {
                hover.append("\n• … and ").append(markers.size() - 3).append(" more");
            }
            hover.append("\n\nClick to teleport to the nearest marker");
        }

        MutableComponent result = Component.literal(symbol).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                    .withHoverEvent(ChatCompat.hoverText(hover.toString())));
        if (!markers.isEmpty()) {
            BlockPos target = markers.getFirst().pos();
            String command = String.format(Locale.ROOT, "/execute in %s run tp @s %d %d %d",
                    dimension, target.getX(), target.getY(), target.getZ());
            result.withStyle(style -> style.withClickEvent(ChatCompat.clickSuggest(command)));
        }
        return result;
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private record Marker(BlockPos pos, String label) {
    }
}
