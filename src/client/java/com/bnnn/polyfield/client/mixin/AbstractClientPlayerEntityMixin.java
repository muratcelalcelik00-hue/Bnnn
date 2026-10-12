package com.bnnn.polyfield.client.mixin;

import com.bnnn.polyfield.client.ClientGunState;
import com.bnnn.polyfield.weapon.Weapon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom while aiming down sights. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
    private void polyfield$aimFov(CallbackInfoReturnable<Float> cir) {
        Object self = this;
        if (self == MinecraftClient.getInstance().player && ClientGunState.aiming) {
            Weapon w = ClientGunState.heldWeapon((AbstractClientPlayerEntity) self);
            if (w != null) {
                cir.setReturnValue(cir.getReturnValueF() * w.aimFov());
            }
        }
    }
}
