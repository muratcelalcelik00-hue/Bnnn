package com.bnnn.polyfield.weapon;

import net.minecraft.item.Item;

public enum AmmoType {
    PISTOL("pistol_ammo"),
    RIFLE("rifle_ammo"),
    MG("mg_belt"),
    ROCKET("rocket");

    public final String itemId;
    private Item item;

    AmmoType(String itemId) {
        this.itemId = itemId;
    }

    public Item item() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }
}
