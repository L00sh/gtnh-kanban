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
    /** Room around cards: between neighbours, and between a card and its column's sides. */
    private static final int CARD_GAP = 6;
    private static final int CARD_INSET = 6;
    private static final int MIN_COLUMN = 118;
    private static final int SCROLLBAR = 4;
    /** Pixels a column scrolls per mouse wheel notch, and per tick while a card is dragged at its edge. */
    private static final int WHEEL_STEP = 24, AUTOSCROLL_STEP = 12;
    private static final int LINE_HEIGHT = 10;

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
    private int scrollGrab;

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

    /** Height of the scrolling card area under a column's header. */
    private int viewHeight() {
        return bottom() - cardsTop();
    }

    private int scrollbarLeft(int column) {
        return columnLeft(column) + columnWidth(column) - 2 - SCROLLBAR;
    }

    private int cardLeft(int column) {
        return columnLeft(column) + CARD_INSET;
    }

    /** A column's cards, sized to their wrapped titles and stacked from the top of the card area. */
    private static final class CardStack {

        final List<CardView> cards;
        final int width;
        final int[] tops, heights;
        final int height;

        CardStack(List<CardView> cards, int width, int[] heights) {
            this.cards = cards;
            this.width = width;
            this.heights = heights;
            this.tops = BoardLayout.stack(heights, CARD_GAP);
            this.height = BoardLayout.stackHeight(tops, heights);
        }
    }

    private CardStack stack(int column) {
        List<CardView> cards = cardsIn(
            columns().get(column)
                .getId());
        CardStack full = stack(cards, columnLeft(column) + columnWidth(column) - CARD_INSET - cardLeft(column));
        // Cards narrow to make room for the scrollbar when the column has one; narrower only makes them taller.
        return hasScrollbar(full) ? stack(cards, scrollbarLeft(column) - 3 - cardLeft(column)) : full;
    }

    private CardStack stack(List<CardView> cards, int width) {
        int[] heights = new int[cards.size()];
        for (int i = 0; i < heights.length; i++) heights[i] = cardHeight(cards.get(i), width);
        return new CardStack(cards, width, heights);
    }

    private boolean hasScrollbar(CardStack stack) {
        return stack.height > viewHeight();
    }

    private int columnAt(int mouseX) {
        List<BoardColumn> columns = columns();
        for (int index = 0; index < columns.size(); index++) {
            int left = columnLeft(index);
            if (mouseX >= left && mouseX < left + columnWidth(index)) return index;
        }
        return -1;
    }

    private int columnIndex(UUID columnId) {
        List<BoardColumn> columns = columns();
        for (int index = 0; index < columns.size(); index++) if (columns.get(index)
            .getId()
            .equals(columnId)) return index;
        return -1;
    }

    private void setScroll(int column, int value) {
        UUID columnId = columns().get(column)
            .getId();
        columnScroll.put(columnId, value);
        columnScroll.put(columnId, scrollOf(columnId, stack(column)));
    }

    /** A column's scroll in pixels, kept within its cards. */
    private int scrollOf(UUID columnId, CardStack stack) {
        Integer scroll = columnScroll.get(columnId);
        int value = scroll == null ? 0 : scroll.intValue();
        return Math.max(0, Math.min(value, maxScroll(stack)));
    }

    private int maxScroll(CardStack stack) {
        return Math.max(0, stack.height - viewHeight());
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
        setScroll(column, scrollOf(columnId, stack(column)) + (wheel > 0 ? -WHEEL_STEP : WHEEL_STEP));
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
        CardStack stack = stack(column);
        int barLeft = scrollbarLeft(column);
        if (!hasScrollbar(stack) || mouseX < barLeft - 1
            || mouseX >= barLeft + SCROLLBAR + 1
            || mouseY < cardsTop()
            || mouseY >= bottom()) return false;
        int scroll = scrollOf(columnId, stack);
        int[] thumb = BoardLayout.thumb(cardsTop(), viewHeight(), viewHeight(), stack.height, scroll);
        if (mouseY >= thumb[0] && mouseY < thumb[0] + thumb[1]) {
            scrollDragColumn = columnId;
            scrollGrab = mouseY - thumb[0];
        } else setScroll(column, scroll + (mouseY < thumb[0] ? -viewHeight() : viewHeight()));
        return true;
    }

    private CardView cardAt(int column, int mouseY, int mouseX) {
        if (mouseY < cardsTop() || mouseY >= bottom()) return null;
        int left = cardLeft(column);
        CardStack stack = stack(column);
        if (mouseX < left || mouseX >= left + stack.width) return null;
        UUID columnId = columns().get(column)
            .getId();
        int index = BoardLayout.cardAt(mouseY - cardsTop() + scrollOf(columnId, stack), stack.tops, stack.heights);
        return index < 0 ? null : stack.cards.get(index);
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
        CardStack stack = stack(columnIndex(columnId));
        List<CardView> shown = stack.cards;
        int index = BoardLayout.dropIndex(mouseY - cardsTop() + scrollOf(columnId, stack), stack.tops, stack.heights);
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
        if (!draggingCard) return;
        int column = columnAt(dragMouseX);
        if (column < 0) return;
        UUID columnId = columns().get(column)
            .getId();
        int scroll = scrollOf(columnId, stack(column));
        if (dragMouseY < cardsTop() + 12) setScroll(column, scroll - AUTOSCROLL_STEP);
        else if (dragMouseY > bottom() - 12 && dragMouseY < bottom()) setScroll(column, scroll + AUTOSCROLL_STEP);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        int dragColumn = scrollDragColumn == null ? -1 : columnIndex(scrollDragColumn);
        if (clickedMouseButton == 0 && dragColumn >= 0) {
            CardStack stack = stack(dragColumn);
            int[] thumb = BoardLayout
                .thumb(cardsTop(), viewHeight(), viewHeight(), stack.height, scrollOf(scrollDragColumn, stack));
            setScroll(
                dragColumn,
                BoardLayout.scrollForThumb(mouseY - scrollGrab, cardsTop(), viewHeight(), thumb[1], maxScroll(stack)));
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

        CardStack stack = stack(index);
        int scroll = scrollOf(column.getId(), stack);
        int cardLeft = cardLeft(index);
        CardView hovered = null;
        // Cards scroll under the header, so they are clipped to the card area (and to the board, sideways).
        int clipLeft = Math.max(left, KanbanFrame.contentLeft());
        int clipRight = Math.min(left + width, KanbanFrame.contentRight(this.width));
        KanbanFrame.clip(mc, clipLeft, cardsTop(), clipRight - clipLeft, viewHeight());
        for (int i = 0; i < stack.cards.size(); i++) {
            int y = cardsTop() - scroll + stack.tops[i];
            if (y + stack.heights[i] <= cardsTop() || y >= bottom()) continue;
            CardView card = stack.cards.get(i);
            boolean dragged = draggingCard && card.getId()
                .equals(pressedCardId);
            drawCard(card, cardLeft, y, stack.width, dragged);
            if (mouseX >= cardLeft && mouseX < cardLeft + stack.width
                && mouseY >= Math.max(y, cardsTop())
                && mouseY < Math.min(y + stack.heights[i], bottom())) hovered = card;
        }
        KanbanFrame.clip(mc, KanbanFrame.contentLeft(), TOP, areaWidth(), bottom() - TOP);
        if (hasScrollbar(stack)) drawScrollbar(index, column.getId(), stack, scroll, mouseX, mouseY);
        if (dropTarget) drawInsertionLine(stack, scroll, cardLeft);
        return hovered;
    }

    private void drawScrollbar(int column, UUID columnId, CardStack stack, int scroll, int mouseX, int mouseY) {
        int x = scrollbarLeft(column);
        int trackTop = cardsTop(), trackHeight = viewHeight();
        int[] thumb = BoardLayout.thumb(trackTop, trackHeight, trackHeight, stack.height, scroll);
        boolean active = columnId.equals(scrollDragColumn)
            || mouseX >= x - 1 && mouseX < x + SCROLLBAR + 1 && mouseY >= thumb[0] && mouseY < thumb[0] + thumb[1];
        drawRect(x, trackTop, x + SCROLLBAR, trackTop + trackHeight, 0xFF161616);
        // Thumb colors follow the frame's bevel greys.
        drawRect(x, thumb[0], x + SCROLLBAR, thumb[0] + thumb[1], active ? 0xFF8A8A8A : 0xFF6A6A6A);
        drawRect(x + SCROLLBAR - 1, thumb[0], x + SCROLLBAR, thumb[0] + thumb[1], 0xFF444444);
    }

    /** A bright line where the dragged card would land. */
    private void drawInsertionLine(CardStack stack, int scroll, int x) {
        if (KanbanClientState.findCard(pressedCardId) == null) return;
        int index = BoardLayout.dropIndex(dragMouseY - cardsTop() + scroll, stack.tops, stack.heights);
        int top = index < stack.cards.size() ? stack.tops[index] : stack.height + CARD_GAP;
        // Centred in the gap above the card it would land on.
        int y = cardsTop() - scroll + top - CARD_GAP / 2 - 1;
        y = Math.max(cardsTop() - CARD_GAP / 2 - 1, Math.min(y, bottom() - 2));
        drawRect(x, y, x + stack.width, y + 2, 0xFF47D6C8);
    }

    /** Where a card's title starts, from the card's left edge: right of its icon when it has one. */
    private int titleOffset(CardView card) {
        return KanbanFrame.CARD_LEFT + 5 + (iconStack(card.getIcon()) == null ? 0 : 18);
    }

    /** The title wrapped beside the card number, which sits at the right end of the first line. */
    private List<String> titleLines(CardView card, int width) {
        int right = width - KanbanFrame.CARD_RIGHT - 2;
        int number = fontRendererObj.getStringWidth("#" + card.getNumber());
        return fontRendererObj
            .listFormattedStringToWidth(card.getTitle(), Math.max(20, right - titleOffset(card) - number - 4));
    }

    /** Taller titles push the rows below them down; the progress row is only there when the card has a checklist. */
    private int cardHeight(CardView card, int width) {
        int rows = KanbanFrame.CARD_TOP + 6 + titleLines(card, width).size() * LINE_HEIGHT;
        rows += card.progressTotal() > 0 ? 31 : 23;
        return rows + KanbanFrame.CARD_BOTTOM;
    }

    private void drawCard(CardView card, int x, int y, int width, boolean faded) {
        CardType type = typeOf(card);
        int typeColor = type == null ? 0x555555 : type.getColor();
        int height = cardHeight(card, width);
        KanbanFrame.card(mc, x, y, width, height, faded ? 0.4f : 1f);
        // The type's color runs down the inside of the card's left bevel.
        int innerLeft = x + KanbanFrame.CARD_LEFT, innerTop = y + KanbanFrame.CARD_TOP;
        drawRect(innerLeft, innerTop, innerLeft + 2, y + height - KanbanFrame.CARD_BOTTOM, 0xFF000000 | typeColor);
        int left = innerLeft + 5;
        int right = x + width - KanbanFrame.CARD_RIGHT - 2;
        int textX = x + titleOffset(card);
        ItemStack icon = iconStack(card.getIcon());
        if (icon != null) drawItem(icon, left - 1, innerTop + 1);
        String number = "#" + card.getNumber();
        drawString(fontRendererObj, number, right - fontRendererObj.getStringWidth(number), innerTop + 3, 0xAAAAAA);
        List<String> title = titleLines(card, width);
        for (int line = 0; line < title.size(); line++)
            drawString(fontRendererObj, title.get(line), textX, innerTop + 3 + line * LINE_HEIGHT, 0xFFFFFF);

        int lineY = innerTop + 6 + title.size() * LINE_HEIGHT;
        // Under a one-line title the badge sits beside the icon; longer titles have already cleared it.
        int badgeX = title.size() > 1 ? left : textX;
        if (type != null) {
            int badge = Math.min(fontRendererObj.getStringWidth(type.getName()) + 6, width - 50);
            drawRect(badgeX, lineY - 1, badgeX + badge, lineY + 9, 0xFF000000 | darken(typeColor));
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(type.getName(), badge - 6),
                badgeX + 3,
                lineY,
                0xFFFFFF);
        }
        // 16px icon at the card's right edge, beside the badge row and clear of the number above it.
        boolean prioritised = card.getPriority() != Priority.NONE;
        KanbanFrame.priorityIcon(mc, card.getPriority(), right - 16, lineY - 2);

        String meta = TimeText.ago(card.getCreatedAt(), System.currentTimeMillis());
        if (!card.getCreatorName()
            .isEmpty()) meta += (meta.isEmpty() ? "by " : " by ") + card.getCreatorName();
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(meta, right - left - (prioritised ? 18 : 0)),
            left,
            lineY + 12,
            0x999999);

        int total = card.progressTotal();
        if (total > 0) {
            int done = card.progressDone();
            String count = done + "/" + total;
            int barRight = right - fontRendererObj.getStringWidth(count) - 4;
            int barLeft = left;
            int barY = lineY + 25;
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
        // Titles can be long; the tooltip wraps them rather than stretching across the screen.
        List<String> title = fontRendererObj
            .listFormattedStringToWidth("#" + card.getNumber() + " " + card.getTitle(), 200);
        lines.addAll(title);
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

    private static int darken(int color) {
        int r = (color >> 16 & 0xFF) * 3 / 5, g = (color >> 8 & 0xFF) * 3 / 5, b = (color & 0xFF) * 3 / 5;
        return r << 16 | g << 8 | b;
    }
}
