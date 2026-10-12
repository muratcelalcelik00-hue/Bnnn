package com.bnnn.polyfield.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/** Grenades and tank kits shown with their Polyfield model. */
public class GrenadeItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private final String modelName;
    private final float handScale;

    public GrenadeItemRenderer(String modelName, float handScale) {
        this.modelName = modelName;
        this.handScale = handScale;
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vcp, int light,
                       int overlay) {
        PfModel model = PfModels.get(modelName);
        if (model == null) {
            return;
        }
        matrices.push();
        matrices.translate(0.5f, 0.5f, 0.5f);
        Vector3f c = GunItemRenderer.center(model);
        float size = Math.max(model.max.x - model.min.x, Math.max(model.max.y - model.min.y, model.max.z - model.min.z));
        float s;
        if (mode == ModelTransformationMode.GUI || mode == ModelTransformationMode.FIXED) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-135f));
            s = 0.9f / size;
        } else if (mode.isFirstPerson()) {
            matrices.translate(-0.1f, 0.1f, 0f);
            s = handScale;
        } else {
            s = Math.min(handScale, 0.6f / size);
        }
        matrices.scale(s, s, s);
        matrices.translate(-c.x, -c.y, -c.z);
        model.render(matrices, vcp, light, overlay);
        matrices.pop();
    }
}
