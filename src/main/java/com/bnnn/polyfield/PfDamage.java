package com.bnnn.polyfield;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class PfDamage {
    public static final RegistryKey<DamageType> BULLET = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, PolyfieldMod.id("bullet"));

    private PfDamage() {
    }

    public static DamageSource bullet(World world, @Nullable Entity source, @Nullable Entity attacker) {
        return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BULLET), source, attacker);
    }
}
