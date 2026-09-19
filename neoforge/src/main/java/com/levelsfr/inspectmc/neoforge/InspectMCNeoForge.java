package com.levelsfr.inspectmc.neoforge;

import com.levelsfr.inspectmc.InspectMC;
import com.levelsfr.inspectmc.command.InspectMCCommands;
import com.levelsfr.inspectmc.client.config.ClothConfigCompat;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(InspectMC.MOD_ID)
public final class InspectMCNeoForge {
    public InspectMCNeoForge(ModContainer modContainer) {
        InspectMC.init(new NeoForgePlatformBridge());
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        if (FMLEnvironment.dist.isClient() && ClothConfigCompat.isAvailable()) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                    (container, parent) -> ClothConfigCompat.createScreen(parent));
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        InspectMCCommands.register(event.getDispatcher(), event.getBuildContext());
    }
}
