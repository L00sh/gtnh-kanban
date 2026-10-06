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
import com.gtnhkanban.api.RequirementView;
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
            return;
        }
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
        java.util.List<MaterialLeaves.Leaf> leaves = MaterialLeaves.of(card.getRequirements());
        int itemCount = leaves.size();
        int lines = Math.min(itemCount, 5);
        int panelHeight = 28 + lines * 22 + (itemCount > lines ? 12 : 0);
        Gui.drawRect(x, y, x + panelWidth, y + panelHeight, 0xAA111111);
        minecraft.fontRenderer.drawStringWithShadow(
            minecraft.fontRenderer.trimStringToWidth("Kanban: " + card.getTitle(), panelWidth - 10),
            x + 5,
            y + 5,
            0xFFFFFF);
        int drawn = 0;
        for (MaterialLeaves.Leaf leaf : leaves) {
            RequirementView requirement = leaf.requirement;
            if (drawn == lines) break;
            int available = requirement.getItem()
                .isFluid() ? 0 : inventoryCount(minecraft, requirement);
            int color = !requirement.getItem()
                .isFluid() && available >= requirement.getQuantity() ? 0x55FF55
                    : requirement.isComplete() ? 0xAAAAAA : 0xDDDDDD;
            String marker = requirement.isComplete() ? "[x] " : "[ ] ";
            String quantity = requirement.getItem()
                .isFluid() ? " " + requirement.getQuantity() + " mB"
                    : " " + available + "/" + requirement.getQuantity();
            int nameWidth = panelWidth - 14
                - minecraft.fontRenderer.getStringWidth(marker)
                - minecraft.fontRenderer.getStringWidth(quantity);
            String label = marker
                + minecraft.fontRenderer.trimStringToWidth(MaterialDisplay.name(requirement.getItem()), nameWidth)
                + quantity;
            minecraft.fontRenderer.drawStringWithShadow(label, x + 9, y + 18 + drawn * 22, color);
            String context = leaf.context.isEmpty() ? "" : "for " + leaf.context;
            if (requirement.isReusable()) context = "Reusable" + (context.isEmpty() ? "" : "; " + context);
            if (!context.isEmpty()) minecraft.fontRenderer.drawStringWithShadow(
                minecraft.fontRenderer.trimStringToWidth(context, panelWidth - 18),
                x + 9,
                y + 28 + drawn * 22,
                0x999999);
            drawn++;
        }
        if (itemCount > lines) {
            minecraft.fontRenderer
                .drawStringWithShadow("+" + (itemCount - lines) + " more items", x + 9, y + 18 + lines * 22, 0xAAAAAA);
        }
    }

    private int inventoryCount(Minecraft minecraft, RequirementView requirement) {
        Object registered = Item.itemRegistry.getObject(
            requirement.getItem()
                .getRegistryName());
        if (!(registered instanceof Item) || minecraft.thePlayer == null) return 0;
        int count = 0;
        for (ItemStack stack : minecraft.thePlayer.inventory.mainInventory) {
            if (MaterialDisplay.matches(stack, requirement.getItem())) count += stack.stackSize;
        }
        for (ItemStack stack : minecraft.thePlayer.inventory.armorInventory) {
            if (MaterialDisplay.matches(stack, requirement.getItem())) count += stack.stackSize;
        }
        Container openContainer = minecraft.thePlayer.openContainer;
        if (minecraft.currentScreen instanceof GuiContainer && openContainer != null
            && openContainer != minecraft.thePlayer.inventoryContainer) {
            for (Object entry : openContainer.inventorySlots) {
                Slot slot = (Slot) entry;
                // Player slots are also present in chest containers and were counted above.
                if (slot.inventory == minecraft.thePlayer.inventory) continue;
                ItemStack stack = slot.getStack();
                if (MaterialDisplay.matches(stack, requirement.getItem())) count += stack.stackSize;
            }
        }
        return count;
    }

}
