package com.levelsfr.inspectmc.neoforge;

import com.levelsfr.inspectmc.InspectMC;
import com.levelsfr.inspectmc.command.InspectMCCommands;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 26.2 NeoForge entrypoint without the removed FMLEnvironment.dist bridge. */
@Mod(InspectMC.MOD_ID)
public final class InspectMCNeoForge {
    public InspectMCNeoForge() {
        InspectMC.init(new NeoForgePlatformBridge());
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        InspectMCCommands.register(event.getDispatcher(), event.getBuildContext());
    }
}
