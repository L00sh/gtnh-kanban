package com.gtnhkanban.proxy;

import java.util.List;

import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.ProjectSummary;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {}

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}

    public void openProjectList() {}

    public void receiveProjectList(List<ProjectSummary> projects) {}

    public void receiveBoard(BoardSnapshot snapshot) {}

    public void receiveOperationResult(boolean success, String code, String message) {}

    public void receivePlayerNames(java.util.UUID projectId, List<String> names) {}

    public void receiveActivity(com.gtnhkanban.api.ActivityLog log) {}
}
