package com.bnnn.polyfield.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Short-lived bullet tracers, drawn as camera-facing additive streaks. */
public final class Tracers {
    private static final RenderLayer LAYER = RenderLayer.getEyes(PfModel.WHITE);
    private static final List<Tracer> TRACERS = new ArrayList<>();

    private record Tracer(Vec3d from, Vec3d to, long born) {
    }

    private Tracers() {
    }

    public static void add(Vec3d from, Vec3d to) {
        if (from.squaredDistanceTo(to) < 1.0) {
            return;
        }
        if (TRACERS.size() > 256) {
            TRACERS.remove(0);
        }
        TRACERS.add(new Tracer(from, to, System.nanoTime()));
    }

    public static void render(WorldRenderContext ctx) {
        if (TRACERS.isEmpty()) {
            return;
        }
        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();
        MatrixStack matrices = ctx.matrixStack();
        VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = immediate.getBuffer(LAYER);
        long now = System.nanoTime();
        matrices.push();
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f nrm = matrices.peek().getNormalMatrix();
        Iterator<Tracer> it = TRACERS.iterator();
        while (it.hasNext()) {
            Tracer t = it.next();
            float age = (now - t.born) / 1.0e9f;
            if (age > 0.09f) {
                it.remove();
                continue;
            }
            // a ~25 m streak travelling from the muzzle to the impact
            Vec3d dir = t.to.subtract(t.from);
            double len = dir.length();
            Vec3d d = dir.multiply(1.0 / len);
            double head = Math.min(len, 20.0 + age * 900.0);
            double tail = Math.max(0.0, head - 25.0);
            Vec3d a = t.from.add(d.multiply(tail)).subtract(cam);
            Vec3d b = t.from.add(d.multiply(head)).subtract(cam);
            Vec3d side = d.crossProduct(a.add(b).multiply(0.5)).normalize().multiply(0.025);
            float alpha = 1.0f - age / 0.09f;
            quad(vc, pos, nrm, a.subtract(side), a.add(side), b.add(side), b.subtract(side), alpha);
        }
        matrices.pop();
        immediate.draw(LAYER);
    }

    private static void quad(VertexConsumer vc, Matrix4f pos, Matrix3f nrm, Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4, float alpha) {
        v(vc, pos, nrm, p1, 0, 0, alpha * 0.4f);
        v(vc, pos, nrm, p2, 1, 0, alpha * 0.4f);
        v(vc, pos, nrm, p3, 1, 1, alpha);
        v(vc, pos, nrm, p4, 0, 1, alpha);
        v(vc, pos, nrm, p4, 0, 1, alpha);
        v(vc, pos, nrm, p3, 1, 1, alpha);
        v(vc, pos, nrm, p2, 1, 0, alpha * 0.4f);
        v(vc, pos, nrm, p1, 0, 0, alpha * 0.4f);
    }

    private static void v(VertexConsumer vc, Matrix4f pos, Matrix3f nrm, Vec3d p, float u, float v, float a) {
        vc.vertex(pos, (float) p.x, (float) p.y, (float) p.z).color(1.0f * a, 0.85f * a, 0.45f * a, 1.0f).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(nrm, 0, 1, 0).next();
    }

    public static void clear() {
        TRACERS.clear();
    }
}
