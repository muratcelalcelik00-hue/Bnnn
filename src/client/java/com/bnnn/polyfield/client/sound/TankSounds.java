package com.bnnn.polyfield.client.sound;

import com.bnnn.polyfield.PfSounds;
import com.bnnn.polyfield.entity.TankEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/** Engine idle/drive loops for crewed tanks, cross-faded by speed. */
public final class TankSounds {
    private static final Map<Integer, Engine[]> ENGINES = new HashMap<>();

    private TankSounds() {
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            ENGINES.clear();
            return;
        }
        for (Entity e : client.world.getEntities()) {
            if (e instanceof TankEntity tank && tank.hasPassengers() && tank.squaredDistanceTo(client.player) < 64 * 64) {
                Engine[] pair = ENGINES.get(tank.getId());
                if (pair == null || pair[0].isDone()) {
                    boolean sherman = tank.type == TankEntity.Type.SHERMAN;
                    Engine idle = new Engine(tank, sherman ? PfSounds.TANK_SHERMAN_IDLE : PfSounds.TANK_PANTHER_IDLE, false);
                    Engine drive = new Engine(tank, sherman ? PfSounds.TANK_SHERMAN_DRIVE : PfSounds.TANK_PANTHER_DRIVE, true);
                    client.getSoundManager().play(idle);
                    client.getSoundManager().play(drive);
                    ENGINES.put(tank.getId(), new Engine[]{idle, drive});
                }
            }
        }
        ENGINES.entrySet().removeIf(en -> en.getValue()[0].isDone() && en.getValue()[1].isDone());
    }

    static final class Engine extends MovingSoundInstance {
        private final TankEntity tank;
        private final boolean drive;
        private int empty;

        Engine(TankEntity tank, SoundEvent sound, boolean drive) {
            super(sound, SoundCategory.NEUTRAL, SoundInstance.createRandom());
            this.tank = tank;
            this.drive = drive;
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = drive ? 0.01f : 0.8f;
            this.x = tank.getX();
            this.y = tank.getY();
            this.z = tank.getZ();
        }

        @Override
        public void tick() {
            if (tank.isRemoved()) {
                setDone();
                return;
            }
            if (!tank.hasPassengers()) {
                if (++empty > 40) {
                    setDone();
                    return;
                }
            } else {
                empty = 0;
            }
            x = tank.getX();
            y = tank.getY() + 1.0;
            z = tank.getZ();
            double v = Math.sqrt(MathHelper.square(tank.getX() - tank.prevX) + MathHelper.square(tank.getZ() - tank.prevZ));
            float s = (float) MathHelper.clamp(v / tank.type.maxSpeed, 0, 1);
            float fade = tank.hasPassengers() ? 1f : Math.max(0, 1f - empty / 40f);
            if (drive) {
                volume = (0.05f + 0.95f * s) * fade * 1.2f;
                pitch = 0.85f + 0.3f * s;
            } else {
                volume = (1.0f - 0.7f * s) * fade;
                pitch = 1.0f;
            }
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }
    }
}
