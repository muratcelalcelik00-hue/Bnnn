package com.bnnn.polyfield.client.render;

import com.bnnn.polyfield.entity.GrenadeEntity;
import com.bnnn.polyfield.entity.RocketEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public final class ProjectileRenderers {
    private ProjectileRenderers() {
    }

    public static class Rocket extends EntityRenderer<RocketEntity> {
        public Rocket(EntityRendererFactory.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(RocketEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
            PfModel model = PfModels.get(e.kind().model);
            if (model != null) {
                Vec3d v = e.getVelocity();
                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) Math.atan2(v.x, v.z)));
                matrices.multiply(RotationAxis.POSITIVE_X.rotation((float) -Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z))));
                if (e.kind() == RocketEntity.Kind.TANK_SHELL) {
                    matrices.scale(0.7f, 0.7f, 0.5f);
                }
                Vector3f c = GunItemRenderer.center(model);
                matrices.translate(-c.x, -c.y, -c.z);
                model.render(matrices, vcp, light, OverlayTexture.DEFAULT_UV);
                matrices.pop();
            }
            super.render(e, yaw, tickDelta, matrices, vcp, light);
        }

        @Override
        public Identifier getTexture(RocketEntity entity) {
            return PfModel.WHITE;
        }
    }

    public static class Grenade extends EntityRenderer<GrenadeEntity> {
        public Grenade(EntityRendererFactory.Context ctx) {
            super(ctx);
            this.shadowRadius = 0.1f;
        }

        @Override
        public void render(GrenadeEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
            PfModel model = PfModels.get(e.isStickGrenade() ? "grenade_m24" : "grenade_mk2");
            if (model != null) {
                matrices.push();
                Vector3f c = GunItemRenderer.center(model);
                matrices.translate(0, (c.y - model.min.y) * 0.6f, 0);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(e.getId() * 47f));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(tickDelta, e.prevSpin, e.spin)
                        + (e.isStickGrenade() ? 90f : 0f)));
                matrices.translate(-c.x, -c.y, -c.z);
                model.render(matrices, vcp, light, OverlayTexture.DEFAULT_UV);
                matrices.pop();
            }
            super.render(e, yaw, tickDelta, matrices, vcp, light);
        }

        @Override
        public Identifier getTexture(GrenadeEntity entity) {
            return PfModel.WHITE;
        }
    }
}
