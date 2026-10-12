package com.bnnn.polyfield.client.render;

import com.bnnn.polyfield.client.ClientGunState;
import com.bnnn.polyfield.weapon.Weapon;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/**
 * Draws a Polyfield weapon model for every item display context: first
 * person (hip and aimed down sights, recoil and reload motion), third person
 * in the hand, inventory icon, dropped item and item frame.
 */
public class GunItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private final Weapon weapon;

    public GunItemRenderer(Weapon weapon) {
        this.weapon = weapon;
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vcp, int light,
                       int overlay) {
        PfModel model = PfModels.get(weapon.id);
        if (model == null) {
            return;
        }
        float tickDelta = MinecraftClient.getInstance().getTickDelta();
        matrices.push();
        matrices.translate(0.5f, 0.5f, 0.5f);
        Entity holder = ClientGunState.renderingEntity;
        switch (mode) {
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                firstPerson(model, mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND, matrices, holder, tickDelta);
                model.render(matrices, vcp, light, overlay);
                MuzzleFlash.render(model, holder, matrices, vcp, tickDelta, 1.0f);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                thirdPerson(model, matrices, holder, tickDelta);
                model.render(matrices, vcp, light, overlay);
                MuzzleFlash.render(model, holder, matrices, vcp, tickDelta, 1.0f);
            }
            case GUI -> {
                icon(model, matrices, 0.95f);
                model.render(matrices, vcp, light, overlay);
            }
            case FIXED -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
                icon(model, matrices, 0.8f);
                model.render(matrices, vcp, light, overlay);
            }
            default -> {
                // dropped item, head slot
                float s = Math.min(0.9f, 0.9f / Math.max(0.3f, model.length()));
                matrices.scale(s, s, s);
                Vector3f c = center(model);
                matrices.translate(-c.x, -c.y, -c.z);
                model.render(matrices, vcp, light, overlay);
            }
        }
        matrices.pop();
    }

    static Vector3f center(PfModel m) {
        return new Vector3f((m.min.x + m.max.x) / 2, (m.min.y + m.max.y) / 2, (m.min.z + m.max.z) / 2);
    }

    private void icon(PfModel m, MatrixStack matrices, float fit) {
        // side view, muzzle up-right like a vanilla tool icon
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(weapon.kind == Weapon.Kind.PISTOL ? 0f : 35f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10f));
        float size = Math.max(m.length(), m.max.y - m.min.y);
        float s = fit / Math.max(0.05f, size);
        matrices.scale(s, s, s);
        Vector3f c = center(m);
        matrices.translate(-c.x, -c.y, -c.z);
    }

    private void firstPerson(PfModel m, boolean left, MatrixStack matrices, Entity holder, float tickDelta) {
        float aim = ClientGunState.aimProgress(tickDelta);
        float kick = ClientGunState.kick(tickDelta);
        float reload = holder != null ? ClientGunState.reloadProgress(holder, tickDelta) : -1f;
        float side = left ? -1f : 1f;
        // vanilla puts the origin at (0.56, -0.52, -0.72) in view space; move the grip to a hip position
        Vector3f muzzle = m.anchor("muzzle", new Vector3f(0, 0.06f, m.max.z));
        float sightY = m.anchor("sight", new Vector3f(0, muzzle.y + 0.035f, 0)).y;
        if (weapon.kind == Weapon.Kind.LAUNCHER) {
            sightY = Math.max(sightY, muzzle.y + 0.09f);
        }
        boolean pistol = weapon.kind == Weapon.Kind.PISTOL;
        float hipX = side * -0.24f;
        float hipY = pistol ? 0.2f : 0.16f;
        float hipZ = pistol ? 0.0f : 0.12f;
        float adsX = side * -0.56f;
        float adsY = 0.52f - sightY;
        float adsZ = 0.0f;
        matrices.translate(MathHelper.lerp(aim, hipX, adsX), MathHelper.lerp(aim, hipY, adsY), MathHelper.lerp(aim, hipZ, adsZ));
        // recoil: back and up
        matrices.translate(0, kick * 0.01f * weapon.recoilPitch * (1f - 0.5f * aim), kick * 0.012f * weapon.kick);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(kick * weapon.recoilPitch * 1.5f * (1f - 0.6f * aim)));
        if (reload >= 0f) {
            float r = MathHelper.sin(reload * (float) Math.PI);
            matrices.translate(0, -0.12f * r, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-28f * r));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 22f * r));
        }
        // gun points along +Z in its own space; the camera looks down -Z
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
        if (aim < 0.5f) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -2.5f * (1f - aim * 2f)));
        }
    }

    private void thirdPerson(PfModel m, MatrixStack matrices, Entity holder, float tickDelta) {
        // the hand frame: -Z along the arm (forward with the crossbow pose), +Y up
        matrices.translate(0f, -0.06f, 0.06f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
        float kick = holder != null ? Math.max(0f, 1f - ClientGunState.sinceShot(holder, tickDelta) / 2f) : 0f;
        matrices.translate(0, 0, -kick * 0.04f);
    }
}
