package com.jonnypog.sharepoint;

import net.fabricmc.api.ClientModInitializer;

public class SharepointClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SharepointMod.LOGGER.info("Sharepoint client initialized.");
        // TODO: Register client UI/hooks for Lunar waypoint sharing here.
    }
}
