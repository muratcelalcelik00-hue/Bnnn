package com.bnnn.polyfield.client;

import com.bnnn.polyfield.entity.TankEntity;
import com.bnnn.polyfield.net.PfNetwork;
import com.bnnn.polyfield.weapon.GunItem;
import com.bnnn.polyfield.weapon.Weapon;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Client-side input (trigger/aim), recoil and per-entity animation timers. */
public final class ClientGunState {
    private static boolean sentTrigger;
    private static boolean sentAim;
    public static boolean aiming;
    private static float aim;
    private static float prevAim;
    private static float kick;
    private static float prevKick;
    public static int hitMarker;
    public static int hitFlags;
    private static long clientTicks;
    private static final Map<Integer, Long> LAST_SHOT = new HashMap<>();
    private static final Map<Integer, long[]> RELOADS = new HashMap<>();
    /** The entity whose held item is being rendered right now (set by mixins). */
    @Nullable
    public static Entity renderingEntity;

    private ClientGunState() {
    }

    @Nullable
    public static Weapon heldWeapon(@Nullable Entity e) {
        if (e instanceof net.minecraft.entity.LivingEntity le) {
            ItemStack s = le.getMainHandStack();
            if (s.getItem() instanceof GunItem g) {
                return g.weapon;
            }
        }
        return null;
    }

    public static void tick(MinecraftClient client) {
        clientTicks++;
        prevAim = aim;
        prevKick = kick;
        kick *= 0.55f;
        if (hitMarker > 0) {
            hitMarker--;
        }
        ClientPlayerEntity p = client.player;
        if (p == null || client.getNetworkHandler() == null) {
            sentTrigger = false;
            sentAim = false;
            aiming = false;
            return;
        }
        boolean free = client.currentScreen == null && client.getOverlay() == null;
        Weapon held = heldWeapon(p);
        boolean inTank = p.getVehicle() instanceof TankEntity;
        boolean trigger = free && (held != null || inTank) && client.options.attackKey.isPressed();
        if (trigger != sentTrigger) {
            sentTrigger = trigger;
            send(PfNetwork.TRIGGER, trigger);
        }
        boolean wantAim = free && held != null && !inTank && client.options.useKey.isPressed() && !p.isSprinting() && !reloading(p);
        if (wantAim != sentAim) {
            sentAim = wantAim;
            send(PfNetwork.AIM, wantAim);
        }
        aiming = wantAim;
        aim += ((aiming ? 1f : 0f) - aim) * 0.45f;
        long now = clientTicks;
        RELOADS.entrySet().removeIf(en -> now > en.getValue()[0] + en.getValue()[1] + 5);
        LAST_SHOT.entrySet().removeIf(en -> now > en.getValue() + 40);
    }

    private static void send(net.minecraft.util.Identifier id, boolean value) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(value);
        ClientPlayNetworking.send(id, buf);
    }

    public static float aimProgress(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevAim, aim);
    }

    public static float kick(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevKick, kick);
    }

    public static void onShot(Entity shooter, Weapon w) {
        LAST_SHOT.put(shooter.getId(), clientTicks);
        MinecraftClient client = MinecraftClient.getInstance();
        if (shooter == client.player) {
            kick = Math.min(1.0f, kick + 0.7f);
            ClientPlayerEntity p = client.player;
            float k = aiming ? 0.55f : 1.0f;
            float up = w.recoilPitch * 0.45f * k;
            float side = (p.getRandom().nextFloat() - 0.5f) * 2f * w.recoilYaw * 0.5f * k;
            p.setPitch(MathHelper.clamp(p.getPitch() - up, -90f, 90f));
            p.setYaw(p.getYaw() + side);
        }
    }

    /** Ticks (with fraction) since the entity last fired, or a large number. */
    public static float sinceShot(Entity e, float tickDelta) {
        Long t = LAST_SHOT.get(e.getId());
        return t == null ? 1000f : (clientTicks - t) + tickDelta;
    }

    public static void onReload(Entity e, int ticks) {
        RELOADS.put(e.getId(), new long[]{clientTicks, ticks});
    }

    /** 0..1 progress of the entity's current reload, or -1. */
    public static float reloadProgress(Entity e, float tickDelta) {
        long[] r = RELOADS.get(e.getId());
        if (r == null) {
            return -1f;
        }
        float p = (clientTicks - r[0] + tickDelta) / (float) r[1];
        if (p > 1f) {
            return -1f;
        }
        if (heldWeapon(e) == null) {
            return -1f;
        }
        return p;
    }

    public static boolean reloading(Entity e) {
        return reloadProgress(e, 0f) >= 0f;
    }

    public static void onHit(int flags) {
        hitMarker = 6;
        hitFlags = flags;
    }

    public static long ticks() {
        return clientTicks;
    }
}
