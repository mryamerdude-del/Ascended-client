package dev.ascend.client.shared.module;

/**
 * Categories shown on the Modules page. Display names follow the Echo reference
 * client so ported modules keep their familiar grouping.
 */
public enum ModuleCategory {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Visuals"),
    WORLD("World"),
    UTILITY("Utility"),
    INTERNALS("Internals");

    private final String displayName;

    ModuleCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
