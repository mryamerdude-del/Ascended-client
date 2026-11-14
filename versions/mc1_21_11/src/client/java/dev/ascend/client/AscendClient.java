package dev.ascend.client;

import dev.ascend.client.shared.AscendBootstrap;
import net.fabricmc.api.ClientModInitializer;

public final class AscendClient implements ClientModInitializer {
    private static final System.Logger LOGGER = System.getLogger("Ascend Client");

    @Override
    public void onInitializeClient() {
        LOGGER.log(System.Logger.Level.INFO, AscendBootstrap.initializationMessage("1.21.11"));
    }
}
