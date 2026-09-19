package com.levelsfr.inspectmc.fabric;

import com.levelsfr.inspectmc.InspectMC;
import com.levelsfr.inspectmc.command.InspectMCCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class InspectMCFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        InspectMC.init(new FabricPlatformBridge());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                InspectMCCommands.register(dispatcher, registryAccess));
    }
}
