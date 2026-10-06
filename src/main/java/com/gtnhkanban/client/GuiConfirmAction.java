package com.gtnhkanban.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

final class GuiConfirmAction extends GuiKanbanScreen {

    private static final int CONFIRM = 1;
    private static final int CANCEL = 2;

    private final String prompt;
    private final Runnable action;

    GuiConfirmAction(GuiScreen parent, String prompt, Runnable action) {
        super(parent);
        this.prompt = prompt;
        this.action = action;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(CONFIRM, width / 2 - 90, height / 2 + 18, 80, 20, "Delete"));
        buttonList.add(new GuiButton(CANCEL, width / 2 + 10, height / 2 + 18, 80, 20, "Cancel"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CONFIRM) action.run();
        if (button.id == CONFIRM || button.id == CANCEL) goBack();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (!handleEscape(keyCode)) super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, prompt, width / 2, height / 2 - 8, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "This cannot be undone.", width / 2, height / 2 + 4, 0xFF8888);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
