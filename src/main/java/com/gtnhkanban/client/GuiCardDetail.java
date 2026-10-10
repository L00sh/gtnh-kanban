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
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.CardLink;
import com.gtnhkanban.model.CardType;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.Priority;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SAddTask;
import com.gtnhkanban.network.message.C2SCreateCard;
import com.gtnhkanban.network.message.C2SDeleteCard;
import com.gtnhkanban.network.message.C2SSetCardLink;
import com.gtnhkanban.network.message.C2SUpdateCard;
import com.gtnhkanban.service.BoardValidator;

/**
 * A card, in three columns: its details (title, type, priority, column, description, tasks, assignees) on the left,
 * its items in the middle, and what it depends on, what blocks it and its comments on the right.
 */
public final class GuiCardDetail extends GuiKanbanScreen {

    private static final int SAVE = 1, BACK = 2, ADD_REQUIREMENT = 4, PIN = 5, DELETE = 6, ASSIGNEES = 7,
        REGENERATE = 9, ICON = 13, ADD_TASK = 15, EDIT_DEPENDS = 16, EDIT_BLOCKERS = 17;
    private static final int MARGIN = 8, GAP = 10, TOP = 20, LINK_ROW = 10, MAX_LINK_ROWS = 4, MAX_ASSIGNEE_ROWS = 5;
    private final UUID projectId, cardId;
    private final GuiScreen boardScreen;
    private final RenderItem render = new RenderItem();
    private GuiTextField titleField, taskField;
    private GuiMultilineEditor descriptionEditor;
    private GuiChecklistPanel tasks, items;
    private GuiCommentsPanel comments;
    private final List<GuiDropdown> dropdowns = new ArrayList<GuiDropdown>();
    private int leftX, leftW, midX, midW, rightX, rightW, contentBottom;
    private int descriptionTop, assigneesTop, dependsTop, blockersTop;

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
        String draft = comments == null ? "" : comments.draft();

        // The items list needs room for its quantity, Apply, Recipe and remove controls.
        int available = width - 2 * MARGIN - 2 * GAP;
        midW = Math.min(available - 240, Math.max(300, available * 2 / 5));
        leftW = (available - midW) / 2;
        rightW = available - midW - leftW;
        leftX = MARGIN;
        midX = leftX + leftW + GAP;
        rightX = midX + midW + GAP;
        contentBottom = height - 40;

        titleField = new GuiTextField(fontRendererObj, leftX, TOP, leftW - 24, 18);
        titleField.setMaxStringLength(BoardValidator.MAX_CARD_TITLE_LENGTH);
        titleField.setText(title);
        titleField.setFocused(cardId == null && !initialized);
        descriptionTop = TOP + 56;
        int descriptionHeight = Math.max(38, Math.min(98, (contentBottom - TOP) / 4));
        descriptionEditor = new GuiMultilineEditor(fontRendererObj, leftX, descriptionTop, leftW, descriptionHeight);
        descriptionEditor.setText(description);
        if (!loaded) load(card);
        descriptionEditor.setReadOnly(!canEditDescription(card));

        buttonList.clear();
        buttonList.add(new GuiButton(ICON, leftX + leftW - 20, TOP - 1, 20, 20, ""));
        int barX = width / 2 - 160;
        buttonList.add(new GuiButton(SAVE, barX, height - 24, 75, 20, cardId == null ? "Create" : "Save"));
        buttonList.add(new GuiButton(BACK, barX + 80, height - 24, 75, 20, "Board"));
        if (cardId != null) {
            buttonList.add(new GuiButton(PIN, barX + 160, height - 24, 75, 20, "Pin to HUD"));
            buttonList.add(new GuiButton(DELETE, barX + 240, height - 24, 80, 20, "Delete card"));

            int row = descriptionTop + descriptionEditor.height() + 5;
            int half = (leftW - 4) / 2;
            buttonList.add(new GuiButton(ADD_REQUIREMENT, leftX, row, half, 20, "Add item"));
            buttonList.add(new GuiButton(REGENERATE, leftX + half + 4, row, leftW - half - 4, 20, "Regenerate all"));
            int taskRow = row + 36;
            taskField = new GuiTextField(fontRendererObj, leftX, taskRow + 1, leftW - 44, 18);
            taskField.setMaxStringLength(128);
            buttonList.add(new GuiButton(ADD_TASK, leftX + leftW - 40, taskRow, 40, 20, "Add"));

            int assigneeRows = Math.min(MAX_ASSIGNEE_ROWS, card == null ? 0 : assigneeCount(card));
            assigneesTop = contentBottom - 20 - (assigneeRows > 0 ? 14 + assigneeRows * LINK_ROW : 0);
            buttonList.add(new GuiButton(ASSIGNEES, leftX, contentBottom - 20, leftW, 20, "Assignees"));
            tasks = new GuiChecklistPanel(
                mc,
                projectId,
                cardId,
                leftX,
                taskRow + 24,
                leftW,
                assigneesTop - 6,
                true,
                null);
            items = new GuiChecklistPanel(
                mc,
                projectId,
                cardId,
                midX,
                TOP + 12,
                midW,
                contentBottom,
                false,
                new GuiChecklistPanel.RecipeAction() {

                    @Override
                    public void open(RequirementView row) {
                        // Shift opens the old list, which also has auto breakdown and removing the materials.
                        if (isShiftKeyDown())
                            mc.displayGuiScreen(new GuiRecipePicker(projectId, cardId, row, GuiCardDetail.this));
                        else NeiRecipeChooser.open(projectId, cardId, row, GuiCardDetail.this);
                    }
                });

            blockersTop = TOP;
            buttonList.add(new GuiButton(EDIT_BLOCKERS, rightX + rightW - 40, blockersTop - 4, 40, 16, "Edit"));
            dependsTop = blockersTop + linkSectionHeight(card, CardLink.BLOCKED_BY);
            buttonList.add(new GuiButton(EDIT_DEPENDS, rightX + rightW - 40, dependsTop - 4, 40, 16, "Edit"));
            int commentsTop = dependsTop + linkSectionHeight(card, CardLink.DEPENDS_ON) + 12;
            comments = new GuiCommentsPanel(mc, projectId, cardId, rightX, commentsTop, rightW, contentBottom, draft);
        }
        buildDropdowns();
        refreshButtons();
    }

    /** Header, rows (at most a few, then "and n more") and spacing below. */
    private int linkSectionHeight(CardView card, CardLink link) {
        int count = card == null ? 0
            : card.getLinks(link)
                .size();
        int rows = count == 0 ? 1 : Math.min(count, MAX_LINK_ROWS) + (count > MAX_LINK_ROWS ? 1 : 0);
        return 14 + rows * LINK_ROW + 8;
    }

    private int assigneeCount(CardView card) {
        return card.getAssigneeIds()
            .size();
    }

    /** New cards, the card's creator and the board owner may change the description. */
    private boolean canEditDescription(CardView card) {
        if (cardId == null) return true;
        BoardSnapshot board = KanbanClientState.getBoard(projectId);
        if (board != null && board.getProject()
            .isActorIsOwner()) return true;
        return card != null && mc.thePlayer != null
            && mc.thePlayer.getUniqueID()
                .equals(card.getCreatorId());
    }

    /** Type, priority and column, under the title. Rebuilt when the card's fields are loaded so they match. */
    private void buildDropdowns() {
        dropdowns.clear();
        final BoardSettings settings = settings();
        int third = (leftW - 8) / 3;

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
        dropdowns.add(new GuiDropdown(leftX, TOP + 22, third, "Type", typeNames, typeIndex, new GuiDropdown.Choice() {

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
                leftX + third + 4,
                TOP + 22,
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
                leftX + 2 * (third + 4),
                TOP + 22,
                leftW - 2 * (third + 4),
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
        for (GuiButton button : buttonList) {
            if (button.id == SAVE) button.enabled = loaded;
            else if (button.id == PIN) {
                button.enabled = card != null;
                button.displayString = KanbanClientState.isCardPinned(projectId, cardId) ? "Unpin HUD" : "Pin to HUD";
            } else if (button.id == REGENERATE) {
                button.enabled = card != null && !card.getRequirements()
                    .isEmpty() && !BreakdownJobs.isBusy(cardId);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        CardView card = KanbanClientState.findCard(cardId);
        if (button.id == SAVE) {
            if (!loaded) return;
            String title = titleField.getText();
            // A description the player may not edit is sent back unchanged.
            String description = canEditDescription(card) || card == null ? descriptionEditor.getText()
                : card.getDescription();
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
            else KanbanClientState.pinCard(projectId, card);
        } else if (button.id == DELETE) {
            String name = card == null ? "this card" : "#" + card.getNumber() + " " + card.getTitle();
            mc.displayGuiScreen(
                new GuiConfirmAction(
                    boardScreen == null ? new GuiKanbanBoard(projectId) : boardScreen,
                    fontRendererObj.trimStringToWidth("Delete " + name, width - 60) + "?",
                    new Runnable() {

                        @Override
                        public void run() {
                            KanbanNetwork.CHANNEL.sendToServer(new C2SDeleteCard(projectId, cardId));
                        }
                    }));
        } else if (button.id == ADD_TASK) addTask();
        else if (button.id == ADD_REQUIREMENT) mc.displayGuiScreen(new GuiItemPicker(projectId, cardId, this));
        else if (button.id == ASSIGNEES) mc.displayGuiScreen(new GuiCardAssignees(projectId, cardId, this));
        else if (button.id == REGENERATE) BreakdownJobs.regenerateAll(projectId, cardId);
        else if (button.id == EDIT_DEPENDS)
            mc.displayGuiScreen(new GuiCardLinkPicker(projectId, cardId, CardLink.DEPENDS_ON, this));
        else if (button.id == EDIT_BLOCKERS)
            mc.displayGuiScreen(new GuiCardLinkPicker(projectId, cardId, CardLink.BLOCKED_BY, this));
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
        CardView card = KanbanClientState.findCard(cardId);
        if (!loaded) load(card);
        titleField.updateCursorCounter();
        if (taskField != null) taskField.updateCursorCounter();
        if (tasks != null) tasks.refresh();
        if (items != null) items.refresh();
        if (comments != null) comments.update();
        // Link lists and assignees change height as they change; lay the columns out again when they do.
        if (card != null && cardId != null
            && (dependsTop != blockersTop + linkSectionHeight(card, CardLink.BLOCKED_BY)
                || assigneesTop != contentBottom - 20
                    - (Math.min(MAX_ASSIGNEE_ROWS, assigneeCount(card)) > 0
                        ? 14 + Math.min(MAX_ASSIGNEE_ROWS, assigneeCount(card)) * LINK_ROW
                        : 0)
                || comments == null))
            initGui();
        descriptionEditor.setReadOnly(!canEditDescription(card));
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
        if (tasks != null) tasks.click(x, y, button);
        if (items != null) items.click(x, y, button);
        if (comments != null) comments.mouseClicked(x, y, button);
        if (button == 0) clickLinkRow(x, y);
    }

    /** The x beside a linked card removes the link. */
    private void clickLinkRow(int x, int y) {
        CardView card = KanbanClientState.findCard(cardId);
        if (card == null || x < rightX + rightW - 10 || x >= rightX + rightW) return;
        for (CardLink link : CardLink.values()) {
            int top = (link == CardLink.BLOCKED_BY ? blockersTop : dependsTop) + 14;
            List<UUID> linked = card.getLinks(link);
            int row = (y - top) / LINK_ROW;
            if (y >= top && row < Math.min(MAX_LINK_ROWS, linked.size()))
                KanbanNetwork.CHANNEL.sendToServer(new C2SSetCardLink(projectId, cardId, linked.get(row), link, false));
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        GuiDropdown open = openDropdown();
        if (open != null) {
            open.scrolled(wheel);
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        descriptionEditor.scroll(wheel, mouseX, mouseY);
        if (tasks != null) tasks.scroll(wheel, mouseX, mouseY);
        if (items != null) items.scroll(wheel, mouseX, mouseY);
        if (comments != null) comments.scroll(wheel, mouseX, mouseY);
    }

    @Override
    protected void keyTyped(char c, int key) {
        GuiDropdown open = openDropdown();
        if (open != null && open.keyTyped(c, key)) return;
        if (handleEscape(key)) return;
        if (comments != null && comments.keyTyped(c, key)) return;
        if (taskField != null && taskField.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) addTask();
            else taskField.textboxKeyTyped(c, key);
            return;
        }
        if (items != null && items.keyTyped(c, key)) return;
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

        // Left: details.
        titleField.drawTextBox();
        boolean locked = !canEditDescription(card);
        drawString(
            fontRendererObj,
            locked ? "Description §8(creator or owner only)" : "Description",
            leftX,
            descriptionTop - 10,
            0xAAAAAA);
        descriptionEditor.draw();
        List<String> tooltip = new ArrayList<String>();
        if (cardId != null) {
            drawString(fontRendererObj, "Tasks", leftX, taskField.yPosition - 11, 0xAAAAAA);
            taskField.drawTextBox();
            if (taskField.getText()
                .isEmpty() && !taskField.isFocused())
                drawString(fontRendererObj, "New task...", leftX + 4, taskField.yPosition + 5, 0x777777);
            tasks.draw(x, y);
            drawAssignees(card);

            // Middle: items.
            drawString(fontRendererObj, "Items", midX, TOP, 0xAAAAAA);
            items.draw(x, y);

            // Right: links and comments.
            drawLinks(card, CardLink.BLOCKED_BY, blockersTop, x, y, tooltip);
            drawLinks(card, CardLink.DEPENDS_ON, dependsTop, x, y, tooltip);
            drawString(
                fontRendererObj,
                "Comments (" + (card == null ? 0
                    : card.getComments()
                        .size())
                    + ")",
                rightX,
                dependsTop + linkSectionHeight(card, CardLink.DEPENDS_ON),
                0xAAAAAA);
            String commentTime = comments.draw(x, y);
            if (commentTime != null) tooltip.add(commentTime);
        } else {
            int hintY = descriptionTop + descriptionEditor.height() + 8;
            for (Object line : fontRendererObj
                .listFormattedStringToWidth("Create the card, then add tasks, items, assignees and comments.", leftW))
                drawString(fontRendererObj, (String) line, leftX, (hintY += 10) - 10, 0xAAAAAA);
        }

        String breakdown = BreakdownJobs.status();
        String result = KanbanClientState.getResultMessage();
        if (!breakdown.isEmpty()) drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(breakdown, width - 20),
            width / 2,
            height - 36,
            0xFFCC66);
        else if (!result.isEmpty()) drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(result, width - 20),
            width / 2,
            height - 36,
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
                leftX + leftW - 18,
                TOP + 1);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
        } else drawCenteredString(fontRendererObj, "?", leftX + leftW - 10, TOP + 5, 0x888888);

        if (tooltip.isEmpty() && items != null) tooltip = items.tooltip(x, y);
        if (tooltip.isEmpty() && tasks != null) tooltip = tasks.tooltip(x, y);
        if (tooltip.isEmpty() && card != null && y >= 2 && y < 16)
            tooltip.add("Created " + TimeText.full(card.getCreatedAt()));
        if (tooltip.isEmpty() && x >= leftX + leftW - 20 && x < leftX + leftW && y >= TOP - 1 && y < TOP + 19)
            tooltip.add(icon == null ? "Choose a card icon" : "Icon: " + MaterialDisplay.name(icon));
        if (open != null) {
            open.drawOverlay(mc, x, y, height);
            return;
        }
        if (!tooltip.isEmpty()) drawHoveringText(tooltip, x, y, fontRendererObj);
    }

    /** The card's assignees with their heads, above the Assignees button. */
    private void drawAssignees(CardView card) {
        if (card == null || card.getAssigneeIds()
            .isEmpty()) return;
        drawString(fontRendererObj, "Assigned", leftX, assigneesTop, 0xAAAAAA);
        BoardSnapshot board = KanbanClientState.getBoard(projectId);
        int row = 0;
        for (UUID id : card.getAssigneeIds()) {
            int rowY = assigneesTop + 12 + row * LINK_ROW;
            if (row == MAX_ASSIGNEE_ROWS - 1 && card.getAssigneeIds()
                .size() > MAX_ASSIGNEE_ROWS) {
                drawString(
                    fontRendererObj,
                    "and " + (card.getAssigneeIds()
                        .size() - row) + " more",
                    leftX + 12,
                    rowY,
                    0x888888);
                break;
            }
            String name = memberName(board, id);
            PlayerHeads.draw(mc, id, name, leftX, rowY, 8);
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(name, leftW - 12),
                leftX + 12,
                rowY,
                0xFFFFFF);
            row++;
        }
    }

    private static String memberName(BoardSnapshot board, UUID id) {
        if (board != null) for (MemberSummary member : board.getMembers()) if (member.getPlayerId()
            .equals(id)) return member.getDisplayName();
        return "Former member";
    }

    /** One links section: its heading, the linked cards (red blockers, blue dependencies) and an x to unlink each. */
    private void drawLinks(CardView card, CardLink link, int top, int mouseX, int mouseY, List<String> tooltip) {
        boolean blockers = link == CardLink.BLOCKED_BY;
        List<UUID> linked = card == null ? new ArrayList<UUID>() : card.getLinks(link);
        int color = blockers ? 0xFF6666 : 0x66AAFF;
        drawString(
            fontRendererObj,
            (blockers ? "Blocked by" : "Depends on") + " (" + linked.size() + ")",
            rightX,
            top,
            color);
        if (linked.isEmpty()) {
            drawString(
                fontRendererObj,
                blockers ? "Nothing is blocking this." : "No cards need finishing first.",
                rightX + 4,
                top + 14,
                0x777777);
            return;
        }
        BoardSnapshot board = KanbanClientState.getBoard(projectId);
        UUID done = board == null ? null
            : board.getSettings()
                .doneColumn();
        for (int row = 0; row < Math.min(MAX_LINK_ROWS, linked.size()); row++) {
            CardView other = KanbanClientState.getBoard(projectId) == null ? null : find(board, linked.get(row));
            int rowY = top + 14 + row * LINK_ROW;
            boolean finished = other != null && other.getColumnId()
                .equals(done);
            String label = other == null ? "?"
                : (finished ? "§a✔ " : "") + "#" + other.getNumber() + " " + other.getTitle();
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(label, rightW - 16),
                rightX + 4,
                rowY,
                finished ? 0x88CC88 : color);
            drawString(fontRendererObj, "x", rightX + rightW - 7, rowY, 0xFF6666);
            if (mouseY >= rowY && mouseY < rowY + LINK_ROW && mouseX >= rightX && mouseX < rightX + rightW) {
                if (mouseX >= rightX + rightW - 10) tooltip.add("Remove");
                else if (other != null) tooltip.add("#" + other.getNumber() + " " + other.getTitle());
            }
        }
        if (linked.size() > MAX_LINK_ROWS) drawString(
            fontRendererObj,
            "and " + (linked.size() - MAX_LINK_ROWS) + " more (Edit)",
            rightX + 4,
            top + 14 + MAX_LINK_ROWS * LINK_ROW,
            0x888888);
    }

    private static CardView find(BoardSnapshot board, UUID id) {
        for (CardView card : board.getCards()) if (card.getId()
            .equals(id)) return card;
        return null;
    }
}
