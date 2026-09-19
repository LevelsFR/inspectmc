package com.levelsfr.inspectmc.locate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import com.levelsfr.inspectmc.util.MinecraftCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

public final class WorldLocator {
    private WorldLocator() {
    }

    public static Optional<BlockPos> block(ServerLevel level, BlockPos center, Block target, int radius) {
        long radiusSquared = (long) radius * radius;
        int chunkRadius = (radius + 15) / 16;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        List<SearchSection> candidates = new ArrayList<>();

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    if (!section.maybeHas(state -> state.is(target))) {
                        continue;
                    }

                    int minX = Math.max(chunkX << 4, center.getX() - radius);
                    int maxX = Math.min((chunkX << 4) + 15, center.getX() + radius);
                    int sectionY = level.getSectionYFromSectionIndex(index);
                    int minY = Math.max(SectionPos.sectionToBlockCoord(sectionY),
                            Math.max(MinecraftCompat.minBuildHeight(level), center.getY() - radius));
                    int maxY = Math.min(SectionPos.sectionToBlockCoord(sectionY) + 15,
                            Math.min(MinecraftCompat.maxBuildHeight(level) - 1, center.getY() + radius));
                    int minZ = Math.max(chunkZ << 4, center.getZ() - radius);
                    int maxZ = Math.min((chunkZ << 4) + 15, center.getZ() + radius);
                    if (minX > maxX || minY > maxY || minZ > maxZ) {
                        continue;
                    }

                    long minimumDistance = boxDistanceSquared(center, minX, maxX, minY, maxY, minZ, maxZ);
                    if (minimumDistance <= radiusSquared) {
                        candidates.add(new SearchSection(section, minX, maxX, minY, maxY, minZ, maxZ,
                                minimumDistance));
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingLong(SearchSection::minimumDistanceSquared));
        BlockPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (SearchSection candidate : candidates) {
            if (candidate.minimumDistanceSquared() >= bestDistance) {
                break;
            }
            for (int x = candidate.minX(); x <= candidate.maxX(); x++) {
                for (int y = candidate.minY(); y <= candidate.maxY(); y++) {
                    for (int z = candidate.minZ(); z <= candidate.maxZ(); z++) {
                        long distance = distanceSquared(center, x, y, z);
                        if (distance > radiusSquared || distance >= bestDistance
                                || !candidate.section().getBlockState(x & 15, y & 15, z & 15).is(target)) {
                            continue;
                        }
                        bestDistance = distance;
                        best = new BlockPos(x, y, z);
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static long boxDistanceSquared(BlockPos center, int minX, int maxX, int minY, int maxY,
                                           int minZ, int maxZ) {
        long dx = axisDistance(center.getX(), minX, maxX);
        long dy = axisDistance(center.getY(), minY, maxY);
        long dz = axisDistance(center.getZ(), minZ, maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static long axisDistance(int value, int min, int max) {
        if (value < min) {
            return (long) min - value;
        }
        if (value > max) {
            return (long) value - max;
        }
        return 0L;
    }

    private static long distanceSquared(BlockPos center, int x, int y, int z) {
        long dx = (long) x - center.getX();
        long dy = (long) y - center.getY();
        long dz = (long) z - center.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private record SearchSection(LevelChunkSection section, int minX, int maxX, int minY, int maxY,
                                 int minZ, int maxZ, long minimumDistanceSquared) {
    }

    public static Optional<Entity> entity(ServerLevel level, BlockPos center, EntityType<?> target, int radius) {
        return entities(level, center, target, radius).stream().findFirst();
    }

    public static List<Entity> entities(ServerLevel level, BlockPos center, EntityType<?> target, int radius) {
        double maxDistanceSq = radius * radius;
        return StreamSupport.stream(level.getAllEntities().spliterator(), false)
                .filter(entity -> entity.getType() == target)
                .filter(entity -> entity.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= maxDistanceSq)
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(
                        center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D)))
                .toList();
    }

    public static Optional<BlockPos> blockEntity(ServerLevel level, BlockPos center, BlockEntityType<?> target, int radius) {
        return blockEntities(level, center, target, radius).stream().findFirst();
    }

    public static List<BlockPos> blockEntities(ServerLevel level, BlockPos center, BlockEntityType<?> target, int radius) {
        int chunkRadius = Math.max(1, (radius + 15) / 16);
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        List<BlockPos> matches = new java.util.ArrayList<>();

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity.getType() != target) {
                        continue;
                    }
                    double distance = blockEntity.getBlockPos().distSqr(center);
                    if (distance <= radius * radius) {
                        matches.add(blockEntity.getBlockPos().immutable());
                    }
                }
            }
        }
        matches.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return List.copyOf(matches);
    }
}
