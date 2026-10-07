package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.api.TaskView;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SDeleteRequirement;
import com.gtnhkanban.network.message.C2SDeleteTask;
import com.gtnhkanban.network.message.C2SSetRequirementComplete;
import com.gtnhkanban.network.message.C2SSetRequirementQuantity;
import com.gtnhkanban.network.message.C2SSetTaskDone;

/** Scrollable checklist with independently hit-tested controls and nested material rows. */
final class GuiChecklistPanel {

    interface RecipeAction {

        void open(RequirementView row);
    }

    private static final int ROW_HEIGHT = 24;
    private final Minecraft mc;
    private final UUID projectId, cardId;
    private final RecipeAction recipeAction;
    private final int left, top, width, bottom;
    /** Branches start collapsed; a full breakdown can be thousands of rows. */
    private final Set<UUID> expanded = new HashSet<UUID>();
    private boolean totalsOpen = true, toolsOpen;
    private final Map<UUID, GuiTextField> quantityFields = new HashMap<UUID, GuiTextField>();
    private final Map<UUID, Integer> observed = new HashMap<UUID, Integer>();
    private final RenderItem render = new RenderItem();
    private int scroll;
    private List<Row> visible = new ArrayList<Row>();

    /** A checklist row, a section header (materials or tools), or one total within a section. */
    private static final class Row {

        final RequirementView requirement;
        final int depth;
        final boolean header;
        final MaterialTotals.Total total;
        boolean tools;
        TaskView task;

        Row(RequirementView requirement, int depth) {
            this(requirement, depth, false, null);
        }

        private Row(RequirementView requirement, int depth, boolean header, MaterialTotals.Total total) {
            this.requirement = requirement;
            this.depth = depth;
            this.header = header;
            this.total = total;
        }

        static Row header(boolean tools) {
            Row row = new Row(null, 0, true, null);
            row.tools = tools;
            return row;
        }

        static Row total(MaterialTotals.Total total) {
            return new Row(null, 1, false, total);
        }

        static Row task(TaskView task) {
            Row row = new Row(null, 0, false, null);
            row.task = task;
            return row;
        }

        boolean isRequirement() {
            return requirement != null;
        }
    }

    private int totalCount, toolCount;

    GuiChecklistPanel(Minecraft mc, UUID projectId, UUID cardId, int left, int top, int width, int bottom,
        RecipeAction action) {
        this.mc = mc;
        this.projectId = projectId;
        this.cardId = cardId;
        this.left = left;
        this.top = top;
        this.width = width;
        this.bottom = bottom;
        this.recipeAction = action;
    }

    private int capacity() {
        return Math.max(0, (bottom - top) / ROW_HEIGHT);
    }

    private void flatten(RequirementView requirement, int depth, List<Row> rows) {
        rows.add(new Row(requirement, depth));
        if (expanded.contains(requirement.getId())) for (RequirementView child : requirement.getChildren())
            if (!child.isReusable()) flatten(child, depth + 1, rows);
    }

    /** One entry per kind of tool: a recipe wanting a hammer is satisfied by a hammer of any material. */
    private static List<MaterialTotals.Total> toolsByKind(List<MaterialTotals.Total> tools) {
        Map<String, MaterialTotals.Total> kinds = new java.util.LinkedHashMap<String, MaterialTotals.Total>();
        for (MaterialTotals.Total tool : tools) {
            String kind = MaterialDisplay.toolName(tool.material);
            MaterialTotals.Total known = kinds.get(kind);
            if (known == null) {
                kinds.put(kind, tool);
                continue;
            }
            // Same kind of tool from different materials: one row, listing every use.
            Map<ItemKey, Long> uses = new java.util.LinkedHashMap<ItemKey, Long>(known.usedFor);
            for (Map.Entry<ItemKey, Long> use : tool.usedFor.entrySet()) {
                Long previous = uses.get(use.getKey());
                uses.put(use.getKey(), previous == null ? use.getValue() : Math.max(previous, use.getValue()));
            }
            kinds.put(kind, new MaterialTotals.Total(known.material, Math.max(known.amount, tool.amount), true, uses));
        }
        return new ArrayList<MaterialTotals.Total>(kinds.values());
    }

    /** Tools are listed in their own section, so a row only opens if it has materials below it. */
    private static boolean hasMaterialChildren(RequirementView requirement) {
        for (RequirementView child : requirement.getChildren()) if (!child.isReusable()) return true;
        return false;
    }

    void refresh() {
        CardView card = KanbanClientState.findCard(cardId);
        List<Row> rows = new ArrayList<Row>();
        totalCount = 0;
        toolCount = 0;
        if (card != null && MaterialTotals.hasBreakdown(card.getRequirements())) {
            List<MaterialTotals.Total> totals = MaterialTotals.of(card.getRequirements());
            totalCount = totals.size();
            rows.add(Row.header(false));
            if (totalsOpen) for (MaterialTotals.Total total : totals) rows.add(Row.total(total));
            List<MaterialTotals.Total> tools = toolsByKind(MaterialTotals.tools(card.getRequirements()));
            toolCount = tools.size();
            if (toolCount > 0) {
                rows.add(Row.header(true));
                if (toolsOpen) for (MaterialTotals.Total tool : tools) rows.add(Row.total(tool));
            }
        }
        if (card != null) for (TaskView task : card.getTasks()) rows.add(Row.task(task));
        if (card != null) for (RequirementView requirement : card.getRequirements()) flatten(requirement, 0, rows);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - capacity())));
        visible = new ArrayList<Row>(rows.subList(scroll, Math.min(rows.size(), scroll + capacity())));
        Set<UUID> visibleRoots = new HashSet<UUID>();
        for (int i = 0; i < visible.size(); i++) {
            Row row = visible.get(i);
            if (!row.isRequirement() || row.depth != 0) continue;
            UUID id = row.requirement.getId();
            visibleRoots.add(id);
            GuiTextField field = quantityFields.get(id);
            if (field == null) {
                field = new GuiTextField(mc.fontRenderer, left + width - 181, top + i * ROW_HEIGHT + 2, 52, 18);
                field.setMaxStringLength(10);
                field.setText(Integer.toString(row.requirement.getQuantity()));
                quantityFields.put(id, field);
                observed.put(id, row.requirement.getQuantity());
            }
            field.yPosition = top + i * ROW_HEIGHT + 2;
            if (observed.get(id)
                .intValue() != row.requirement.getQuantity()) {
                field.setText(Integer.toString(row.requirement.getQuantity()));
                observed.put(id, row.requirement.getQuantity());
            }
            field.updateCursorCounter();
        }
        quantityFields.keySet()
            .retainAll(visibleRoots);
        observed.keySet()
            .retainAll(visibleRoots);
    }

    void scroll(int delta, int mouseX, int mouseY) {
        if (mouseX < left || mouseX >= left + width || mouseY < top || mouseY >= bottom || delta == 0) return;
        scroll += delta > 0 ? -1 : 1;
        refresh();
    }

    private GuiButton control(int id, int x, int y, int w, String label) {
        return new GuiButton(id, x, y, w, 18, label);
    }

    void draw(int mouseX, int mouseY) {
        refresh();
        Gui.drawRect(left - 2, top - 2, left + width + 2, bottom + 1, 0xAA111111);
        if (visible.isEmpty()) mc.fontRenderer
            .drawStringWithShadow("No tasks or items yet. Add a task or an item above.", left + 5, top + 5, 0xAAAAAA);
        for (int i = 0; i < visible.size(); i++) {
            Row row = visible.get(i);
            int y = top + i * ROW_HEIGHT + 2;
            if (row.task != null) {
                drawTaskRow(row.task, y, mouseX, mouseY);
                continue;
            }
            if (!row.isRequirement()) {
                drawTotalsRow(row, y, mouseX, mouseY);
                continue;
            }
            RequirementView requirement = row.requirement;
            int indent = Math.min(row.depth * 10, 70);
            int x = left + indent;
            Gui.drawRect(left, y - 1, left + width, y + 20, 0x55333333);
            if (hasMaterialChildren(requirement))
                control(0, x, y, 16, expanded.contains(requirement.getId()) ? "v" : ">").drawButton(mc, mouseX, mouseY);
            ItemStack stack = MaterialDisplay.stack(requirement.getItem());
            if (stack != null)
                render.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x + 18, y);
            String label = (requirement.isComplete() ? "[x] " : "[ ] ") + MaterialDisplay.name(requirement.getItem());
            mc.fontRenderer.drawStringWithShadow(
                mc.fontRenderer.trimStringToWidth(label, Math.max(10, width - 225 - indent)),
                x + 38,
                y + 5,
                requirement.isComplete() ? 0x55FF55 : 0xFFFFFF);
            if (row.depth == 0) {
                quantityFields.get(requirement.getId())
                    .drawTextBox();
                control(1, left + width - 123, y, 38, "Apply").drawButton(mc, mouseX, mouseY);
            } else {
                String amount = Integer.toString(requirement.getQuantity()) + (requirement.getItem()
                    .isFluid() ? "mB" : "") + (requirement.isReusable() ? " *" : "");
                mc.fontRenderer.drawStringWithShadow(amount, left + width - 178, y + 5, 0xCCCCCC);
            }
            control(2, left + width - 81, y, 57, "Recipe").drawButton(mc, mouseX, mouseY);
            control(3, left + width - 20, y, 18, "X").drawButton(mc, mouseX, mouseY);

        }
    }

    private void drawTaskRow(TaskView task, int y, int mouseX, int mouseY) {
        Gui.drawRect(left, y - 1, left + width, y + 20, 0x55334433);
        String label = (task.isDone() ? "[x] " : "[ ] ") + task.getText();
        mc.fontRenderer.drawStringWithShadow(
            mc.fontRenderer.trimStringToWidth(label, width - 30),
            left + 6,
            y + 5,
            task.isDone() ? 0x55FF55 : 0xFFFFFF);
        control(3, left + width - 20, y, 18, "X").drawButton(mc, mouseX, mouseY);
    }

    private void drawTotalsRow(Row row, int y, int mouseX, int mouseY) {
        if (row.header) {
            Gui.drawRect(left, y - 1, left + width, y + 20, 0x77224466);
            boolean open = row.tools ? toolsOpen : totalsOpen;
            control(0, left, y, 16, open ? "v" : ">").drawButton(mc, mouseX, mouseY);
            String title = row.tools ? "Tools & equipment (" + toolCount + ")"
                : "Base materials for this card (" + totalCount + ")";
            mc.fontRenderer.drawStringWithShadow(title, left + 22, y + 5, 0xFFFFFF);
            return;
        }
        MaterialTotals.Total total = row.total;
        int x = left + 10;
        Gui.drawRect(left, y - 1, left + width, y + 20, 0x55223344);
        ItemStack stack = MaterialDisplay.stack(total.material);
        if (stack != null) render.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        boolean fluid = total.material.isFluid();
        long available = fluid ? 0 : KanbanClientControls.inventoryCount(mc, total.material);
        String amount = total.reusable ? (available > 0 ? "have" : "need")
            : fluid ? total.amount + " mB" : available + " / " + total.amount;
        int amountWidth = mc.fontRenderer.getStringWidth(amount);
        String label = total.reusable ? MaterialDisplay.toolName(total.material) : MaterialDisplay.name(total.material);
        mc.fontRenderer.drawStringWithShadow(
            mc.fontRenderer.trimStringToWidth(label, width - amountWidth - 40),
            x + 20,
            y + 5,
            0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow(
            amount,
            left + width - amountWidth - 6,
            y + 5,
            !fluid && available >= (total.reusable ? 1 : total.amount) ? 0x55FF55 : 0xCCCCCC);
    }

    List<String> tooltip(int mouseX, int mouseY) {
        if (mouseY < top || mouseY >= bottom || mouseX < left || mouseX >= left + width - 185)
            return java.util.Collections.emptyList();
        int i = (mouseY - top) / ROW_HEIGHT;
        if (i >= visible.size()) return java.util.Collections.emptyList();
        if (visible.get(i).task != null) {
            List<String> lines = new ArrayList<String>();
            lines.add(visible.get(i).task.getText());
            lines.add("\u00a77Task: click to tick or untick");
            return lines;
        }
        if (!visible.get(i)
            .isRequirement()) return totalsTooltip(visible.get(i));
        RequirementView row = visible.get(i).requirement;
        List<String> lines = new ArrayList<String>();
        lines.add(MaterialDisplay.name(row.getItem()));
        lines.add(
            "Required: " + row.getQuantity()
                + (row.getItem()
                    .isFluid() ? " mB" : " items"));
        if (row.isReusable()) lines.add("Reusable: required once, not consumed per batch.");
        if (!row.getRecipeName()
            .isEmpty()) lines.add("Recipe: " + row.getRecipeName());
        if (visible.get(i).depth > 0) lines.add("Quantity follows the parent recipe.");
        return lines;
    }

    private List<String> totalsTooltip(Row row) {
        List<String> lines = new ArrayList<String>();
        if (row.header && row.tools) {
            lines.add("Tools and other items the recipes use but do not");
            lines.add("consume. They are not part of the material totals.");
            return lines;
        }
        if (row.header) {
            lines.add("Materials the unfinished rows below still consume,");
            lines.add("added up across all of their recipe branches.");
            lines.add("Rows marked done are left out.");
            return lines;
        }
        lines.add(MaterialDisplay.name(row.total.material));
        lines.add("Needed: " + row.total.amount + (row.total.material.isFluid() ? " mB" : " items"));
        if (!row.total.material.isFluid())
            lines.add("Carried or in open container: " + KanbanClientControls.inventoryCount(mc, row.total.material));
        if (row.total.reusable) lines.add("Used by a recipe, not consumed.");
        addUsedFor(lines, row.total);
        return lines;
    }

    /** At most this many "used for" lines; the rest are summarised. */
    private static final int USED_FOR_LINES = 8;

    /** What in this breakdown the material goes into, not every recipe in the game that uses it. */
    private void addUsedFor(List<String> lines, MaterialTotals.Total total) {
        if (total.usedFor.isEmpty()) return;
        lines.add("§7Used for:");
        int shown = 0;
        for (Map.Entry<ItemKey, Long> use : total.usedFor.entrySet()) {
            if (shown == USED_FOR_LINES) {
                lines.add("§7  +" + (total.usedFor.size() - shown) + " more");
                break;
            }
            String target = use.getKey() == null ? "this card directly" : MaterialDisplay.name(use.getKey());
            String amount = total.reusable ? "" : " §7x" + use.getValue() + (total.material.isFluid() ? " mB" : "");
            lines.add("  " + target + amount);
            shown++;
        }
    }

    void click(int mouseX, int mouseY, int button) {
        for (GuiTextField field : quantityFields.values()) field.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || mouseX < left || mouseX >= left + width || mouseY < top || mouseY >= bottom) return;
        int i = (mouseY - top) / ROW_HEIGHT;
        if (i >= visible.size()) return;
        Row row = visible.get(i);
        if (row.task != null) {
            UUID taskId = row.task.getId();
            if (mouseX >= left + width - 20) {
                KanbanClientState.removeTaskLocally(projectId, cardId, taskId);
                KanbanNetwork.CHANNEL.sendToServer(new C2SDeleteTask(projectId, cardId, taskId));
            } else {
                boolean done = !row.task.isDone();
                KanbanClientState.setTaskDoneLocally(projectId, cardId, taskId, done);
                KanbanNetwork.CHANNEL.sendToServer(new C2SSetTaskDone(projectId, cardId, taskId, done));
            }
            refresh();
            return;
        }
        if (!row.isRequirement()) {
            if (row.header) {
                if (row.tools) toolsOpen = !toolsOpen;
                else totalsOpen = !totalsOpen;
                refresh();
            }
            return;
        }
        RequirementView requirement = row.requirement;
        int x = left + Math.min(row.depth * 10, 70);
        if (mouseX < x) return;
        if (mouseX < x + 16 && hasMaterialChildren(requirement)) {
            if (!expanded.add(requirement.getId())) expanded.remove(requirement.getId());
            refresh();
        } else if (mouseX >= left + width - 20) {
            KanbanNetwork.CHANNEL.sendToServer(new C2SDeleteRequirement(projectId, cardId, requirement.getId()));
        } else if (mouseX >= left + width - 81 && mouseX < left + width - 24) {
            recipeAction.open(requirement);
        } else if (mouseX >= left + width - 123 && mouseX < left + width - 85 && row.depth == 0) {
            apply(requirement.getId());
        } else if (mouseX >= x + 18 && mouseX < left + width - 185) {
            KanbanNetwork.CHANNEL.sendToServer(
                new C2SSetRequirementComplete(projectId, cardId, requirement.getId(), !requirement.isComplete()));
        }
    }

    boolean keyTyped(char c, int key) {
        for (Map.Entry<UUID, GuiTextField> entry : quantityFields.entrySet()) if (entry.getValue()
            .isFocused()) {
                if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) apply(entry.getKey());
                else if (Character.isDigit(c) || c < 32 || net.minecraft.client.gui.GuiScreen.isCtrlKeyDown())
                    entry.getValue()
                        .textboxKeyTyped(c, key);
                return true;
            }
        return false;
    }

    private void apply(UUID id) {
        try {
            int amount = Integer.parseInt(
                quantityFields.get(id)
                    .getText());
            if (amount < 1) throw new NumberFormatException();
            KanbanNetwork.CHANNEL.sendToServer(new C2SSetRequirementQuantity(projectId, cardId, id, amount));
        } catch (NumberFormatException exception) {
            KanbanClientState.setResult(false, "INVALID_QUANTITY", "Quantity must be a positive whole number.");
        }
    }
}
