package com.bnnn.polyfield.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws Polyfield's Normandy map (terrain and every building, ~300k triangles)
 * from static GPU buffers with Minecraft's cutout block shader, so day/night,
 * fog and the lightmap apply as to normal terrain.
 */
public final class MapRenderer {
    private static boolean active;
    private static double ox;
    private static double oy;
    private static double oz;
    private static final List<Batch> BATCHES = new ArrayList<>();
    private static PfModel built;

    private record Batch(Identifier texture, VertexBuffer buffer) {
    }

    private MapRenderer() {
    }

    public static void setState(boolean on, int x, int y, int z) {
        active = on;
        ox = x;
        oy = y;
        oz = z;
    }

    public static void clear() {
        active = false;
        for (Batch b : BATCHES) {
            b.buffer.close();
        }
        BATCHES.clear();
        built = null;
    }

    private static void build(PfModel m) {
        for (Batch b : BATCHES) {
            b.buffer.close();
        }
        BATCHES.clear();
        int light = LightmapTextureManager.pack(0, 15);
        float lx = 0.4f, ly = 0.8f, lz = 0.3f;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        for (PfModel.Part p : m.parts) {
            BufferBuilder bb = new BufferBuilder(p.tris.length * 32 + 256);
            bb.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
            float[] v = p.verts;
            for (int idx : p.tris) {
                int o = idx * 8;
                float shade = 0.55f + 0.45f * Math.max(0f, (v[o + 3] * lx + v[o + 4] * ly + v[o + 5] * lz) / ll);
                bb.vertex(v[o], v[o + 1], v[o + 2])
                        .color(Math.min(1f, p.r * shade), Math.min(1f, p.g * shade), Math.min(1f, p.b * shade), p.a)
                        .texture(v[o + 6], v[o + 7])
                        .light(light)
                        .normal(v[o + 3], v[o + 4], v[o + 5])
                        .next();
            }
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(bb.end());
            VertexBuffer.unbind();
            BATCHES.add(new Batch(p.texture, vb));
        }
        built = m;
    }

    public static void render(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active || client.world == null || client.world.getRegistryKey() != World.OVERWORLD) {
            return;
        }
        PfModel m = PfModels.get("map_normandy");
        if (m == null) {
            return;
        }
        if (m != built) {
            build(m);
        }
        Vec3d cam = ctx.camera().getPos();
        MatrixStack ms = ctx.matrixStack();
        ms.push();
        ms.translate(ox - cam.x, oy - cam.y, oz - cam.z);
        Matrix4f mv = ms.peek().getPositionMatrix();
        LightmapTextureManager lm = client.gameRenderer.getLightmapTextureManager();
        lm.enable();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRenderTypeCutoutProgram);
        ShaderProgram shader = RenderSystem.getShader();
        if (shader != null && shader.chunkOffset != null) {
            shader.chunkOffset.set(0f, 0f, 0f);
        }
        for (Batch b : BATCHES) {
            RenderSystem.setShaderTexture(0, b.texture);
            b.buffer.bind();
            b.buffer.draw(mv, ctx.projectionMatrix(), shader);
        }
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        lm.disable();
        ms.pop();
    }
}
