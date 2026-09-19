package com.levelsfr.inspectmc.cobblemon;

import com.levelsfr.inspectmc.InspectMC;

import java.util.Locale;

/** Detects optional Cobblemon integrations without linking their classes. */
public final class CobblemonApiPresence {
    public static final String COBBLEMON_MOD_ID = "cobblemon";
    public static final String OS_LIBRARY_MOD_ID = "os_cobblemon_library";

    private static final String POKEMON_ENTITY_CLASS =
            "com.cobblemon.mod.common.entity.pokemon.PokemonEntity";
    private static final String LIBRARY_ENTITY_BRIDGE_CLASS =
            "com.ourstory.oscobblemon.entity.PokemonEntities";

    private CobblemonApiPresence() {
    }

    public static boolean isCobblemonInstalled() {
        return hasMod(COBBLEMON_MOD_ID);
    }

    public static boolean isAvailable() {
        if (!hasMod(COBBLEMON_MOD_ID) || !hasMod(OS_LIBRARY_MOD_ID)) {
            return false;
        }

        return loadable(POKEMON_ENTITY_CLASS) && loadable(LIBRARY_ENTITY_BRIDGE_CLASS);
    }

    public static boolean isPokemonEntity(Object value) {
        if (value == null || !isAvailable()) {
            return false;
        }

        try {
            return Class.forName(POKEMON_ENTITY_CLASS, false,
                    CobblemonApiPresence.class.getClassLoader()).isInstance(value);
        } catch (LinkageError | ReflectiveOperationException exception) {
            return false;
        }
    }

    public static String availabilityDescription() {
        boolean cobblemon = hasMod(COBBLEMON_MOD_ID);
        boolean library = hasMod(OS_LIBRARY_MOD_ID);
        if (cobblemon && library && isAvailable()) {
            return "Cobblemon and OS Cobblemon Library integration available";
        }
        if (!cobblemon && !library) {
            return "Cobblemon and OS Cobblemon Library are not installed";
        }
        if (!cobblemon) {
            return "Cobblemon is not installed";
        }
        if (!library) {
            return "OS Cobblemon Library is not installed";
        }
        return "Cobblemon integration classes could not be loaded";
    }

    private static boolean hasMod(String modId) {
        return InspectMC.platform().loadedMods().stream()
                .anyMatch(mod -> mod.id().toLowerCase(Locale.ROOT).equals(modId));
    }

    private static boolean loadable(String className) {
        try {
            Class.forName(className, false, CobblemonApiPresence.class.getClassLoader());
            return true;
        } catch (LinkageError | ReflectiveOperationException exception) {
            return false;
        }
    }
}
