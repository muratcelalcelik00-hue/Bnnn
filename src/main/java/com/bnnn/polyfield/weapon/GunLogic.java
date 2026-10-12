package com.bnnn.polyfield.weapon;

import com.bnnn.polyfield.PfDamage;
import com.bnnn.polyfield.PfEntities;
import com.bnnn.polyfield.PfGameRules;
import com.bnnn.polyfield.PfSounds;
import com.bnnn.polyfield.entity.RocketEntity;
import com.bnnn.polyfield.entity.SoldierEntity;
import com.bnnn.polyfield.entity.TankEntity;
import com.bnnn.polyfield.net.PfNetwork;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side firing, hitscan, reloading. Shared by players and soldiers. */
public final class GunLogic {
    private static final Map<UUID, ShooterState> PLAYERS = new HashMap<>();
    private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.55f, 0.02f, 0.02f), 1.2f);

    private GunLogic() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(GunLogic::tickServer);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PLAYERS.remove(handler.player.getUuid()));
    }

    public static ShooterState state(PlayerEntity player) {
        return PLAYERS.computeIfAbsent(player.getUuid(), u -> new ShooterState());
    }

    private static void tickServer(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickPlayer(player);
        }
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        ShooterState s = state(player);
        s.tickTimers();
        tickBolt(player, s);
        if (player.getVehicle() instanceof TankEntity tank) {
            if (s.trigger) {
                tank.tryFire(player);
            }
            s.triggerFresh = false;
            return;
        }
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof GunItem gun)) {
            if (s.reloading()) {
                s.cancelReload();
            }
            s.triggerFresh = false;
            return;
        }
        Weapon w = gun.weapon;
        if (s.reloading()) {
            if (player.getInventory().selectedSlot != s.reloadSlot || s.reloadWeapon != w) {
                s.cancelReload();
            } else if (--s.reloadTicks <= 0) {
                finishReload(player, stack, w, s);
            }
        }
        if (player.isSpectator() || !player.isAlive() || s.reloading()) {
            return;
        }
        if (w.fireMode == Weapon.FireMode.AUTO) {
            if (s.trigger) {
                int guard = 0;
                while (s.cooldown <= 0 && guard++ < 4) {
                    if (!fireOnce(player, stack, w, s, null, 1.0f, 1.0f)) {
                        s.trigger = false;
                        break;
                    }
                    s.cooldown += w.ticksPerShot();
                }
            }
            if (s.cooldown < 0) {
                s.cooldown = 0;
            }
        } else if (s.triggerFresh && s.cooldown <= 0) {
            s.triggerFresh = false;
            fireOnce(player, stack, w, s, null, 1.0f, 1.0f);
            s.cooldown = w.ticksPerShot();
        }
    }

    public static void tickBolt(LivingEntity e, ShooterState s) {
        if (s.boltTicks > 0 && --s.boltTicks == 0 && s.boltWeapon != null) {
            e.getWorld().playSound(null, e.getX(), e.getY(), e.getZ(), PfSounds.of(s.boltWeapon).bolt(), category(e), 0.9f, 1.0f);
        }
    }

    /** Reload progress for soldiers (players are handled in {@link #tickPlayer}). */
    public static void tickMobReload(LivingEntity e, ItemStack stack, Weapon w, ShooterState s) {
        if (s.reloading() && --s.reloadTicks <= 0) {
            finishReload(e, stack, w, s);
        }
    }

    // ------------------------------------------------------------------ firing

    /**
     * Fires one round. Returns false when the magazine was empty.
     *
     * @param aimDir    direction to fire in, or null for the shooter's look vector
     * @param spreadMul extra inaccuracy (soldiers)
     * @param damageMul damage factor (soldiers by difficulty)
     */
    public static boolean fireOnce(LivingEntity shooter, ItemStack stack, Weapon w, ShooterState s, @Nullable Vec3d aimDir,
                                   float spreadMul, float damageMul) {
        if (!(shooter.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        int ammo = GunItem.getAmmo(stack);
        if (ammo <= 0) {
            world.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.BLOCK_LEVER_CLICK,
                    category(shooter), 0.6f, 1.8f);
            if (shooter instanceof ServerPlayerEntity p) {
                startReload(p, stack, w, s);
            }
            return false;
        }
        GunItem.setAmmo(stack, ammo - 1);
        Vec3d look = aimDir != null ? aimDir.normalize() : shooter.getRotationVec(1.0f);
        float spread = (s.aiming ? w.aimSpread() : w.hipSpread()) * spreadMul;
        if (w.fireMode == Weapon.FireMode.AUTO) {
            spread += Math.min(2.0f, s.consecutive * 0.2f) * (s.aiming ? 0.4f : 1.0f);
        }
        if (!shooter.isOnGround()) {
            spread *= 1.8f;
        } else if (shooter.getVelocity().horizontalLengthSquared() > 0.003) {
            spread *= 1.3f;
        }
        s.consecutive++;
        s.ticksSinceShot = 0;
        Random random = shooter.getRandom();
        Vec3d dir = spread(look, spread, random);

        if (w.isLauncher()) {
            launchRocket(world, shooter, w, dir);
            PfNetwork.sendShot(world, shooter, w, shooter.getEyePos().add(dir.multiply(2.0)), 0);
        } else {
            hitscan(world, shooter, w, dir, damageMul);
            if (random.nextInt(3) == 0) {
                world.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), PfSounds.SHELL_DROP, category(shooter), 0.35f,
                        0.9f + random.nextFloat() * 0.2f);
            }
        }
        if (w.boltAction) {
            s.boltTicks = 7;
            s.boltWeapon = w;
        }
        if (w == Weapon.M1_GARAND && ammo - 1 == 0) {
            world.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), PfSounds.GARAND_PING, category(shooter), 1.0f, 1.0f);
        }
        return true;
    }

    private static void launchRocket(ServerWorld world, LivingEntity shooter, Weapon w, Vec3d dir) {
        RocketEntity rocket = new RocketEntity(PfEntities.ROCKET, world);
        rocket.setup(shooter, w == Weapon.BAZOOKA ? RocketEntity.Kind.BAZOOKA : RocketEntity.Kind.PANZERFAUST);
        Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() > 1e-6) {
            right = right.normalize();
        }
        Vec3d start = shooter.getEyePos().add(dir.multiply(0.9)).add(right.multiply(0.15)).add(0, -0.12, 0);
        rocket.setPosition(start.x, start.y, start.z);
        float speed = w == Weapon.BAZOOKA ? 3.2f : 2.4f;
        rocket.setVelocity(dir.x, dir.y, dir.z, speed, 0.0f);
        world.spawnEntity(rocket);
    }

    private static void hitscan(ServerWorld world, LivingEntity shooter, Weapon w, Vec3d dir, float damageMul) {
        Vec3d eye = shooter.getEyePos();
        double range = w.maxRange();
        Vec3d end = eye.add(dir.multiply(range));
        BlockHitResult bhr = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, shooter));
        Vec3d stop = bhr.getType() == HitResult.Type.MISS ? end : bhr.getPos();
        Box box = shooter.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);
        EntityHitResult ehr = ProjectileUtil.raycast(shooter, eye, stop, box, e -> canBeShot(shooter, e), eye.squaredDistanceTo(stop));
        if (ehr != null) {
            hitEntity(world, shooter, w, ehr.getEntity(), ehr.getPos(), dir, damageMul);
            PfNetwork.sendShot(world, shooter, w, ehr.getPos(), 2);
        } else if (bhr.getType() != HitResult.Type.MISS) {
            hitBlock(world, shooter, bhr);
            PfNetwork.sendShot(world, shooter, w, bhr.getPos(), 1);
        } else {
            PfNetwork.sendShot(world, shooter, w, end, 0);
        }
    }

    private static boolean canBeShot(LivingEntity shooter, Entity e) {
        if (e == shooter || e.isSpectator() || !e.isAlive() || !e.canHit() || shooter.isConnectedThroughVehicle(e)) {
            return false;
        }
        // soldiers do not shoot through their own squad mates
        return !(shooter instanceof SoldierEntity a && e instanceof SoldierEntity b && a.faction == b.faction);
    }

    private static void hitEntity(ServerWorld world, LivingEntity shooter, Weapon w, Entity target, Vec3d hit, Vec3d dir, float damageMul) {
        float dmg = w.minecraftDamage() * damageMul;
        boolean head = false;
        if (target instanceof LivingEntity le && !(target instanceof TankEntity)) {
            double headBottom = le.getEyeY() - 0.25 * Math.min(1.5, le.getHeight() / 1.8);
            if (hit.y >= headBottom) {
                head = true;
                dmg *= 2.0f;
            }
        }
        DamageSource src = PfDamage.bullet(world, shooter, shooter);
        Vec3d vel = target.getVelocity();
        boolean wasAlive = target.isAlive();
        target.timeUntilRegen = 0;
        boolean damaged = target.damage(src, dmg);
        target.timeUntilRegen = 0;
        if (damaged && target instanceof LivingEntity) {
            // bullets do not knock targets around like a sword
            target.setVelocity(vel.add(dir.x * 0.04, 0.0, dir.z * 0.04));
        }
        if (target instanceof LivingEntity && !(target instanceof TankEntity)) {
            world.spawnParticles(BLOOD, hit.x, hit.y, hit.z, 8, 0.08, 0.08, 0.08, 0.0);
            world.playSound(null, hit.x, hit.y, hit.z, PfSounds.IMPACT_BODY, SoundCategory.PLAYERS, 0.8f, 0.9f + world.random.nextFloat() * 0.2f);
        } else {
            world.spawnParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 4, 0.05, 0.05, 0.05, 0.2);
            world.playSound(null, hit.x, hit.y, hit.z, PfSounds.IMPACT_RICOCHET, SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
        if (shooter instanceof ServerPlayerEntity sp && damaged) {
            int flags = (head ? PfNetwork.HIT_HEADSHOT : 0) | (wasAlive && !target.isAlive() ? PfNetwork.HIT_KILL : 0);
            PfNetwork.sendHit(sp, flags);
        }
    }

    private static void hitBlock(ServerWorld world, LivingEntity shooter, BlockHitResult bhr) {
        BlockPos pos = bhr.getBlockPos();
        BlockState state = world.getBlockState(pos);
        Vec3d hp = bhr.getPos();
        if (!state.isAir()) {
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), hp.x, hp.y, hp.z, 6, 0.05, 0.05, 0.05, 0.1);
        }
        world.spawnParticles(ParticleTypes.SMOKE, hp.x, hp.y, hp.z, 1, 0.0, 0.0, 0.0, 0.01);
        BlockSoundGroup g = state.getSoundGroup();
        world.playSound(null, hp.x, hp.y, hp.z, impactSound(g, world.random), SoundCategory.BLOCKS, 0.7f, 0.9f + world.random.nextFloat() * 0.2f);
        if (g == BlockSoundGroup.GLASS && canGrief(shooter)) {
            world.breakBlock(pos, false, shooter);
        }
    }

    private static SoundEvent impactSound(BlockSoundGroup g, Random r) {
        if (g == BlockSoundGroup.WOOD || g == BlockSoundGroup.NETHER_WOOD || g == BlockSoundGroup.BAMBOO_WOOD
                || g == BlockSoundGroup.CHERRY_WOOD) {
            return PfSounds.IMPACT_WOOD;
        }
        if (g == BlockSoundGroup.METAL || g == BlockSoundGroup.ANVIL || g == BlockSoundGroup.CHAIN
                || g == BlockSoundGroup.COPPER || g == BlockSoundGroup.NETHERITE) {
            return PfSounds.IMPACT_RICOCHET;
        }
        if (g == BlockSoundGroup.STONE || g == BlockSoundGroup.DEEPSLATE || g == BlockSoundGroup.NETHER_BRICKS
                || g == BlockSoundGroup.TUFF || g == BlockSoundGroup.BASALT) {
            return r.nextInt(4) == 0 ? PfSounds.IMPACT_RICOCHET : PfSounds.IMPACT_CONCRETE;
        }
        return PfSounds.IMPACT_DIRT;
    }

    public static boolean canGrief(@Nullable Entity shooter) {
        if (shooter == null) {
            return false;
        }
        if (shooter instanceof PlayerEntity) {
            return true;
        }
        return shooter.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
    }

    public static Vec3d spread(Vec3d dir, float degrees, Random r) {
        if (degrees <= 0) {
            return dir;
        }
        double a = Math.toRadians(degrees) * Math.sqrt(r.nextDouble());
        double phi = r.nextDouble() * Math.PI * 2;
        Vec3d up = Math.abs(dir.y) < 0.99 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        Vec3d u = dir.crossProduct(up).normalize();
        Vec3d v = dir.crossProduct(u).normalize();
        return dir.multiply(Math.cos(a)).add(u.multiply(Math.sin(a) * Math.cos(phi))).add(v.multiply(Math.sin(a) * Math.sin(phi))).normalize();
    }

    public static SoundCategory category(Entity e) {
        return e instanceof PlayerEntity ? SoundCategory.PLAYERS : SoundCategory.HOSTILE;
    }

    // ------------------------------------------------------------------ reload

    public static void requestReload(ServerPlayerEntity p) {
        ItemStack stack = p.getMainHandStack();
        if (stack.getItem() instanceof GunItem gun) {
            startReload(p, stack, gun.weapon, state(p));
        }
    }

    public static boolean startReload(LivingEntity e, ItemStack stack, Weapon w, ShooterState s) {
        if (s.reloading() || GunItem.getAmmo(stack) >= w.magazine) {
            return false;
        }
        if (e instanceof PlayerEntity p) {
            if (!freeAmmo(p) && countAmmo(p, w.ammo) <= 0) {
                p.sendMessage(Text.translatable("message.polyfield.no_ammo",
                        Text.translatable("item.polyfield." + w.ammo.itemId)).formatted(Formatting.RED), true);
                return false;
            }
            s.reloadSlot = p.getInventory().selectedSlot;
        } else {
            s.reloadSlot = 0;
        }
        s.reloadTicks = w.reloadTicks;
        s.reloadWeapon = w;
        e.getWorld().playSound(null, e.getX(), e.getY(), e.getZ(), PfSounds.of(w).reload(), category(e), 1.0f, 1.0f);
        if (e.getWorld() instanceof ServerWorld sw) {
            PfNetwork.sendReloadAnim(sw, e, w.reloadTicks);
        }
        return true;
    }

    private static void finishReload(LivingEntity e, ItemStack stack, Weapon w, ShooterState s) {
        int have = GunItem.getAmmo(stack);
        int need = Math.max(0, w.magazine - have);
        int got = need;
        if (e instanceof PlayerEntity p && !freeAmmo(p)) {
            got = takeAmmo(p, w.ammo, need);
        }
        GunItem.setAmmo(stack, have + got);
        s.cancelReload();
    }

    public static boolean freeAmmo(PlayerEntity p) {
        return p.getAbilities().creativeMode || p.getWorld().getGameRules().getBoolean(PfGameRules.INFINITE_AMMO);
    }

    public static int countAmmo(PlayerEntity p, AmmoType type) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isOf(type.item())) {
                n += st.getCount();
            }
        }
        return n;
    }

    private static int takeAmmo(PlayerEntity p, AmmoType type, int want) {
        int got = 0;
        for (int i = 0; i < p.getInventory().size() && got < want; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isOf(type.item())) {
                int t = Math.min(want - got, st.getCount());
                st.decrement(t);
                got += t;
            }
        }
        return got;
    }
}
