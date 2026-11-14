package dev.ascend.client.shared;

import java.util.regex.Pattern;

public record GuiPreferences(
        double uiScale,
        String accentColor,
        boolean animationsEnabled,
        double backgroundDimming,
        boolean closeOnEscape
) {
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    public GuiPreferences {
        if (!Double.isFinite(uiScale) || uiScale < 0.8 || uiScale > 1.2) {
            throw new IllegalArgumentException("UI scale must be between 0.8 and 1.2.");
        }
        if (accentColor == null || !HEX_COLOR.matcher(accentColor).matches()) {
            throw new IllegalArgumentException("Accent color must be a six-digit hexadecimal color.");
        }
        if (!Double.isFinite(backgroundDimming) || backgroundDimming < 0.0 || backgroundDimming > 0.85) {
            throw new IllegalArgumentException("Background dimming must be between 0 and 0.85.");
        }
    }

    public static GuiPreferences defaults() {
        return new GuiPreferences(1.0, "#9B7BFF", true, 0.58, true);
    }

    public GuiPreferences withUiScale(double value) {
        return new GuiPreferences(value, accentColor, animationsEnabled, backgroundDimming, closeOnEscape);
    }

    public GuiPreferences withAccentColor(String value) {
        return new GuiPreferences(uiScale, value, animationsEnabled, backgroundDimming, closeOnEscape);
    }

    public GuiPreferences withAnimationsEnabled(boolean value) {
        return new GuiPreferences(uiScale, accentColor, value, backgroundDimming, closeOnEscape);
    }

    public GuiPreferences withBackgroundDimming(double value) {
        return new GuiPreferences(uiScale, accentColor, animationsEnabled, value, closeOnEscape);
    }

    public GuiPreferences withCloseOnEscape(boolean value) {
        return new GuiPreferences(uiScale, accentColor, animationsEnabled, backgroundDimming, value);
    }
}
