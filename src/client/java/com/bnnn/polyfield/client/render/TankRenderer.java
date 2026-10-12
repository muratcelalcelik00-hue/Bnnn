package com.bnnn.polyfield.client.render;

import com.bnnn.polyfield.entity.TankEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Hull, turret (yaw about the turret ring) and gun (elevation about the trunnions). */
public class TankRenderer extends EntityRenderer<TankEntity> {
    public TankRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.8f;
    }

    @Override
    public void render(TankEntity tank, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        PfModel model = PfModels.get(tank.type.id);
        if (model != null) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-tank.getYaw(tickDelta)));
            float hurt = tank.getHurtTicks() - tickDelta;
            if (hurt > 0) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(hurt) * hurt * 0.15f));
            }
            float fired = 1f - tank.reloadProgress() * 12f;
            int overlay = OverlayTexture.getUv(0f, hurt > 5);
            model.renderGroup(0, matrices, vcp, light, overlay);

            float turret = MathHelper.lerpAngleDegrees(tickDelta, tank.prevClientTurretYaw, tank.clientTurretYaw);
            float gun = MathHelper.lerp(tickDelta, tank.prevClientGunPitch, tank.clientGunPitch);
            Vec3d tp = tank.type.turretPivot;
            matrices.push();
            matrices.translate(tp.x, tp.y, tp.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-turret));
            matrices.translate(-tp.x, -tp.y, -tp.z);
            model.renderGroup(1, matrices, vcp, light, overlay);
            Vec3d gp = tank.type.gunPivot;
            matrices.translate(gp.x, gp.y, gp.z);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(gun));
            if (fired > 0f) {
                matrices.translate(0, 0, -0.35f * fired);
            }
            matrices.translate(-gp.x, -gp.y, -gp.z);
            model.renderGroup(2, matrices, vcp, light, overlay);
            matrices.pop();
            matrices.pop();
        }
        super.render(tank, yaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public Identifier getTexture(TankEntity entity) {
        return PfModel.WHITE;
    }
}
