package com.levelsfr.inspectmc.platform;

import java.util.List;

public interface PlatformBridge {
    String loaderName();

    List<ModDescriptor> loadedMods();
}
