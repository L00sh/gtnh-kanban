package com.gtnhkanban.storage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Binds one {@link KanbanStore} to the running server and saves it whenever the overworld saves. */
public final class KanbanStorage {

    private static final KanbanStorage INSTANCE = new KanbanStorage();
    private static KanbanStore store;

    private KanbanStorage() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    /** Server thread only. Loads lazily so worlds that never use the board never get a kanban file. */
    public static KanbanStore get() {
        if (store == null) {
            WorldServer overworld = MinecraftServer.getServer()
                .worldServerForDimension(0);
            store = KanbanStore.load(
                overworld.getSaveHandler()
                    .getMapFileFromName(KanbanStore.DATA_NAME));
        }
        return store;
    }

    /** Called after the server has stopped and saved, so one singleplayer world's data never leaks into the next. */
    public static void unload() {
        store = null;
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (store != null && !event.world.isRemote && event.world.provider.dimensionId == 0) store.save();
    }
}
