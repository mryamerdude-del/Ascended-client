package dev.ascend.client;

import dev.ascend.client.shared.combat.Rotation;
import net.minecraft.client.player.LocalPlayer;

/**
 * Silent rotation state for the 26.3 adapter. Mirrors the reference client's
 * constants: the silent return blends 55% per tick toward the player's
 * rotation and stops once the error is under one degree.
 */
public final class AscendRotations {
    private static final float RETURN_BLEND = 0.55F;
    private static final float RETURN_TOLERANCE = 1.0F;

    private static Rotation silentRotation;
    private static boolean returning;

    private AscendRotations() {
    }

    public static boolean silentActive() {
        return silentRotation != null;
    }

    public static Rotation silentRotation() {
        return silentRotation;
    }

    /** Starts tracking a silent rotation while the module is aiming. */
    public static void aimAt(Rotation rotation) {
        silentRotation = rotation;
        returning = false;
    }

    /** Stops aiming: the silent rotation returns smoothly to the player's view. */
    public static void beginReturn() {
        returning = silentRotation != null;
    }

    /** Immediately discards any silent rotation. */
    public static void reset() {
        silentRotation = null;
        returning = false;
    }

    /** Advances the smooth return; call once per client tick. */
    public static void tick(LocalPlayer player) {
        if (silentRotation == null || !returning || player == null) {
            return;
        }
        float yawDelta = Rotation.wrapDegrees(player.getYRot() - silentRotation.yaw());
        float pitchDelta = player.getXRot() - silentRotation.pitch();
        silentRotation = new Rotation(
                silentRotation.yaw() + yawDelta * RETURN_BLEND,
                silentRotation.pitch() + pitchDelta * RETURN_BLEND);
        if (Math.abs(yawDelta) < RETURN_TOLERANCE && Math.abs(pitchDelta) < RETURN_TOLERANCE) {
            reset();
        }
    }
}
