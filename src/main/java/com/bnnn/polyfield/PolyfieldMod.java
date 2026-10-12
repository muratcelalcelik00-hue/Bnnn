package com.bnnn.polyfield;

import com.bnnn.polyfield.net.PfNetwork;
import com.bnnn.polyfield.weapon.GunLogic;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polyfield in Minecraft: the weapons, soldiers and tanks of Polyfield
 * (com.MA.Polyfield 0.7.5) with the game's own models and sounds, converted
 * from the user's own copy by converter/extract_polyfield.py.
 */
public class PolyfieldMod implements ModInitializer {
    public static final String MOD_ID = "polyfield";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        PfSounds.init();
        PfGameRules.init();
        PfEntities.init();
        PfItems.init();
        PfNetwork.init();
        GunLogic.init();
        com.bnnn.polyfield.map.PfMap.init();
        PfCommands.init();
        SelfTest.init();
        LOGGER.info("Polyfield loaded: {} weapons", com.bnnn.polyfield.weapon.Weapon.values().length);
    }
}
