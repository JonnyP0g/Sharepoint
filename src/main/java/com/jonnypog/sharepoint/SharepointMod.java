package com.jonnypog.sharepoint;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharepointMod implements ModInitializer {
    public static final String MOD_ID = "sharepoint";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Sharepoint initialized.");
        // TODO: Register common waypoint sharing components here.
    }
}
