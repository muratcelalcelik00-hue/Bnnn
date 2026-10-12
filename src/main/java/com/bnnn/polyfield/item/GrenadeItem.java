package com.bnnn.polyfield.item;

import com.bnnn.polyfield.PfEntities;
import com.bnnn.polyfield.entity.GrenadeEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class GrenadeItem extends Item {
    public final boolean stick;

    public GrenadeItem(boolean stick, Settings settings) {
        super(settings);
        this.stick = stick;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS,
                0.6f, 0.6f);
        if (!world.isClient) {
            throwGrenade(world, user, stick, user.getPitch(), user.getYaw(), 1.1f);
        }
        user.getItemCooldownManager().set(this, 20);
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        if (!user.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    public static void throwGrenade(World world, LivingEntity thrower, boolean stick, float pitch, float yaw, float speed) {
        GrenadeEntity g = new GrenadeEntity(PfEntities.GRENADE, world);
        g.setup(thrower, stick);
        g.setPosition(thrower.getX(), thrower.getEyeY() - 0.1, thrower.getZ());
        g.setVelocity(thrower, pitch - 8.0f, yaw, 0.0f, speed, 1.0f);
        world.spawnEntity(g);
    }
}
