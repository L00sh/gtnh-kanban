package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Mouse;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.CardLink;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SSetCardLink;

/** Ticks the other cards on the board that a card depends on, or that are blocking it. */
final class GuiCardLinkPicker extends GuiKanbanScreen {

    private static final int DONE = 1, ROW = 14;
    private final UUID projectId, cardId;
    private final CardLink link;
    private GuiTextField search;
    private int left, panelWidth, scroll;

    GuiCardLinkPicker(UUID projectId, UUID cardId, CardLink link, GuiScreen parent) {
        super(parent);
        this.projectId = projectId;
        this.cardId = cardId;
        this.link = link;
        refreshBoardWhileOpen(projectId);
    }

    @Override
    public void initGui() {
        panelWidth = Math.min(width - 24, 360);
        left = (width - panelWidth) / 2;
        String query = search == null ? "" : search.getText();
        search = new GuiTextField(fontRendererObj, left, 34, panelWidth, 18);
        search.setText(query);
        search.setFocused(true);
        buttonList.clear();
        buttonList.add(new GuiButton(DONE, width / 2 - 40, height - 28, 80, 20, "Done"));
    }

    private int top() {
        return 60;
    }

    private int capacity() {
        return Math.max(1, (height - 36 - top()) / ROW);
    }

    /** Every other card matching the search, by number. */
    private List<CardView> candidates() {
        List<CardView> found = new ArrayList<CardView>();
        BoardSnapshot board = KanbanClientState.getBoard(projectId);
        if (board == null) return found;
        String query = search.getText()
            .trim()
            .toLowerCase(Locale.ROOT);
        for (CardView card : board.getCards()) {
            if (card.getId()
                .equals(cardId)) continue;
            String label = ("#" + card.getNumber() + " " + card.getTitle()).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || label.contains(query)) found.add(card);
        }
        java.util.Collections.sort(found, new java.util.Comparator<CardView>() {

            @Override
            public int compare(CardView a, CardView b) {
                return Integer.compare(a.getNumber(), b.getNumber());
            }
        });
        return found;
    }

    private boolean linked(UUID other) {
        CardView card = KanbanClientState.findCard(cardId);
        return card != null && card.getLinks(link)
            .contains(other);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE) goBack();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scroll = Math
            .max(0, Math.min(Math.max(0, candidates().size() - capacity()), scroll + (wheel > 0 ? -3 : 3)));
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        search.mouseClicked(x, y, button);
        if (button != 0 || x < left || x >= left + panelWidth || y < top()) return;
        int row = (y - top()) / ROW;
        List<CardView> cards = candidates();
        if (row >= capacity() || scroll + row >= cards.size()) return;
        UUID other = cards.get(scroll + row)
            .getId();
        KanbanNetwork.CHANNEL.sendToServer(new C2SSetCardLink(projectId, cardId, other, link, !linked(other)));
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (handleEscape(key)) return;
        if (search.textboxKeyTyped(c, key)) scroll = 0;
        else super.keyTyped(c, key);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        search.updateCursorCounter();
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        drawDefaultBackground();
        CardView card = KanbanClientState.findCard(cardId);
        String heading = link == CardLink.DEPENDS_ON ? "Cards that must be done first" : "Cards blocking this one";
        drawCenteredString(fontRendererObj, heading, width / 2, 8, link == CardLink.DEPENDS_ON ? 0x66AAFF : 0xFF6666);
        if (card != null) drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth("#" + card.getNumber() + " " + card.getTitle(), width - 20),
            width / 2,
            20,
            0xAAAAAA);
        search.drawTextBox();
        if (search.getText()
            .isEmpty() && !search.isFocused())
            drawString(fontRendererObj, "Search by number or name", left + 4, 39, 0x777777);
        drawRect(left - 2, top() - 2, left + panelWidth + 2, top() + capacity() * ROW + 1, 0xAA111111);
        List<CardView> cards = candidates();
        if (cards.isEmpty()) drawString(fontRendererObj, "No other cards match.", left + 3, top() + 3, 0xAAAAAA);
        BoardSnapshot board = KanbanClientState.getBoard(projectId);
        UUID done = board == null ? null
            : board.getSettings()
                .doneColumn();
        for (int row = 0; row < capacity() && scroll + row < cards.size(); row++) {
            CardView other = cards.get(scroll + row);
            int rowY = top() + row * ROW;
            boolean on = linked(other.getId());
            if (x >= left && x < left + panelWidth && y >= rowY && y < rowY + ROW)
                drawRect(left, rowY, left + panelWidth, rowY + ROW, 0x33FFFFFF);
            drawString(fontRendererObj, on ? "§a[x]" : "§7[ ]", left + 3, rowY + 3, 0xFFFFFF);
            String column = columnName(board, other.getColumnId());
            int columnWidth = fontRendererObj.getStringWidth(column);
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(
                    "§7#" + other.getNumber() + " §f" + other.getTitle(),
                    panelWidth - columnWidth - 34),
                left + 22,
                rowY + 3,
                0xFFFFFF);
            drawString(
                fontRendererObj,
                column,
                left + panelWidth - columnWidth - 3,
                rowY + 3,
                other.getColumnId()
                    .equals(done) ? 0x55FF55 : 0x888888);
        }
        String result = KanbanClientState.getResultMessage();
        if (!result.isEmpty() && !KanbanClientState.isResultSuccess()) drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(result, width - 20),
            width / 2,
            height - 40,
            0xFF5555);
        super.drawScreen(x, y, partialTicks);
    }

    private static String columnName(BoardSnapshot board, UUID columnId) {
        if (board != null) for (BoardColumn column : board.getSettings()
            .getColumns())
            if (column.getId()
                .equals(columnId)) return column.getName();
        return "";
    }
}
