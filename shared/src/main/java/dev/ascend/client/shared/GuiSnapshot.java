package dev.ascend.client.shared;

import java.util.List;

public record GuiSnapshot(
        String minecraftVersion,
        String status,
        String activePage,
        GuiPreferences preferences,
        String openingKeybind,
        List<GuiModule> modules
) {
    public GuiSnapshot {
        modules = List.copyOf(modules);
    }
}
