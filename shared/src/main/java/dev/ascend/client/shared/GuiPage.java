package dev.ascend.client.shared;

public enum GuiPage {
    DASHBOARD("dashboard"),
    MODULES("modules"),
    SETTINGS("settings"),
    APPEARANCE("appearance");

    private final String wireName;

    GuiPage(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static GuiPage fromWireName(String value) {
        for (GuiPage page : values()) {
            if (page.wireName.equals(value)) {
                return page;
            }
        }
        throw new IllegalArgumentException("Unknown GUI page: " + value);
    }
}
