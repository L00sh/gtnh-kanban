package com.gtnhkanban.network;

import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Queues packet work for the 1.7.10 server thread, which has no WorldServer task scheduler. */
public final class KanbanServerTaskQueue {

    private static final ConcurrentLinkedQueue<Runnable> TASKS = new ConcurrentLinkedQueue<Runnable>();
    private static final KanbanServerTaskQueue INSTANCE = new KanbanServerTaskQueue();

    private KanbanServerTaskQueue() {}

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    public static void enqueue(Runnable task) {
        TASKS.add(task);
    }

    /** Drops work left over from a stopped server so it can never run against the next world. */
    public static void clear() {
        TASKS.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Runnable task;
        int processed = 0;
        while (processed++ < 1024 && (task = TASKS.poll()) != null) {
            task.run();
        }
    }
}
