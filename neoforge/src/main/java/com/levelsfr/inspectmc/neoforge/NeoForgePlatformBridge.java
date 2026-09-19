package com.levelsfr.inspectmc.neoforge;

import com.levelsfr.inspectmc.platform.ModDescriptor;
import com.levelsfr.inspectmc.platform.PlatformBridge;
import net.neoforged.fml.ModList;

import java.util.List;

public final class NeoForgePlatformBridge implements PlatformBridge {
    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public List<ModDescriptor> loadedMods() {
        return ModList.get().getMods().stream()
                .map(info -> new ModDescriptor(info.getModId(), info.getDisplayName(), info.getVersion().toString()))
                .toList();
    }
}
