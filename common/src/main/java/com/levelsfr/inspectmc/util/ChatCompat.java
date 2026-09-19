package com.levelsfr.inspectmc.util;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.lang.reflect.Constructor;

/** Bridges the 1.21.1 event classes and the 26.2 event interfaces. */
public final class ChatCompat {
    private ChatCompat() {
    }

    public static ClickEvent clickCopy(String value) {
        return click("CopyToClipboard", ClickEvent.Action.COPY_TO_CLIPBOARD, value);
    }

    public static ClickEvent clickRun(String value) {
        return click("RunCommand", ClickEvent.Action.RUN_COMMAND, value);
    }

    public static ClickEvent clickSuggest(String value) {
        return click("SuggestCommand", ClickEvent.Action.SUGGEST_COMMAND, value);
    }

    public static HoverEvent hoverText(String value) {
        try {
            Class<?> type = Class.forName("net.minecraft.network.chat.HoverEvent$ShowText");
            Constructor<?> constructor = type.getConstructor(Component.class);
            return (HoverEvent) constructor.newInstance(Component.literal(value));
        } catch (ReflectiveOperationException ignored) {
            try {
                return HoverEvent.class.getConstructor(HoverEvent.Action.class, Object.class)
                        .newInstance(HoverEvent.Action.SHOW_TEXT, Component.literal(value));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Minecraft hover event API is unavailable", exception);
            }
        }
    }

    private static ClickEvent click(String nestedName, ClickEvent.Action action, String value) {
        try {
            Class<?> type = Class.forName("net.minecraft.network.chat.ClickEvent$" + nestedName);
            return (ClickEvent) type.getConstructor(String.class).newInstance(value);
        } catch (ReflectiveOperationException ignored) {
            try {
                return ClickEvent.class.getConstructor(ClickEvent.Action.class, String.class)
                        .newInstance(action, value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Minecraft click event API is unavailable", exception);
            }
        }
    }
}
