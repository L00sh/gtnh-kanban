package com.gtnhkanban.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.ProjectSummary;
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
        GuiScreen list = projectList();
        mc.displayGuiScreen(list == null ? new GuiProjectList() : list);
        KanbanNetwork.CHANNEL.sendToServer(new C2SListProjects());
    }

    /** The project list this screen was (directly or indirectly) opened from, or null. */
    private GuiScreen projectList() {
        GuiScreen screen = this;
        while (screen != null) {
            if (screen instanceof GuiProjectList) return screen;
            screen = screen instanceof GuiKanbanScreen ? ((GuiKanbanScreen) screen).parentScreen : null;
        }
        return null;
    }

    private static final int PROJECT_TAB = 1000;

    /** Adds a closable tab for each open project; {@code selected} is highlighted (null for none). */
    protected final void addProjectTabs(KanbanFrame.Tabs tabs, UUID selected) {
        List<UUID> open = OpenProjects.list();
        for (int i = 0; i < open.size(); i++) {
            UUID project = open.get(i);
            tabs.addProject(
                PROJECT_TAB + i,
                project,
                projectName(project),
                projectIcon(project),
                project.equals(selected));
        }
    }

    /**
     * Handles a click on a project tab: its × closes it (moving to a neighbouring tab, or the project list, if it was
     * the one on screen), anywhere else shows that project's board.
     *
     * @param current the project on screen, or null
     * @return true when the click was on a project tab
     */
    protected final boolean projectTabClicked(KanbanFrame.Tabs tabs, int mouseX, int mouseY, UUID current) {
        return projectTabClicked(tabs, mouseX, mouseY, current, 0);
    }

    /** As above; a right-click ({@code button} 1) on a project tab closes it, even when its × is hidden. */
    protected final boolean projectTabClicked(KanbanFrame.Tabs tabs, int mouseX, int mouseY, UUID current, int button) {
        KanbanFrame.Tab close = tabs.closeAt(mouseX, mouseY);
        if (button == 1) {
            KanbanFrame.Tab tab = tabs.at(mouseX, mouseY);
            close = tab != null && tab.project != null ? tab : null;
            if (close == null) return false;
        } else if (button != 0) return false;
        if (close != null) {
            UUID next = OpenProjects.close(close.project);
            if (close.project.equals(current)) {
                if (next == null) openProjects();
                else showBoard(next);
            }
            return true;
        }
        KanbanFrame.Tab tab = tabs.at(mouseX, mouseY);
        if (tab == null || tab.project == null) return false;
        if (!(this instanceof GuiKanbanBoard) || !tab.project.equals(current)) showBoard(tab.project);
        return true;
    }

    /** Shows a project's board, from its cached copy at once and refreshed from the server. */
    protected final void showBoard(UUID projectId) {
        KanbanNetwork.CHANNEL.sendToServer(new C2SFetchBoard(projectId));
        mc.displayGuiScreen(new GuiKanbanBoard(projectId, projectList()));
    }

    private static String projectName(UUID projectId) {
        BoardSnapshot board = KanbanClientState.getBoard(projectId);
        if (board != null) return board.getProject()
            .getName();
        for (ProjectSummary project : KanbanClientState.getProjects()) if (project.getId()
            .equals(projectId)) return project.getName();
        return "Project";
    }

    private static ItemStack projectIcon(UUID projectId) {
        BoardSnapshot board = KanbanClientState.getBoard(projectId);
        if (board != null) return GuiKanbanBoard.iconStack(
            board.getProject()
                .getIcon());
        for (ProjectSummary project : KanbanClientState.getProjects()) if (project.getId()
            .equals(projectId)) return GuiKanbanBoard.iconStack(project.getIcon());
        return null;
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
