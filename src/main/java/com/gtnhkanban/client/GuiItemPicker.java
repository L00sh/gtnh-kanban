package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SAddRequirement;

import codechicken.nei.ItemList;

public final class GuiItemPicker extends GuiKanbanScreen {

    private static final int PAGE_SIZE = 18;
    private static final int BACK = 1;
    private static final int PREVIOUS = 2;
    private static final int NEXT = 3;

    private final UUID projectId;
    private final UUID cardId;
    private GuiTextField quantityField;
    private GuiTextField searchField;
    private List<ItemStack> filteredItems = new ArrayList<ItemStack>();
    private String lastSearch = "";
    private int page;

    public GuiItemPicker(UUID projectId, UUID cardId) {
        this(projectId, cardId, null);
    }

    public GuiItemPicker(UUID projectId, UUID cardId, GuiScreen parent) {
        super(parent);
        this.projectId = projectId;
        this.cardId = cardId;
        refreshBoardWhileOpen(projectId);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(BACK, width / 2 - 115, height - 28, 60, 20, "Back"));
        buttonList.add(new GuiButton(PREVIOUS, width / 2 + 5, height - 28, 35, 20, "<"));
        buttonList.add(new GuiButton(NEXT, width / 2 + 45, height - 28, 35, 20, ">"));
        quantityField = new GuiTextField(fontRendererObj, width / 2 - 40, height - 58, 80, 20);
        quantityField.setText("1");
        quantityField.setMaxStringLength(10);
        searchField = new GuiTextField(fontRendererObj, width / 2 - 95, 34, 220, 18);
        searchField.setMaxStringLength(64);
        refreshItems();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK) {
            goBack();
        } else if (button.id == PREVIOUS) {
            page = Math.max(0, page - 1);
        } else if (button.id == NEXT) {
            page = Math.min(lastPage(), page + 1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        quantityField.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || mouseY < 62 || mouseY >= height - 66) return;
        List<ItemStack> visible = PagedList.pageItems(filteredItems, page, PAGE_SIZE);
        int row = (mouseY - 62) / 22;
        int column = (mouseX - (width / 2 - 150)) / 200;
        int index = row * 2 + column;
        if (column >= 0 && column < 2 && row >= 0 && index < visible.size()) addItem(visible.get(index));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (handleEscape(keyCode)) return;
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            refreshItems();
            page = 0;
        } else if (!quantityField.textboxKeyTyped(typedChar, keyCode)) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Select an NEI item (items are not consumed)", width / 2, 18, 0xFFFFFF);
        drawString(fontRendererObj, "Search", width / 2 - 145, 39, 0xAAAAAA);
        searchField.drawTextBox();
        drawString(fontRendererObj, "Quantity", width / 2 - 90, height - 53, 0xAAAAAA);
        quantityField.drawTextBox();
        List<ItemStack> visible = PagedList.pageItems(filteredItems, page, PAGE_SIZE);
        for (int index = 0; index < visible.size(); index++) {
            ItemStack stack = visible.get(index);
            int column = index % 2;
            int row = index / 2;
            int x = width / 2 - 150 + column * 200;
            int y = 62 + row * 22;
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(stack.getDisplayName(), 175),
                x + 20,
                y + 4,
                0xFFFFFF);
        }
        if (filteredItems.isEmpty()) {
            drawCenteredString(fontRendererObj, "No matching NEI items are available.", width / 2, 72, 0xAAAAAA);
        }
        drawCenteredString(
            fontRendererObj,
            "Page " + (page + 1) + " / " + (lastPage() + 1) + "  (" + filteredItems.size() + " items)",
            width / 2,
            height - 30,
            0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void addItem(ItemStack selected) {
        try {
            int quantity = Integer.parseInt(quantityField.getText());
            Object registryName = Item.itemRegistry.getNameForObject(selected.getItem());
            if (quantity > 0 && registryName != null) {
                KanbanNetwork.CHANNEL.sendToServer(
                    new C2SAddRequirement(
                        projectId,
                        cardId,
                        new ItemKey(registryName.toString(), selected.getItemDamage()),
                        quantity));
                goBack();
            }
        } catch (NumberFormatException ignored) {
            KanbanClientState.setResult(false, "INVALID_QUANTITY", "Quantity must be a positive whole number.");
        }
    }

    private void refreshItems() {
        String query = searchField == null ? ""
            : searchField.getText()
                .trim()
                .toLowerCase(Locale.ROOT);
        if (query.equals(lastSearch) && !filteredItems.isEmpty()) return;
        lastSearch = query;
        filteredItems = new ArrayList<ItemStack>();
        for (ItemStack stack : ItemList.items) {
            if (stack == null || stack.getItem() == null) continue;
            Object registryName = Item.itemRegistry.getNameForObject(stack.getItem());
            if (query.isEmpty() || stack.getDisplayName()
                .toLowerCase(Locale.ROOT)
                .contains(query)
                || (registryName != null && registryName.toString()
                    .toLowerCase(Locale.ROOT)
                    .contains(query))) {
                filteredItems.add(stack);
            }
        }
    }

    private int lastPage() {
        return PagedList.pageCount(filteredItems.size(), PAGE_SIZE) - 1;
    }
}
