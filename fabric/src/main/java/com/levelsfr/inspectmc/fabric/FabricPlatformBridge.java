package com.levelsfr.inspectmc.fabric;

import com.levelsfr.inspectmc.platform.ModDescriptor;
import com.levelsfr.inspectmc.platform.PlatformBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

public final class FabricPlatformBridge implements PlatformBridge {
    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public List<ModDescriptor> loadedMods() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(container -> new ModDescriptor(
                        container.getMetadata().getId(),
                        container.getMetadata().getName(),
                        container.getMetadata().getVersion().getFriendlyString()
                ))
                .toList();
    }
}
