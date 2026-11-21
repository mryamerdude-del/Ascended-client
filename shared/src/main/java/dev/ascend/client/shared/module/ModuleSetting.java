package dev.ascend.client.shared.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A single module setting. State, metadata, validation and the visibility rule
 * live here so the GUI and persistence never need Minecraft imports.
 *
 * <p>Instances are mutable by design: modules hold a field reference to each
 * setting and read its current value while running. The registry only mutates
 * settings through {@link #applyValue(Object)}, which validates first.
 */
public final class ModuleSetting {
    public enum Type {
        BOOLEAN("boolean"),
        NUMBER("number"),
        CHOICE("choice"),
        TEXT("text"),
        SLOTS("slots");

        private final String wireName;

        Type(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int MAX_TEXT_LENGTH = 1024;

    private final String id;
    private final String name;
    private final Type type;
    private final List<String> options;
    private final double minimum;
    private final double maximum;
    private final double step;
    private final String unit;
    private final boolean defaultValue;
    private final double defaultNumber;
    private final String defaultText;

    private String group = "";
    private String description = "";
    private String visibleSettingId;
    private String visibleValue;
    private boolean visibleWhenEqual = true;
    private boolean allowOutOfRange;

    private Object value;

    private ModuleSetting(String id, String name, Type type, List<String> options,
                          double minimum, double maximum, double step, String unit,
                          boolean defaultValue, double defaultNumber, String defaultText) {
        this.id = requireId(id, "setting id");
        this.name = Objects.requireNonNull(name, "name");
        this.type = type;
        this.options = List.copyOf(options);
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.unit = unit == null ? "" : unit;
        this.defaultValue = defaultValue;
        this.defaultNumber = defaultNumber;
        this.defaultText = defaultText == null ? "" : defaultText;
        this.value = type == Type.SLOTS ? List.<Integer>of() : type == Type.NUMBER
                ? (Object) defaultNumber : type == Type.BOOLEAN ? (Object) defaultValue : defaultText;
    }

    public static ModuleSetting bool(String id, String name, boolean defaultValue) {
        return new ModuleSetting(id, name, Type.BOOLEAN, List.of(), 0, 0, 0, "",
                defaultValue, 0, "");
    }

    public static ModuleSetting number(String id, String name, double defaultValue,
                                       double minimum, double maximum, double step, String unit) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Invalid numeric range for setting: " + id);
        }
        if (!Double.isFinite(step) || step < 0) {
            throw new IllegalArgumentException("Invalid step for setting: " + id);
        }
        if (!Double.isFinite(defaultValue) || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("Default value is outside the range for setting: " + id);
        }
        return new ModuleSetting(id, name, Type.NUMBER, List.of(), minimum, maximum, step, unit,
                false, defaultValue, "");
    }

    public static ModuleSetting choice(String id, String name, String defaultValue, String... options) {
        List<String> values = new ArrayList<>();
        for (String option : options) {
            if (option == null || option.isBlank()) {
                throw new IllegalArgumentException("Choice option cannot be blank: " + id);
            }
            values.add(option);
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Choice setting requires at least one option: " + id);
        }
        String canonical = canonicalChoice(values, defaultValue);
        if (canonical == null) {
            throw new IllegalArgumentException("Default choice is not an option for setting: " + id);
        }
        return new ModuleSetting(id, name, Type.CHOICE, values, 0, 0, 0, "",
                false, 0, canonical);
    }

    public static ModuleSetting text(String id, String name, String defaultValue) {
        String initial = defaultValue == null ? "" : defaultValue;
        if (initial.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Default text is too long for setting: " + id);
        }
        return new ModuleSetting(id, name, Type.TEXT, List.of(), 0, 0, 0, "",
                false, 0, initial);
    }

    public static ModuleSetting slots(String id, String name) {
        return new ModuleSetting(id, name, Type.SLOTS, List.of(), 0, HOTBAR_SLOT_COUNT - 1, 1, "",
                false, 0, "");
    }

    public ModuleSetting group(String value) {
        this.group = value == null ? "" : value;
        return this;
    }

    public ModuleSetting description(String value) {
        this.description = value == null ? "" : value;
        return this;
    }

    /** Visible only while the referenced choice setting equals {@code expected}. */
    public ModuleSetting showWhen(String settingId, String expected) {
        this.visibleSettingId = Objects.requireNonNull(settingId, "settingId");
        this.visibleValue = Objects.requireNonNull(expected, "expected");
        this.visibleWhenEqual = true;
        return this;
    }

    /** Visible only while the referenced choice setting does not equal {@code unexpected}. */
    public ModuleSetting hideWhen(String settingId, String unexpected) {
        this.visibleSettingId = Objects.requireNonNull(settingId, "settingId");
        this.visibleValue = Objects.requireNonNull(unexpected, "unexpected");
        this.visibleWhenEqual = false;
        return this;
    }

    /**
     * Mirrors Echo's {@code FloatSetting.setAllowOutOfRange(true)}: values are
     * still validated as finite numbers but are not clamped to the slider range.
     */
    public ModuleSetting allowOutOfRange() {
        this.allowOutOfRange = true;
        return this;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Type type() {
        return type;
    }

    public String group() {
        return group;
    }

    public String description() {
        return description;
    }

    public List<String> options() {
        return options;
    }

    public double minimum() {
        return minimum;
    }

    public double maximum() {
        return maximum;
    }

    public double step() {
        return step;
    }

    public String unit() {
        return unit;
    }

    public Object value() {
        return value;
    }

    public boolean isVisible(AscendModule owner) {
        if (visibleSettingId == null) {
            return true;
        }
        ModuleSetting dependency = owner == null ? null : owner.findSetting(visibleSettingId);
        if (dependency == null) {
            return true;
        }
        boolean equal = dependency.type() == Type.CHOICE
                && sameText(dependency.asChoice(), visibleValue);
        return visibleWhenEqual == equal;
    }

    /**
     * Validates and stores {@code requested}. Accepts the Gson-decoded primitive
     * shapes the GUI bridge produces.
     */
    public void applyValue(Object requested) {
        switch (type) {
            case BOOLEAN -> {
                if (!(requested instanceof Boolean flag)) {
                    throw new IllegalArgumentException(name + " must be a boolean.");
                }
                value = flag;
            }
            case NUMBER -> {
                double number = requireNumber(requested, name);
                if (!allowOutOfRange && (number < minimum || number > maximum)) {
                    throw new IllegalArgumentException(name + " must be between "
                            + format(minimum) + " and " + format(maximum) + ".");
                }
                value = number;
            }
            case CHOICE -> {
                if (!(requested instanceof String text)) {
                    throw new IllegalArgumentException(name + " must be a choice.");
                }
                String canonical = canonicalChoice(options, text);
                if (canonical == null) {
                    throw new IllegalArgumentException(name + " has no option named " + text + ".");
                }
                value = canonical;
            }
            case TEXT -> {
                if (!(requested instanceof String text)) {
                    throw new IllegalArgumentException(name + " must be text.");
                }
                if (text.length() > MAX_TEXT_LENGTH) {
                    throw new IllegalArgumentException(name + " exceeds " + MAX_TEXT_LENGTH + " characters.");
                }
                value = text;
            }
            case SLOTS -> value = readSlots(requested, name);
        }
    }

    public boolean asBoolean() {
        return type == Type.BOOLEAN && Boolean.TRUE.equals(value);
    }

    public double asNumber() {
        return type == Type.NUMBER && value instanceof Number number ? number.doubleValue() : 0.0;
    }

    public String asChoice() {
        return type == Type.CHOICE && value instanceof String text ? text : "";
    }

    public String asText() {
        return type == Type.TEXT && value instanceof String text ? text : "";
    }

    public boolean isChoice(String option) {
        return type == Type.CHOICE && sameText(asChoice(), option);
    }

    public boolean isSlotSelected(int slot) {
        if (type != Type.SLOTS || slot < 0 || slot >= HOTBAR_SLOT_COUNT || !(value instanceof List<?> list)) {
            return false;
        }
        for (Object entry : list) {
            if (entry instanceof Integer selected && selected == slot) {
                return true;
            }
        }
        return false;
    }

    /** Slots selected by this setting as a comma-separated list, for persistence. */
    public String slotsAsText() {
        if (!(value instanceof List<?> list)) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Object entry : list) {
            joiner.add(String.valueOf(entry));
        }
        return joiner.toString();
    }

    /** Restores the compile-time default for this setting. */
    public void reset() {
        value = type == Type.SLOTS ? List.<Integer>of() : type == Type.NUMBER
                ? (Object) defaultNumber : type == Type.BOOLEAN ? (Object) defaultValue : defaultText;
    }

    public boolean isDefault() {
        return switch (type) {
            case BOOLEAN -> asBoolean() == defaultValue;
            case NUMBER -> Double.compare(asNumber(), defaultNumber) == 0;
            case CHOICE, TEXT -> sameText(asChoiceOrText(), defaultText);
            case SLOTS -> value instanceof List<?> list && list.isEmpty();
        };
    }

    private String asChoiceOrText() {
        return type == Type.CHOICE ? asChoice() : asText();
    }

    private static List<Integer> readSlots(Object requested, String name) {
        List<Integer> slots = new ArrayList<>();
        if (requested instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Number number)) {
                    throw new IllegalArgumentException(name + " must contain slot numbers.");
                }
                int slot = number.intValue();
                if (slot < 0 || slot >= HOTBAR_SLOT_COUNT || number.doubleValue() != slot) {
                    throw new IllegalArgumentException(name + " slot must be between 0 and 8.");
                }
                if (!slots.contains(slot)) {
                    slots.add(slot);
                }
            }
        } else if (requested instanceof String text) {
            for (String part : text.split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int slot;
                try {
                    slot = Integer.parseInt(trimmed);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(name + " must contain slot numbers.");
                }
                if (slot < 0 || slot >= HOTBAR_SLOT_COUNT) {
                    throw new IllegalArgumentException(name + " slot must be between 0 and 8.");
                }
                if (!slots.contains(slot)) {
                    slots.add(slot);
                }
            }
        } else {
            throw new IllegalArgumentException(name + " must be a list of slots.");
        }
        Collections.sort(slots);
        return List.copyOf(slots);
    }

    private static double requireNumber(Object requested, String name) {
        if (!(requested instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be a number.");
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
        return value;
    }

    private static String canonicalChoice(List<String> options, String requested) {
        if (requested == null) {
            return null;
        }
        for (String option : options) {
            if (sameText(option, requested)) {
                return option;
            }
        }
        return null;
    }

    private static boolean sameText(String first, String second) {
        return first != null && second != null
                && first.toLowerCase(Locale.ROOT).equals(second.toLowerCase(Locale.ROOT));
    }

    private static String requireId(String id, String label) {
        if (id == null || id.isBlank() || id.length() > 80
                || !id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid " + label + ": " + id);
        }
        return id;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
