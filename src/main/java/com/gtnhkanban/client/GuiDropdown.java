package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.input.Keyboard;

/**
 * A button that opens a list of choices. Long lists scroll with the mouse wheel and can be filtered by typing. The
 * open list is drawn by {@link #drawOverlay} after the rest of the screen so it sits on top.
 */
final class GuiDropdown {

    interface Choice {

        void chosen(int index);
    }

    private static final int ROW = 12;
    private static final int MAX_ROWS = 12;
    /** Lists longer than this show a filter line that typing narrows. */
    private static final int FILTER_AT = 12;

    private final String label;
    private final List<String> options;
    private final Choice choice;
    private final int x, y, width;
    private int selected;
    private boolean open;
    private String filter = "";
    private int scroll;

    GuiDropdown(int x, int y, int width, String label, List<String> options, int selected, Choice choice) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.label = label;
        this.options = new ArrayList<String>(options);
        this.selected = selected;
        this.choice = choice;
    }

    boolean isOpen() {
        return open;
    }

    void setSelected(int index) {
        selected = index;
    }

    /** The closed button. */
    void draw(Minecraft mc, int mouseX, int mouseY) {
        String value = selected >= 0 && selected < options.size() ? options.get(selected) : "";
        String text = label.isEmpty() ? value : label + ": " + value;
        FontRenderer font = mc.fontRenderer;
        GuiButton button = new GuiButton(0, x, y, width, 20, font.trimStringToWidth(text, width - 16));
        button.drawButton(mc, mouseX, mouseY);
        font.drawStringWithShadow(open ? "▴" : "▾", x + width - 10, y + 6, 0xE0E0E0);
    }

    /** The open list, drawn last so it covers whatever is below it. */
    void drawOverlay(Minecraft mc, int mouseX, int mouseY, int screenHeight) {
        if (!open) return;
        FontRenderer font = mc.fontRenderer;
        List<Integer> shown = shown();
        int top = listTop(screenHeight, shown.size());
        int rows = visibleRows(shown.size());
        int height = listHeight(shown.size());
        Gui.drawRect(x - 1, top - 1, x + width + 1, top + height + 1, 0xFF6A6A6A);
        Gui.drawRect(x, top, x + width, top + height, 0xF0141414);
        int rowTop = top;
        if (filterable()) {
            String line = filter.isEmpty() ? "§8Type to filter..." : filter + "_";
            font.drawStringWithShadow(font.trimStringToWidth(line, width - 6), x + 3, rowTop + 2, 0xFFFFFF);
            Gui.drawRect(x, rowTop + ROW - 1, x + width, rowTop + ROW, 0xFF444444);
            rowTop += ROW;
        }
        for (int row = 0; row < rows && scroll + row < shown.size(); row++) {
            int index = shown.get(scroll + row);
            int rowY = rowTop + row * ROW;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW;
            if (index == selected) Gui.drawRect(x, rowY, x + width, rowY + ROW, 0xFF0E4A44);
            if (hover) Gui.drawRect(x, rowY, x + width, rowY + ROW, 0x55FFFFFF);
            font.drawStringWithShadow(
                font.trimStringToWidth(options.get(index), width - 6),
                x + 3,
                rowY + 2,
                index == selected ? 0x47D6C8 : 0xE0E0E0);
        }
        if (shown.isEmpty()) font.drawStringWithShadow("No matches", x + 3, rowTop + 2, 0x888888);
        if (shown.size() > rows) {
            int trackHeight = rows * ROW;
            int thumbHeight = Math.max(6, trackHeight * rows / shown.size());
            int maxScroll = shown.size() - rows;
            int thumbTop = rowTop + (trackHeight - thumbHeight) * scroll / maxScroll;
            Gui.drawRect(x + width - 3, thumbTop, x + width - 1, thumbTop + thumbHeight, 0xFF8A8A8A);
        }
    }

    /** @return true when the click was for this dropdown (including a click that just closes it) */
    boolean mouseClicked(int mouseX, int mouseY, int button, int screenHeight) {
        boolean onButton = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 20;
        if (!open) {
            if (button != 0 || !onButton) return false;
            open = true;
            filter = "";
            scroll = Math.max(0, Math.min(selected - 2, Math.max(0, options.size() - visibleRows(options.size()))));
            return true;
        }
        List<Integer> shown = shown();
        int top = listTop(screenHeight, shown.size()) + (filterable() ? ROW : 0);
        int row = (mouseY - top) / ROW;
        if (button == 0 && mouseX >= x
            && mouseX < x + width
            && mouseY >= top
            && row < visibleRows(shown.size())
            && scroll + row < shown.size()) {
            selected = shown.get(scroll + row);
            open = false;
            choice.chosen(selected);
            return true;
        }
        boolean inList = mouseX >= x && mouseX < x + width
            && mouseY >= listTop(screenHeight, shown.size())
            && mouseY < listTop(screenHeight, shown.size()) + listHeight(shown.size());
        if (!inList) open = false;
        return true;
    }

    /** @return true when the key was used (the list is open) */
    boolean keyTyped(char character, int key) {
        if (!open) return false;
        if (key == Keyboard.KEY_ESCAPE) open = false;
        else if (key == Keyboard.KEY_BACK && !filter.isEmpty()) filter = filter.substring(0, filter.length() - 1);
        else if (filterable() && character >= 32 && character != 127) filter += character;
        scroll = 0;
        return true;
    }

    /** @return true when the wheel was used (the list is open) */
    boolean scrolled(int wheel) {
        if (!open || wheel == 0) return false;
        int max = Math.max(0, shown().size() - visibleRows(shown().size()));
        scroll = Math.max(0, Math.min(max, scroll + (wheel > 0 ? -1 : 1)));
        return true;
    }

    private boolean filterable() {
        return options.size() > FILTER_AT;
    }

    private List<Integer> shown() {
        List<Integer> shown = new ArrayList<Integer>();
        String needle = filter.toLowerCase(Locale.ROOT);
        for (int i = 0; i < options.size(); i++) {
            if (needle.isEmpty() || options.get(i)
                .toLowerCase(Locale.ROOT)
                .contains(needle)) shown.add(i);
        }
        return shown;
    }

    /** Sized for the full list, so the list does not jump while a filter narrows it. */
    private int visibleRows(int ignored) {
        return Math.max(1, Math.min(MAX_ROWS, options.size()));
    }

    private int listHeight(int ignored) {
        return visibleRows(0) * ROW + (filterable() ? ROW : 0);
    }

    /** Opens downward, or upward when there is not enough room below. */
    private int listTop(int screenHeight, int count) {
        int height = listHeight(count);
        return y + 20 + height + 4 <= screenHeight ? y + 20 : Math.max(2, y - height);
    }
}
