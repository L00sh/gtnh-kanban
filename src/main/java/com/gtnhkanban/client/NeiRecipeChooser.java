package com.gtnhkanban.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;

import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.RecipePlan;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SExpandRequirement;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.RecipeHandlerRef;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Chooses a card item's recipe in NEI's own recipe screen, the one its "R" key opens: every tab, layout and
 * ingredient exactly as NEI shows them. While choosing, each recipe that makes the item gets a "use this recipe"
 * button beside NEI's own recipe buttons. Ingredients with several options are switched the NEI way, by scrolling over
 * them, and the option on screen is the one used.
 */
public final class NeiRecipeChooser {

    /** The card item being chosen for; null when not choosing. */
    private static Session session;

    private static final class Session {

        final UUID projectId, cardId;
        final RequirementView requirement;
        final GuiScreen returnTo;

        Session(UUID projectId, UUID cardId, RequirementView requirement, GuiScreen returnTo) {
            this.projectId = projectId;
            this.cardId = cardId;
            this.requirement = requirement;
            this.returnTo = returnTo;
        }
    }

    /** Opens NEI's recipes for the row's item in choosing mode; returns to {@code returnTo} when done or closed. */
    static void open(UUID projectId, UUID cardId, RequirementView requirement, GuiScreen returnTo) {
        ItemStack stack = MaterialDisplay.stack(requirement.getItem());
        GuiRecipe<?> gui = stack == null ? null : GuiCraftingRecipe.createRecipeGui("item", false, stack);
        if (gui == null) {
            KanbanClientState.setResult(
                false,
                "NO_RECIPES",
                "NEI has no recipes for " + MaterialDisplay.name(requirement.getItem()) + ".");
            return;
        }
        session = new Session(projectId, cardId, requirement, returnTo);
        Minecraft.getMinecraft()
            .displayGuiScreen(gui);
    }

    /** Adds the "use this recipe" button to each recipe that makes the item being chosen for. */
    @SubscribeEvent
    public void onRecipeButtons(GuiRecipeButton.UpdateRecipeButtonsEvent.Post event) {
        Session current = session;
        if (current == null || event.recipeWidget == null) return;
        RecipeHandlerRef ref = event.recipeWidget.getRecipeHandlerRef();
        if (ref == null || !(ref.handler instanceof ICraftingHandler)
            || !NeiRecipeCatalog.makes((ICraftingHandler) ref.handler, ref.recipeIndex, current.requirement.getItem()))
            return;
        // NEI stacks its buttons upwards from the recipe's bottom right corner; ours goes on top.
        int x = Math.min(166, event.recipeWidget.w) - GuiRecipeButton.BUTTON_WIDTH;
        int y = event.recipeWidget.h - GuiRecipeButton.BUTTON_HEIGHT - 6;
        for (GuiRecipeButton button : event.buttonList) y = Math.min(y, button.yPosition - 13);
        event.buttonList.add(new UseButton(ref, x, y));
    }

    /** Leaving NEI ends choosing; closing NEI goes back to the card instead of out of the screens. */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        Session current = session;
        if (current == null || event.gui instanceof GuiRecipe) return;
        session = null;
        if (event.gui == null && Minecraft.getMinecraft().currentScreen instanceof GuiRecipe)
            event.gui = current.returnTo;
    }

    static void clear() {
        session = null;
    }

    private static void use(RecipeHandlerRef ref) {
        Session current = session;
        if (current == null) return;
        RequirementView requirement = current.requirement;
        NeiRecipeCatalog.Choice choice = NeiRecipeCatalog
            .choice((ICraftingHandler) ref.handler, ref.recipeIndex, requirement.getItem(), true);
        String problem = null;
        RecipePlan plan = null;
        if (choice == null) problem = "That recipe does not make " + MaterialDisplay.name(requirement.getItem()) + ".";
        else if (!choice.error.isEmpty()) problem = choice.error;
        else {
            try {
                plan = choice.plan(new int[choice.inputs.size()]);
                if (!C2SExpandRequirement.fitsPacket(
                    current.projectId,
                    current.cardId,
                    requirement.getId(),
                    requirement.getQuantity(),
                    requirement.getRevision(),
                    plan)) problem = "This recipe has too much ingredient data to synchronize.";
            } catch (IllegalArgumentException exception) {
                problem = exception.getMessage();
            }
        }
        session = null;
        if (problem != null) KanbanClientState.setResult(false, "RECIPE_UNUSABLE", problem);
        else {
            KanbanNetwork.CHANNEL.sendToServer(
                new C2SExpandRequirement(
                    current.projectId,
                    current.cardId,
                    requirement.getId(),
                    requirement.getQuantity(),
                    requirement.getRevision(),
                    plan));
            KanbanClientState.setResult(
                true,
                "",
                "Using " + choice.name + " for " + MaterialDisplay.name(requirement.getItem()) + ".");
        }
        Minecraft.getMinecraft()
            .displayGuiScreen(current.returnTo);
    }

    /** NEI-styled 12x12 recipe button with a green tick. */
    private static final class UseButton extends GuiRecipeButton {

        UseButton(RecipeHandlerRef ref, int x, int y) {
            super(ref, x, y, 0, "✔");
        }

        @Override
        protected int getTextColour(boolean hovered) {
            return hovered ? 0x9CFF9C : 0x55DD55;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            use(handlerRef);
        }

        @Override
        public List<String> handleTooltip(List<String> tooltip) {
            Session current = session;
            if (current != null) {
                tooltip.add("Use this recipe for " + MaterialDisplay.name(current.requirement.getItem()));
                tooltip.add("§7Uses the ingredients shown; scroll over one");
                tooltip.add("§7to choose between its options.");
                if (!current.requirement.getChildren()
                    .isEmpty()) tooltip.add("§6Replaces the item's current materials.");
            }
            return tooltip;
        }

        @Override
        public Map<String, String> handleHotkeys(int mouseX, int mouseY, Map<String, String> hotkeys) {
            return hotkeys;
        }

        @Override
        public void lastKeyTyped(char typedChar, int keyCode) {}

        @Override
        public void drawItemOverlay() {}
    }
}
