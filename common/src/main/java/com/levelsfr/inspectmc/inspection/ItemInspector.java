package com.levelsfr.inspectmc.inspection;

import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ItemInspector {
    private ItemInspector() {
    }

    public static List<Component> inspect(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        if (stack.isEmpty()) {
            lines.add(TextUtil.hint("No item in the main hand."));
            return lines;
        }

        String id = IdentifierCompat.value(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        lines.add(TextUtil.section("Identity"));
        lines.add(TextUtil.line("Name", stack.getHoverName().getString()));
        lines.add(TextUtil.resourceLine("Registry ID", id, "item"));
        InspectionOrigin.append(lines, id);
        lines.add(TextUtil.line("Count", stack.getCount()));

        lines.add(TextUtil.section("State"));
        lines.add(TextUtil.line("Max stack size", stack.getMaxStackSize()));
        lines.add(TextUtil.line("Damageable", stack.isDamageableItem()));
        if (stack.isDamageableItem()) {
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            lines.add(TextUtil.line("Durability", remaining + " / " + stack.getMaxDamage()));
        }

        lines.add(TextUtil.section("Data"));
        lines.add(TextUtil.line("Data components", stack.getComponents().size()));
        lines.add(TextUtil.actionRow(
                TextUtil.copyAction("[Copy ID]", id, "Copy the item ID"),
                TextUtil.copyAction("[Copy components]", stack.getComponents().toString(),
                        "Copy the raw data components"),
                TextUtil.suggestAction("[Details]", "/inspectmc inspect held details",
                        "Prefill the detailed inspection command")
        ));
        return lines;
    }

    /** Compact, copy-first view of the main-hand item. */
    public static List<Component> inspectHand(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        if (stack.isEmpty()) {
            lines.add(TextUtil.hint("No item in the main hand."));
            return lines;
        }

        String itemId = IdentifierCompat.value(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        List<String> itemTags = IdentifierCompat.tags(stack).stream().map(tag -> "#" + tag).toList();

        lines.add(TextUtil.section("Item in main hand"));
        lines.add(TextUtil.line("Name", stack.getHoverName().getString()));
        lines.add(TextUtil.resourceLine("Item ID", itemId, "item"));
        InspectionOrigin.append(lines, itemId);
        lines.add(TextUtil.line("Count", stack.getCount()));

        lines.add(TextUtil.section("Item tags"));
        appendCopyableValues(lines, itemTags, "No item tags.", "item tag");

        lines.add(TextUtil.section("Data"));
        lines.add(TextUtil.line("Data components", stack.getComponents().size()));
        List<Component> actions = new ArrayList<>();
        actions.add(TextUtil.copyAction("[Copy ID]", itemId, "Copy the item ID"));
        if (!itemTags.isEmpty()) {
            actions.add(TextUtil.copyAction("[Copy tags]", TextUtil.join(itemTags), "Copy all item tags"));
        }
        actions.add(TextUtil.copyAction("[Copy components]", stack.getComponents().toString(),
                "Copy the raw data components"));
        actions.add(TextUtil.suggestAction("[Details]", "/inspectmc inspect held details",
                "Prefill the detailed inspection command"));
        lines.add(TextUtil.actionRow(actions.toArray(Component[]::new)));

        if (stack.getItem() instanceof BlockItem blockItem) {
            String blockId = IdentifierCompat.value(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()));
            List<String> blockTags = IdentifierCompat.tags(blockItem.getBlock().defaultBlockState()).stream()
                    .map(tag -> "#" + tag).toList();

            lines.add(TextUtil.section("Held block"));
            lines.add(TextUtil.resourceLine("Block ID", blockId, "block"));
            lines.add(TextUtil.section("Block tags"));
            appendCopyableValues(lines, blockTags, "No block tags.", "block tag");
            List<Component> blockActions = new ArrayList<>();
            blockActions.add(TextUtil.copyAction("[Copy block ID]", blockId, "Copy the block ID"));
            if (!blockTags.isEmpty()) {
                blockActions.add(TextUtil.copyAction("[Copy block tags]", TextUtil.join(blockTags),
                        "Copy all block tags"));
            }
            lines.add(TextUtil.actionRow(blockActions.toArray(Component[]::new)));
        }
        return lines;
    }

    public static List<Component> inspectHandSummary(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        if (stack.isEmpty()) {
            lines.add(TextUtil.hint("No item in the main hand."));
            return lines;
        }

        String itemId = IdentifierCompat.value(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        lines.add(TextUtil.line("Name", stack.getHoverName().getString()));
        lines.add(TextUtil.resourceLine("Item ID", itemId, "item"));
        lines.add(TextUtil.line("Count", stack.getCount()));
        lines.add(TextUtil.actionRow(
                TextUtil.copyAction("[Copy ID]", itemId, "Copy the item ID")
        ));
        return lines;
    }

    public static List<Component> inspectDetails(ItemStack stack) {
        List<Component> lines = new ArrayList<>(inspect(stack));
        if (stack.isEmpty()) {
            return lines;
        }
        lines.add(TextUtil.section("Technical"));
        lines.add(TextUtil.line("Translation key", IdentifierCompat.descriptionId(stack.getItem())));
        lines.add(TextUtil.line("Item class", stack.getItem().getClass().getName()));
        lines.add(TextUtil.line("Rarity", stack.getRarity()));
        lines.add(TextUtil.line("Enchanted", stack.isEnchanted()));
        lines.add(TextUtil.line("Foil", stack.hasFoil()));
        if (stack.isDamageableItem()) {
            lines.add(TextUtil.line("Raw damage", stack.getDamageValue()));
        }
        lines.add(TextUtil.hint("Raw components remain available through the copy button without flooding chat."));
        return lines;
    }

    private static void appendCopyableValues(List<Component> lines, List<String> values,
                                             String emptyMessage, String valueType) {
        if (values.isEmpty()) {
            lines.add(TextUtil.hint(emptyMessage));
            return;
        }
        for (String value : values) {
            lines.add(TextUtil.copyAction("  " + value, value, "Copy this " + valueType));
        }
    }
}
