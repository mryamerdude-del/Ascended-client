package dev.ascend.client.shared;

import com.google.gson.Gson;
import dev.ascend.client.shared.combat.AimAssistModule;
import dev.ascend.client.shared.combat.AimMath;
import dev.ascend.client.shared.combat.AimView;
import dev.ascend.client.shared.combat.Rotation;
import dev.ascend.client.shared.module.ModuleRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModulePersistenceTest {
    private static final Gson GSON = new Gson();

    /** Records the last requested rotation so tests can assert aim output. */
    private static final class ScriptedAimView implements AimView {
        Rotation lastAim;
        boolean lastSilent;
        boolean released;
        boolean inGame = true;
        boolean attackDown = true;

        @Override
        public boolean inGame() {
            return inGame;
        }

        @Override
        public Rotation rotation() {
            return new Rotation(180.0F, 0.0F);
        }

        @Override
        public double eyeX() {
            return 0;
        }

        @Override
        public double eyeY() {
            return 64;
        }

        @Override
        public double eyeZ() {
            return 0;
        }

        @Override
        public double playerX() {
            return 0;
        }

        @Override
        public double playerY() {
            return 64;
        }

        @Override
        public double playerZ() {
            return 0;
        }

        @Override
        public List<AimMath.AimTarget> targets(double range) {
            return List.of(new AimMath.AimTarget(7, "Zombie", 0, 64, -5,
                    new AimMath.Box(-0.3, 64, -5.3, 0.3, 65.8, -4.7),
                    0, 64.9, -5, 0L));
        }

        @Override
        public boolean attackPressed() {
            return attackDown;
        }

        @Override
        public boolean acceptsHeldItem(String whitelist) {
            return true;
        }

        @Override
        public boolean permitsCurrentSlot(String slots) {
            return true;
        }

        @Override
        public boolean visible(double x, double y, double z) {
            return true;
        }

        @Override
        public void aimAt(Rotation rotation, boolean silent) {
            lastAim = rotation;
            lastSilent = silent;
        }

        @Override
        public void releaseAim(boolean silent) {
            released = true;
            lastSilent = silent;
        }
    }

    @TempDir
    Path temporaryDirectory;

    private AscendApplication newApplication(Path config, ScriptedAimView view) throws IOException {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new AimAssistModule(view));
        return new AscendApplication(config, "1.21.11", registry);
    }

    @Test
    void moduleToggleAndSettingsSurviveRestart() throws Exception {
        Path config = temporaryDirectory.resolve("ascendclient/settings.json");
        AscendApplication first = newApplication(config, new ScriptedAimView());

        first.handle("{\"action\":\"module\",\"id\":\"aim_assist\",\"enabled\":true}", "Right Shift", true);
        first.handle("{\"action\":\"moduleSetting\",\"module\":\"aim_assist\",\"setting\":\"range\",\"value\":12.5}",
                "Right Shift", true);
        first.handle("{\"action\":\"moduleSetting\",\"module\":\"aim_assist\",\"setting\":\"aim_math\",\"value\":\"Blatant\"}",
                "Right Shift", true);
        first.handle("{\"action\":\"moduleSetting\",\"module\":\"aim_assist\",\"setting\":\"item_whitelist\",\"value\":\"minecraft:sword\"}",
                "Right Shift", true);

        ScriptedAimView restoredView = new ScriptedAimView();
        AscendApplication restored = newApplication(config, restoredView);
        GuiSnapshot snapshot = GSON.fromJson(
                restored.handle("{\"action\":\"state\"}", "Right Shift", true).json(),
                GuiSnapshot.class);

        assertEquals(1, snapshot.modules().size());
        GuiModule module = snapshot.modules().get(0);
        assertTrue(module.enabled());
        assertEquals("Aim Assist", module.name());
        assertEquals("Combat", module.category());
        assertEquals("Blatant", module.settings().stream()
                .filter(setting -> setting.id().equals("aim_math"))
                .findFirst().orElseThrow().value());
        assertEquals(12.5, ((Number) module.settings().stream()
                .filter(setting -> setting.id().equals("range"))
                .findFirst().orElseThrow().value()).doubleValue());

        String stored = Files.readString(config);
        assertTrue(stored.contains("\"modules\""));
        assertTrue(stored.contains("\"aim_assist\""));
    }

    @Test
    void invalidModuleSettingIsRejectedWithoutBreakingState() throws Exception {
        AscendApplication application = newApplication(
                temporaryDirectory.resolve("settings.json"), new ScriptedAimView());

        assertThrows(IllegalArgumentException.class, () -> application.handle(
                "{\"action\":\"moduleSetting\",\"module\":\"aim_assist\",\"setting\":\"range\",\"value\":999}",
                "Right Shift", true));
        assertThrows(IllegalArgumentException.class, () -> application.handle(
                "{\"action\":\"moduleSetting\",\"module\":\"aim_assist\",\"setting\":\"missing\",\"value\":5}",
                "Right Shift", true));
        assertThrows(IllegalArgumentException.class, () -> application.handle(
                "{\"action\":\"moduleSetting\",\"module\":\"aim_assist\",\"setting\":\"aim_math\",\"value\":\"Sideways\"}",
                "Right Shift", true));
        assertThrows(IllegalArgumentException.class, () -> application.handle(
                "{\"action\":\"moduleSetting\",\"module\":\"nope\",\"setting\":\"range\",\"value\":5}",
                "Right Shift", true));
    }

    @Test
    void conditionalSettingsFollowTheirVisibilityRules() throws Exception {
        AscendApplication application = newApplication(
                temporaryDirectory.resolve("settings.json"), new ScriptedAimView());
        GuiSnapshot snapshot = GSON.fromJson(
                application.handle("{\"action\":\"state\"}", "Right Shift", true).json(),
                GuiSnapshot.class);
        GuiModule module = snapshot.modules().get(0);

        assertTrue(visible(module, "fov"), "FOV shows in Single mode");
        assertFalse(visible(module, "yaw_fov"), "Yaw FOV hides in Single mode");
        assertFalse(visible(module, "pitch_fov"), "Pitch FOV hides in Single mode");
        assertTrue(visible(module, "horizontal_speed"), "Speeds show for Smooth");

        application.handle("{\"action\":\"moduleSetting\",\"module\":\"aim_assist\",\"setting\":\"fov_mode\",\"value\":\"Split\"}",
                "Right Shift", true);
        application.handle("{\"action\":\"moduleSetting\",\"module\":\"aim_assist\",\"setting\":\"aim_math\",\"value\":\"Blatant\"}",
                "Right Shift", true);
        snapshot = GSON.fromJson(
                application.handle("{\"action\":\"state\"}", "Right Shift", true).json(),
                GuiSnapshot.class);
        module = snapshot.modules().get(0);

        assertFalse(visible(module, "fov"));
        assertTrue(visible(module, "yaw_fov"));
        assertTrue(visible(module, "pitch_fov"));
        assertFalse(visible(module, "horizontal_speed"), "Speeds hide for Blatant");
    }

    @Test
    void disabledModuleDoesNotAimAndEnabledModuleAims() throws Exception {
        ScriptedAimView view = new ScriptedAimView();
        AscendApplication application = newApplication(
                temporaryDirectory.resolve("settings.json"), view);

        application.registry().tick();
        assertFalse(view.released || view.lastAim != null,
                "a disabled module must not drive rotations");

        application.handle("{\"action\":\"module\",\"id\":\"aim_assist\",\"enabled\":true}", "Right Shift", true);
        application.registry().tick();
        assertTrue(view.lastAim != null, "an enabled module must aim at the scripted target");
        assertFalse(view.lastSilent, "silent rotations default to false");
        // Default Aim Math is Smooth, so the first eased step stays near the
        // starting rotation instead of snapping.
        assertTrue(Math.abs(view.lastAim.yaw() - 180.0F) < 90.0F);

        application.handle("{\"action\":\"module\",\"id\":\"aim_assist\",\"enabled\":false}", "Right Shift", true);
        application.registry().tick();
        assertTrue(view.released, "disabling must release the rotation");
    }

    @Test
    void onClickAndWhitelistConditionsReleaseAim() throws Exception {
        ScriptedAimView view = new ScriptedAimView();
        AscendApplication application = newApplication(
                temporaryDirectory.resolve("settings.json"), view);
        application.handle("{\"action\":\"module\",\"id\":\"aim_assist\",\"enabled\":true}", "Right Shift", true);

        view.attackDown = false;
        application.handle("{\"action\":\"moduleSetting\",\"module\":\"aim_assist\",\"setting\":\"on_click\",\"value\":true}",
                "Right Shift", true);
        application.registry().tick();
        assertTrue(view.released);

        view.attackDown = true;
        view.released = false;
        application.registry().tick();
        assertTrue(view.lastAim != null, "aiming resumes when the attack key is pressed");
    }

    private static boolean visible(GuiModule module, String settingId) {
        return module.settings().stream()
                .filter(setting -> setting.id().equals(settingId))
                .findFirst().orElseThrow().visible();
    }
}
