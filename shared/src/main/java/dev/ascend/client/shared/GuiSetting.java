package dev.ascend.client.shared;

import java.util.List;

/**
 * A module setting as seen by the GUI. {@code value} is a JSON-friendly
 * primitive: Boolean, Double, String, or a List of slot numbers.
 */
public record GuiSetting(
        String id,
        String name,
        String type,
        Object value,
        double minimum,
        double maximum,
        double step,
        String unit,
        List<String> options,
        String group,
        String description,
        boolean visible
) {
    public GuiSetting {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
