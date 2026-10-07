package com.gtnhkanban.client;

import java.util.UUID;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SFetchBoard;
import com.gtnhkanban.network.message.C2SListProjects;

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

    /** Opens the project list: the one this screen was opened from if there is one, otherwise a fresh one. */
    protected final void openProjects() {
        for (GuiScreen screen = this; screen
            != null; screen = screen instanceof GuiKanbanScreen ? ((GuiKanbanScreen) screen).parentScreen : null) {
            if (screen instanceof GuiProjectList) {
                mc.displayGuiScreen(screen);
                KanbanNetwork.CHANNEL.sendToServer(new C2SListProjects());
                return;
            }
        }
        mc.displayGuiScreen(new GuiProjectList());
        KanbanNetwork.CHANNEL.sendToServer(new C2SListProjects());
    }

    protected final void refreshBoardWhileOpen(UUID projectId) {
        refreshProjectId = projectId;
        KanbanClientState.setActiveProject(projectId);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (refreshProjectId == null || mc.thePlayer == null) return;
        // The screen on display decides which project's board the shared state shows.
        KanbanClientState.setActiveProject(refreshProjectId);
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
