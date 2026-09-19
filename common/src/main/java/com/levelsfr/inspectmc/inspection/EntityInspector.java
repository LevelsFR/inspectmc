package com.levelsfr.inspectmc.inspection;

import com.levelsfr.inspectmc.cobblemon.CobblemonOptionalBridge;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import com.levelsfr.inspectmc.util.MinecraftCompat;
import com.levelsfr.inspectmc.util.TextUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class EntityInspector {
    private EntityInspector() {
    }

    public static Entity findLookedAt(ServerPlayer player, double distance) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(distance));
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(distance)).inflate(1.0D);
        EntityHitResult result = ProjectileUtil.getEntityHitResult(
                player,
                start,
                end,
                searchBox,
                entity -> !entity.isSpectator() && entity.isPickable(),
                distance * distance
        );
        return result == null ? null : result.getEntity();
    }

    public static List<Component> inspect(Entity entity) {
        List<Component> lines = new ArrayList<>();
        String typeId = IdentifierCompat.value(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        lines.add(TextUtil.section("Identity"));
        lines.add(TextUtil.line("Name", entity.getName().getString()));
        lines.add(TextUtil.resourceLine("Registry ID", typeId, "entity_type"));
        InspectionOrigin.append(lines, typeId);
        lines.add(TextUtil.line("UUID", entity.getUUID()));
        lines.add(TextUtil.line("Entity ID", entity.getId()));

        lines.add(TextUtil.section("World"));
        lines.add(TextUtil.positionLine("Position", entity.getX(), entity.getY(), entity.getZ(),
                IdentifierCompat.resourceKeyId(entity.level().dimension())));
        lines.add(TextUtil.resourceLine("Dimension", IdentifierCompat.resourceKeyId(entity.level().dimension()), "dimension"));

        lines.add(TextUtil.section("State"));
        lines.add(TextUtil.line("Custom name", entity.hasCustomName() ? entity.getCustomName().getString() : "<none>"));
        lines.add(TextUtil.line("Invulnerable", entity.isInvulnerable()));
        lines.add(TextUtil.line("Pose", entity.getPose()));
        lines.add(TextUtil.line("Velocity", entity.getDeltaMovement()));
        lines.add(TextUtil.line("On fire", entity.isOnFire()));
        lines.add(TextUtil.line("Silent", entity.isSilent()));
        lines.add(TextUtil.line("No gravity", entity.isNoGravity()));
        if (entity instanceof LivingEntity living) {
            lines.add(TextUtil.line("Health", living.getHealth() + " / " + living.getMaxHealth()));
            lines.add(TextUtil.listLine("Active effects",
                    living.getActiveEffects().stream().map(Object::toString).toList()));
            List<String> equipment = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                var stack = living.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    equipment.add(slot.getName() + "="
                            + IdentifierCompat.value(BuiltInRegistries.ITEM.getKey(stack.getItem())) + " ×" + stack.getCount());
                }
            }
            lines.add(TextUtil.listLine("Equipment", equipment));
        }

        lines.add(TextUtil.section("Relations"));
        lines.add(TextUtil.line("Passengers", entity.getPassengers().size()));
        lines.add(TextUtil.line("Vehicle", entity.getVehicle() == null ? "<none>" : entity.getVehicle().getName().getString()));

        CompoundTag serialized = MinecraftCompat.saveEntity(entity);
        lines.add(TextUtil.section("Data"));
        lines.add(TextUtil.listLine("Scoreboard tags", IdentifierCompat.tags(entity)));
        lines.add(TextUtil.line("Serialized fields", serialized.size()));
        lines.add(TextUtil.listLine("NBT field names", MinecraftCompat.nbtKeys(serialized)));
        lines.add(TextUtil.actionRow(
                TextUtil.copyAction("[Copy ID]", typeId, "Copy the entity type ID"),
                TextUtil.copyAction("[Copy UUID]", entity.getUUID().toString(), "Copy this instance UUID"),
                TextUtil.copyAction("[Copy NBT]", serialized.toString(),
                        "Copy NBT without displaying it in chat")
        ));
        CobblemonOptionalBridge.appendEntityDetails(lines, entity);
        lines.add(TextUtil.hint("Raw NBT is hidden by default; hover fields or use the copy button."));
        return lines;
    }

    public static List<Component> inspectType(Holder.Reference<EntityType<?>> entityTypeHolder) {
        EntityType<?> type = entityTypeHolder.value();
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Identity"));
        lines.add(TextUtil.resourceLine("Registry ID", IdentifierCompat.resourceKeyId(entityTypeHolder.key()), "entity_type"));
        InspectionOrigin.append(lines, IdentifierCompat.resourceKeyId(entityTypeHolder.key()));
        lines.add(TextUtil.line("Name", type.getDescription().getString()));
        lines.add(TextUtil.line("Translation key", type.getDescriptionId()));
        lines.add(TextUtil.line("Category", type.getCategory().getName()));

        lines.add(TextUtil.section("Properties"));
        lines.add(TextUtil.line("Dimensions", type.getWidth() + " x " + type.getHeight()));
        lines.add(TextUtil.line("Can summon", type.canSummon()));
        lines.add(TextUtil.line("Can serialize", type.canSerialize()));
        lines.add(TextUtil.line("Fire immune", type.fireImmune()));
        lines.add(TextUtil.line("Can spawn far from player", type.canSpawnFarFromPlayer()));
        lines.add(TextUtil.resourceLine("Default loot table", IdentifierCompat.resourceKeyId(type.getDefaultLootTable()), "loot_table"));

        lines.add(TextUtil.section("Network"));
        lines.add(TextUtil.line("Client tracking range", type.clientTrackingRange()));
        lines.add(TextUtil.line("Update interval", type.updateInterval()));
        lines.add(TextUtil.line("Tracks deltas", type.trackDeltas()));

        lines.add(TextUtil.section("Data"));
        lines.add(TextUtil.line("Required features", type.requiredFeatures()));
        List<String> tags = entityTypeHolder.tags().map(IdentifierCompat::resourceKeyId).toList();
        lines.add(TextUtil.listLine("Tags", tags));
        lines.add(TextUtil.actionRow(
                TextUtil.copyAction("[Copy ID]", IdentifierCompat.resourceKeyId(entityTypeHolder.key()), "Copy the entity type ID"),
                TextUtil.copyAction("[Copy tags]", TextUtil.join(tags), "Copy all tags")
        ));
        return lines;
    }
}
