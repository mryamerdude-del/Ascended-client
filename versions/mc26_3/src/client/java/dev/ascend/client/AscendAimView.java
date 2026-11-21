package dev.ascend.client;

import dev.ascend.client.shared.combat.AimMath;
import dev.ascend.client.shared.combat.AimView;
import dev.ascend.client.shared.combat.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minecraft 26.3 implementation of the shared Aim Assist surface.
 * Candidate collection, FOV inputs and raycasts mirror the reference client:
 * distance uses eye position to entity position, hitboxes are interpolated
 * and expanded by the entity pick radius, and block raycasts use the outline
 * shape.
 */
public final class AscendAimView implements AimView {
    private final Minecraft client;

    public AscendAimView(Minecraft client) {
        this.client = client;
    }

    @Override
    public boolean inGame() {
        return client.player != null && client.level != null && client.gui.screen() == null;
    }

    @Override
    public Rotation rotation() {
        LocalPlayer player = client.player;
        return new Rotation(player.getYRot(), player.getXRot());
    }

    @Override
    public Rotation effectiveRotation() {
        if (AscendRotations.silentActive()) {
            return AscendRotations.silentRotation();
        }
        return rotation();
    }

    @Override
    public double eyeX() {
        return client.player.getEyePosition().x;
    }

    @Override
    public double eyeY() {
        return client.player.getEyePosition().y;
    }

    @Override
    public double eyeZ() {
        return client.player.getEyePosition().z;
    }

    @Override
    public double playerX() {
        return client.player.getX();
    }

    @Override
    public double playerY() {
        return client.player.getY();
    }

    @Override
    public double playerZ() {
        return client.player.getZ();
    }

    @Override
    public List<AimMath.AimTarget> targets(double range) {
        LocalPlayer player = client.player;
        List<AimMath.AimTarget> targets = new ArrayList<>();
        for (Entity entity : client.level.getEntities(player,
                player.getBoundingBox().inflate(range), AscendAimView::isCandidate)) {
            LivingEntity living = (LivingEntity) entity;
            Vec3 interpolated = entity.getPosition(1.0F);
            AABB box = entity.getBoundingBox().move(
                    interpolated.x - entity.getX(),
                    interpolated.y - entity.getY(),
                    interpolated.z - entity.getZ());
            double margin = entity.getPickRadius();
            if (margin > 0.0) {
                box = box.inflate(margin);
            }
            Vec3 eyes = living.getEyePosition();
            targets.add(new AimMath.AimTarget(
                    entity.getId(),
                    living.getName().getString(),
                    interpolated.x, interpolated.y, interpolated.z,
                    new AimMath.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
                    eyes.x, eyes.y, eyes.z,
                    0L));
        }
        return targets;
    }

    private static boolean isCandidate(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.isSpectator()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return living != player && !living.isRemoved() && living.isAlive();
    }

    @Override
    public boolean attackPressed() {
        return client.options.keyAttack.isDown();
    }

    @Override
    public boolean acceptsHeldItem(String whitelist) {
        if (whitelist == null || whitelist.isBlank()) {
            return true;
        }
        ItemStack held = client.player.getMainHandItem();
        if (held.isEmpty()) {
            return false;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        String heldId = itemId.toString().toLowerCase(Locale.ROOT);
        for (String entry : whitelist.split(",")) {
            if (heldId.equals(entry.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean permitsCurrentSlot(String slots) {
        if (slots == null || slots.isBlank()) {
            return true;
        }
        int selected = client.player.getInventory().getSelectedSlot();
        for (String entry : slots.split(",")) {
            try {
                if (Integer.parseInt(entry.trim()) == selected) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // Malformed entries are skipped, like an empty whitelist.
            }
        }
        return false;
    }

    @Override
    public boolean visible(double x, double y, double z) {
        LocalPlayer player = client.player;
        Vec3 from = player.getEyePosition();
        HitResult hit = client.level.clip(new ClipContext(
                from, new Vec3(x, y, z), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS
                || hit.getLocation().distanceToSqr(x, y, z) < 1.0E-6;
    }

    @Override
    public void aimAt(Rotation rotation, boolean silent) {
        if (silent) {
            AscendRotations.aimAt(rotation);
            return;
        }
        AscendRotations.reset();
        client.player.setYRot(rotation.yaw());
        client.player.setXRot(rotation.pitch());
    }

    @Override
    public void releaseAim(boolean silent) {
        if (silent && AscendRotations.silentActive()) {
            AscendRotations.beginReturn();
        } else {
            AscendRotations.reset();
        }
    }
}
