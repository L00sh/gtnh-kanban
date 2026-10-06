package com.gtnhkanban.proxy;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.network.KanbanNetwork;

import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        KanbanNetwork.registerClientMessages();
    }

    @Override
    public void openProjectList() {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    Minecraft.getMinecraft()
                        .displayGuiScreen(new GuiScreen() {});
                }
            });
    }

    @Override
    public void receiveProjectList(final List<ProjectSummary> projects) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() { /* Project list screen consumes this state in the GUI task. */ }
            });
    }

    @Override
    public void receiveBoard(final BoardSnapshot snapshot) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() { /* Board screen consumes this state in the GUI task. */ }
            });
    }

    @Override
    public void receiveOperationResult(final boolean success, final String code, final String message) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() { /* GUI task presents operation results to the player. */ }
            });
    }
}
