package com.bnnn.polyfield.client;

import com.bnnn.polyfield.PfEntities;
import com.bnnn.polyfield.PfItems;
import com.bnnn.polyfield.client.render.GrenadeItemRenderer;
import com.bnnn.polyfield.client.render.GunItemRenderer;
import com.bnnn.polyfield.client.render.PfModels;
import com.bnnn.polyfield.client.render.ProjectileRenderers;
import com.bnnn.polyfield.client.render.SoldierRenderer;
import com.bnnn.polyfield.client.render.TankRenderer;
import com.bnnn.polyfield.client.render.Tracers;
import com.bnnn.polyfield.client.sound.GunSounds;
import com.bnnn.polyfield.client.sound.TankSounds;
import com.bnnn.polyfield.net.PfNetwork;
import com.bnnn.polyfield.weapon.Weapon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class PolyfieldClient implements ClientModInitializer {
    public static KeyBinding reloadKey;

    @Override
    public void onInitializeClient() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new PfModels());

        EntityRendererRegistry.register(PfEntities.GERMAN_SOLDIER, ctx -> new SoldierRenderer(ctx, "soldier_ger"));
        EntityRendererRegistry.register(PfEntities.ALLIED_SOLDIER, ctx -> new SoldierRenderer(ctx, "soldier_usa"));
        EntityRendererRegistry.register(PfEntities.SHERMAN, TankRenderer::new);
        EntityRendererRegistry.register(PfEntities.JAGDPANTHER, TankRenderer::new);
        EntityRendererRegistry.register(PfEntities.ROCKET, ProjectileRenderers.Rocket::new);
        EntityRendererRegistry.register(PfEntities.GRENADE, ProjectileRenderers.Grenade::new);

        for (Weapon w : Weapon.values()) {
            BuiltinItemRendererRegistry.INSTANCE.register(w.item(), new GunItemRenderer(w));
        }
        BuiltinItemRendererRegistry.INSTANCE.register(PfItems.GRENADE_M24, new GrenadeItemRenderer("grenade_m24", 1.0f));
        BuiltinItemRendererRegistry.INSTANCE.register(PfItems.GRENADE_MK2, new GrenadeItemRenderer("grenade_mk2", 1.0f));
        BuiltinItemRendererRegistry.INSTANCE.register(PfItems.SHERMAN, new GrenadeItemRenderer("sherman", 0.08f));
        BuiltinItemRendererRegistry.INSTANCE.register(PfItems.JAGDPANTHER, new GrenadeItemRenderer("jagdpanther", 0.08f));

        reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.polyfield.reload", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R, "category.polyfield"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientGunState.tick(client);
            GunSounds.tick();
            TankSounds.tick();
            while (reloadKey.wasPressed()) {
                if (client.player != null && ClientGunState.heldWeapon(client.player) != null) {
                    ClientPlayNetworking.send(PfNetwork.RELOAD, PacketByteBufs.empty());
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GunSounds.clear();
            Tracers.clear();
            com.bnnn.polyfield.client.render.MapRenderer.clear();
        });

        WorldRenderEvents.BEFORE_ENTITIES.register(com.bnnn.polyfield.client.render.MapRenderer::render);
        WorldRenderEvents.LAST.register(Tracers::render);
        ClientPlayNetworking.registerGlobalReceiver(com.bnnn.polyfield.map.PfMap.STATE_PACKET, (client, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            int x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
            client.execute(() -> com.bnnn.polyfield.client.render.MapRenderer.setState(on, x, y, z));
        });
        ClientSelfTest.init();
        HudRenderCallback.EVENT.register(PfHud::render);

        ClientPlayNetworking.registerGlobalReceiver(PfNetwork.SHOT, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            Weapon w = Weapon.byOrdinal(buf.readByte());
            Vec3d end = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            int hit = buf.readByte();
            client.execute(() -> onShot(client, id, w, end, hit));
        });
        ClientPlayNetworking.registerGlobalReceiver(PfNetwork.HIT, (client, handler, buf, sender) -> {
            int flags = buf.readByte();
            client.execute(() -> {
                ClientGunState.onHit(flags);
                if (client.player != null) {
                    client.player.playSound((flags & PfNetwork.HIT_HEADSHOT) != 0 ? com.bnnn.polyfield.PfSounds.HEADSHOT
                            : com.bnnn.polyfield.PfSounds.HITMARKER, 0.7f, 1.0f);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(PfNetwork.RELOAD_ANIM, (client, handler, buf, sender) -> {
            int id = buf.readVarInt();
            int ticks = buf.readVarInt();
            client.execute(() -> {
                if (client.world != null) {
                    Entity e = client.world.getEntityById(id);
                    if (e != null) {
                        ClientGunState.onReload(e, ticks);
                    }
                }
            });
        });
    }

    private static void onShot(MinecraftClient client, int id, Weapon w, Vec3d end, int hit) {
        if (client.world == null) {
            return;
        }
        Entity shooter = client.world.getEntityById(id);
        if (shooter == null) {
            return;
        }
        ClientGunState.onShot(shooter, w);
        GunSounds.onShot(shooter, w);
        if (!w.isLauncher()) {
            Tracers.add(muzzleGuess(client, shooter), end);
        }
    }

    /** Where tracers start: roughly the muzzle of the held weapon. */
    private static Vec3d muzzleGuess(MinecraftClient client, Entity shooter) {
        float td = client.getTickDelta();
        Vec3d look = shooter.getRotationVec(td);
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : right.normalize();
        Vec3d eye = shooter.getCameraPosVec(td);
        if (shooter == client.player && client.options.getPerspective().isFirstPerson()) {
            boolean aim = ClientGunState.aiming;
            return eye.add(look.multiply(1.2)).add(right.multiply(aim ? 0.0 : 0.22)).add(0, aim ? -0.06 : -0.2, 0);
        }
        return eye.add(look.multiply(1.0)).add(right.multiply(0.25)).add(0, -0.3, 0);
    }
}
