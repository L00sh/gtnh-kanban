package com.gtnhkanban.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * While a Kanban screen draws, {@link Minecraft#fontRenderer} is the half-size {@link KanbanFont}, so everything drawn
 * through it (vanilla buttons, the frame's tabs, checklist rows) uses the small font without per-call changes. It is
 * restored right after the screen draws, and every client tick as a safety net in case a draw ever stops partway.
 */
final class KanbanFontSwap {

    private static final KanbanFontSwap INSTANCE = new KanbanFontSwap();
    private static FontRenderer vanilla;

    private KanbanFontSwap() {}

    static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    /** The game's normal font, even while a Kanban screen has swapped it out (for tooltips). */
    static FontRenderer vanilla(Minecraft mc) {
        return vanilla != null ? vanilla : mc.fontRenderer;
    }

    @SubscribeEvent
    public void beforeDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        restore();
        if (!(event.gui instanceof GuiKanbanScreen)) return;
        Minecraft mc = Minecraft.getMinecraft();
        vanilla = mc.fontRenderer;
        mc.fontRenderer = KanbanFont.get(mc);
    }

    @SubscribeEvent
    public void afterDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        restore();
    }

    /** Puts the normal font back if it is swapped out; called after every draw and every client tick. */
    static void restore() {
        if (vanilla == null) return;
        Minecraft.getMinecraft().fontRenderer = vanilla;
        vanilla = null;
    }
}
