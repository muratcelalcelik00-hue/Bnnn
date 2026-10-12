package com.bnnn.polyfield.client.mixin;

import com.bnnn.polyfield.client.ClientGunState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void polyfield$begin(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                                 ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vcp, int light,
                                 CallbackInfo ci) {
        ClientGunState.renderingEntity = player;
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void polyfield$end(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                               ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vcp, int light,
                               CallbackInfo ci) {
        ClientGunState.renderingEntity = null;
    }
}
