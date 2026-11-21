package dev.ascend.client.shared.module;

import dev.ascend.client.shared.GuiModule;
import dev.ascend.client.shared.GuiSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns every registered module and is the only place that changes module state.
 * Both the GUI bridge and any in-game keybind path go through here, so the
 * Modules page always reflects the real runtime state.
 */
public final class ModuleRegistry {
    /** Persisted shape of one module. Values are JSON-friendly primitives. */
    public record StoredModule(boolean enabled, int keyCode, Map<String, Object> settings) {
        public StoredModule {
            settings = settings == null ? Map.of() : Map.copyOf(settings);
        }
    }

    private final Map<String, AscendModule> modules = new LinkedHashMap<>();
    private final Map<String, String> failures = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Long> lastFailureNanos = new LinkedHashMap<>();

    private static final long FAILURE_LOG_INTERVAL_NANOS = 5_000_000_000L;

    public synchronized void register(AscendModule module) {
        Objects.requireNonNull(module, "module");
        if (modules.putIfAbsent(module.id(), module) != null) {
            throw new IllegalStateException("A module is already registered with id: " + module.id());
        }
        notifyListeners();
    }

    public synchronized void unregister(String moduleId) {
        AscendModule module = modules.remove(moduleId);
        if (module == null) {
            return;
        }
        if (module.isEnabled()) {
            module.applyEnabled(false);
            try {
                module.onDisable();
            } catch (RuntimeException | LinkageError failure) {
                recordFailure(moduleId, "Disable failed: " + failure);
            }
        }
        try {
            module.onDetach();
        } catch (RuntimeException | LinkageError failure) {
            recordFailure(moduleId, "Detach failed: " + failure);
        }
        failures.remove(moduleId);
        lastFailureNanos.remove(moduleId);
        notifyListeners();
    }

    public synchronized AscendModule find(String moduleId) {
        return modules.get(moduleId);
    }

    /** Modules in display order: category, then case-insensitive name. */
    public synchronized List<AscendModule> modules() {
        List<AscendModule> ordered = new ArrayList<>(modules.values());
        ordered.sort(Comparator
                .comparingInt((AscendModule module) -> module.category().ordinal())
                .thenComparing(AscendModule::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(ordered);
    }

    public synchronized boolean isEnabled(String moduleId) {
        AscendModule module = modules.get(moduleId);
        return module != null && module.isEnabled();
    }

    /**
     * Enables or disables a module and runs its lifecycle hooks. On failure the
     * previous state is restored and the exception is rethrown for the GUI.
     */
    public synchronized void setEnabled(String moduleId, boolean requested) {
        AscendModule module = modules.get(moduleId);
        if (module == null) {
            throw new IllegalArgumentException("No module is registered with id: " + moduleId);
        }
        if (module.isEnabled() == requested) {
            return;
        }
        if (requested && !module.canEnable()) {
            throw new IllegalStateException(module.name() + " cannot be enabled right now.");
        }

        module.applyEnabled(requested);
        try {
            if (requested) {
                module.onEnable();
            } else {
                module.onDisable();
            }
        } catch (RuntimeException | LinkageError failure) {
            module.applyEnabled(!requested);
            recordFailure(moduleId, (requested ? "Enable" : "Disable") + " failed: " + failure);
            try {
                if (requested) {
                    module.onDisable();
                } else {
                    module.onEnable();
                }
            } catch (RuntimeException | LinkageError rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw new IllegalStateException(
                    "Could not " + (requested ? "enable " : "disable ") + module.name() + ".", failure);
        }

        failures.remove(moduleId);
        lastFailureNanos.remove(moduleId);
        notifyListeners();
    }

    public synchronized void toggle(String moduleId) {
        AscendModule module = modules.get(moduleId);
        if (module == null) {
            throw new IllegalArgumentException("No module is registered with id: " + moduleId);
        }
        setEnabled(moduleId, !module.isEnabled());
    }

    /**
     * Validates and applies a setting. If the module rejects the change, the
     * previous value is restored so GUI state and runtime state stay aligned.
     */
    public synchronized void setSetting(String moduleId, String settingId, Object requested) {
        AscendModule module = modules.get(moduleId);
        if (module == null) {
            throw new IllegalArgumentException("No module is registered with id: " + moduleId);
        }
        ModuleSetting setting = module.findSetting(settingId);
        if (setting == null) {
            throw new IllegalArgumentException(
                    "No setting is registered with id '" + settingId + "' for module " + module.name() + ".");
        }

        Object previous = setting.value();
        try {
            setting.applyValue(requested);
            module.onSettingChanged(setting);
        } catch (RuntimeException | LinkageError failure) {
            setting.applyValue(previous);
            recordFailure(moduleId, "Setting '" + setting.name() + "' failed: " + failure);
            throw failure instanceof IllegalArgumentException illegal
                    ? illegal
                    : new IllegalArgumentException(failure.getMessage(), failure);
        }
        notifyListeners();
    }

    /** Runs one client tick for every enabled module, isolating module failures. */
    public void tick() {
        List<AscendModule> snapshot;
        synchronized (this) {
            snapshot = modules();
        }
        for (AscendModule module : snapshot) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onClientTick();
                clearFailure(module.id());
            } catch (RuntimeException | LinkageError failure) {
                recordFailureThrottled(module.id(), "Tick failed: " + failure);
            }
        }
    }

    public synchronized String failure(String moduleId) {
        return failures.getOrDefault(moduleId, "");
    }

    /** Applies persisted state after registration; invalid entries are skipped. */
    public synchronized void restore(Map<String, StoredModule> stored) {
        for (AscendModule module : modules.values()) {
            StoredModule state = stored.get(module.id());
            if (state == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : state.settings().entrySet()) {
                ModuleSetting setting = module.findSetting(entry.getKey());
                if (setting == null) {
                    continue;
                }
                try {
                    setting.applyValue(entry.getValue());
                } catch (RuntimeException failure) {
                    recordFailure(module.id(), "Ignored stored setting '" + entry.getKey() + "': "
                            + failure.getMessage());
                }
            }
            module.setKeyCode(state.keyCode());
            if (state.enabled()) {
                try {
                    setEnabled(module.id(), true);
                } catch (RuntimeException failure) {
                    // setEnabled records the failure and leaves the module disabled.
                }
            }
        }
    }

    /** Captures current state for persistence. */
    public synchronized Map<String, StoredModule> captureState() {
        Map<String, StoredModule> state = new LinkedHashMap<>();
        for (AscendModule module : modules.values()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (ModuleSetting setting : module.settings()) {
                values.put(setting.id(), setting.value());
            }
            state.put(module.id(), new StoredModule(module.isEnabled(), module.keyCode(), values));
        }
        return state;
    }

    public synchronized List<GuiModule> snapshot() {
        List<GuiModule> snapshot = new ArrayList<>();
        for (AscendModule module : modules()) {
            List<GuiSetting> settings = new ArrayList<>();
            for (ModuleSetting setting : module.settings()) {
                settings.add(new GuiSetting(
                        setting.id(),
                        setting.name(),
                        setting.type().wireName(),
                        setting.value(),
                        setting.minimum(),
                        setting.maximum(),
                        setting.step(),
                        setting.unit(),
                        setting.options(),
                        setting.group(),
                        setting.description(),
                        setting.isVisible(module)
                ));
            }
            String status;
            try {
                status = module.statusText();
            } catch (RuntimeException | LinkageError failure) {
                status = "";
                recordFailure(module.id(), "Status failed: " + failure);
            }
            snapshot.add(new GuiModule(
                    module.id(),
                    module.name(),
                    module.category().displayName(),
                    module.description(),
                    module.isEnabled(),
                    !settings.isEmpty(),
                    status,
                    failure(module.id()),
                    settings
            ));
        }
        return List.copyOf(snapshot);
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // A listener (usually persistence) must not break state changes.
            }
        }
    }

    private synchronized void clearFailure(String moduleId) {
        if (failures.remove(moduleId) != null) {
            notifyListeners();
        }
    }

    private synchronized void recordFailure(String moduleId, String message) {
        failures.put(moduleId, message);
        lastFailureNanos.put(moduleId, System.nanoTime());
    }

    private synchronized void recordFailureThrottled(String moduleId, String message) {
        long now = System.nanoTime();
        Long previous = lastFailureNanos.get(moduleId);
        if (previous != null && now - previous < FAILURE_LOG_INTERVAL_NANOS
                && message.equals(failures.get(moduleId))) {
            return;
        }
        failures.put(moduleId, message);
        lastFailureNanos.put(moduleId, now);
        notifyListeners();
    }
}
