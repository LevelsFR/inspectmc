package com.levelsfr.inspectmc.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/** Small method-name compatibility boundary for the 1.21.1 to 26.2 port. */
public final class MinecraftCompat {
    private MinecraftCompat() {
    }

    public static ServerLevel serverLevel(ServerPlayer player) {
        return (ServerLevel) player.level();
    }

    public static MinecraftServer server(ServerPlayer player) {
        return serverLevel(player).getServer();
    }

    public static boolean hasPermission(CommandSourceStack source, int level) {
        try {
            try {
                return (boolean) source.getClass().getMethod("hasPermission", int.class).invoke(source, level);
            } catch (NoSuchMethodException ignored) {
                Object permissions = source.getClass().getMethod("permissions").invoke(source);
                return (boolean) permissions.getClass().getMethod("hasPermission", int.class).invoke(permissions, level);
            }
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public static int moonPhase(ServerLevel level) {
        try {
            return (int) level.getClass().getMethod("getMoonPhase").invoke(level);
        } catch (ReflectiveOperationException ignored) {
            Object dayTime = invoke(level, "getDayTime", "dayTime");
            return dayTime instanceof Number number ? (int) ((number.longValue() / 24000L) % 8L) : 0;
        }
    }

    public static String playerName(ServerPlayer player) {
        return player.getName().getString();
    }

    public static CompoundTag saveEntity(Entity entity) {
        CompoundTag tag = new CompoundTag();
        for (Method method : entity.getClass().getMethods()) {
            if (!method.getName().equals("saveWithoutId") || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(CompoundTag.class)) {
                try {
                    Object result = method.invoke(entity, tag);
                    return result instanceof CompoundTag compound ? compound : tag;
                } catch (ReflectiveOperationException ignored) {
                    return tag;
                }
            }
        }
        return tag;
    }

    public static List<String> nbtKeys(CompoundTag tag) {
        try {
            Object keys = tag.getClass().getMethod("getAllKeys").invoke(tag);
            if (keys instanceof Iterable<?> iterable) {
                return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                        .map(String::valueOf).sorted().toList();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return List.of();
    }

    public static int minBuildHeight(ServerLevel level) {
        return invokeInt(level, "getMinBuildHeight", "getMinY", -64);
    }

    public static int maxBuildHeight(ServerLevel level) {
        return invokeInt(level, "getMaxBuildHeight", "getMaxY", 320);
    }

    public static Set<Long> forcedChunks(ServerLevel level) {
        Object value = invoke(level, "getForcedChunks", "forcedChunks");
        return value instanceof Set<?> set ? set.stream().filter(Long.class::isInstance).map(Long.class::cast)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()) : Set.of();
    }

    public static String versionName() {
        Object version = net.minecraft.SharedConstants.getCurrentVersion();
        Object name = invoke(version, "getName", "name");
        return name == null ? version.toString() : name.toString();
    }

    public static Object call(Object target, String name, Object... arguments) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) {
                continue;
            }
            boolean compatible = true;
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int index = 0; index < arguments.length; index++) {
                if (arguments[index] != null && !parameterTypes[index].isAssignableFrom(arguments[index].getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) {
                continue;
            }
            try {
                return method.invoke(target, arguments);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    public static int chunkX(ChunkPos pos) {
        Object value = invoke(pos, "x", "getX");
        return value instanceof Number number ? number.intValue() : 0;
    }

    public static int chunkZ(ChunkPos pos) {
        Object value = invoke(pos, "z", "getZ");
        return value instanceof Number number ? number.intValue() : 0;
    }

    public static ChunkPos chunkFromLong(long packed) {
        try {
            Method method = ChunkPos.class.getMethod("of", long.class);
            return (ChunkPos) method.invoke(null, packed);
        } catch (ReflectiveOperationException ignored) {
            try {
                return ChunkPos.class.getConstructor(long.class).newInstance(packed);
            } catch (ReflectiveOperationException exception) {
                return new ChunkPos((int) packed, (int) (packed >> 32));
            }
        }
    }

    public static long chunkLong(int x, int z) {
        try {
            Method method = ChunkPos.class.getMethod("asLong", int.class, int.class);
            return (long) method.invoke(null, x, z);
        } catch (ReflectiveOperationException ignored) {
            try {
                return (long) ChunkPos.class.getMethod("toLong", int.class, int.class).invoke(null, x, z);
            } catch (ReflectiveOperationException exception) {
                return ((long) z << 32) | (x & 0xffffffffL);
            }
        }
    }

    private static int invokeInt(Object target, String first, String second, int fallback) {
        Object value = invoke(target, first, second);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Object invoke(Object target, String... names) {
        for (String name : names) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
