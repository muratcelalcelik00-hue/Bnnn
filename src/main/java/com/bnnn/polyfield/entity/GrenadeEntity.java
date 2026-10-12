package com.bnnn.polyfield.entity;

import com.bnnn.polyfield.PfExplosions;
import com.bnnn.polyfield.PfSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Bouncing, timed hand grenade (M24 Stielhandgranate or Mk 2). */
public class GrenadeEntity extends ProjectileEntity {
    private static final TrackedData<Boolean> STICK = DataTracker.registerData(GrenadeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final int FUSE = 80;
    private int fuse = FUSE;
    public float spin;
    public float prevSpin;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, World world) {
        super(type, world);
    }

    public void setup(LivingEntity owner, boolean stickGrenade) {
        setOwner(owner);
        dataTracker.set(STICK, stickGrenade);
    }

    public boolean isStickGrenade() {
        return dataTracker.get(STICK);
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(STICK, true);
    }

    @Override
    public void tick() {
        super.tick();
        prevSpin = spin;
        Vec3d v = getVelocity().add(0, -0.04, 0);
        setVelocity(v);
        Vec3d before = v;
        move(MovementType.SELF, v);
        Vec3d after = getVelocity();
        boolean bounced = false;
        if (horizontalCollision) {
            after = new Vec3d(-before.x * 0.4, after.y, -before.z * 0.4);
            bounced = before.horizontalLengthSquared() > 0.01;
        }
        if (verticalCollision) {
            if (before.y < -0.12) {
                bounced = true;
                after = new Vec3d(after.x * 0.6, -before.y * 0.35, after.z * 0.6);
            } else {
                after = new Vec3d(after.x * 0.7, 0, after.z * 0.7);
            }
        }
        if (isTouchingWater()) {
            after = after.multiply(0.6);
        }
        setVelocity(after.multiply(0.98));
        spin += (float) (after.length() * 40.0);
        if (bounced && !getWorld().isClient) {
            getWorld().playSound(null, getX(), getY(), getZ(), PfSounds.GRENADE_BOUNCE, SoundCategory.NEUTRAL, 0.6f, 1.2f);
        }
        if (getWorld().isClient) {
            getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0, 0.02, 0);
        } else if (--fuse <= 0 && getWorld() instanceof ServerWorld sw) {
            PfExplosions.explode(sw, this, getOwner(), getPos().add(0, 0.2, 0), 2.6f, PfSounds.EXPLOSION_CLOSE);
            discard();
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Fuse", fuse);
        nbt.putBoolean("Stick", isStickGrenade());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        fuse = nbt.getInt("Fuse");
        dataTracker.set(STICK, nbt.getBoolean("Stick"));
    }
}
