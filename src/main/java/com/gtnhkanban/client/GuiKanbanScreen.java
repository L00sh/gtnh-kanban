package com.gtnhkanban.client;

import java.util.UUID;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SFetchBoard;

abstract class GuiKanbanScreen extends GuiScreen {

    private final GuiScreen parentScreen;
    private UUID refreshProjectId;
    private int refreshTicks;

    GuiKanbanScreen(GuiScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    @Override
    public boolean doesGuiPauseGame() {
        // Packet mutations run on server ticks, including in an integrated server.
        return false;
    }

    protected final void goBack() {
        mc.displayGuiScreen(parentScreen);
    }

    protected final void refreshBoardWhileOpen(UUID projectId) {
        refreshProjectId = projectId;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (refreshProjectId == null || mc.thePlayer == null) return;
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            KanbanNetwork.CHANNEL.sendToServer(new C2SFetchBoard(refreshProjectId));
        }
    }

    protected final boolean handleEscape(int keyCode) {
        if (keyCode != Keyboard.KEY_ESCAPE) return false;
        goBack();
        return true;
    }
}
