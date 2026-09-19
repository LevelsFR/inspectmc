package com.levelsfr.inspectmc.inspection;

import com.levelsfr.inspectmc.config.InspectMCConfig;
import com.levelsfr.inspectmc.output.CommandOutput;
import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RegistrySearch {
    private RegistrySearch() {
    }

    public static List<Component> search(MinecraftServer server, String rawQuery) {
        return search(server, rawQuery, 1);
    }

    public static List<Component> search(MinecraftServer server, String rawQuery, int requestedPage) {
        String query = rawQuery.trim().toLowerCase(Locale.ROOT);
        List<Match> matches = new ArrayList<>();
        add(matches, "Item", "item", BuiltInRegistries.ITEM.keySet().stream().toList(), query);
        add(matches, "Block", "block", BuiltInRegistries.BLOCK.keySet().stream().toList(), query);
        add(matches, "Entity", "entity_type", BuiltInRegistries.ENTITY_TYPE.keySet().stream().toList(), query);
        add(matches, "Block entity", "block_entity_type", BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet().stream().toList(), query);
        add(matches, "Fluid", "fluid", BuiltInRegistries.FLUID.keySet().stream().toList(), query);
        add(matches, "Particle", "particle_type", BuiltInRegistries.PARTICLE_TYPE.keySet().stream().toList(), query);
        add(matches, "Sound", "sound_event", BuiltInRegistries.SOUND_EVENT.keySet().stream().toList(), query);
        add(matches, "Biome", "worldgen/biome",
                IdentifierCompat.registry(server.registryAccess(), Registries.BIOME).keySet().stream().toList(), query);

        matches.sort(Comparator
                .comparingInt((Match match) -> score(match.id(), query))
                .thenComparing(Match::id)
                .thenComparing(Match::kind));

        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Search"));
        lines.add(TextUtil.line("Query", rawQuery));
        lines.add(TextUtil.line("Matches", matches.size()));
        if (matches.isEmpty()) {
            lines.add(TextUtil.hint("Try a shorter name such as copper, chest or forest."));
            return lines;
        }

        int pageSize = InspectMCConfig.forServer(server).searchPageSize();
        int pages = Math.max(1, (matches.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(requestedPage, 1), pages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, matches.size());
        List<Match> visible = matches.subList(start, end);
        lines.add(TextUtil.section("Best matches"));
        visible.stream()
                .forEach(match -> lines.add(TextUtil.resourceLine(match.kind(), match.id(), match.registryName())));
        if (pages > 1) {
            lines.add(TextUtil.hint("Showing matches " + (start + 1) + "–" + end + " out of " + matches.size()
                    + ". Add a namespace or a longer fragment to refine the search."));
        }
        lines.add(TextUtil.actionRow(TextUtil.copyAction("[Copy shown IDs]",
                visible.stream().map(Match::id).distinct()
                        .collect(java.util.stream.Collectors.joining(", ")),
                "Copy the IDs shown by this search")));
        CommandOutput.addPaging(lines, page, pages,
                "/inspectmc search page " + (page - 1) + " " + rawQuery,
                "/inspectmc search page " + (page + 1) + " " + rawQuery);
        return lines;
    }

    private static void add(List<Match> matches, String kind, String registryName,
                            List<?> ids, String query) {
        ids.stream()
                .map(IdentifierCompat::value)
                .filter(id -> id.toLowerCase(Locale.ROOT).contains(query))
                .map(id -> new Match(kind, registryName, id))
                .forEach(matches::add);
    }

    private static int score(String id, String query) {
        String full = id.toLowerCase(Locale.ROOT);
        String path = IdentifierCompat.path(id).toLowerCase(Locale.ROOT);
        if (full.equals(query) || path.equals(query)) {
            return 0;
        }
        if (path.startsWith(query)) {
            return 1;
        }
        if (full.startsWith(query)) {
            return 2;
        }
        return 3;
    }

    private record Match(String kind, String registryName, String id) {
    }
}
