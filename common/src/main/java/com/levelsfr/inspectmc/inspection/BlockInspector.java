package com.levelsfr.inspectmc.inspection;

import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

public final class BlockInspector {
    private BlockInspector() {
    }

    public static List<Component> inspect(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        List<Component> lines = new ArrayList<>();
        String blockId = IdentifierCompat.value(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        lines.add(TextUtil.section("Identity"));
        lines.add(TextUtil.resourceLine("Registry ID", blockId, "block"));
        InspectionOrigin.append(lines, blockId);
        lines.add(TextUtil.line("Name", state.getBlock().getName().getString()));

        lines.add(TextUtil.section("World"));
        lines.add(TextUtil.positionLine("Position", pos, IdentifierCompat.resourceKeyId(level.dimension())));
        lines.add(TextUtil.resourceLine("Dimension", IdentifierCompat.resourceKeyId(level.dimension()), "dimension"));

        lines.add(TextUtil.section("State"));
        lines.add(TextUtil.line("State", state));
        lines.add(TextUtil.line("Destroy speed", state.getDestroySpeed(level, pos)));
        lines.add(TextUtil.resourceLine("Fluid", IdentifierCompat.value(BuiltInRegistries.FLUID.getKey(state.getFluidState().getType())), "fluid"));

        if (!state.getProperties().isEmpty()) {
            lines.add(TextUtil.section("Properties"));
            for (Property<?> property : state.getProperties()) {
                lines.add(TextUtil.line("  " + property.getName(), propertyValue(state, property)));
            }
        }

        List<String> tags = IdentifierCompat.tags(state);
        lines.add(TextUtil.section("Data"));
        lines.add(TextUtil.listLine("Tags", tags));

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            lines.add(TextUtil.resourceLine("Block entity", IdentifierCompat.value(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())), "block_entity_type"));
        }
        lines.add(TextUtil.actionRow(
                TextUtil.copyAction("[Copy ID]", blockId, "Copy the block ID"),
                TextUtil.copyAction("[Copy tags]", TextUtil.join(tags), "Copy all tags"),
                TextUtil.suggestAction("[Inspect again]", "/inspectmc inspect block target",
                        "Prefill the targeting command")
        ));
        return lines;
    }

    public static List<Component> inspect(Holder.Reference<Block> blockHolder) {
        Block block = blockHolder.value();
        BlockState state = block.defaultBlockState();
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Identity"));
        lines.add(TextUtil.resourceLine("Registry ID", IdentifierCompat.resourceKeyId(blockHolder.key()), "block"));
        InspectionOrigin.append(lines, IdentifierCompat.resourceKeyId(blockHolder.key()));
        lines.add(TextUtil.line("Name", block.getName().getString()));
        lines.add(TextUtil.line("Translation key", block.getDescriptionId()));
        lines.add(TextUtil.resourceLine("Item form", IdentifierCompat.value(BuiltInRegistries.ITEM.getKey(block.asItem())), "item"));

        lines.add(TextUtil.section("States"));
        lines.add(TextUtil.line("Default state", state));
        lines.add(TextUtil.line("Possible states", block.getStateDefinition().getPossibleStates().size()));

        lines.add(TextUtil.section("Physics"));
        lines.add(TextUtil.line("Explosion resistance", block.getExplosionResistance()));
        lines.add(TextUtil.line("Friction", block.getFriction()));
        lines.add(TextUtil.line("Speed factor", block.getSpeedFactor()));
        lines.add(TextUtil.line("Jump factor", block.getJumpFactor()));

        lines.add(TextUtil.section("Behavior"));
        lines.add(TextUtil.line("Render shape", state.getRenderShape()));
        lines.add(TextUtil.line("Light emission", state.getLightEmission()));
        lines.add(TextUtil.line("Air", state.isAir()));
        lines.add(TextUtil.line("Contains fluid", !state.getFluidState().isEmpty()));
        lines.add(TextUtil.line("Occluding", state.canOcclude()));
        lines.add(TextUtil.line("Signal source", state.isSignalSource()));
        lines.add(TextUtil.line("Randomly ticking", state.isRandomlyTicking()));
        lines.add(TextUtil.line("Requires correct tool", state.requiresCorrectToolForDrops()));
        lines.add(TextUtil.line("Piston reaction", state.getPistonPushReaction()));
        lines.add(TextUtil.line("Sound type", state.getSoundType()));

        if (!state.getProperties().isEmpty()) {
            lines.add(TextUtil.section("Available properties"));
            for (Property<?> property : state.getProperties()) {
                String values = property.getPossibleValues().stream().map(String::valueOf).sorted().toList().toString();
                lines.add(TextUtil.line("  " + property.getName(), values));
            }
        }
        List<String> tags = blockHolder.tags().map(IdentifierCompat::resourceKeyId).toList();
        lines.add(TextUtil.section("Data"));
        lines.add(TextUtil.listLine("Tags", tags));
        lines.add(TextUtil.actionRow(
                TextUtil.copyAction("[Copy ID]", IdentifierCompat.resourceKeyId(blockHolder.key()), "Copy the block ID"),
                TextUtil.copyAction("[Copy tags]", TextUtil.join(tags), "Copy all tags"),
                TextUtil.suggestAction("[Find within 32]",
                        "/inspectmc inspect block id " + IdentifierCompat.resourceKeyId(blockHolder.key()) + " 32",
                        "Find the nearest instance within 32 blocks")
        ));
        return lines;
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
