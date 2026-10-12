package com.bnnn.polyfield.weapon;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GunItem extends Item {
    private static final String AMMO = "PfAmmo";

    public final Weapon weapon;

    public GunItem(Weapon weapon, Settings settings) {
        super(settings);
        this.weapon = weapon;
    }

    /** Rounds in the magazine. A gun without the tag (fresh from the creative tab) is full. */
    public static int getAmmo(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gun)) {
            return 0;
        }
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(AMMO)) {
            return gun.weapon.magazine;
        }
        return nbt.getInt(AMMO);
    }

    public static void setAmmo(ItemStack stack, int ammo) {
        stack.getOrCreateNbt().putInt(AMMO, ammo);
    }

    public static ItemStack emptyStack(Weapon w) {
        ItemStack s = new ItemStack(w.item());
        setAmmo(s, 0);
        return s;
    }

    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        // the magazine count changes on every shot; do not re-play the equip animation
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean allowContinuingBlockBreaking(PlayerEntity player, ItemStack oldStack, ItemStack newStack) {
        return true;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13.0f * getAmmo(stack) / (float) weapon.magazine);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        float f = getAmmo(stack) / (float) weapon.magazine;
        return MathHelper.hsvToRgb(0.12f * f, 0.9f, 1.0f);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.polyfield.ammo", getAmmo(stack), weapon.magazine).formatted(Formatting.YELLOW));
        tooltip.add(Text.translatable("tooltip.polyfield.damage", String.format("%.1f", weapon.minecraftDamage())).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.polyfield.rate", weapon.roundsPerSecond * 60,
                Text.translatable("tooltip.polyfield.mode." + weapon.fireMode.name().toLowerCase())).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.polyfield.uses", Text.translatable("item.polyfield." + weapon.ammo.itemId)).formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.translatable("tooltip.polyfield.controls").formatted(Formatting.DARK_AQUA));
    }
}
