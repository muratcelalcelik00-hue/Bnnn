package com.bnnn.polyfield.client.mixin;

import com.bnnn.polyfield.weapon.GunItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players hold Polyfield weapons with both hands, shouldered. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void polyfield$armPose(AbstractClientPlayerEntity player, Hand hand,
                                          CallbackInfoReturnable<BipedEntityModel.ArmPose> cir) {
        if (player.getMainHandStack().getItem() instanceof GunItem) {
            cir.setReturnValue(hand == Hand.MAIN_HAND ? BipedEntityModel.ArmPose.CROSSBOW_HOLD : BipedEntityModel.ArmPose.EMPTY);
        }
    }
}
