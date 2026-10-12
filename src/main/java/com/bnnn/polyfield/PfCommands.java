package com.bnnn.polyfield;

import com.bnnn.polyfield.entity.SoldierEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/** /polyfield squad <german|allied> [count] spawns a squad a short distance away. */
public final class PfCommands {
    private PfCommands() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("polyfield").requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("map")
                                .then(CommandManager.literal("build").executes(ctx -> com.bnnn.polyfield.map.PfMap.build(ctx.getSource())))
                                .then(CommandManager.literal("tp").executes(ctx -> com.bnnn.polyfield.map.PfMap.teleport(ctx.getSource(), "SpawnPoint_Red"))
                                        .then(CommandManager.literal("german").executes(ctx -> com.bnnn.polyfield.map.PfMap.teleport(ctx.getSource(), "SpawnPoint_Blue"))))
                                .then(CommandManager.literal("battle").executes(ctx -> com.bnnn.polyfield.map.PfMap.battle(ctx.getSource()))))
                        .then(CommandManager.literal("squad")
                                .then(CommandManager.literal("german")
                                        .executes(ctx -> squad(ctx, PfEntities.GERMAN_SOLDIER, 4))
                                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 32))
                                                .executes(ctx -> squad(ctx, PfEntities.GERMAN_SOLDIER, IntegerArgumentType.getInteger(ctx, "count")))))
                                .then(CommandManager.literal("allied")
                                        .executes(ctx -> squad(ctx, PfEntities.ALLIED_SOLDIER, 4))
                                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 32))
                                                .executes(ctx -> squad(ctx, PfEntities.ALLIED_SOLDIER, IntegerArgumentType.getInteger(ctx, "count"))))))));
    }

    private static int squad(CommandContext<ServerCommandSource> ctx, EntityType<SoldierEntity> type, int count) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();
        Vec3d base = src.getPosition();
        Vec3d look = src.getRotation() != null ? Vec3d.fromPolar(0, src.getRotation().y) : new Vec3d(0, 0, 1);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double x = base.x + look.x * 24 + (world.random.nextDouble() - 0.5) * 6;
            double z = base.z + look.z * 24 + (world.random.nextDouble() - 0.5) * 6;
            BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, base.y, z));
            SoldierEntity s = type.spawn(world, top, SpawnReason.COMMAND);
            if (s != null) {
                spawned++;
            }
        }
        int n = spawned;
        src.sendFeedback(() -> Text.translatable("command.polyfield.squad", n), true);
        return spawned;
    }
}
