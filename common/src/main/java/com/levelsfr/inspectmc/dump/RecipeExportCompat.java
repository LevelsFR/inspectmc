package com.levelsfr.inspectmc.dump;

import com.levelsfr.inspectmc.util.IdentifierCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class RecipeExportCompat {
    private RecipeExportCompat() {
    }

    static List<RecipeData> collect(MinecraftServer server) {
        List<RecipeData> entries = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            var recipe = holder.value();
            var typeKey = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            ItemStack result = recipe.getResultItem(server.registryAccess());
            if (result == null) {
                result = ItemStack.EMPTY;
            }

            List<List<String>> ingredients = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                try {
                    List<String> alternatives = Arrays.stream(ingredient.getItems())
                            .filter(stack -> !stack.isEmpty())
                            .map(stack -> IdentifierCompat.value(BuiltInRegistries.ITEM.getKey(stack.getItem())))
                            .distinct().sorted().toList();
                    ingredients.add(alternatives.isEmpty() ? List.of("<custom ingredient>") : alternatives);
                } catch (RuntimeException exception) {
                    ingredients.add(List.of("<custom ingredient>"));
                }
            }

            entries.add(new RecipeData(IdentifierCompat.value(holder.id()),
                    typeKey == null ? "<unknown>" : IdentifierCompat.value(typeKey), recipe.getGroup(),
                    result.isEmpty() ? "" : IdentifierCompat.value(BuiltInRegistries.ITEM.getKey(result.getItem())),
                    result.isEmpty() ? 0 : result.getCount(),
                    result.isEmpty() ? "" : result.getComponents().toString(), List.copyOf(ingredients)));
        }
        return List.copyOf(entries);
    }

    record RecipeData(String id, String type, String group, String resultId, int resultCount,
                      String resultComponents, List<List<String>> ingredients) {
    }
}
