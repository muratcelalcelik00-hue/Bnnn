package com.bnnn.polyfield.weapon;

import com.bnnn.polyfield.entity.Faction;
import net.minecraft.item.Item;

/**
 * Polyfield's weapons with the numbers read from the game's own {@code Weapon}
 * MonoBehaviours (fireRate in rounds/s, damage in Polyfield HP where a soldier
 * has 100, magazine and reserve sizes, recoil "forces"). Reload times are the
 * lengths of the game's reload sounds.
 */
public enum Weapon {
    //        id               faction        kind          fire           rps  dmg  range mag  reserve reload  recoil(x, y, z)       bolt   loop   pitch  ammo
    KAR98K("kar98k", Faction.GER, Kind.RIFLE, FireMode.SEMI, 1, 99, 2000, 5, 60, 64, 3.0f, 1.0f, 10.0f, true, false, 0.0f, AmmoType.RIFLE),
    M1_GARAND("m1_garand", Faction.ALLIES, Kind.RIFLE, FireMode.SEMI, 3, 50, 1000, 8, 72, 40, 2.0f, 0.7f, 7.0f, false, false, 0.0f, AmmoType.RIFLE),
    MP40("mp40", Faction.GER, Kind.SMG, FireMode.AUTO, 8, 20, 300, 32, 288, 48, 1.0f, 0.5f, 5.0f, false, true, 0.0f, AmmoType.PISTOL),
    THOMPSON("thompson", Faction.ALLIES, Kind.SMG, FireMode.AUTO, 10, 17, 200, 30, 270, 52, 1.5f, 0.8f, 4.0f, false, true, 0.0f, AmmoType.PISTOL),
    STG44("stg44", Faction.GER, Kind.SMG, FireMode.AUTO, 9, 25, 400, 30, 270, 68, 1.0f, 1.0f, 5.0f, false, true, 0.0f, AmmoType.RIFLE),
    STEN("sten", Faction.ALLIES, Kind.SMG, FireMode.AUTO, 8, 18, 200, 32, 288, 56, 1.0f, 0.7f, 3.0f, false, true, -0.05f, AmmoType.PISTOL),
    MG42("mg42", Faction.GER, Kind.LMG, FireMode.AUTO, 12, 22, 600, 125, 1125, 90, 2.0f, 1.0f, 5.0f, false, true, 0.0f, AmmoType.MG),
    MG34("mg34", Faction.GER, Kind.LMG, FireMode.AUTO, 10, 22, 600, 75, 600, 90, 2.0f, 1.0f, 5.0f, false, true, 0.0f, AmmoType.MG),
    BAR("bar", Faction.ALLIES, Kind.LMG, FireMode.AUTO, 6, 33, 600, 20, 180, 70, 2.0f, 0.5f, 10.0f, false, true, -0.1f, AmmoType.RIFLE),
    M3_GREASE_GUN("m3_grease_gun", Faction.ALLIES, Kind.SMG, FireMode.AUTO, 7, 17, 150, 30, 270, 48, 1.0f, 0.5f, 4.0f, false, true, -0.15f, AmmoType.PISTOL),
    VG15("vg15", Faction.GER, Kind.RIFLE, FireMode.AUTO, 7, 30, 400, 30, 270, 68, 1.0f, 0.5f, 7.0f, false, true, -0.2f, AmmoType.RIFLE),
    WALTHER_P38("walther_p38", Faction.GER, Kind.PISTOL, FireMode.SEMI, 5, 15, 100, 8, 64, 38, 8.0f, 1.0f, 5.0f, false, false, 0.2f, AmmoType.PISTOL),
    M1915("m1915", Faction.ALLIES, Kind.PISTOL, FireMode.SEMI, 5, 15, 100, 7, 63, 44, 8.0f, 1.0f, 5.0f, false, false, 0.0f, AmmoType.PISTOL),
    WELROD("welrod", Faction.ALLIES, Kind.PISTOL, FireMode.SEMI, 1, 65, 100, 6, 54, 38, 4.0f, 0.25f, 3.0f, true, false, 0.0f, AmmoType.PISTOL),
    PANZERFAUST("panzerfaust", Faction.GER, Kind.LAUNCHER, FireMode.SEMI, 1, 100, 800, 1, 3, 52, 5.0f, 0.5f, 5.0f, false, false, 0.0f, AmmoType.ROCKET),
    BAZOOKA("bazooka", Faction.ALLIES, Kind.LAUNCHER, FireMode.SEMI, 1, 100, 1200, 1, 6, 98, 10.0f, 2.0f, 15.0f, false, false, 0.0f, AmmoType.ROCKET);

    public enum Kind { RIFLE, SMG, LMG, PISTOL, LAUNCHER }

    public enum FireMode { SEMI, AUTO }

    /** Polyfield soldiers have 100 HP, Minecraft players 20. */
    public static final float DAMAGE_SCALE = 0.2f;

    public final String id;
    public final Faction faction;
    public final Kind kind;
    public final FireMode fireMode;
    public final int roundsPerSecond;
    public final int damage;
    public final int range;
    public final int magazine;
    public final int reserve;
    public final int reloadTicks;
    public final float recoilPitch;
    public final float recoilYaw;
    public final float kick;
    public final boolean boltAction;
    public final boolean soundLoop;
    public final float pitchOffset;
    public final AmmoType ammo;
    private Item item;

    Weapon(String id, Faction faction, Kind kind, FireMode fireMode, int rps, int damage, int range, int magazine,
           int reserve, int reloadTicks, float recoilPitch, float recoilYaw, float kick, boolean boltAction,
           boolean soundLoop, float pitchOffset, AmmoType ammo) {
        this.id = id;
        this.faction = faction;
        this.kind = kind;
        this.fireMode = fireMode;
        this.roundsPerSecond = rps;
        this.damage = damage;
        this.range = range;
        this.magazine = magazine;
        this.reserve = reserve;
        this.reloadTicks = reloadTicks;
        this.recoilPitch = recoilPitch;
        this.recoilYaw = recoilYaw;
        this.kick = kick;
        this.boltAction = boltAction;
        this.soundLoop = soundLoop;
        this.pitchOffset = pitchOffset;
        this.ammo = ammo;
    }

    public float ticksPerShot() {
        return 20.0f / Math.max(1, roundsPerSecond);
    }

    public float minecraftDamage() {
        return damage * DAMAGE_SCALE;
    }

    /** Hip-fire cone in degrees. */
    public float hipSpread() {
        return switch (kind) {
            case RIFLE -> 2.0f;
            case SMG -> 3.0f;
            case LMG -> 3.5f;
            case PISTOL -> 2.5f;
            case LAUNCHER -> 1.5f;
        };
    }

    /** Aimed-down-sights cone in degrees. */
    public float aimSpread() {
        return switch (kind) {
            case RIFLE -> 0.15f;
            case SMG -> 0.9f;
            case LMG -> 1.2f;
            case PISTOL -> 0.6f;
            case LAUNCHER -> 0.4f;
        };
    }

    /** Field of view multiplier while aiming. */
    public float aimFov() {
        return switch (kind) {
            case RIFLE -> this == KAR98K ? 0.45f : 0.6f;
            case LAUNCHER -> 0.7f;
            default -> 0.72f;
        };
    }

    public double maxRange() {
        return Math.min(range, 256);
    }

    /** Distance a soldier carrying this weapon likes to keep. */
    public double preferredRange() {
        return switch (kind) {
            case RIFLE -> 26;
            case SMG -> 13;
            case LMG -> 22;
            case PISTOL -> 9;
            case LAUNCHER -> 22;
        };
    }

    public boolean isLauncher() {
        return kind == Kind.LAUNCHER;
    }

    public boolean isTwoHandedLong() {
        return kind != Kind.PISTOL;
    }

    public Item item() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public static Weapon byOrdinal(int i) {
        Weapon[] v = values();
        return i >= 0 && i < v.length ? v[i] : MP40;
    }
}
