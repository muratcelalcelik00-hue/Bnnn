package com.bnnn.polyfield.client.mixin;

import com.bnnn.polyfield.client.ClientGunState;
import com.bnnn.polyfield.entity.TankEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With a Polyfield weapon (or in a tank) the attack button fires instead of punching or mining. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    private boolean polyfield$armed() {
        return player != null && (ClientGunState.heldWeapon(player) != null || player.getVehicle() instanceof TankEntity);
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void polyfield$doAttack(CallbackInfoReturnable<Boolean> cir) {
        if (polyfield$armed()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void polyfield$handleBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (polyfield$armed()) {
            ci.cancel();
        }
    }
}
