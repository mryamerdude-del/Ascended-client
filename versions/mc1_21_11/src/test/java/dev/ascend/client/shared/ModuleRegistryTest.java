package dev.ascend.client.shared;

import dev.ascend.client.shared.module.AscendModule;
import dev.ascend.client.shared.module.ModuleCategory;
import dev.ascend.client.shared.module.ModuleRegistry;
import dev.ascend.client.shared.module.ModuleSetting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleRegistryTest {
    /** Test module with one setting of every type used by Aim Assist. */
    private static class StubModule extends AscendModule {
        final ModuleSetting flag = add(ModuleSetting.bool("flag", "Flag", false));
        final ModuleSetting speed = add(ModuleSetting.number("speed", "Speed", 5.0, 0.0, 10.0, 0.1, ""));
        final ModuleSetting mode = add(ModuleSetting.choice("mode", "Mode", "Smooth", "Smooth", "Blatant"));

        int enableCount;
        int disableCount;
        int tickCount;

        StubModule(String id, ModuleCategory category) {
            this(id, id, category);
        }

        StubModule(String id, String name, ModuleCategory category) {
            super(id, name, category, "Test module");
        }

        @Override
        protected void onEnable() {
            enableCount++;
        }

        @Override
        protected void onDisable() {
            disableCount++;
        }

        @Override
        public void onClientTick() {
            tickCount++;
        }
    }

    @Test
    void togglingRunsLifecycleHooksExactlyOnce() {
        ModuleRegistry registry = new ModuleRegistry();
        StubModule module = new StubModule("stub", ModuleCategory.COMBAT);
        registry.register(module);

        assertFalse(module.isEnabled());
        registry.toggle("stub");
        assertTrue(module.isEnabled());
        assertEquals(1, module.enableCount);
        registry.setEnabled("stub", false);
        assertFalse(module.isEnabled());
        assertEquals(1, module.disableCount);
    }

    @Test
    void enableFailureRestoresPreviousStateAndReportsError() {
        ModuleRegistry registry = new ModuleRegistry();
        AscendModule broken = new AscendModule("broken", "Broken", ModuleCategory.COMBAT, "Fails") {
            @Override
            protected void onEnable() {
                throw new IllegalStateException("boom");
            }
        };
        registry.register(broken);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> registry.setEnabled("broken", true));
        assertTrue(error.getMessage().contains("Could not enable"));
        assertFalse(broken.isEnabled());
        assertTrue(registry.failure("broken").contains("boom"));
    }

    @Test
    void unknownModuleOrSettingIsRejected() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new StubModule("stub", ModuleCategory.COMBAT));

        assertThrows(IllegalArgumentException.class, () -> registry.setEnabled("missing", true));
        assertThrows(IllegalArgumentException.class, () -> registry.setSetting("stub", "missing", 1.0));
        assertThrows(IllegalArgumentException.class, () -> registry.setSetting("missing", "speed", 1.0));
    }

    @Test
    void settingValuesAreValidatedAndRevertedOnFailure() {
        ModuleRegistry registry = new ModuleRegistry();
        StubModule module = new StubModule("stub", ModuleCategory.COMBAT);
        registry.register(module);

        registry.setSetting("stub", "speed", 7.5);
        assertEquals(7.5, module.speed.asNumber());
        assertThrows(IllegalArgumentException.class, () -> registry.setSetting("stub", "speed", 99.0));
        assertEquals(7.5, module.speed.asNumber(), "failed writes must keep the previous value");
        assertThrows(IllegalArgumentException.class, () -> registry.setSetting("stub", "flag", "yes"));
        assertFalse(module.flag.asBoolean());
    }

    @Test
    void stateCaptureAndRestoreRoundTrips() {
        ModuleRegistry registry = new ModuleRegistry();
        StubModule module = new StubModule("stub", ModuleCategory.COMBAT);
        registry.register(module);
        registry.setSetting("stub", "speed", 2.5);
        registry.setEnabled("stub", true);

        Map<String, ModuleRegistry.StoredModule> captured = registry.captureState();
        assertTrue(captured.get("stub").enabled());
        assertEquals(2.5, captured.get("stub").settings().get("speed"));

        ModuleRegistry restored = new ModuleRegistry();
        StubModule restoredModule = new StubModule("stub", ModuleCategory.COMBAT);
        restored.register(restoredModule);
        restored.restore(captured);
        assertTrue(restoredModule.isEnabled());
        assertEquals(2.5, restoredModule.speed.asNumber());
        assertEquals(1, restoredModule.enableCount);
    }

    @Test
    void invalidStoredValuesAreSkippedNotFatal() {
        ModuleRegistry registry = new ModuleRegistry();
        StubModule module = new StubModule("stub", ModuleCategory.COMBAT);
        registry.register(module);

        registry.restore(Map.of("stub", new ModuleRegistry.StoredModule(true, -1, Map.of(
                "speed", 42.0,
                "flag", "not-a-boolean",
                "mode", "not-an-option",
                "unknown", 1.0
        ))));

        assertEquals(5.0, module.speed.asNumber(), "out-of-range stored value must be rejected");
        assertFalse(module.flag.asBoolean());
        assertEquals("Smooth", module.mode.asChoice());
    }

    @Test
    void duplicateRegistrationIsRejected() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new StubModule("stub", ModuleCategory.COMBAT));
        assertThrows(IllegalStateException.class,
                () -> registry.register(new StubModule("stub", ModuleCategory.MOVEMENT)));
    }

    @Test
    void snapshotOrdersByCategoryThenNameAndIncludesSettings() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new StubModule("zeta", "Zulu", ModuleCategory.COMBAT));
        registry.register(new StubModule("alpha", "Alpha", ModuleCategory.MOVEMENT));
        registry.register(new StubModule("beta", "Bravo", ModuleCategory.COMBAT));

        List<GuiModule> snapshot = registry.snapshot();
        assertEquals(List.of("beta", "zeta", "alpha"),
                snapshot.stream().map(GuiModule::id).toList());
        GuiModule first = snapshot.get(0);
        assertEquals("Combat", first.category());
        assertTrue(first.hasSettings());
        assertEquals(3, first.settings().size());
        assertEquals("number", first.settings().get(1).type());
    }

    @Test
    void visibilityRulesHideConditionalSettings() {
        ModuleRegistry registry = new ModuleRegistry();
        StubModule module = new StubModule("stub", ModuleCategory.COMBAT) {
            {
                speed.hideWhen("mode", "Blatant");
            }
        };
        registry.register(module);

        GuiModule snapshot = registry.snapshot().get(0);
        assertTrue(snapshot.settings().stream()
                .filter(setting -> setting.id().equals("speed"))
                .allMatch(GuiSetting::visible));

        registry.setSetting("stub", "mode", "Blatant");
        snapshot = registry.snapshot().get(0);
        assertFalse(snapshot.settings().stream()
                .filter(setting -> setting.id().equals("speed"))
                .allMatch(GuiSetting::visible));
    }

    @Test
    void tickOnlyRunsEnabledModulesAndIsolatesFailures() {
        ModuleRegistry registry = new ModuleRegistry();
        StubModule enabled = new StubModule("enabled", ModuleCategory.COMBAT);
        AscendModule failing = new AscendModule("failing", "Failing", ModuleCategory.COMBAT, "Fails") {
            @Override
            public void onClientTick() {
                throw new RuntimeException("tick boom");
            }
        };
        registry.register(enabled);
        registry.register(failing);
        registry.setEnabled("enabled", true);
        registry.setEnabled("failing", true);

        registry.tick();
        registry.tick();

        assertEquals(2, enabled.tickCount);
        assertTrue(registry.failure("failing").contains("tick boom"));
    }
}
