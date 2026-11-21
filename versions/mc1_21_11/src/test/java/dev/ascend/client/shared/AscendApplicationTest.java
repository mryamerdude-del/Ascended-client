package dev.ascend.client.shared;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        assertThrows(IllegalArgumentException.class,
                () -> application.handle("{\"action\":\"setting\",\"name\":\"uiScale\",\"value\":0.74}", "Right Shift", true));
        assertThrows(IllegalArgumentException.class,
                () -> application.handle("{\"action\":\"setting\",\"name\":\"uiScale\",\"value\":1.26}", "Right Shift", true));

        GuiSnapshot snapshot = GSON.fromJson(
                application.handle("{\"action\":\"state\"}", "Right Shift", true).json(),
                GuiSnapshot.class
        );
        assertEquals(1.0, snapshot.preferences().uiScale());
        assertFalse(java.nio.file.Files.exists(temporaryDirectory.resolve("settings.json")));
    }

    @Test
    void persistsMinimumAndMaximumScaleAcrossApplicationInstances() throws Exception {
        Path config = temporaryDirectory.resolve("settings.json");
        AscendApplication application = new AscendApplication(config, "1.21.11");

        application.handle("{\"action\":\"setting\",\"name\":\"uiScale\",\"value\":0.75}", "Right Shift", true);
        AscendApplication restoredMinimum = new AscendApplication(config, "1.21.11");
        assertEquals(0.75, restoredMinimum.preferences().uiScale());

        restoredMinimum.handle("{\"action\":\"setting\",\"name\":\"uiScale\",\"value\":1.25}", "Right Shift", true);
        AscendApplication restoredMaximum = new AscendApplication(config, "1.21.11");
        assertEquals(1.25, restoredMaximum.preferences().uiScale());
    }

    @Test
    void defaultsMissingAndInvalidStoredScaleWithoutDiscardingValidPreferences() throws Exception {
        for (String invalidScale : new String[]{"missing", "\"invalid\"", "0.5", "1.5"}) {
            Path config = temporaryDirectory.resolve(invalidScale.replaceAll("[^a-zA-Z0-9]", "") + ".json");
            JsonObject stored = JsonParser.parseString("""
                    {
                      "schemaVersion": 1,
                      "activePage": "APPEARANCE",
                      "preferences": {
                        "uiScale": 1,
                        "accentColor": "#5B9DFF",
                        "animationsEnabled": true,
                        "backgroundDimming": 0.3,
                        "closeOnEscape": true
                      }
                    }
                    """).getAsJsonObject();
            JsonObject preferences = stored.getAsJsonObject("preferences");
            if ("missing".equals(invalidScale)) {
                preferences.remove("uiScale");
            } else if (invalidScale.startsWith("\"")) {
                preferences.addProperty("uiScale", "invalid");
            } else {
                preferences.addProperty("uiScale", Double.parseDouble(invalidScale));
            }
            java.nio.file.Files.writeString(config, GSON.toJson(stored));

            AscendApplication restored = new AscendApplication(config, "1.21.11");
            assertEquals(1.0, restored.preferences().uiScale(), invalidScale);
            assertEquals("#5B9DFF", restored.preferences().accentColor(), invalidScale);
            assertEquals(0.3, restored.preferences().backgroundDimming(), invalidScale);
        }
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
