package com.bnnn.polyfield.entity.ai;

import com.bnnn.polyfield.entity.Faction;
import com.bnnn.polyfield.entity.SoldierEntity;
import com.bnnn.polyfield.entity.TankEntity;
import com.bnnn.polyfield.item.GrenadeItem;
import com.bnnn.polyfield.weapon.GunItem;
import com.bnnn.polyfield.weapon.GunLogic;
import com.bnnn.polyfield.weapon.Weapon;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Polyfield-style infantry: close to the weapon's preferred range, strafe,
 * fire in bursts with line of sight, reload when the magazine runs dry and
 * now and then throw a grenade.
 */
public class SoldierAttackGoal extends Goal {
    private final SoldierEntity mob;
    private int seeTime;
    private int strafeTime;
    private boolean strafeLeft;
    private boolean strafeBack;
    private int repath;
    private int burstLeft;
    private int pause;
    private int reaction;

    public SoldierAttackGoal(SoldierEntity mob) {
        this.mob = mob;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && mob.weapon() != null;
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void start() {
        mob.setAttacking(true);
        seeTime = 0;
        reaction = 8 + mob.getRandom().nextInt(16);
        pause = 0;
        burstLeft = 0;
    }

    @Override
    public void stop() {
        mob.setAttacking(false);
        mob.gun.trigger = false;
        mob.getNavigation().stop();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        Weapon w = mob.weapon();
        if (target == null || w == null) {
            return;
        }
        ItemStack stack = mob.getMainHandStack();
        double distSq = mob.squaredDistanceTo(target);
        boolean canSee = mob.getVisibilityCache().canSee(target);
        seeTime = canSee ? Math.min(seeTime + 1, 200) : Math.max(seeTime - 1, -60);

        double pref = w.preferredRange();
        if (!canSee || distSq > pref * pref) {
            if (--repath <= 0) {
                repath = 10 + mob.getRandom().nextInt(10);
                mob.getNavigation().startMovingTo(target, 1.0);
            }
        } else {
            mob.getNavigation().stop();
            if (++strafeTime >= 25) {
                strafeTime = mob.getRandom().nextInt(15);
                strafeLeft = mob.getRandom().nextBoolean();
                strafeBack = distSq < (pref * 0.5) * (pref * 0.5);
            }
            mob.getMoveControl().strafeTo(strafeBack ? -0.4f : 0.0f, strafeLeft ? 0.35f : -0.35f);
        }
        mob.getLookControl().lookAt(target, 40.0f, 40.0f);
        mob.lookAtEntity(target, 40.0f, 40.0f);
        mob.setBodyYaw(mob.getHeadYaw());

        if (GunItem.getAmmo(stack) <= 0 && !mob.gun.reloading()) {
            GunLogic.startReload(mob, stack, w, mob.gun);
            return;
        }
        if (mob.gun.reloading()) {
            return;
        }

        // the occasional grenade at mid range
        if (canSee && mob.grenadeCooldown <= 0 && distSq > 36 && distSq < 400 && mob.getRandom().nextInt(120) == 0) {
            throwGrenade(target);
            return;
        }

        if (!canSee || seeTime < reaction) {
            return;
        }
        if (w.isLauncher() && !(target.getVehicle() instanceof TankEntity) && distSq < 64) {
            return; // do not blow themselves up
        }
        if (pause > 0) {
            pause--;
            return;
        }
        if (mob.gun.cooldown > 0) {
            return;
        }
        Vec3d aim = aimAt(target);
        mob.gun.aiming = seeTime > reaction + 15;
        GunLogic.fireOnce(mob, stack, w, mob.gun, aim, mob.spreadFactor(), mob.damageFactor());
        if (w.fireMode == Weapon.FireMode.AUTO) {
            mob.gun.cooldown += w.ticksPerShot();
            if (burstLeft <= 0) {
                burstLeft = 3 + mob.getRandom().nextInt(w.kind == Weapon.Kind.LMG ? 10 : 5);
            }
            if (--burstLeft <= 0) {
                pause = 12 + mob.getRandom().nextInt(25);
            }
        } else {
            mob.gun.cooldown = w.ticksPerShot();
            pause = (w.isLauncher() ? 60 : 8) + mob.getRandom().nextInt(w.boltAction ? 30 : 15);
        }
    }

    private Vec3d aimAt(LivingEntity target) {
        Vec3d eye = mob.getEyePos();
        Vec3d to = new Vec3d(target.getX(), target.getBodyY(0.6), target.getZ());
        return to.subtract(eye);
    }

    private void throwGrenade(LivingEntity target) {
        mob.grenadeCooldown = 300 + mob.getRandom().nextInt(300);
        Vec3d d = target.getPos().subtract(mob.getPos());
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (MathHelper.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0f;
        float pitch = -30.0f;
        float speed = (float) MathHelper.clamp(0.35 + horiz * 0.045, 0.5, 1.3);
        mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        GrenadeItem.throwGrenade(mob.getWorld(), mob, mob.faction == Faction.GER, pitch, yaw, speed);
    }
}
