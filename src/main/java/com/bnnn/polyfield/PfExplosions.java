package com.bnnn.polyfield;

import com.bnnn.polyfield.weapon.GunLogic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

/**
 * Explosions with Polyfield's own sound instead of the vanilla boom: the
 * explosion is resolved on the server (affectWorld(false) plays nothing) and
 * the particles and sound are sent separately.
 */
public final class PfExplosions {
    private PfExplosions() {
    }

    public static void explode(ServerWorld world, @Nullable Entity source, @Nullable Entity owner, Vec3d pos, float power,
                               SoundEvent sound) {
        boolean breakBlocks;
        if (owner instanceof PlayerEntity) {
            breakBlocks = world.getGameRules().getBoolean(PfGameRules.EXPLOSIVES_BREAK_BLOCKS);
        } else {
            breakBlocks = GunLogic.canGrief(owner != null ? owner : source);
        }
        Explosion.DestructionType type = breakBlocks ? Explosion.DestructionType.DESTROY_WITH_DECAY : Explosion.DestructionType.KEEP;
        Explosion explosion = new Explosion(world, source, world.getDamageSources().explosion(source, owner), null,
                pos.x, pos.y, pos.z, power, false, type);
        explosion.collectBlocksAndDamageEntities();
        explosion.affectWorld(false);
        for (PlayerEntity p : explosion.getAffectedPlayers().keySet()) {
            if (p instanceof ServerPlayerEntity sp) {
                sp.velocityModified = true;
            }
        }
        world.spawnParticles(power >= 2.0f ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z,
                1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 20, power * 0.3, power * 0.2, power * 0.3, 0.05);
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 12, power * 0.2, power * 0.1, power * 0.2, 0.08);
        world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.BLOCKS, 4.0f, 0.9f + world.random.nextFloat() * 0.2f);
        world.playSound(null, pos.x, pos.y, pos.z, PfSounds.EXPLOSION_DEBRIS, SoundCategory.BLOCKS, 2.0f, 1.0f);
    }
}
