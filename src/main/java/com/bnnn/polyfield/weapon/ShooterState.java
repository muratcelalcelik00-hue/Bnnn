package com.bnnn.polyfield.weapon;

/** Server-side trigger/reload state of one shooter (player or soldier). */
public class ShooterState {
    public boolean trigger;
    /** Set on trigger press, consumed by a semi-automatic shot. */
    public boolean triggerFresh;
    public boolean aiming;
    /** Ticks until the next shot may leave the barrel (may go fractional). */
    public float cooldown;
    public int consecutive;
    public int ticksSinceShot = 1000;
    public int reloadTicks;
    public int reloadSlot = -1;
    public Weapon reloadWeapon;
    public int boltTicks;
    public Weapon boltWeapon;

    public boolean reloading() {
        return reloadTicks > 0;
    }

    public void tickTimers() {
        if (cooldown > 0) {
            cooldown -= 1.0f;
        }
        ticksSinceShot++;
        if (ticksSinceShot > 6) {
            consecutive = 0;
        }
    }

    public void cancelReload() {
        reloadTicks = 0;
        reloadSlot = -1;
        reloadWeapon = null;
    }
}
