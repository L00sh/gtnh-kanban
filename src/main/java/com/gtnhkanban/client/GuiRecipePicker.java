package com.gtnhkanban.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.ItemRequirement;
import com.gtnhkanban.model.RecipeIngredient;
import com.gtnhkanban.model.RecipePlan;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SExpandRequirement;

final class GuiRecipePicker extends GuiKanbanScreen {

    private final UUID projectId;
    private final UUID cardId;
    private final RequirementView requirement;
    private List<NeiRecipeCatalog.Choice> recipes;
    private int recipeIndex;
    private int page;
    private int[] selection;
    private String error = "";
    private static final int BACK = 1, PREVIOUS = 2, NEXT = 3, APPLY = 4, CLEAR = 5, PAGE_PREVIOUS = 6, PAGE_NEXT = 7;

    GuiRecipePicker(UUID projectId, UUID cardId, RequirementView requirement, GuiScreen parent) {
        super(parent);
        this.projectId = projectId;
        this.cardId = cardId;
        this.requirement = requirement;
        refreshBoardWhileOpen(projectId);
    }

    private int pageSize() {
        return Math.max(1, (height - 185) / 24);
    }

    @Override
    public void initGui() {
        if (recipes == null) {
            try {
                recipes = NeiRecipeCatalog.find(requirement.getItem());
            } catch (RuntimeException exception) {
                recipes = java.util.Collections.emptyList();
                error = "NEI recipe lookup failed. Try again after item loading completes.";
            }
        }
        if (!recipes.isEmpty() && selection == null) {
            selection = new int[recipes.get(recipeIndex).inputs.size()];
            for (int i = 0; i < selection.length; i++)
                selection[i] = recipes.get(recipeIndex).inputs.get(i).alternatives.size() == 1 ? 0 : -1;
        }
        buttonList.clear();
        int left = width / 2 - 160;
        buttonList.add(new GuiButton(BACK, left, height - 28, 60, 20, "Back"));
        buttonList.add(new GuiButton(APPLY, left + 65, height - 28, 115, 20, "Use this recipe"));
        buttonList.add(new GuiButton(CLEAR, left + 185, height - 28, 130, 20, "Remove expansion"));
        buttonList.add(new GuiButton(PREVIOUS, left, 40, 30, 20, "<"));
        buttonList.add(new GuiButton(NEXT, left + 285, 40, 30, 20, ">"));
        buttonList.add(new GuiButton(PAGE_PREVIOUS, left + 100, height - 54, 30, 20, "<"));
        buttonList.add(new GuiButton(PAGE_NEXT, left + 190, height - 54, 30, 20, ">"));
        if (!recipes.isEmpty()) {
            NeiRecipeCatalog.Choice choice = recipes.get(recipeIndex);
            for (int i = page * pageSize(); i < Math.min(choice.inputs.size(), (page + 1) * pageSize()); i++) {
                NeiRecipeCatalog.Input input = choice.inputs.get(i);
                String name = selection[i] < 0 ? "Choose ingredient (" + input.alternatives.size() + " options)"
                    : label(input.alternatives.get(selection[i]), choice.output);
                GuiButton button = new GuiButton(
                    100 + i,
                    left,
                    95 + (i % pageSize()) * 24,
                    315,
                    20,
                    fontRendererObj.trimStringToWidth(name, 300));
                button.enabled = input.alternatives.size() > 1;
                buttonList.add(button);
            }
        }
        buttonList.get(1).enabled = canApply();
        buttonList.get(2).enabled = !requirement.getChildren()
            .isEmpty();
        buttonList.get(3).enabled = recipeIndex > 0;
        buttonList.get(4).enabled = recipeIndex + 1 < recipes.size();
        buttonList.get(5).enabled = page > 0;
        buttonList.get(6).enabled = !recipes.isEmpty()
            && (page + 1) * pageSize() < recipes.get(recipeIndex).inputs.size();
    }

    private String label(RecipeIngredient ingredient, int output) {
        try {
            int amount = ItemRequirement
                .childAmount(requirement.getQuantity(), output, ingredient.getAmount(), ingredient.isReusable());
            return MaterialDisplay.name(ingredient.getMaterial()) + "  "
                + amount
                + (ingredient.getMaterial()
                    .isFluid() ? " mB" : " items")
                + (ingredient.isReusable() ? " (reusable)" : "");
        } catch (IllegalArgumentException exception) {
            return "Quantity exceeds supported limit";
        }
    }

    private boolean canApply() {
        if (recipes.isEmpty()) return false;
        try {
            RecipePlan plan = recipes.get(recipeIndex)
                .plan(selection);
            for (RecipeIngredient ingredient : plan.getIngredients()) ItemRequirement.childAmount(
                requirement.getQuantity(),
                plan.getOutputAmount(),
                ingredient.getAmount(),
                ingredient.isReusable());
            return C2SExpandRequirement.fitsPacket(
                projectId,
                cardId,
                requirement.getId(),
                requirement.getQuantity(),
                requirement.getRevision(),
                plan);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BACK) {
            goBack();
            return;
        }
        if (button.id == APPLY || button.id == CLEAR) {
            RecipePlan plan = button.id == CLEAR ? null
                : recipes.get(recipeIndex)
                    .plan(selection);
            if (!C2SExpandRequirement.fitsPacket(
                projectId,
                cardId,
                requirement.getId(),
                requirement.getQuantity(),
                requirement.getRevision(),
                plan)) {
                error = "This recipe has too much ingredient data to synchronize.";
                return;
            }
            KanbanNetwork.CHANNEL.sendToServer(
                new C2SExpandRequirement(
                    projectId,
                    cardId,
                    requirement.getId(),
                    requirement.getQuantity(),
                    requirement.getRevision(),
                    plan));
            goBack();
            return;
        }
        if (button.id == PREVIOUS || button.id == NEXT) {
            recipeIndex += button.id == NEXT ? 1 : -1;
            page = 0;
            selection = null;
        } else if (button.id == PAGE_PREVIOUS || button.id == PAGE_NEXT) page += button.id == PAGE_NEXT ? 1 : -1;
        else if (button.id >= 100) {
            int i = button.id - 100;
            selection[i] = (selection[i] + 1) % recipes.get(recipeIndex).inputs.get(i).alternatives.size();
        }
        initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            "Materials for " + MaterialDisplay.name(requirement.getItem()),
            width / 2,
            18,
            0xFFFFFF);
        if (recipes.isEmpty()) drawCenteredString(
            fontRendererObj,
            error.isEmpty() ? "No NEI recipe is available for this material." : error,
            width / 2,
            75,
            0xFFAAAA);
        else {
            NeiRecipeCatalog.Choice choice = recipes.get(recipeIndex);
            drawCenteredString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(choice.name, 240) + " ("
                    + (recipeIndex + 1)
                    + "/"
                    + recipes.size()
                    + ")",
                width / 2,
                45,
                0xFFFFFF);
            drawCenteredString(
                fontRendererObj,
                "Output/batch: " + choice.output
                    + "; required: "
                    + requirement.getQuantity()
                    + (requirement.getItem()
                        .isFluid() ? " mB" : " items"),
                width / 2,
                69,
                0xAAAAAA);
            String note = choice.error.isEmpty() ? "Choose alternatives by clicking their rows." : choice.error;
            if (!error.isEmpty()) note = error;
            else if (choice.error.isEmpty() && selection != null) {
                boolean allSelected = true;
                for (int selected : selection) if (selected < 0) allSelected = false;
                if (allSelected && !canApply())
                    note = "Recipe quantities or ingredient data exceed the supported limit.";
            }
            drawCenteredString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(note, width - 20),
                width / 2,
                height - 70,
                choice.error.isEmpty() ? 0xAAAAAA : 0xFF7777);
            drawCenteredString(fontRendererObj, "Page " + (page + 1), width / 2, height - 49, 0xAAAAAA);
            if (!requirement.getChildren()
                .isEmpty())
                drawCenteredString(
                    fontRendererObj,
                    "Using a recipe replaces this item's current material branch.",
                    width / 2,
                    82,
                    0xFFCC66);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
