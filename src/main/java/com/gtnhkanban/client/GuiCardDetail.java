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
import com.gtnhkanban.planner.RecipePreference;
import com.gtnhkanban.service.BoardValidator;

public final class GuiCardDetail extends GuiKanbanScreen {

    private static final int SAVE = 1, BACK = 2, ADD_REQUIREMENT = 4, PIN = 5, DELETE = 6, ASSIGNEES = 7,
        REGENERATE = 9, ICON = 13, COMMENTS = 14, ADD_TASK = 15;
    private final UUID projectId, cardId;
    private final GuiScreen boardScreen;
    private final RenderItem render = new RenderItem();
    private GuiTextField titleField, taskField;
    private GuiMultilineEditor descriptionEditor;
    private GuiChecklistPanel checklist;
    private int panelLeft, panelWidth, editorBottom, strategyRow;
    private final List<GuiDropdown> dropdowns = new ArrayList<GuiDropdown>();

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
        titleField.setMaxStringLength(BoardValidator.MAX_CARD_TITLE_LENGTH);
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
            strategyRow = row;
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
                        // Shift opens the old list, which also has auto breakdown and removing the materials.
                        if (isShiftKeyDown())
                            mc.displayGuiScreen(new GuiRecipePicker(projectId, cardId, row, GuiCardDetail.this));
                        else NeiRecipeChooser.open(projectId, cardId, row, GuiCardDetail.this);
                    }
                });
        }
        buildDropdowns();
        refreshButtons();
    }

    /**
     * Type, priority and column dropdowns under the title, and (for saved cards) the breakdown recipe preference.
     * Rebuilt whenever the card's fields are (re)loaded so their selections match.
     */
    private void buildDropdowns() {
        dropdowns.clear();
        final BoardSettings settings = settings();
        int third = (panelWidth - 8) / 3;

        final List<CardType> types = settings.getTypes();
        List<String> typeNames = new ArrayList<String>();
        typeNames.add("None");
        int typeIndex = 0;
        for (int i = 0; i < types.size(); i++) {
            typeNames.add(
                types.get(i)
                    .getName());
            if (types.get(i)
                .getId()
                .equals(typeId)) typeIndex = i + 1;
        }
        dropdowns.add(new GuiDropdown(panelLeft, 41, third, "Type", typeNames, typeIndex, new GuiDropdown.Choice() {

            @Override
            public void chosen(int index) {
                typeId = index == 0 ? null
                    : types.get(index - 1)
                        .getId();
            }
        }));

        List<String> priorities = new ArrayList<String>();
        for (Priority value : Priority.values()) priorities.add(value.getLabel());
        dropdowns.add(
            new GuiDropdown(
                panelLeft + third + 4,
                41,
                third,
                "Priority",
                priorities,
                priority.ordinal(),
                new GuiDropdown.Choice() {

                    @Override
                    public void chosen(int index) {
                        priority = Priority.values()[index];
                    }
                }));

        final List<BoardColumn> columns = settings.getColumns();
        List<String> columnNames = new ArrayList<String>();
        UUID current = columnId == null ? settings.firstColumn() : columnId;
        int columnIndex = 0;
        for (int i = 0; i < columns.size(); i++) {
            columnNames.add(
                columns.get(i)
                    .getName());
            if (columns.get(i)
                .getId()
                .equals(current)) columnIndex = i;
        }
        dropdowns.add(
            new GuiDropdown(
                panelLeft + 2 * (third + 4),
                41,
                panelWidth - 2 * (third + 4),
                "Column",
                columnNames,
                columnIndex,
                new GuiDropdown.Choice() {

                    @Override
                    public void chosen(int index) {
                        columnId = columns.get(index)
                            .getId();
                    }
                }));

        if (cardId != null) {
            final List<String> recipeTypes = new ArrayList<String>();
            recipeTypes.add("Crafting table (default)");
            recipeTypes.addAll(NeiRecipeTypes.names());
            String preferred = BreakdownJobs.getPreference()
                .getRecipeType();
            int recipeIndex = preferred == null ? 0 : Math.max(0, recipeTypes.indexOf(preferred));
            dropdowns.add(
                new GuiDropdown(
                    panelLeft + 237,
                    strategyRow,
                    Math.max(60, panelWidth - 237),
                    "Recipe",
                    recipeTypes,
                    recipeIndex,
                    new GuiDropdown.Choice() {

                        @Override
                        public void chosen(int index) {
                            BreakdownJobs.setPreference(
                                index == 0 ? RecipePreference.CRAFTING_TABLE
                                    : RecipePreference.of(recipeTypes.get(index)));
                        }
                    }));
        }
    }

    private GuiDropdown openDropdown() {
        for (GuiDropdown dropdown : dropdowns) if (dropdown.isOpen()) return dropdown;
        return null;
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
        buildDropdowns();
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
        refreshButtons();
    }

    private void addTask() {
        String text = taskField.getText()
            .trim();
        if (text.isEmpty()) return;
        KanbanNetwork.CHANNEL.sendToServer(new C2SAddTask(projectId, cardId, text));
        taskField.setText("");
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
        // An open list sits on top of everything, so it gets the click first; so does a dropdown being opened.
        GuiDropdown open = openDropdown();
        if (open != null) {
            open.mouseClicked(x, y, button, height);
            return;
        }
        for (GuiDropdown dropdown : dropdowns) if (dropdown.mouseClicked(x, y, button, height)) return;
        super.mouseClicked(x, y, button);
        titleField.mouseClicked(x, y, button);
        if (taskField != null) taskField.mouseClicked(x, y, button);
        descriptionEditor.mouseClicked(x, y, button);
        if (checklist != null) checklist.click(x, y, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        GuiDropdown open = openDropdown();
        if (open != null) {
            open.scrolled(Mouse.getEventDWheel());
            return;
        }
        if (checklist != null) checklist.scroll(
            Mouse.getEventDWheel(),
            Mouse.getEventX() * width / mc.displayWidth,
            height - Mouse.getEventY() * height / mc.displayHeight - 1);
    }

    @Override
    protected void keyTyped(char c, int key) {
        GuiDropdown open = openDropdown();
        if (open != null && open.keyTyped(c, key)) return;
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
        GuiDropdown open = openDropdown();
        int buttonMouseX = open == null ? x : -1;
        for (GuiDropdown dropdown : dropdowns) dropdown.draw(mc, buttonMouseX, y);
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
        if (open != null) {
            open.drawOverlay(mc, x, y, height);
            return;
        }
        if (!tooltip.isEmpty()) drawHoveringText(tooltip, x, y, fontRendererObj);
    }
}
