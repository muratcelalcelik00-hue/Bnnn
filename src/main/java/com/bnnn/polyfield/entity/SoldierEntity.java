package com.bnnn.polyfield.entity;

import com.bnnn.polyfield.PfEntities;
import com.bnnn.polyfield.PfGameRules;
import com.bnnn.polyfield.PfSounds;
import com.bnnn.polyfield.entity.ai.SoldierAttackGoal;
import com.bnnn.polyfield.weapon.GunItem;
import com.bnnn.polyfield.weapon.GunLogic;
import com.bnnn.polyfield.weapon.ShooterState;
import com.bnnn.polyfield.weapon.Weapon;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** A Polyfield soldier (German or Allied) that hunts players with a Polyfield weapon. */
public class SoldierEntity extends HostileEntity {
    private static final Weapon[] GER_WEAPONS = {Weapon.KAR98K, Weapon.MP40, Weapon.STG44, Weapon.MG42, Weapon.VG15, Weapon.MG34,
            Weapon.WALTHER_P38, Weapon.PANZERFAUST};
    private static final int[] GER_WEIGHTS = {25, 25, 20, 9, 9, 4, 5, 3};
    private static final Weapon[] ALLIED_WEAPONS = {Weapon.M1_GARAND, Weapon.THOMPSON, Weapon.STEN, Weapon.BAR, Weapon.M3_GREASE_GUN,
            Weapon.M1915, Weapon.WELROD, Weapon.BAZOOKA};
    private static final int[] ALLIED_WEIGHTS = {26, 20, 16, 12, 12, 6, 3, 3};

    public final Faction faction;
    public final ShooterState gun = new ShooterState();
    public int grenadeCooldown = 200;

    public SoldierEntity(EntityType<? extends SoldierEntity> type, World world) {
        super(type, world);
        this.faction = type == PfEntities.ALLIED_SOLDIER ? Faction.ALLIES : Faction.GER;
        this.experiencePoints = 8;
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.08f);
    }

    public static DefaultAttributeContainer.Builder createSoldierAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_ARMOR, 2.0);
    }

    public static boolean canSpawn(EntityType<? extends SoldierEntity> type, ServerWorldAccess world, SpawnReason reason,
                                   BlockPos pos, Random random) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GENERATION) {
            if (!world.toServerWorld().getGameRules().getBoolean(PfGameRules.SOLDIER_SPAWNING)) {
                return false;
            }
            // surface troops; mostly at night, now and then by day
            if (!world.isSkyVisible(pos)) {
                return false;
            }
            if (!HostileEntity.isSpawnDark(world, pos, random) && random.nextInt(4) != 0) {
                return false;
            }
        }
        return canMobSpawn(type, world, reason, pos, random);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, new SoldierAttackGoal(this));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));
        goalSelector.add(7, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this, SoldierEntity.class));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        targetSelector.add(3, new ActiveTargetGoal<>(this, SoldierEntity.class, 10, true, false,
                e -> e instanceof SoldierEntity s && s.faction != this.faction));
        targetSelector.add(4, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                 @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
        if (getMainHandStack().isEmpty()) {
            equipStack(EquipmentSlot.MAINHAND, new ItemStack(pickWeapon(world.getRandom()).item()));
        }
        return data;
    }

    private Weapon pickWeapon(Random r) {
        Weapon[] ws = faction == Faction.GER ? GER_WEAPONS : ALLIED_WEAPONS;
        int[] wt = faction == Faction.GER ? GER_WEIGHTS : ALLIED_WEIGHTS;
        int total = 0;
        for (int w : wt) {
            total += w;
        }
        int x = r.nextInt(total);
        for (int i = 0; i < ws.length; i++) {
            x -= wt[i];
            if (x < 0) {
                return ws[i];
            }
        }
        return ws[0];
    }

    @Nullable
    public Weapon weapon() {
        return getMainHandStack().getItem() instanceof GunItem g ? g.weapon : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient) {
            gun.tickTimers();
            GunLogic.tickBolt(this, gun);
            Weapon w = weapon();
            if (w != null) {
                GunLogic.tickMobReload(this, getMainHandStack(), w, gun);
            }
            if (grenadeCooldown > 0) {
                grenadeCooldown--;
            }
        }
    }

    /** Difficulty-dependent damage of soldier bullets. */
    public float damageFactor() {
        return switch (getWorld().getDifficulty()) {
            case PEACEFUL, EASY -> 0.4f;
            case NORMAL -> 0.6f;
            case HARD -> 0.85f;
        };
    }

    /** Difficulty-dependent extra inaccuracy. */
    public float spreadFactor() {
        return switch (getWorld().getDifficulty()) {
            case PEACEFUL, EASY -> 3.0f;
            case NORMAL -> 2.2f;
            case HARD -> 1.6f;
        };
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity old = getTarget();
        super.setTarget(target);
        if (target != null && old == null && !getWorld().isClient && random.nextInt(6) == 0) {
            playSound(PfSounds.SOLDIER_WHISTLE, 1.5f, 1.0f);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return PfSounds.SOLDIER_SCREAM;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return random.nextInt(40) == 0 ? PfSounds.SOLDIER_WILHELM : PfSounds.SOLDIER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(PfSounds.SOLDIER_STEP, 0.25f, 1.0f);
    }

    @Override
    protected float getSoundVolume() {
        return 1.2f;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return !hasCustomName();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("GrenadeCooldown", grenadeCooldown);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        grenadeCooldown = nbt.getInt("GrenadeCooldown");
    }

    @Override
    public boolean isTeammate(net.minecraft.entity.Entity other) {
        if (other instanceof SoldierEntity s && s.faction == faction) {
            return true;
        }
        return super.isTeammate(other);
    }
}
