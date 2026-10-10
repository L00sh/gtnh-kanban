package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.CommentView;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SAddComment;
import com.gtnhkanban.network.message.C2SDeleteComment;

/**
 * A card's comments, oldest at the top and newest at the bottom, each with its author's head, and a box to post a new
 * one. It stays scrolled to the newest comment unless the player scrolls up.
 */
final class GuiCommentsPanel {

    private static final int LINE = 10, HEAD = 8;
    private final Minecraft mc;
    private final FontRenderer font;
    private final UUID projectId, cardId;
    private final int left, top, width, bottom;
    private final GuiTextField input;
    private final GuiButton post;
    /** Lines scrolled up from the newest; 0 keeps the newest comment in view. */
    private int scrollUp;

    /** One drawn line: a comment's header (author, age) or a line of its text, or a gap between comments. */
    private static final class Line {

        final CommentView comment;
        final String text;
        final boolean header;

        Line(CommentView comment, String text, boolean header) {
            this.comment = comment;
            this.text = text;
            this.header = header;
        }
    }

    GuiCommentsPanel(Minecraft mc, UUID projectId, UUID cardId, int left, int top, int width, int bottom,
        String draft) {
        this.mc = mc;
        this.font = mc.fontRenderer;
        this.projectId = projectId;
        this.cardId = cardId;
        this.left = left;
        this.top = top;
        this.width = width;
        this.bottom = bottom;
        input = new GuiTextField(font, left, bottom - 19, width - 44, 18);
        input.setMaxStringLength(512);
        input.setText(draft == null ? "" : draft);
        post = new GuiButton(0, left + width - 40, bottom - 20, 40, 20, "Post");
    }

    String draft() {
        return input.getText();
    }

    private int listBottom() {
        return bottom - 24;
    }

    private int capacity() {
        return Math.max(1, (listBottom() - top) / LINE);
    }

    private List<Line> lines() {
        List<Line> lines = new ArrayList<Line>();
        CardView card = KanbanClientState.findCard(cardId);
        if (card == null) return lines;
        for (CommentView comment : card.getComments()) {
            if (!lines.isEmpty()) lines.add(new Line(null, "", false));
            lines.add(new Line(comment, "", true));
            for (Object wrapped : font.listFormattedStringToWidth(comment.getText(), width - HEAD - 8))
                lines.add(new Line(comment, (String) wrapped, false));
        }
        return lines;
    }

    private boolean canDelete(CommentView comment) {
        BoardSnapshot board = KanbanClientState.getBoard();
        if (board != null && board.getProject()
            .isActorIsOwner()) return true;
        return mc.thePlayer != null && comment.getAuthorId() != null
            && comment.getAuthorId()
                .equals(mc.thePlayer.getUniqueID());
    }

    private void send() {
        String text = input.getText()
            .trim();
        if (text.isEmpty()) return;
        KanbanNetwork.CHANNEL.sendToServer(new C2SAddComment(projectId, cardId, text));
        input.setText("");
        scrollUp = 0;
    }

    void update() {
        input.updateCursorCounter();
        post.enabled = !input.getText()
            .trim()
            .isEmpty();
    }

    void scroll(int wheel, int mouseX, int mouseY) {
        if (wheel == 0 || mouseX < left || mouseX >= left + width || mouseY < top || mouseY >= listBottom()) return;
        int most = Math.max(0, lines().size() - capacity());
        scrollUp = Math.max(0, Math.min(most, scrollUp + (wheel > 0 ? 3 : -3)));
    }

    /** First drawn line, so the newest comment sits at the bottom. */
    private int first(List<Line> lines) {
        return Math.max(0, lines.size() - capacity() - scrollUp);
    }

    void mouseClicked(int mouseX, int mouseY, int button) {
        input.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return;
        if (post.mousePressed(mc, mouseX, mouseY)) {
            post.func_146113_a(mc.getSoundHandler());
            send();
            return;
        }
        if (mouseY < top || mouseY >= listBottom() || mouseX < left + width - 10 || mouseX >= left + width) return;
        List<Line> lines = lines();
        int index = first(lines) + (mouseY - top) / LINE;
        if (index < lines.size() && lines.get(index).header && canDelete(lines.get(index).comment))
            KanbanNetwork.CHANNEL
                .sendToServer(new C2SDeleteComment(projectId, cardId, lines.get(index).comment.getId()));
    }

    /** @return true when the key was the comment box's */
    boolean keyTyped(char c, int key) {
        if (!input.isFocused()) return false;
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) send();
        else input.textboxKeyTyped(c, key);
        return true;
    }

    boolean isTyping() {
        return input.isFocused();
    }

    /** @return the comment time to show as a tooltip, or null */
    String draw(int mouseX, int mouseY) {
        Gui.drawRect(left - 2, top - 2, left + width + 2, listBottom() + 1, 0xAA111111);
        List<Line> lines = lines();
        if (lines.isEmpty()) font.drawStringWithShadow("No comments yet.", left + 3, top + 3, 0xAAAAAA);
        long now = System.currentTimeMillis();
        String hover = null;
        int first = first(lines);
        for (int row = 0; row < capacity() && first + row < lines.size(); row++) {
            Line line = lines.get(first + row);
            int y = top + row * LINE;
            if (line.header) {
                CommentView comment = line.comment;
                String author = comment.getAuthorName()
                    .isEmpty() ? "Unknown" : comment.getAuthorName();
                PlayerHeads.draw(mc, comment.getAuthorId(), author, left + 1, y, HEAD);
                String age = TimeText.ago(comment.getCreatedAt(), now);
                font.drawStringWithShadow(
                    "§e" + author + (age.isEmpty() ? "" : " §7" + age),
                    left + HEAD + 4,
                    y,
                    0xFFFFFF);
                if (canDelete(comment)) font.drawStringWithShadow("x", left + width - 7, y, 0xFF6666);
                if (mouseY >= y && mouseY < y + LINE && mouseX >= left && mouseX < left + width - 10)
                    hover = TimeText.full(comment.getCreatedAt());
            } else if (line.comment != null) font.drawStringWithShadow(line.text, left + HEAD + 4, y, 0xDDDDDD);
        }
        if (scrollUp > 0) font.drawStringWithShadow("↓ newer", left + width - 46, listBottom() - 9, 0x888888);
        input.drawTextBox();
        if (input.getText()
            .isEmpty() && !input.isFocused()) font.drawString("Write a comment...", left + 4, bottom - 14, 0x777777);
        post.drawButton(mc, mouseX, mouseY);
        return hover;
    }
}
