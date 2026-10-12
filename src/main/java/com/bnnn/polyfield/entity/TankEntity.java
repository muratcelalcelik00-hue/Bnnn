package com.bnnn.polyfield.entity;

import com.bnnn.polyfield.PfDamage;
import com.bnnn.polyfield.PfEntities;
import com.bnnn.polyfield.PfExplosions;
import com.bnnn.polyfield.PfItems;
import com.bnnn.polyfield.PfSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Drivable Polyfield tank. Movement is client-authoritative for the driver
 * (like boats); turret and gun follow the driver's view on the server and are
 * synced with tracked data.
 */
public class TankEntity extends Entity {
    public enum Type {
        // anchors in model space (metres, +Z forward) from the converted Polyfield prefabs
        SHERMAN("sherman", 240f, 0.19, 2.4f, 2.0f, 100, -12f, 8f, 180f,
                new Vec3d(0, 0, 0), new Vec3d(0, 2.466, 0.840), new Vec3d(0, 2.459, 5.637), new Vec3d(-0.42, 2.35, 0.31), true),
        JAGDPANTHER("jagdpanther", 300f, 0.17, 2.0f, 1.0f, 120, -10f, 8f, 13f,
                new Vec3d(0, 2.097, 1.133), new Vec3d(0, 2.097, 1.133), new Vec3d(0, 1.952, 6.984), new Vec3d(-0.83, 1.25, 1.43), false);

        public final String id;
        public final float maxHealth;
        public final double maxSpeed;
        public final float turnRate;
        public final float turretRate;
        public final int reloadTicks;
        public final float minPitch;
        public final float maxPitch;
        public final float traverse;
        public final Vec3d turretPivot;
        public final Vec3d gunPivot;
        public final Vec3d muzzle;
        public final Vec3d seat;
        public final boolean seatOnTurret;

        Type(String id, float maxHealth, double maxSpeed, float turnRate, float turretRate, int reloadTicks, float minPitch,
             float maxPitch, float traverse, Vec3d turretPivot, Vec3d gunPivot, Vec3d muzzle, Vec3d seat, boolean seatOnTurret) {
            this.id = id;
            this.maxHealth = maxHealth;
            this.maxSpeed = maxSpeed;
            this.turnRate = turnRate;
            this.turretRate = turretRate;
            this.reloadTicks = reloadTicks;
            this.minPitch = minPitch;
            this.maxPitch = maxPitch;
            this.traverse = traverse;
            this.turretPivot = turretPivot;
            this.gunPivot = gunPivot;
            this.muzzle = muzzle;
            this.seat = seat;
            this.seatOnTurret = seatOnTurret;
        }
    }

    private static final TrackedData<Float> HEALTH = DataTracker.registerData(TankEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TURRET_YAW = DataTracker.registerData(TankEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> GUN_PITCH = DataTracker.registerData(TankEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> HURT = DataTracker.registerData(TankEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> FIRED_AT = DataTracker.registerData(TankEntity.class, TrackedDataHandlerRegistry.LONG);

    public final Type type;
    private double speed;
    private int lerpTicks;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYaw;
    private int cannonCooldown;
    private Vec3d lastServerPos;
    /** Client-side smoothed turret/gun angles for rendering. */
    public float clientTurretYaw;
    public float prevClientTurretYaw;
    public float clientGunPitch;
    public float prevClientGunPitch;
    public float trackScroll;
    public float prevTrackScroll;

    public TankEntity(EntityType<? extends TankEntity> entityType, World world) {
        super(entityType, world);
        this.type = entityType == PfEntities.JAGDPANTHER ? Type.JAGDPANTHER : Type.SHERMAN;
        this.intersectionChecked = true;
        dataTracker.set(HEALTH, type.maxHealth);
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(HEALTH, 200f);
        dataTracker.startTracking(TURRET_YAW, 0f);
        dataTracker.startTracking(GUN_PITCH, 0f);
        dataTracker.startTracking(HURT, 0);
        dataTracker.startTracking(FIRED_AT, -1000L);
    }

    public float getHealth() {
        return dataTracker.get(HEALTH);
    }

    public void setHealth(float h) {
        dataTracker.set(HEALTH, MathHelper.clamp(h, 0, type.maxHealth));
    }

    public float getTurretYaw() {
        return dataTracker.get(TURRET_YAW);
    }

    public float getGunPitch() {
        return dataTracker.get(GUN_PITCH);
    }

    public int getHurtTicks() {
        return dataTracker.get(HURT);
    }

    /** 0 = just fired, 1 = loaded. */
    public float reloadProgress() {
        long t = getWorld().getTime() - dataTracker.get(FIRED_AT);
        return MathHelper.clamp(t / (float) type.reloadTicks, 0f, 1f);
    }

    public double getSpeed() {
        return speed;
    }

    @Override
    public float getStepHeight() {
        return 1.05f;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (getHurtTicks() > 0 && !getWorld().isClient) {
            dataTracker.set(HURT, getHurtTicks() - 1);
        }
        updateLerp();
        if (isLogicalSideForUpdatingMovement()) {
            drive();
        } else {
            setVelocity(Vec3d.ZERO);
        }
        checkBlockCollision();
        if (getWorld().isClient) {
            clientTick();
        } else {
            serverTick();
        }
    }

    private void drive() {
        float fwd = 0;
        float side = 0;
        if (getControllingPassenger() instanceof PlayerEntity p) {
            fwd = p.forwardSpeed;
            side = p.sidewaysSpeed;
        }
        float yawDelta = -Math.signum(side) * type.turnRate * (Math.abs(speed) > 0.02 || fwd == 0 ? 1.0f : 0.6f);
        if (fwd < 0) {
            yawDelta = -yawDelta;
        }
        setYaw(getYaw() + yawDelta);
        double target = fwd > 0 ? type.maxSpeed : fwd < 0 ? -type.maxSpeed * 0.45 : 0.0;
        double accel = Math.signum(target - speed) == Math.signum(speed) || speed == 0 ? 0.006 : 0.014;
        speed += MathHelper.clamp(target - speed, -accel, accel);
        if (isTouchingWater()) {
            speed *= 0.9;
        }
        Vec3d forward = Vec3d.fromPolar(0.0f, getYaw());
        double vy = getVelocity().y;
        vy = isOnGround() ? -0.04 : (vy - 0.08) * 0.98;
        if (isTouchingWater()) {
            vy = Math.max(vy, -0.06);
        }
        setVelocity(forward.x * speed, vy, forward.z * speed);
        move(MovementType.SELF, getVelocity());
        if (horizontalCollision) {
            speed *= 0.5;
        }
    }

    private void clientTick() {
        prevClientTurretYaw = clientTurretYaw;
        prevClientGunPitch = clientGunPitch;
        clientTurretYaw += MathHelper.wrapDegrees(getTurretYaw() - clientTurretYaw) * 0.5f;
        clientGunPitch += (getGunPitch() - clientGunPitch) * 0.5f;
        prevTrackScroll = trackScroll;
        Vec3d d = new Vec3d(getX() - prevX, 0, getZ() - prevZ);
        trackScroll += (float) d.length() * (d.dotProduct(Vec3d.fromPolar(0, getYaw())) >= 0 ? 1 : -1);
        float hp = getHealth() / type.maxHealth;
        if (hp < 0.35f && random.nextFloat() < 0.6f) {
            getWorld().addParticle(hp < 0.15f ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, getX() + random.nextGaussian() * 0.6,
                    getY() + 2.0, getZ() + random.nextGaussian() * 0.6, 0, 0.05, 0);
        }
        if (hp < 0.15f && random.nextFloat() < 0.3f) {
            getWorld().addParticle(ParticleTypes.FLAME, getX() + random.nextGaussian() * 0.5, getY() + 1.6,
                    getZ() + random.nextGaussian() * 0.5, 0, 0.03, 0);
        }
    }

    private void serverTick() {
        if (cannonCooldown > 0) {
            cannonCooldown--;
        }
        LivingEntity driver = getControllingPassenger();
        if (driver != null) {
            float wanted = MathHelper.wrapDegrees(driver.getHeadYaw() - getYaw());
            wanted = MathHelper.clamp(wanted, -type.traverse, type.traverse);
            float cur = getTurretYaw();
            float step = MathHelper.clamp(MathHelper.wrapDegrees(wanted - cur), -type.turretRate, type.turretRate);
            dataTracker.set(TURRET_YAW, MathHelper.clamp(MathHelper.wrapDegrees(cur + step), -type.traverse, type.traverse));
            float pitch = MathHelper.clamp(driver.getPitch(), type.minPitch, type.maxPitch);
            float p = getGunPitch();
            dataTracker.set(GUN_PITCH, p + MathHelper.clamp(pitch - p, -1.5f, 1.5f));
        }
        // run soldiers over
        Vec3d pos = getPos();
        if (lastServerPos != null && driver != null) {
            double moved = pos.subtract(lastServerPos).horizontalLength();
            if (moved > 0.08) {
                Box box = getBoundingBox().expand(0.3, 0, 0.3);
                for (Entity e : getWorld().getOtherEntities(this, box, e -> e instanceof LivingEntity && !hasPassenger(e))) {
                    e.damage(driver instanceof PlayerEntity pl ? getDamageSources().playerAttack(pl) : getDamageSources().generic(),
                            (float) (moved * 30.0));
                }
            }
        }
        lastServerPos = pos;
        if (getHealth() / type.maxHealth < 0.15f && age % 160 == 0) {
            getWorld().playSound(null, getX(), getY(), getZ(), PfSounds.TANK_FIRE, SoundCategory.NEUTRAL, 1.0f, 1.0f);
        }
    }

    // ------------------------------------------------------------------ cannon

    public void tryFire(PlayerEntity player) {
        if (getControllingPassenger() != player || cannonCooldown > 0 || !(getWorld() instanceof ServerWorld sw)) {
            return;
        }
        cannonCooldown = type.reloadTicks;
        dataTracker.set(FIRED_AT, sw.getTime());
        Vec3d muzzle = muzzleWorld(1.0f);
        Vec3d dir = Vec3d.fromPolar(getGunPitch(), getYaw() + getTurretYaw());
        RocketEntity shell = new RocketEntity(PfEntities.ROCKET, sw);
        shell.setup(player, RocketEntity.Kind.TANK_SHELL);
        Vec3d start = muzzle.add(dir.multiply(0.6));
        shell.setPosition(start.x, start.y, start.z);
        shell.setVelocity(dir.x, dir.y, dir.z, 5.0f, 0.2f);
        sw.spawnEntity(shell);
        sw.playSound(null, muzzle.x, muzzle.y, muzzle.z, PfSounds.TANK_CANNON, SoundCategory.PLAYERS, 6.0f, 1.0f);
        sw.spawnParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 12, 0.3, 0.3, 0.3, 0.04);
    }

    /** Muzzle position in world space (rotations as in the renderer). */
    public Vec3d muzzleWorld(float tickDelta) {
        Vec3d m = type.muzzle.subtract(type.gunPivot).rotateX((float) Math.toRadians(-getGunPitch()));
        m = m.add(type.gunPivot).subtract(type.turretPivot).rotateY((float) Math.toRadians(-getTurretYaw())).add(type.turretPivot);
        return getLerpedPos(tickDelta).add(m.rotateY((float) Math.toRadians(-getYaw(tickDelta))));
    }

    // ------------------------------------------------------------------ riding

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (held.isOf(Items.IRON_INGOT) && getHealth() < type.maxHealth) {
            if (!getWorld().isClient) {
                setHealth(getHealth() + 25f);
                if (!player.getAbilities().creativeMode) {
                    held.decrement(1);
                }
                getWorld().playSound(null, getX(), getY(), getZ(), PfSounds.TANK_REPAIR, SoundCategory.NEUTRAL, 1.0f, 1.0f);
            }
            return ActionResult.success(getWorld().isClient);
        }
        if (player.shouldCancelInteraction()) {
            return ActionResult.PASS;
        }
        if (!getWorld().isClient) {
            return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().isEmpty();
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof PlayerEntity p ? p : null;
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        if (!hasPassenger(passenger)) {
            return;
        }
        Vec3d local = type.seat;
        if (type.seatOnTurret) {
            float ty = getWorld().isClient ? clientTurretYaw : getTurretYaw();
            local = local.subtract(type.turretPivot).rotateY((float) Math.toRadians(-ty)).add(type.turretPivot);
        }
        Vec3d w = local.rotateY((float) Math.toRadians(-getYaw()));
        positionUpdater.accept(passenger, getX() + w.x, getY() + w.y, getZ() + w.z);
    }

    @Override
    public double getMountedHeightOffset() {
        return type.seat.y;
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    @Override
    public boolean collidesWith(Entity other) {
        return (other.isCollidable() || other.isPushable()) && !isConnectedThroughVehicle(other);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    // ------------------------------------------------------------------ damage

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (getWorld().isClient || isRemoved()) {
            return true;
        }
        float a = amount;
        if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
            a *= 1.6f;
        } else if (source.isOf(PfDamage.BULLET)) {
            a *= 0.12f;
        } else if (source.getAttacker() instanceof PlayerEntity p && p.getAbilities().creativeMode && source.getSource() == p) {
            dropStack(new ItemStack(type == Type.SHERMAN ? PfItems.SHERMAN : PfItems.JAGDPANTHER));
            discard();
            return true;
        } else {
            a *= 0.5f;
        }
        if (source.getAttacker() != null && hasPassenger(source.getAttacker())) {
            return false;
        }
        setHealth(getHealth() - a);
        dataTracker.set(HURT, 10);
        if (getHealth() <= 0) {
            destroy();
        }
        return true;
    }

    private void destroy() {
        if (getWorld() instanceof ServerWorld sw) {
            removeAllPassengers();
            PfExplosions.explode(sw, this, null, getPos().add(0, 1.2, 0), 4.0f, PfSounds.EXPLOSION_VEHICLE);
            dropStack(new ItemStack(Items.IRON_INGOT, 6 + random.nextInt(8)));
            discard();
        }
    }

    // ------------------------------------------------------------------ sync

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yaw;
        this.lerpTicks = 6;
    }

    private void updateLerp() {
        if (isLogicalSideForUpdatingMovement()) {
            lerpTicks = 0;
            updateTrackedPosition(getX(), getY(), getZ());
        }
        if (lerpTicks > 0) {
            double x = getX() + (lerpX - getX()) / lerpTicks;
            double y = getY() + (lerpY - getY()) / lerpTicks;
            double z = getZ() + (lerpZ - getZ()) / lerpTicks;
            setYaw(getYaw() + MathHelper.wrapDegrees(lerpYaw - getYaw()) / lerpTicks);
            lerpTicks--;
            setPosition(x, y, z);
            setRotation(getYaw(), getPitch());
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(type == Type.SHERMAN ? PfItems.SHERMAN : PfItems.JAGDPANTHER);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Health")) {
            setHealth(nbt.getFloat("Health"));
        }
        dataTracker.set(TURRET_YAW, nbt.getFloat("TurretYaw"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putFloat("Health", getHealth());
        nbt.putFloat("TurretYaw", getTurretYaw());
    }
}
