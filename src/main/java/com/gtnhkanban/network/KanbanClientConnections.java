package com.gtnhkanban.network;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;

import com.gtnhkanban.KanbanMod;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;

/** Tracks advertised channels by connection, never by player UUID or username. */
public final class KanbanClientConnections {

    private static final KanbanClientConnections INSTANCE = new KanbanClientConnections();
    private static final Set<NetworkManager> CLIENTS = Collections
        .synchronizedSet(Collections.newSetFromMap(new WeakHashMap<NetworkManager, Boolean>()));

    private KanbanClientConnections() {}

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    public static boolean supportsKanban(EntityPlayerMP player) {
        if (player == null || player.playerNetServerHandler == null) return false;
        NetworkManager manager = player.playerNetServerHandler.netManager;
        // Forge's integrated server shares this mod with its local client.
        return manager.isChannelOpen() && (manager.isLocalChannel() || CLIENTS.contains(manager));
    }

    public static void clear() {
        CLIENTS.clear();
    }

    @SubscribeEvent
    public void onRegistration(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
        if (event.side != Side.SERVER || !event.registrations.contains(KanbanMod.MODID)) return;
        if ("REGISTER".equals(event.operation)) CLIENTS.add(event.manager);
        else if ("UNREGISTER".equals(event.operation)) CLIENTS.remove(event.manager);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ServerDisconnectionFromClientEvent event) {
        CLIENTS.remove(event.manager);
    }
}
