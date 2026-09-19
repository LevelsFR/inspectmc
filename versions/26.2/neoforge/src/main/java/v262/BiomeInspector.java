package com.levelsfr.inspectmc.inspection;

import com.levelsfr.inspectmc.util.IdentifierCompat;
import com.levelsfr.inspectmc.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/** Minimal biome view for the 26.2 biome API, whose detailed accessors are version-specific. */
public final class BiomeInspector {
    private BiomeInspector() {
    }

    public static List<Component> inspect(ServerLevel level, BlockPos pos) {
        return view(level, pos, false);
    }

    public static List<Component> inspectDetails(ServerLevel level, BlockPos pos) {
        return view(level, pos, true);
    }

    private static List<Component> view(ServerLevel level, BlockPos pos, boolean details) {
        List<Component> lines = new ArrayList<>();
        String id = level.getBiome(pos).unwrapKey().map(IdentifierCompat::resourceKeyId).orElse("minecraft:unknown");
        lines.add(TextUtil.section("Biome"));
        lines.add(TextUtil.resourceLine("Registry ID", id, "biome"));
        lines.add(TextUtil.positionLine("Position", pos, IdentifierCompat.resourceKeyId(level.dimension())));
        if (details) {
            lines.add(TextUtil.hint("Detailed climate and generation accessors are version-specific on Minecraft 26.2."));
        }
        return lines;
    }
}
