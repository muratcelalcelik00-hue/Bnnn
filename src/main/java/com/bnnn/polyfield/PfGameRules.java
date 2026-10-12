package com.bnnn.polyfield;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

public final class PfGameRules {
    /** Reloading takes no ammo items from the inventory. */
    public static final GameRules.Key<GameRules.BooleanRule> INFINITE_AMMO =
            GameRuleRegistry.register("pfInfiniteAmmo", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(false));
    /** Polyfield soldiers spawn naturally in the overworld. */
    public static final GameRules.Key<GameRules.BooleanRule> SOLDIER_SPAWNING =
            GameRuleRegistry.register("pfSoldierSpawning", GameRules.Category.SPAWNING, GameRuleFactory.createBooleanRule(true));
    /** Rockets, grenades and tank shells fired by players break blocks. */
    public static final GameRules.Key<GameRules.BooleanRule> EXPLOSIVES_BREAK_BLOCKS =
            GameRuleRegistry.register("pfExplosivesBreakBlocks", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));

    private PfGameRules() {
    }

    public static void init() {
    }
}
