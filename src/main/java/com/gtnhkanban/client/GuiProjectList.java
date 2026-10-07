package com.gtnhkanban.client;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SCreateProject;
import com.gtnhkanban.network.message.C2SDeleteProject;
import com.gtnhkanban.network.message.C2SFetchBoard;

public final class GuiProjectList extends GuiKanbanScreen {

    private static final int PAGE_SIZE = 7;
    private static final int CREATE = 1;
    private static final int PREVIOUS = 2;
    private static final int NEXT = 3;

    private static final int TAB_PROJECTS = 10, TAB_SETTINGS = 11;

    private GuiTextField nameField;
    private int page;
    private KanbanFrame.Tabs tabs = new KanbanFrame.Tabs();

    public GuiProjectList() {
        super(null);
    }

    public GuiProjectList(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(CREATE, width / 2 - 100, height - 54, 120, 20, "Create project"));
        buttonList.add(new GuiButton(PREVIOUS, width / 2 + 25, height - 54, 35, 20, "<"));
        buttonList.add(new GuiButton(NEXT, width / 2 + 65, height - 54, 35, 20, ">"));
        nameField = new GuiTextField(fontRendererObj, width / 2 - 100, height - 82, 200, 20);
        nameField.setMaxStringLength(64);
        tabs = new KanbanFrame.Tabs().add(TAB_PROJECTS, "Projects", null, true, KanbanFrame.Color.PURPLE)
            .addRight(TAB_SETTINGS, "Board settings", null, true);
        tabs.layout(fontRendererObj, width);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CREATE) {
            createProject();
        } else if (button.id == PREVIOUS) {
            page = Math.max(0, page - 1);
        } else if (button.id == NEXT) {
            page = Math.min(lastPage(), page + 1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        KanbanFrame.Tab tab = mouseButton == 0 ? tabs.at(mouseX, mouseY) : null;
        if (tab != null) {
            if (tab.id == TAB_SETTINGS) mc.displayGuiScreen(new GuiBoardSettings(this));
            return;
        }
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || mouseX < width / 2 - 135 || mouseX > width / 2 + 110) {
            return;
        }
        int row = (mouseY - 48) / 20;
        if (mouseY >= 48 && row >= 0) {
            List<ProjectSummary> visible = PagedList.pageItems(KanbanClientState.getProjects(), page, PAGE_SIZE);
            if (row < visible.size()) {
                ProjectSummary project = visible.get(row);
                if (mouseX >= width / 2 + 72 && project.isActorIsOwner()) {
                    mc.displayGuiScreen(
                        new GuiConfirmAction(this, "Delete project '" + project.getName() + "'?", new Runnable() {

                            @Override
                            public void run() {
                                KanbanNetwork.CHANNEL.sendToServer(new C2SDeleteProject(project.getId()));
                            }
                        }));
                } else if (mouseX < width / 2 + 60) {
                    KanbanNetwork.CHANNEL.sendToServer(new C2SFetchBoard(project.getId()));
                    mc.displayGuiScreen(new GuiKanbanBoard(project.getId(), this));
                }
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (handleEscape(keyCode)) return;
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            createProject();
            return;
        }
        if (!nameField.textboxKeyTyped(typedChar, keyCode)) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        KanbanFrame.drawWindow(mc, width, height);
        tabs.draw(mc, mouseX, mouseY);
        page = Math.min(page, lastPage());
        List<ProjectSummary> projects = PagedList.pageItems(KanbanClientState.getProjects(), page, PAGE_SIZE);
        if (projects.isEmpty()) {
            drawCenteredString(fontRendererObj, "No projects yet. Create one below.", width / 2, 54, 0xAAAAAA);
        }
        for (int index = 0; index < projects.size(); index++) {
            ProjectSummary project = projects.get(index);
            net.minecraft.item.ItemStack icon = GuiKanbanBoard.iconStack(project.getIcon());
            if (icon != null) {
                itemRender.renderItemAndEffectIntoGUI(
                    fontRendererObj,
                    mc.getTextureManager(),
                    icon,
                    width / 2 - 145,
                    46 + index * 20);
                net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
                org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            }
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(project.getName(), 130),
                width / 2 - 125,
                50 + index * 20,
                0xFFFFFF);
            drawString(
                fontRendererObj,
                project.isActorIsOwner() ? "Owner" : "Member",
                width / 2 + 12,
                50 + index * 20,
                0xAAAAAA);
            if (project.isActorIsOwner()) {
                drawString(fontRendererObj, "Delete", width / 2 + 72, 50 + index * 20, 0xFF7777);
            }
        }
        drawString(fontRendererObj, "Project name (64 characters max)", width / 2 - 100, height - 96, 0xAAAAAA);
        drawString(
            fontRendererObj,
            "Page " + (page + 1) + " / " + (lastPage() + 1),
            width / 2 - 22,
            height - 29,
            0xFFFFFF);
        drawResult();
        nameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
        String tabTip = tabs.tooltip(mouseX, mouseY);
        if (tabTip != null)
            drawHoveringText(java.util.Collections.singletonList(tabTip), mouseX, mouseY, fontRendererObj);
    }

    private int lastPage() {
        return PagedList.pageCount(
            KanbanClientState.getProjects()
                .size(),
            PAGE_SIZE) - 1;
    }

    private void createProject() {
        if (nameField != null && !nameField.getText()
            .trim()
            .isEmpty()) {
            KanbanNetwork.CHANNEL.sendToServer(new C2SCreateProject(nameField.getText()));
        }
    }

    private void drawResult() {
        String result = KanbanClientState.getResultMessage();
        if (!result.isEmpty()) {
            drawCenteredString(
                fontRendererObj,
                result,
                width / 2,
                height - 106,
                KanbanClientState.isResultSuccess() ? 0x55FF55 : 0xFF5555);
        }
    }
}
