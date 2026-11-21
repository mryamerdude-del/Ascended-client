package dev.ascend.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.keksuccino.rinku.Rinku;
import dev.ascend.client.shared.AscendApplication;
import dev.ascend.client.shared.AscendBootstrap;
import dev.ascend.client.shared.combat.AimAssistModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.cef.CefClient;
import org.cef.browser.CefMessageRouter;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.io.IOException;
import java.nio.file.Path;

public final class AscendClient implements ClientModInitializer {
    private static final System.Logger LOGGER = System.getLogger("Ascend Client");
    static AscendApplication application;
    static KeyMapping openGuiKey;
    private static CefMessageRouter uiMessageRouter;

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
        application.registry().register(new AimAssistModule(new AscendAimView(Minecraft.getInstance())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (!(client.gui.screen() instanceof AscendScreen)) {
                    client.gui.setScreen(new AscendScreen(client.gui.screen()));
                }
            }
            AscendRotations.tick(client.player);
            application.registry().tick();
        });
        if (Rinku.isInitialized()) {
            registerUiMessageRouter(true);
        } else {
            Rinku.scheduleForInit(AscendClient::registerUiMessageRouter);
        }
    }

    private static void registerUiMessageRouter(boolean initialized) {
        if (!initialized) {
            LOGGER.log(System.Logger.Level.ERROR, "Ascend UI bridge is unavailable because Rinku failed to initialize.");
            return;
        }
        if (uiMessageRouter != null) {
            return;
        }

        uiMessageRouter = CefMessageRouter.create();
        uiMessageRouter.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(org.cef.browser.CefBrowser browser, org.cef.browser.CefFrame frame,
                                   long queryId, String request, boolean persistent,
                                   org.cef.callback.CefQueryCallback callback) {
                return AscendScreen.routeUiRequest(browser, frame, request, callback);
            }
        }, true);
        CefClient client = Rinku.getClient().getHandle();
        client.addMessageRouter(uiMessageRouter);
    }

    static String openingKeybindName() {
        return openGuiKey == null ? "Right Shift" : openGuiKey.getTranslatedKeyMessage().getString();
    }
}
