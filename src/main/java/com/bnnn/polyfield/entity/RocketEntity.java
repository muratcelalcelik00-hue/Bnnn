package com.bnnn.polyfield.entity;

import com.bnnn.polyfield.PfExplosions;
import com.bnnn.polyfield.PfSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Bazooka rocket, Panzerfaust warhead and tank shell. */
public class RocketEntity extends ProjectileEntity {
    public enum Kind {
        BAZOOKA(3.0f, 0.012, "rocket"),
        PANZERFAUST(3.4f, 0.02, "panzerfaust_warhead"),
        TANK_SHELL(4.0f, 0.03, "rocket");

        public final float power;
        public final double gravity;
        public final String model;

        Kind(float power, double gravity, String model) {
            this.power = power;
            this.gravity = gravity;
            this.model = model;
        }
    }

    private static final TrackedData<Integer> KIND = DataTracker.registerData(RocketEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private int life;

    public RocketEntity(EntityType<? extends RocketEntity> type, World world) {
        super(type, world);
    }

    public void setup(Entity owner, Kind kind) {
        setOwner(owner);
        dataTracker.set(KIND, kind.ordinal());
    }

    public Kind kind() {
        int i = dataTracker.get(KIND);
        Kind[] v = Kind.values();
        return i >= 0 && i < v.length ? v[i] : Kind.BAZOOKA;
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(KIND, 0);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3d vel = getVelocity();
        if (!getWorld().isClient) {
            HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
            if (hit.getType() != HitResult.Type.MISS) {
                onCollision(hit);
                if (isRemoved()) {
                    return;
                }
            }
            if (++life > 200 || getY() < getWorld().getBottomY() - 16) {
                discard();
                return;
            }
        } else {
            for (int i = 0; i < 2; i++) {
                double f = i * 0.5;
                getWorld().addParticle(ParticleTypes.SMOKE, getX() - vel.x * f, getY() - vel.y * f, getZ() - vel.z * f, 0, 0.01, 0);
            }
            if (kind() != Kind.TANK_SHELL) {
                getWorld().addParticle(ParticleTypes.FLAME, getX() - vel.x * 0.3, getY() - vel.y * 0.3, getZ() - vel.z * 0.3, 0, 0, 0);
            }
        }
        setPosition(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
        setVelocity(vel.x * 0.995, vel.y * 0.995 - kind().gravity, vel.z * 0.995);
        ProjectileUtil.setRotationFromVelocity(this, 1.0f);
        if (isTouchingWater()) {
            setVelocity(getVelocity().multiply(0.8));
        }
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && !entity.isConnectedThroughVehicle(getOwner() == null ? this : getOwner());
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        explode(hit.getPos());
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!isRemoved() && hit.getType() == HitResult.Type.BLOCK) {
            explode(hit.getPos());
        }
    }

    private void explode(Vec3d at) {
        if (getWorld() instanceof ServerWorld sw && !isRemoved()) {
            PfExplosions.explode(sw, this, getOwner(), at, kind().power, PfSounds.EXPLOSION_CLOSE);
            discard();
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Kind", dataTracker.get(KIND));
        nbt.putInt("Life", life);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(KIND, nbt.getInt("Kind"));
        life = nbt.getInt("Life");
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 256 * 256;
    }
}
