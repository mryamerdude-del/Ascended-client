package dev.ascend.client.shared.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base class for an Ascend module. The lifecycle here is deliberately
 * Minecraft-free: version adapters call {@link #onClientTick()} once per client
 * tick for enabled modules and implement {@link #onEnable()} / {@link #onDisable()}
 * with their own Minecraft APIs.
 */
public abstract class AscendModule {
    private final String id;
    private final String name;
    private final ModuleCategory category;
    private final String description;
    private final List<ModuleSetting> settings = new ArrayList<>();

    private boolean enabled;
    private int keyCode = -1;

    protected AscendModule(String id, String name, ModuleCategory category, String description) {
        if (id == null || id.isBlank() || id.length() > 80 || !id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid module id: " + id);
        }
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.category = Objects.requireNonNull(category, "category");
        this.description = description == null ? "" : description;
    }

    /** Registers a setting and returns it so modules can keep a field reference. */
    protected final <T extends ModuleSetting> T add(T setting) {
        Objects.requireNonNull(setting, "setting");
        if (findSetting(setting.id()) != null) {
            throw new IllegalStateException("Duplicate setting id '" + setting.id()
                    + "' in module " + id);
        }
        settings.add(setting);
        return setting;
    }

    public final String id() {
        return id;
    }

    public final String name() {
        return name;
    }

    public final ModuleCategory category() {
        return category;
    }

    public final String description() {
        return description;
    }

    public final List<ModuleSetting> settings() {
        return Collections.unmodifiableList(settings);
    }

    public final ModuleSetting findSetting(String settingId) {
        for (ModuleSetting setting : settings) {
            if (setting.id().equals(settingId)) {
                return setting;
            }
        }
        return null;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final int keyCode() {
        return keyCode;
    }

    public final void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    /** Called by the registry before enable/disable so the two stay in sync. */
    final void applyEnabled(boolean requested) {
        this.enabled = requested;
    }

    public boolean canEnable() {
        return true;
    }

    /** Called after the enabled flag is set. Register listeners/tick hooks here. */
    protected void onEnable() {
    }

    /** Called after the enabled flag is cleared. Release state here. */
    protected void onDisable() {
    }

    /** Called once per client tick while the module is enabled. */
    public void onClientTick() {
    }

    /** Called after a setting value was validated and stored. */
    public void onSettingChanged(ModuleSetting setting) {
    }

    /** Called when the module is about to be removed, or the client is shutting down. */
    public void onDetach() {
    }

    /** Short status shown next to the module name; empty when there is nothing to show. */
    public String statusText() {
        return "";
    }
}
