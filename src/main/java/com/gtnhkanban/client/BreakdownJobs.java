package com.gtnhkanban.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gtnhkanban.api.CardView;
import com.gtnhkanban.api.RequirementView;
import com.gtnhkanban.model.ItemKey;
import com.gtnhkanban.model.RecipeTree;
import com.gtnhkanban.network.KanbanNetwork;
import com.gtnhkanban.network.message.C2SAddRequirement;
import com.gtnhkanban.network.message.C2SExpandRequirement;
import com.gtnhkanban.network.message.C2SUploadBreakdown;
import com.gtnhkanban.network.message.KanbanRequest;
import com.gtnhkanban.network.message.RecipeTreeCodec;
import com.gtnhkanban.planner.BreakdownPlanner;
import com.gtnhkanban.planner.BreakdownStrategy;
import com.gtnhkanban.service.KanbanService;

import cpw.mods.fml.common.FMLLog;

/**
 * Runs automatic material breakdowns on the client thread, a few milliseconds per tick so the game stays responsive
 * while NEI is queried, then uploads each finished tree to the server.
 *
 * <p>
 * Jobs run one at a time. Each reads its row's current state when it starts, and the next job waits until the server
 * has answered the previous upload and sent the updated board, so row budgets and revisions are never stale.
 */
public final class BreakdownJobs {

    private static final long SLICE_NANOS = 20000000L;
    /** Stop waiting for a server answer after this many ticks (10 seconds). */
    private static final int ACK_TIMEOUT_TICKS = 200;
    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<Job>();
    private static BreakdownStrategy strategy = BreakdownStrategy.CRAFTING_TABLE;
    private static int nextUploadId;
    private static String notice = "";

    private enum Wait {
        NONE,
        RESULT,
        BOARD
    }

    private static Wait waiting = Wait.NONE;
    private static int waitedTicks;

    /** Progress of a "regenerate all" run, reported once its last job finishes. */
    private static final class Batch {

        int remaining, regenerated, partial;
    }

    private static final class Job {

        final UUID projectId, cardId, entryId;
        final Batch batch;
        ItemKey item;
        int quantity;
        long revision;
        boolean hadBranch;
        BreakdownPlanner planner;
        NeiRecipeSource source;

        Job(UUID projectId, UUID cardId, UUID entryId, ItemKey item, int quantity, Batch batch) {
            this.projectId = projectId;
            this.cardId = cardId;
            this.entryId = entryId;
            this.item = item;
            this.quantity = quantity;
            this.batch = batch;
        }
    }

    private BreakdownJobs() {}

    static BreakdownStrategy getStrategy() {
        return strategy;
    }

    static void setStrategy(BreakdownStrategy value) {
        strategy = value;
    }

    /** Adds {@code item} to the card with its full breakdown, or plainly when it has nothing to break down. */
    static void addWithBreakdown(UUID projectId, UUID cardId, ItemKey item, int quantity) {
        QUEUE.add(new Job(projectId, cardId, null, item, quantity, null));
        notice = "";
    }

    /** Replaces an existing row's material branch with a fresh full breakdown. */
    static void breakDown(UUID projectId, UUID cardId, RequirementView row) {
        QUEUE.add(new Job(projectId, cardId, row.getId(), row.getItem(), row.getQuantity(), null));
        notice = "";
    }

    /** Re-runs the breakdown of every item on the card with the current recipe preference. */
    static void regenerateAll(UUID projectId, UUID cardId) {
        CardView card = KanbanClientState.findCard(cardId);
        if (card == null || card.getRequirements()
            .isEmpty()) return;
        Batch run = new Batch();
        for (RequirementView root : card.getRequirements()) {
            QUEUE.add(new Job(projectId, cardId, root.getId(), root.getItem(), root.getQuantity(), run));
            run.remaining++;
        }
        notice = "";
    }

    /** True while this card has breakdowns queued or running. */
    static boolean isBusy(UUID cardId) {
        for (Job job : QUEUE) if (job.cardId.equals(cardId)) return true;
        return false;
    }

    /** Called every client tick. */
    static void tick() {
        if (waiting != Wait.NONE) {
            if (++waitedTicks < ACK_TIMEOUT_TICKS) return;
            waiting = Wait.NONE;
        }
        Job job = QUEUE.peek();
        if (job == null) return;
        try {
            if (job.planner == null && !start(job)) {
                QUEUE.poll();
                done(job, false, false);
                return;
            }
            if (!job.planner.step(job.source, System.nanoTime() + SLICE_NANOS)) return;
            QUEUE.poll();
            finish(job);
        } catch (RuntimeException exception) {
            QUEUE.poll();
            FMLLog.warning("GTNH Kanban breakdown failed for %s: %s", job.item.getRegistryName(), exception);
            if (job.batch == null) notice = "Could not break down " + MaterialDisplay.name(job.item) + ".";
            if (job.entryId == null) addPlainly(job);
            done(job, false, false);
        }
    }

    /** The server answered a request; after an upload, the updated board follows. */
    public static void onServerResult() {
        if (waiting == Wait.RESULT) waiting = Wait.BOARD;
    }

    public static void onBoard() {
        if (waiting == Wait.BOARD) waiting = Wait.NONE;
    }

    /** Reads the row's current state and sizes the planner to what the card can still hold. */
    private static boolean start(Job job) {
        CardView card = KanbanClientState.findCard(job.cardId);
        int rows = card == null ? 0 : rowCount(card.getRequirements());
        job.source = new NeiRecipeSource();
        if (job.entryId == null) {
            job.planner = new BreakdownPlanner(job.item, strategy, 1, KanbanService.MAX_CARD_ROWS - rows - 1);
            return true;
        }
        RequirementView row = KanbanClientState.findRequirement(job.cardId, job.entryId);
        if (card == null || row == null) return false;
        job.item = row.getItem();
        job.quantity = row.getQuantity();
        job.revision = row.getRevision();
        job.hadBranch = !row.getChildren()
            .isEmpty();
        List<ItemKey> ancestors = new ArrayList<ItemKey>();
        for (RequirementView root : card.getRequirements()) if (findPath(root, job.entryId, ancestors)) break;
        int budget = KanbanService.MAX_CARD_ROWS - rows + rowCount(row.getChildren());
        job.planner = new BreakdownPlanner(job.item, strategy, ancestors.size() + 1, budget, ancestors);
        return true;
    }

    private static void finish(Job job) {
        RecipeTree tree = job.planner.result();
        String name = MaterialDisplay.name(job.item);
        report(job, name, tree);
        if (tree == null) {
            if (job.entryId == null) addPlainly(job);
            else if (job.hadBranch) {
                // Under the current rules this is a base material or has no usable recipe: drop the old branch.
                send(
                    new C2SExpandRequirement(job.projectId, job.cardId, job.entryId, job.quantity, job.revision, null));
            } else if (job.batch == null) notice = "No craftable breakdown found for " + name + ".";
            done(job, false, false);
            return;
        }
        List<C2SUploadBreakdown> parts;
        try {
            parts = C2SUploadBreakdown.split(
                job.projectId,
                job.cardId,
                job.entryId,
                job.item,
                job.quantity,
                job.revision,
                ++nextUploadId,
                RecipeTreeCodec.encode(tree));
        } catch (IllegalArgumentException exception) {
            if (job.batch == null) notice = "The breakdown for " + name + " is too large to send.";
            if (job.entryId == null) addPlainly(job);
            done(job, false, false);
            return;
        }
        for (C2SUploadBreakdown part : parts) KanbanNetwork.CHANNEL.sendToServer(part);
        waitForServer();
        if (job.batch == null) notice = job.planner.isTruncated()
            ? "Broke down " + name + " partly; size or depth limits stopped some branches."
            : "";
        done(job, true, job.planner.isTruncated());
    }

    /** Writes why materials were left unexpanded to the client log, for diagnosing missing breakdowns. */
    private static void report(Job job, String name, RecipeTree tree) {
        FMLLog.info(
            "GTNH Kanban breakdown of %s (%s): %d rows, %d recipe lookups%s",
            name,
            strategy.getLabel(),
            tree == null ? 0 : tree.nodeCount(),
            job.planner.lookups(),
            job.planner.isTruncated() ? ", partial" : "");
        for (java.util.Map.Entry<ItemKey, String> stop : job.planner.stopReasons()
            .entrySet()) {
            FMLLog.info(
                "GTNH Kanban   not broken down: %s [%s] - %s",
                MaterialDisplay.name(stop.getKey()),
                stop.getKey()
                    .getRegistryName() + ":"
                    + stop.getKey()
                        .getMetadata(),
                stop.getValue());
        }
    }

    private static void done(Job job, boolean regenerated, boolean partial) {
        Batch run = job.batch;
        if (run == null) return;
        if (regenerated) run.regenerated++;
        if (partial) run.partial++;
        if (--run.remaining > 0) return;
        notice = "Regenerated materials for " + run.regenerated
            + (run.regenerated == 1 ? " item" : " items")
            + (run.partial > 0 ? "; " + run.partial + " stopped at size or depth limits." : ".");
    }

    private static void addPlainly(Job job) {
        send(new C2SAddRequirement(job.projectId, job.cardId, job.item, job.quantity));
    }

    private static void send(KanbanRequest request) {
        KanbanNetwork.CHANNEL.sendToServer(request);
        waitForServer();
    }

    private static void waitForServer() {
        waiting = Wait.RESULT;
        waitedTicks = 0;
    }

    /** Progress of the running breakdown, or an outcome worth showing; empty when there is nothing to say. */
    static String status() {
        Job job = QUEUE.peek();
        if (job == null) return notice;
        String queued = QUEUE.size() > 1 ? " (" + (QUEUE.size() - 1) + " more queued)" : "";
        if (job.planner == null) return "Waiting to break down " + MaterialDisplay.name(job.item) + "..." + queued;
        return "Breaking down " + MaterialDisplay
            .name(job.item) + "... " + job.planner.lookups() + " recipes checked" + queued;
    }

    static void clear() {
        QUEUE.clear();
        notice = "";
        waiting = Wait.NONE;
    }

    private static int rowCount(List<RequirementView> rows) {
        int count = 0;
        for (RequirementView row : rows) count += 1 + rowCount(row.getChildren());
        return count;
    }

    /** Collects the materials of the rows above {@code id}, nearest last. */
    private static boolean findPath(RequirementView row, UUID id, List<ItemKey> path) {
        if (row.getId()
            .equals(id)) return true;
        path.add(row.getItem());
        for (RequirementView child : row.getChildren()) if (findPath(child, id, path)) return true;
        path.remove(path.size() - 1);
        return false;
    }
}
