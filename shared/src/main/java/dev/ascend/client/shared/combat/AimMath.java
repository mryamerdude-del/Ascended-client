package dev.ascend.client.shared.combat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure targeting and rotation maths ported from the reference client. No
 * Minecraft types are used, so the exact same behaviour is shared by every
 * version adapter and can be unit tested.
 */
public final class AimMath {
    private AimMath() {
    }

    /** Choose the aim point inside the target box, exactly like the reference client. */
    public static Point aimPoint(Box box, AimVector vector, double multipointPercent,
                                 boolean randomize, boolean allowOccluded, Visibility visibility) {
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerY = (box.minY + box.maxY) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;

        Point center = new Point(centerX, centerY, centerZ);
        Point selected = switch (vector) {
            case STRAIGHT -> new Point(centerX, box.maxY - 0.1, centerZ);
            case CLOSEST -> closestPointToEyes(box, visibility);
        };
        if (randomize) {
            selected = randomizePoint(selected, box, vector);
        }
        double weight = multipointPercent / 100.0;
        Point preferred = new Point(
                centerX + (selected.x - centerX) * weight,
                centerY + (selected.y - centerY) * weight,
                centerZ + (selected.z - centerZ) * weight);

        if (visibility.visible(preferred.x, preferred.y, preferred.z)) {
            return preferred;
        }
        Point alternative = closestVisibleGridPoint(box, preferred, visibility);
        if (alternative != null) {
            return alternative;
        }
        return allowOccluded ? preferred : null;
    }

    private static Point closestPointToEyes(Box box, Visibility visibility) {
        Point eyes = visibility.eyes();
        return new Point(
                Math.clamp(eyes.x, box.minX, box.maxX),
                Math.clamp(eyes.y, box.minY, box.maxY),
                Math.clamp(eyes.z, box.minZ, box.maxZ));
    }

    private static Point randomizePoint(Point point, Box box, AimVector vector) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (vector != AimVector.CLOSEST) {
            return lerp(point, randomPoint(box), 0.5);
        }
        double offsetX = point.x + (random.nextDouble() - 0.5) * (box.maxX - box.minX) * 0.9;
        double offsetY = point.y + (random.nextDouble() - 0.5) * (box.maxY - box.minY) * 0.9;
        double offsetZ = point.z + (random.nextDouble() - 0.5) * (box.maxZ - box.minZ) * 0.9;
        return lerp(point, new Point(
                Math.clamp(offsetX, box.minX, box.maxX),
                Math.clamp(offsetY, box.minY, box.maxY),
                Math.clamp(offsetZ, box.minZ, box.maxZ)), 0.5);
    }

    private static Point randomPoint(Box box) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new Point(
                box.minX + random.nextDouble() * (box.maxX - box.minX),
                box.minY + random.nextDouble() * (box.maxY - box.minY),
                box.minZ + random.nextDouble() * (box.maxZ - box.minZ));
    }

    private static Point closestVisibleGridPoint(Box box, Point preferred, Visibility visibility) {
        Point closest = null;
        double distance = Double.MAX_VALUE;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 3; z++) {
                    double px = box.minX + (double) x / 2.0 * (box.maxX - box.minX);
                    double py = box.minY + (double) y / 4.0 * (box.maxY - box.minY);
                    double pz = box.minZ + (double) z / 2.0 * (box.maxZ - box.minZ);
                    double squared = squaredDistance(px, py, pz, preferred);
                    if (visibility.visible(px, py, pz) && squared < distance) {
                        distance = squared;
                        closest = new Point(px, py, pz);
                    }
                }
            }
        }
        return closest;
    }

    /**
     * Selects a target with the configured priority. Candidates must already
     * pass range and FOV. Comparators mirror the reference client exactly:
     * <ul>
     *   <li>Nearest — player position to entity position (feet), no tiebreak.</li>
     *   <li>FOV — angular error, then eye position to entity position.</li>
     *   <li>HurtTick — last attack tick, then eye position to entity position,
     *       then angular error.</li>
     * </ul>
     * Exact ties keep the first candidate, like {@code Stream.min}.
     */
    public static AimTarget select(List<AimTarget> candidates, AimPriority priority,
                                   double playerX, double playerY, double playerZ,
                                   double eyeX, double eyeY, double eyeZ,
                                   float yaw, float pitch) {
        AimTarget best = null;
        double bestPrimary = Double.MAX_VALUE;
        double bestSecondary = Double.MAX_VALUE;
        double bestTertiary = Double.MAX_VALUE;
        for (AimTarget candidate : candidates) {
            double distanceFromEyes = squaredDistance(candidate.x(), candidate.y(), candidate.z(),
                    new Point(eyeX, eyeY, eyeZ));
            double distanceFromPlayer = squaredDistance(candidate.x(), candidate.y(), candidate.z(),
                    new Point(playerX, playerY, playerZ));
            double angle = angularDistance(candidate, eyeX, eyeY, eyeZ, yaw, pitch);
            double primary;
            double secondary;
            double tertiary;
            switch (priority) {
                case HURT_TICK -> {
                    primary = candidate.lastAttackTick();
                    secondary = distanceFromEyes;
                    tertiary = angle;
                }
                case FOV -> {
                    primary = angle;
                    secondary = distanceFromEyes;
                    tertiary = 0.0;
                }
                default -> {
                    primary = distanceFromPlayer;
                    secondary = 0.0;
                    tertiary = 0.0;
                }
            }
            if (primary < bestPrimary
                    || (primary == bestPrimary && secondary < bestSecondary)
                    || (primary == bestPrimary && secondary == bestSecondary && tertiary < bestTertiary)) {
                best = candidate;
                bestPrimary = primary;
                bestSecondary = secondary;
                bestTertiary = tertiary;
            }
        }
        return best;
    }

    /** Combined yaw/pitch error between the player's view and the target's eyes. */
    public static double angularDistance(AimTarget target, double eyeX, double eyeY, double eyeZ,
                                         float yaw, float pitch) {
        double dx = target.eyeX() - eyeX;
        double dy = target.eyeY() - eyeY;
        double dz = target.eyeZ() - eyeZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        float yawDifference = Math.abs(Rotation.wrapDegrees(targetYaw - yaw));
        float pitchDifference = Math.abs(targetPitch - pitch);
        return Math.sqrt(yawDifference * yawDifference + pitchDifference * pitchDifference);
    }

    /** FOV test using the target's eye position, as the reference client does. */
    public static boolean withinFov(AimTarget target, double eyeX, double eyeY, double eyeZ,
                                    float yaw, float pitch, double fullYawFov, double fullPitchFov,
                                    boolean split) {
        double dx = target.eyeX() - eyeX;
        double dy = target.eyeY() - eyeY;
        double dz = target.eyeZ() - eyeZ;
        double targetYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double yawDifference = Math.abs(Rotation.wrapDegrees((float) targetYaw - yaw));
        if (yawDifference > fullYawFov / 2.0) {
            return false;
        }
        if (!split) {
            return true;
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double targetPitch = -Math.toDegrees(Math.atan2(dy, horizontal));
        double pitchDifference = Math.abs((float) targetPitch - pitch);
        return pitchDifference <= fullPitchFov / 2.0;
    }

    /** Smooth velocity update used by the NATURAL transition. */
    public static float smoothVelocity(float velocity, float angularError, float speed, float elapsed) {
        float speedLimit = speed * Math.min(1.0F + Math.abs(angularError) / 30.0F, 3.0F);
        float desiredVelocity = Rotation.clamp(angularError * speed, -speedLimit, speedLimit);
        float blend = 1.0F - (float) Math.exp(-elapsed * 15.0F);
        return Rotation.clamp(velocity + (desiredVelocity - velocity) * blend,
                -speedLimit, speedLimit);
    }

    private static Rotation eased(Rotation current, Rotation desired, float yawSpeed,
                                  float pitchSpeed, float elapsed) {
        float yaw = Rotation.wrapDegrees(desired.yaw() - current.yaw());
        float pitch = desired.pitch() - current.pitch();
        float yawStep = Math.min(Math.abs(yaw), Math.max(yawSpeed * elapsed * 0.2F,
                Math.abs(yaw) * (1.0F - (float) Math.exp(-yawSpeed * elapsed * 0.1F))));
        float pitchStep = Math.min(Math.abs(pitch), Math.max(pitchSpeed * elapsed * 0.2F,
                Math.abs(pitch) * (1.0F - (float) Math.exp(-pitchSpeed * elapsed * 0.1F))));
        return new Rotation(current.yaw() + Math.signum(yaw) * yawStep,
                current.pitch() + Math.signum(pitch) * pitchStep);
    }

    private static Point lerp(Point from, Point to, double delta) {
        return new Point(from.x + (to.x - from.x) * delta,
                from.y + (to.y - from.y) * delta,
                from.z + (to.z - from.z) * delta);
    }

    private static double squaredDistance(double x, double y, double z, Point point) {
        double dx = x - point.x;
        double dy = y - point.y;
        double dz = z - point.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Aim point inside a target's hitbox. */
    public record Point(double x, double y, double z) {
    }

    /** Axis-aligned hitbox in world space. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    /** A candidate target as the shared code sees it. */
    public record AimTarget(int entityId, String name, double x, double y, double z,
                            Box box, double eyeX, double eyeY, double eyeZ, long lastAttackTick) {
    }

    /** Visibility query used for the through-walls fallback grid. */
    public interface Visibility {
        boolean visible(double x, double y, double z);

        Point eyes();
    }

    /** Keeps NATURAL transition state between ticks. */
    public static final class Transition {
        private float yawVelocity;
        private float pitchVelocity;
        private long previousNanos;

        public Rotation step(Rotation current, Rotation desired, AimTransition transition,
                             float yawSpeed, float pitchSpeed, long nowNanos) {
            float elapsed = elapsed(nowNanos);
            if (transition == AimTransition.SNAP) {
                return desired;
            }
            if (transition == AimTransition.EASED) {
                return eased(current, desired, yawSpeed, pitchSpeed, elapsed);
            }
            float clamped = Rotation.clamp(elapsed, 0.001F, 0.1F);
            float yaw = Rotation.wrapDegrees(desired.yaw() - current.yaw());
            float pitch = desired.pitch() - current.pitch();
            yawVelocity = smoothVelocity(yawVelocity, yaw, yawSpeed, clamped);
            pitchVelocity = smoothVelocity(pitchVelocity, pitch, pitchSpeed, clamped);
            return new Rotation(current.yaw() + yawVelocity * clamped,
                    current.pitch() + pitchVelocity * clamped);
        }

        public void reset() {
            yawVelocity = 0.0F;
            pitchVelocity = 0.0F;
            previousNanos = 0L;
        }

        private float elapsed(long nowNanos) {
            if (previousNanos == 0L) {
                previousNanos = nowNanos;
                return 0.016666668F;
            }
            float elapsed = (float) (nowNanos - previousNanos) / 1.0E9F;
            previousNanos = nowNanos;
            return Math.clamp(elapsed, 0.001F, 0.1F);
        }
    }
}
