package com.bnnn.polyfield.client.mixin;

import com.bnnn.polyfield.client.ClientGunState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the weapon item renderer which entity holds it (muzzle flash, recoil). */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void polyfield$begin(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm, MatrixStack matrices,
                                 VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        ClientGunState.renderingEntity = entity;
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void polyfield$end(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm, MatrixStack matrices,
                               VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        ClientGunState.renderingEntity = null;
    }
}
