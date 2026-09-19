package com.levelsfr.inspectmc.fabric.client;

import com.levelsfr.inspectmc.client.DumpOpenUtil;
import com.levelsfr.inspectmc.client.config.ClothConfigCompat;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

public final class InspectMCFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("inspectmc-open")
                        .then(ClientCommandManager.literal("file")
                                .then(ClientCommandManager.argument("path", StringArgumentType.word())
                                        .executes(ctx -> open(ctx.getSource(), StringArgumentType.getString(ctx, "path"), false))))
                        .then(ClientCommandManager.literal("folder")
                                .then(ClientCommandManager.argument("path", StringArgumentType.word())
                                        .executes(ctx -> open(ctx.getSource(), StringArgumentType.getString(ctx, "path"), true))))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("inspectmc-config")
                        .executes(ctx -> openConfig())));
    }

    private static int open(FabricClientCommandSource source, String encodedPath, boolean directory) {
        DumpOpenUtil.OpenResult result = DumpOpenUtil.open(encodedPath, directory);
        Component message = Component.literal("[InspectMC] " + result.message());
        if (result.success()) {
            source.sendFeedback(message);
            return 1;
        }
        source.sendError(message);
        return 0;
    }

    private static int openConfig() {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(ClothConfigCompat.createScreenOrUnavailable(client.screen));
        return 1;
    }
}
