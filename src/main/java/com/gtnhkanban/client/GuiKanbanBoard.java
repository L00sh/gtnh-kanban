package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SMoveCard;

public final class GuiKanbanBoard extends GuiKanbanScreen {

    private static final int PAGE_SIZE = 4;
    private static final int BACK = 1;
    private static final int CREATE_CARD = 2;
    private static final int MEMBERS = 3;
    private static final int PREVIOUS = 4;
    private static final int NEXT = 5;

    private final UUID projectId;
    private int page;
    private UUID pressedCardId;
    private int pressedX;
    private int pressedY;
    private boolean draggingCard;
    private int dragMouseX;
    private int dragMouseY;

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
        buttonList.add(new GuiButton(BACK, width / 2 - 135, height - 28, 55, 20, "Projects"));
        buttonList.add(new GuiButton(CREATE_CARD, width / 2 - 75, height - 28, 80, 20, "New card"));
        buttonList.add(new GuiButton(MEMBERS, width / 2 + 10, height - 28, 70, 20, "Members"));
        buttonList.add(new GuiButton(PREVIOUS, width / 2 + 85, height - 28, 25, 20, "<"));
        buttonList.add(new GuiButton(NEXT, width / 2 + 115, height - 28, 25, 20, ">"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK) {
            goBack();
        } else if (button.id == CREATE_CARD && projectId != null) {
            mc.displayGuiScreen(new GuiCardDetail(projectId, null, this));
        } else if (button.id == MEMBERS && projectId != null) {
            mc.displayGuiScreen(new GuiProjectMembers(projectId, this));
        } else if (button.id == PREVIOUS) {
            page = Math.max(0, page - 1);
        } else if (button.id == NEXT) {
            page = Math.min(lastPage(), page + 1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || projectId == null || mouseY < 61 || mouseY >= height - 34) {
            return;
        }
        int left = (width - 300) / 2;
        int column = (mouseX - left) / 100;
        if (column < 0 || column > 2 || mouseX >= left + 300) {
            return;
        }
        List<CardView> cards = cardsInColumn(CardStatus.values()[column]);
        List<CardView> visible = PagedList.pageItems(cards, page, PAGE_SIZE);
        int row = (mouseY - 61) / 28;
        if (row >= 0 && row < visible.size()) {
            pressedCardId = visible.get(row)
                .getId();
            pressedX = mouseX;
            pressedY = mouseY;
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        super.mouseMovedOrUp(mouseX, mouseY, button);
        if (button != 0 || pressedCardId == null) return;
        UUID cardId = pressedCardId;
        pressedCardId = null;
        boolean dragged = Math.abs(mouseX - pressedX) > 5 || Math.abs(mouseY - pressedY) > 5;
        int left = (width - 300) / 2;
        int targetColumn = (mouseX - left) / 100;
        if (dragged && targetColumn >= 0
            && targetColumn < CardStatus.values().length
            && mouseX >= left
            && mouseX < left + 300
            && mouseY >= 61
            && mouseY < height - 34) {
            CardStatus targetStatus = CardStatus.values()[targetColumn];
            CardView card = findCard(cardId);
            if (card != null && card.getStatus() != targetStatus) {
                KanbanClientState.moveCardLocally(projectId, cardId, targetStatus);
                KanbanClientState.setResult(true, "", "Moved card to " + statusLabel(targetStatus) + ".");
                KanbanNetwork.CHANNEL.sendToServer(new C2SMoveCard(projectId, cardId, targetStatus));
            }
        } else if (!dragged) {
            mc.displayGuiScreen(new GuiCardDetail(projectId, cardId, this));
        }
        draggingCard = false;
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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        BoardSnapshot board = KanbanClientState.getBoard();
        String projectName = board == null ? "Loading board..."
            : board.getProject()
                .getName();
        drawCenteredString(fontRendererObj, projectName, width / 2, 14, 0xFFFFFF);
        int left = (width - 300) / 2;
        CardStatus[] statuses = CardStatus.values();
        String[] labels = { "To do", "In progress", "Done" };
        for (int column = 0; column < statuses.length; column++) {
            int x = left + column * 100;
            drawCenteredString(fontRendererObj, labels[column], x + 50, 39, 0xFFFFFF);
            List<CardView> visible = PagedList.pageItems(cardsInColumn(statuses[column]), page, PAGE_SIZE);
            for (int row = 0; row < visible.size(); row++) {
                String title = visible.get(row)
                    .getTitle();
                if (title.length() > 15) title = title.substring(0, 14) + "…";
                boolean selectedForDrag = draggingCard && pressedCardId.equals(
                    visible.get(row)
                        .getId());
                drawRect(x + 3, 58 + row * 28, x + 97, 82 + row * 28, selectedForDrag ? 0x66444444 : 0xAA333333);
                drawCenteredString(fontRendererObj, title, x + 50, 66 + row * 28, 0xFFFFFF);
                CardView rowCard = visible.get(row);
                String subtitle = rowCard.getAssigneeIds()
                    .isEmpty()
                        ? "requirements: " + rowCard.getRequirements()
                            .size()
                        : KanbanClientState.assigneeNames(rowCard);
                drawCenteredString(
                    fontRendererObj,
                    fontRendererObj.trimStringToWidth(subtitle, 88),
                    x + 50,
                    77 + row * 28,
                    0xAAAAAA);
            }
        }
        if (draggingCard) {
            int hoverColumn = (dragMouseX - left) / 100;
            if (hoverColumn >= 0 && hoverColumn < statuses.length
                && dragMouseX >= left
                && dragMouseX < left + 300
                && dragMouseY >= 58
                && dragMouseY < height - 34) {
                int highlightX = left + hoverColumn * 100;
                drawRect(highlightX, 55, highlightX + 100, height - 37, 0x2233CC66);
            }
            CardView draggedCard = findCard(pressedCardId);
            if (draggedCard != null) {
                String title = fontRendererObj.trimStringToWidth(draggedCard.getTitle(), 88);
                drawRect(dragMouseX + 8, dragMouseY + 8, dragMouseX + 100, dragMouseY + 30, 0xDD222222);
                drawCenteredString(fontRendererObj, title, dragMouseX + 54, dragMouseY + 14, 0xFFFFFF);
            }
        }
        drawCenteredString(
            fontRendererObj,
            "Page " + (page + 1) + " / " + (lastPage() + 1),
            width / 2,
            height - 31,
            0xFFFFFF);
        if (board != null && !KanbanClientState.getResultMessage()
            .isEmpty()) {
            drawCenteredString(
                fontRendererObj,
                KanbanClientState.getResultMessage(),
                width / 2,
                26,
                KanbanClientState.isResultSuccess() ? 0x55FF55 : 0xFF5555);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private List<CardView> cardsInColumn(CardStatus status) {
        List<CardView> cards = new ArrayList<CardView>();
        BoardSnapshot board = KanbanClientState.getBoard();
        if (board != null && projectId != null
            && projectId.equals(
                board.getProject()
                    .getId())) {
            for (CardView card : board.getCards()) {
                if (card.getStatus() == status) cards.add(card);
            }
        }
        return cards;
    }

    private int lastPage() {
        int pages = 1;
        for (CardStatus status : CardStatus.values()) {
            pages = Math.max(pages, PagedList.pageCount(cardsInColumn(status).size(), PAGE_SIZE));
        }
        return pages - 1;
    }

    private CardView findCard(UUID cardId) {
        for (CardView card : cardsInColumn(CardStatus.TODO)) if (cardId.equals(card.getId())) return card;
        for (CardView card : cardsInColumn(CardStatus.IN_PROGRESS)) if (cardId.equals(card.getId())) return card;
        for (CardView card : cardsInColumn(CardStatus.DONE)) if (cardId.equals(card.getId())) return card;
        return null;
    }

    private static String statusLabel(CardStatus cardStatus) {
        switch (cardStatus) {
            case IN_PROGRESS:
                return "In progress";
            case DONE:
                return "Done";
            default:
                return "To do";
        }
    }
}
