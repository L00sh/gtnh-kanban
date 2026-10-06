package com.gtnhkanban.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.gtnhkanban.KanbanMod;
import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.network.message.C2SAddMember;
import com.gtnhkanban.network.message.C2SAddRequirement;
import com.gtnhkanban.network.message.C2SCreateCard;
import com.gtnhkanban.network.message.C2SCreateProject;
import com.gtnhkanban.network.message.C2SFetchBoard;
import com.gtnhkanban.network.message.C2SListProjects;
import com.gtnhkanban.network.message.C2SRemoveMember;
import com.gtnhkanban.network.message.C2SSetRequirementComplete;
import com.gtnhkanban.network.message.C2SUpdateCard;
import com.gtnhkanban.network.message.KanbanRequest;
import com.gtnhkanban.network.message.RequestType;
import com.gtnhkanban.network.message.S2CBoardSnapshot;
import com.gtnhkanban.network.message.S2COpenProjectList;
import com.gtnhkanban.network.message.S2COperationResult;
import com.gtnhkanban.network.message.S2CProjectList;
import com.gtnhkanban.service.ItemResolver;
import com.gtnhkanban.service.KanbanService;
import com.gtnhkanban.service.OperationResult;
import com.gtnhkanban.service.ProfileResolver;
import com.gtnhkanban.storage.KanbanWorldData;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class KanbanNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(KanbanMod.MODID);

    private KanbanNetwork() {}

    public static void registerMessages() {
        KanbanServerTaskQueue.register();
        CHANNEL.registerMessage(new ListProjectsHandler(), C2SListProjects.class, 0, Side.SERVER);
        CHANNEL.registerMessage(new CreateProjectHandler(), C2SCreateProject.class, 1, Side.SERVER);
        CHANNEL.registerMessage(new AddMemberHandler(), C2SAddMember.class, 2, Side.SERVER);
        CHANNEL.registerMessage(new RemoveMemberHandler(), C2SRemoveMember.class, 3, Side.SERVER);
        CHANNEL.registerMessage(new FetchBoardHandler(), C2SFetchBoard.class, 4, Side.SERVER);
        CHANNEL.registerMessage(new CreateCardHandler(), C2SCreateCard.class, 5, Side.SERVER);
        CHANNEL.registerMessage(new UpdateCardHandler(), C2SUpdateCard.class, 6, Side.SERVER);
        CHANNEL.registerMessage(new AddRequirementHandler(), C2SAddRequirement.class, 7, Side.SERVER);
        CHANNEL.registerMessage(new SetRequirementCompleteHandler(), C2SSetRequirementComplete.class, 8, Side.SERVER);
    }

    public static void registerClientMessages() {
        CHANNEL.registerMessage(new ClientOpenHandler(), S2COpenProjectList.class, 9, Side.CLIENT);
        CHANNEL.registerMessage(new ClientProjectListHandler(), S2CProjectList.class, 10, Side.CLIENT);
        CHANNEL.registerMessage(new ClientBoardHandler(), S2CBoardSnapshot.class, 11, Side.CLIENT);
        CHANNEL.registerMessage(new ClientResultHandler(), S2COperationResult.class, 12, Side.CLIENT);
    }

    public static void requestProjectList(EntityPlayerMP player) {
        CHANNEL.sendTo(new S2COpenProjectList(), player);
        KanbanService service = service(player.worldObj);
        CHANNEL.sendTo(new S2CProjectList(service.listAccessibleProjects(player.getUniqueID())), player);
    }

    private static KanbanService service(final World world) {
        return new KanbanService(KanbanWorldData.get(world), new ProfileResolver() {

            @Override
            public UUID resolveUsername(String username) {
                String[] knownUsernames = MinecraftServer.getServer()
                    .func_152358_ax()
                    .func_152654_a();
                boolean known = false;
                for (String knownUsername : knownUsernames) {
                    if (knownUsername.equalsIgnoreCase(username)) {
                        known = true;
                        break;
                    }
                }
                if (!known) return null;
                GameProfile profile = MinecraftServer.getServer()
                    .func_152358_ax()
                    .func_152655_a(username);
                return profile == null ? null : profile.getId();
            }

            @Override
            public String usernameFor(UUID playerId) {
                GameProfile profile = MinecraftServer.getServer()
                    .func_152358_ax()
                    .func_152652_a(playerId);
                return profile == null || profile.getName() == null ? playerId.toString() : profile.getName();
            }
        }, new ItemResolver() {

            @Override
            public boolean isRegistered(ItemKey item) {
                return Item.itemRegistry.getObject(item.getRegistryName()) != null;
            }
        });
    }

    private static class ServerRequestHandler implements IMessageHandler<KanbanRequest, IMessage> {

        @Override
        public IMessage onMessage(final KanbanRequest request, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            KanbanServerTaskQueue.enqueue(new Runnable() {

                @Override
                public void run() {
                    try {
                        process(player, request);
                    } catch (RuntimeException exception) {
                        FMLLog.severe("GTNH Kanban request failed: %s", exception.toString());
                        CHANNEL.sendTo(
                            new S2COperationResult(false, "INTERNAL_ERROR", "The request could not be completed."),
                            player);
                    }
                }
            });
            return null;
        }

        private void process(EntityPlayerMP player, KanbanRequest request) {
            KanbanService service = service(player.worldObj);
            UUID actorId = player.getUniqueID();
            if (request.getType() == RequestType.LIST_PROJECTS) {
                requestProjectList(player);
                return;
            }
            if (request.getType() == RequestType.CREATE_PROJECT) {
                OperationResult<ProjectSummary> result = service
                    .createProject(actorId, bounded(request.getFirstText(), 64));
                sendResult(player, result);
                if (result.isSuccess()) requestProjectList(player);
                return;
            }
            if (request.getType() == RequestType.FETCH_BOARD) {
                OperationResult<BoardSnapshot> result = service.getBoard(actorId, request.getProjectId());
                sendResult(player, result);
                if (result.isSuccess()) CHANNEL.sendTo(new S2CBoardSnapshot(result.getValue()), player);
                return;
            }

            OperationResult<?> result;
            UUID projectId = request.getProjectId();
            switch (request.getType()) {
                case ADD_MEMBER:
                    result = service.addMember(actorId, projectId, bounded(request.getFirstText(), 16));
                    break;
                case REMOVE_MEMBER:
                    result = service.removeMember(actorId, projectId, request.getMemberId());
                    break;
                case CREATE_CARD:
                    result = service.createCard(
                        actorId,
                        projectId,
                        bounded(request.getFirstText(), 64),
                        bounded(request.getSecondText(), 512));
                    break;
                case UPDATE_CARD:
                    result = service.updateCard(
                        actorId,
                        projectId,
                        request.getCardId(),
                        bounded(request.getFirstText(), 64),
                        bounded(request.getSecondText(), 512),
                        request.getStatus());
                    break;
                case ADD_REQUIREMENT:
                    result = service.addRequirement(
                        actorId,
                        projectId,
                        request.getCardId(),
                        request.getItem(),
                        request.getQuantity());
                    break;
                case SET_REQUIREMENT_COMPLETE:
                    result = service.setRequirementComplete(
                        actorId,
                        projectId,
                        request.getCardId(),
                        request.getEntryId(),
                        request.isComplete());
                    break;
                default:
                    CHANNEL.sendTo(
                        new S2COperationResult(false, "INVALID_REQUEST", "That request is not supported."),
                        player);
                    return;
            }
            sendResult(player, result);
            if (result.isSuccess()) broadcastBoard(projectId);
        }

        private String bounded(String value, int maximum) {
            return value == null || value.length() > maximum ? null : value;
        }

        private void sendResult(EntityPlayerMP player, OperationResult<?> result) {
            CHANNEL
                .sendTo(new S2COperationResult(result.isSuccess(), result.getErrorCode(), result.getMessage()), player);
        }

        private void broadcastBoard(UUID projectId) {
            MinecraftServer server = MinecraftServer.getServer();
            for (Object object : server.getConfigurationManager().playerEntityList) {
                EntityPlayerMP recipient = (EntityPlayerMP) object;
                OperationResult<BoardSnapshot> result = service(recipient.worldObj)
                    .getBoard(recipient.getUniqueID(), projectId);
                if (result.isSuccess()) CHANNEL.sendTo(new S2CBoardSnapshot(result.getValue()), recipient);
            }
        }
    }

    private static final class ListProjectsHandler extends ServerRequestHandler {
    }

    private static final class CreateProjectHandler extends ServerRequestHandler {
    }

    private static final class AddMemberHandler extends ServerRequestHandler {
    }

    private static final class RemoveMemberHandler extends ServerRequestHandler {
    }

    private static final class FetchBoardHandler extends ServerRequestHandler {
    }

    private static final class CreateCardHandler extends ServerRequestHandler {
    }

    private static final class UpdateCardHandler extends ServerRequestHandler {
    }

    private static final class AddRequirementHandler extends ServerRequestHandler {
    }

    private static final class SetRequirementCompleteHandler extends ServerRequestHandler {
    }

    private static final class ClientOpenHandler implements IMessageHandler<S2COpenProjectList, IMessage> {

        @Override
        public IMessage onMessage(S2COpenProjectList message, MessageContext context) {
            KanbanMod.proxy.openProjectList();
            return null;
        }
    }

    private static final class ClientProjectListHandler implements IMessageHandler<S2CProjectList, IMessage> {

        @Override
        public IMessage onMessage(S2CProjectList message, MessageContext context) {
            KanbanMod.proxy.receiveProjectList(message.getProjects());
            return null;
        }
    }

    private static final class ClientBoardHandler implements IMessageHandler<S2CBoardSnapshot, IMessage> {

        @Override
        public IMessage onMessage(S2CBoardSnapshot message, MessageContext context) {
            KanbanMod.proxy.receiveBoard(message.getSnapshot());
            return null;
        }
    }

    private static final class ClientResultHandler implements IMessageHandler<S2COperationResult, IMessage> {

        @Override
        public IMessage onMessage(S2COperationResult message, MessageContext context) {
            KanbanMod.proxy.receiveOperationResult(message.isSuccess(), message.getCode(), message.getMessage());
            return null;
        }
    }
}
