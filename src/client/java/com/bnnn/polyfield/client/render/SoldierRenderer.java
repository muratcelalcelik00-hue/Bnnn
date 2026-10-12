package com.bnnn.polyfield.client.render;

import com.bnnn.polyfield.entity.SoldierEntity;
import com.bnnn.polyfield.weapon.Weapon;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Renders Polyfield soldiers with their own mesh, texture and weapon. */
public class SoldierRenderer extends LivingEntityRenderer<SoldierEntity, SoldierModel> {
    public SoldierRenderer(EntityRendererFactory.Context ctx, String modelName) {
        super(ctx, new SoldierModel(modelName), 0.45f);
        addFeature(new GunFeature(this));
    }

    @Override
    public Identifier getTexture(SoldierEntity entity) {
        PfSkinnedModel m = getModel().mesh();
        return m != null ? m.texture() : PfModel.WHITE;
    }

    @Override
    protected boolean hasLabel(SoldierEntity entity) {
        return entity.hasCustomName() && super.hasLabel(entity);
    }

    static final class GunFeature extends FeatureRenderer<SoldierEntity, SoldierModel> {
        GunFeature(FeatureRendererContext<SoldierEntity, SoldierModel> ctx) {
            super(ctx);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, SoldierEntity entity, float limbAngle,
                           float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            SoldierModel model = getContextModel();
            Weapon w = entity.weapon();
            if (w == null || !model.hasPose) {
                return;
            }
            PfModel gun = PfModels.get(w.id);
            if (gun == null) {
                return;
            }
            matrices.push();
            SoldierModel.toModelFrame(matrices);
            matrices.translate(model.gunPos.x, model.gunPos.y, model.gunPos.z);
            matrices.multiply(model.gunRot);
            gun.render(matrices, vcp, light, OverlayTexture.DEFAULT_UV);
            MuzzleFlash.render(gun, entity, matrices, vcp, tickDelta, 1.0f);
            matrices.pop();
        }
    }
}
