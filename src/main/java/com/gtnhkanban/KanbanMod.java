package com.gtnhkanban;

import com.gtnhkanban.command.KanbanCommand;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.KanbanServerTaskQueue;
import com.gtnhkanban.proxy.CommonProxy;
import com.gtnhkanban.storage.KanbanStorage;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;

@Mod(modid = KanbanMod.MODID, version = Tags.VERSION, name = "GTNH Kanban", acceptedMinecraftVersions = "[1.7.10]")
public class KanbanMod {

    public static final String MODID = "gtnhkanban";

    @SidedProxy(clientSide = "com.gtnhkanban.proxy.ClientProxy", serverSide = "com.gtnhkanban.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        KanbanNetwork.registerMessages();
        KanbanStorage.register();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
        event.registerServerCommand(new KanbanCommand());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        KanbanServerTaskQueue.clear();
        KanbanNetwork.clearUploads();
        KanbanStorage.unload();
    }
}
