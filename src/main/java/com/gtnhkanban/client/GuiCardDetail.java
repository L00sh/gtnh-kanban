package com.gtnhkanban.client;

import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Mouse;

import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SCreateCard;
import com.gtnhkanban.network.message.C2SDeleteCard;
import com.gtnhkanban.network.message.C2SUpdateCard;

public final class GuiCardDetail extends GuiKanbanScreen {

    private static final int SAVE = 1, BACK = 2, STATUS = 3, ADD_REQUIREMENT = 4, PIN = 5, DELETE = 6, ASSIGNEES = 7;
    private final UUID projectId, cardId;
    private final GuiScreen boardScreen;
    private GuiTextField titleField;
    private GuiMultilineEditor descriptionEditor;
    private GuiChecklistPanel checklist;
    private CardStatus status = CardStatus.TODO;
    private int panelLeft, panelWidth, editorBottom;

    public GuiCardDetail(UUID projectId, UUID cardId) {
        this(projectId, cardId, null);
    }

    public GuiCardDetail(UUID projectId, UUID cardId, GuiScreen boardScreen) {
        super(boardScreen);
        this.projectId = projectId;
        this.cardId = cardId;
        this.boardScreen = boardScreen;
        refreshBoardWhileOpen(projectId);
    }

    @Override
    public void initGui() {
        CardView card = KanbanClientState.findCard(cardId);
        boolean initialized = titleField != null;
        String title = initialized ? titleField.getText() : card == null ? "" : card.getTitle();
        String description = initialized ? descriptionEditor.getText() : card == null ? "" : card.getDescription();
        if (!initialized && card != null) status = card.getStatus();
        panelWidth = Math.min(width - 24, 520);
        panelLeft = (width - panelWidth) / 2;
        titleField = new GuiTextField(fontRendererObj, panelLeft, 37, panelWidth, 18);
        titleField.setMaxStringLength(64);
        titleField.setText(title);
        titleField.setFocused(cardId == null);
        int lines = Math.max(1, Math.min(4, (height - 205) / 18));
        descriptionEditor = new GuiMultilineEditor(fontRendererObj, panelLeft, 72, panelWidth, lines);
        descriptionEditor.setText(description);
        editorBottom = 72 + lines * 18 + 4;
        buttonList.clear();
        buttonList.add(new GuiButton(SAVE, width / 2 - 150, height - 53, 65, 20, "Save"));
        buttonList.add(new GuiButton(BACK, width / 2 - 77, height - 53, 65, 20, "Board"));
        buttonList.add(new GuiButton(STATUS, width / 2 - 4, height - 53, 120, 20, "Status: " + statusLabel(status)));
        if (cardId != null) {
            buttonList.add(new GuiButton(PIN, width / 2 - 100, height - 28, 95, 20, "Pin to HUD"));
            buttonList.add(new GuiButton(DELETE, width / 2 + 5, height - 28, 80, 20, "Delete card"));
            buttonList.add(new GuiButton(ADD_REQUIREMENT, panelLeft, editorBottom + 20, 125, 20, "Add checklist item"));
            buttonList.add(new GuiButton(ASSIGNEES, panelLeft + 130, editorBottom + 20, 100, 20, "Assignees"));
            checklist = new GuiChecklistPanel(
                mc,
                projectId,
                cardId,
                panelLeft,
                editorBottom + 45,
                panelWidth,
                height - 74,
                new GuiChecklistPanel.RecipeAction() {

                    @Override
                    public void open(RequirementView row) {
                        mc.displayGuiScreen(new GuiRecipePicker(projectId, cardId, row, GuiCardDetail.this));
                    }
                });
        }
        refreshPin();
    }

    private void refreshPin() {
        if (cardId == null) return;
        CardView card = KanbanClientState.findCard(cardId);
        for (GuiButton button : buttonList) if (button.id == PIN) {
            button.enabled = card != null && card.getStatus() == CardStatus.IN_PROGRESS;
            button.displayString = KanbanClientState.isCardPinned(projectId, cardId) ? "Unpin HUD" : "Pin to HUD";
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == SAVE) {
            if (cardId == null) KanbanNetwork.CHANNEL
                .sendToServer(new C2SCreateCard(projectId, titleField.getText(), descriptionEditor.getText()));
            else KanbanNetwork.CHANNEL.sendToServer(
                new C2SUpdateCard(projectId, cardId, titleField.getText(), descriptionEditor.getText(), status));
            mc.displayGuiScreen(boardScreen == null ? new GuiKanbanBoard(projectId) : boardScreen);
        } else if (button.id == BACK) goBack();
        else if (button.id == STATUS) {
            status = CardStatus.values()[(status.ordinal() + 1) % CardStatus.values().length];
            button.displayString = "Status: " + statusLabel(status);
        } else if (button.id == PIN) {
            if (KanbanClientState.isCardPinned(projectId, cardId)) KanbanClientState.clearPinnedCard();
            else KanbanClientState.pinCard(projectId, KanbanClientState.findCard(cardId));
            refreshPin();
        } else if (button.id == DELETE) {
            mc.displayGuiScreen(
                new GuiConfirmAction(
                    boardScreen == null ? new GuiKanbanBoard(projectId) : boardScreen,
                    "Delete this card?",
                    new Runnable() {

                        @Override
                        public void run() {
                            KanbanNetwork.CHANNEL.sendToServer(new C2SDeleteCard(projectId, cardId));
                        }
                    }));
        } else if (button.id == ADD_REQUIREMENT) mc.displayGuiScreen(new GuiItemPicker(projectId, cardId, this));
        else if (button.id == ASSIGNEES) mc.displayGuiScreen(new GuiCardAssignees(projectId, cardId, this));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        titleField.updateCursorCounter();
        if (checklist != null) checklist.refresh();
        refreshPin();
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        titleField.mouseClicked(x, y, button);
        descriptionEditor.mouseClicked(x, y, button);
        if (checklist != null) checklist.click(x, y, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        if (checklist != null) checklist.scroll(
            Mouse.getEventDWheel(),
            Mouse.getEventX() * width / mc.displayWidth,
            height - Mouse.getEventY() * height / mc.displayHeight - 1);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (handleEscape(key)) return;
        if (checklist != null && checklist.keyTyped(c, key)) return;
        if (!titleField.textboxKeyTyped(c, key) && !descriptionEditor.keyTyped(c, key)) super.keyTyped(c, key);
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, cardId == null ? "Create card" : "Card details", width / 2, 10, 0xFFFFFF);
        drawString(fontRendererObj, "Title", panelLeft, 25, 0xAAAAAA);
        titleField.drawTextBox();
        drawString(fontRendererObj, "Description (optional)", panelLeft, 61, 0xAAAAAA);
        descriptionEditor.drawTextBoxes();
        if (cardId != null) {
            CardView card = KanbanClientState.findCard(cardId);
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(
                    "Assigned: " + (card == null ? "" : KanbanClientState.assigneeNames(card)),
                    panelWidth),
                panelLeft,
                editorBottom + 5,
                0xCCCCCC);
            checklist.draw(x, y);
        } else drawString(
            fontRendererObj,
            "Save this card, then add checklist items and assignees.",
            panelLeft,
            editorBottom + 8,
            0xAAAAAA);
        String result = KanbanClientState.getResultMessage();
        if (!result.isEmpty()) drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(result, width - 20),
            width / 2,
            height - 70,
            KanbanClientState.isResultSuccess() ? 0x55FF55 : 0xFF5555);
        super.drawScreen(x, y, partialTicks);
        if (checklist != null) {
            java.util.List<String> tooltip = checklist.tooltip(x, y);
            if (!tooltip.isEmpty()) drawHoveringText(tooltip, x, y, fontRendererObj);
        }
    }

    private String statusLabel(CardStatus value) {
        switch (value) {
            case IN_PROGRESS:
                return "In progress";
            case DONE:
                return "Done";
            default:
                return "To do";
        }
    }
}
