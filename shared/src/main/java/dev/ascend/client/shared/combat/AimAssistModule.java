package dev.ascend.client.shared.combat;

import dev.ascend.client.shared.module.AscendModule;
import dev.ascend.client.shared.module.ModuleCategory;
import dev.ascend.client.shared.module.ModuleSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aim Assist. Setting names, defaults, ranges, choices, conditions, target
 * selection, rotation speeds and aim-point maths follow the reference client.
 * All Minecraft access goes through {@link AimView}.
 */
public final class AimAssistModule extends AscendModule {
    private static final String FOV_SINGLE = "Single";
    private static final String FOV_SPLIT = "Split";
    private static final String AIM_BLATANT = "Blatant";
    private static final String AIM_NATURAL = "Natural";

    private final AimView view;
    private final AimMath.Transition transition = new AimMath.Transition();

    private final ModuleSetting aimMath;
    private final ModuleSetting aimVector;
    private final ModuleSetting targetPriority;
    private final ModuleSetting random;
    private final ModuleSetting silentRotations;
    private final ModuleSetting throughWalls;
    private final ModuleSetting horizontalSpeed;
    private final ModuleSetting verticalSpeed;
    private final ModuleSetting onClick;
    private final ModuleSetting range;
    private final ModuleSetting fovMode;
    private final ModuleSetting fov;
    private final ModuleSetting yawFov;
    private final ModuleSetting pitchFov;
    private final ModuleSetting multipoint;
    private final ModuleSetting itemWhitelist;
    private final ModuleSetting hotbarWhitelist;

    private boolean aiming;

    public AimAssistModule(AimView view) {
        super("aim_assist", "Aim Assist", ModuleCategory.COMBAT, "Automatically aims at targets");
        this.view = view;

        // Order follows the reference client's settings catalog.
        this.aimMath = add(ModuleSetting.choice("aim_math", "Aim Math", "Smooth",
                "Smooth", AIM_NATURAL, AIM_BLATANT).group("Aim"));
        this.aimVector = add(ModuleSetting.choice("aim_vector", "Aim Vector", "Straight",
                "Straight", "Closest").group("Aim"));
        this.targetPriority = add(ModuleSetting.choice("target_priority", "Target Priority", "Nearest",
                "Nearest", "FOV", "HurtTick").group("Targeting"));
        this.random = add(ModuleSetting.bool("random", "Random", false).group("Aim"));
        this.silentRotations = add(ModuleSetting.bool("silent_rotations", "Silent Rotations", false)
                .group("Aim"));
        this.throughWalls = add(ModuleSetting.bool("through_walls", "Through Walls", false).group("Aim"));
        this.horizontalSpeed = add(ModuleSetting.number("horizontal_speed", "Horizontal Speed",
                5.0, 0.0, 10.0, 0.1, "").allowOutOfRange()
                .hideWhen("aim_math", AIM_BLATANT).group("Timing"));
        this.verticalSpeed = add(ModuleSetting.number("vertical_speed", "Vertical Speed",
                2.0, 0.0, 10.0, 0.1, "").allowOutOfRange()
                .hideWhen("aim_math", AIM_BLATANT).group("Timing"));
        this.onClick = add(ModuleSetting.bool("on_click", "On Click", false).group("Conditions"));
        this.range = add(ModuleSetting.number("range", "Range", 6.0, 1.0, 50.0, 0.1, " blocks")
                .group("Targeting"));
        this.fovMode = add(ModuleSetting.choice("fov_mode", "FOV Mode", FOV_SINGLE,
                FOV_SINGLE, FOV_SPLIT).group("Targeting"));
        this.fov = add(ModuleSetting.number("fov", "FOV", 90.0, 0.0, 360.0, 1.0, " deg")
                .showWhen("fov_mode", FOV_SINGLE).group("Targeting"));
        this.yawFov = add(ModuleSetting.number("yaw_fov", "Yaw FOV", 90.0, 0.0, 360.0, 1.0, " deg")
                .showWhen("fov_mode", FOV_SPLIT).group("Targeting"));
        this.pitchFov = add(ModuleSetting.number("pitch_fov", "Pitch FOV", 90.0, 0.0, 180.0, 1.0, " deg")
                .showWhen("fov_mode", FOV_SPLIT).group("Targeting"));
        this.multipoint = add(ModuleSetting.number("multipoint", "Multipoint", 50.0, 0.0, 100.0, 1.0, "%")
                .group("Aim"));
        this.itemWhitelist = add(ModuleSetting.text("item_whitelist", "Item Whitelist", "")
                .description("Comma-separated item ids. Empty aims with any held item.")
                .group("Conditions"));
        this.hotbarWhitelist = add(ModuleSetting.slots("hotbar_whitelist", "Hotbar Whitelist")
                .description("Selected slots only. Empty aims from any slot.")
                .group("Conditions"));
    }

    @Override
    public void onClientTick() {
        if (!view.inGame()
                || !view.acceptsHeldItem(itemWhitelist.asText())
                || !view.permitsCurrentSlot(hotbarWhitelist.slotsAsText())) {
            release();
            return;
        }
        if (onClick.asBoolean() && !view.attackPressed()) {
            release();
            return;
        }

        double distance = range.asNumber();
        double distanceSquared = distance * distance;
        double eyeX = view.eyeX();
        double eyeY = view.eyeY();
        double eyeZ = view.eyeZ();
        // FOV and priority always use the player's real rotation, even while
        // silent rotations are active, exactly like the reference client.
        Rotation real = view.rotation();
        boolean splitFov = fovMode.isChoice(FOV_SPLIT);
        double fullYawFov = splitFov ? yawFov.asNumber() : fov.asNumber();

        List<AimMath.AimTarget> candidates = new ArrayList<>();
        for (AimMath.AimTarget candidate : view.targets(distance)) {
            double dx = candidate.x() - eyeX;
            double dy = candidate.y() - eyeY;
            double dz = candidate.z() - eyeZ;
            if (dx * dx + dy * dy + dz * dz > distanceSquared) {
                continue;
            }
            if (!AimMath.withinFov(candidate, eyeX, eyeY, eyeZ, real.yaw(), real.pitch(),
                    fullYawFov, pitchFov.asNumber(), splitFov)) {
                continue;
            }
            candidates.add(candidate);
        }

        AimMath.AimTarget target = AimMath.select(candidates, priority(),
                view.playerX(), view.playerY(), view.playerZ(),
                eyeX, eyeY, eyeZ, real.yaw(), real.pitch());
        if (target == null) {
            release();
            return;
        }

        AimMath.Point point = AimMath.aimPoint(target.box(), vector(), multipoint.asNumber(),
                random.asBoolean(), throughWalls.asBoolean(), new AimMath.Visibility() {
                    @Override
                    public boolean visible(double x, double y, double z) {
                        return view.visible(x, y, z);
                    }

                    @Override
                    public AimMath.Point eyes() {
                        return new AimMath.Point(eyeX, eyeY, eyeZ);
                    }
                });
        if (point == null) {
            release();
            return;
        }

        Rotation desired = Rotation.lookingAt(eyeX, eyeY, eyeZ, point.x(), point.y(), point.z());
        boolean silent = silentRotations.asBoolean();
        Rotation stepped = transition.step(view.effectiveRotation(), desired, transition(),
                (float) horizontalSpeed.asNumber() * 20.0F,
                (float) verticalSpeed.asNumber() * 20.0F, System.nanoTime());

        view.aimAt(stepped, silent);
        aiming = true;
    }

    @Override
    protected void onDisable() {
        aiming = false;
        transition.reset();
        view.releaseAim(silentRotations.asBoolean());
    }

    @Override
    public String statusText() {
        // The reference client shows the Aim Math selection as the module suffix.
        return aimMath.asChoice();
    }

    /** Horizontal-speed / aim-math helpers, used by the adapter for its rotation state. */
    public boolean isSilent() {
        return silentRotations.asBoolean();
    }

    public AimTransition transition() {
        if (aimMath.isChoice(AIM_NATURAL)) {
            return AimTransition.NATURAL;
        }
        return aimMath.isChoice(AIM_BLATANT) ? AimTransition.SNAP : AimTransition.EASED;
    }

    public AimVector vector() {
        String selected = aimVector.asChoice();
        for (AimVector value : AimVector.values()) {
            if (value.displayName().toLowerCase(Locale.ROOT)
                    .equals(selected.toLowerCase(Locale.ROOT))) {
                return value;
            }
        }
        return AimVector.STRAIGHT;
    }

    public AimPriority priority() {
        String selected = targetPriority.asChoice();
        for (AimPriority value : AimPriority.values()) {
            if (value.displayName().equalsIgnoreCase(selected)) {
                return value;
            }
        }
        return AimPriority.NEAREST;
    }

    private void release() {
        aiming = false;
        transition.reset();
        view.releaseAim(silentRotations.asBoolean());
    }
}
