package com.levelsfr.inspectmc.output;

import com.levelsfr.inspectmc.client.DumpOpenUtil;
import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.ChatCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CommandOutput {
    private CommandOutput() {
    }

    public static int send(CommandSourceStack source, String title, List<Component> lines) {
        source.sendSuccess(() -> TextUtil.header(title), false);
        for (Component line : lines) {
            source.sendSuccess(() -> line, false);
        }
        return lines.size();
    }

    public static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("[InspectMC] " + message));
        return 0;
    }

    public static int failWithActions(CommandSourceStack source, String message, Component actions) {
        source.sendFailure(Component.literal("[InspectMC] " + message));
        source.sendSuccess(() -> actions, false);
        return 0;
    }

    public static void addPaging(List<Component> lines, int page, int pages,
                                 String previousCommand, String nextCommand) {
        if (pages <= 1) {
            return;
        }
        lines.add(TextUtil.line("Page", page + " / " + pages));
        List<Component> actions = new ArrayList<>();
        if (page > 1) {
            actions.add(TextUtil.suggestAction("[Previous]", previousCommand,
                    "Show the previous page"));
        }
        if (page < pages) {
            actions.add(TextUtil.suggestAction("[Next]", nextCommand,
                    "Show the next page"));
        }
        lines.add(TextUtil.actionRow(actions.toArray(Component[]::new)));
    }

    public static List<Component> dumpResult(Path path, Path latestPath, boolean dedicatedServer) {
        Path absolute = path.toAbsolutePath().normalize();
        String fullPath = absolute.toString();
        Path parent = absolute.getParent() == null ? absolute : absolute.getParent();
        String directory = parent.toString();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Archive: ").withStyle(ChatFormatting.GRAY)
                .append(copyable(absolute.getFileName().toString(), fullPath, "Copy the absolute dump path")));
        if (latestPath != null) {
            Path latest = latestPath.toAbsolutePath().normalize();
            lines.add(Component.literal("Stable alias: ").withStyle(ChatFormatting.GRAY)
                    .append(copyable(latest.getFileName().toString(), latest.toString(),
                            "Copy the stable latest path for scripts and bots")));
        }
        lines.add(Component.literal(dedicatedServer ? "Server location: " : "Location: ").withStyle(ChatFormatting.GRAY)
                .append(copyable(fullPath, fullPath, dedicatedServer
                        ? "Copy this path on the server filesystem"
                        : "Copy this local path")));
        lines.add(Component.literal("  ")
                .append(copyable(dedicatedServer ? "[Copy server path]" : "[Copy archive]", fullPath,
                        dedicatedServer ? "Copy the server-side absolute path" : "Copy the absolute path"))
                .append(latestPath == null ? Component.empty() : Component.literal(" ").append(copyable(
                        dedicatedServer ? "[Copy server latest]" : "[Copy latest]", latestPath.toString(),
                        "Copy the stable latest path")))
                .append(Component.literal(" "))
                .append(copyable(dedicatedServer ? "[Copy server folder]" : "[Copy folder]", directory,
                        dedicatedServer ? "Copy the server-side dump folder" : "Copy the local dump folder")));
        if (dedicatedServer) {
            lines.add(Component.literal("This file is on the server; client-side open actions are intentionally unavailable.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.literal("  ")
                    .append(openable("[Open archive]", DumpOpenUtil.command("file", absolute),
                            "Open this dump with the default desktop application"))
                    .append(latestPath == null ? Component.empty() : Component.literal(" ").append(openable(
                            "[Open latest]", DumpOpenUtil.command("file", latestPath),
                            "Open the stable latest export")))
                    .append(Component.literal(" "))
                    .append(openable("[Open folder]", DumpOpenUtil.command("folder", parent),
                            "Open the local InspectMC dump folder")));
        }
        return lines;
    }

    private static Component copyable(String label, String value, String hoverText) {
        return Component.literal(label).withStyle(style -> style
                .withColor(label.startsWith("[") ? ChatFormatting.GRAY : ChatFormatting.WHITE)
                .withClickEvent(ChatCompat.clickCopy(value))
                .withHoverEvent(ChatCompat.hoverText(hoverText)));
    }

    private static Component openable(String label, String command, String hoverText) {
        return Component.literal(label).withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(ChatCompat.clickRun(command))
                .withHoverEvent(ChatCompat.hoverText(hoverText)));
    }
}
