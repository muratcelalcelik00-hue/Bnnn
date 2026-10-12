package com.bnnn.polyfield.client.render;

import com.bnnn.polyfield.PolyfieldMod;
import com.bnnn.polyfield.client.ClientGunState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Additive muzzle flash drawn at a weapon model's muzzle anchor for a moment after each shot. */
public final class MuzzleFlash {
    public static final Identifier TEXTURE = PolyfieldMod.id("textures/misc/muzzle_flash.png");
    private static final RenderLayer LAYER = RenderLayer.getEyes(TEXTURE);

    private MuzzleFlash() {
    }

    public static void render(PfModel model, @Nullable Entity shooter, MatrixStack matrices, VertexConsumerProvider vcp,
                              float tickDelta, float scale) {
        if (shooter == null) {
            return;
        }
        float t = ClientGunState.sinceShot(shooter, tickDelta);
        if (t > 1.6f) {
            return;
        }
        Vector3f m = model.anchor("muzzle", new Vector3f(0, 0, model.max.z));
        float size = 0.22f * scale * (1.0f - t / 2.0f);
        matrices.push();
        matrices.translate(m.x, m.y, m.z + size * 0.6f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((shooter.getId() * 37 + (int) (ClientGunState.ticks() * 53)) % 360));
        VertexConsumer vc = vcp.getBuffer(LAYER);
        // two quads along the barrel and one facing it
        quad(vc, matrices, new Vector3f(-size, 0, -size), new Vector3f(size, 0, -size), new Vector3f(size, 0, size * 1.8f),
                new Vector3f(-size, 0, size * 1.8f));
        quad(vc, matrices, new Vector3f(0, -size, -size), new Vector3f(0, size, -size), new Vector3f(0, size, size * 1.8f),
                new Vector3f(0, -size, size * 1.8f));
        quad(vc, matrices, new Vector3f(-size, -size, 0), new Vector3f(size, -size, 0), new Vector3f(size, size, 0),
                new Vector3f(-size, size, 0));
        matrices.pop();
    }

    private static void quad(VertexConsumer vc, MatrixStack matrices, Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f nrm = matrices.peek().getNormalMatrix();
        v(vc, pos, nrm, a, 0, 0);
        v(vc, pos, nrm, b, 1, 0);
        v(vc, pos, nrm, c, 1, 1);
        v(vc, pos, nrm, d, 0, 1);
        // back side
        v(vc, pos, nrm, d, 0, 1);
        v(vc, pos, nrm, c, 1, 1);
        v(vc, pos, nrm, b, 1, 0);
        v(vc, pos, nrm, a, 0, 0);
    }

    private static void v(VertexConsumer vc, Matrix4f pos, Matrix3f nrm, Vector3f p, float u, float vv) {
        vc.vertex(pos, p.x, p.y, p.z).color(1f, 0.9f, 0.6f, 1f).texture(u, vv).overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0).normal(nrm, 0, 1, 0).next();
    }
}
