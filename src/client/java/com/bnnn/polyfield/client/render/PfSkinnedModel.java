package com.bnnn.polyfield.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Polyfield's skinned soldier (PFS1): the Mixamo skeleton (rest pose local
 * transforms) and parts with up to four bone weights per vertex and their own
 * inverse bind matrices. Posed on the CPU by {@link SoldierModel}.
 */
public final class PfSkinnedModel {
    public final String[] boneNames;
    public final int[] parents;
    public final Matrix4f[] restLocal;
    public final Part[] parts;
    private final Map<String, Integer> index = new HashMap<>();

    public static final class Part {
        public final Identifier texture;
        public final RenderLayer layer;
        public final float r;
        public final float g;
        public final float b;
        public final float a;
        public final float[] verts;
        public final byte[] bones;
        public final float[] weights;
        public final int[] tris;
        public final Matrix4f[] invBind;

        Part(Identifier texture, float r, float g, float b, float a, float[] verts, byte[] bones, float[] weights, int[] tris,
             Matrix4f[] invBind) {
            this.texture = texture;
            this.layer = RenderLayer.getEntityCutoutNoCull(texture);
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.verts = verts;
            this.bones = bones;
            this.weights = weights;
            this.tris = tris;
            this.invBind = invBind;
        }
    }

    private PfSkinnedModel(String[] names, int[] parents, Matrix4f[] rest, Part[] parts) {
        this.boneNames = names;
        this.parents = parents;
        this.restLocal = rest;
        this.parts = parts;
        for (int i = 0; i < names.length; i++) {
            index.put(names[i], i);
        }
    }

    public int bone(String name) {
        return index.getOrDefault(name, -1);
    }

    public Identifier texture() {
        return parts.length > 0 ? parts[0].texture : PfModel.WHITE;
    }

    public static PfSkinnedModel read(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(stream));
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'P' || magic[1] != 'F' || magic[2] != 'S' || magic[3] != '1') {
            throw new IOException("not a PFS1 model");
        }
        int nb = in.readInt();
        String[] names = new String[nb];
        int[] parents = new int[nb];
        Matrix4f[] rest = new Matrix4f[nb];
        for (int i = 0; i < nb; i++) {
            names[i] = PfModel.readUtf(in);
            parents[i] = in.readInt();
            float px = in.readFloat(), py = in.readFloat(), pz = in.readFloat();
            float qx = in.readFloat(), qy = in.readFloat(), qz = in.readFloat(), qw = in.readFloat();
            float sx = in.readFloat(), sy = in.readFloat(), sz = in.readFloat();
            rest[i] = new Matrix4f().translationRotateScale(px, py, pz, qx, qy, qz, qw, sx, sy, sz);
        }
        int np = in.readInt();
        Part[] parts = new Part[np];
        for (int i = 0; i < np; i++) {
            Identifier tex = PfModel.textureId(PfModel.readUtf(in));
            float r = in.readFloat(), g = in.readFloat(), b = in.readFloat(), a = in.readFloat();
            int nv = in.readInt();
            float[] v = new float[nv * 8];
            for (int k = 0; k < v.length; k++) {
                v[k] = in.readFloat();
            }
            byte[] bi = new byte[nv * 4];
            in.readFully(bi);
            float[] w = new float[nv * 4];
            for (int k = 0; k < w.length; k++) {
                w[k] = in.readFloat();
            }
            int nt = in.readInt();
            int[] t = new int[nt * 3];
            for (int k = 0; k < t.length; k++) {
                t[k] = in.readInt();
            }
            Matrix4f[] inv = new Matrix4f[nb];
            float[] m = new float[16];
            for (int k = 0; k < nb; k++) {
                for (int j = 0; j < 16; j++) {
                    m[j] = in.readFloat();
                }
                inv[k] = new Matrix4f().set(m).transpose();
            }
            parts[i] = new Part(tex, r, g, b, a, v, bi, w, t, inv);
        }
        return new PfSkinnedModel(names, parents, rest, parts);
    }
}
