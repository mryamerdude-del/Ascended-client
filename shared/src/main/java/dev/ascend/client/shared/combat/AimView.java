package dev.ascend.client.shared.combat;

import java.util.List;

/**
 * The Minecraft-facing surface the shared Aim Assist logic needs. Each version
 * adapter implements this; no Minecraft type crosses into shared code.
 */
public interface AimView {
    /** World and player are present and no screen is open. */
    boolean inGame();

    /**
     * The player's real rotation. FOV checks and target priority always use
     * this, matching the reference client which reads the player directly.
     */
    Rotation rotation();

    /**
     * Rotation to step from. While silent rotations are active this is the
     * maintained silent rotation; otherwise it equals {@link #rotation()}.
     */
    default Rotation effectiveRotation() {
        return rotation();
    }

    double eyeX();

    double eyeY();

    double eyeZ();

    /** Player feet position; the Nearest priority measures from here. */
    double playerX();

    double playerY();

    double playerZ();

    /**
     * Living candidates within {@code range} blocks that pass the basic
     * validity rules (alive, not removed, not spectator, not the local player).
     * Hitboxes should be interpolated and expanded by the entity targeting
     * margin, as the reference client does before picking an aim point.
     */
    List<AimMath.AimTarget> targets(double range);

    boolean attackPressed();

    /** {@code whitelist} is a comma-separated item-id list; empty accepts everything. */
    boolean acceptsHeldItem(String whitelist);

    /** {@code slots} is a comma-separated hotbar slot list; empty accepts every slot. */
    boolean permitsCurrentSlot(String slots);

    boolean visible(double x, double y, double z);

    /** Applies the rotation. Silent keeps the camera still and spoofs packets instead. */
    void aimAt(Rotation rotation, boolean silent);

    /** Stops aiming: returns the rotation to normal. */
    void releaseAim(boolean silent);
}
