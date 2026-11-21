package dev.ascend.client.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.ascend.client.shared.module.ModuleRegistry;
import dev.ascend.client.shared.module.ModuleRegistry.StoredModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AscendApplication {
    /** Schema 2 adds the persisted {@code modules} block; schema 1 files still load. */
    private static final int CONFIG_SCHEMA_VERSION = 2;
    private static final int MAX_CONFIG_BYTES = 262_144;
    private static final System.Logger LOGGER = System.getLogger("Ascend Client");

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private final String minecraftVersion;
    private final ModuleRegistry registry;
    private volatile boolean persistOnChange = true;
    private volatile boolean writing;
    private StoredConfiguration configuration;

    public AscendApplication(Path configPath, String minecraftVersion) throws IOException {
        this(configPath, minecraftVersion, new ModuleRegistry());
    }

    public AscendApplication(Path configPath, String minecraftVersion, ModuleRegistry registry) throws IOException {
        this.configPath = Objects.requireNonNull(configPath, "configPath").toAbsolutePath();
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        this.registry = Objects.requireNonNull(registry, "registry");
        if (minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version cannot be blank.");
        }
        LoadedConfiguration loaded = loadConfiguration();
        registry.restore(loaded.modules());
        this.configuration = new StoredConfiguration(
                CONFIG_SCHEMA_VERSION, loaded.activePage(), loaded.preferences());
        registry.addChangeListener(this::persistQuietly);
    }

    public ModuleRegistry registry() {
        return registry;
    }

    public synchronized UiResponse handle(String request, String openingKeybind, boolean browserReady) throws IOException {
        UiCommand command = UiCommand.parse(request);
        if (command instanceof UiCommand.RequestState) {
            return response(openingKeybind, browserReady, false);
        }
        if (command instanceof UiCommand.Close) {
            return response(openingKeybind, browserReady, true);
        }

        if (command instanceof UiCommand.ToggleModule change) {
            registry.setEnabled(change.id(), change.enabled());
        } else if (command instanceof UiCommand.SetModuleSetting change) {
            registry.setSetting(change.moduleId(), change.settingId(), change.value());
        } else {
            configuration = apply(command);
        }
        persist();
        return response(openingKeybind, browserReady, false);
    }

    public synchronized GuiPreferences preferences() {
        return configuration.preferences();
    }

    /** Writes current UI and module state. Safe to call from the client thread. */
    public synchronized void saveNow() throws IOException {
        persist();
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
                registry.snapshot()
        );
        return new UiResponse(gson.toJson(snapshot), closeRequested);
    }

    private LoadedConfiguration loadConfiguration() throws IOException {
        if (!Files.exists(configPath)) {
            return new LoadedConfiguration(GuiPage.DASHBOARD, GuiPreferences.defaults(), Map.of());
        }
        long size = Files.size(configPath);
        if (size > MAX_CONFIG_BYTES) {
            throw new IOException("Ascend configuration exceeds " + MAX_CONFIG_BYTES + " bytes: " + configPath);
        }

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(Files.readString(configPath, StandardCharsets.UTF_8));
        } catch (JsonParseException exception) {
            throw new IOException("Ascend configuration is invalid: " + configPath, exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException("Ascend configuration must be a JSON object: " + configPath);
        }

        JsonObject stored = parsed.getAsJsonObject();
        Integer schemaVersion = readInteger(stored.get("schemaVersion"));
        if (schemaVersion == null
                || (schemaVersion != 1 && schemaVersion != CONFIG_SCHEMA_VERSION)) {
            throw new IOException("Ascend configuration has an unsupported schema: " + configPath);
        }
        Map<String, StoredModule> modules = schemaVersion >= 2
                ? readModules(stored.get("modules"))
                : Map.of();
        return new LoadedConfiguration(
                readActivePage(stored.get("activePage")),
                readPreferences(stored.get("preferences")),
                modules
        );
    }

    private Map<String, StoredModule> readModules(JsonElement value) {
        if (value == null || !value.isJsonObject()) {
            return Map.of();
        }
        Map<String, StoredModule> modules = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject stored = entry.getValue().getAsJsonObject();
            boolean enabled = Boolean.TRUE.equals(readBoolean(stored.get("enabled")));
            Integer keyCode = readInteger(stored.get("keyCode"));
            modules.put(entry.getKey(), new StoredModule(
                    enabled, keyCode == null ? -1 : keyCode, readSettingValues(stored.get("settings"))));
        }
        return modules;
    }

    private static Map<String, Object> readSettingValues(JsonElement value) {
        if (value == null || !value.isJsonObject()) {
            return Map.of();
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            Object decoded = readRawValue(entry.getValue());
            if (decoded != null) {
                settings.put(entry.getKey(), decoded);
            }
        }
        return settings;
    }

    private static Object readRawValue(JsonElement value) {
        if (value == null) {
            return null;
        }
        if (value.isJsonArray()) {
            List<Double> numbers = new ArrayList<>();
            for (JsonElement element : value.getAsJsonArray()) {
                Double number = readNumber(element);
                if (number == null) {
                    return null;
                }
                numbers.add(number);
            }
            return List.copyOf(numbers);
        }
        String text = readString(value);
        if (text != null) {
            return text;
        }
        Boolean flag = readBoolean(value);
        if (flag != null) {
            return flag;
        }
        return readNumber(value);
    }

    private GuiPreferences readPreferences(JsonElement value) {
        if (value == null || !value.isJsonObject()) {
            return GuiPreferences.defaults();
        }
        JsonObject preferences = value.getAsJsonObject();
        return GuiPreferences.restore(
                readNumber(preferences.get("uiScale")),
                readString(preferences.get("accentColor")),
                readBoolean(preferences.get("animationsEnabled")),
                readNumber(preferences.get("backgroundDimming")),
                readBoolean(preferences.get("closeOnEscape"))
        );
    }

    private GuiPage readActivePage(JsonElement value) {
        try {
            GuiPage page = gson.fromJson(value, GuiPage.class);
            return page == null ? GuiPage.DASHBOARD : page;
        } catch (JsonParseException | IllegalStateException exception) {
            return GuiPage.DASHBOARD;
        }
    }

    private static Integer readInteger(JsonElement value) {
        Double number = readNumber(value);
        if (number == null || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            return null;
        }
        return number.intValue();
    }

    private static Double readNumber(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return null;
        }
        try {
            double number = primitive.getAsDouble();
            return Double.isFinite(number) ? number : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String readString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private static Boolean readBoolean(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                ? value.getAsBoolean() : null;
    }

    private void persistQuietly() {
        if (!persistOnChange) {
            return;
        }
        try {
            persist();
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Ascend could not save its configuration.", exception);
        }
    }

    private synchronized void persist() throws IOException {
        if (writing) {
            return;
        }
        writing = true;
        try {
            StoredConfiguration current = configuration;
            JsonObject stored = new JsonObject();
            stored.addProperty("schemaVersion", CONFIG_SCHEMA_VERSION);
            stored.add("activePage", gson.toJsonTree(current.activePage()));
            stored.add("preferences", gson.toJsonTree(current.preferences()));
            stored.add("modules", gson.toJsonTree(registry.captureState()));
            writeConfiguration(stored);
        } finally {
            writing = false;
        }
    }

    private void writeConfiguration(JsonObject stored) throws IOException {
        Path parent = configPath.getParent();
        if (parent == null) {
            throw new IOException("Ascend configuration path has no parent directory: " + configPath);
        }
        Files.createDirectories(parent);
        Path temporaryPath = parent.resolve(configPath.getFileName() + ".tmp");
        Files.writeString(temporaryPath, gson.toJson(stored), StandardCharsets.UTF_8);
        try {
            Files.move(temporaryPath, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record StoredConfiguration(int schemaVersion, GuiPage activePage, GuiPreferences preferences) {
    }

    private record LoadedConfiguration(GuiPage activePage, GuiPreferences preferences,
                                       Map<String, StoredModule> modules) {
    }
}
