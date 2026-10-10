package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import org.lwjgl.input.Keyboard;

/**
 * A multiline text area with one caret and a bounded text buffer. Long lines wrap at word boundaries to fit the box,
 * as they are typed; the caret, clicks and arrow keys follow the wrapped lines.
 */
final class GuiMultilineEditor {

    private static final int LINE_HEIGHT = 10;
    private static final int MAX_LENGTH = 512;
    private static final int PADDING = 4;

    private final FontRenderer font;
    private final int x, y, width, lineCount;
    private final StringBuilder text = new StringBuilder();
    /** Wrapped lines as {start, end} offsets into {@link #text}; end excludes any newline or wrapping space. */
    private List<int[]> lines = new ArrayList<int[]>();
    private int cursor;
    private int firstVisibleLine;
    private boolean focused;
    private boolean readOnly;

    /** @param height the box height; as many lines as fit are shown */
    GuiMultilineEditor(FontRenderer font, int x, int y, int width, int height) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.lineCount = Math.max(1, (height - PADDING * 2 + 2) / LINE_HEIGHT);
        layout();
    }

    void setText(String value) {
        text.setLength(0);
        if (value != null) text.append(value, 0, Math.min(value.length(), MAX_LENGTH));
        cursor = text.length();
        layout();
        keepCursorVisible();
    }

    String getText() {
        return text.toString();
    }

    /** Shown but not editable, e.g. for players who may not change it. */
    void setReadOnly(boolean value) {
        readOnly = value;
        if (value) focused = false;
    }

    int height() {
        return lineCount * LINE_HEIGHT + PADDING * 2 - 2;
    }

    boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height();
    }

    void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        focused = !readOnly && mouseButton == 0 && contains(mouseX, mouseY);
        if (!focused) return;
        int line = Math.min(lines.size() - 1, firstVisibleLine + Math.max(0, (mouseY - y - PADDING) / LINE_HEIGHT));
        cursor = offsetAt(line, mouseX - x - PADDING);
    }

    void scroll(int wheel, int mouseX, int mouseY) {
        if (wheel == 0 || !contains(mouseX, mouseY)) return;
        int maxFirst = Math.max(0, lines.size() - lineCount);
        firstVisibleLine = Math.max(0, Math.min(maxFirst, firstVisibleLine + (wheel > 0 ? -1 : 1)));
    }

    boolean keyTyped(char typedChar, int keyCode) {
        if (!focused || readOnly) return false;
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) return insert('\n');
        if (keyCode == Keyboard.KEY_BACK) {
            if (cursor > 0) {
                text.deleteCharAt(--cursor);
                changed();
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            if (cursor < text.length()) {
                text.deleteCharAt(cursor);
                changed();
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT) {
            cursor = Math.max(0, Math.min(text.length(), cursor + (keyCode == Keyboard.KEY_LEFT ? -1 : 1)));
            keepCursorVisible();
            return true;
        }
        if (keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END) {
            int[] line = lines.get(lineOf(cursor));
            cursor = keyCode == Keyboard.KEY_HOME ? line[0] : line[1];
            return true;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            int line = lineOf(cursor);
            int target = line + (keyCode == Keyboard.KEY_UP ? -1 : 1);
            if (target >= 0 && target < lines.size()) {
                int column = font.getStringWidth(text.substring(lines.get(line)[0], cursor));
                cursor = offsetAt(target, column);
                keepCursorVisible();
            }
            return true;
        }
        return typedChar >= 32 && typedChar != 127 && insert(typedChar);
    }

    void draw() {
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height() + 1, focused ? 0xFFDDDDDD : 0xFFA0A0A0);
        Gui.drawRect(x, y, x + width, y + height(), 0xFF000000);
        int cursorLine = lineOf(cursor);
        for (int visible = 0; visible < lineCount && firstVisibleLine + visible < lines.size(); visible++) {
            int index = firstVisibleLine + visible;
            int[] line = lines.get(index);
            int lineY = y + PADDING + visible * LINE_HEIGHT;
            font.drawString(text.substring(line[0], line[1]), x + PADDING, lineY, readOnly ? 0xBBBBBB : 0xFFFFFF);
            if (focused && index == cursorLine && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int caretX = x + PADDING + font.getStringWidth(text.substring(line[0], Math.min(cursor, line[1])));
                Gui.drawRect(caretX, lineY - 1, caretX + 1, lineY + 9, 0xFFFFFFFF);
            }
        }
        if (lines.size() > lineCount) {
            // A thin bar on the right shows where in a long description the box is.
            int track = height() - 2;
            int thumb = Math.max(6, track * lineCount / lines.size());
            int top = y + 1 + (track - thumb) * firstVisibleLine / Math.max(1, lines.size() - lineCount);
            Gui.drawRect(x + width - 3, top, x + width - 1, top + thumb, 0xFF6A6A6A);
        }
    }

    private boolean insert(char value) {
        if (text.length() >= MAX_LENGTH) return true;
        text.insert(cursor++, value);
        changed();
        return true;
    }

    private void changed() {
        layout();
        keepCursorVisible();
    }

    /** The text offset in a wrapped line nearest {@code pixels} from its left edge. */
    private int offsetAt(int lineIndex, int pixels) {
        int[] line = lines.get(lineIndex);
        int offset = line[0];
        while (offset < line[1] && font.getStringWidth(text.substring(line[0], offset + 1)) <= pixels) offset++;
        return offset;
    }

    /** The wrapped line holding the caret: the last one starting at or before it. */
    private int lineOf(int position) {
        int found = 0;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i)[0] <= position) found = i;
        return found;
    }

    private void layout() {
        List<int[]> wrapped = new ArrayList<int[]>();
        int usable = Math.max(10, width - PADDING * 2 - 4);
        int start = 0;
        while (true) {
            int paragraphEnd = text.indexOf("\n", start);
            if (paragraphEnd < 0) paragraphEnd = text.length();
            int lineStart = start;
            while (true) {
                String rest = text.substring(lineStart, paragraphEnd);
                int fits = font.trimStringToWidth(rest, usable)
                    .length();
                if (fits >= rest.length()) {
                    wrapped.add(new int[] { lineStart, paragraphEnd });
                    break;
                }
                int space = rest.lastIndexOf(' ', fits);
                if (space > 0) {
                    wrapped.add(new int[] { lineStart, lineStart + space });
                    lineStart += space + 1;
                } else {
                    // One word wider than the box: break it.
                    int cut = Math.max(1, fits);
                    wrapped.add(new int[] { lineStart, lineStart + cut });
                    lineStart += cut;
                }
            }
            if (paragraphEnd >= text.length()) break;
            start = paragraphEnd + 1;
        }
        lines = wrapped;
    }

    private void keepCursorVisible() {
        int line = lineOf(cursor);
        if (line < firstVisibleLine) firstVisibleLine = line;
        if (line >= firstVisibleLine + lineCount) firstVisibleLine = line - lineCount + 1;
        firstVisibleLine = Math.max(0, Math.min(firstVisibleLine, Math.max(0, lines.size() - lineCount)));
    }
}
