package dev.ascend.client.shared;

public record GuiModule(
        String id,
        String name,
        String category,
        String description,
        boolean enabled,
        boolean hasSettings
) {
}
