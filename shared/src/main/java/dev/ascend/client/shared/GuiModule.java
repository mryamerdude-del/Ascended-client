package dev.ascend.client.shared;

import java.util.List;

public record GuiModule(
        String id,
        String name,
        String category,
        String description,
        boolean enabled,
        boolean hasSettings,
        String status,
        String error,
        List<GuiSetting> settings
) {
    public GuiModule {
        settings = settings == null ? List.of() : List.copyOf(settings);
        status = status == null ? "" : status;
        error = error == null ? "" : error;
    }

    /** Compatibility constructor for tests and callers that only need metadata. */
    public GuiModule(String id, String name, String category, String description,
                     boolean enabled, boolean hasSettings) {
        this(id, name, category, description, enabled, hasSettings, "", "", List.of());
    }
}
