package com.levelsfr.inspectmc.fabric.client;

import com.levelsfr.inspectmc.client.config.ClothConfigCompat;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class InspectMCModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClothConfigCompat::createScreenOrUnavailable;
    }
}
