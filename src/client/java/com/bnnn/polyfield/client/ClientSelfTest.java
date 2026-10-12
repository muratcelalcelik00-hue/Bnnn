package com.bnnn.polyfield.client;

import com.bnnn.polyfield.PfEntities;
import com.bnnn.polyfield.PolyfieldMod;
import com.bnnn.polyfield.SelfTest;
import com.bnnn.polyfield.entity.TankEntity;
import com.bnnn.polyfield.net.PfNetwork;
import com.bnnn.polyfield.weapon.Weapon;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.util.UUID;

/**
 * CI-only client check (-Dpolyfield.selftest=true with --quickPlaySingleplayer):
 * audits all mixins, then fires, reloads and drives a tank in a real client,
 * taking screenshots, and quits.
 */
public final class ClientSelfTest {
    private static int ticks;
    private static int inWorld;
    private static TankEntity tank;

    private ClientSelfTest() {
    }

    public static void init() {
        if (!SelfTest.enabled()) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(ClientSelfTest::tick);
    }

    private static void shot(MinecraftClient c, String name) {
        ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(),
                msg -> PolyfieldMod.LOGGER.info("POLYFIELD CLIENT SELFTEST screenshot {}", name));
    }

    private static void reload() {
        ClientPlayNetworking.send(PfNetwork.RELOAD, PacketByteBufs.empty());
    }

    private static void tick(MinecraftClient c) {
        ticks++;
        if (ticks == 5) {
            MixinEnvironment.getCurrentEnvironment().audit();
            PolyfieldMod.LOGGER.info("POLYFIELD CLIENT SELFTEST mixin audit done");
        }
        IntegratedServer s = c.getServer();
        if (c.player == null || s == null) {
            if (ticks > 20 * 300) {
                PolyfieldMod.LOGGER.error("POLYFIELD CLIENT SELFTEST FAIL: no world");
                c.scheduleStop();
            }
            return;
        }
        inWorld++;
        UUID id = c.player.getUuid();
        try {
            switch (inWorld) {
                case 40 -> s.execute(() -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(id);
                    ServerWorld w = s.getOverworld();
                    w.setTimeOfDay(6000);
                    p.changeGameMode(GameMode.CREATIVE);
                    p.getInventory().setStack(p.getInventory().selectedSlot, new ItemStack(Weapon.MP40.item()));
                    Vec3d f = Vec3d.fromPolar(0, p.getYaw());
                    for (int i = 0; i < 3; i++) {
                        BlockPos at = BlockPos.ofFloored(p.getX() + f.x * 12 + i * 1.5 - 1.5, p.getY(), p.getZ() + f.z * 12);
                        PfEntities.GERMAN_SOLDIER.spawn(w, w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at), SpawnReason.COMMAND);
                    }
                    BlockPos tp = BlockPos.ofFloored(p.getX() - f.z * 8, p.getY(), p.getZ() + f.x * 8);
                    tank = PfEntities.SHERMAN.spawn(w, w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, tp), SpawnReason.COMMAND);
                })
                case 60 -> ClientGunState.forcedTrigger = Boolean.TRUE;
                case 72 -> shot(c, "pf_1_firing");
                case 100 -> ClientGunState.forcedTrigger = Boolean.FALSE;
                case 110 -> reload();
                case 122 -> shot(c, "pf_2_reload");
                case 170 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                case 175 -> ClientGunState.forcedTrigger = Boolean.TRUE;
                case 185 -> shot(c, "pf_3_third_person");
                case 190 -> ClientGunState.forcedTrigger = Boolean.FALSE;
                case 200 -> s.execute(() -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(id);
                    if (tank != null) {
                        p.startRiding(tank, true);
                    }
                });
                case 240 -> shot(c, "pf_4_tank");
                case 245 -> ClientGunState.forcedTrigger = Boolean.TRUE;
                case 250 -> ClientGunState.forcedTrigger = Boolean.FALSE;
                case 262 -> shot(c, "pf_5_tank_fire");
                case 320 -> {
                    PolyfieldMod.LOGGER.info("POLYFIELD CLIENT SELFTEST PASS");
                    c.scheduleStop();
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            PolyfieldMod.LOGGER.error("POLYFIELD CLIENT SELFTEST FAIL", t);
            c.scheduleStop();
        }
    }
}
