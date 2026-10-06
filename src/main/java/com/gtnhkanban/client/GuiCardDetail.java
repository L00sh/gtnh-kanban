package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.CardStatus;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SCreateCard;
import com.gtnhkanban.network.message.C2SDeleteCard;
import com.gtnhkanban.network.message.C2SDeleteRequirement;
import com.gtnhkanban.network.message.C2SSetRequirementComplete;
import com.gtnhkanban.network.message.C2SSetRequirementQuantity;
import com.gtnhkanban.network.message.C2SUpdateCard;

public final class GuiCardDetail extends GuiKanbanScreen {

    private static final int SAVE = 1;
    private static final int BACK = 2;
    private static final int STATUS = 3;
    private static final int ADD_REQUIREMENT = 4;
    private static final int PIN = 5;
    private static final int DELETE = 6;

    private final UUID projectId;
    private final UUID cardId;
    private final GuiScreen boardScreen;
    private GuiTextField titleField;
    private GuiMultilineEditor descriptionEditor;
    private CardStatus status = CardStatus.TODO;
    private final Map<Integer, UUID> deleteRequirementButtons = new HashMap<Integer, UUID>();
    private final Map<Integer, UUID> quantityButtons = new HashMap<Integer, UUID>();
    private final Map<UUID, GuiTextField> quantityFields = new HashMap<UUID, GuiTextField>();
    private final Map<UUID, Integer> displayedQuantities = new HashMap<UUID, Integer>();
    private final List<UUID> renderedRequirementIds = new ArrayList<UUID>();

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
        buttonList.clear();
        deleteRequirementButtons.clear();
        quantityButtons.clear();
        quantityFields.clear();
        displayedQuantities.clear();
        renderedRequirementIds.clear();
        buttonList.add(new GuiButton(SAVE, width / 2 - 150, height - 53, 65, 20, "Save"));
        buttonList.add(new GuiButton(BACK, width / 2 - 77, height - 53, 65, 20, "Board"));
        buttonList.add(new GuiButton(STATUS, width / 2 - 4, height - 53, 100, 20, "Status: " + statusLabel(status)));
        GuiButton pin = new GuiButton(PIN, width / 2 - 100, height - 28, 95, 20, "Pin to HUD");
        pin.visible = cardId != null;
        CardView existingCard = findCard();
        pin.enabled = existingCard != null && existingCard.getStatus() == CardStatus.IN_PROGRESS;
        buttonList.add(pin);
        GuiButton delete = new GuiButton(DELETE, width / 2 + 5, height - 28, 80, 20, "Delete card");
        delete.visible = cardId != null;
        buttonList.add(delete);
        titleField = new GuiTextField(fontRendererObj, width / 2 - 150, 42, 300, 20);
        titleField.setMaxStringLength(64);
        titleField.setFocused(cardId == null);
        descriptionEditor = new GuiMultilineEditor(fontRendererObj, width / 2 - 150, 88, 300);
        if (cardId != null) {
            buttonList.add(new GuiButton(ADD_REQUIREMENT, width / 2 - 55, 194, 110, 20, "Add checklist item"));
            CardView checklistCard = findCard();
            if (checklistCard != null) {
                for (int index = 0; index < checklistCard.getRequirements()
                    .size(); index++) {
                    RequirementView requirement = checklistCard.getRequirements()
                        .get(index);
                    GuiTextField quantity = new GuiTextField(fontRendererObj, width / 2 + 14, 221 + index * 22, 56, 18);
                    quantity.setMaxStringLength(10);
                    quantity.setText(Integer.toString(requirement.getQuantity()));
                    quantityFields.put(requirement.getId(), quantity);
                    displayedQuantities.put(requirement.getId(), requirement.getQuantity());
                    int applyId = 100 + index * 2;
                    buttonList.add(new GuiButton(applyId, width / 2 + 76, 221 + index * 22, 44, 18, "Apply"));
                    quantityButtons.put(applyId, requirement.getId());
                    int buttonId = applyId + 1;
                    renderedRequirementIds.add(
                        checklistCard.getRequirements()
                            .get(index)
                            .getId());
                    buttonList.add(new GuiButton(buttonId, width / 2 + 126, 221 + index * 22, 20, 18, "X"));
                    deleteRequirementButtons.put(
                        buttonId,
                        checklistCard.getRequirements()
                            .get(index)
                            .getId());
                }
            }
        }
        CardView card = findCard();
        if (card != null) {
            titleField.setText(card.getTitle());
            descriptionEditor.setText(card.getDescription());
            status = card.getStatus();
            buttonList.get(2).displayString = "Status: " + statusLabel(status);
            buttonList.get(3).displayString = KanbanClientState.isCardPinned(projectId, cardId) ? "Unpin HUD"
                : "Pin to HUD";
            buttonList.get(3).enabled = card.getStatus() == CardStatus.IN_PROGRESS;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == SAVE) {
            if (cardId == null) {
                KanbanNetwork.CHANNEL
                    .sendToServer(new C2SCreateCard(projectId, titleField.getText(), descriptionEditor.getText()));
            } else {
                KanbanNetwork.CHANNEL.sendToServer(
                    new C2SUpdateCard(projectId, cardId, titleField.getText(), descriptionEditor.getText(), status));
            }
            if (boardScreen != null) mc.displayGuiScreen(boardScreen);
            else mc.displayGuiScreen(new GuiKanbanBoard(projectId));
        } else if (button.id == BACK) {
            goBack();
        } else if (button.id == STATUS) {
            status = CardStatus.values()[(status.ordinal() + 1) % CardStatus.values().length];
            button.displayString = "Status: " + statusLabel(status);
            CardView storedCard = findCard();
            buttonList.get(3).enabled = storedCard != null && storedCard.getStatus() == CardStatus.IN_PROGRESS;
        } else if (button.id == PIN && cardId != null) {
            if (KanbanClientState.isCardPinned(projectId, cardId)) KanbanClientState.clearPinnedCard();
            else KanbanClientState.pinCard(projectId, findCard());
            button.displayString = KanbanClientState.isCardPinned(projectId, cardId) ? "Unpin HUD" : "Pin to HUD";
        } else if (button.id == DELETE && cardId != null) {
            mc.displayGuiScreen(
                new GuiConfirmAction(
                    boardScreen == null ? new GuiKanbanBoard(projectId) : boardScreen,
                    "Delete this card?",
                    new Runnable() {

                        @Override
                        public void run() {
                            KanbanNetwork.CHANNEL.sendToServer(new C2SDeleteCard(projectId, cardId));
                            KanbanClientState.clearPinnedCard();
                        }
                    }));
        } else if (button.id == ADD_REQUIREMENT && cardId != null) {
            saveCardThenOpenPicker();
        } else if (quantityButtons.containsKey(button.id)) {
            applyQuantity(quantityButtons.get(button.id));
        } else if (deleteRequirementButtons.containsKey(button.id)) {
            UUID requirementId = deleteRequirementButtons.get(button.id);
            KanbanClientState.removeRequirementLocally(projectId, cardId, requirementId);
            KanbanNetwork.CHANNEL.sendToServer(new C2SDeleteRequirement(projectId, cardId, requirementId));
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        List<UUID> currentIds = new ArrayList<UUID>();
        CardView card = findCard();
        if (card != null) {
            for (RequirementView requirement : card.getRequirements()) currentIds.add(requirement.getId());
        }
        if (card != null) {
            for (RequirementView requirement : card.getRequirements()) {
                GuiTextField field = quantityFields.get(requirement.getId());
                Integer displayed = displayedQuantities.get(requirement.getId());
                if (field != null && displayed != null && displayed.intValue() != requirement.getQuantity()) {
                    field.setText(Integer.toString(requirement.getQuantity()));
                    displayedQuantities.put(requirement.getId(), requirement.getQuantity());
                }
                if (field != null) field.updateCursorCounter();
            }
        }
        if (!renderedRequirementIds.equals(currentIds)) {
            String title = titleField.getText();
            String description = descriptionEditor.getText();
            CardStatus currentStatus = status;
            initGui();
            titleField.setText(title);
            descriptionEditor.setText(description);
            status = currentStatus;
            buttonList.get(2).displayString = "Status: " + statusLabel(status);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        titleField.mouseClicked(mouseX, mouseY, mouseButton);
        descriptionEditor.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField field : quantityFields.values()) field.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || cardId == null) return;
        CardView card = findCard();
        if (card == null) return;
        if (mouseX < width / 2 - 150 || mouseX >= width / 2 + 12) return;
        int row = (mouseY - 221) / 22;
        if (mouseY >= 221 && row >= 0
            && row < card.getRequirements()
                .size()) {
            RequirementView requirement = card.getRequirements()
                .get(row);
            KanbanNetwork.CHANNEL.sendToServer(
                new C2SSetRequirementComplete(projectId, cardId, requirement.getId(), !requirement.isComplete()));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (handleEscape(keyCode)) return;
        for (Map.Entry<UUID, GuiTextField> entry : quantityFields.entrySet()) {
            if (!entry.getValue()
                .isFocused()) continue;
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                applyQuantity(entry.getKey());
                return;
            }
            if (typedChar >= '0' && typedChar <= '9' || typedChar < 32 || isCtrlKeyDown()) {
                entry.getValue()
                    .textboxKeyTyped(typedChar, keyCode);
            }
            return;
        }
        boolean handled = titleField.textboxKeyTyped(typedChar, keyCode);
        handled |= descriptionEditor.keyTyped(typedChar, keyCode);
        if (!handled) super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, cardId == null ? "Create card" : "Card details", width / 2, 18, 0xFFFFFF);
        drawString(fontRendererObj, "Title", width / 2 - 150, 30, 0xAAAAAA);
        titleField.drawTextBox();
        drawString(fontRendererObj, "Description (optional)", width / 2 - 150, 69, 0xAAAAAA);
        descriptionEditor.drawTextBoxes();
        if (cardId != null) {
            drawString(
                fontRendererObj,
                "Checklist (click an entry to toggle completion)",
                width / 2 - 150,
                168,
                0xFFFFFF);
            if (status != CardStatus.IN_PROGRESS) {
                drawString(
                    fontRendererObj,
                    "Only in-progress cards can be pinned to the HUD.",
                    width / 2 - 150,
                    184,
                    0xAAAAAA);
            }
            CardView card = findCard();
            if (card != null) {
                if (card.getRequirements()
                    .isEmpty()) {
                    drawString(
                        fontRendererObj,
                        "No checklist items yet. Add one above.",
                        width / 2 - 150,
                        224,
                        0xAAAAAA);
                }
                for (int index = 0; index < card.getRequirements()
                    .size(); index++) {
                    RequirementView requirement = card.getRequirements()
                        .get(index);
                    String itemName = displayName(requirement);
                    String check = requirement.isComplete() ? "[x] " : "[ ] ";
                    int y = 225 + index * 22;
                    renderRequirementIcon(requirement, width / 2 - 150, y - 2);
                    drawString(
                        fontRendererObj,
                        check + fontRendererObj.trimStringToWidth(itemName, 115),
                        width / 2 - 130,
                        y,
                        requirement.isComplete() ? 0x55FF55 : 0xFFFFFF);
                }
            }
        } else {
            drawString(fontRendererObj, "Save this card, then add checklist items.", width / 2 - 150, 170, 0xAAAAAA);
        }
        for (GuiTextField field : quantityFields.values()) field.drawTextBox();
        String result = KanbanClientState.getResultMessage();
        if (!result.isEmpty()) drawCenteredString(
            fontRendererObj,
            result,
            width / 2,
            height - 48,
            KanbanClientState.isResultSuccess() ? 0x55FF55 : 0xFF5555);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void applyQuantity(UUID requirementId) {
        GuiTextField field = quantityFields.get(requirementId);
        try {
            int quantity = Integer.parseInt(field.getText());
            if (quantity < 1) throw new NumberFormatException();
            KanbanNetwork.CHANNEL
                .sendToServer(new C2SSetRequirementQuantity(projectId, cardId, requirementId, quantity));
        } catch (NumberFormatException exception) {
            KanbanClientState.setResult(false, "INVALID_QUANTITY", "Quantity must be a positive whole number.");
        }
    }

    private CardView findCard() {
        BoardSnapshot board = KanbanClientState.getBoard();
        if (board == null || cardId == null) return null;
        for (CardView card : board.getCards()) {
            if (cardId.equals(card.getId())) return card;
        }
        return null;
    }

    private void saveCardThenOpenPicker() {
        KanbanNetwork.CHANNEL.sendToServer(
            new C2SUpdateCard(projectId, cardId, titleField.getText(), descriptionEditor.getText(), status));
        mc.displayGuiScreen(new GuiItemPicker(projectId, cardId, this));
    }

    private String displayName(RequirementView requirement) {
        Object registered = Item.itemRegistry.getObject(
            requirement.getItem()
                .getRegistryName());
        if (!(registered instanceof Item)) return "[missing] " + requirement.getItem()
            .getRegistryName();
        ItemStack stack = new ItemStack(
            (Item) registered,
            1,
            requirement.getItem()
                .getMetadata());
        return stack.getDisplayName();
    }

    private void renderRequirementIcon(RequirementView requirement, int x, int y) {
        Object registered = Item.itemRegistry.getObject(
            requirement.getItem()
                .getRegistryName());
        if (registered instanceof Item) {
            ItemStack stack = new ItemStack(
                (Item) registered,
                1,
                requirement.getItem()
                    .getMetadata());
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        } else {
            drawRect(x, y, x + 16, y + 16, 0xFFAA3333);
            drawCenteredString(fontRendererObj, "?", x + 8, y + 4, 0xFFFFFF);
        }
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
