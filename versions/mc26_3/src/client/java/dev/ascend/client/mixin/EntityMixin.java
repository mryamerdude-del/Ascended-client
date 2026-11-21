package dev.ascend.client.mixin;

import dev.ascend.client.AscendRotations;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While silent rotations are active, the local player's look vector points at
 * the maintained silent rotation instead of the camera. Crosshair picking,
 * attack direction and interact raycasts all read this vector, so hits land on
 * the silently aimed target while the camera stays still — the same approach
 * the reference client uses. (26.3 exposes {@code calculateViewVector} as a
 * static method, unlike 1.21.11 where it is an instance method.)
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getViewVector(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void ascend$silentLookVector(float partialTick, CallbackInfoReturnable<Vec3> info) {
        if (!AscendRotations.silentActive()) {
            return;
        }
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        info.setReturnValue(Entity.calculateViewVector(
                AscendRotations.silentRotation().pitch(),
                AscendRotations.silentRotation().yaw()));
    }
}
