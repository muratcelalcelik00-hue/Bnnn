package com.bnnn.polyfield.item;

import com.bnnn.polyfield.entity.TankEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.function.Supplier;

/** Places a drivable tank. */
public class TankItem extends Item {
    private final Supplier<EntityType<TankEntity>> type;

    public TankItem(Supplier<EntityType<TankEntity>> type, Settings settings) {
        super(settings);
        this.type = type;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (!(world instanceof ServerWorld sw)) {
            return ActionResult.SUCCESS;
        }
        BlockPos pos = ctx.getBlockPos().offset(ctx.getSide());
        TankEntity tank = type.get().create(sw);
        if (tank == null) {
            return ActionResult.FAIL;
        }
        PlayerEntity player = ctx.getPlayer();
        float yaw = player != null ? player.getYaw() : 0.0f;
        tank.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0f);
        sw.spawnEntity(tank);
        sw.emitGameEvent(player, GameEvent.ENTITY_PLACE, pos);
        if (player == null || !player.getAbilities().creativeMode) {
            ctx.getStack().decrement(1);
        }
        return ActionResult.CONSUME;
    }
}
