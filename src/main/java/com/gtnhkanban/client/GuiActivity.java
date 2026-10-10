package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import com.gtnhkanban.api.ActivityLog;
import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.model.ActivityEntry;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SFetchActivity;
import com.gtnhkanban.network.message.C2SRestoreCard;

/**
 * A board's activity, newest first: what was created, edited, moved, deleted and restored, and by whom. It can be
 * narrowed to one card to see that card's history. A second view lists deleted cards, which their creator or the board
 * owner can restore.
 */
public final class GuiActivity extends GuiKanbanScreen {

    private static final int TAB_PROJECTS = 10, TAB_SETTINGS = 11, TAB_MEMBERS = 12, TAB_ACTIVITY = 13;
    private static final int SHOW_LOG = 1, SHOW_DELETED = 2, SHOW_ALL = 3;
    private static final int ROW = 12;
    private final UUID projectId;
    /** The card whose history is shown, or null for the whole board. */
    private UUID cardFilter;
    private boolean deletedView;
    private int scroll;
    private KanbanFrame.Tabs tabs = new KanbanFrame.Tabs();
    /** The board last seen; a new one means something changed, so the log is fetched again. */
    private BoardSnapshot seenBoard;

    public GuiActivity(UUID projectId, UUID cardFilter, GuiScreen parent) {
        super(parent);
        this.projectId = projectId;
        this.cardFilter = cardFilter;
        refreshBoardWhileOpen(projectId);
        KanbanNetwork.CHANNEL.sendToServer(new C2SFetchActivity(projectId));
    }

    @Override
    public void initGui() {
        rebuildTabs();
        buttonList.clear();
        int left = KanbanFrame.contentLeft();
        int top = KanbanFrame.contentTop();
        buttonList.add(new GuiButton(SHOW_LOG, left, top, 80, 20, "Activity"));
        buttonList.add(new GuiButton(SHOW_DELETED, left + 84, top, 110, 20, "Deleted cards"));
        buttonList.add(new GuiButton(SHOW_ALL, KanbanFrame.contentRight(width) - 80, top, 80, 20, "Show all"));
        refreshButtons();
    }

    /** Project tabs carry names and icons that change with the boards, so they are rebuilt as drawn. */
    private void rebuildTabs() {
        tabs = new KanbanFrame.Tabs().add(TAB_PROJECTS, "Projects", null, false, KanbanFrame.Color.PURPLE);
        addProjectTabs(tabs, null);
        tabs.addRight(TAB_SETTINGS, "Board settings", null, true)
            .addRightLabel(TAB_MEMBERS, "Members", false)
            .addRightLabel(TAB_ACTIVITY, "Activity", true);
        tabs.layout(fontRendererObj, width);
    }

    private ActivityLog log() {
        return KanbanClientState.getActivity(projectId);
    }

    private void refreshButtons() {
        ActivityLog log = log();
        for (GuiButton button : buttonList) {
            if (button.id == SHOW_LOG) button.enabled = deletedView || cardFilter != null;
            else if (button.id == SHOW_DELETED) {
                button.enabled = !deletedView;
                button.displayString = "Deleted cards" + (log == null ? ""
                    : " (" + log.getDeleted()
                        .size() + ")");
            } else if (button.id == SHOW_ALL) button.visible = !deletedView && cardFilter != null;
        }
    }

    private int listTop() {
        return KanbanFrame.contentTop() + (cardFilter != null && !deletedView ? 38 : 26);
    }

    private int listBottom() {
        return KanbanFrame.contentBottom(height) - 14;
    }

    private int capacity() {
        return Math.max(1, (listBottom() - listTop()) / ROW);
    }

    private List<ActivityLog.Entry> entries() {
        ActivityLog log = log();
        if (log == null) return Collections.emptyList();
        if (cardFilter == null) return log.getEntries();
        List<ActivityLog.Entry> mine = new ArrayList<ActivityLog.Entry>();
        for (ActivityLog.Entry entry : log.getEntries()) if (cardFilter.equals(entry.cardId)) mine.add(entry);
        return mine;
    }

    private int rowCount() {
        ActivityLog log = log();
        if (deletedView) return log == null ? 0
            : log.getDeleted()
                .size();
        return entries().size();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        BoardSnapshot board = KanbanClientState.getBoard(projectId);
        if (board != seenBoard) {
            if (seenBoard != null) KanbanNetwork.CHANNEL.sendToServer(new C2SFetchActivity(projectId));
            seenBoard = board;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rowCount() - capacity())));
        refreshButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == SHOW_LOG) {
            deletedView = false;
            cardFilter = null;
        } else if (button.id == SHOW_DELETED) deletedView = true;
        else if (button.id == SHOW_ALL) cardFilter = null;
        scroll = 0;
        refreshButtons();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0)
            scroll = Math.max(0, Math.min(Math.max(0, rowCount() - capacity()), scroll + (wheel > 0 ? -3 : 3)));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (projectTabClicked(tabs, mouseX, mouseY, projectId, mouseButton)) return;
        KanbanFrame.Tab tab = mouseButton == 0 ? tabs.at(mouseX, mouseY) : null;
        if (tab != null) {
            if (tab.id == TAB_PROJECTS) openProjects();
            else if (tab.id == TAB_SETTINGS) mc.displayGuiScreen(new GuiBoardSettings(this));
            else if (tab.id == TAB_MEMBERS) mc.displayGuiScreen(new GuiProjectMembers(projectId, this));
            return;
        }
        if (mouseButton != 0 || mouseY < listTop() || mouseY >= listTop() + capacity() * ROW) return;
        int index = scroll + (mouseY - listTop()) / ROW;
        ActivityLog log = log();
        if (log == null) return;
        if (deletedView) {
            if (index >= log.getDeleted()
                .size()) return;
            ActivityLog.Deleted card = log.getDeleted()
                .get(index);
            if (card.canRestore && mouseX >= restoreLeft()) {
                KanbanNetwork.CHANNEL.sendToServer(new C2SRestoreCard(projectId, card.cardId));
                KanbanClientState.setResult(true, "", "Restoring #" + card.number + "...");
            }
            return;
        }
        List<ActivityLog.Entry> entries = entries();
        // A card's entry narrows the log to that card's history.
        if (cardFilter == null && index < entries.size() && entries.get(index).cardId != null) {
            cardFilter = entries.get(index).cardId;
            scroll = 0;
        }
    }

    private int restoreLeft() {
        return KanbanFrame.contentRight(width) - 60;
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (!handleEscape(key)) super.keyTyped(c, key);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        rebuildTabs();
        KanbanFrame.drawWindow(mc, width, height);
        tabs.draw(mc, mouseX, mouseY);
        int left = KanbanFrame.contentLeft(), right = KanbanFrame.contentRight(width);
        ActivityLog log = log();
        String hover = null;
        if (cardFilter != null && !deletedView) {
            ActivityLog.Entry latest = entries().isEmpty() ? null : entries().get(0);
            String title = latest == null ? "this card" : "#" + latest.cardNumber + " " + latest.cardTitle;
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth("History of " + title, right - left - 90),
                left,
                KanbanFrame.contentTop() + 26,
                0xFFFFFF);
        }
        if (log == null) drawString(fontRendererObj, "Loading...", left + 4, listTop() + 2, 0xAAAAAA);
        else if (rowCount() == 0) drawString(
            fontRendererObj,
            deletedView ? "No deleted cards are kept for this board." : "Nothing has happened here yet.",
            left + 4,
            listTop() + 2,
            0xAAAAAA);
        long now = System.currentTimeMillis();
        for (int row = 0; row < capacity() && scroll + row < rowCount(); row++) {
            int y = listTop() + row * ROW;
            // Zebra stripes make long logs easier to follow across the row.
            drawRect(left, y, right, y + ROW, row % 2 == 0 ? 0x22FFFFFF : 0x11000000);
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW;
            if (deletedView) hover = drawDeleted(
                log.getDeleted()
                    .get(scroll + row),
                left,
                right,
                y,
                mouseX,
                hovered,
                now,
                hover);
            else hover = drawEntry(entries().get(scroll + row), left, right, y, hovered, now, hover);
        }
        if (rowCount() > capacity()) drawString(
            fontRendererObj,
            (scroll + 1) + "-" + Math.min(rowCount(), scroll + capacity()) + " of " + rowCount(),
            left,
            listBottom() + 3,
            0x888888);
        String result = KanbanClientState.getResultMessage();
        if (!result.isEmpty()) drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(result, right - left - 120),
            width / 2,
            listBottom() + 3,
            KanbanClientState.isResultSuccess() ? 0x55FF55 : 0xFF5555);
        super.drawScreen(mouseX, mouseY, partialTicks);
        String tabTip = tabs.tooltip(mouseX, mouseY);
        if (tabTip != null) hover = tabTip;
        if (hover != null)
            drawHoveringText(fontRendererObj.listFormattedStringToWidth(hover, 260), mouseX, mouseY, fontRendererObj);
    }

    private String drawEntry(ActivityLog.Entry entry, int left, int right, int y, boolean hovered, long now,
        String hover) {
        String actor = entry.actorName.isEmpty() ? "Someone" : entry.actorName;
        PlayerHeads.draw(mc, entry.actorId, actor, left + 2, y + 2, 8);
        String age = TimeText.ago(entry.time, now);
        int ageWidth = fontRendererObj.getStringWidth(age);
        drawString(fontRendererObj, age, right - ageWidth - 3, y + 2, 0x888888);
        String card = entry.cardId == null ? "" : "§7#" + entry.cardNumber + " §f" + entry.cardTitle + " ";
        String line = "§e" + actor + " " + color(entry.kind) + verb(entry.kind) + " " + card + "§7" + entry.detail;
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(line, right - left - ageWidth - 20),
            left + 13,
            y + 2,
            0xFFFFFF);
        if (!hovered) return hover;
        String text = actor + " " + verb(entry.kind) + " " + card + entry.detail + "\n§7" + TimeText.full(entry.time);
        if (cardFilter == null && entry.cardId != null) text += "\n§8Click for this card's history";
        return text;
    }

    private String drawDeleted(ActivityLog.Deleted card, int left, int right, int y, int mouseX, boolean hovered,
        long now, String hover) {
        String line = "§7#" + card.number
            + " §f"
            + card.title
            + " §7deleted "
            + TimeText.ago(card.deletedAt, now)
            + (card.deletedByName.isEmpty() ? "" : " by " + card.deletedByName);
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(line, restoreLeft() - left - 8),
            left + 3,
            y + 2,
            0xFFFFFF);
        boolean overRestore = hovered && mouseX >= restoreLeft();
        if (card.canRestore)
            drawString(fontRendererObj, "Restore", restoreLeft() + 8, y + 2, overRestore ? 0xFFE080 : 0xFFAA00);
        else drawString(fontRendererObj, "§8Restore", restoreLeft() + 8, y + 2, 0xFFFFFF);
        if (!hovered) return hover;
        if (mouseX >= restoreLeft()) return card.canRestore ? "Put #" + card.number + " back on the board"
            : "Only the card's creator or the board owner can restore it";
        return "#" + card.number + " " + card.title + "\n§7Deleted " + TimeText.full(card.deletedAt);
    }

    private static String verb(ActivityEntry.Kind kind) {
        switch (kind) {
            case CARD_CREATED:
                return "created";
            case CARD_EDITED:
                return "edited";
            case CARD_MOVED:
                return "moved";
            case CARD_DELETED:
                return "deleted";
            case CARD_RESTORED:
                return "restored";
            case TASKS:
                return "updated tasks on";
            case COMMENTS:
                return "commented on";
            case ITEMS:
                return "updated items on";
            case ASSIGNEES:
                return "updated assignees on";
            case LINKS:
                return "updated links on";
            case MEMBERS:
                return "changed members:";
            default:
                return "changed the project:";
        }
    }

    private static String color(ActivityEntry.Kind kind) {
        switch (kind) {
            case CARD_CREATED:
                return "§a";
            case CARD_DELETED:
                return "§c";
            case CARD_RESTORED:
                return "§6";
            case CARD_MOVED:
                return "§b";
            default:
                return "§f";
        }
    }
}
