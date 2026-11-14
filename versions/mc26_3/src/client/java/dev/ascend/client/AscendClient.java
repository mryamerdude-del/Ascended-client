package dev.ascend.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ascend.client.shared.AscendApplication;
import dev.ascend.client.shared.AscendBootstrap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

import java.io.IOException;
import java.nio.file.Path;

public final class AscendClient implements ClientModInitializer {
    private static final System.Logger LOGGER = System.getLogger("Ascend Client");
    static AscendApplication application;
    static KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        LOGGER.log(System.Logger.Level.INFO, AscendBootstrap.initializationMessage("26.3"));
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("ascendclient/settings.json");
        try {
            application = new AscendApplication(configPath, "26.3");
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Ascend GUI is unavailable because its configuration could not be loaded.", exception);
            return;
        }

        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ascendclient.open_gui",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RSHIFT,
                KeyMapping.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (!(client.gui.screen() instanceof AscendScreen)) {
                    client.gui.setScreen(new AscendScreen(client.gui.screen()));
                }
            }
        });
    }

    static String openingKeybindName() {
        return openGuiKey == null ? "Right Shift" : openGuiKey.getTranslatedKeyMessage().getString();
    }
}
