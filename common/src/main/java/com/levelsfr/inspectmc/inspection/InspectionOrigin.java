package com.levelsfr.inspectmc.inspection;

import com.levelsfr.inspectmc.InspectMC;
import com.levelsfr.inspectmc.platform.ModDescriptor;
import com.levelsfr.inspectmc.util.TextUtil;
import com.levelsfr.inspectmc.util.IdentifierCompat;
import net.minecraft.network.chat.Component;

import java.util.List;

final class InspectionOrigin {
    private InspectionOrigin() {
    }

    static void append(List<Component> lines, String id) {
        if (IdentifierCompat.namespace(id).equals("minecraft")) {
            lines.add(TextUtil.line("Provided by", "Minecraft"));
            return;
        }

        InspectMC.platform().loadedMods().stream()
                .filter(mod -> mod.id().equals(IdentifierCompat.namespace(id)))
                .findFirst()
                .ifPresent(mod -> lines.add(TextUtil.line("Provided by", display(mod))));
    }

    private static String display(ModDescriptor mod) {
        return mod.name() + " (" + mod.id() + ") • " + mod.version();
    }
}
