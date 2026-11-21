package dev.ascend.client.shared;

import dev.ascend.client.shared.combat.AimMath;
import dev.ascend.client.shared.combat.AimPriority;
import dev.ascend.client.shared.combat.AimTransition;
import dev.ascend.client.shared.combat.AimVector;
import dev.ascend.client.shared.combat.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimAssistMathTest {
    private static final double EPSILON = 1.0E-6;

    private static AimMath.Box unitBoxAt(double x, double y, double z) {
        return new AimMath.Box(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
    }

    private static AimMath.Visibility alwaysVisible(double eyeX, double eyeY, double eyeZ) {
        return new AimMath.Visibility() {
            @Override
            public boolean visible(double x, double y, double z) {
                return true;
            }

            @Override
            public AimMath.Point eyes() {
                return new AimMath.Point(eyeX, eyeY, eyeZ);
            }
        };
    }

    private static AimMath.Visibility nothingVisible(double eyeX, double eyeY, double eyeZ) {
        return new AimMath.Visibility() {
            @Override
            public boolean visible(double x, double y, double z) {
                return false;
            }

            @Override
            public AimMath.Point eyes() {
                return new AimMath.Point(eyeX, eyeY, eyeZ);
            }
        };
    }

    @Test
    void wrapDegreesMatchesVanillaBehaviour() {
        assertEquals(0.0F, Rotation.wrapDegrees(360.0F), EPSILON);
        assertEquals(-180.0F, Rotation.wrapDegrees(180.0F), EPSILON);
        assertEquals(179.5F, Rotation.wrapDegrees(-180.5F), EPSILON);
        assertEquals(-90.0F, Rotation.wrapDegrees(270.0F), EPSILON);
        assertEquals(5.0F, Rotation.wrapDegrees(-715.0F), EPSILON);
    }

    @Test
    void rotationRecordClampsPitch() {
        assertEquals(90.0F, new Rotation(0.0F, 120.0F).pitch(), EPSILON);
        assertEquals(-90.0F, new Rotation(0.0F, -120.0F).pitch(), EPSILON);
    }

    @Test
    void lookingAtProducesVanillaYawAndPitch() {
        // Target directly north (negative Z): yaw must be ±180.
        Rotation north = Rotation.lookingAt(0, 64, 0, 0, 64, -10);
        assertEquals(180.0F, Math.abs(north.yaw()), EPSILON);
        assertEquals(0.0F, north.pitch(), EPSILON);

        // Target above: Minecraft pitch is -90 when looking up.
        Rotation up = Rotation.lookingAt(0, 64, 0, 0, 74, 0);
        assertEquals(-90.0F, up.pitch(), EPSILON);

        // Target below: pitch +90.
        Rotation down = Rotation.lookingAt(0, 64, 0, 0, 54, 0);
        assertEquals(90.0F, down.pitch(), EPSILON);
    }

    @Test
    void easedTransitionUsesReferenceFormula() {
        Rotation current = new Rotation(0.0F, 0.0F);
        Rotation desired = new Rotation(90.0F, 45.0F);
        float elapsed = 0.05F;
        float yawSpeed = 100.0F;
        float pitchSpeed = 40.0F;

        // Replicates the reference formula exactly.
        float expectedYawStep = Math.min(Math.abs(90.0F),
                Math.max(yawSpeed * elapsed * 0.2F, 90.0F * (1.0F - (float) Math.exp(-yawSpeed * elapsed * 0.1F))));
        float expectedPitchStep = Math.min(Math.abs(45.0F),
                Math.max(pitchSpeed * elapsed * 0.2F, 45.0F * (1.0F - (float) Math.exp(-pitchSpeed * elapsed * 0.1F))));

        AimMath.Transition transition = new AimMath.Transition();
        // First call initializes the clock (default 1/60 s), like the
        // reference client's request clock.
        transition.step(current, desired, AimTransition.EASED, yawSpeed, pitchSpeed, 1L);
        Rotation stepped = transition.step(current, desired, AimTransition.EASED, yawSpeed, pitchSpeed, 50_000_000L);
        assertEquals(expectedYawStep, stepped.yaw(), EPSILON);
        assertEquals(expectedPitchStep, stepped.pitch(), EPSILON);
    }

    @Test
    void snapTransitionJumpsStraightToTheTarget() {
        AimMath.Transition transition = new AimMath.Transition();
        Rotation stepped = transition.step(new Rotation(10.0F, 5.0F),
                new Rotation(-30.0F, -20.0F), AimTransition.SNAP, 100.0F, 40.0F, 0L);
        assertEquals(-30.0F, stepped.yaw(), EPSILON);
        assertEquals(-20.0F, stepped.pitch(), EPSILON);
    }

    @Test
    void naturalTransitionNeverExceedsTheSpeedLimit() {
        AimMath.Transition transition = new AimMath.Transition();
        Rotation current = new Rotation(0.0F, 0.0F);
        float speed = 100.0F;
        long nanos = 0L;
        for (int tick = 1; tick <= 40; tick++) {
            nanos += 50_000_000L;
            current = transition.step(current, new Rotation(180.0F, 0.0F),
                    AimTransition.NATURAL, speed, speed, nanos);
            // Speed limit is speed * 3 at most; per-tick movement is velocity * elapsed.
            float maxStep = speed * 3.0F * 0.05F + 1.0E-3F;
            assertTrue(Math.abs(current.yaw()) <= maxStep * tick + 0.05F,
                    "yaw must respect the NATURAL speed limit");
        }
        // Converges close to the target; wrapDegrees(180) is -180, so the
        // rotation approaches from the negative side like the reference client.
        assertTrue(Math.abs(Rotation.wrapDegrees(current.yaw() - 180.0F)) < 2.0F);
    }

    @Test
    void zeroSpeedNeverMovesTheRotation() {
        AimMath.Transition transition = new AimMath.Transition();
        for (AimTransition transitionKind : AimTransition.values()) {
            Rotation stepped = transition.step(new Rotation(10.0F, 5.0F),
                    new Rotation(150.0F, 40.0F), transitionKind, 0.0F, 0.0F, 0L);
            if (transitionKind == AimTransition.SNAP) {
                // SNAP ignores speeds by design.
                continue;
            }
            assertEquals(10.0F, stepped.yaw(), EPSILON, transitionKind + " must not move at speed 0");
            assertEquals(5.0F, stepped.pitch(), EPSILON);
        }
    }

    @Test
    void straightAimVectorTargetsHeadLevelAtFullWeight() {
        AimMath.Box box = unitBoxAt(10, 64, 10);
        // Multipoint 100 aims exactly at the STRAIGHT point (head level);
        // lower weights interpolate from the box center, like the reference.
        AimMath.Point point = AimMath.aimPoint(box, AimVector.STRAIGHT, 100.0, false, false,
                alwaysVisible(0, 64, 0));
        assertEquals(10.0, point.x(), EPSILON);
        assertEquals(box.maxY() - 0.1, point.y(), EPSILON);
        assertEquals(10.0, point.z(), EPSILON);
    }

    @Test
    void zeroMultipointWeightAimsAtTheBoxCenter() {
        AimMath.Box box = unitBoxAt(10, 64, 10);
        AimMath.Point point = AimMath.aimPoint(box, AimVector.STRAIGHT, 0.0, false, false,
                alwaysVisible(0, 64, 0));
        assertEquals(10.0, point.x(), EPSILON);
        assertEquals((box.minY() + box.maxY()) / 2.0, point.y(), EPSILON);
    }

    @Test
    void closestAimVectorClampsEyesOntoTheBox() {
        AimMath.Box box = unitBoxAt(10, 64, 10);
        AimMath.Point point = AimMath.aimPoint(box, AimVector.CLOSEST, 100.0, false, false,
                alwaysVisible(10.5, 70.0, 9.0));
        assertEquals(10.3, point.x(), EPSILON);
        assertEquals(65.8, point.y(), EPSILON);
        assertEquals(9.7, point.z(), EPSILON);
    }

    @Test
    void multipointWeightInterpolatesFromCenter() {
        AimMath.Box box = unitBoxAt(10, 64, 10);
        AimMath.Visibility visibility = alwaysVisible(0, 64, 0);
        AimMath.Point straight = AimMath.aimPoint(box, AimVector.STRAIGHT, 100.0, false, false, visibility);
        AimMath.Point center = new AimMath.Point((box.minX() + box.maxX()) / 2.0,
                (box.minY() + box.maxY()) / 2.0, (box.minZ() + box.maxZ()) / 2.0);
        AimMath.Point half = AimMath.aimPoint(box, AimVector.STRAIGHT, 50.0, false, false, visibility);
        assertEquals(center.x() + (straight.x() - center.x()) * 0.5, half.x(), EPSILON);
        assertEquals(center.y() + (straight.y() - center.y()) * 0.5, half.y(), EPSILON);
        assertEquals(center.z() + (straight.z() - center.z()) * 0.5, half.z(), EPSILON);
    }

    @Test
    void occludedPointIsRejectedUnlessThroughWalls() {
        AimMath.Box box = unitBoxAt(10, 64, 10);
        assertNull(AimMath.aimPoint(box, AimVector.STRAIGHT, 0.0, false, false,
                nothingVisible(0, 64, 0)));
        AimMath.Point occluded = AimMath.aimPoint(box, AimVector.STRAIGHT, 0.0, false, true,
                nothingVisible(0, 64, 0));
        assertEquals((box.minY() + box.maxY()) / 2.0, occluded.y(), EPSILON);
    }

    @Test
    void fallbackGridFindsTheClosestVisiblePoint() {
        AimMath.Box box = unitBoxAt(10, 64, 10);
        // Only the top-left-front corner of the grid is visible.
        AimMath.Visibility visibility = new AimMath.Visibility() {
            @Override
            public boolean visible(double x, double y, double z) {
                return x == box.minX() && y == box.maxY() && z == box.minZ();
            }

            @Override
            public AimMath.Point eyes() {
                return new AimMath.Point(0, 64, 0);
            }
        };
        AimMath.Point point = AimMath.aimPoint(box, AimVector.STRAIGHT, 0.0, false, false, visibility);
        assertEquals(box.minX(), point.x(), EPSILON);
        assertEquals(box.maxY(), point.y(), EPSILON);
        assertEquals(box.minZ(), point.z(), EPSILON);
    }

    @Test
    void singleAxisFovChecksYawOnly() {
        // Target due east of the player at the origin.
        AimMath.AimTarget target = new AimMath.AimTarget(1, "Zombie", 10, 64, 0,
                unitBoxAt(10, 64, 0), 10, 64.9, 0, 0L);
        // Looking straight at it (east is yaw -90) is inside any FOV.
        assertTrue(AimMath.withinFov(target, 0, 64, 0, -90.0F, 0.0F, 90.0, 90.0, false));
        // 90 deg FOV means 45 deg each side; looking north (yaw 180) is 90 deg off: outside.
        assertFalse(AimMath.withinFov(target, 0, 64, 0, 180.0F, 0.0F, 90.0, 90.0, false));
        // Split mode also constrains pitch: a narrow pitch window excludes the target.
        assertTrue(AimMath.withinFov(target, 0, 64, 0, -90.0F, 0.0F, 360.0, 90.0, true));
        assertFalse(AimMath.withinFov(target, 0, 64, 0, -90.0F, 80.0F, 360.0, 10.0, true));
    }

    @Test
    void nearestPriorityMeasuresFeetToFeet() {
        // Both candidates are equally far from the eyes, but candidate A stands
        // closer to the player's feet; Nearest must pick A.
        AimMath.AimTarget nearFeet = new AimMath.AimTarget(1, "A", 0, 64, 5,
                unitBoxAt(0, 64, 5), 0, 64.9, 5, 0L);
        AimMath.AimTarget highHead = new AimMath.AimTarget(2, "B", 0, 61, 5,
                unitBoxAt(0, 61, 5), 0, 62.9, 5, 0L);
        AimMath.AimTarget selected = AimMath.select(List.of(highHead, nearFeet), AimPriority.NEAREST,
                0, 64, 0, 0, 64.9, 0, 180.0F, 0.0F);
        assertEquals(1, selected.entityId());
    }

    @Test
    void fovPriorityPrefersTheSmallestAngularError() {
        AimMath.AimTarget left = new AimMath.AimTarget(1, "Left", 5, 64, 0,
                unitBoxAt(5, 64, 0), 5, 64.9, 0, 0L);
        AimMath.AimTarget ahead = new AimMath.AimTarget(2, "Ahead", 0, 64, -5,
                unitBoxAt(0, 64, -5), 0, 64.9, -5, 0L);
        AimMath.AimTarget selected = AimMath.select(List.of(left, ahead), AimPriority.FOV,
                0, 64, 0, 0, 64.9, 0, 180.0F, 0.0F);
        assertEquals(2, selected.entityId());
    }

    @Test
    void hurtTickPriorityPrefersExpiredCooldownsThenDistance() {
        // Reference semantics: lastAttackTick tracks when we last attacked an
        // entity (0 = never). HURT_TICK takes the minimum, so targets whose
        // attack cooldown has expired — or were never attacked — come first,
        // then the closest to the eyes.
        AimMath.AimTarget cooldownReady = new AimMath.AimTarget(1, "Ready", 0, 64, -5,
                unitBoxAt(0, 64, -5), 0, 64.9, -5, 0L);
        AimMath.AimTarget onCooldownFar = new AimMath.AimTarget(2, "CooldownFar", 0, 64, -10,
                unitBoxAt(0, 64, -10), 0, 64.9, -10, 5L);
        AimMath.AimTarget onCooldownNear = new AimMath.AimTarget(3, "CooldownNear", 0, 64, -3,
                unitBoxAt(0, 64, -3), 0, 64.9, -3, 5L);
        AimMath.AimTarget selected = AimMath.select(
                List.of(onCooldownFar, onCooldownNear, cooldownReady), AimPriority.HURT_TICK,
                0, 64, 0, 0, 64.9, 0, 180.0F, 0.0F);
        assertEquals(1, selected.entityId());

        // All on cooldown: the closest to the eyes wins.
        AimMath.AimTarget far = new AimMath.AimTarget(2, "Far", 0, 64, -10,
                unitBoxAt(0, 64, -10), 0, 64.9, -10, 5L);
        AimMath.AimTarget near = new AimMath.AimTarget(3, "Near", 0, 64, -3,
                unitBoxAt(0, 64, -3), 0, 64.9, -3, 5L);
        selected = AimMath.select(List.of(far, near), AimPriority.HURT_TICK,
                0, 64, 0, 0, 64.9, 0, 180.0F, 0.0F);
        assertEquals(3, selected.entityId());
    }

    @Test
    void noCandidatesYieldsNoTarget() {
        assertNull(AimMath.select(List.of(), AimPriority.NEAREST, 0, 64, 0, 0, 64, 0, 0.0F, 0.0F));
    }
}
