package com.bnnn.polyfield;

import com.bnnn.polyfield.entity.GrenadeEntity;
import com.bnnn.polyfield.entity.RocketEntity;
import com.bnnn.polyfield.entity.SoldierEntity;
import com.bnnn.polyfield.entity.TankEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.Heightmap;

public final class PfEntities {
    public static final EntityType<SoldierEntity> GERMAN_SOLDIER = soldier("german_soldier");
    public static final EntityType<SoldierEntity> ALLIED_SOLDIER = soldier("allied_soldier");

    public static final EntityType<RocketEntity> ROCKET = Registry.register(Registries.ENTITY_TYPE, PolyfieldMod.id("rocket"),
            FabricEntityTypeBuilder.<RocketEntity>create(SpawnGroup.MISC, RocketEntity::new)
                    .dimensions(EntityDimensions.fixed(0.3f, 0.3f)).trackRangeChunks(10).trackedUpdateRate(2).build());

    public static final EntityType<GrenadeEntity> GRENADE = Registry.register(Registries.ENTITY_TYPE, PolyfieldMod.id("grenade"),
            FabricEntityTypeBuilder.<GrenadeEntity>create(SpawnGroup.MISC, GrenadeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f)).trackRangeChunks(8).trackedUpdateRate(2).build());

    public static final EntityType<TankEntity> SHERMAN = tank("sherman", 3.4f, 2.7f);
    public static final EntityType<TankEntity> JAGDPANTHER = tank("jagdpanther", 3.6f, 2.5f);

    private PfEntities() {
    }

    private static EntityType<SoldierEntity> soldier(String name) {
        return Registry.register(Registries.ENTITY_TYPE, PolyfieldMod.id(name),
                FabricEntityTypeBuilder.<SoldierEntity>createMob()
                        .spawnGroup(SpawnGroup.MONSTER)
                        .entityFactory(SoldierEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6f, 1.85f))
                        .trackRangeChunks(10)
                        .defaultAttributes(SoldierEntity::createSoldierAttributes)
                        .spawnRestriction(SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, SoldierEntity::canSpawn)
                        .build());
    }

    private static EntityType<TankEntity> tank(String name, float width, float height) {
        return Registry.register(Registries.ENTITY_TYPE, PolyfieldMod.id(name),
                FabricEntityTypeBuilder.<TankEntity>create(SpawnGroup.MISC, TankEntity::new)
                        .dimensions(EntityDimensions.fixed(width, height)).trackRangeChunks(10).trackedUpdateRate(2).build());
    }

    public static void init() {
        // squads of soldiers in open overworld land
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld()
                        .and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
                        .and(BiomeSelectors.tag(BiomeTags.IS_RIVER).negate()),
                SpawnGroup.MONSTER, GERMAN_SOLDIER, 22, 2, 4);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld()
                        .and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
                        .and(BiomeSelectors.tag(BiomeTags.IS_RIVER).negate()),
                SpawnGroup.MONSTER, ALLIED_SOLDIER, 22, 2, 4);
    }
}
