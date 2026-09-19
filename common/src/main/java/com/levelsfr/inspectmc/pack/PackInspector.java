package com.levelsfr.inspectmc.pack;

import com.levelsfr.inspectmc.InspectMC;
import com.levelsfr.inspectmc.config.InspectMCConfig;
import com.levelsfr.inspectmc.output.CommandOutput;
import com.levelsfr.inspectmc.platform.ModDescriptor;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import com.levelsfr.inspectmc.util.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class PackInspector {
    private PackInspector() {
    }

    public static List<Component> mods(MinecraftServer server) {
        return mods(server, 1);
    }

    public static List<Component> mods(MinecraftServer server, int requestedPage) {
        List<Component> lines = new ArrayList<>();
        List<ModDescriptor> mods = InspectMC.platform().loadedMods().stream()
                .sorted((a, b) -> a.id().compareToIgnoreCase(b.id()))
                .toList();
        int pageSize = InspectMCConfig.forServer(server).listPageSize();
        int pages = Math.max(1, (mods.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(requestedPage, 1), pages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, mods.size());
        lines.add(TextUtil.section("Environment"));
        lines.add(TextUtil.line("Loader", InspectMC.platform().loaderName()));
        lines.add(TextUtil.line("Loaded mods", mods.size()));
        lines.add(TextUtil.section("Mods"));
        mods.subList(start, end)
                .stream()
                .forEach(mod -> lines.add(TextUtil.line("  Mod", mod.id() + " | " + mod.version() + " | " + mod.name())));
        if (pages > 1) {
            lines.add(TextUtil.hint("Showing mods " + (start + 1) + "–" + end + " out of " + mods.size() + "."));
        }
        lines.add(TextUtil.actionRow(TextUtil.copyAction("[Copy all mod IDs]",
                mods.stream().map(ModDescriptor::id).sorted().collect(java.util.stream.Collectors.joining(", ")),
                "Copy every loaded mod ID")));
        CommandOutput.addPaging(lines, page, pages,
                "/inspectmc pack mods " + (page - 1),
                "/inspectmc pack mods " + (page + 1));
        return lines;
    }

    public static List<Component> datapacks(CommandSourceStack source) {
        return datapacks(source, 1);
    }

    public static List<Component> datapacks(CommandSourceStack source, int requestedPage) {
        List<Component> lines = new ArrayList<>();
        List<String> selected = new ArrayList<>(source.getServer().getPackRepository().getSelectedIds());
        selected.sort(String::compareToIgnoreCase);
        int pageSize = InspectMCConfig.forServer(source.getServer()).listPageSize();
        int pages = Math.max(1, (selected.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(requestedPage, 1), pages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, selected.size());
        lines.add(TextUtil.section("Active data packs"));
        lines.add(TextUtil.line("Selected data packs", selected.size()));
        selected.subList(start, end).forEach(id -> lines.add(TextUtil.line("  Pack", id)));
        if (pages > 1) {
            lines.add(TextUtil.hint("Showing data packs " + (start + 1) + "–" + end + " out of " + selected.size() + "."));
        }
        lines.add(TextUtil.actionRow(TextUtil.copyAction("[Copy all pack IDs]", String.join(", ", selected),
                "Copy every selected data pack ID")));
        CommandOutput.addPaging(lines, page, pages,
                "/inspectmc pack datapacks " + (page - 1),
                "/inspectmc pack datapacks " + (page + 1));
        return lines;
    }

    public static List<Component> namespaces(CommandSourceStack source) {
        return namespaces(source, 1);
    }

    public static List<Component> namespaces(CommandSourceStack source, int requestedPage) {
        Set<String> namespaces = new TreeSet<>();
        source.getServer().registryAccess().registries().forEach(entry ->
                entry.value().keySet().forEach(id -> namespaces.add(IdentifierCompat.namespace(IdentifierCompat.value(id)))));
        List<String> sorted = new ArrayList<>(namespaces);
        int pageSize = InspectMCConfig.forServer(source.getServer()).listPageSize();
        int pages = Math.max(1, (sorted.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(requestedPage, 1), pages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, sorted.size());
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Runtime namespaces"));
        lines.add(TextUtil.line("Runtime namespaces", namespaces.size()));
        sorted.subList(start, end).forEach(namespace -> lines.add(TextUtil.line("  Namespace", namespace)));
        if (pages > 1) {
            lines.add(TextUtil.hint("Showing namespaces " + (start + 1) + "–" + end + " out of " + namespaces.size() + "."));
        }
        lines.add(TextUtil.actionRow(TextUtil.copyAction("[Copy all namespaces]", String.join(", ", namespaces),
                "Copy every runtime namespace")));
        CommandOutput.addPaging(lines, page, pages,
                "/inspectmc pack namespaces " + (page - 1),
                "/inspectmc pack namespaces " + (page + 1));
        return lines;
    }
}
