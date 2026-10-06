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
        KanbanNetwork.requestProjectList((EntityPlayerMP) sender);
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
