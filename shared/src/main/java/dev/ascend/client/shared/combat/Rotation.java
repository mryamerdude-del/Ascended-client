package dev.ascend.client.shared.combat;

/** A yaw/pitch pair in degrees. Pitch is clamped exactly like the game does. */
public record Rotation(float yaw, float pitch) {
    public Rotation {
        pitch = Math.clamp(pitch, -90.0F, 90.0F);
    }

    /** Rotation that looks from {@code origin} toward {@code target}. */
    public static Rotation lookingAt(double originX, double originY, double originZ,
                                     double targetX, double targetY, double targetZ) {
        double dx = targetX - originX;
        double dy = targetY - originY;
        double dz = targetZ - originZ;
        double horizontal = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new Rotation(wrapDegrees(yaw), wrapDegrees(pitch));
    }

    public float yawDifferenceTo(Rotation target) {
        return wrapDegrees(target.yaw - yaw);
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public static float clamp(float value, float minimum, float maximum) {
        return value < minimum ? minimum : Math.min(value, maximum);
    }
}
