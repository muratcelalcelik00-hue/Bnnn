package com.bnnn.polyfield.client.render;

import com.bnnn.polyfield.client.ClientGunState;
import com.bnnn.polyfield.entity.SoldierEntity;
import com.bnnn.polyfield.weapon.Weapon;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Polyfield's soldier mesh, skinned on the CPU. Polyfield's own animation
 * clips are Mecanim humanoid muscle curves, so the pose is procedural: legs
 * swing with the walk cycle, the upper body pitches with the aim and both
 * arms reach the weapon with a two-bone IK.
 *
 * Model frame: origin between the feet, +Y up, +Z forward, right = -X.
 */
public class SoldierModel extends EntityModel<SoldierEntity> {
    public static final float SCALE = 0.93f;
    private final String modelName;
    private PfSkinnedModel cached;
    private Matrix4f[] world;
    private Matrix4f[] skin;
    /** Weapon placement (grip position and barrel rotation, barrel +Z) in the model frame. */
    public final Vector3f gunPos = new Vector3f();
    public final Quaternionf gunRot = new Quaternionf();
    public boolean hasPose;

    private int hips, spine1, spine2, neck, head, lUpLeg, lLeg, rUpLeg, rLeg, lArm, lFore, lHand, rArm, rFore, rHand;

    public SoldierModel(String modelName) {
        super(RenderLayer::getEntityCutoutNoCull);
        this.modelName = modelName;
    }

    public PfSkinnedModel mesh() {
        PfSkinnedModel m = PfModels.skinned(modelName);
        if (m != cached) {
            cached = m;
            if (m != null) {
                world = new Matrix4f[m.parents.length];
                skin = new Matrix4f[m.parents.length];
                for (int i = 0; i < world.length; i++) {
                    world[i] = new Matrix4f();
                    skin[i] = new Matrix4f();
                }
                hips = m.bone("Hips");
                spine1 = m.bone("Spine1");
                spine2 = m.bone("Spine2");
                neck = m.bone("Neck");
                head = m.bone("Head");
                lUpLeg = m.bone("LeftUpLeg");
                lLeg = m.bone("LeftLeg");
                rUpLeg = m.bone("RightUpLeg");
                rLeg = m.bone("RightLeg");
                lArm = m.bone("LeftArm");
                lFore = m.bone("LeftForeArm");
                lHand = m.bone("LeftHand");
                rArm = m.bone("RightArm");
                rFore = m.bone("RightForeArm");
                rHand = m.bone("RightHand");
            }
        }
        return m;
    }

    @Override
    public void setAngles(SoldierEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
                          float headPitch) {
        PfSkinnedModel m = mesh();
        hasPose = false;
        if (m == null) {
            return;
        }
        float tickDelta = animationProgress - entity.age;
        Weapon w = entity.weapon();
        boolean aiming = entity.isAttacking();
        float reload = ClientGunState.reloadProgress(entity, tickDelta);
        float since = ClientGunState.sinceShot(entity, tickDelta);
        float recoil = since < 3f ? (1f - since / 3f) : 0f;

        float pitch = (float) Math.toRadians(MathHelper.clamp(headPitch, -60f, 60f));
        float yaw = (float) Math.toRadians(MathHelper.clamp(headYaw, -70f, 70f));
        float aimPitch = aiming ? pitch : pitch * 0.3f + 0.55f;
        if (reload >= 0f) {
            aimPitch = 0.6f * MathHelper.sin(reload * (float) Math.PI) + aimPitch * (1f - MathHelper.sin(reload * (float) Math.PI));
        }
        aimPitch -= recoil * 0.06f * (w != null ? w.recoilPitch : 1f) * 0.5f;
        float phase = limbAngle * 0.6662f;
        float amt = Math.min(1.0f, limbDistance);

        // hand targets in the model frame
        Vector3f chest = new Vector3f(0, 1.30f, 0);
        Quaternionf aimRot = new Quaternionf().rotateY(-yaw).rotateX(aimPitch);
        boolean pistol = w != null && w.kind == Weapon.Kind.PISTOL;
        boolean launcher = w != null && w.kind == Weapon.Kind.LAUNCHER;
        Vector3f gripLocal = pistol ? new Vector3f(-0.06f, 0.10f, 0.46f)
                : launcher ? new Vector3f(-0.14f, 0.14f, 0.18f)
                : new Vector3f(-0.12f, 0.03f, 0.32f);
        gripLocal.z -= recoil * 0.04f;
        Vector3f grip = aimRot.transform(new Vector3f(gripLocal)).add(chest);
        Vector3f supportLocal = pistol ? new Vector3f(gripLocal).add(0.05f, -0.03f, -0.01f)
                : new Vector3f(gripLocal).add(0.07f, -0.04f, launcher ? 0.34f : 0.28f);
        if (reload >= 0f && !pistol) {
            float r = MathHelper.sin(reload * (float) Math.PI);
            supportLocal.add(0.05f * r, -0.12f * r, -0.18f * r);
        }
        Vector3f support = aimRot.transform(new Vector3f(supportLocal)).add(chest);
        gunPos.set(grip);
        gunRot.set(aimRot);

        Matrix4f[] rest = m.restLocal;
        int[] parents = m.parents;
        Matrix4f tmp = new Matrix4f();
        for (int i = 0; i < parents.length; i++) {
            if (parents[i] < 0) {
                world[i].set(rest[i]);
            } else {
                world[parents[i]].mul(rest[i], world[i]);
            }
            if (i == spine1) {
                pivot(world[i], new Quaternionf().rotateY(-yaw * 0.5f));
            } else if (i == spine2) {
                pivot(world[i], new Quaternionf().rotateY(-yaw * 0.5f).rotateX(aimPitch * 0.6f));
            } else if (i == neck) {
                pivot(world[i], new Quaternionf().rotateX((pitch - aimPitch * 0.6f) * 0.7f));
            } else if (i == lUpLeg || i == rUpLeg) {
                float s = i == lUpLeg ? 1f : -1f;
                pivot(world[i], new Quaternionf().rotateX(-s * MathHelper.cos(phase) * 0.7f * amt));
            } else if (i == lLeg || i == rLeg) {
                float s = i == lLeg ? 1f : -1f;
                pivot(world[i], new Quaternionf().rotateX(Math.max(0f, s * MathHelper.sin(phase)) * 1.0f * amt));
            } else if (w != null && (i == rArm || i == lArm)) {
                boolean right = i == rArm;
                int fore = right ? rFore : lFore;
                int hand = right ? rHand : lHand;
                Vector3f target = right ? grip : support;
                Vector3f s = world[i].getTranslation(new Vector3f());
                Matrix4f foreW = world[i].mul(rest[fore], tmp);
                Vector3f e = foreW.getTranslation(new Vector3f());
                Vector3f h = new Matrix4f(foreW).mul(rest[hand]).getTranslation(new Vector3f());
                Vector3f pole = new Vector3f(right ? -0.6f : 0.6f, -1.0f, -0.2f);
                Vector3f eNew = solveElbow(s, e, h, target, pole);
                pivot(world[i], new Quaternionf().rotationTo(new Vector3f(e).sub(s), new Vector3f(eNew).sub(s)));
            } else if (w != null && (i == rFore || i == lFore)) {
                boolean right = i == rFore;
                int hand = right ? rHand : lHand;
                Vector3f target = right ? grip : support;
                Vector3f e = world[i].getTranslation(new Vector3f());
                Vector3f h = world[i].mul(rest[hand], tmp).getTranslation(new Vector3f());
                pivot(world[i], new Quaternionf().rotationTo(new Vector3f(h).sub(e), new Vector3f(target).sub(e)));
            }
        }
        hasPose = true;
    }

    /** Rotates a bone's world matrix about its own joint, in model axes. */
    private static void pivot(Matrix4f w, Quaternionf q) {
        Vector3f p = w.getTranslation(new Vector3f());
        new Matrix4f().translation(p).rotate(q).translate(-p.x, -p.y, -p.z).mul(w, w);
    }

    private static Vector3f solveElbow(Vector3f s, Vector3f e, Vector3f h, Vector3f target, Vector3f pole) {
        float l1 = e.distance(s);
        float l2 = h.distance(e);
        Vector3f d = new Vector3f(target).sub(s);
        float dist = MathHelper.clamp(d.length(), 1e-4f, l1 + l2 - 1e-4f);
        Vector3f dn = d.normalize();
        float a = (l1 * l1 - l2 * l2 + dist * dist) / (2f * dist);
        float hgt = (float) Math.sqrt(Math.max(l1 * l1 - a * a, 0f));
        Vector3f pp = new Vector3f(pole).sub(new Vector3f(dn).mul(pole.dot(dn)));
        if (pp.lengthSquared() < 1e-8f) {
            pp.set(0, -1, 0);
        }
        pp.normalize();
        return new Vector3f(s).add(new Vector3f(dn).mul(a)).add(pp.mul(hgt));
    }

    /** Converts the vanilla living-entity model frame (Y down, facing -Z) to ours. */
    public static void toModelFrame(MatrixStack matrices) {
        matrices.translate(0.0f, 1.501f, 0.0f);
        matrices.scale(-1.0f, -1.0f, 1.0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
        matrices.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vc, int light, int overlay, float red, float green, float blue,
                       float alpha) {
        PfSkinnedModel m = cached;
        if (m == null || !hasPose) {
            return;
        }
        matrices.push();
        toModelFrame(matrices);
        Matrix4f pos = matrices.peek().getPositionMatrix();
        Matrix3f nrm = matrices.peek().getNormalMatrix();
        Vector4f p = new Vector4f();
        Vector4f acc = new Vector4f();
        Vector3f n = new Vector3f();
        Vector3f nacc = new Vector3f();
        for (PfSkinnedModel.Part part : m.parts) {
            for (int b = 0; b < skin.length; b++) {
                world[b].mul(part.invBind[b], skin[b]);
            }
            float[] v = part.verts;
            int nv = v.length / 8;
            float[] out = new float[nv * 6];
            for (int i = 0; i < nv; i++) {
                acc.set(0, 0, 0, 0);
                nacc.set(0, 0, 0);
                for (int k = 0; k < 4; k++) {
                    float wgt = part.weights[i * 4 + k];
                    if (wgt <= 0f) {
                        continue;
                    }
                    Matrix4f sm = skin[part.bones[i * 4 + k] & 0xFF];
                    p.set(v[i * 8], v[i * 8 + 1], v[i * 8 + 2], 1f);
                    sm.transform(p);
                    acc.add(p.mul(wgt));
                    n.set(v[i * 8 + 3], v[i * 8 + 4], v[i * 8 + 5]);
                    sm.transformDirection(n);
                    nacc.add(n.mul(wgt));
                }
                if (nacc.lengthSquared() > 1e-12f) {
                    nacc.normalize();
                }
                out[i * 6] = acc.x;
                out[i * 6 + 1] = acc.y;
                out[i * 6 + 2] = acc.z;
                out[i * 6 + 3] = nacc.x;
                out[i * 6 + 4] = nacc.y;
                out[i * 6 + 5] = nacc.z;
            }
            float r = part.r * red;
            float g = part.g * green;
            float bl = part.b * blue;
            float a = part.a * alpha;
            int[] t = part.tris;
            for (int i = 0; i < t.length; i += 3) {
                emit(vc, pos, nrm, out, v, t[i], r, g, bl, a, light, overlay);
                emit(vc, pos, nrm, out, v, t[i + 1], r, g, bl, a, light, overlay);
                emit(vc, pos, nrm, out, v, t[i + 2], r, g, bl, a, light, overlay);
                emit(vc, pos, nrm, out, v, t[i + 2], r, g, bl, a, light, overlay);
            }
        }
        matrices.pop();
    }

    private static void emit(VertexConsumer vc, Matrix4f pos, Matrix3f nrm, float[] out, float[] v, int idx, float r, float g,
                             float b, float a, int light, int overlay) {
        int o = idx * 6;
        vc.vertex(pos, out[o], out[o + 1], out[o + 2])
                .color(r, g, b, a)
                .texture(v[idx * 8 + 6], v[idx * 8 + 7])
                .overlay(overlay)
                .light(light)
                .normal(nrm, out[o + 3], out[o + 4], out[o + 5])
                .next();
    }
}
