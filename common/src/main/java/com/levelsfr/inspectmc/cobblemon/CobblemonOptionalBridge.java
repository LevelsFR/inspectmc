package com.levelsfr.inspectmc.cobblemon;

import com.levelsfr.inspectmc.util.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.List;

/** Reflective boundary for optional OS Cobblemon Library support. */
public final class CobblemonOptionalBridge {
    private CobblemonOptionalBridge() {
    }

    public static boolean isAvailable() {
        return CobblemonApiPresence.isAvailable();
    }

    public static boolean isPokemonEntity(Entity entity) {
        return CobblemonApiPresence.isPokemonEntity(entity);
    }

    public static void appendEntityDetails(List<Component> lines, Entity entity) {
        if (!isPokemonEntity(entity)) {
            return;
        }

        try {
            lines.addAll(invokeList("inspectEntity", Entity.class, entity));
        } catch (Throwable exception) {
            lines.add(TextUtil.hint("Cobblemon data could not be inspected: "
                    + safeMessage(exception)));
        }
    }

    public static List<Component> inspectEntity(Entity entity) {
        if (!isAvailable()) {
            return List.of(TextUtil.hint(CobblemonApiPresence.availabilityDescription()
                    + ". Install both optional mods to use this diagnostic."));
        }
        if (!isPokemonEntity(entity)) {
            return List.of(TextUtil.hint("The selected entity is not a Cobblemon Pokémon."));
        }

        try {
            return invokeList("inspectEntity", Entity.class, entity);
        } catch (Throwable exception) {
            return List.of(TextUtil.hint("Cobblemon data could not be inspected: "
                    + safeMessage(exception)));
        }
    }

    public static List<Component> inspectPlayer(ServerPlayer player) {
        if (!isAvailable()) {
            return List.of(TextUtil.hint(CobblemonApiPresence.availabilityDescription()
                    + ". Install both optional mods to use this diagnostic."));
        }

        try {
            return invokeList("inspectPlayer", ServerPlayer.class, player);
        } catch (Throwable exception) {
            return List.of(TextUtil.hint("Cobblemon player data could not be inspected: "
                    + safeMessage(exception)));
        }
    }

    public static List<Component> findOwned(ServerPlayer player, String query) {
        if (!isAvailable()) {
            return List.of(TextUtil.hint(CobblemonApiPresence.availabilityDescription()
                    + ". Install both optional mods to use this diagnostic."));
        }

        try {
            return invokeList("findOwned", ServerPlayer.class, String.class, player, query);
        } catch (Throwable exception) {
            return List.of(TextUtil.hint("Cobblemon search failed: " + safeMessage(exception)));
        }
    }

    private static String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : TextUtil.abbreviate(message, 180);
    }

    @SuppressWarnings("unchecked")
    private static List<Component> invokeList(String methodName, Class<?> parameterType, Object argument) throws Exception {
        return (List<Component>) invoke(methodName, new Class<?>[]{parameterType}, argument);
    }

    @SuppressWarnings("unchecked")
    private static List<Component> invokeList(String methodName, Class<?> firstType, Class<?> secondType,
                                             Object first, Object second) throws Exception {
        return (List<Component>) invoke(methodName, new Class<?>[]{firstType, secondType}, first, second);
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Class<?> integration = Class.forName("com.levelsfr.inspectmc.cobblemon.CobblemonApiIntegration");
        Method method = integration.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }
}
