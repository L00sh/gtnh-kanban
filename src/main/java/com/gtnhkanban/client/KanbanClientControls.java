package com.gtnhkanban.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

import com.gtnhkanban.api.CardView;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SListProjects;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Client-only key and HUD behavior. */
public final class KanbanClientControls {

    private static final KeyBinding OPEN_BOARD = new KeyBinding(
        "key.gtnhkanban.openBoard",
        Keyboard.KEY_K,
        "key.categories.gtnhkanban");

    private KanbanClientControls() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN_BOARD);
        KanbanClientControls listener = new KanbanClientControls();
        FMLCommonHandler.instance()
            .bus()
            .register(listener);
        MinecraftForge.EVENT_BUS.register(listener);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null) {
            KanbanClientState.clearPinnedCard();
            KanbanClientState.clearBoards();
            OpenProjects.clear();
            BreakdownJobs.clear();
            return;
        }
        BreakdownJobs.tick();
        while (OPEN_BOARD.isPressed()) {
            if (minecraft.thePlayer != null && minecraft.currentScreen == null) {
                KanbanNetwork.CHANNEL.sendToServer(new C2SListProjects());
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        CardView card = KanbanClientState.getPinnedCard();
        if (card == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        int x = 5;
        int y = 5;
        int panelWidth = 190;
        java.util.List<MaterialTotals.Total> totals = MaterialTotals.of(card.getRequirements());
        int itemCount = totals.size();
        int lines = Math.min(itemCount, 8);
        int panelHeight = 28 + lines * 12 + (itemCount > lines ? 12 : 0);
        Gui.drawRect(x, y, x + panelWidth, y + panelHeight, 0xAA111111);
        minecraft.fontRenderer.drawStringWithShadow(
            minecraft.fontRenderer.trimStringToWidth("Kanban: " + card.getTitle(), panelWidth - 10),
            x + 5,
            y + 5,
            0xFFFFFF);
        if (itemCount == 0) minecraft.fontRenderer.drawStringWithShadow("All materials done", x + 9, y + 18, 0x55FF55);
        int drawn = 0;
        for (MaterialTotals.Total total : totals) {
            if (drawn == lines) break;
            boolean fluid = total.material.isFluid();
            long available = fluid ? 0 : inventoryCount(minecraft, total.material);
            int color = !fluid && available >= total.amount ? 0x55FF55 : 0xDDDDDD;
            String quantity = fluid ? " " + total.amount + " mB" : " " + available + "/" + total.amount;
            int nameWidth = panelWidth - 14 - minecraft.fontRenderer.getStringWidth(quantity);
            String label = minecraft.fontRenderer.trimStringToWidth(MaterialDisplay.name(total.material), nameWidth)
                + quantity;
            minecraft.fontRenderer.drawStringWithShadow(label, x + 9, y + 18 + drawn * 12, color);
            drawn++;
        }
        if (itemCount > lines) {
            minecraft.fontRenderer.drawStringWithShadow(
                "+" + (itemCount - lines) + " more materials",
                x + 9,
                y + 18 + lines * 12,
                0xAAAAAA);
        }
    }

    static int inventoryCount(Minecraft minecraft, ItemKey material) {
        Object registered = Item.itemRegistry.getObject(material.getRegistryName());
        if (!(registered instanceof Item) || minecraft.thePlayer == null) return 0;
        int count = 0;
        for (ItemStack stack : minecraft.thePlayer.inventory.mainInventory) {
            if (MaterialDisplay.matches(stack, material)) count += stack.stackSize;
        }
        for (ItemStack stack : minecraft.thePlayer.inventory.armorInventory) {
            if (MaterialDisplay.matches(stack, material)) count += stack.stackSize;
        }
        Container openContainer = minecraft.thePlayer.openContainer;
        if (minecraft.currentScreen instanceof GuiContainer && openContainer != null
            && openContainer != minecraft.thePlayer.inventoryContainer) {
            for (Object entry : openContainer.inventorySlots) {
                Slot slot = (Slot) entry;
                // Player slots are also present in chest containers and were counted above.
                if (slot.inventory == minecraft.thePlayer.inventory) continue;
                ItemStack stack = slot.getStack();
                if (MaterialDisplay.matches(stack, material)) count += stack.stackSize;
            }
        }
        return count;
    }

}
