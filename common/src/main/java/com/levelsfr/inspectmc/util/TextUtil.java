package com.levelsfr.inspectmc.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import com.levelsfr.inspectmc.util.ChatCompat;
import net.minecraft.network.chat.MutableComponent;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class TextUtil {
    private static final int LIST_PREVIEW_LIMIT = 5;
    private static final int MAX_COPY_LENGTH = 16_384;

    private TextUtil() {
    }

    public static Component header(String title) {
        return Component.literal("[InspectMC] ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(title).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
    }

    public static Component section(String title) {
        return Component.literal("— ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(title).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" —").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Every regular value is copyable so useful information is never trapped in chat. */
    public static Component line(String key, Object value) {
        if (value instanceof Boolean booleanValue) {
            return boolLine(key, booleanValue);
        }
        String text = String.valueOf(value);
        return label(key).append(copyValue(text, "Click to copy: " + text));
    }

    public static Component boolLine(String key, boolean value) {
        String copied = Boolean.toString(value);
        return label(key).append(Component.literal(value ? "✓ Yes" : "✕ No")
                .withStyle(style -> style
                        .withColor(value ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .withClickEvent(ChatCompat.clickCopy(copied))
                        .withHoverEvent(textHover("Click to copy: " + copied))
                        .withInsertion(copied)));
    }

    public static Component resourceLine(String key, String id, String registryName) {
        String value = id;
        String hover = "Registry: " + registryName + "\nNamespace: " + IdentifierCompat.namespace(id) + "\nClick to copy the ID";
        return label(key).append(copyValue(value, hover));
    }

    public static Component positionLine(String key, BlockPos pos) {
        return positionLine(key, pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    public static Component positionLine(String key, BlockPos pos, String dimension) {
        String coordinates = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        String command = "/execute in " + dimension + " run tp @s " + coordinates;
        return label(key)
                .append(copyValue(coordinates, "Click to copy the coordinates"))
                .append(Component.literal(" "))
                .append(suggestAction("[TP]", command,
                        "Prefill a teleport command in " + dimension));
    }

    public static Component positionLine(String key, double x, double y, double z) {
        return positionLine(key, String.format(Locale.ROOT, "%.2f %.2f %.2f", x, y, z));
    }

    public static Component positionLine(String key, double x, double y, double z, String dimension) {
        String coordinates = String.format(Locale.ROOT, "%.2f %.2f %.2f", x, y, z);
        String command = "/execute in " + dimension + " run tp @s " + coordinates;
        return label(key)
                .append(copyValue(coordinates, "Click to copy the coordinates"))
                .append(Component.literal(" "))
                .append(suggestAction("[TP]", command,
                        "Prefill a teleport command in " + dimension));
    }

    private static Component positionLine(String key, String coordinates) {
        return label(key)
                .append(copyValue(coordinates, "Click to copy the coordinates"))
                .append(Component.literal(" "))
                .append(suggestAction("[TP]", "/tp " + coordinates,
                        "Prefill a teleport command"));
    }

    public static Component listLine(String key, Collection<String> values) {
        List<String> sorted = values.stream().sorted().toList();
        if (sorted.isEmpty()) {
            return label(key).append(Component.literal("<none>").withStyle(ChatFormatting.DARK_GRAY));
        }
        String allValues = String.join(", ", sorted);
        String preview = sorted.stream().limit(LIST_PREVIEW_LIMIT).collect(Collectors.joining(", "));
        if (sorted.size() > LIST_PREVIEW_LIMIT) {
            preview += ", …";
        }
        String hover = sorted.size() + " value(s)\n" + abbreviate(allValues, 4_000) + "\n\nClick to copy all";
        return label(key).append(copyValue(sorted.size() + " — " + preview, hover));
    }

    public static Component colorLine(String key, int color) {
        String hex = String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
        return label(key).append(copyValue(hex, "Decimal value: " + color + "\nClick to copy " + hex));
    }

    public static MutableComponent copyAction(String label, String value, String hover) {
        boolean truncated = value.length() > MAX_COPY_LENGTH;
        String safeValue = truncated ? abbreviate(value, MAX_COPY_LENGTH) : value;
        String safeHover = hover + (truncated ? "\nValue truncated to protect the chat packet." : "");
        return Component.literal(label).withStyle(style -> style
                .withColor(label.startsWith("[") ? ChatFormatting.GRAY : ChatFormatting.WHITE)
                .withClickEvent(ChatCompat.clickCopy(safeValue))
                .withHoverEvent(textHover(safeHover))
                .withInsertion(safeValue));
    }

    public static MutableComponent suggestAction(String label, String command, String hover) {
        return Component.literal(label).withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withClickEvent(ChatCompat.clickSuggest(command))
                .withHoverEvent(textHover(hover)));
    }

    public static Component actionRow(Component... actions) {
        MutableComponent row = Component.literal("  ");
        for (int index = 0; index < actions.length; index++) {
            if (index > 0) {
                row.append(Component.literal(" "));
            }
            row.append(actions[index]);
        }
        return row;
    }

    public static Component hint(String text) {
        return Component.literal("ℹ " + text).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    public static Component commandLine(String syntax, String validExample) {
        return suggestAction(syntax, validExample,
                "Click to prefill a valid example: " + validExample);
    }

    public static String join(Collection<String> values) {
        return values.isEmpty() ? "<none>" : values.stream().sorted().collect(Collectors.joining(", "));
    }

    public static String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    private static MutableComponent label(String key) {
        return Component.literal(key + ": ").withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent copyValue(String value, String hover) {
        boolean truncated = value.length() > MAX_COPY_LENGTH;
        String safeValue = truncated ? abbreviate(value, MAX_COPY_LENGTH) : value;
        String safeHover = hover + (truncated ? "\nValue truncated to protect the chat packet." : "");
        return Component.literal(safeValue).withStyle(style -> style
                .withColor(ChatFormatting.WHITE)
                .withClickEvent(ChatCompat.clickCopy(safeValue))
                .withHoverEvent(textHover(safeHover))
                .withInsertion(safeValue));
    }

    private static HoverEvent textHover(String text) {
        return ChatCompat.hoverText(text);
    }
}
