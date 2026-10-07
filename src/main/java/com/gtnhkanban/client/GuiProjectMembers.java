package com.gtnhkanban.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SAddMember;
import com.gtnhkanban.network.message.C2SRemoveMember;

public final class GuiProjectMembers extends GuiKanbanScreen {

    private static final int PAGE_SIZE = 7;
    private static final int ADD = 2;
    private static final int PREVIOUS = 3;
    private static final int NEXT = 4;

    private final UUID projectId;
    private static final int TAB_PROJECTS = 10, TAB_BOARD = 11, TAB_MEMBERS = 12, TAB_SETTINGS = 13;

    private GuiTextField usernameField;
    private int page;
    private KanbanFrame.Tabs tabs = new KanbanFrame.Tabs();

    public GuiProjectMembers(UUID projectId) {
        this(projectId, null);
    }

    public GuiProjectMembers(UUID projectId, GuiScreen parent) {
        super(parent);
        this.projectId = projectId;
        refreshBoardWhileOpen(projectId);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        GuiButton add = new GuiButton(ADD, width / 2 - 55, height - 40, 70, 20, "Add member");
        BoardSnapshot board = KanbanClientState.getBoard();
        add.enabled = board != null && board.getProject()
            .isActorIsOwner();
        buttonList.add(add);
        buttonList.add(new GuiButton(PREVIOUS, width / 2 + 20, height - 40, 30, 20, "<"));
        buttonList.add(new GuiButton(NEXT, width / 2 + 55, height - 40, 30, 20, ">"));
        usernameField = new GuiTextField(fontRendererObj, width / 2 - 100, height - 66, 200, 20);
        rebuildTabs();
        usernameField.setMaxStringLength(16);
    }

    private void rebuildTabs() {
        BoardSnapshot board = KanbanClientState.getBoard();
        boolean ours = board != null && board.getProject()
            .getId()
            .equals(projectId);
        String name = ours ? board.getProject()
            .getName() : "Board";
        tabs = new KanbanFrame.Tabs().add(TAB_PROJECTS, "Projects", null, false, KanbanFrame.Color.PURPLE)
            .add(
                TAB_BOARD,
                fontRendererObj.trimStringToWidth(name, 140),
                ours ? GuiKanbanBoard.iconStack(
                    board.getProject()
                        .getIcon())
                    : null,
                false)
            .add(TAB_MEMBERS, "Members", null, true)
            .addRight(TAB_SETTINGS, "Board settings", null, true);
        tabs.layout(fontRendererObj, width);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ADD && button.enabled) {
            KanbanNetwork.CHANNEL.sendToServer(new C2SAddMember(projectId, usernameField.getText()));
        } else if (button.id == PREVIOUS) {
            page = Math.max(0, page - 1);
        } else if (button.id == NEXT) {
            page = Math.min(lastPage(), page + 1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        KanbanFrame.Tab tab = mouseButton == 0 ? tabs.at(mouseX, mouseY) : null;
        if (tab != null) {
            if (tab.id == TAB_PROJECTS) openProjects();
            else if (tab.id == TAB_BOARD) goBack();
            else if (tab.id == TAB_SETTINGS) mc.displayGuiScreen(new GuiBoardSettings(this));
            return;
        }
        usernameField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || mouseX < width / 2 - 100 || mouseX > width / 2 + 100) return;
        BoardSnapshot board = KanbanClientState.getBoard();
        if (board == null || !board.getProject()
            .isActorIsOwner()) return;
        List<MemberSummary> visible = PagedList.pageItems(board.getMembers(), page, PAGE_SIZE);
        int row = (mouseY - 48) / 22;
        if (mouseY >= 48 && row >= 0
            && row < visible.size()
            && !visible.get(row)
                .isOwner()) {
            KanbanNetwork.CHANNEL.sendToServer(
                new C2SRemoveMember(
                    projectId,
                    visible.get(row)
                        .getPlayerId()));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (handleEscape(keyCode)) return;
        if (!usernameField.textboxKeyTyped(typedChar, keyCode)) super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        rebuildTabs();
        KanbanFrame.drawWindow(mc, width, height);
        tabs.draw(mc, mouseX, mouseY);
        BoardSnapshot board = KanbanClientState.getBoard();
        if (board != null) {
            List<MemberSummary> visible = PagedList.pageItems(board.getMembers(), page, PAGE_SIZE);
            for (int index = 0; index < visible.size(); index++) {
                MemberSummary member = visible.get(index);
                String label = (member.isOwner() ? "Owner: " : "Member: ") + member.getDisplayName();
                drawString(fontRendererObj, label, width / 2 - 100, 50 + index * 22, 0xFFFFFF);
            }
            if (board.getProject()
                .isActorIsOwner()) {
                drawString(
                    fontRendererObj,
                    "Username (known server players only)",
                    width / 2 - 100,
                    height - 80,
                    0xAAAAAA);
                drawString(
                    fontRendererObj,
                    "Click a member row to remove them",
                    width / 2 - 100,
                    height - 96,
                    0x888888);
            }
        }
        String result = KanbanClientState.getResultMessage();
        if (!result.isEmpty()) drawCenteredString(
            fontRendererObj,
            result,
            width / 2,
            KanbanFrame.contentTop() + 2,
            KanbanClientState.isResultSuccess() ? 0x55FF55 : 0xFF5555);
        if (board != null && board.getProject()
            .isActorIsOwner()) usernameField.drawTextBox();
        drawCenteredString(
            fontRendererObj,
            "Page " + (page + 1) + " / " + (lastPage() + 1),
            width / 2,
            height - 34,
            0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        String tabTip = tabs.tooltip(mouseX, mouseY);
        if (tabTip != null)
            drawHoveringText(java.util.Collections.singletonList(tabTip), mouseX, mouseY, fontRendererObj);
    }

    private int lastPage() {
        BoardSnapshot board = KanbanClientState.getBoard();
        return PagedList.pageCount(
            board == null ? 0
                : board.getMembers()
                    .size(),
            PAGE_SIZE) - 1;
    }
}
