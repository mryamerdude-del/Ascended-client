package dev.ascend.client;

import dev.ascend.client.shared.AscendApplication;
import dev.ascend.client.shared.AscendBootstrap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;

public final class AscendClient implements ClientModInitializer {
    private static final System.Logger LOGGER = System.getLogger("Ascend Client");
    static AscendApplication application;
    static KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        LOGGER.log(System.Logger.Level.INFO, AscendBootstrap.initializationMessage("1.21.11"));
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("ascendclient/settings.json");
        try {
            application = new AscendApplication(configPath, "1.21.11");
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Ascend GUI is unavailable because its configuration could not be loaded.", exception);
            return;
        }

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.ascendclient.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (!(client.screen instanceof AscendScreen)) {
                    client.setScreen(new AscendScreen(client.screen));
                }
            }
        });
    }

    static String openingKeybindName() {
        return openGuiKey == null ? "Right Shift" : openGuiKey.getTranslatedKeyMessage().getString();
    }
}
