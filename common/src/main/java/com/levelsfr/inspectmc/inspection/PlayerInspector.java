package com.levelsfr.inspectmc.inspection;

import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import com.levelsfr.inspectmc.util.MinecraftCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerInspector {
    private PlayerInspector() {
    }

    public static List<Component> inspect(ServerPlayer player) {
        List<Component> lines = new ArrayList<>();
        lines.add(TextUtil.section("Identity"));
        lines.add(TextUtil.line("Name", MinecraftCompat.playerName(player)));
        lines.add(TextUtil.line("UUID", player.getUUID()));
        lines.add(TextUtil.line("Entity ID", player.getId()));

        lines.add(TextUtil.section("World"));
        lines.add(TextUtil.positionLine("Position", player.getX(), player.getY(), player.getZ(),
                IdentifierCompat.resourceKeyId(player.level().dimension())));
        lines.add(TextUtil.positionLine("Block position", player.blockPosition(), IdentifierCompat.resourceKeyId(player.level().dimension())));
        lines.add(TextUtil.resourceLine("Dimension", IdentifierCompat.resourceKeyId(player.level().dimension()), "dimension"));

        lines.add(TextUtil.section("Survival"));
        lines.add(TextUtil.line("Game mode", player.gameMode.getGameModeForPlayer().getName()));
        lines.add(TextUtil.line("Health", player.getHealth() + " / " + player.getMaxHealth()));
        lines.add(TextUtil.line("Food", player.getFoodData().getFoodLevel()));
        lines.add(TextUtil.line("Saturation", player.getFoodData().getSaturationLevel()));

        lines.add(TextUtil.section("Progression"));
        lines.add(TextUtil.line("Experience level", player.experienceLevel));
        lines.add(TextUtil.line("Experience progress", String.format(Locale.ROOT, "%.1f%%", player.experienceProgress * 100.0F)));

        lines.add(TextUtil.section("Abilities"));
        lines.add(TextUtil.line("Flying", player.getAbilities().flying));
        lines.add(TextUtil.line("May fly", player.getAbilities().mayfly));
        lines.add(TextUtil.line("Invulnerable", player.isInvulnerable()));

        lines.add(TextUtil.section("Data"));
        lines.add(TextUtil.listLine("Scoreboard tags", IdentifierCompat.tags(player)));
        lines.add(TextUtil.actionRow(
                TextUtil.copyAction("[Copy name]", MinecraftCompat.playerName(player), "Copy the player name"),
                TextUtil.copyAction("[Copy UUID]", player.getUUID().toString(), "Copy the player UUID"),
                TextUtil.suggestAction("[Inspect again]", "/inspectmc inspect player " + MinecraftCompat.playerName(player),
                        "Prefill the inspection command for this player")
        ));
        return lines;
    }
}
