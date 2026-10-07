package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Mouse;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.CardType;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.Priority;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SMoveCard;
import com.gtnhkanban.network.message.C2SSetProjectIcon;

/** The board: one scrollable column per server-wide column, with draggable cards. */
public final class GuiKanbanBoard extends GuiKanbanScreen {

    private static final int BACK = 1, MEMBERS = 2, SETTINGS = 3, ICON = 4, SCROLL_LEFT = 5, SCROLL_RIGHT = 6;
    private static final int TOP = KanbanFrame.contentTop();
    private static final int HEADER = 18;
    private static final int GAP = 4;
    static final int CARD_HEIGHT = 56;
    /** Room around cards: between neighbours, and between a card and its column's sides. */
    private static final int CARD_GAP = 6;
    private static final int CARD_INSET = 6;
    private static final int MIN_COLUMN = 118;
    private static final int PITCH = CARD_HEIGHT + CARD_GAP;
    private static final int SCROLLBAR = 4;
    /** Ticks between automatic scroll steps while dragging a card at a column's edge. */
    private static final int AUTOSCROLL_TICKS = 4;

    private final UUID projectId;
    private final RenderItem render = new RenderItem();
    private final Map<UUID, Integer> columnScroll = new HashMap<UUID, Integer>();
    private int horizontalScroll;
    private KanbanFrame.Tabs tabs = new KanbanFrame.Tabs();
    private UUID pressedCardId;
    private int pressedX, pressedY, dragMouseX, dragMouseY;
    private boolean draggingCard;
    /** Column whose scrollbar thumb is being dragged, and where on the thumb it was grabbed. */
    private UUID scrollDragColumn;
    private int scrollGrab, autoscrollTicks;

    public GuiKanbanBoard(UUID projectId) {
        this(projectId, null);
    }

    public GuiKanbanBoard(UUID projectId, GuiScreen parent) {
        super(parent);
        this.projectId = projectId;
        // Usually already open (the project list checks the limit); a board reached another way still gets a tab.
        OpenProjects.open(projectId);
        refreshBoardWhileOpen(projectId);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int arrowsY = KanbanFrame.contentBottom(height) - 20;
        buttonList.add(new GuiButton(SCROLL_LEFT, KanbanFrame.contentLeft(), arrowsY, 20, 18, "<"));
        buttonList.add(new GuiButton(SCROLL_RIGHT, KanbanFrame.contentRight(width) - 20, arrowsY, 20, 18, ">"));
        rebuildTabs();
    }

    /** Project names and icons live in their tabs, so tabs are rebuilt when boards change. */
    private void rebuildTabs() {
        BoardSnapshot board = board();
        ItemStack icon = board == null ? null
            : iconStack(
                board.getProject()
                    .getIcon());
        tabs = new KanbanFrame.Tabs().add(BACK, "Projects", null, false, KanbanFrame.Color.PURPLE);
        addProjectTabs(tabs, projectId);
        tabs.add(MEMBERS, "Members", null, false)
            .addRight(SETTINGS, "Board settings", null, true)
            .addRight(ICON, "Change project icon", icon, false);
        tabs.layout(fontRendererObj, width);
    }

    private void tabClicked(int id) {
        if (id == BACK) openProjects();
        else if (id == MEMBERS && projectId != null) mc.displayGuiScreen(new GuiProjectMembers(projectId, this));
        else if (id == SETTINGS) mc.displayGuiScreen(new GuiBoardSettings(this));
        else if (id == ICON && projectId != null) {
            mc.displayGuiScreen(new GuiItemPicker(this, "Choose a project icon", new GuiItemPicker.IconChoice() {

                @Override
                public void chosen(ItemKey icon) {
                    KanbanNetwork.CHANNEL.sendToServer(new C2SSetProjectIcon(projectId, icon));
                }
            }));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == SCROLL_LEFT) horizontalScroll = Math.max(0, horizontalScroll - (MIN_COLUMN + GAP));
        else if (button.id == SCROLL_RIGHT)
            horizontalScroll = Math.min(maxHorizontalScroll(), horizontalScroll + (MIN_COLUMN + GAP));
    }

    // ---- layout ----

    private List<BoardColumn> columns() {
        BoardSnapshot board = board();
        return board == null ? Collections.<BoardColumn>emptyList()
            : board.getSettings()
                .getColumns();
    }

    private int[][] layout() {
        return BoardLayout.columns(KanbanFrame.contentLeft(), areaWidth(), columns().size(), GAP, MIN_COLUMN);
    }

    private int columnWidth(int index) {
        return layout()[1][index];
    }

    private int boardWidth() {
        return BoardLayout.totalWidth(layout(), GAP);
    }

    private int maxHorizontalScroll() {
        return Math.max(0, boardWidth() - areaWidth());
    }

    /** Columns sit inside the window panel. */
    private int areaWidth() {
        return KanbanFrame.contentRight(width) - KanbanFrame.contentLeft();
    }

    private int columnLeft(int index) {
        return layout()[0][index] - horizontalScroll;
    }

    /** Only the part of the board inside the panel is visible, so only it takes clicks and drops. */
    private boolean inBoardArea(int mouseX, int mouseY) {
        return mouseX >= KanbanFrame.contentLeft() && mouseX < KanbanFrame.contentRight(width)
            && mouseY >= TOP
            && mouseY < bottom();
    }

    private int bottom() {
        return KanbanFrame.contentBottom(height) - 24;
    }

    private int cardsTop() {
        return TOP + HEADER + CARD_GAP;
    }

    private int visibleCardSlots() {
        return Math.max(1, (bottom() - cardsTop()) / PITCH);
    }

    /** Bottom of the last card slot, where a column's scrollbar track ends. */
    private int slotsBottom() {
        return cardsTop() + visibleCardSlots() * PITCH - CARD_GAP;
    }

    private boolean hasScrollbar(int cardCount) {
        return cardCount > visibleCardSlots();
    }

    private int scrollbarLeft(int column) {
        return columnLeft(column) + columnWidth(column) - 2 - SCROLLBAR;
    }

    private int cardLeft(int column) {
        return columnLeft(column) + CARD_INSET;
    }

    /** Cards narrow to make room for the scrollbar when the column has one. */
    private int cardWidth(int column, int cardCount) {
        int right = hasScrollbar(cardCount) ? scrollbarLeft(column) - 3
            : columnLeft(column) + columnWidth(column) - CARD_INSET;
        return right - cardLeft(column);
    }

    private int columnAt(int mouseX) {
        List<BoardColumn> columns = columns();
        for (int index = 0; index < columns.size(); index++) {
            int left = columnLeft(index);
            if (mouseX >= left && mouseX < left + columnWidth(index)) return index;
        }
        return -1;
    }

    private void setScroll(UUID columnId, int value) {
        columnScroll.put(columnId, value);
        columnScroll.put(columnId, scrollOf(columnId, cardsIn(columnId).size()));
    }

    private int scrollOf(UUID columnId, int cardCount) {
        Integer scroll = columnScroll.get(columnId);
        int value = scroll == null ? 0 : scroll.intValue();
        return Math.max(0, Math.min(value, Math.max(0, cardCount - visibleCardSlots())));
    }

    // ---- input ----

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int column = columnAt(mouseX);
        if (isShiftKeyDown() || mouseY < cardsTop() || column < 0) {
            horizontalScroll = Math
                .max(0, Math.min(maxHorizontalScroll(), horizontalScroll - Integer.signum(wheel) * 40));
            return;
        }
        UUID columnId = columns().get(column)
            .getId();
        setScroll(columnId, scrollOf(columnId, cardsIn(columnId).size()) + (wheel > 0 ? -1 : 1));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (projectTabClicked(tabs, mouseX, mouseY, projectId, mouseButton)) return;
        KanbanFrame.Tab tab = mouseButton == 0 ? tabs.at(mouseX, mouseY) : null;
        if (tab != null) {
            tabClicked(tab.id);
            return;
        }
        if (mouseButton != 0 || projectId == null || !inBoardArea(mouseX, mouseY)) return;
        int column = columnAt(mouseX);
        if (column < 0) return;
        BoardColumn target = columns().get(column);
        int left = columnLeft(column);
        if (mouseY < TOP + HEADER) {
            if (mouseX >= left + columnWidth(column) - 16) {
                mc.displayGuiScreen(new GuiCardDetail(projectId, null, this, target.getId()));
            }
            return;
        }
        if (clickScrollbar(column, target.getId(), mouseX, mouseY)) return;
        CardView card = cardAt(column, mouseY, mouseX);
        if (card != null) {
            pressedCardId = card.getId();
            pressedX = mouseX;
            pressedY = mouseY;
        }
    }

    /** Starts dragging the thumb, or pages up or down when the track is clicked elsewhere. */
    private boolean clickScrollbar(int column, UUID columnId, int mouseX, int mouseY) {
        int count = cardsIn(columnId).size();
        int barLeft = scrollbarLeft(column);
        if (!hasScrollbar(count) || mouseX < barLeft - 1
            || mouseX >= barLeft + SCROLLBAR + 1
            || mouseY < cardsTop()
            || mouseY >= slotsBottom()) return false;
        int scroll = scrollOf(columnId, count);
        int[] thumb = BoardLayout.thumb(cardsTop(), slotsBottom() - cardsTop(), visibleCardSlots(), count, scroll);
        if (mouseY >= thumb[0] && mouseY < thumb[0] + thumb[1]) {
            scrollDragColumn = columnId;
            scrollGrab = mouseY - thumb[0];
        } else setScroll(columnId, scroll + (mouseY < thumb[0] ? -visibleCardSlots() : visibleCardSlots()));
        return true;
    }

    private CardView cardAt(int column, int mouseY, int mouseX) {
        UUID columnId = columns().get(column)
            .getId();
        List<CardView> cards = cardsIn(columnId);
        int slot = (mouseY - cardsTop()) / PITCH;
        int within = (mouseY - cardsTop()) % PITCH;
        int index = scrollOf(columnId, cards.size()) + slot;
        if (mouseY < cardsTop() || within >= CARD_HEIGHT || slot >= visibleCardSlots() || index >= cards.size())
            return null;
        int left = cardLeft(column);
        if (mouseX < left || mouseX >= left + cardWidth(column, cards.size())) return null;
        return cards.get(index);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        super.mouseMovedOrUp(mouseX, mouseY, button);
        if (button == 0) scrollDragColumn = null;
        if (button != 0 || pressedCardId == null) return;
        UUID cardId = pressedCardId;
        pressedCardId = null;
        boolean dragged = draggingCard;
        draggingCard = false;
        if (!dragged) {
            mc.displayGuiScreen(new GuiCardDetail(projectId, cardId, this));
            return;
        }
        int column = columnAt(mouseX);
        if (column < 0 || !inBoardArea(mouseX, mouseY)) return;
        drop(cardId, column, mouseY);
    }

    /**
     * Drops a card where the insertion line shows: above the card at the drop index, or at the bottom of the column.
     * Dropping a card onto its own place changes nothing.
     */
    private void drop(UUID cardId, int column, int mouseY) {
        BoardColumn target = columns().get(column);
        CardView card = KanbanClientState.findCard(cardId);
        if (card == null) return;
        CardView before = dropTarget(card, target.getId(), mouseY);
        boolean sameColumn = card.getColumnId()
            .equals(target.getId());
        if (sameColumn && same(before, cardAfter(card, cardsIn(target.getId())))) return;
        UUID beforeId = before == null ? null : before.getId();
        KanbanClientState.moveCardLocally(projectId, cardId, target.getId(), beforeId);
        KanbanClientState.setResult(
            true,
            "",
            sameColumn ? "Moved #" + card.getNumber() + "."
                : "Moved #" + card.getNumber() + " to " + target.getName() + ".");
        KanbanNetwork.CHANNEL.sendToServer(new C2SMoveCard(projectId, cardId, target.getId(), beforeId));
    }

    /** The card the dragged card would land above, or null for the bottom of the column. */
    private CardView dropTarget(CardView dragged, UUID columnId, int mouseY) {
        List<CardView> shown = cardsIn(columnId);
        int index = BoardLayout.dropIndex(mouseY, cardsTop(), PITCH, scrollOf(columnId, shown.size()), shown.size());
        CardView before = index < shown.size() ? shown.get(index) : null;
        // Landing just above itself is the same place as just above the card after it.
        return same(before, dragged) ? cardAfter(dragged, shown) : before;
    }

    private static CardView cardAfter(CardView card, List<CardView> shown) {
        for (int i = 0; i < shown.size() - 1; i++) if (same(shown.get(i), card)) return shown.get(i + 1);
        return null;
    }

    private static boolean same(CardView first, CardView second) {
        return first == null ? second == null
            : second != null && first.getId()
                .equals(second.getId());
    }

    /** While a card is dragged near a column's top or bottom edge, that column scrolls. */
    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!draggingCard || ++autoscrollTicks < AUTOSCROLL_TICKS) return;
        autoscrollTicks = 0;
        int column = columnAt(dragMouseX);
        if (column < 0) return;
        UUID columnId = columns().get(column)
            .getId();
        int scroll = scrollOf(columnId, cardsIn(columnId).size());
        if (dragMouseY < cardsTop() + 12) setScroll(columnId, scroll - 1);
        else if (dragMouseY > slotsBottom() - 12 && dragMouseY < bottom()) setScroll(columnId, scroll + 1);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (clickedMouseButton == 0 && scrollDragColumn != null) {
            int count = cardsIn(scrollDragColumn).size();
            int[] thumb = BoardLayout.thumb(
                cardsTop(),
                slotsBottom() - cardsTop(),
                visibleCardSlots(),
                count,
                scrollOf(scrollDragColumn, count));
            setScroll(
                scrollDragColumn,
                BoardLayout.scrollForThumb(
                    mouseY - scrollGrab,
                    cardsTop(),
                    slotsBottom() - cardsTop(),
                    thumb[1],
                    Math.max(0, count - visibleCardSlots())));
            return;
        }
        if (clickedMouseButton != 0 || pressedCardId == null) return;
        dragMouseX = mouseX;
        dragMouseY = mouseY;
        if (Math.abs(mouseX - pressedX) > 5 || Math.abs(mouseY - pressedY) > 5) draggingCard = true;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (!handleEscape(keyCode)) super.keyTyped(typedChar, keyCode);
    }

    // ---- drawing ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        rebuildTabs();
        KanbanFrame.drawWindow(mc, width, height);
        tabs.draw(mc, mouseX, mouseY);
        BoardSnapshot board = board();
        boolean overflow = maxHorizontalScroll() > 0;
        for (GuiButton button : buttonList) {
            if (button.id == SCROLL_LEFT || button.id == SCROLL_RIGHT) button.visible = overflow;
        }
        horizontalScroll = Math.min(horizontalScroll, maxHorizontalScroll());
        CardView hovered = null;
        List<BoardColumn> columns = columns();
        KanbanFrame.clip(mc, KanbanFrame.contentLeft(), TOP, areaWidth(), bottom() - TOP);
        for (int index = 0; index < columns.size(); index++) {
            CardView hit = drawColumn(index, columns.get(index), mouseX, mouseY);
            if (hit != null && mouseX >= KanbanFrame.contentLeft() && mouseX < KanbanFrame.contentRight(width))
                hovered = hit;
        }
        KanbanFrame.endClip();
        if (draggingCard) drawDragged(columns);
        String result = KanbanClientState.getResultMessage();
        int statusY = KanbanFrame.contentBottom(height) - 15;
        if (!result.isEmpty()) drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(result, areaWidth() - 50),
            width / 2,
            statusY,
            KanbanClientState.isResultSuccess() ? 0x55FF55 : 0xFF5555);
        else if (overflow) drawCenteredString(
            fontRendererObj,
            "Scroll sideways: shift + mouse wheel, or the arrows",
            width / 2,
            statusY,
            0x888888);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (hovered != null && !draggingCard) drawHoveringText(cardTooltip(hovered), mouseX, mouseY, fontRendererObj);
        String tabTip = tabs.tooltip(mouseX, mouseY);
        if (tabTip != null) drawHoveringText(Collections.singletonList(tabTip), mouseX, mouseY, fontRendererObj);
    }

    /** @return the card under the mouse, if any */
    private CardView drawColumn(int index, BoardColumn column, int mouseX, int mouseY) {
        int left = columnLeft(index);
        int width = columnWidth(index);
        if (left + width < 0 || left > this.width) return null;
        List<CardView> cards = cardsIn(column.getId());
        boolean dropTarget = draggingCard && columnAt(dragMouseX) == index;
        drawRect(left, TOP, left + width, bottom(), dropTarget ? 0x5533CC66 : 0x66000000);
        drawRect(left, TOP, left + width, TOP + HEADER, 0xCC1E2A38);
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(column.getName(), width - 46) + " §7(" + cards.size() + ")",
            left + 4,
            TOP + 5,
            0xFFFFFF);
        boolean plusHover = mouseX >= left + width - 16 && mouseX < left + width
            && mouseY >= TOP
            && mouseY < TOP + HEADER;
        drawRect(left + width - 16, TOP + 2, left + width - 2, TOP + 16, plusHover ? 0xFF557755 : 0xFF334433);
        drawCenteredString(fontRendererObj, "+", left + width - 9, TOP + 5, 0xFFFFFF);

        int scroll = scrollOf(column.getId(), cards.size());
        int cardWidth = cardWidth(index, cards.size());
        CardView hovered = null;
        for (int slot = 0; slot < visibleCardSlots() && scroll + slot < cards.size(); slot++) {
            CardView card = cards.get(scroll + slot);
            int y = cardsTop() + slot * PITCH;
            boolean dragged = draggingCard && card.getId()
                .equals(pressedCardId);
            drawCard(card, cardLeft(index), y, cardWidth, dragged);
            if (mouseX >= cardLeft(index) && mouseX < cardLeft(index) + cardWidth
                && mouseY >= y
                && mouseY < y + CARD_HEIGHT) hovered = card;
        }
        if (hasScrollbar(cards.size())) drawScrollbar(index, column.getId(), cards.size(), scroll, mouseX, mouseY);
        if (dropTarget) drawInsertionLine(index, column.getId(), cards, scroll, cardLeft(index), cardWidth);
        return hovered;
    }

    private void drawScrollbar(int column, UUID columnId, int count, int scroll, int mouseX, int mouseY) {
        int x = scrollbarLeft(column);
        int trackTop = cardsTop(), trackHeight = slotsBottom() - cardsTop();
        int[] thumb = BoardLayout.thumb(trackTop, trackHeight, visibleCardSlots(), count, scroll);
        boolean active = columnId.equals(scrollDragColumn)
            || mouseX >= x - 1 && mouseX < x + SCROLLBAR + 1 && mouseY >= thumb[0] && mouseY < thumb[0] + thumb[1];
        drawRect(x, trackTop, x + SCROLLBAR, trackTop + trackHeight, 0xFF161616);
        // Thumb colors follow the frame's bevel greys.
        drawRect(x, thumb[0], x + SCROLLBAR, thumb[0] + thumb[1], active ? 0xFF8A8A8A : 0xFF6A6A6A);
        drawRect(x + SCROLLBAR - 1, thumb[0], x + SCROLLBAR, thumb[0] + thumb[1], 0xFF444444);
    }

    /** A bright line where the dragged card would land. */
    private void drawInsertionLine(int column, UUID columnId, List<CardView> cards, int scroll, int x, int width) {
        CardView dragged = KanbanClientState.findCard(pressedCardId);
        if (dragged == null) return;
        int index = BoardLayout.dropIndex(dragMouseY, cardsTop(), PITCH, scroll, cards.size());
        int slot = index - scroll;
        if (slot < 0 || slot > visibleCardSlots()) return;
        // Centred in the gap above the slot.
        int y = Math.min(cardsTop() + slot * PITCH - CARD_GAP / 2 - 1, slotsBottom() + CARD_GAP / 2 - 1);
        drawRect(x, y, x + width, y + 2, 0xFF47D6C8);
    }

    private void drawCard(CardView card, int x, int y, int width, boolean faded) {
        CardType type = typeOf(card);
        int typeColor = type == null ? 0x555555 : type.getColor();
        KanbanFrame.card(mc, x, y, width, CARD_HEIGHT, faded ? 0.4f : 1f);
        // The type's color runs down the inside of the card's left bevel.
        int innerLeft = x + KanbanFrame.CARD_LEFT, innerTop = y + KanbanFrame.CARD_TOP;
        drawRect(innerLeft, innerTop, innerLeft + 2, y + CARD_HEIGHT - KanbanFrame.CARD_BOTTOM, 0xFF000000 | typeColor);
        int left = innerLeft + 5;
        int right = x + width - KanbanFrame.CARD_RIGHT - 2;
        int textX = left;
        ItemStack icon = iconStack(card.getIcon());
        if (icon != null) {
            drawItem(icon, left - 1, innerTop + 1);
            textX = left + 18;
        }
        String number = "§7#" + card.getNumber() + " §f";
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(number + card.getTitle(), right - textX),
            textX,
            innerTop + 3,
            0xFFFFFF);

        int lineY = innerTop + 16;
        if (type != null) {
            int badge = Math.min(fontRendererObj.getStringWidth(type.getName()) + 6, width - 50);
            drawRect(textX, lineY - 1, textX + badge, lineY + 9, 0xFF000000 | darken(typeColor));
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(type.getName(), badge - 6),
                textX + 3,
                lineY,
                0xFFFFFF);
        }
        String priority = priorityLabel(card.getPriority());
        if (!priority.isEmpty()) drawString(
            fontRendererObj,
            priority,
            right - fontRendererObj.getStringWidth(priority),
            lineY,
            priorityColor(card.getPriority()));

        String meta = TimeText.ago(card.getCreatedAt(), System.currentTimeMillis());
        if (!card.getCreatorName()
            .isEmpty()) meta += (meta.isEmpty() ? "by " : " by ") + card.getCreatorName();
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(meta, right - left),
            left,
            innerTop + 28,
            0x999999);

        int total = card.progressTotal();
        if (total > 0) {
            int done = card.progressDone();
            String count = done + "/" + total;
            int barRight = right - fontRendererObj.getStringWidth(count) - 4;
            int barLeft = left;
            int barY = innerTop + 41;
            drawRect(barLeft, barY, barRight, barY + 4, 0xFF444444);
            drawRect(
                barLeft,
                barY,
                barLeft + (barRight - barLeft) * done / total,
                barY + 4,
                done == total ? 0xFF55CC55 : 0xFF4A90D9);
            drawString(fontRendererObj, count, barRight + 4, barY - 2, 0xAAAAAA);
        }
    }

    private void drawDragged(List<BoardColumn> columns) {
        CardView card = KanbanClientState.findCard(pressedCardId);
        if (card == null) return;
        drawCard(card, dragMouseX + 6, dragMouseY + 6, MIN_COLUMN - 2 * CARD_INSET, false);
    }

    private List<String> cardTooltip(CardView card) {
        List<String> lines = new ArrayList<String>();
        lines.add("#" + card.getNumber() + " " + card.getTitle());
        CardType type = typeOf(card);
        lines.add("§7Type: §f" + (type == null ? "None" : type.getName()));
        lines.add(
            "§7Priority: §f" + card.getPriority()
                .getLabel());
        lines.add(
            "§7Created: §f" + TimeText.full(card.getCreatedAt())
                + (card.getCreatorName()
                    .isEmpty() ? "" : " by " + card.getCreatorName()));
        if (!card.getAssigneeIds()
            .isEmpty()) lines.add("§7Assigned: §f" + KanbanClientState.assigneeNames(card));
        if (card.progressTotal() > 0)
            lines.add("§7Checklist: §f" + card.progressDone() + " / " + card.progressTotal() + " done");
        if (!card.getComments()
            .isEmpty())
            lines.add(
                "§7Comments: §f" + card.getComments()
                    .size());
        lines.add("§8Click to open, drag to move or reorder");
        return lines;
    }

    // ---- data ----

    private BoardSnapshot board() {
        BoardSnapshot board = KanbanClientState.getBoard();
        return board != null && projectId != null
            && projectId.equals(
                board.getProject()
                    .getId()) ? board : null;
    }

    /** Cards in a column, in board order (the order members arrange them in by dragging). */
    private List<CardView> cardsIn(UUID columnId) {
        List<CardView> cards = new ArrayList<CardView>();
        BoardSnapshot board = board();
        if (board == null) return cards;
        for (CardView card : board.getCards()) if (card.getColumnId()
            .equals(columnId)) cards.add(card);
        return cards;
    }

    private CardType typeOf(CardView card) {
        BoardSnapshot board = board();
        if (board == null || card.getTypeId() == null) return null;
        for (CardType type : board.getSettings()
            .getTypes())
            if (type.getId()
                .equals(card.getTypeId())) return type;
        return null;
    }

    private void drawItem(ItemStack stack, int x, int y) {
        render.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
    }

    static ItemStack iconStack(ItemKey icon) {
        return icon == null ? null : MaterialDisplay.stack(icon);
    }

    static String priorityLabel(Priority priority) {
        switch (priority) {
            case HIGH:
                return "!!! High";
            case MEDIUM:
                return "!! Medium";
            case LOW:
                return "! Low";
            default:
                return "";
        }
    }

    static int priorityColor(Priority priority) {
        switch (priority) {
            case HIGH:
                return 0xFF5555;
            case MEDIUM:
                return 0xFFAA33;
            default:
                return 0x66AAFF;
        }
    }

    private static int darken(int color) {
        int r = (color >> 16 & 0xFF) * 3 / 5, g = (color >> 8 & 0xFF) * 3 / 5, b = (color & 0xFF) * 3 / 5;
        return r << 16 | g << 8 | b;
    }
}
