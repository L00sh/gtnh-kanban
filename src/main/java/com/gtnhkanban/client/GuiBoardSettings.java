package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Mouse;

import com.gtnhkanban.model.BoardColumn;
import com.gtnhkanban.model.BoardSettings;
import com.gtnhkanban.model.CardType;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SFetchBoard;
import com.gtnhkanban.network.message.C2SSaveSettings;

/**
 * Server-wide board settings: columns (renamed, reordered, added, removed) and card types (named and colored). Nothing
 * is sent until Save. Removing a column moves its cards to the first column; removing a type only clears it from
 * cards.
 */
final class GuiBoardSettings extends GuiKanbanScreen {

    private static final int SAVE = 1, CANCEL = 2, ADD_COLUMN = 3, ADD_TYPE = 4;
    private static final int ROW = 22;
    private static final int MAX_COLUMNS = 12, MAX_TYPES = 32;

    private static final class Entry {

        final UUID id;
        GuiTextField name;
        int color;

        Entry(UUID id, GuiTextField name, int color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }
    }

    private final List<Entry> columns = new ArrayList<Entry>();
    private final List<Entry> types = new ArrayList<Entry>();
    private GuiTextField newColumn, newType;
    private int columnsLeft, typesLeft, panelWidth, columnScroll, typeScroll;
    private boolean loaded, requested;
    private String error = "";

    GuiBoardSettings(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initGui() {
        panelWidth = Math.min(200, (width - 36) / 2);
        columnsLeft = width / 2 - panelWidth - 6;
        typesLeft = width / 2 + 6;
        if (!loaded) load();
        else {
            // Text fields have a fixed width, so rebuild them for the new screen size.
            for (Entry entry : columns) entry.name = field(entry.name.getText(), columnFieldWidth());
            for (Entry entry : types) entry.name = field(entry.name.getText(), typeFieldWidth());
        }
        newColumn = keep(newColumn, columnsLeft, height - 76, panelWidth - 50);
        newType = keep(newType, typesLeft, height - 76, panelWidth - 50);
        buttonList.clear();
        buttonList.add(new GuiButton(ADD_COLUMN, columnsLeft + panelWidth - 46, height - 77, 46, 20, "Add"));
        buttonList.add(new GuiButton(ADD_TYPE, typesLeft + panelWidth - 46, height - 77, 46, 20, "Add"));
        buttonList.add(new GuiButton(SAVE, width / 2 - 85, height - 28, 80, 20, "Save"));
        buttonList.add(new GuiButton(CANCEL, width / 2 + 5, height - 28, 80, 20, "Cancel"));
    }

    private int columnFieldWidth() {
        return panelWidth - 56;
    }

    private int typeFieldWidth() {
        return panelWidth - 38;
    }

    private GuiTextField field(String text, int fieldWidth) {
        GuiTextField field = new GuiTextField(fontRendererObj, 0, -100, fieldWidth, 16);
        field.setMaxStringLength(32);
        field.setText(text);
        return field;
    }

    private GuiTextField keep(GuiTextField previous, int x, int y, int width) {
        GuiTextField field = new GuiTextField(fontRendererObj, x, y, width, 18);
        field.setMaxStringLength(32);
        if (previous != null) field.setText(previous.getText());
        return field;
    }

    private int listTop() {
        return 44;
    }

    private int capacity() {
        return Math.max(1, (height - 84 - listTop()) / ROW);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CANCEL) goBack();
        else if (button.id == SAVE) save();
        else if (button.id == ADD_COLUMN) add(newColumn, columns, MAX_COLUMNS, 0);
        else if (button.id == ADD_TYPE)
            add(newType, types, MAX_TYPES, TypeColors.PALETTE[types.size() % TypeColors.PALETTE.length]);
    }

    private void add(GuiTextField input, List<Entry> list, int maximum, int color) {
        String name = input.getText()
            .trim();
        if (name.isEmpty()) return;
        if (list.size() >= maximum) {
            error = "At most " + maximum + " allowed.";
            return;
        }
        list.add(
            new Entry(UUID.randomUUID(), field(name, list == columns ? columnFieldWidth() : typeFieldWidth()), color));
        input.setText("");
        error = "";
    }

    /**
     * Fills the lists from the server's settings. Until a board (which carries them) has arrived the screen waits with
     * Save disabled: saving the defaults instead would overwrite every project's real columns and types.
     */
    private void load() {
        BoardSettings settings = KanbanClientState.getSettings();
        if (settings == null) return;
        for (BoardColumn column : settings.getColumns())
            columns.add(new Entry(column.getId(), field(column.getName(), columnFieldWidth()), 0));
        for (CardType type : settings.getTypes())
            types.add(new Entry(type.getId(), field(type.getName(), typeFieldWidth()), type.getColor()));
        loaded = true;
    }

    private void save() {
        if (!loaded) return;
        List<BoardColumn> savedColumns = new ArrayList<BoardColumn>();
        for (Entry entry : columns) savedColumns.add(
            new BoardColumn(
                entry.id,
                entry.name.getText()
                    .trim()));
        List<CardType> savedTypes = new ArrayList<CardType>();
        for (Entry entry : types) savedTypes.add(
            new CardType(
                entry.id,
                entry.name.getText()
                    .trim(),
                entry.color));
        if (savedColumns.isEmpty()) {
            error = "Keep at least one column.";
            return;
        }
        KanbanNetwork.CHANNEL.sendToServer(new C2SSaveSettings(savedColumns, savedTypes));
        goBack();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int step = wheel > 0 ? -1 : 1;
        if (mouseX >= typesLeft) typeScroll = clampScroll(typeScroll + step, types.size());
        else columnScroll = clampScroll(columnScroll + step, columns.size());
    }

    private int clampScroll(int value, int size) {
        return Math.max(0, Math.min(value, Math.max(0, size - capacity())));
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        newColumn.mouseClicked(x, y, button);
        newType.mouseClicked(x, y, button);
        for (Entry entry : columns) entry.name.mouseClicked(x, y, button);
        for (Entry entry : types) entry.name.mouseClicked(x, y, button);
        int row = (y - listTop()) / ROW;
        if (y < listTop() || row >= capacity()) return;
        int columnIndex = columnScroll + row;
        if (x >= columnsLeft && x < columnsLeft + panelWidth && columnIndex < columns.size() && button == 0) {
            int right = columnsLeft + panelWidth;
            if (x >= right - 16) {
                if (columns.size() > 1) columns.remove(columnIndex);
                else error = "Keep at least one column.";
            } else if (x >= right - 34 && columnIndex + 1 < columns.size()) {
                columns.add(columnIndex + 1, columns.remove(columnIndex));
            } else if (x >= right - 52 && columnIndex > 0) {
                columns.add(columnIndex - 1, columns.remove(columnIndex));
            }
            columnScroll = clampScroll(columnScroll, columns.size());
        }
        int typeIndex = typeScroll + row;
        if (x >= typesLeft && x < typesLeft + panelWidth && typeIndex < types.size()) {
            Entry entry = types.get(typeIndex);
            if (x < typesLeft + 16) entry.color = TypeColors.cycle(entry.color, button == 1 ? -1 : 1);
            else if (x >= typesLeft + panelWidth - 16 && button == 0) types.remove(typeIndex);
            typeScroll = clampScroll(typeScroll, types.size());
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (handleEscape(key)) return;
        newColumn.textboxKeyTyped(c, key);
        newType.textboxKeyTyped(c, key);
        for (Entry entry : columns) entry.name.textboxKeyTyped(c, key);
        for (Entry entry : types) entry.name.textboxKeyTyped(c, key);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (!loaded) {
            load();
            // Opened from the project list before any board was shown: fetch one to learn the settings.
            if (!loaded && !requested
                && !KanbanClientState.getProjects()
                    .isEmpty()) {
                requested = true;
                KanbanNetwork.CHANNEL.sendToServer(
                    new C2SFetchBoard(
                        KanbanClientState.getProjects()
                            .get(0)
                            .getId()));
            }
        }
        for (GuiButton button : buttonList) if (button.id == SAVE) button.enabled = loaded;
        newColumn.updateCursorCounter();
        newType.updateCursorCounter();
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Board settings (all projects on this server)", width / 2, 8, 0xFFFFFF);
        if (!loaded)
            drawCenteredString(fontRendererObj, "Loading the current settings...", width / 2, height / 2, 0xAAAAAA);
        drawString(fontRendererObj, "Columns, left to right", columnsLeft, 30, 0xFFFFFF);
        drawString(fontRendererObj, "Card types", typesLeft, 30, 0xFFFFFF);
        for (int row = 0; row < capacity(); row++) {
            int rowY = listTop() + row * ROW;
            if (columnScroll + row < columns.size())
                drawColumnRow(columns.get(columnScroll + row), columnScroll + row, rowY, x, y);
            if (typeScroll + row < types.size()) drawTypeRow(types.get(typeScroll + row), rowY, x, y);
        }
        hideOffscreen(columns, columnScroll);
        hideOffscreen(types, typeScroll);
        newColumn.drawTextBox();
        newType.drawTextBox();
        if (newColumn.getText()
            .isEmpty()) drawString(fontRendererObj, "New column", columnsLeft + 4, height - 71, 0x777777);
        if (newType.getText()
            .isEmpty()) drawString(fontRendererObj, "New type", typesLeft + 4, height - 71, 0x777777);
        drawCenteredString(
            fontRendererObj,
            error.isEmpty()
                ? "Removed columns move their cards to the first column. Removed types are cleared from cards."
                : error,
            width / 2,
            height - 48,
            error.isEmpty() ? 0x999999 : 0xFF5555);
        super.drawScreen(x, y, partialTicks);
        if (x >= typesLeft && x < typesLeft + 16 && y >= listTop() && y < listTop() + capacity() * ROW) {
            List<String> tooltip = new ArrayList<String>();
            tooltip.add("Click: next color, right-click: previous");
            drawHoveringText(tooltip, x, y, fontRendererObj);
        }
    }

    private void drawColumnRow(Entry entry, int index, int y, int mouseX, int mouseY) {
        int right = columnsLeft + panelWidth;
        entry.name.xPosition = columnsLeft;
        entry.name.yPosition = y + 2;
        entry.name.setVisible(true);
        entry.name.drawTextBox();
        small(right - 52, y, "↑", index > 0, mouseX, mouseY);
        small(right - 34, y, "↓", index + 1 < columns.size(), mouseX, mouseY);
        small(right - 16, y, "x", columns.size() > 1, mouseX, mouseY);
    }

    private void drawTypeRow(Entry entry, int y, int mouseX, int mouseY) {
        drawRect(typesLeft, y + 2, typesLeft + 14, y + 18, 0xFFFFFFFF);
        drawRect(typesLeft + 1, y + 3, typesLeft + 13, y + 17, 0xFF000000 | entry.color);
        entry.name.xPosition = typesLeft + 18;
        entry.name.yPosition = y + 2;
        entry.name.setVisible(true);
        entry.name.drawTextBox();
        small(typesLeft + panelWidth - 16, y, "x", true, mouseX, mouseY);
    }

    private void small(int x, int y, String label, boolean enabled, int mouseX, int mouseY) {
        GuiButton button = new GuiButton(0, x, y + 1, 16, 18, label);
        button.enabled = enabled;
        button.drawButton(mc, mouseX, mouseY);
    }

    /** Off-screen rows keep their fields but must not receive clicks or keys. */
    private void hideOffscreen(List<Entry> entries, int scroll) {
        for (int i = 0; i < entries.size(); i++) {
            boolean visible = i >= scroll && i < scroll + capacity();
            entries.get(i).name.setVisible(visible);
            if (!visible) {
                entries.get(i).name.setFocused(false);
                entries.get(i).name.yPosition = -100;
            }
        }
    }
}
