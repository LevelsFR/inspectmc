package com.levelsfr.inspectmc.inspection;

import com.levelsfr.inspectmc.util.IdentifierCompat;
import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.MinecraftCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public final class BlockEntityInspector {
    private BlockEntityInspector() {
    }

    public static List<Component> inspect(ServerLevel level, BlockEntity blockEntity) {
        List<Component> lines = new ArrayList<>();
        String typeId = IdentifierCompat.value(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
        String blockId = IdentifierCompat.value(BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()));

        lines.add(TextUtil.section("Identity"));
        lines.add(TextUtil.resourceLine("Block entity ID", typeId, "block_entity_type"));
        lines.add(TextUtil.resourceLine("Block ID", blockId, "block"));
        InspectionOrigin.append(lines, typeId);
        if (blockEntity instanceof Nameable nameable && nameable.hasCustomName()) {
            lines.add(TextUtil.line("Custom name", nameable.getCustomName().getString()));
        }

        lines.add(TextUtil.section("World"));
        lines.add(TextUtil.positionLine("Position", blockEntity.getBlockPos(), IdentifierCompat.resourceKeyId(level.dimension())));
        lines.add(TextUtil.resourceLine("Dimension", IdentifierCompat.resourceKeyId(level.dimension()), "dimension"));
                lines.add(TextUtil.line("Removed", blockEntity.isRemoved()));

        if (blockEntity instanceof Container container) {
            int occupied = 0;
            List<String> contents = new ArrayList<>();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                var stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    occupied++;
                    contents.add("slot " + slot + ": " + BuiltInRegistries.ITEM.getKey(stack.getItem())
                            + " ×" + stack.getCount());
                }
            }
            lines.add(TextUtil.section("Inventory"));
            lines.add(TextUtil.line("Slots", container.getContainerSize()));
            lines.add(TextUtil.line("Occupied slots", occupied));
            lines.add(TextUtil.listLine("Contents", contents));
        }

        CompoundTag nbt = blockEntity.saveWithFullMetadata(level.registryAccess());
        lines.add(TextUtil.section("Data"));
        lines.add(TextUtil.line("Serialized fields", nbt.size()));
        lines.add(TextUtil.listLine("NBT field names", MinecraftCompat.nbtKeys(nbt)));
        lines.add(TextUtil.actionRow(
                TextUtil.copyAction("[Copy type ID]", typeId, "Copy the block entity type ID"),
                TextUtil.copyAction("[Copy block ID]", blockId, "Copy the block ID"),
                TextUtil.copyAction("[Copy NBT]", nbt.toString(), "Copy the serialized block entity NBT")
        ));
        lines.add(TextUtil.hint("Raw NBT stays hidden to keep chat readable."));
        return lines;
    }
}
