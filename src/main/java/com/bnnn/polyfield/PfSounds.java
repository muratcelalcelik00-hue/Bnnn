package com.bnnn.polyfield;

import com.bnnn.polyfield.weapon.Weapon;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Sound events. The audio itself comes from the user's own Polyfield install
 * (converter/extract_polyfield.py writes sounds.json + mono OGGs).
 */
public final class PfSounds {
    public record WeaponSounds(SoundEvent shot, SoundEvent tail, SoundEvent reload, SoundEvent bolt) {
    }

    public static final Map<Weapon, WeaponSounds> WEAPONS = new EnumMap<>(Weapon.class);

    public static final SoundEvent GARAND_PING = register("weapon.m1_garand.ping");
    public static final SoundEvent IMPACT_BODY = register("impact.body");
    public static final SoundEvent IMPACT_CONCRETE = register("impact.concrete");
    public static final SoundEvent IMPACT_DIRT = register("impact.dirt");
    public static final SoundEvent IMPACT_RICOCHET = register("impact.ricochet");
    public static final SoundEvent IMPACT_WOOD = register("impact.wood");
    public static final SoundEvent HITMARKER = register("hitmarker");
    public static final SoundEvent HEADSHOT = register("headshot");
    public static final SoundEvent EXPLOSION_CLOSE = register("explosion.close");
    public static final SoundEvent EXPLOSION_DEBRIS = register("explosion.debris");
    public static final SoundEvent EXPLOSION_VEHICLE = register("explosion.vehicle");
    public static final SoundEvent SOLDIER_DEATH = register("soldier.death");
    public static final SoundEvent SOLDIER_SCREAM = register("soldier.scream");
    public static final SoundEvent SOLDIER_WILHELM = register("soldier.wilhelm");
    public static final SoundEvent SOLDIER_WHISTLE = register("soldier.whistle");
    public static final SoundEvent SOLDIER_STEP = register("soldier.step");
    public static final SoundEvent SHELL_DROP = register("shell.drop");
    public static final SoundEvent WEAPON_SWITCH = register("weapon.switch");
    public static final SoundEvent WEAPON_RATTLE = register("weapon.rattle");
    public static final SoundEvent TANK_CANNON = register("tank.cannon");
    public static final SoundEvent TANK_SHERMAN_IDLE = register("tank.sherman.idle");
    public static final SoundEvent TANK_SHERMAN_DRIVE = register("tank.sherman.drive");
    public static final SoundEvent TANK_PANTHER_IDLE = register("tank.panther.idle");
    public static final SoundEvent TANK_PANTHER_DRIVE = register("tank.panther.drive");
    public static final SoundEvent TANK_TRACKS = register("tank.tracks");
    public static final SoundEvent TANK_FIRE = register("tank.fire");
    public static final SoundEvent TANK_REPAIR = register("tank.repair");
    public static final SoundEvent GRENADE_BOUNCE = register("grenade.bounce");

    static {
        for (Weapon w : Weapon.values()) {
            String p = "weapon." + w.id + ".";
            WEAPONS.put(w, new WeaponSounds(register(p + "shot"), register(p + "tail"), register(p + "reload"),
                    w.boltAction ? register(p + "bolt") : null));
        }
    }

    private PfSounds() {
    }

    private static SoundEvent register(String name) {
        Identifier id = PolyfieldMod.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static WeaponSounds of(Weapon w) {
        return WEAPONS.get(w);
    }

    public static void init() {
        // class loading registers everything
    }
}
