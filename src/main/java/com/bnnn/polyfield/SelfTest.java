package com.bnnn.polyfield;

import com.bnnn.polyfield.entity.GrenadeEntity;
import com.bnnn.polyfield.entity.SoldierEntity;
import com.bnnn.polyfield.entity.TankEntity;
import com.bnnn.polyfield.weapon.GunItem;
import com.bnnn.polyfield.weapon.GunLogic;
import com.bnnn.polyfield.weapon.Weapon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless check run only with -Dpolyfield.selftest=true (CI): spawns both
 * factions, a tank and a grenade, fires every weapon once, lets the soldiers
 * fight, logs a verdict and stops the server. Inert in normal play.
 */
public final class SelfTest {
    private static final boolean ENABLED = "true".equals(System.getProperty("polyfield.selftest"));
    private static int ticks;
    private static int fired;
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static boolean failed;

    private SelfTest() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void init() {
        if (!ENABLED) {
            return;
        }
        PolyfieldMod.LOGGER.info("POLYFIELD SELFTEST armed");
        ServerTickEvents.END_SERVER_TICK.register(SelfTest::tick);
    }

    private static BlockPos ground(ServerWorld w, BlockPos p) {
        return w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p);
    }

    private static void tick(MinecraftServer server) {
        if (server.isSingleplayer()) {
            return; // the client self-test drives singleplayer
        }
        ticks++;
        ServerWorld w = server.getOverworld();
        BlockPos spawn = w.getSpawnPos();
        try {
            if (ticks == 20) {
                for (int i = 0; i < 4; i++) {
                    SPAWNED.add(PfEntities.GERMAN_SOLDIER.spawn(w, ground(w, spawn.add(-10, 0, i * 2)), SpawnReason.COMMAND));
                    SPAWNED.add(PfEntities.ALLIED_SOLDIER.spawn(w, ground(w, spawn.add(10, 0, i * 2)), SpawnReason.COMMAND));
                }
                SPAWNED.add(PfEntities.SHERMAN.spawn(w, ground(w, spawn.add(0, 0, 20)), SpawnReason.COMMAND));
                SPAWNED.add(PfEntities.JAGDPANTHER.spawn(w, ground(w, spawn.add(0, 0, -20)), SpawnReason.COMMAND));
                GrenadeEntity g = new GrenadeEntity(PfEntities.GRENADE, w);
                BlockPos gp = ground(w, spawn.add(0, 0, 30));
                g.setPosition(gp.getX() + 0.5, gp.getY() + 1.0, gp.getZ() + 0.5);
                w.spawnEntity(g);
                long nulls = SPAWNED.stream().filter(e -> e == null).count();
                PolyfieldMod.LOGGER.info("POLYFIELD SELFTEST spawned {} entities ({} failed)", SPAWNED.size(), nulls);
                if (nulls > 0) {
                    failed = true;
                }
            }
            if (ticks == 40) {
                SoldierEntity s = (SoldierEntity) SPAWNED.get(0);
                for (Weapon wp : Weapon.values()) {
                    ItemStack st = new ItemStack(wp.item());
                    s.equipStack(EquipmentSlot.MAINHAND, st);
                    s.gun.cooldown = 0;
                    if (GunLogic.fireOnce(s, st, wp, s.gun, new Vec3d(1, 0, 0), 1.0f, 1.0f)) {
                        fired++;
                    }
                    if (GunItem.getAmmo(st) != wp.magazine - 1) {
                        PolyfieldMod.LOGGER.error("POLYFIELD SELFTEST ammo not consumed for {}", wp.id);
                        failed = true;
                    }
                    GunItem.setAmmo(st, 0);
                    s.gun.cancelReload();
                    if (!GunLogic.startReload(s, st, wp, s.gun)) {
                        PolyfieldMod.LOGGER.error("POLYFIELD SELFTEST reload refused for {}", wp.id);
                        failed = true;
                    }
                    s.gun.cancelReload();
                }
                s.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Weapon.MP40.item()));
                PolyfieldMod.LOGGER.info("POLYFIELD SELFTEST fired {}/{} weapons", fired, Weapon.values().length);
                if (fired != Weapon.values().length) {
                    failed = true;
                }
            }
            if (ticks == 60) {
                TankEntity tank = (TankEntity) SPAWNED.get(8);
                tank.damage(w.getDamageSources().generic(), 10f);
                PolyfieldMod.LOGGER.info("POLYFIELD SELFTEST tank health {}", tank.getHealth());
            }
            if (ticks == 600) {
                int alive = 0;
                Box box = new Box(spawn).expand(64);
                for (SoldierEntity s : w.getEntitiesByClass(SoldierEntity.class, box, e -> true)) {
                    alive++;
                }
                PolyfieldMod.LOGGER.info("POLYFIELD SELFTEST soldiers alive after battle: {}", alive);
                PolyfieldMod.LOGGER.info(failed ? "POLYFIELD SELFTEST FAIL" : "POLYFIELD SELFTEST PASS");
                server.stop(false);
            }
        } catch (Throwable t) {
            PolyfieldMod.LOGGER.error("POLYFIELD SELFTEST FAIL", t);
            server.stop(false);
        }
    }
}
