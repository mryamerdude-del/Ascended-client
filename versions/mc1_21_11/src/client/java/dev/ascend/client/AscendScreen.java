package dev.ascend.client;

import dev.ascend.client.shared.UiResponse;
import de.keksuccino.rinku.MIMEUtil;
import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.cef.callback.CefCallback;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandler;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

public final class AscendScreen extends Screen {
    private static final String PAGE_URL = "ascend://ascendclient/index.html";
    private static final Logger LOGGER = Logger.getLogger(AscendScreen.class.getName());

    private final Screen parent;
    private RinkuBrowser browser;
    private CefMessageRouter messageRouter;
    private CefMessageRouterHandler messageHandler;
    private volatile boolean pageReady;

    public AscendScreen(Screen parent) {
        super(Component.literal("Ascend Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (browser == null && Rinku.isInitialized()) {
            createBrowser();
        }
        resizeBrowser();
    }

    @Override
    public void tick() {
        super.tick();
        if (browser == null && Rinku.isInitialized()) {
            createBrowser();
        }
    }

    private void createBrowser() {
        Rinku.getApp().getHandle().registerSchemeHandlerFactory(
                "ascend",
                "",
                (cefBrowser, frame, url, request) -> new UiResourceHandler()
        );
        CefClient client = Rinku.getClient().getHandle();
        messageRouter = CefMessageRouter.create();
        messageHandler = new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser cefBrowser, CefFrame frame, long queryId, String request,
                                   boolean persistent, CefQueryCallback callback) {
                if (browser == null || cefBrowser.getIdentifier() != browser.getIdentifier()
                        || frame == null || !frame.isMain() || !PAGE_URL.equals(frame.getURL())) {
                    callback.failure(403, "Ascend accepts UI requests only from its bundled page.");
                    return true;
                }
                try {
                    UiResponse response = AscendClient.application.handle(
                            request,
                            AscendClient.openingKeybindName(),
                            browser.isValid()
                    );
                    pageReady = true;
                    if (response.closeRequested()) {
                        minecraft.execute(() -> {
                            if (minecraft.screen == AscendScreen.this) {
                                minecraft.setScreen(parent);
                            }
                        });
                    }
                    callback.success(response.json());
                } catch (IOException | IllegalArgumentException exception) {
                    callback.failure(400, exception.getMessage());
                }
                return true;
            }
        };
        messageRouter.addHandler(messageHandler, true);
        client.addMessageRouter(messageRouter);
        browser = Rinku.createBrowser(PAGE_URL, true);
        resizeBrowser();
    }

    private void resizeBrowser() {
        if (browser != null) {
            double scale = minecraft.getWindow().getGuiScale();
            browser.resize(Math.max(1, (int) Math.ceil(width * scale)), Math.max(1, (int) Math.ceil(height * scale)));
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        resizeBrowser();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int alpha = (int) Math.round(AscendClient.application.preferences().backgroundDimming() * 255);
        graphics.fill(0, 0, width, height, alpha << 24);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (browser != null && browser.isTextureReady()) {
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                    browser.getTextureIdentifier(),
                    0, 0, 0.0F, 0.0F, width, height, width, height
            );
        } else {
            graphics.drawCenteredString(font, Component.literal(
                    Rinku.isInitializationAllowed()
                            ? "Ascend browser is starting. Please wait..."
                            : "Ascend browser could not be initialized."
            ), width / 2, height / 2, 0xFFE6E2F0);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (browser == null) {
            return true;
        }
        double scale = minecraft.getWindow().getGuiScale();
        browser.sendMousePress((int) (event.x() * scale), (int) (event.y() * scale), event.button());
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (browser == null) {
            return true;
        }
        double scale = minecraft.getWindow().getGuiScale();
        browser.sendMouseRelease((int) (event.x() * scale), (int) (event.y() * scale), event.button());
        browser.setFocus(true);
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null) {
            double scale = minecraft.getWindow().getGuiScale();
            browser.sendMouseMove((int) (mouseX * scale), (int) (mouseY * scale));
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (browser != null) {
            double scale = minecraft.getWindow().getGuiScale();
            browser.sendMouseMove((int) (event.x() * scale), (int) (event.y() * scale));
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (browser != null) {
            double scale = minecraft.getWindow().getGuiScale();
            browser.sendMouseWheel((int) (mouseX * scale), (int) (mouseY * scale), scrollY, 0);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE
                && AscendClient.application.preferences().closeOnEscape()
                && !pageReady) {
            minecraft.setScreen(parent);
            return true;
        }
        if (browser != null) {
            browser.sendKeyPress(event.key(), event.scancode(), event.modifiers());
            browser.setFocus(true);
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (browser != null) {
            browser.sendKeyRelease(event.key(), event.scancode(), event.modifiers());
            browser.setFocus(true);
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (browser != null && event.codepoint() != 0) {
            browser.sendKeyTyped((char) event.codepoint(), 0);
            browser.setFocus(true);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        pageReady = false;
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (messageRouter != null && Rinku.isInitialized()) {
            CefClient client = Rinku.getClient().getHandle();
            client.removeMessageRouter(messageRouter);
            messageRouter.removeHandler(messageHandler);
            messageRouter.dispose();
            messageRouter = null;
            messageHandler = null;
        }
        super.onClose();
    }

    private static final class UiResourceHandler extends CefResourceHandlerAdapter {
        private InputStream stream;
        private String contentType;

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            URI uri;
            try {
                uri = new URI(request.getURL());
            } catch (URISyntaxException exception) {
                callback.cancel();
                return false;
            }

            if (!"ascend".equals(uri.getScheme())
                    || !"ascendclient".equals(uri.getHost())
                    || uri.getPort() != -1
                    || uri.getUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                callback.cancel();
                return false;
            }

            String path = uri.getPath();
            if (path == null || path.length() < 2 || path.indexOf('\\') >= 0) {
                callback.cancel();
                return false;
            }
            String resource = "assets/ascendclient/html/" + path.substring(1);
            for (String segment : path.substring(1).split("/")) {
                if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                    callback.cancel();
                    return false;
                }
            }

            stream = AscendScreen.class.getClassLoader().getResourceAsStream(resource);
            if (stream == null) {
                LOGGER.warning("Ascend UI resource not found: " + resource);
                callback.cancel();
                return false;
            }

            int extension = resource.lastIndexOf('.');
            if (extension >= 0 && extension < resource.length() - 1) {
                contentType = MIMEUtil.mimeFromExtension(resource.substring(extension + 1));
            }
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(CefResponse response, IntRef contentLength, StringRef redirectUrl) {
            if (contentType != null) {
                response.setMimeType(contentType);
            }
            response.setStatus(200);
            response.setStatusText("OK");
            contentLength.set(0);
        }

        @Override
        public boolean readResponse(byte[] output, int bytesToRead, IntRef bytesRead, CefCallback callback) {
            if (stream == null) {
                bytesRead.set(0);
                return false;
            }
            try {
                int count = stream.read(output, 0, bytesToRead);
                if (count <= 0) {
                    closeStream();
                    bytesRead.set(0);
                    return false;
                }
                bytesRead.set(count);
                return true;
            } catch (IOException exception) {
                LOGGER.warning("Failed to read Ascend UI resource: " + exception.getMessage());
                closeStream();
                bytesRead.set(0);
                return false;
            }
        }

        @Override
        public void cancel() {
            closeStream();
        }

        private void closeStream() {
            if (stream == null) {
                return;
            }
            try {
                stream.close();
            } catch (IOException exception) {
                LOGGER.warning("Failed to close Ascend UI resource: " + exception.getMessage());
            } finally {
                stream = null;
            }
        }
    }
}
