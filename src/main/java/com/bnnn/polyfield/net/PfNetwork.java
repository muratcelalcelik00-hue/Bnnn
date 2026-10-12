package com.bnnn.polyfield.net;

import com.bnnn.polyfield.PolyfieldMod;
import com.bnnn.polyfield.weapon.GunLogic;
import com.bnnn.polyfield.weapon.ShooterState;
import com.bnnn.polyfield.weapon.Weapon;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.function.Consumer;

/**
 * Packets. Client -> server: trigger, aim, reload. Server -> client: shot
 * (tracer, muzzle flash, gun sound), hit (hit marker), reload (animation).
 */
public final class PfNetwork {
    public static final Identifier TRIGGER = PolyfieldMod.id("trigger");
    public static final Identifier AIM = PolyfieldMod.id("aim");
    public static final Identifier RELOAD = PolyfieldMod.id("reload");

    public static final Identifier SHOT = PolyfieldMod.id("shot");
    public static final Identifier HIT = PolyfieldMod.id("hit");
    public static final Identifier RELOAD_ANIM = PolyfieldMod.id("reload_anim");

    /** How far gunfire is sent (and heard). */
    public static final double SHOT_RADIUS = 160.0;

    public static final int HIT_HEADSHOT = 1;
    public static final int HIT_KILL = 2;

    private PfNetwork() {
    }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(TRIGGER, (server, player, handler, buf, sender) -> {
            boolean down = buf.readBoolean();
            server.execute(() -> {
                ShooterState s = GunLogic.state(player);
                if (down && !s.trigger) {
                    s.triggerFresh = true;
                }
                s.trigger = down;
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(AIM, (server, player, handler, buf, sender) -> {
            boolean aim = buf.readBoolean();
            server.execute(() -> GunLogic.state(player).aiming = aim);
        });
        ServerPlayNetworking.registerGlobalReceiver(RELOAD, (server, player, handler, buf, sender) ->
                server.execute(() -> GunLogic.requestReload(player)));
    }

    public static void sendAround(ServerWorld world, Vec3d pos, double radius, Identifier id, Consumer<PacketByteBuf> writer) {
        for (ServerPlayerEntity p : PlayerLookup.around(world, pos, radius)) {
            PacketByteBuf buf = PacketByteBufs.create();
            writer.accept(buf);
            ServerPlayNetworking.send(p, id, buf);
        }
    }

    public static void sendShot(ServerWorld world, Entity shooter, Weapon weapon, Vec3d end, int hitType) {
        sendAround(world, shooter.getPos(), SHOT_RADIUS, SHOT, buf -> {
            buf.writeVarInt(shooter.getId());
            buf.writeByte(weapon.ordinal());
            buf.writeDouble(end.x);
            buf.writeDouble(end.y);
            buf.writeDouble(end.z);
            buf.writeByte(hitType);
        });
    }

    public static void sendHit(ServerPlayerEntity player, int flags) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(flags);
        ServerPlayNetworking.send(player, HIT, buf);
    }

    public static void sendReloadAnim(ServerWorld world, Entity entity, int ticks) {
        sendAround(world, entity.getPos(), 96, RELOAD_ANIM, buf -> {
            buf.writeVarInt(entity.getId());
            buf.writeVarInt(ticks);
        });
    }
}
