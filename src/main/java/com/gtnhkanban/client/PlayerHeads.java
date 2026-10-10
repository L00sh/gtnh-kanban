package com.gtnhkanban.client;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;

import cpw.mods.fml.common.FMLLog;

/**
 * Players' skin faces, for avatars. Works like player head items: a player's signed "textures" profile entry is kept
 * (here in {@code gtnhkanban/skin-profiles.dat}, for heads in the world's NBT), so their skin shows while they are
 * offline and after restarts without asking Mojang again. The skin image itself is cached on disk by Minecraft's skin
 * manager. Entries come from players seen in the world, or are looked up once in the background; an entry older than
 * a week is refreshed the same way while the cached skin keeps showing. Steve stands in until a skin is known.
 */
final class PlayerHeads {

    private static final String TEXTURES = "textures";
    private static final long REFRESH_AFTER_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private static final Map<UUID, Entry> PROFILES = new ConcurrentHashMap<UUID, Entry>();
    private static final Map<UUID, ResourceLocation> SKINS = new ConcurrentHashMap<UUID, ResourceLocation>();
    /** Players whose skin has been asked of the skin manager, or looked up, this session. */
    private static final Map<UUID, Boolean> LOADING = new ConcurrentHashMap<UUID, Boolean>();
    private static final Map<UUID, Boolean> LOOKED_UP = new ConcurrentHashMap<UUID, Boolean>();
    private static final ExecutorService BACKGROUND = Executors.newSingleThreadExecutor(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "GTNH Kanban skins");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static boolean loaded;

    /** A cached "textures" profile entry and when it was fetched. */
    private static final class Entry {

        final String name, value, signature;
        final long fetchedAt;

        Entry(String name, String value, String signature, long fetchedAt) {
            this.name = name;
            this.value = value;
            this.signature = signature;
            this.fetchedAt = fetchedAt;
        }
    }

    private PlayerHeads() {}

    /** Draws the face (with its hat layer) as a {@code size} x {@code size} square. */
    static void draw(Minecraft mc, UUID playerId, String name, int x, int y, int size) {
        mc.getTextureManager()
            .bindTexture(skin(mc, playerId, name));
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Gui.func_152125_a(x, y, 8, 8, 8, 8, size, size, 64, 32);
        Gui.func_152125_a(x, y, 40, 8, 8, 8, size, size, 64, 32);
    }

    private static ResourceLocation skin(Minecraft mc, UUID playerId, String name) {
        if (playerId == null) return AbstractClientPlayer.locationStevePng;
        load(mc);
        if (mc.theWorld != null) {
            EntityPlayer online = mc.theWorld.func_152378_a(playerId);
            if (online instanceof AbstractClientPlayer) {
                // Like a head placed while its player is around: keep their textures for when they are not.
                remember(mc, online.getGameProfile());
                return ((AbstractClientPlayer) online).getLocationSkin();
            }
        }
        Entry entry = PROFILES.get(playerId);
        if (entry != null) {
            if (LOADING.put(playerId, Boolean.TRUE) == null) loadSkin(mc, profile(playerId, entry));
            if (System.currentTimeMillis() - entry.fetchedAt > REFRESH_AFTER_MILLIS) lookUp(mc, playerId, name);
        } else lookUp(mc, playerId, name);
        ResourceLocation known = SKINS.get(playerId);
        return known != null ? known : AbstractClientPlayer.locationStevePng;
    }

    private static GameProfile profile(UUID playerId, Entry entry) {
        GameProfile profile = new GameProfile(playerId, entry.name);
        profile.getProperties()
            .put(TEXTURES, new Property(TEXTURES, entry.value, entry.signature));
        return profile;
    }

    /** Hands the profile to the skin manager, which reads the image from its disk cache or downloads it once. */
    private static void loadSkin(Minecraft mc, final GameProfile profile) {
        mc.func_152342_ad()
            .func_152790_a(profile, new SkinManager.SkinAvailableCallback() {

                @Override
                public void func_152121_a(MinecraftProfileTexture.Type type, ResourceLocation location) {
                    if (type == MinecraftProfileTexture.Type.SKIN) SKINS.put(profile.getId(), location);
                }
            }, false);
    }

    /** Fetches a player's profile from Mojang, at most once per session, and keeps its textures entry. */
    private static void lookUp(final Minecraft mc, final UUID playerId, final String name) {
        if (LOOKED_UP.put(playerId, Boolean.TRUE) != null) return;
        BACKGROUND.execute(new Runnable() {

            @Override
            public void run() {
                try {
                    final GameProfile filled = mc.func_152347_ac()
                        .fillProfileProperties(new GameProfile(playerId, name), false);
                    mc.func_152344_a(new Runnable() {

                        @Override
                        public void run() {
                            if (remember(mc, filled)) {
                                LOADING.put(playerId, Boolean.TRUE);
                                loadSkin(mc, filled);
                            }
                        }
                    });
                } catch (RuntimeException exception) {
                    // Offline or rate limited: keep the cached skin, or Steve.
                }
            }
        });
    }

    /** Keeps the profile's textures entry if it has one that is new or changed; true when it did. */
    private static boolean remember(Minecraft mc, GameProfile profile) {
        if (profile == null || profile.getId() == null) return false;
        Property textures = Iterables.getFirst(
            profile.getProperties()
                .get(TEXTURES),
            null);
        if (textures == null) return false;
        Entry previous = PROFILES.get(profile.getId());
        boolean changed = previous == null || !previous.value.equals(textures.getValue());
        if (!changed && System.currentTimeMillis() - previous.fetchedAt < REFRESH_AFTER_MILLIS) return false;
        PROFILES.put(
            profile.getId(),
            new Entry(
                profile.getName() == null ? "" : profile.getName(),
                textures.getValue(),
                textures.getSignature() == null ? "" : textures.getSignature(),
                System.currentTimeMillis()));
        if (changed) SKINS.remove(profile.getId());
        save(mc);
        return changed;
    }

    private static File file(Minecraft mc) {
        return new File(new File(mc.mcDataDir, "gtnhkanban"), "skin-profiles.dat");
    }

    private static synchronized void load(Minecraft mc) {
        if (loaded) return;
        loaded = true;
        File file = file(mc);
        if (!file.isFile()) return;
        try {
            NBTTagCompound root = CompressedStreamTools.read(file);
            if (root == null) return;
            for (Object key : root.func_150296_c()) {
                try {
                    NBTTagCompound tag = root.getCompoundTag((String) key);
                    PROFILES.put(
                        UUID.fromString((String) key),
                        new Entry(
                            tag.getString("name"),
                            tag.getString("value"),
                            tag.getString("signature"),
                            tag.getLong("fetched")));
                } catch (RuntimeException exception) {
                    // One unreadable entry is simply looked up again.
                }
            }
        } catch (Exception exception) {
            FMLLog.warning("GTNH Kanban could not read its skin cache: %s", exception);
        }
    }

    /** Writes the cache in the background, to a temporary file first so a crash never leaves it half written. */
    private static void save(Minecraft mc) {
        final File file = file(mc);
        final Map<UUID, Entry> snapshot = new HashMap<UUID, Entry>(PROFILES);
        BACKGROUND.execute(new Runnable() {

            @Override
            public void run() {
                try {
                    NBTTagCompound root = new NBTTagCompound();
                    for (Map.Entry<UUID, Entry> profile : snapshot.entrySet()) {
                        NBTTagCompound tag = new NBTTagCompound();
                        tag.setString("name", profile.getValue().name);
                        tag.setString("value", profile.getValue().value);
                        tag.setString("signature", profile.getValue().signature);
                        tag.setLong("fetched", profile.getValue().fetchedAt);
                        root.setTag(
                            profile.getKey()
                                .toString(),
                            tag);
                    }
                    file.getParentFile()
                        .mkdirs();
                    File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
                    CompressedStreamTools.write(root, temporary);
                    try {
                        java.nio.file.Files.move(
                            temporary.toPath(),
                            file.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                        java.nio.file.Files
                            .move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception exception) {
                    FMLLog.warning("GTNH Kanban could not save its skin cache: %s", exception);
                }
            }
        });
    }
}
