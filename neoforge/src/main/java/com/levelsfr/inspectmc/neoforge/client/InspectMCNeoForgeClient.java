package com.levelsfr.inspectmc.neoforge.client;

import com.levelsfr.inspectmc.InspectMC;
import com.levelsfr.inspectmc.client.DumpOpenUtil;
import com.levelsfr.inspectmc.client.config.ClothConfigCompat;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = InspectMC.MOD_ID, value = Dist.CLIENT)
public final class InspectMCNeoForgeClient {
    private InspectMCNeoForgeClient() {
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("inspectmc-open")
                .then(Commands.literal("file")
                        .then(Commands.argument("path", StringArgumentType.word())
                                .executes(ctx -> open(ctx.getSource(), StringArgumentType.getString(ctx, "path"), false))))
                .then(Commands.literal("folder")
                        .then(Commands.argument("path", StringArgumentType.word())
                                .executes(ctx -> open(ctx.getSource(), StringArgumentType.getString(ctx, "path"), true)))));
        event.getDispatcher().register(Commands.literal("inspectmc-config")
                .executes(ctx -> openConfig()));
    }

    private static int open(CommandSourceStack source, String encodedPath, boolean directory) {
        DumpOpenUtil.OpenResult result = DumpOpenUtil.open(encodedPath, directory);
        if (result.success()) {
            source.sendSuccess(() -> Component.literal("[InspectMC] " + result.message()), false);
            return 1;
        }
        source.sendFailure(Component.literal("[InspectMC] " + result.message()));
        return 0;
    }

    private static int openConfig() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(ClothConfigCompat.createScreenOrUnavailable(client.screen));
        return 1;
    }
}
