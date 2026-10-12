package com.bnnn.polyfield.client.render;

import com.bnnn.polyfield.PolyfieldMod;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A static Polyfield mesh (PFM1, written by converter/extract_polyfield.py):
 * parts with a texture (or flat material colour), a group id (0 hull/body,
 * 1 turret, 2 gun) and named anchor points. Units are metres = blocks,
 * +Y up, +Z forward.
 */
public final class PfModel {
    public static final Identifier WHITE = PolyfieldMod.id("textures/misc/white.png");

    public final Vector3f min;
    public final Vector3f max;
    public final Map<String, Vector3f> anchors = new HashMap<>();
    public final List<Part> parts = new ArrayList<>();

    public static final class Part {
        public final Identifier texture;
        public final RenderLayer layer;
        public final float r;
        public final float g;
        public final float b;
        public final float a;
        public final int group;
        /** x y z nx ny nz u v per vertex. */
        public final float[] verts;
        public final int[] tris;

        Part(Identifier texture, float r, float g, float b, float a, int group, float[] verts, int[] tris) {
            this.texture = texture;
            this.layer = RenderLayer.getEntityCutout(texture);
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.group = group;
            this.verts = verts;
            this.tris = tris;
        }
    }

    private PfModel(Vector3f min, Vector3f max) {
        this.min = min;
        this.max = max;
    }

    static String readUtf(DataInputStream in) throws IOException {
        int n = in.readUnsignedShort();
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    static Identifier textureId(String s) {
        return s.isEmpty() ? WHITE : new Identifier(s);
    }

    public static PfModel read(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(stream));
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'P' || magic[1] != 'F' || magic[2] != 'M' || magic[3] != '1') {
            throw new IOException("not a PFM1 model");
        }
        Vector3f min = new Vector3f(in.readFloat(), in.readFloat(), in.readFloat());
        Vector3f max = new Vector3f(in.readFloat(), in.readFloat(), in.readFloat());
        PfModel m = new PfModel(min, max);
        int na = in.readInt();
        for (int i = 0; i < na; i++) {
            String name = readUtf(in);
            m.anchors.put(name, new Vector3f(in.readFloat(), in.readFloat(), in.readFloat()));
        }
        int np = in.readInt();
        for (int i = 0; i < np; i++) {
            Identifier tex = textureId(readUtf(in));
            float r = in.readFloat();
            float g = in.readFloat();
            float b = in.readFloat();
            float a = in.readFloat();
            int group = in.readUnsignedByte();
            int nv = in.readInt();
            float[] v = new float[nv * 8];
            for (int k = 0; k < v.length; k++) {
                v[k] = in.readFloat();
            }
            int nt = in.readInt();
            int[] t = new int[nt * 3];
            for (int k = 0; k < t.length; k++) {
                t[k] = in.readInt();
            }
            m.parts.add(new Part(tex, r, g, b, a, group, v, t));
        }
        return m;
    }

    public Vector3f anchor(String name, Vector3f fallback) {
        Vector3f v = anchors.get(name);
        return v != null ? v : fallback;
    }

    public float length() {
        return max.z - min.z;
    }

    public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay) {
        for (Part p : parts) {
            renderPart(p, matrices, vcp, light, overlay, 1.0f);
        }
    }

    public void renderGroup(int group, MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay) {
        for (Part p : parts) {
            if (p.group == group) {
                renderPart(p, matrices, vcp, light, overlay, 1.0f);
            }
        }
    }

    public static void renderPart(Part p, MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay, float tint) {
        MatrixStack.Entry e = matrices.peek();
        Matrix4f pos = e.getPositionMatrix();
        Matrix3f nrm = e.getNormalMatrix();
        VertexConsumer vc = vcp.getBuffer(p.layer);
        float r = Math.min(1f, p.r * tint);
        float g = Math.min(1f, p.g * tint);
        float b = Math.min(1f, p.b * tint);
        float[] v = p.verts;
        int[] t = p.tris;
        for (int i = 0; i < t.length; i += 3) {
            emit(vc, pos, nrm, v, t[i], r, g, b, p.a, light, overlay);
            emit(vc, pos, nrm, v, t[i + 1], r, g, b, p.a, light, overlay);
            emit(vc, pos, nrm, v, t[i + 2], r, g, b, p.a, light, overlay);
            emit(vc, pos, nrm, v, t[i + 2], r, g, b, p.a, light, overlay);
        }
    }

    private static void emit(VertexConsumer vc, Matrix4f pos, Matrix3f nrm, float[] v, int idx, float r, float g, float b, float a,
                             int light, int overlay) {
        int o = idx * 8;
        vc.vertex(pos, v[o], v[o + 1], v[o + 2])
                .color(r, g, b, a)
                .texture(v[o + 6], v[o + 7])
                .overlay(overlay)
                .light(light)
                .normal(nrm, v[o + 3], v[o + 4], v[o + 5])
                .next();
    }
}
