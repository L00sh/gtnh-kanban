package com.gtnhkanban.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fluids.FluidRegistry;

import com.gtnhkanban.KanbanMod;
import com.gtnhkanban.api.BoardSnapshot;
import com.gtnhkanban.api.ProjectSummary;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.MaterialNbt;
import com.gtnhkanban.network.message.C2SAddComment;
import com.gtnhkanban.network.message.C2SAddMember;
import com.gtnhkanban.network.message.C2SAddRequirement;
import com.gtnhkanban.network.message.C2SAddTask;
import com.gtnhkanban.network.message.C2SCreateCard;
import com.gtnhkanban.network.message.C2SCreateProject;
import com.gtnhkanban.network.message.C2SDeleteCard;
import com.gtnhkanban.network.message.C2SDeleteComment;
import com.gtnhkanban.network.message.C2SDeleteProject;
import com.gtnhkanban.network.message.C2SDeleteRequirement;
import com.gtnhkanban.network.message.C2SDeleteTask;
import com.gtnhkanban.network.message.C2SExpandRequirement;
import com.gtnhkanban.network.message.C2SFetchBoard;
import com.gtnhkanban.network.message.C2SListProjects;
import com.gtnhkanban.network.message.C2SMoveCard;
import com.gtnhkanban.network.message.C2SRemoveMember;
import com.gtnhkanban.network.message.C2SSaveSettings;
import com.gtnhkanban.network.message.C2SSetCardAssigned;
import com.gtnhkanban.network.message.C2SSetProjectIcon;
import com.gtnhkanban.network.message.C2SSetRequirementComplete;
import com.gtnhkanban.network.message.C2SSetRequirementQuantity;
import com.gtnhkanban.network.message.C2SSetTaskDone;
import com.gtnhkanban.network.message.C2SUpdateCard;
import com.gtnhkanban.network.message.C2SUploadBreakdown;
import com.gtnhkanban.network.message.KanbanRequest;
import com.gtnhkanban.network.message.RecipeTreeCodec;
import com.gtnhkanban.network.message.RequestType;
import com.gtnhkanban.network.message.S2CBoardSnapshot;
import com.gtnhkanban.network.message.S2COpenProjectList;
import com.gtnhkanban.network.message.S2COperationResult;
import com.gtnhkanban.network.message.S2CProjectList;
import com.gtnhkanban.service.CardFields;
import com.gtnhkanban.service.ItemResolver;
import com.gtnhkanban.service.KanbanService;
import com.gtnhkanban.service.OperationResult;
import com.gtnhkanban.service.ProfileResolver;
import com.gtnhkanban.storage.KanbanStorage;
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
        KanbanClientConnections.register();
        // Server-to-client messages are registered on both physical sides: the server needs their ids to encode
        // them, the client to decode them. FML builds client channels on dedicated servers too, and these handlers
        // only call the proxy, which does nothing there.
        CHANNEL.registerMessage(new ClientOpenHandler(), S2COpenProjectList.class, 9, Side.CLIENT);
        CHANNEL.registerMessage(new ClientProjectListHandler(), S2CProjectList.class, 10, Side.CLIENT);
        CHANNEL.registerMessage(new ClientBoardHandler(), S2CBoardSnapshot.class, 11, Side.CLIENT);
        CHANNEL.registerMessage(new ClientResultHandler(), S2COperationResult.class, 12, Side.CLIENT);
        CHANNEL.registerMessage(new ListProjectsHandler(), C2SListProjects.class, 0, Side.SERVER);
        CHANNEL.registerMessage(new CreateProjectHandler(), C2SCreateProject.class, 1, Side.SERVER);
        CHANNEL.registerMessage(new AddMemberHandler(), C2SAddMember.class, 2, Side.SERVER);
        CHANNEL.registerMessage(new RemoveMemberHandler(), C2SRemoveMember.class, 3, Side.SERVER);
        CHANNEL.registerMessage(new FetchBoardHandler(), C2SFetchBoard.class, 4, Side.SERVER);
        CHANNEL.registerMessage(new CreateCardHandler(), C2SCreateCard.class, 5, Side.SERVER);
        CHANNEL.registerMessage(new UpdateCardHandler(), C2SUpdateCard.class, 6, Side.SERVER);
        CHANNEL.registerMessage(new AddRequirementHandler(), C2SAddRequirement.class, 7, Side.SERVER);
        CHANNEL.registerMessage(new SetRequirementCompleteHandler(), C2SSetRequirementComplete.class, 8, Side.SERVER);
        CHANNEL.registerMessage(new DeleteProjectHandler(), C2SDeleteProject.class, 13, Side.SERVER);
        CHANNEL.registerMessage(new DeleteCardHandler(), C2SDeleteCard.class, 14, Side.SERVER);
        CHANNEL.registerMessage(new MoveCardHandler(), C2SMoveCard.class, 15, Side.SERVER);
        CHANNEL.registerMessage(new DeleteRequirementHandler(), C2SDeleteRequirement.class, 16, Side.SERVER);
        CHANNEL.registerMessage(new SetRequirementQuantityHandler(), C2SSetRequirementQuantity.class, 17, Side.SERVER);
        CHANNEL.registerMessage(new SetCardAssignedHandler(), C2SSetCardAssigned.class, 18, Side.SERVER);
        CHANNEL.registerMessage(new ExpandRequirementHandler(), C2SExpandRequirement.class, 19, Side.SERVER);
        CHANNEL.registerMessage(new UploadBreakdownHandler(), C2SUploadBreakdown.class, 20, Side.SERVER);
        CHANNEL.registerMessage(new SetProjectIconHandler(), C2SSetProjectIcon.class, 21, Side.SERVER);
        CHANNEL.registerMessage(new AddTaskHandler(), C2SAddTask.class, 22, Side.SERVER);
        CHANNEL.registerMessage(new SetTaskDoneHandler(), C2SSetTaskDone.class, 23, Side.SERVER);
        CHANNEL.registerMessage(new DeleteTaskHandler(), C2SDeleteTask.class, 24, Side.SERVER);
        CHANNEL.registerMessage(new AddCommentHandler(), C2SAddComment.class, 25, Side.SERVER);
        CHANNEL.registerMessage(new DeleteCommentHandler(), C2SDeleteComment.class, 26, Side.SERVER);
        CHANNEL.registerMessage(new SaveSettingsHandler(), C2SSaveSettings.class, 27, Side.SERVER);
    }

    /** Drops unfinished uploads from a stopped server. */
    public static void clearUploads() {
        BreakdownUploads.clear();
    }

    public static boolean supportsClient(EntityPlayerMP player) {
        return KanbanClientConnections.supportsKanban(player);
    }

    public static void clearClientConnections() {
        KanbanClientConnections.clear();
    }

    private static void sendToClient(IMessage message, EntityPlayerMP player) {
        if (supportsClient(player)) CHANNEL.sendTo(message, player);
    }

    public static void requestProjectList(EntityPlayerMP player) {
        requestProjectList(player, true);
    }

    private static void requestProjectList(EntityPlayerMP player, boolean openScreen) {
        if (!supportsClient(player)) return;
        if (openScreen) sendToClient(new S2COpenProjectList(), player);
        KanbanService service = service();
        sendToClient(new S2CProjectList(service.listAccessibleProjects(player.getUniqueID())), player);
    }

    private static KanbanService service() {
        return new KanbanService(KanbanStorage.get(), new ProfileResolver() {

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
                try {
                    MaterialNbt.decode(item.getNbt());
                } catch (IllegalArgumentException exception) {
                    return false;
                }
                return item.isFluid() ? FluidRegistry.getFluid(item.getRegistryName()) != null
                    : Item.itemRegistry.getObject(item.getRegistryName()) != null;
            }
        });
    }

    private static class ServerRequestHandler implements IMessageHandler<KanbanRequest, IMessage> {

        @Override
        public IMessage onMessage(final KanbanRequest request, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().playerEntity;
            if (!supportsClient(player)) return null;
            KanbanServerTaskQueue.enqueue(new Runnable() {

                @Override
                public void run() {
                    try {
                        process(player, request);
                    } catch (RuntimeException exception) {
                        FMLLog.severe("GTNH Kanban request failed: %s", exception.toString());
                        sendToClient(
                            new S2COperationResult(false, "INTERNAL_ERROR", "The request could not be completed."),
                            player);
                    }
                }
            });
            return null;
        }

        private void process(EntityPlayerMP player, KanbanRequest request) {
            if (!supportsClient(player)) return;
            KanbanService service = service();
            UUID actorId = player.getUniqueID();
            if (request.getType() != RequestType.LIST_PROJECTS && request.getType() != RequestType.FETCH_BOARD
                && !KanbanStorage.get()
                    .isWritable()) {
                sendToClient(
                    new S2COperationResult(
                        false,
                        "STORAGE_READ_ONLY",
                        "Kanban data for this world could not be loaded, so editing is disabled. See the server log."),
                    player);
                return;
            }
            if (request.getType() == RequestType.LIST_PROJECTS) {
                requestProjectList(player);
                return;
            }
            if (request.getType() == RequestType.CREATE_PROJECT) {
                OperationResult<ProjectSummary> result = service
                    .createProject(actorId, bounded(request.getFirstText(), 64));
                sendResult(player, result);
                if (result.isSuccess()) requestProjectList(player, false);
                return;
            }
            if (request.getType() == RequestType.FETCH_BOARD) {
                OperationResult<BoardSnapshot> result = service.getBoard(actorId, request.getProjectId());
                if (result.isSuccess()) sendToClient(new S2CBoardSnapshot(result.getValue()), player);
                else sendResult(player, result);
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
                    result = service.createCard(actorId, projectId, cardFields(request));
                    break;
                case UPDATE_CARD:
                    result = service.updateCard(actorId, projectId, request.getCardId(), cardFields(request));
                    break;
                case SET_PROJECT_ICON:
                    result = service.setProjectIcon(actorId, projectId, request.getItem());
                    break;
                case ADD_TASK:
                    result = service.addTask(actorId, projectId, request.getCardId(), request.getFirstText());
                    break;
                case SET_TASK_DONE:
                    result = service.setTaskDone(
                        actorId,
                        projectId,
                        request.getCardId(),
                        request.getEntryId(),
                        request.isComplete());
                    break;
                case DELETE_TASK:
                    result = service.deleteTask(actorId, projectId, request.getCardId(), request.getEntryId());
                    break;
                case ADD_COMMENT:
                    result = service.addComment(actorId, projectId, request.getCardId(), request.getFirstText());
                    break;
                case DELETE_COMMENT:
                    result = service.deleteComment(actorId, projectId, request.getCardId(), request.getEntryId());
                    break;
                case SAVE_SETTINGS:
                    C2SSaveSettings settings = (C2SSaveSettings) request;
                    result = service.saveSettings(actorId, settings.getColumns(), settings.getTypes());
                    break;
                case DELETE_PROJECT:
                    result = service.deleteProject(actorId, projectId);
                    break;
                case DELETE_CARD:
                    result = service.deleteCard(actorId, projectId, request.getCardId());
                    break;
                case MOVE_CARD:
                    result = service
                        .moveCard(actorId, projectId, request.getCardId(), request.getColumnId(), request.getEntryId());
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
                case SET_CARD_ASSIGNED:
                    result = service.setCardAssigned(
                        actorId,
                        projectId,
                        request.getCardId(),
                        request.getMemberId(),
                        request.isComplete());
                    break;
                case UPLOAD_BREAKDOWN:
                    C2SUploadBreakdown part = (C2SUploadBreakdown) request;
                    byte[] encoded;
                    try {
                        encoded = BreakdownUploads.accept(actorId, part);
                    } catch (IllegalArgumentException exception) {
                        result = OperationResult.failure("INVALID_UPLOAD", "The breakdown upload was interrupted.");
                        break;
                    }
                    // Earlier parts get no reply; the result is sent once the whole tree has arrived.
                    if (encoded == null) return;
                    try {
                        result = service.applyBreakdown(
                            actorId,
                            projectId,
                            part.getCardId(),
                            part.getEntryId(),
                            part.getItem(),
                            part.getQuantity(),
                            part.getExpectedRevision(),
                            RecipeTreeCodec.decode(encoded));
                    } catch (RuntimeException exception) {
                        result = OperationResult.failure("INVALID_RECIPE", "The breakdown could not be read.");
                    }
                    break;
                case EXPAND_REQUIREMENT:
                    result = service.expandRequirement(
                        actorId,
                        projectId,
                        request.getCardId(),
                        request.getEntryId(),
                        request.getQuantity(),
                        request.getExpectedRevision(),
                        request.getRecipePlan());
                    break;
                case SET_REQUIREMENT_QUANTITY:
                    result = service.setRequirementQuantity(
                        actorId,
                        projectId,
                        request.getCardId(),
                        request.getEntryId(),
                        request.getQuantity());
                    break;
                case DELETE_REQUIREMENT:
                    result = service.deleteRequirement(actorId, projectId, request.getCardId(), request.getEntryId());
                    break;
                default:
                    sendToClient(
                        new S2COperationResult(false, "INVALID_REQUEST", "That request is not supported."),
                        player);
                    return;
            }
            sendResult(player, result);
            if (result.isSuccess()) {
                if (request.getType() == RequestType.DELETE_PROJECT) broadcastProjectLists();
                else if (request.getType() == RequestType.SAVE_SETTINGS) broadcastAllBoards();
                else {
                    if (request.getType() == RequestType.SET_PROJECT_ICON) broadcastProjectLists();
                    broadcastBoard(projectId);
                }
            }
        }

        private CardFields cardFields(KanbanRequest request) {
            return new CardFields(
                bounded(request.getFirstText(), 64),
                bounded(request.getSecondText(), 512),
                request.getColumnId(),
                request.getTypeId(),
                request.getPriority(),
                request.getIcon());
        }

        /** Settings are server-wide, so every open board may have changed. */
        private void broadcastAllBoards() {
            for (Object object : MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList) {
                EntityPlayerMP recipient = (EntityPlayerMP) object;
                if (!supportsClient(recipient)) continue;
                for (ProjectSummary project : service().listAccessibleProjects(recipient.getUniqueID())) {
                    OperationResult<BoardSnapshot> board = service().getBoard(recipient.getUniqueID(), project.getId());
                    if (board.isSuccess()) sendToClient(new S2CBoardSnapshot(board.getValue()), recipient);
                }
            }
        }

        private String bounded(String value, int maximum) {
            return value == null || value.length() > maximum ? null : value;
        }

        private void sendResult(EntityPlayerMP player, OperationResult<?> result) {
            sendToClient(
                new S2COperationResult(result.isSuccess(), result.getErrorCode(), result.getMessage()),
                player);
        }

        private void broadcastBoard(UUID projectId) {
            MinecraftServer server = MinecraftServer.getServer();
            for (Object object : server.getConfigurationManager().playerEntityList) {
                EntityPlayerMP recipient = (EntityPlayerMP) object;
                if (!supportsClient(recipient)) continue;
                OperationResult<BoardSnapshot> result = service().getBoard(recipient.getUniqueID(), projectId);
                if (result.isSuccess()) sendToClient(new S2CBoardSnapshot(result.getValue()), recipient);
            }
        }

        private void broadcastProjectLists() {
            for (Object object : MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList) {
                EntityPlayerMP recipient = (EntityPlayerMP) object;
                if (!supportsClient(recipient)) continue;
                sendToClient(new S2CProjectList(service().listAccessibleProjects(recipient.getUniqueID())), recipient);
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

    private static final class DeleteProjectHandler extends ServerRequestHandler {
    }

    private static final class DeleteCardHandler extends ServerRequestHandler {
    }

    private static final class MoveCardHandler extends ServerRequestHandler {
    }

    private static final class SetCardAssignedHandler extends ServerRequestHandler {
    }

    private static final class ExpandRequirementHandler extends ServerRequestHandler {
    }

    private static final class UploadBreakdownHandler extends ServerRequestHandler {
    }

    private static final class SetProjectIconHandler extends ServerRequestHandler {
    }

    private static final class AddTaskHandler extends ServerRequestHandler {
    }

    private static final class SetTaskDoneHandler extends ServerRequestHandler {
    }

    private static final class DeleteTaskHandler extends ServerRequestHandler {
    }

    private static final class AddCommentHandler extends ServerRequestHandler {
    }

    private static final class DeleteCommentHandler extends ServerRequestHandler {
    }

    private static final class SaveSettingsHandler extends ServerRequestHandler {
    }

    private static final class SetRequirementQuantityHandler extends ServerRequestHandler {
    }

    private static final class DeleteRequirementHandler extends ServerRequestHandler {
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
