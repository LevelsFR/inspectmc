package com.levelsfr.inspectmc;

import com.levelsfr.inspectmc.platform.PlatformBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class InspectMC {
    public static final String MOD_ID = "inspectmc";
    public static final String MOD_NAME = "InspectMC";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static PlatformBridge platform;

    private InspectMC() {
    }

    public static void init(PlatformBridge bridge) {
        platform = Objects.requireNonNull(bridge, "platform");
        LOGGER.info("Initializing {} on {}", MOD_NAME, bridge.loaderName());
    }

    public static PlatformBridge platform() {
        if (platform == null) {
            throw new IllegalStateException("InspectMC platform bridge has not been initialized");
        }
        return platform;
    }
}
