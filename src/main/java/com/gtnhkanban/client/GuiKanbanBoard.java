package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private static final int TOP = 32;
    private static final int HEADER = 18;
    private static final int GAP = 4;
    static final int CARD_HEIGHT = 52;
    private static final int CARD_GAP = 3;
    private static final int MIN_COLUMN = 118, MAX_COLUMN = 170;

    private final UUID projectId;
    private final RenderItem render = new RenderItem();
    private final Map<UUID, Integer> columnScroll = new HashMap<UUID, Integer>();
    private int horizontalScroll;
    private UUID pressedCardId;
    private int pressedX, pressedY, dragMouseX, dragMouseY;
    private boolean draggingCard;

    public GuiKanbanBoard(UUID projectId) {
        this(projectId, null);
    }

    public GuiKanbanBoard(UUID projectId, GuiScreen parent) {
        super(parent);
        this.projectId = projectId;
        refreshBoardWhileOpen(projectId);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(BACK, 4, 4, 60, 20, "Projects"));
        buttonList.add(new GuiButton(ICON, width - 214, 4, 50, 20, "Icon"));
        buttonList.add(new GuiButton(MEMBERS, width - 160, 4, 70, 20, "Members"));
        buttonList.add(new GuiButton(SETTINGS, width - 86, 4, 82, 20, "Board settings"));
        buttonList.add(new GuiButton(SCROLL_LEFT, 4, height - 22, 20, 18, "<"));
        buttonList.add(new GuiButton(SCROLL_RIGHT, width - 24, height - 22, 20, 18, ">"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK) goBack();
        else if (button.id == MEMBERS && projectId != null) mc.displayGuiScreen(new GuiProjectMembers(projectId, this));
        else if (button.id == SETTINGS) mc.displayGuiScreen(new GuiBoardSettings(this));
        else if (button.id == ICON && projectId != null) {
            mc.displayGuiScreen(new GuiItemPicker(this, "Choose a project icon", new GuiItemPicker.IconChoice() {

                @Override
                public void chosen(ItemKey icon) {
                    KanbanNetwork.CHANNEL.sendToServer(new C2SSetProjectIcon(projectId, icon));
                }
            }));
        } else if (button.id == SCROLL_LEFT) horizontalScroll = Math.max(0, horizontalScroll - columnWidth());
        else if (button.id == SCROLL_RIGHT)
            horizontalScroll = Math.min(maxHorizontalScroll(), horizontalScroll + columnWidth());
    }

    // ---- layout ----

    private List<BoardColumn> columns() {
        BoardSnapshot board = board();
        return board == null ? Collections.<BoardColumn>emptyList()
            : board.getSettings()
                .getColumns();
    }

    private int columnWidth() {
        int count = Math.max(1, columns().size());
        return Math.max(MIN_COLUMN, Math.min(MAX_COLUMN, (width - 8 - GAP * (count - 1)) / count));
    }

    private int boardWidth() {
        int count = columns().size();
        return count * columnWidth() + Math.max(0, count - 1) * GAP;
    }

    private int maxHorizontalScroll() {
        return Math.max(0, boardWidth() - (width - 8));
    }

    private int columnLeft(int index) {
        int offset = boardWidth() <= width - 8 ? (width - boardWidth()) / 2 : 4 - horizontalScroll;
        return offset + index * (columnWidth() + GAP);
    }

    private int bottom() {
        return height - 26;
    }

    private int cardsTop() {
        return TOP + HEADER + 2;
    }

    private int visibleCardSlots() {
        return Math.max(1, (bottom() - cardsTop()) / (CARD_HEIGHT + CARD_GAP));
    }

    private int columnAt(int mouseX) {
        List<BoardColumn> columns = columns();
        for (int index = 0; index < columns.size(); index++) {
            int left = columnLeft(index);
            if (mouseX >= left && mouseX < left + columnWidth()) return index;
        }
        return -1;
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
        int count = cardsIn(columnId).size();
        columnScroll.put(columnId, scrollOf(columnId, count) + (wheel > 0 ? -1 : 1));
        columnScroll.put(columnId, scrollOf(columnId, count));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || projectId == null || mouseY < TOP || mouseY >= bottom()) return;
        int column = columnAt(mouseX);
        if (column < 0) return;
        BoardColumn target = columns().get(column);
        int left = columnLeft(column);
        if (mouseY < TOP + HEADER) {
            if (mouseX >= left + columnWidth() - 16) {
                mc.displayGuiScreen(new GuiCardDetail(projectId, null, this, target.getId()));
            }
            return;
        }
        CardView card = cardAt(column, mouseY);
        if (card != null) {
            pressedCardId = card.getId();
            pressedX = mouseX;
            pressedY = mouseY;
        }
    }

    private CardView cardAt(int column, int mouseY) {
        UUID columnId = columns().get(column)
            .getId();
        List<CardView> cards = cardsIn(columnId);
        int slot = (mouseY - cardsTop()) / (CARD_HEIGHT + CARD_GAP);
        int within = (mouseY - cardsTop()) % (CARD_HEIGHT + CARD_GAP);
        int index = scrollOf(columnId, cards.size()) + slot;
        if (mouseY < cardsTop() || within >= CARD_HEIGHT || slot >= visibleCardSlots() || index >= cards.size())
            return null;
        return cards.get(index);
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        super.mouseMovedOrUp(mouseX, mouseY, button);
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
        if (column < 0 || mouseY < TOP || mouseY >= bottom()) return;
        BoardColumn target = columns().get(column);
        CardView card = KanbanClientState.findCard(cardId);
        if (card != null && !card.getColumnId()
            .equals(target.getId())) {
            KanbanClientState.moveCardLocally(projectId, cardId, target.getId());
            KanbanClientState.setResult(true, "", "Moved #" + card.getNumber() + " to " + target.getName() + ".");
            KanbanNetwork.CHANNEL.sendToServer(new C2SMoveCard(projectId, cardId, target.getId()));
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
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
        BoardSnapshot board = board();
        boolean overflow = maxHorizontalScroll() > 0;
        for (GuiButton button : buttonList) {
            if (button.id == SCROLL_LEFT || button.id == SCROLL_RIGHT) button.visible = overflow;
        }
        horizontalScroll = Math.min(horizontalScroll, maxHorizontalScroll());
        String title = board == null ? "Loading board..."
            : board.getProject()
                .getName();
        int titleWidth = fontRendererObj.getStringWidth(title);
        ItemStack projectIcon = board == null ? null
            : iconStack(
                board.getProject()
                    .getIcon());
        int titleX = width / 2 - titleWidth / 2 + (projectIcon == null ? 0 : 10);
        if (projectIcon != null) drawItem(projectIcon, titleX - 20, 6);
        drawString(fontRendererObj, title, titleX, 10, 0xFFFFFF);
        String result = KanbanClientState.getResultMessage();
        if (!result.isEmpty()) drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(result, width - 20),
            width / 2,
            22,
            KanbanClientState.isResultSuccess() ? 0x55FF55 : 0xFF5555);

        CardView hovered = null;
        List<BoardColumn> columns = columns();
        for (int index = 0; index < columns.size(); index++) {
            CardView hit = drawColumn(index, columns.get(index), mouseX, mouseY);
            if (hit != null) hovered = hit;
        }
        if (draggingCard) drawDragged(columns);
        if (overflow) drawCenteredString(
            fontRendererObj,
            "Scroll sideways: shift + mouse wheel, or the arrows",
            width / 2,
            height - 17,
            0x888888);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (hovered != null && !draggingCard) drawHoveringText(cardTooltip(hovered), mouseX, mouseY, fontRendererObj);
    }

    /** @return the card under the mouse, if any */
    private CardView drawColumn(int index, BoardColumn column, int mouseX, int mouseY) {
        int left = columnLeft(index);
        int width = columnWidth();
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
        CardView hovered = null;
        for (int slot = 0; slot < visibleCardSlots() && scroll + slot < cards.size(); slot++) {
            CardView card = cards.get(scroll + slot);
            int y = cardsTop() + slot * (CARD_HEIGHT + CARD_GAP);
            boolean dragged = draggingCard && card.getId()
                .equals(pressedCardId);
            drawCard(card, left + 3, y, width - 6, dragged);
            if (mouseX >= left + 3 && mouseX < left + width - 3 && mouseY >= y && mouseY < y + CARD_HEIGHT)
                hovered = card;
        }
        if (scroll > 0)
            drawCenteredString(fontRendererObj, "▲ " + scroll + " more", left + width / 2, cardsTop() - 1, 0xAAAAAA);
        int below = cards.size() - scroll - visibleCardSlots();
        if (below > 0)
            drawCenteredString(fontRendererObj, "▼ " + below + " more", left + width / 2, bottom() - 9, 0xAAAAAA);
        return hovered;
    }

    private void drawCard(CardView card, int x, int y, int width, boolean faded) {
        CardType type = typeOf(card);
        int typeColor = type == null ? 0x555555 : type.getColor();
        drawRect(x, y, x + width, y + CARD_HEIGHT, faded ? 0x66333333 : 0xEE2B2B2B);
        drawRect(x, y, x + 3, y + CARD_HEIGHT, 0xFF000000 | typeColor);
        int textX = x + 6;
        ItemStack icon = iconStack(card.getIcon());
        if (icon != null) {
            drawItem(icon, x + 5, y + 3);
            textX = x + 24;
        }
        String number = "§7#" + card.getNumber() + " §f";
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(number + card.getTitle(), x + width - textX - 2),
            textX,
            y + 4,
            0xFFFFFF);

        int lineY = y + 17;
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
            x + width - fontRendererObj.getStringWidth(priority) - 4,
            lineY,
            priorityColor(card.getPriority()));

        String meta = TimeText.ago(card.getCreatedAt(), System.currentTimeMillis());
        if (!card.getCreatorName()
            .isEmpty()) meta += (meta.isEmpty() ? "by " : " by ") + card.getCreatorName();
        drawString(fontRendererObj, fontRendererObj.trimStringToWidth(meta, width - 10), x + 6, y + 29, 0x999999);

        int total = card.progressTotal();
        if (total > 0) {
            int done = card.progressDone();
            String count = done + "/" + total;
            int barRight = x + width - fontRendererObj.getStringWidth(count) - 8;
            int barLeft = x + 6;
            int barY = y + 42;
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
        drawCard(card, dragMouseX + 6, dragMouseY + 6, columnWidth() - 6, false);
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
        lines.add("§8Click to open, drag to move");
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

    /** Cards in a column, highest priority first, then by number. */
    private List<CardView> cardsIn(UUID columnId) {
        List<CardView> cards = new ArrayList<CardView>();
        BoardSnapshot board = board();
        if (board == null) return cards;
        for (CardView card : board.getCards()) if (card.getColumnId()
            .equals(columnId)) cards.add(card);
        Collections.sort(cards, new Comparator<CardView>() {

            @Override
            public int compare(CardView first, CardView second) {
                int byPriority = second.getPriority()
                    .compareTo(first.getPriority());
                return byPriority != 0 ? byPriority : Integer.compare(first.getNumber(), second.getNumber());
            }
        });
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
