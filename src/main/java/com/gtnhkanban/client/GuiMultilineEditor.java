package com.gtnhkanban.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import org.lwjgl.input.Keyboard;

/** A single multiline text area with one caret and a bounded text buffer. */
final class GuiMultilineEditor {

    private final int lineCount;
    private static final int LINE_HEIGHT = 18;
    private static final int MAX_LENGTH = 512;
    private static final int PADDING = 5;

    private final FontRenderer font;
    private final int x;
    private final int y;
    private final int width;
    private final StringBuilder text = new StringBuilder();
    private int cursor;
    private int firstVisibleLine;
    private boolean focused;

    GuiMultilineEditor(FontRenderer font, int x, int y, int width) {
        this(font, x, y, width, 4);
    }

    GuiMultilineEditor(FontRenderer font, int x, int y, int width, int lineCount) {
        this.lineCount = Math.max(1, lineCount);
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
    }

    void setText(String value) {
        text.setLength(0);
        if (value != null) text.append(value, 0, Math.min(value.length(), MAX_LENGTH));
        cursor = text.length();
        keepCursorVisible();
    }

    String getText() {
        return text.toString();
    }

    void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        focused = mouseButton == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + boxHeight();
        if (!focused) return;
        int lineNumber = firstVisibleLine + Math.max(0, Math.min(lineCount - 1, (mouseY - y - 3) / LINE_HEIGHT));
        int lineStart = lineStart(lineNumber);
        int lineEnd = lineEnd(lineStart);
        int targetWidth = Math.max(0, mouseX - x - PADDING);
        cursor = lineStart;
        while (cursor < lineEnd) {
            int nextWidth = font.getStringWidth(text.substring(lineStart, cursor + 1));
            if (nextWidth > targetWidth) break;
            cursor++;
        }
        keepCursorVisible();
    }

    boolean keyTyped(char typedChar, int keyCode) {
        if (!focused) return false;
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            return insert('\n');
        }
        if (keyCode == Keyboard.KEY_BACK) {
            if (cursor > 0) {
                text.deleteCharAt(--cursor);
                keepCursorVisible();
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            if (cursor < text.length()) text.deleteCharAt(cursor);
            return true;
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            if (cursor > 0) cursor--;
            keepCursorVisible();
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            if (cursor < text.length()) cursor++;
            keepCursorVisible();
            return true;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            cursor = lineStart(lineAt(cursor));
            return true;
        }
        if (keyCode == Keyboard.KEY_END) {
            cursor = lineEnd(cursor);
            return true;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            moveCursorVertically(keyCode == Keyboard.KEY_UP ? -1 : 1);
            return true;
        }
        return typedChar >= 32 && typedChar != 127 && insert(typedChar);
    }

    void drawTextBoxes() {
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + boxHeight() + 1, 0xFFAAAAAA);
        Gui.drawRect(x, y, x + width, y + boxHeight(), 0xFF000000);
        int cursorLine = lineAt(cursor);
        int cursorColumn = cursor - lineStart(cursorLine);
        for (int visible = 0; visible < lineCount; visible++) {
            int lineNumber = firstVisibleLine + visible;
            int start = lineStart(lineNumber);
            int end = lineEnd(start);
            String line = font.trimStringToWidth(text.substring(start, end), width - PADDING * 2);
            int lineY = y + 4 + visible * LINE_HEIGHT;
            font.drawString(line, x + PADDING, lineY, 0xFFFFFF);
            if (lineNumber == cursorLine && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int caretX = x + PADDING
                    + font.getStringWidth(text.substring(start, Math.min(start + cursorColumn, end)));
                if (caretX < x + width - PADDING) Gui.drawRect(caretX, lineY - 1, caretX + 1, lineY + 9, 0xFFFFFFFF);
            }
            if (end >= text.length()) break;
        }
    }

    private boolean insert(char value) {
        if (text.length() >= MAX_LENGTH) return true;
        text.insert(cursor++, value);
        keepCursorVisible();
        return true;
    }

    private void moveCursorVertically(int lineDelta) {
        int lineNumber = lineAt(cursor);
        int column = cursor - lineStart(lineNumber);
        int targetLine = Math.max(0, lineNumber + lineDelta);
        int targetStart = lineStart(targetLine);
        int targetEnd = lineEnd(targetStart);
        cursor = Math.min(targetStart + column, targetEnd);
        keepCursorVisible();
    }

    private int lineAt(int position) {
        int line = 0;
        for (int index = 0; index < position && index < text.length(); index++) {
            if (text.charAt(index) == '\n') line++;
        }
        return line;
    }

    private int lineStart(int lineNumber) {
        int line = 0;
        for (int index = 0; index < text.length(); index++) {
            if (line == lineNumber) return index;
            if (text.charAt(index) == '\n') line++;
        }
        return text.length();
    }

    private int lineEnd(int start) {
        int end = start;
        while (end < text.length() && text.charAt(end) != '\n') end++;
        return end;
    }

    private void keepCursorVisible() {
        int cursorLine = lineAt(cursor);
        if (cursorLine < firstVisibleLine) firstVisibleLine = cursorLine;
        if (cursorLine >= firstVisibleLine + lineCount) firstVisibleLine = cursorLine - lineCount + 1;
    }

    private int boxHeight() {
        return lineCount * LINE_HEIGHT + 4;
    }
}
