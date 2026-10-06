package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.CardType;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.Priority;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SAddTask;
import com.gtnhkanban.network.message.C2SCreateCard;
import com.gtnhkanban.network.message.C2SDeleteCard;
import com.gtnhkanban.network.message.C2SUpdateCard;

public final class GuiCardDetail extends GuiKanbanScreen {

    private static final int SAVE = 1, BACK = 2, ADD_REQUIREMENT = 4, PIN = 5, DELETE = 6, ASSIGNEES = 7, STRATEGY = 8,
        REGENERATE = 9, TYPE = 10, PRIORITY = 11, COLUMN = 12, ICON = 13, COMMENTS = 14, ADD_TASK = 15;
    private final UUID projectId, cardId;
    private final GuiScreen boardScreen;
    private final RenderItem render = new RenderItem();
    private GuiTextField titleField, taskField;
    private GuiMultilineEditor descriptionEditor;
    private GuiChecklistPanel checklist;
    private int panelLeft, panelWidth, editorBottom;

    // Edited locally and sent on Save, like the title.
    private boolean loaded;
    private UUID columnId, typeId;
    private Priority priority = Priority.NONE;
    private ItemKey icon;

    public GuiCardDetail(UUID projectId, UUID cardId) {
        this(projectId, cardId, null);
    }

    public GuiCardDetail(UUID projectId, UUID cardId, GuiScreen boardScreen) {
        this(projectId, cardId, boardScreen, null);
    }

    /** @param columnId for a new card, the column it starts in (null for the first column) */
    public GuiCardDetail(UUID projectId, UUID cardId, GuiScreen boardScreen, UUID columnId) {
        super(boardScreen);
        this.projectId = projectId;
        this.cardId = cardId;
        this.boardScreen = boardScreen;
        this.columnId = columnId;
        refreshBoardWhileOpen(projectId);
    }

    @Override
    public void initGui() {
        CardView card = KanbanClientState.findCard(cardId);
        boolean initialized = titleField != null;
        String title = initialized ? titleField.getText() : "";
        String description = initialized ? descriptionEditor.getText() : "";
        panelWidth = Math.min(width - 24, 520);
        panelLeft = (width - panelWidth) / 2;
        titleField = new GuiTextField(fontRendererObj, panelLeft, 19, panelWidth - 24, 18);
        titleField.setMaxStringLength(64);
        titleField.setText(title);
        titleField.setFocused(cardId == null && !initialized);
        int lines = Math.max(1, Math.min(4, (height - 250) / 18));
        descriptionEditor = new GuiMultilineEditor(fontRendererObj, panelLeft, 74, panelWidth, lines);
        descriptionEditor.setText(description);
        editorBottom = 74 + lines * 18 + 2;
        if (!loaded) load(card);

        buttonList.clear();
        buttonList.add(new GuiButton(ICON, panelLeft + panelWidth - 20, 18, 20, 20, ""));
        int third = (panelWidth - 8) / 3;
        buttonList.add(new GuiButton(TYPE, panelLeft, 41, third, 20, ""));
        buttonList.add(new GuiButton(PRIORITY, panelLeft + third + 4, 41, third, 20, ""));
        buttonList.add(new GuiButton(COLUMN, panelLeft + 2 * (third + 4), 41, panelWidth - 2 * (third + 4), 20, ""));
        buttonList.add(new GuiButton(SAVE, width / 2 - 150, height - 53, 65, 20, cardId == null ? "Create" : "Save"));
        buttonList.add(new GuiButton(BACK, width / 2 - 80, height - 53, 65, 20, "Board"));
        if (cardId != null) {
            buttonList.add(new GuiButton(PIN, width / 2 - 10, height - 53, 80, 20, "Pin to HUD"));
            buttonList.add(new GuiButton(DELETE, width / 2 + 75, height - 53, 80, 20, "Delete card"));
            buttonList.add(new GuiButton(REGENERATE, width / 2 - 55, height - 28, 110, 20, "Regenerate all"));
            taskField = new GuiTextField(fontRendererObj, panelLeft, editorBottom + 3, panelWidth - 75, 18);
            taskField.setMaxStringLength(128);
            buttonList.add(new GuiButton(ADD_TASK, panelLeft + panelWidth - 70, editorBottom + 2, 70, 20, "Add task"));
            int row = editorBottom + 25;
            buttonList.add(new GuiButton(ADD_REQUIREMENT, panelLeft, row, 70, 20, "Add item"));
            buttonList.add(new GuiButton(ASSIGNEES, panelLeft + 74, row, 70, 20, "Assignees"));
            buttonList.add(new GuiButton(COMMENTS, panelLeft + 148, row, 85, 20, "Comments"));
            buttonList.add(
                new GuiButton(STRATEGY, panelLeft + 237, row, Math.max(60, panelWidth - 237), 20, strategyLabel()));
            checklist = new GuiChecklistPanel(
                mc,
                projectId,
                cardId,
                panelLeft,
                editorBottom + 50,
                panelWidth,
                height - 74,
                new GuiChecklistPanel.RecipeAction() {

                    @Override
                    public void open(RequirementView row) {
                        mc.displayGuiScreen(new GuiRecipePicker(projectId, cardId, row, GuiCardDetail.this));
                    }
                });
        }
        refreshButtons();
    }

    /** Fills the editable fields from the card once it is known, so Save never overwrites it with defaults. */
    private void load(CardView card) {
        if (cardId == null) {
            loaded = true;
            return;
        }
        if (card == null) return;
        titleField.setText(card.getTitle());
        descriptionEditor.setText(card.getDescription());
        columnId = card.getColumnId();
        typeId = card.getTypeId();
        priority = card.getPriority();
        icon = card.getIcon();
        loaded = true;
    }

    private void refreshButtons() {
        CardView card = KanbanClientState.findCard(cardId);
        BoardSettings settings = settings();
        for (GuiButton button : buttonList) {
            if (button.id == SAVE) button.enabled = loaded;
            else if (button.id == PIN) {
                button.enabled = card != null;
                button.displayString = KanbanClientState.isCardPinned(projectId, cardId) ? "Unpin HUD" : "Pin to HUD";
            } else if (button.id == REGENERATE) {
                button.enabled = card != null && !card.getRequirements()
                    .isEmpty() && !BreakdownJobs.isBusy(cardId);
            } else if (button.id == TYPE) {
                CardType type = findType(settings, typeId);
                button.displayString = "Type: " + (type == null ? "None" : type.getName());
            } else if (button.id == PRIORITY) {
                button.displayString = "Priority: " + priority.getLabel();
            } else if (button.id == COLUMN) {
                button.displayString = "Column: " + columnName(settings);
            } else if (button.id == COMMENTS) {
                button.displayString = "Comments (" + (card == null ? 0
                    : card.getComments()
                        .size())
                    + ")";
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        BoardSettings settings = settings();
        if (button.id == SAVE) {
            if (!loaded) return;
            String title = titleField.getText(), description = descriptionEditor.getText();
            if (cardId == null) KanbanNetwork.CHANNEL
                .sendToServer(new C2SCreateCard(projectId, title, description, columnId, typeId, priority, icon));
            else KanbanNetwork.CHANNEL.sendToServer(
                new C2SUpdateCard(projectId, cardId, title, description, columnId, typeId, priority, icon));
            mc.displayGuiScreen(boardScreen == null ? new GuiKanbanBoard(projectId) : boardScreen);
        } else if (button.id == BACK) goBack();
        else if (button.id == TYPE) typeId = nextType(settings);
        else if (button.id == PRIORITY) priority = priority.next();
        else if (button.id == COLUMN) columnId = nextColumn(settings);
        else if (button.id == ICON) {
            mc.displayGuiScreen(new GuiItemPicker(this, "Choose a card icon", new GuiItemPicker.IconChoice() {

                @Override
                public void chosen(ItemKey chosen) {
                    icon = chosen;
                }
            }));
        } else if (button.id == PIN) {
            if (KanbanClientState.isCardPinned(projectId, cardId)) KanbanClientState.clearPinnedCard();
            else KanbanClientState.pinCard(projectId, KanbanClientState.findCard(cardId));
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
        } else if (button.id == ADD_TASK) addTask();
        else if (button.id == ADD_REQUIREMENT) mc.displayGuiScreen(new GuiItemPicker(projectId, cardId, this));
        else if (button.id == ASSIGNEES) mc.displayGuiScreen(new GuiCardAssignees(projectId, cardId, this));
        else if (button.id == COMMENTS) mc.displayGuiScreen(new GuiCardComments(projectId, cardId, this));
        else if (button.id == REGENERATE) BreakdownJobs.regenerateAll(projectId, cardId);
        else if (button.id == STRATEGY) {
            BreakdownJobs.setStrategy(
                BreakdownJobs.getStrategy()
                    .next());
            button.displayString = strategyLabel();
        }
        refreshButtons();
    }

    private void addTask() {
        String text = taskField.getText()
            .trim();
        if (text.isEmpty()) return;
        KanbanNetwork.CHANNEL.sendToServer(new C2SAddTask(projectId, cardId, text));
        taskField.setText("");
    }

    private UUID nextType(BoardSettings settings) {
        List<CardType> types = settings.getTypes();
        if (types.isEmpty()) return null;
        for (int i = 0; i < types.size(); i++) if (types.get(i)
            .getId()
            .equals(typeId))
            return i + 1 < types.size() ? types.get(i + 1)
                .getId() : null;
        return types.get(0)
            .getId();
    }

    private UUID nextColumn(BoardSettings settings) {
        List<BoardColumn> columns = settings.getColumns();
        UUID current = columnId == null ? settings.firstColumn() : columnId;
        for (int i = 0; i < columns.size(); i++) if (columns.get(i)
            .getId()
            .equals(current))
            return columns.get((i + 1) % columns.size())
                .getId();
        return settings.firstColumn();
    }

    private String columnName(BoardSettings settings) {
        UUID current = columnId == null ? settings.firstColumn() : columnId;
        for (BoardColumn column : settings.getColumns()) if (column.getId()
            .equals(current)) return column.getName();
        return "?";
    }

    private static CardType findType(BoardSettings settings, UUID id) {
        if (id == null) return null;
        for (CardType type : settings.getTypes()) if (type.getId()
            .equals(id)) return type;
        return null;
    }

    private BoardSettings settings() {
        BoardSnapshot board = KanbanClientState.getBoard();
        return board == null ? BoardSettings.defaults() : board.getSettings();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!loaded) load(KanbanClientState.findCard(cardId));
        titleField.updateCursorCounter();
        if (taskField != null) taskField.updateCursorCounter();
        if (checklist != null) checklist.refresh();
        refreshButtons();
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        titleField.mouseClicked(x, y, button);
        if (taskField != null) taskField.mouseClicked(x, y, button);
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
        if (taskField != null && taskField.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) addTask();
            else taskField.textboxKeyTyped(c, key);
            return;
        }
        if (checklist != null && checklist.keyTyped(c, key)) return;
        if (!titleField.textboxKeyTyped(c, key) && !descriptionEditor.keyTyped(c, key)) super.keyTyped(c, key);
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        drawDefaultBackground();
        CardView card = KanbanClientState.findCard(cardId);
        String header = card == null ? "New card"
            : "#" + card.getNumber()
                + "  §7created "
                + (card.getCreatedAt() > 0 ? TimeText.ago(card.getCreatedAt(), System.currentTimeMillis()) : "earlier")
                + (card.getCreatorName()
                    .isEmpty() ? "" : " by " + card.getCreatorName());
        drawCenteredString(fontRendererObj, header, width / 2, 6, 0xFFFFFF);
        titleField.drawTextBox();
        drawString(fontRendererObj, "Description", panelLeft, 64, 0xAAAAAA);
        if (card != null) {
            String assigned = "Assigned: " + KanbanClientState.assigneeNames(card);
            String trimmed = fontRendererObj.trimStringToWidth(assigned, panelWidth - 80);
            drawString(
                fontRendererObj,
                trimmed,
                panelLeft + panelWidth - fontRendererObj.getStringWidth(trimmed),
                64,
                0xAAAAAA);
        }
        descriptionEditor.drawTextBoxes();
        if (cardId != null) {
            taskField.drawTextBox();
            if (taskField.getText()
                .isEmpty() && !taskField.isFocused())
                drawString(
                    fontRendererObj,
                    "New task, e.g. \"wire up the boiler\"",
                    panelLeft + 4,
                    editorBottom + 8,
                    0x777777);
            checklist.draw(x, y);
        } else drawString(
            fontRendererObj,
            "Create the card, then add tasks, items, assignees and comments.",
            panelLeft,
            editorBottom + 8,
            0xAAAAAA);
        String breakdown = BreakdownJobs.status();
        String result = KanbanClientState.getResultMessage();
        if (!breakdown.isEmpty()) drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(breakdown, width - 20),
            width / 2,
            height - 70,
            0xFFCC66);
        else if (!result.isEmpty()) drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(result, width - 20),
            width / 2,
            height - 70,
            KanbanClientState.isResultSuccess() ? 0x55FF55 : 0xFF5555);
        super.drawScreen(x, y, partialTicks);
        ItemStack iconStack = GuiKanbanBoard.iconStack(icon);
        if (iconStack != null) {
            render.renderItemAndEffectIntoGUI(
                fontRendererObj,
                mc.getTextureManager(),
                iconStack,
                panelLeft + panelWidth - 18,
                20);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
        } else drawCenteredString(fontRendererObj, "?", panelLeft + panelWidth - 10, 24, 0x888888);
        List<String> tooltip = new ArrayList<String>();
        if (checklist != null) tooltip = checklist.tooltip(x, y);
        if (tooltip.isEmpty() && card != null && y >= 2 && y < 16) {
            tooltip = new ArrayList<String>();
            tooltip.add("Created " + TimeText.full(card.getCreatedAt()));
        }
        if (tooltip.isEmpty() && x >= panelLeft + panelWidth - 20 && x < panelLeft + panelWidth && y >= 18 && y < 38) {
            tooltip = new ArrayList<String>();
            tooltip.add(icon == null ? "Choose a card icon" : "Icon: " + MaterialDisplay.name(icon));
        }
        if (!tooltip.isEmpty()) drawHoveringText(tooltip, x, y, fontRendererObj);
    }

    private static String strategyLabel() {
        return "Breakdown: " + BreakdownJobs.getStrategy()
            .getLabel();
    }
}
