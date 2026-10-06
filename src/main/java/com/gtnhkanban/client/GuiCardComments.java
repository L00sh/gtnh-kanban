package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.CommentView;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SAddComment;
import com.gtnhkanban.network.message.C2SDeleteComment;

/** A card's comments, newest first, with a box to post a new one. */
final class GuiCardComments extends GuiKanbanScreen {

    private static final int BACK = 1, POST = 2;
    private static final int LINE = 10;
    private final UUID projectId, cardId;
    private GuiTextField input;
    private int left, panelWidth, scroll;

    /** One drawn line: a comment header (with the comment) or a line of its text. */
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

    GuiCardComments(UUID projectId, UUID cardId, GuiScreen parent) {
        super(parent);
        this.projectId = projectId;
        this.cardId = cardId;
        refreshBoardWhileOpen(projectId);
    }

    @Override
    public void initGui() {
        panelWidth = Math.min(width - 24, 420);
        left = (width - panelWidth) / 2;
        String draft = input == null ? "" : input.getText();
        input = new GuiTextField(fontRendererObj, left, height - 52, panelWidth - 64, 18);
        input.setMaxStringLength(512);
        input.setText(draft);
        input.setFocused(true);
        buttonList.clear();
        buttonList.add(new GuiButton(POST, left + panelWidth - 60, height - 53, 60, 20, "Post"));
        buttonList.add(new GuiButton(BACK, width / 2 - 40, height - 28, 80, 20, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK) goBack();
        else if (button.id == POST) post();
    }

    private void post() {
        String text = input.getText()
            .trim();
        if (text.isEmpty()) return;
        KanbanNetwork.CHANNEL.sendToServer(new C2SAddComment(projectId, cardId, text));
        input.setText("");
        scroll = 0;
    }

    private List<Line> lines() {
        List<Line> lines = new ArrayList<Line>();
        CardView card = KanbanClientState.findCard(cardId);
        if (card == null) return lines;
        List<CommentView> comments = new ArrayList<CommentView>(card.getComments());
        Collections.reverse(comments);
        for (CommentView comment : comments) {
            lines.add(new Line(comment, "", true));
            for (Object wrapped : fontRendererObj.listFormattedStringToWidth(comment.getText(), panelWidth - 12))
                lines.add(new Line(comment, (String) wrapped, false));
            lines.add(new Line(null, "", false));
        }
        return lines;
    }

    private int top() {
        return 30;
    }

    private int capacity() {
        return Math.max(1, (height - 62 - top()) / LINE);
    }

    private boolean canDelete(CommentView comment) {
        BoardSnapshot board = KanbanClientState.getBoard();
        if (board != null && board.getProject()
            .isActorIsOwner()) return true;
        return mc.thePlayer != null && comment.getAuthorId() != null
            && comment.getAuthorId()
                .equals(mc.thePlayer.getUniqueID());
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0)
            scroll = Math.max(0, Math.min(Math.max(0, lines().size() - capacity()), scroll + (wheel > 0 ? -3 : 3)));
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        input.mouseClicked(x, y, button);
        Line line = lineAt(y);
        if (button == 0 && line != null
            && line.header
            && canDelete(line.comment)
            && x >= left + panelWidth - 12
            && x < left + panelWidth) {
            KanbanNetwork.CHANNEL.sendToServer(new C2SDeleteComment(projectId, cardId, line.comment.getId()));
        }
    }

    private Line lineAt(int y) {
        if (y < top()) return null;
        List<Line> lines = lines();
        int index = scroll + (y - top()) / LINE;
        return index < lines.size() && (y - top()) / LINE < capacity() ? lines.get(index) : null;
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (handleEscape(key)) return;
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) post();
        else if (!input.textboxKeyTyped(c, key)) super.keyTyped(c, key);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        input.updateCursorCounter();
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        drawDefaultBackground();
        CardView card = KanbanClientState.findCard(cardId);
        drawCenteredString(
            fontRendererObj,
            card == null ? "Comments"
                : fontRendererObj
                    .trimStringToWidth("Comments on #" + card.getNumber() + " " + card.getTitle(), width - 20),
            width / 2,
            12,
            0xFFFFFF);
        drawRect(left - 4, top() - 4, left + panelWidth + 4, height - 58, 0x88000000);
        List<Line> lines = lines();
        if (lines.isEmpty()) drawString(fontRendererObj, "No comments yet.", left + 2, top() + 2, 0xAAAAAA);
        long now = System.currentTimeMillis();
        String hoverText = null;
        for (int row = 0; row < capacity() && scroll + row < lines.size(); row++) {
            Line line = lines.get(scroll + row);
            int lineY = top() + row * LINE;
            if (line.header) {
                String author = line.comment.getAuthorName()
                    .isEmpty() ? "Unknown" : line.comment.getAuthorName();
                String age = TimeText.ago(line.comment.getCreatedAt(), now);
                drawString(
                    fontRendererObj,
                    "§e" + author + (age.isEmpty() ? "" : " §7" + age),
                    left + 2,
                    lineY,
                    0xFFFFFF);
                if (canDelete(line.comment)) drawString(fontRendererObj, "x", left + panelWidth - 9, lineY, 0xFF6666);
                if (y >= lineY && y < lineY + LINE && x >= left && x < left + panelWidth - 12)
                    hoverText = TimeText.full(line.comment.getCreatedAt());
            } else if (line.comment != null) drawString(fontRendererObj, line.text, left + 8, lineY, 0xDDDDDD);
        }
        input.drawTextBox();
        if (input.getText()
            .isEmpty()) drawString(fontRendererObj, "Write a comment, then Enter", left + 4, height - 47, 0x777777);
        super.drawScreen(x, y, partialTicks);
        if (hoverText != null) {
            List<String> tooltip = new ArrayList<String>();
            tooltip.add(hoverText);
            drawHoveringText(tooltip, x, y, fontRendererObj);
        }
    }
}
