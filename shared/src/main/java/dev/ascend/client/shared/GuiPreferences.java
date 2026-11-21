package dev.ascend.client.shared;

import java.util.regex.Pattern;

public record GuiPreferences(
        double uiScale,
        String accentColor,
        boolean animationsEnabled,
        double backgroundDimming,
        boolean closeOnEscape
) {
    public static final double MIN_UI_SCALE = 0.75;
    public static final double MAX_UI_SCALE = 1.25;

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    public GuiPreferences {
        if (!isValidUiScale(uiScale)) {
            throw new IllegalArgumentException("UI scale must be between 0.75 and 1.25.");
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

    static GuiPreferences restore(
            Double uiScale,
            String accentColor,
            Boolean animationsEnabled,
            Double backgroundDimming,
            Boolean closeOnEscape
    ) {
        GuiPreferences defaults = defaults();
        return new GuiPreferences(
                uiScale != null && isValidUiScale(uiScale) ? uiScale : defaults.uiScale,
                accentColor != null && HEX_COLOR.matcher(accentColor).matches()
                        ? accentColor : defaults.accentColor,
                animationsEnabled != null ? animationsEnabled : defaults.animationsEnabled,
                backgroundDimming != null && Double.isFinite(backgroundDimming)
                        && backgroundDimming >= 0.0 && backgroundDimming <= 0.85
                        ? backgroundDimming : defaults.backgroundDimming,
                closeOnEscape != null ? closeOnEscape : defaults.closeOnEscape
        );
    }

    private static boolean isValidUiScale(double value) {
        return Double.isFinite(value) && value >= MIN_UI_SCALE && value <= MAX_UI_SCALE;
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
