package dev.ascend.client.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

public final class AscendApplication {
    private static final int CONFIG_SCHEMA_VERSION = 1;
    private static final int MAX_CONFIG_BYTES = 16_384;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private final String minecraftVersion;
    private StoredConfiguration configuration;

    public AscendApplication(Path configPath, String minecraftVersion) throws IOException {
        this.configPath = Objects.requireNonNull(configPath, "configPath").toAbsolutePath();
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        if (minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version cannot be blank.");
        }
        this.configuration = loadConfiguration();
    }

    public synchronized UiResponse handle(String request, String openingKeybind, boolean browserReady) throws IOException {
        UiCommand command = UiCommand.parse(request);
        if (command instanceof UiCommand.RequestState) {
            return response(openingKeybind, browserReady, false);
        }
        if (command instanceof UiCommand.Close) {
            return response(openingKeybind, browserReady, true);
        }

        StoredConfiguration updated = apply(command);
        writeConfiguration(updated);
        configuration = updated;
        return response(openingKeybind, browserReady, false);
    }

    public synchronized GuiPreferences preferences() {
        return configuration.preferences();
    }

    private StoredConfiguration apply(UiCommand command) {
        GuiPage page = configuration.activePage();
        GuiPreferences preferences = configuration.preferences();

        if (command instanceof UiCommand.Navigate navigate) {
            page = navigate.page();
        } else if (command instanceof UiCommand.SetUiScale change) {
            preferences = preferences.withUiScale(change.value());
        } else if (command instanceof UiCommand.SetAccentColor change) {
            preferences = preferences.withAccentColor(change.value());
        } else if (command instanceof UiCommand.SetAnimationsEnabled change) {
            preferences = preferences.withAnimationsEnabled(change.value());
        } else if (command instanceof UiCommand.SetBackgroundDimming change) {
            preferences = preferences.withBackgroundDimming(change.value());
        } else if (command instanceof UiCommand.SetCloseOnEscape change) {
            preferences = preferences.withCloseOnEscape(change.value());
        } else if (command instanceof UiCommand.ToggleModule change) {
            throw new IllegalArgumentException("No module is registered with id: " + change.id());
        } else {
            throw new IllegalArgumentException("Unsupported UI command.");
        }

        return new StoredConfiguration(CONFIG_SCHEMA_VERSION, page, preferences);
    }

    private UiResponse response(String openingKeybind, boolean browserReady, boolean closeRequested) {
        GuiSnapshot snapshot = new GuiSnapshot(
                minecraftVersion,
                browserReady ? "ready" : "starting",
                configuration.activePage().wireName(),
                configuration.preferences(),
                Objects.requireNonNullElse(openingKeybind, "Unknown"),
                List.of()
        );
        return new UiResponse(gson.toJson(snapshot), closeRequested);
    }

    private StoredConfiguration loadConfiguration() throws IOException {
        if (!Files.exists(configPath)) {
            return defaults();
        }
        long size = Files.size(configPath);
        if (size > MAX_CONFIG_BYTES) {
            throw new IOException("Ascend configuration exceeds " + MAX_CONFIG_BYTES + " bytes: " + configPath);
        }

        try {
            StoredConfiguration loaded = gson.fromJson(
                    Files.readString(configPath, StandardCharsets.UTF_8),
                    StoredConfiguration.class
            );
            if (loaded == null || loaded.schemaVersion() != CONFIG_SCHEMA_VERSION
                    || loaded.activePage() == null || loaded.preferences() == null) {
                throw new IOException("Ascend configuration has an unsupported or incomplete schema: " + configPath);
            }
            return loaded;
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new IOException("Ascend configuration is invalid: " + configPath, exception);
        }
    }

    private void writeConfiguration(StoredConfiguration updated) throws IOException {
        Path parent = configPath.getParent();
        if (parent == null) {
            throw new IOException("Ascend configuration path has no parent directory: " + configPath);
        }
        Files.createDirectories(parent);
        Path temporaryPath = parent.resolve(configPath.getFileName() + ".tmp");
        Files.writeString(temporaryPath, gson.toJson(updated), StandardCharsets.UTF_8);
        try {
            Files.move(temporaryPath, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static StoredConfiguration defaults() {
        return new StoredConfiguration(CONFIG_SCHEMA_VERSION, GuiPage.DASHBOARD, GuiPreferences.defaults());
    }

    private record StoredConfiguration(int schemaVersion, GuiPage activePage, GuiPreferences preferences) {
    }
}
