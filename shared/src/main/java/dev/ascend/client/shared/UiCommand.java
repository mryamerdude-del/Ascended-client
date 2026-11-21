package dev.ascend.client.shared;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Set;

public sealed interface UiCommand permits UiCommand.RequestState, UiCommand.Navigate,
        UiCommand.SetUiScale, UiCommand.SetAccentColor, UiCommand.SetAnimationsEnabled,
        UiCommand.SetBackgroundDimming, UiCommand.SetCloseOnEscape, UiCommand.ToggleModule,
        UiCommand.SetModuleSetting, UiCommand.Close {

    record RequestState() implements UiCommand {
    }

    record Navigate(GuiPage page) implements UiCommand {
    }

    record SetUiScale(double value) implements UiCommand {
    }

    record SetAccentColor(String value) implements UiCommand {
    }

    record SetAnimationsEnabled(boolean value) implements UiCommand {
    }

    record SetBackgroundDimming(double value) implements UiCommand {
    }

    record SetCloseOnEscape(boolean value) implements UiCommand {
    }

    record ToggleModule(String id, boolean enabled) implements UiCommand {
    }

    record SetModuleSetting(String moduleId, String settingId, Object value) implements UiCommand {
    }

    record Close() implements UiCommand {
    }

    static UiCommand parse(String json) {
        if (json == null || json.length() > 8192) {
            throw new IllegalArgumentException("UI request is missing or exceeds 8192 characters.");
        }

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("UI request is not valid JSON.", exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("UI request must be a JSON object.");
        }

        JsonObject object = parsed.getAsJsonObject();
        if (!object.has("action") || !object.get("action").isJsonPrimitive()
                || !object.get("action").getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("UI request requires a string action.");
        }

        String action = object.get("action").getAsString();
        return switch (action) {
            case "state" -> {
                requireKeys(object, Set.of("action"));
                yield new RequestState();
            }
            case "navigate" -> {
                requireKeys(object, Set.of("action", "page"));
                yield new Navigate(GuiPage.fromWireName(requireString(object, "page")));
            }
            case "setting" -> parseSetting(object);
            case "module" -> {
                requireKeys(object, Set.of("action", "id", "enabled"));
                String id = requireString(object, "id");
                if (id.isBlank() || id.length() > 80) {
                    throw new IllegalArgumentException("Module id is invalid.");
                }
                yield new ToggleModule(id, requireBoolean(object, "enabled"));
            }
            case "moduleSetting" -> {
                requireKeys(object, Set.of("action", "module", "setting", "value"));
                yield new SetModuleSetting(
                        requireIdentifier(object, "module"),
                        requireIdentifier(object, "setting"),
                        readRawValue(object.get("value")));
            }
            case "close" -> {
                requireKeys(object, Set.of("action"));
                yield new Close();
            }
            default -> throw new IllegalArgumentException("Unsupported UI action: " + action);
        };
    }

    private static UiCommand parseSetting(JsonObject object) {
        requireKeys(object, Set.of("action", "name", "value"));
        String name = requireString(object, "name");
        JsonElement value = object.get("value");
        return switch (name) {
            case "uiScale" -> new SetUiScale(requireNumber(value, name));
            case "accentColor" -> new SetAccentColor(requireString(value, name));
            case "animationsEnabled" -> new SetAnimationsEnabled(requireBoolean(value, name));
            case "backgroundDimming" -> new SetBackgroundDimming(requireNumber(value, name));
            case "closeOnEscape" -> new SetCloseOnEscape(requireBoolean(value, name));
            default -> throw new IllegalArgumentException("Unsupported setting: " + name);
        };
    }

    private static void requireKeys(JsonObject object, Set<String> expected) {
        if (!object.keySet().equals(expected)) {
            throw new IllegalArgumentException("UI request has missing or unsupported fields.");
        }
    }

    private static String requireIdentifier(JsonObject object, String key) {
        String value = requireString(object, key);
        if (value.isBlank() || value.length() > 80) {
            throw new IllegalArgumentException(key + " is invalid.");
        }
        return value;
    }

    /** Decodes a setting payload into the primitive shapes ModuleSetting expects. */
    private static Object readRawValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("value is missing.");
        }
        if (value.isJsonArray()) {
            java.util.List<Double> numbers = new java.util.ArrayList<>();
            for (JsonElement element : value.getAsJsonArray()) {
                numbers.add(requireNumber(element, "value"));
            }
            return java.util.List.copyOf(numbers);
        }
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException("value must be a primitive or a list of numbers.");
        }
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return requireNumber(value, "value");
        }
        return primitive.getAsString();
    }

    private static String requireString(JsonObject object, String key) {
        return requireString(object.get(key), key);
    }

    private static String requireString(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a string.");
        }
        return value.getAsString();
    }

    private static boolean requireBoolean(JsonObject object, String key) {
        return requireBoolean(object.get(key), key);
    }

    private static boolean requireBoolean(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean.");
        }
        return value.getAsBoolean();
    }

    private static double requireNumber(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a number.");
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
        return number;
    }
}
