package com.levelsfr.inspectmc.util;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Compatibility boundary for identifiers across supported Minecraft versions. */
public final class IdentifierCompat {
    private IdentifierCompat() {
    }

    public static String value(Object id) {
        return String.valueOf(id);
    }

    public static String namespace(String id) {
        int separator = id.indexOf(':');
        return separator < 0 ? "minecraft" : id.substring(0, separator);
    }

    public static String path(String id) {
        int separator = id.indexOf(':');
        return separator < 0 ? id : id.substring(separator + 1);
    }

    public static String resourceKeyId(Object key) {
        Object id = invokeNoArg(key, "identifier");
        if (id == null) {
            id = invokeNoArg(key, "location");
        }
        return id == null ? value(key) : value(id);
    }

    public static Object parse(String id) {
        Object parsed = invokeStatic("net.minecraft.resources.Identifier", "parse", id);
        if (parsed != null) {
            return parsed;
        }
        return invokeStatic("net.minecraft.resources.ResourceLocation", "parse", id);
    }

    public static Object withDefaultNamespace(String path) {
        Object parsed = invokeStatic("net.minecraft.resources.Identifier", "withDefaultNamespace", path);
        if (parsed != null) {
            return parsed;
        }
        return invokeStatic("net.minecraft.resources.ResourceLocation", "withDefaultNamespace", path);
    }

    public static Object fromNamespaceAndPath(String namespace, String path) {
        Object parsed = invokeStatic("net.minecraft.resources.Identifier", "of", namespace, path);
        if (parsed != null) {
            return parsed;
        }
        return invokeStatic("net.minecraft.resources.ResourceLocation", "fromNamespaceAndPath", namespace, path);
    }

    public static ArgumentType<?> argumentType() {
        String[] classes = {
                "net.minecraft.commands.arguments.IdentifierArgument",
                "net.minecraft.commands.arguments.ResourceLocationArgument"
        };
        for (String className : classes) {
            Object argument = invokeStatic(className, "id");
            if (argument instanceof ArgumentType<?> typed) {
                return typed;
            }
        }
        throw new IllegalStateException("No Minecraft identifier command argument is available");
    }

    public static String argument(CommandContext<?> context, String name) {
        String[] classes = {
                "net.minecraft.commands.arguments.IdentifierArgument",
                "net.minecraft.commands.arguments.ResourceLocationArgument"
        };
        for (String className : classes) {
            Object value = invokeStatic(className, "getId", context, name);
            if (value != null) {
                return value(value);
            }
        }
        throw new IllegalStateException("No Minecraft identifier command argument is available");
    }

    public static boolean registryContains(Registry<?> registry, String id) {
        Object result = invokeOneArg(registry, "containsKey", parse(id));
        return result instanceof Boolean booleanValue && booleanValue;
    }

    @SuppressWarnings("unchecked")
    public static <T> Registry<T> registry(RegistryAccess access, ResourceKey<? extends Registry<T>> key) {
        return access.registries()
                .filter(entry -> resourceKeyId(entry.key()).equals(resourceKeyId(key)))
                .map(entry -> (Registry<T>) entry.value())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Registry is unavailable: " + resourceKeyId(key)));
    }

    public static boolean registryHasTag(Registry<?> registry, ResourceKey<?> registryKey, String id) {
        Object tag = invokeStatic("net.minecraft.tags.TagKey", "create", registryKey, parse(id));
        Object result = invokeOneArg(registry, "getTag", tag);
        return result instanceof java.util.Optional<?> optional && optional.isPresent();
    }

    public static boolean holderHasTag(Holder<?> holder, ResourceKey<?> registryKey, String id) {
        Object tag = invokeStatic("net.minecraft.tags.TagKey", "create", registryKey, parse(id));
        Object result = invokeOneArg(holder, "is", tag);
        return result instanceof Boolean booleanValue && booleanValue;
    }

    public static Map<String, Resource> listResources(ResourceManager manager, String directory) {
        @SuppressWarnings("unchecked")
        Map<?, Resource> resources = (Map<?, Resource>) (Map<?, ?>) manager.listResources(directory, ignored -> true);
        Map<String, Resource> result = new LinkedHashMap<>();
        resources.forEach((id, resource) -> result.put(value(id), resource));
        return result;
    }

    public static List<String> tags(Object target) {
        Object tags = invokeNoArg(target, "getTags");
        if (tags == null) {
            tags = invokeNoArg(target, "tags");
        }
        if (tags instanceof Stream<?> stream) {
            return stream.map(IdentifierCompat::resourceKeyId).sorted().toList();
        }
        return List.of();
    }

    public static String descriptionId(Object target) {
        Object value = invokeNoArg(target, "getDescriptionId");
        return value == null ? "<unknown>" : value.toString();
    }

    private static Object invokeStatic(String className, String methodName, Object... arguments) {
        try {
            Class<?> type = Class.forName(className);
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != arguments.length) {
                    continue;
                }
                return method.invoke(null, arguments);
            }
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeOneArg(Object target, String methodName, Object argument) {
        if (target == null || argument == null) {
            return null;
        }
        try {
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                    return method.invoke(target, argument);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }
}
