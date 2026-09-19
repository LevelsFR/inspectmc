package com.levelsfr.inspectmc.client.config;

import com.levelsfr.inspectmc.InspectMC;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;

/** Keeps Cloth Config optional for both dedicated servers and clients. */
public final class ClothConfigCompat {
    private static final String CLOTH_CONFIG_CLASS = "me.shedaniel.clothconfig2.api.ConfigBuilder";
    private static final String SCREEN_FACTORY_CLASS =
            "com.levelsfr.inspectmc.client.config.InspectMCClothConfigScreen";

    private ClothConfigCompat() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName(CLOTH_CONFIG_CLASS, false, ClothConfigCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    public static Screen createScreen(Screen parent) {
        if (!isAvailable()) {
            return null;
        }
        try {
            Class<?> factory = Class.forName(SCREEN_FACTORY_CLASS, true, ClothConfigCompat.class.getClassLoader());
            Method create = factory.getMethod("create", Screen.class);
            Object screen = create.invoke(null, parent);
            return screen instanceof Screen castScreen ? castScreen : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            InspectMC.LOGGER.warn("Could not create the optional Cloth Config screen", exception);
            return null;
        }
    }

    public static Screen createScreenOrUnavailable(Screen parent) {
        Screen screen = createScreen(parent);
        if (screen != null) {
            return screen;
        }
        try {
            Class<?> type = Class.forName("com.levelsfr.inspectmc.client.config.ClothConfigUnavailableScreen");
            return (Screen) type.getConstructor(Screen.class).newInstance(parent);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return parent;
        }
    }
}
