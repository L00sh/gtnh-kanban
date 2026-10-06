package com.gtnhkanban.proxy;

import java.util.List;

import net.minecraft.client.Minecraft;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.client.BreakdownJobs;
import com.gtnhkanban.client.GuiProjectList;
import com.gtnhkanban.client.KanbanClientControls;
import com.gtnhkanban.client.KanbanClientState;
import com.gtnhkanban.network.KanbanNetwork;

import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        KanbanNetwork.registerClientMessages();
        KanbanClientControls.register();
    }

    @Override
    public void openProjectList() {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    Minecraft.getMinecraft()
                        .displayGuiScreen(new GuiProjectList());
                }
            });
    }

    @Override
    public void receiveProjectList(final List<ProjectSummary> projects) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    KanbanClientState.setProjects(projects);
                }
            });
    }

    @Override
    public void receiveBoard(final BoardSnapshot snapshot) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    KanbanClientState.setBoard(snapshot);
                    BreakdownJobs.onBoard();
                }
            });
    }

    @Override
    public void receiveOperationResult(final boolean success, final String code, final String message) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    KanbanClientState.setResult(success, code, message);
                    BreakdownJobs.onServerResult();
                }
            });
    }
}
