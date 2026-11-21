package dev.ascend.client.mixin;

import dev.ascend.client.AscendRotations;
import dev.ascend.client.shared.combat.Rotation;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spoofs the rotation sent to the server while silent rotations are active.
 * {@code sendPosition} builds every outgoing movement packet from the player's
 * current rotation, so the silent rotation is applied for the duration of the
 * call and the camera rotation is restored afterwards.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Unique
    private float ascend$visualYaw;
    @Unique
    private float ascend$visualPitch;
    @Unique
    private boolean ascend$spoofing;

    @Inject(method = "sendPosition()V", at = @At("HEAD"))
    private void ascend$beginSilentPacket(CallbackInfo info) {
        Rotation silent = AscendRotations.silentRotation();
        if (silent == null) {
            return;
        }
        LocalPlayer self = (LocalPlayer) (Object) this;
        ascend$visualYaw = self.getYRot();
        ascend$visualPitch = self.getXRot();
        self.setYRot(silent.yaw());
        self.setXRot(silent.pitch());
        ascend$spoofing = true;
    }

    @Inject(method = "sendPosition()V", at = @At("RETURN"))
    private void ascend$endSilentPacket(CallbackInfo info) {
        if (!ascend$spoofing) {
            return;
        }
        ascend$spoofing = false;
        LocalPlayer self = (LocalPlayer) (Object) this;
        self.setYRot(ascend$visualYaw);
        self.setXRot(ascend$visualPitch);
    }
}
