package com.bnnn.polyfield.client.render;

import com.bnnn.polyfield.PolyfieldMod;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Lazily loaded, reload-aware cache of converted Polyfield meshes. */
public final class PfModels implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, Optional<PfModel>> STATIC = new HashMap<>();
    private static final Map<String, Optional<PfSkinnedModel>> SKINNED = new HashMap<>();
    private static boolean warned;

    @Override
    public Identifier getFabricId() {
        return PolyfieldMod.id("models");
    }

    @Override
    public void reload(ResourceManager manager) {
        STATIC.clear();
        SKINNED.clear();
        warned = false;
    }

    @Nullable
    public static PfModel get(String name) {
        return STATIC.computeIfAbsent(name, n -> load(n, ".pfm", PfModel::read)).orElse(null);
    }

    @Nullable
    public static PfSkinnedModel skinned(String name) {
        return SKINNED.computeIfAbsent(name, n -> load(n, ".pfs", PfSkinnedModel::read)).orElse(null);
    }

    private interface Reader<T> {
        T read(InputStream in) throws java.io.IOException;
    }

    private static <T> Optional<T> load(String name, String ext, Reader<T> reader) {
        Identifier id = PolyfieldMod.id("pfmodels/" + name + ext);
        Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(id);
        if (res.isEmpty()) {
            if (!warned) {
                warned = true;
                PolyfieldMod.LOGGER.warn("Polyfield model {} missing: run converter/extract_polyfield.py on your Polyfield copy", id);
            }
            return Optional.empty();
        }
        try (InputStream in = res.get().getInputStream()) {
            return Optional.of(reader.read(in));
        } catch (Exception e) {
            PolyfieldMod.LOGGER.error("Could not read Polyfield model {}", id, e);
            return Optional.empty();
        }
    }
}
