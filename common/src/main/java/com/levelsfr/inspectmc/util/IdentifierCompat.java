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
        Object id = invokeNoArg(key, "identifier", "location", "method_29177", "comp_327");
        return id == null ? value(key) : value(id);
    }

    public static Object parse(String id) {
        Object parsed = invokeStatic("net.minecraft.resources.Identifier", "parse", id);
        if (parsed != null) {
            return parsed;
        }
        parsed = invokeStatic("net.minecraft.resources.ResourceLocation", "parse", id);
        return parsed != null ? parsed : invokeStatic("net.minecraft.class_2960", "method_60654", id);
    }

    public static Object withDefaultNamespace(String path) {
        Object parsed = invokeStatic("net.minecraft.resources.Identifier", "withDefaultNamespace", path);
        if (parsed != null) {
            return parsed;
        }
        parsed = invokeStatic("net.minecraft.resources.ResourceLocation", "withDefaultNamespace", path);
        return parsed != null ? parsed : invokeStatic("net.minecraft.class_2960", "method_60656", path);
    }

    public static Object fromNamespaceAndPath(String namespace, String path) {
        Object parsed = invokeStatic("net.minecraft.resources.Identifier", "of", namespace, path);
        if (parsed != null) {
            return parsed;
        }
        parsed = invokeStatic("net.minecraft.resources.ResourceLocation", "fromNamespaceAndPath", namespace, path);
        return parsed != null ? parsed : invokeStatic("net.minecraft.class_2960", "method_60655", namespace, path);
    }

    public static ArgumentType<?> argumentType() {
        String[] classes = {
                "net.minecraft.commands.arguments.IdentifierArgument",
                "net.minecraft.commands.arguments.ResourceLocationArgument",
                "net.minecraft.class_2232"
        };
        for (String className : classes) {
            Object argument = invokeStatic(className, "id");
            if (argument instanceof ArgumentType<?> typed) {
                return typed;
            }
            try {
                // The 1.21.1 Fabric runtime remaps the factory name, but not constructors.
                Object instance = Class.forName(className).getConstructor().newInstance();
                if (instance instanceof ArgumentType<?> typed) {
                    return typed;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next Minecraft namespace.
            }
        }
        throw new IllegalStateException("No Minecraft identifier command argument is available");
    }

    public static String argument(CommandContext<?> context, String name) {
        return value(context.getArgument(name, Object.class));
    }

    public static boolean registryContains(Registry<?> registry, String id) {
        Object result = invokeOneArg(registry, parse(id), "containsKey", "method_10250");
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
        if (tag == null) {
            tag = invokeStatic("net.minecraft.class_6862", "method_40092", registryKey, parse(id));
        }
        Object result = invokeOneArg(registry, tag, "getTag", "method_40266");
        return result instanceof java.util.Optional<?> optional && optional.isPresent();
    }

    public static boolean holderHasTag(Holder<?> holder, ResourceKey<?> registryKey, String id) {
        Object tag = invokeStatic("net.minecraft.tags.TagKey", "create", registryKey, parse(id));
        if (tag == null) {
            tag = invokeStatic("net.minecraft.class_6862", "method_40092", registryKey, parse(id));
        }
        Object result = invokeOneArg(holder, tag, "is", "method_40220");
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
        Object tags = invokeNoArg(target, "getTags", "tags", "method_40133", "method_40144", "method_5752");
        if (tags instanceof Stream<?> stream) {
            return stream.map(IdentifierCompat::resourceKeyId).sorted().toList();
        }
        return List.of();
    }

    public static String descriptionId(Object target) {
        Object value = invokeNoArg(target, "getDescriptionId", "method_7876");
        return value == null ? "<unknown>" : value.toString();
    }

    private static Object invokeStatic(String className, String methodName, Object... arguments) {
        try {
            Class<?> type = Class.forName(className);
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != arguments.length) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean compatible = true;
                for (int index = 0; index < arguments.length; index++) {
                    if (arguments[index] == null
                            ? parameterTypes[index].isPrimitive()
                            : !parameterTypes[index].isAssignableFrom(arguments[index].getClass())) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    return method.invoke(null, arguments);
                }
            }
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                return target.getClass().getMethod(methodName).invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Object invokeOneArg(Object target, Object argument, String... methodNames) {
        if (target == null || argument == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                for (Method method : target.getClass().getMethods()) {
                    if (method.getName().equals(methodName) && method.getParameterCount() == 1
                            && method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                        return method.invoke(target, argument);
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
