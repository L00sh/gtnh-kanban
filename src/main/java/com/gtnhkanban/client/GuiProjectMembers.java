package com.gtnhkanban.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.MemberSummary;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SAddMember;
import com.gtnhkanban.network.message.C2SListPlayerNames;
import com.gtnhkanban.network.message.C2SRemoveMember;

public final class GuiProjectMembers extends GuiKanbanScreen {

    private static final int PAGE_SIZE = 7;
    private static final int ADD = 2;
    private static final int PREVIOUS = 3;
    private static final int NEXT = 4;

    private final UUID projectId;
    private static final int TAB_PROJECTS = 10, TAB_BOARD = 11, TAB_MEMBERS = 12, TAB_SETTINGS = 13;

    /** Suggestions shown above the username box while it has focus. */
    private static final int MAX_SUGGESTIONS = 6;
    private static final int SUGGESTION_ROW = 11;

    private GuiTextField usernameField;
    private int page;
    private int highlighted;
    private boolean namesRequested;
    private KanbanFrame.Tabs tabs = new KanbanFrame.Tabs();

    public GuiProjectMembers(UUID projectId) {
        this(projectId, null);
    }

    public GuiProjectMembers(UUID projectId, GuiScreen parent) {
        super(parent);
        this.projectId = projectId;
        refreshBoardWhileOpen(projectId);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        GuiButton add = new GuiButton(ADD, width / 2 - 55, height - 40, 70, 20, "Add member");
        BoardSnapshot board = KanbanClientState.getBoard();
        add.enabled = board != null && board.getProject()
            .isActorIsOwner();
        buttonList.add(add);
        buttonList.add(new GuiButton(PREVIOUS, width / 2 + 20, height - 40, 30, 20, "<"));
        buttonList.add(new GuiButton(NEXT, width / 2 + 55, height - 40, 30, 20, ">"));
        String typed = usernameField == null ? "" : usernameField.getText();
        usernameField = new GuiTextField(fontRendererObj, width / 2 - 100, height - 66, 200, 20);
        usernameField.setMaxStringLength(16);
        usernameField.setText(typed);
        rebuildTabs();
    }

    /** Asks for suggestions once the board shows this player owns the project (it may arrive after opening). */
    @Override
    public void updateScreen() {
        super.updateScreen();
        usernameField.updateCursorCounter();
        if (!namesRequested && isOwner()) {
            namesRequested = true;
            KanbanNetwork.CHANNEL.sendToServer(new C2SListPlayerNames(projectId));
        }
    }

    private boolean isOwner() {
        BoardSnapshot board = KanbanClientState.getBoard();
        return board != null && board.getProject()
            .isActorIsOwner();
    }

    /** Whitelisted (or known) players matching what has been typed, minus current members. */
    private List<String> suggestions() {
        if (!isOwner() || !usernameField.isFocused()) return java.util.Collections.emptyList();
        List<String> members = new java.util.ArrayList<String>();
        for (MemberSummary member : KanbanClientState.getBoard()
            .getMembers()) members.add(member.getDisplayName());
        return MemberSuggestions
            .matching(KanbanClientState.getPlayerNames(projectId), usernameField.getText(), members, MAX_SUGGESTIONS);
    }

    private void addMember(String username) {
        if (username.trim()
            .isEmpty()) return;
        KanbanNetwork.CHANNEL.sendToServer(new C2SAddMember(projectId, username.trim()));
        usernameField.setText("");
        highlighted = 0;
    }

    /** Suggestion rows sit just above the username box, nearest match closest to it. */
    private int suggestionTop(int count) {
        return height - 67 - count * SUGGESTION_ROW;
    }

    private void rebuildTabs() {
        BoardSnapshot board = KanbanClientState.getBoard();
        boolean ours = board != null && board.getProject()
            .getId()
            .equals(projectId);
        String name = ours ? board.getProject()
            .getName() : "Board";
        tabs = new KanbanFrame.Tabs().add(TAB_PROJECTS, "Projects", null, false, KanbanFrame.Color.PURPLE)
            .add(
                TAB_BOARD,
                fontRendererObj.trimStringToWidth(name, 140),
                ours ? GuiKanbanBoard.iconStack(
                    board.getProject()
                        .getIcon())
                    : null,
                false)
            .add(TAB_MEMBERS, "Members", null, true)
            .addRight(TAB_SETTINGS, "Board settings", null, true);
        tabs.layout(fontRendererObj, width);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ADD && button.enabled) {
            addMember(usernameField.getText());
        } else if (button.id == PREVIOUS) {
            page = Math.max(0, page - 1);
        } else if (button.id == NEXT) {
            page = Math.min(lastPage(), page + 1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        KanbanFrame.Tab tab = mouseButton == 0 ? tabs.at(mouseX, mouseY) : null;
        if (tab != null) {
            if (tab.id == TAB_PROJECTS) openProjects();
            else if (tab.id == TAB_BOARD) goBack();
            else if (tab.id == TAB_SETTINGS) mc.displayGuiScreen(new GuiBoardSettings(this));
            return;
        }
        List<String> suggestions = suggestions();
        if (mouseButton == 0 && !suggestions.isEmpty()) {
            int top = suggestionTop(suggestions.size());
            int row = (mouseY - top) / SUGGESTION_ROW;
            if (mouseX >= width / 2 - 100 && mouseX < width / 2 + 100 && mouseY >= top && row < suggestions.size()) {
                usernameField.setText(suggestions.get(row));
                usernameField.setFocused(true);
                highlighted = 0;
                return;
            }
        }
        usernameField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || !isOwner()) return;
        List<MemberSummary> visible = PagedList.pageItems(
            KanbanClientState.getBoard()
                .getMembers(),
            page,
            PAGE_SIZE);
        for (int index = 0; index < visible.size(); index++) {
            final MemberSummary member = visible.get(index);
            if (member.isOwner() || !overRemove(index, mouseX, mouseY)) continue;
            mc.displayGuiScreen(
                new GuiConfirmAction(this, "Remove " + member.getDisplayName() + " from this project?", new Runnable() {

                    @Override
                    public void run() {
                        KanbanNetwork.CHANNEL.sendToServer(new C2SRemoveMember(projectId, member.getPlayerId()));
                    }
                }));
            return;
        }
    }

    private int removeX() {
        return width / 2 + 50;
    }

    private int rowY(int index) {
        return 50 + index * 22;
    }

    private boolean overRemove(int index, int mouseX, int mouseY) {
        return mouseX >= removeX() && mouseX < removeX() + 50 && mouseY >= rowY(index) - 5 && mouseY < rowY(index) + 13;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (handleEscape(keyCode)) return;
        if (usernameField.isFocused()) {
            List<String> suggestions = suggestions();
            if (!suggestions.isEmpty()) {
                // The list grows upward from the box: Up moves away from it, Down back toward it.
                if (keyCode == Keyboard.KEY_UP) {
                    highlighted = Math.max(0, Math.min(suggestions.size() - 1, highlighted + 1));
                    return;
                }
                if (keyCode == Keyboard.KEY_DOWN) {
                    highlighted = Math.max(0, highlighted - 1);
                    return;
                }
                if (keyCode == Keyboard.KEY_TAB) {
                    usernameField.setText(suggestions.get(Math.min(highlighted, suggestions.size() - 1)));
                    highlighted = 0;
                    return;
                }
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                addMember(
                    suggestions.isEmpty() ? usernameField.getText()
                        : suggestions.get(Math.min(highlighted, suggestions.size() - 1)));
                return;
            }
        }
        if (usernameField.textboxKeyTyped(typedChar, keyCode)) highlighted = 0;
        else super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        rebuildTabs();
        KanbanFrame.drawWindow(mc, width, height);
        tabs.draw(mc, mouseX, mouseY);
        BoardSnapshot board = KanbanClientState.getBoard();
        if (board != null) {
            List<MemberSummary> visible = PagedList.pageItems(board.getMembers(), page, PAGE_SIZE);
            for (int index = 0; index < visible.size(); index++) {
                MemberSummary member = visible.get(index);
                String label = (member.isOwner() ? "Owner: " : "Member: ") + member.getDisplayName();
                drawString(fontRendererObj, label, width / 2 - 100, rowY(index), 0xFFFFFF);
                if (!member.isOwner() && board.getProject()
                    .isActorIsOwner()) {
                    GuiButton remove = new GuiButton(0, removeX(), rowY(index) - 5, 50, 18, "Remove");
                    remove.drawButton(mc, mouseX, mouseY);
                }
            }
            if (board.getProject()
                .isActorIsOwner()) {
                if (!usernameField.isFocused()) drawString(
                    fontRendererObj,
                    "Add a player: whitelisted players are suggested as you type",
                    width / 2 - 100,
                    height - 80,
                    0xAAAAAA);
            }
        }
        String result = KanbanClientState.getResultMessage();
        if (!result.isEmpty()) drawCenteredString(
            fontRendererObj,
            result,
            width / 2,
            KanbanFrame.contentTop() + 2,
            KanbanClientState.isResultSuccess() ? 0x55FF55 : 0xFF5555);
        if (board != null && board.getProject()
            .isActorIsOwner()) usernameField.drawTextBox();
        drawCenteredString(
            fontRendererObj,
            "Page " + (page + 1) + " / " + (lastPage() + 1),
            width / 2,
            height - 34,
            0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawSuggestions(mouseX, mouseY);
        String tabTip = tabs.tooltip(mouseX, mouseY);
        if (tabTip != null)
            drawHoveringText(java.util.Collections.singletonList(tabTip), mouseX, mouseY, fontRendererObj);
    }

    private void drawSuggestions(int mouseX, int mouseY) {
        List<String> suggestions = suggestions();
        if (suggestions.isEmpty()) return;
        highlighted = Math.min(highlighted, suggestions.size() - 1);
        int left = width / 2 - 100, top = suggestionTop(suggestions.size());
        drawRect(left - 1, top - 1, left + 201, height - 66, 0xFF6A6A6A);
        drawRect(left, top, left + 200, height - 67, 0xF0141414);
        for (int i = 0; i < suggestions.size(); i++) {
            // Index 0 (best match) sits next to the box, so rows are drawn bottom-up.
            int rowTop = height - 67 - (i + 1) * SUGGESTION_ROW;
            boolean hover = mouseX >= left && mouseX < left + 200
                && mouseY >= rowTop
                && mouseY < rowTop + SUGGESTION_ROW;
            if (i == highlighted) drawRect(left, rowTop, left + 200, rowTop + SUGGESTION_ROW, 0xFF0E4A44);
            if (hover) drawRect(left, rowTop, left + 200, rowTop + SUGGESTION_ROW, 0x55FFFFFF);
            drawString(
                fontRendererObj,
                suggestions.get(i),
                left + 3,
                rowTop + 2,
                i == highlighted ? 0x47D6C8 : 0xE0E0E0);
        }
        drawString(fontRendererObj, "\u00a78Tab: complete, Enter: add", left + 205, height - 60, 0x888888);
    }

    private int lastPage() {
        BoardSnapshot board = KanbanClientState.getBoard();
        return PagedList.pageCount(
            board == null ? 0
                : board.getMembers()
                    .size(),
            PAGE_SIZE) - 1;
    }
}
