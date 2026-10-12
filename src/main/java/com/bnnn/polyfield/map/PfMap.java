package com.bnnn.polyfield.map;

import com.bnnn.polyfield.PfEntities;
import com.bnnn.polyfield.PolyfieldMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Polyfield's Normandy map placed in the world: the server lays invisible
 * collider blocks (terrain to 1/8 block, buildings to 1 block) and water,
 * clients draw the map with Polyfield's own meshes (see MapRenderer).
 */
public final class PfMap {
    public static final Identifier STATE_PACKET = PolyfieldMod.id("map_state");
    public static final Block COLLIDER = Registry.register(Registries.BLOCK, PolyfieldMod.id("collider"),
            new ColliderBlock(AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f).dropsNothing().nonOpaque()
                    .noBlockBreakParticles().pistonBehavior(PistonBehavior.BLOCK).sounds(BlockSoundGroup.GRAVEL)));
    private static final int HALF = 375;
    private static Job job;

    /** The converted collision data (heights in Polyfield metres, building voxels, anchors). */
    public static final class Data {
        int n;
        float cell;
        float sea;
        float[] h;
        int[] vox;
        final Map<String, float[]> anchors = new HashMap<>();

        float height(double x, double z) {
            double u = MathHelper.clamp(x / cell + n / 2.0, 0, n - 1.001);
            double v = MathHelper.clamp(z / cell + n / 2.0, 0, n - 1.001);
            int iu = (int) u;
            int iv = (int) v;
            double fu = u - iu;
            double fv = v - iv;
            double a = h[iv * n + iu] * (1 - fu) + h[iv * n + iu + 1] * fu;
            double b = h[(iv + 1) * n + iu] * (1 - fu) + h[(iv + 1) * n + iu + 1] * fu;
            return (float) (a * (1 - fv) + b * fv);
        }
    }

    public static final class State extends PersistentState {
        public boolean built;
        public int ox;
        public int oy = 64;
        public int oz;

        static State read(NbtCompound nbt) {
            State s = new State();
            s.built = nbt.getBoolean("Built");
            s.ox = nbt.getInt("X");
            s.oy = nbt.getInt("Y");
            s.oz = nbt.getInt("Z");
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            nbt.putBoolean("Built", built);
            nbt.putInt("X", ox);
            nbt.putInt("Y", oy);
            nbt.putInt("Z", oz);
            return nbt;
        }
    }

    private PfMap() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (job != null && job.step()) {
                job = null;
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendState(handler.player));
    }

    public static State state(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(State::read, State::new, "polyfield_map");
    }

    public static void sendState(ServerPlayerEntity p) {
        State s = state(p.getServer());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(s.built);
        buf.writeInt(s.ox);
        buf.writeInt(s.oy);
        buf.writeInt(s.oz);
        ServerPlayNetworking.send(p, STATE_PACKET, buf);
    }

    public static Optional<Data> load(MinecraftServer server) {
        Optional<Resource> res = server.getResourceManager().getResource(PolyfieldMod.id("map/normandy.pfc"));
        if (res.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream raw = res.get().getInputStream(); DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            Data d = new Data();
            d.n = in.readInt();
            d.cell = in.readFloat();
            d.sea = in.readFloat();
            in.readFloat();
            d.h = new float[d.n * d.n];
            for (int i = 0; i < d.h.length; i++) {
                d.h[i] = in.readFloat();
            }
            int na = in.readInt();
            for (int i = 0; i < na; i++) {
                byte[] b = new byte[in.readUnsignedShort()];
                in.readFully(b);
                d.anchors.put(new String(b, java.nio.charset.StandardCharsets.UTF_8), new float[]{in.readFloat(), in.readFloat(), in.readFloat()});
            }
            int nv = in.readInt();
            d.vox = new int[nv * 3];
            for (int i = 0; i < d.vox.length; i++) {
                d.vox[i] = in.readInt();
            }
            return Optional.of(d);
        } catch (Exception e) {
            PolyfieldMod.LOGGER.error("Could not read the Polyfield map", e);
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ commands

    public static int build(ServerCommandSource src) {
        MinecraftServer server = src.getServer();
        Optional<Data> data = load(server);
        if (data.isEmpty()) {
            src.sendError(Text.translatable("command.polyfield.map.missing"));
            return 0;
        }
        if (job != null) {
            src.sendError(Text.translatable("command.polyfield.map.busy"));
            return 0;
        }
        ServerWorld w = server.getOverworld();
        State s = state(server);
        int top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING, new BlockPos(0, 0, 0)).getY();
        s.ox = 0;
        s.oz = 0;
        s.oy = Math.max(64, top + 6);
        s.markDirty();
        job = new Job(w, data.get(), s, src);
        src.sendFeedback(() -> Text.translatable("command.polyfield.map.started", s.oy), true);
        return 1;
    }

    public static double surface(Data d, State s, double x, double z) {
        return s.oy + d.height(x - s.ox, z - s.oz);
    }

    public static int teleport(ServerCommandSource src, String anchor) {
        ServerPlayerEntity p = src.getPlayer();
        Optional<Data> data = load(src.getServer());
        State s = state(src.getServer());
        if (p == null || data.isEmpty() || !s.built) {
            src.sendError(Text.translatable("command.polyfield.map.not_built"));
            return 0;
        }
        float[] a = data.get().anchors.getOrDefault(anchor, new float[]{0, 0, -240});
        double x = s.ox + a[0];
        double z = s.oz + a[2];
        p.teleport(src.getServer().getOverworld(), x + 0.5, surface(data.get(), s, x, z) + 1.5, z + 0.5,
                anchor.contains("Red") ? 180f : 0f, 0f);
        return 1;
    }

    public static int battle(ServerCommandSource src) {
        Optional<Data> data = load(src.getServer());
        State s = state(src.getServer());
        if (data.isEmpty() || !s.built) {
            src.sendError(Text.translatable("command.polyfield.map.not_built"));
            return 0;
        }
        ServerWorld w = src.getServer().getOverworld();
        int n = 0;
        for (String flag : new String[]{"Flag_A", "Flag_B", "Flag_C", "Flag_D", "Flag_E"}) {
            n += squad(w, data.get(), s, data.get().anchors.get(flag), PfEntities.GERMAN_SOLDIER, 3);
        }
        n += squad(w, data.get(), s, data.get().anchors.get("SpawnPoint_Red"), PfEntities.ALLIED_SOLDIER, 10);
        float[] beach = data.get().anchors.get("SpawnPoint_Red");
        if (beach != null) {
            double x = s.ox + beach[0] + 12;
            double z = s.oz + beach[2] + 6;
            PfEntities.SHERMAN.spawn(w, BlockPos.ofFloored(x, surface(data.get(), s, x, z) + 0.5, z), SpawnReason.COMMAND);
        }
        int total = n;
        src.sendFeedback(() -> Text.translatable("command.polyfield.squad", total), true);
        return n;
    }

    private static int squad(ServerWorld w, Data d, State s, float[] at, EntityType<?> type, int count) {
        if (at == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < count; i++) {
            double x = s.ox + at[0] + (w.random.nextDouble() - 0.5) * 10;
            double z = s.oz + at[2] + (w.random.nextDouble() - 0.5) * 10;
            if (type.spawn(w, BlockPos.ofFloored(x, surface(d, s, x, z) + 0.6, z), SpawnReason.COMMAND) != null) {
                n++;
            }
        }
        return n;
    }

    /** Lays the colliders a few rows per tick so the server keeps running. */
    private static final class Job {
        final ServerWorld w;
        final Data d;
        final State s;
        final ServerCommandSource src;
        int row = -HALF;
        int voxel;
        final BlockState water = Blocks.WATER.getDefaultState();

        Job(ServerWorld w, Data d, State s, ServerCommandSource src) {
            this.w = w;
            this.d = d;
            this.s = s;
            this.src = src;
        }

        boolean step() {
            int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
            BlockPos.Mutable p = new BlockPos.Mutable();
            if (row < HALF) {
                for (int r = 0; r < 4 && row < HALF; r++, row++) {
                    int z = row;
                    for (int x = -HALF; x < HALF; x++) {
                        float h = d.height(x + 0.5, z + 0.5);
                        float lo = Math.min(h, Math.min(Math.min(d.height(x - 0.5, z + 0.5), d.height(x + 1.5, z + 0.5)),
                                Math.min(d.height(x + 0.5, z - 0.5), d.height(x + 0.5, z + 1.5))));
                        int top = MathHelper.floor(h);
                        int layers = Math.round((h - top) * 8f);
                        int bottom = Math.min(MathHelper.floor(lo) - 1, top - 2);
                        for (int y = bottom; y < top; y++) {
                            w.setBlockState(p.set(s.ox + x, s.oy + y, s.oz + z), COLLIDER.getDefaultState(), flags);
                        }
                        if (layers > 0) {
                            w.setBlockState(p.set(s.ox + x, s.oy + top, s.oz + z), COLLIDER.getDefaultState().with(ColliderBlock.LAYERS, layers), flags);
                        }
                        for (int y = Math.max(top + (layers > 0 ? 1 : 0), -6); y < 0; y++) {
                            w.setBlockState(p.set(s.ox + x, s.oy + y, s.oz + z), water, flags);
                        }
                    }
                }
                if ((row + HALF) % 100 == 0) {
                    int pct = (row + HALF) * 100 / (2 * HALF);
                    src.sendFeedback(() -> Text.translatable("command.polyfield.map.progress", pct), false);
                }
                return false;
            }
            for (int k = 0; k < 4000 && voxel < d.vox.length / 3; k++, voxel++) {
                p.set(s.ox + d.vox[voxel * 3], s.oy + d.vox[voxel * 3 + 1], s.oz + d.vox[voxel * 3 + 2]);
                w.setBlockState(p, COLLIDER.getDefaultState(), flags);
            }
            if (voxel < d.vox.length / 3) {
                return false;
            }
            s.built = true;
            s.markDirty();
            for (ServerPlayerEntity pl : w.getServer().getPlayerManager().getPlayerList()) {
                sendState(pl);
            }
            src.sendFeedback(() -> Text.translatable("command.polyfield.map.done"), true);
            return true;
        }
    }
}
