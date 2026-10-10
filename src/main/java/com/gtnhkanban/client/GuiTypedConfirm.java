package com.gtnhkanban.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

/** Confirms something that cannot be undone by having the player type a name, e.g. the project being deleted. */
final class GuiTypedConfirm extends GuiKanbanScreen {

    private static final int CONFIRM = 1, CANCEL = 2;
    private final String prompt, expected;
    private final Runnable action;
    private GuiTextField field;

    GuiTypedConfirm(GuiScreen parent, String prompt, String expected, Runnable action) {
        super(parent);
        this.prompt = prompt;
        this.expected = expected;
        this.action = action;
    }

    @Override
    public void initGui() {
        String typed = field == null ? "" : field.getText();
        field = new GuiTextField(fontRendererObj, width / 2 - 100, height / 2 + 4, 200, 20);
        field.setMaxStringLength(256);
        field.setText(typed);
        field.setFocused(true);
        buttonList.clear();
        buttonList.add(new GuiButton(CONFIRM, width / 2 - 100, height / 2 + 32, 96, 20, "Delete"));
        buttonList.add(new GuiButton(CANCEL, width / 2 + 4, height / 2 + 32, 96, 20, "Cancel"));
        updateButtons();
    }

    /** The name must match exactly, so a stray click or a near miss cannot confirm. */
    private boolean matches() {
        return field.getText()
            .equals(expected);
    }

    private void updateButtons() {
        for (GuiButton button : buttonList) if (button.id == CONFIRM) button.enabled = matches();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CONFIRM && matches()) {
            action.run();
            goBack();
        } else if (button.id == CANCEL) goBack();
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        field.mouseClicked(x, y, button);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (handleEscape(key)) return;
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && matches()) {
            action.run();
            goBack();
            return;
        }
        if (field.textboxKeyTyped(c, key)) updateButtons();
        else super.keyTyped(c, key);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        field.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(prompt, width - 20),
            width / 2,
            height / 2 - 40,
            0xFFFFFF);
        drawCenteredString(
            fontRendererObj,
            "This cannot be undone. Type the name to confirm:",
            width / 2,
            height / 2 - 26,
            0xFF8888);
        drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(expected, width - 20),
            width / 2,
            height / 2 - 12,
            0xFFFF55);
        field.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
