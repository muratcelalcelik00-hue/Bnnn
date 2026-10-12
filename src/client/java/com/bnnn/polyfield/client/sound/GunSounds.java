package com.bnnn.polyfield.client.sound;

import com.bnnn.polyfield.PfSounds;
import com.bnnn.polyfield.weapon.Weapon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Plays Polyfield's gun sounds the way the game does: automatic weapons with
 * a looping burst sample while the trigger is held and a tail when the burst
 * ends; semi-automatic weapons with a shot (plus tail) per round.
 */
public final class GunSounds {
    private static final Map<Integer, Loop> LOOPS = new HashMap<>();

    private GunSounds() {
    }

    public static void onShot(Entity shooter, Weapon w) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        PfSounds.WeaponSounds s = PfSounds.of(w);
        SoundCategory cat = shooter instanceof PlayerEntity ? SoundCategory.PLAYERS : SoundCategory.HOSTILE;
        float pitch = 1.0f + w.pitchOffset;
        if (w.soundLoop) {
            Loop loop = LOOPS.get(shooter.getId());
            if (loop == null || loop.isDone() || loop.weapon != w) {
                if (loop != null) {
                    loop.stop();
                }
                loop = new Loop(shooter, w, s.shot(), cat, pitch);
                LOOPS.put(shooter.getId(), loop);
                client.getSoundManager().play(loop);
            }
            loop.lastShot = 0;
        } else {
            play(client, s.shot(), cat, shooter, 1.0f, pitch * (0.97f + client.world.random.nextFloat() * 0.06f));
            if (w.kind == Weapon.Kind.PISTOL || w.kind == Weapon.Kind.RIFLE) {
                play(client, s.tail(), cat, shooter, 0.6f, pitch);
            }
        }
    }

    private static void play(MinecraftClient client, SoundEvent ev, SoundCategory cat, Entity at, float volume, float pitch) {
        client.getSoundManager().play(new PositionedSoundInstance(ev, cat, volume, pitch, SoundInstance.createRandom(),
                at.getX(), at.getEyeY(), at.getZ()));
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        Iterator<Map.Entry<Integer, Loop>> it = LOOPS.entrySet().iterator();
        while (it.hasNext()) {
            Loop loop = it.next().getValue();
            loop.lastShot++;
            int grace = Math.round(loop.weapon.ticksPerShot()) + 3;
            if (loop.entity.isRemoved() || loop.lastShot > grace) {
                loop.stop();
                if (!loop.entity.isRemoved() && client.world != null) {
                    play(client, PfSounds.of(loop.weapon).tail(), loop.getCategory(), loop.entity, 1.0f, loop.basePitch);
                }
                it.remove();
            } else if (loop.isDone()) {
                it.remove();
            }
        }
    }

    public static void clear() {
        for (Loop l : LOOPS.values()) {
            l.stop();
        }
        LOOPS.clear();
    }

    static final class Loop extends MovingSoundInstance {
        final Entity entity;
        final Weapon weapon;
        final float basePitch;
        int lastShot;

        Loop(Entity entity, Weapon weapon, SoundEvent sound, SoundCategory category, float pitch) {
            super(sound, category, SoundInstance.createRandom());
            this.entity = entity;
            this.weapon = weapon;
            this.basePitch = pitch;
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = 1.0f;
            this.pitch = pitch;
            this.x = entity.getX();
            this.y = entity.getEyeY();
            this.z = entity.getZ();
        }

        @Override
        public void tick() {
            if (entity.isRemoved()) {
                setDone();
                return;
            }
            x = entity.getX();
            y = entity.getEyeY();
            z = entity.getZ();
        }

        void stop() {
            setDone();
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }
    }
}
