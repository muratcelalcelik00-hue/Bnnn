package com.bnnn.polyfield;

import com.bnnn.polyfield.item.GrenadeItem;
import com.bnnn.polyfield.item.TankItem;
import com.bnnn.polyfield.weapon.AmmoType;
import com.bnnn.polyfield.weapon.GunItem;
import com.bnnn.polyfield.weapon.Weapon;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class PfItems {
    public static final Item GRENADE_M24 = register("grenade_m24", new GrenadeItem(true, new Item.Settings().maxCount(16)));
    public static final Item GRENADE_MK2 = register("grenade_mk2", new GrenadeItem(false, new Item.Settings().maxCount(16)));
    public static final Item GERMAN_SOLDIER_SPAWN_EGG = register("german_soldier_spawn_egg",
            new SpawnEggItem(PfEntities.GERMAN_SOLDIER, 0x2b3532, 0x6b6f5a, new Item.Settings()));
    public static final Item ALLIED_SOLDIER_SPAWN_EGG = register("allied_soldier_spawn_egg",
            new SpawnEggItem(PfEntities.ALLIED_SOLDIER, 0x4a3a28, 0x8a7550, new Item.Settings()));
    public static final Item SHERMAN = register("sherman", new TankItem(() -> PfEntities.SHERMAN, new Item.Settings().maxCount(1)));
    public static final Item JAGDPANTHER = register("jagdpanther", new TankItem(() -> PfEntities.JAGDPANTHER, new Item.Settings().maxCount(1)));

    static {
        for (Weapon w : Weapon.values()) {
            w.setItem(register(w.id, new GunItem(w, new Item.Settings().maxCount(1))));
        }
        for (AmmoType a : AmmoType.values()) {
            a.setItem(register(a.itemId, new Item(new Item.Settings().maxCount(a == AmmoType.ROCKET ? 8 : 64))));
        }
    }

    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, PolyfieldMod.id("polyfield"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.polyfield"))
                    .icon(() -> new ItemStack(Weapon.MP40.item()))
                    .entries((context, entries) -> {
                        for (Weapon w : Weapon.values()) {
                            entries.add(w.item());
                        }
                        for (AmmoType a : AmmoType.values()) {
                            entries.add(a.item());
                        }
                        entries.add(GRENADE_M24);
                        entries.add(GRENADE_MK2);
                        entries.add(SHERMAN);
                        entries.add(JAGDPANTHER);
                        entries.add(GERMAN_SOLDIER_SPAWN_EGG);
                        entries.add(ALLIED_SOLDIER_SPAWN_EGG);
                    })
                    .build());

    private PfItems() {
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, PolyfieldMod.id(name), item);
    }

    public static void init() {
    }
}
