package com.gtnhkanban.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.gtnhkanban.network.KanbanNetwork;

public final class KanbanCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "kanban";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/kanban";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.addChatMessage(new ChatComponentText("This command can only be used by a player."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        if (!KanbanNetwork.supportsClient(player)) {
            sender.addChatMessage(
                new ChatComponentText(
                    "Install the server's version of GTNH Kanban on your client to use the board, checklist and HUD. You can still play without it."));
            return;
        }
        KanbanNetwork.requestProjectList(player);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }
}
