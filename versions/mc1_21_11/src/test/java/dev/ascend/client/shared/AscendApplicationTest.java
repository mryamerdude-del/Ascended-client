package dev.ascend.client.shared;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AscendApplicationTest {
    private static final Gson GSON = new Gson();

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsPageAndPreferencesAcrossApplicationInstances() throws Exception {
        Path config = temporaryDirectory.resolve("ascendclient/settings.json");
        AscendApplication first = new AscendApplication(config, "1.21.11");

        first.handle("{\"action\":\"navigate\",\"page\":\"appearance\"}", "Right Shift", true);
        first.handle("{\"action\":\"setting\",\"name\":\"accentColor\",\"value\":\"#5B9DFF\"}", "Right Shift", true);
        first.handle("{\"action\":\"setting\",\"name\":\"uiScale\",\"value\":1.1}", "Right Shift", true);

        AscendApplication restored = new AscendApplication(config, "1.21.11");
        GuiSnapshot snapshot = GSON.fromJson(
                restored.handle("{\"action\":\"state\"}", "Right Shift", true).json(),
                GuiSnapshot.class
        );

        assertEquals("appearance", snapshot.activePage());
        assertEquals("#5B9DFF", snapshot.preferences().accentColor());
        assertEquals(1.1, snapshot.preferences().uiScale());
    }

    @Test
    void rejectsUnknownFieldsAndInvalidSettingsWithoutChangingState() throws Exception {
        AscendApplication application = new AscendApplication(
                temporaryDirectory.resolve("settings.json"),
                "1.21.11"
        );

        assertThrows(IllegalArgumentException.class,
                () -> application.handle("{\"action\":\"state\",\"extra\":true}", "Right Shift", true));
        assertThrows(IllegalArgumentException.class,
                () -> application.handle("{\"action\":\"setting\",\"name\":\"uiScale\",\"value\":9}", "Right Shift", true));

        GuiSnapshot snapshot = GSON.fromJson(
                application.handle("{\"action\":\"state\"}", "Right Shift", true).json(),
                GuiSnapshot.class
        );
        assertEquals(1.0, snapshot.preferences().uiScale());
        assertFalse(java.nio.file.Files.exists(temporaryDirectory.resolve("settings.json")));
    }

    @Test
    void rejectsModuleTogglesUntilARealModuleIsRegistered() throws Exception {
        AscendApplication application = new AscendApplication(
                temporaryDirectory.resolve("settings.json"),
                "1.21.11"
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> application.handle(
                        "{\"action\":\"module\",\"id\":\"not-registered\",\"enabled\":true}",
                        "Right Shift",
                        true
                ));

        assertTrue(error.getMessage().contains("No module is registered"));
    }
}
