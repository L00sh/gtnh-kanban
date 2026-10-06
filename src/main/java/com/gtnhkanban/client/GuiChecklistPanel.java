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
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SDeleteRequirement;
import com.gtnhkanban.network.message.C2SSetRequirementComplete;
import com.gtnhkanban.network.message.C2SSetRequirementQuantity;

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
    private final Set<UUID> collapsed = new HashSet<UUID>();
    private final Map<UUID, GuiTextField> quantityFields = new HashMap<UUID, GuiTextField>();
    private final Map<UUID, Integer> observed = new HashMap<UUID, Integer>();
    private final RenderItem render = new RenderItem();
    private int scroll;
    private List<Row> visible = new ArrayList<Row>();

    private static final class Row {

        final RequirementView requirement;
        final int depth;

        Row(RequirementView requirement, int depth) {
            this.requirement = requirement;
            this.depth = depth;
        }
    }

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
        if (!collapsed.contains(requirement.getId()))
            for (RequirementView child : requirement.getChildren()) flatten(child, depth + 1, rows);
    }

    void refresh() {
        CardView card = KanbanClientState.findCard(cardId);
        List<Row> rows = new ArrayList<Row>();
        if (card != null) for (RequirementView requirement : card.getRequirements()) flatten(requirement, 0, rows);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - capacity())));
        visible = new ArrayList<Row>(rows.subList(scroll, Math.min(rows.size(), scroll + capacity())));
        Set<UUID> visibleRoots = new HashSet<UUID>();
        for (int i = 0; i < visible.size(); i++) {
            Row row = visible.get(i);
            if (row.depth != 0) continue;
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
        if (visible.isEmpty())
            mc.fontRenderer.drawStringWithShadow("No checklist items. Add an item above.", left + 5, top + 5, 0xAAAAAA);
        for (int i = 0; i < visible.size(); i++) {
            Row row = visible.get(i);
            RequirementView requirement = row.requirement;
            int y = top + i * ROW_HEIGHT + 2;
            int indent = Math.min(row.depth * 10, 70);
            int x = left + indent;
            Gui.drawRect(left, y - 1, left + width, y + 20, 0x55333333);
            if (!requirement.getChildren()
                .isEmpty())
                control(0, x, y, 16, collapsed.contains(requirement.getId()) ? ">" : "v")
                    .drawButton(mc, mouseX, mouseY);
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

    List<String> tooltip(int mouseX, int mouseY) {
        if (mouseY < top || mouseY >= bottom || mouseX < left || mouseX >= left + width - 185)
            return java.util.Collections.emptyList();
        int i = (mouseY - top) / ROW_HEIGHT;
        if (i >= visible.size()) return java.util.Collections.emptyList();
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

    void click(int mouseX, int mouseY, int button) {
        for (GuiTextField field : quantityFields.values()) field.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || mouseX < left || mouseX >= left + width || mouseY < top || mouseY >= bottom) return;
        int i = (mouseY - top) / ROW_HEIGHT;
        if (i >= visible.size()) return;
        Row row = visible.get(i);
        RequirementView requirement = row.requirement;
        int x = left + Math.min(row.depth * 10, 70);
        if (mouseX < x) return;
        if (mouseX < x + 16 && !requirement.getChildren()
            .isEmpty()) {
            if (!collapsed.add(requirement.getId())) collapsed.remove(requirement.getId());
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
