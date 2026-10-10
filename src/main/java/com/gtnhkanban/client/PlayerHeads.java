package com.gtnhkanban.client;

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
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

/**
 * Players' skin faces, for avatars. A player in the world already has their skin; anyone else's is looked up from
 * their profile once, in the background, and cached. Until then (or if that fails) the face is Steve's.
 */
final class PlayerHeads {

    private static final Map<UUID, ResourceLocation> SKINS = new ConcurrentHashMap<UUID, ResourceLocation>();
    /** Players whose profile lookup has started, so each is asked for at most once per session. */
    private static final Map<UUID, Boolean> REQUESTED = new ConcurrentHashMap<UUID, Boolean>();
    private static final ExecutorService LOOKUPS = Executors.newSingleThreadExecutor(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "GTNH Kanban skin lookup");
            thread.setDaemon(true);
            return thread;
        }
    });

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
        if (mc.theWorld != null) {
            EntityPlayer online = mc.theWorld.func_152378_a(playerId);
            if (online instanceof AbstractClientPlayer) return ((AbstractClientPlayer) online).getLocationSkin();
        }
        ResourceLocation known = SKINS.get(playerId);
        if (known != null) return known;
        if (REQUESTED.put(playerId, Boolean.TRUE) == null) lookUp(mc, new GameProfile(playerId, name));
        return AbstractClientPlayer.locationStevePng;
    }

    private static void lookUp(final Minecraft mc, final GameProfile profile) {
        LOOKUPS.execute(new Runnable() {

            @Override
            public void run() {
                try {
                    final GameProfile filled = mc.func_152347_ac()
                        .fillProfileProperties(profile, false);
                    // Textures are loaded on the client thread.
                    mc.func_152344_a(new Runnable() {

                        @Override
                        public void run() {
                            mc.func_152342_ad()
                                .func_152790_a(filled, new SkinManager.SkinAvailableCallback() {

                                    @Override
                                    public void func_152121_a(MinecraftProfileTexture.Type type,
                                        ResourceLocation location) {
                                        if (type == MinecraftProfileTexture.Type.SKIN)
                                            SKINS.put(profile.getId(), location);
                                    }
                                }, false);
                        }
                    });
                } catch (RuntimeException exception) {
                    // Offline, rate limited or unknown player: keep Steve.
                }
            }
        });
    }
}
