package com.gtnhkanban.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SSetCardAssigned;

final class GuiCardAssignees extends GuiKanbanScreen {

    private final UUID projectId, cardId;
    private int page;

    GuiCardAssignees(UUID projectId, UUID cardId, GuiScreen parent) {
        super(parent);
        this.projectId = projectId;
        this.cardId = cardId;
        refreshBoardWhileOpen(projectId);
    }

    private int pageSize() {
        return Math.max(1, (height - 95) / 24);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 110, height - 28, 80, 20, "Back"));
        buttonList.add(new GuiButton(2, width / 2 + 10, height - 28, 30, 20, "<"));
        buttonList.add(new GuiButton(3, width / 2 + 50, height - 28, 30, 20, ">"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) goBack();
        else if (button.id == 2) page = Math.max(0, page - 1);
        else if (button.id == 3) {
            BoardSnapshot board = KanbanClientState.getBoard();
            if (board != null) page = Math.min(
                PagedList.pageCount(
                    board.getMembers()
                        .size(),
                    pageSize()) - 1,
                page + 1);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        BoardSnapshot board = KanbanClientState.getBoard();
        CardView card = KanbanClientState.findCard(cardId);
        if (button != 0 || board == null || card == null || x < width / 2 - 150 || x >= width / 2 + 150 || y < 58)
            return;
        List<MemberSummary> visible = PagedList.pageItems(board.getMembers(), page, pageSize());
        int row = (y - 58) / 24;
        if (row >= visible.size()) return;
        UUID memberId = visible.get(row)
            .getPlayerId();
        boolean assigned = !card.getAssigneeIds()
            .contains(memberId);
        KanbanClientState.assignLocally(projectId, cardId, memberId, assigned);
        KanbanNetwork.CHANNEL.sendToServer(new C2SSetCardAssigned(projectId, cardId, memberId, assigned));
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (!handleEscape(key)) super.keyTyped(c, key);
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Assign project members", width / 2, 18, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Click members to assign or unassign them.", width / 2, 38, 0xAAAAAA);
        BoardSnapshot board = KanbanClientState.getBoard();
        CardView card = KanbanClientState.findCard(cardId);
        if (board != null && card != null) {
            page = Math.min(
                page,
                PagedList.pageCount(
                    board.getMembers()
                        .size(),
                    pageSize()) - 1);
            List<MemberSummary> visible = PagedList.pageItems(board.getMembers(), page, pageSize());
            for (int i = 0; i < visible.size(); i++) {
                MemberSummary member = visible.get(i);
                boolean assigned = card.getAssigneeIds()
                    .contains(member.getPlayerId());
                drawRect(width / 2 - 150, 58 + i * 24, width / 2 + 150, 80 + i * 24, 0xAA333333);
                drawString(
                    fontRendererObj,
                    (assigned ? "[x] " : "[ ] ") + member.getDisplayName() + (member.isOwner() ? " (owner)" : ""),
                    width / 2 - 140,
                    65 + i * 24,
                    assigned ? 0x55FF55 : 0xFFFFFF);
            }
        }
        drawCenteredString(fontRendererObj, "Page " + (page + 1), width / 2, height - 42, 0xAAAAAA);
        super.drawScreen(x, y, partialTicks);
    }
}
