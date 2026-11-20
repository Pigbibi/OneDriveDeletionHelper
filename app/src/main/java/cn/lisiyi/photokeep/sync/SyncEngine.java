package cn.lisiyi.photokeep.sync;

import android.content.Context;
import cn.lisiyi.photokeep.R;
import cn.lisiyi.photokeep.core.*;
import cn.lisiyi.photokeep.data.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class SyncEngine {
    private final Context context;
    private final BooleanSupplier stopped;
    private final Consumer<String> progress;
    public SyncEngine(Context context, BooleanSupplier stopped, Consumer<String> progress) {
        this.context = context; this.stopped = stopped; this.progress = progress;
    }
    public void run(Set<String> approved, long previewTime, boolean background) throws AppFailure {
        if (!Store.GATE.tryLock()) throw new AppFailure("BUSY");
        Store store = new Store(context);
        State state = null;
        try {
            state = store.read();
            final State s = state;
            if (background && !s.scheduled) return;
            if (s.clientId.isEmpty() || s.accountId.isEmpty() || s.driveId.isEmpty() || s.folders.isEmpty() || s.cloudRoots.isEmpty()) throw new AppFailure("CONFIG");
            if (!approved.isEmpty() && (previewTime != s.lastScan || approved.size() > DeletionPolicy.MAX_MANUAL_BATCH)) throw new AppFailure("CLOUD_CHANGED");
            for (String id : approved) if (!s.bindings.containsKey(id) || !s.bindings.get(id).candidate()) throw new AppFailure("CLOUD_CHANGED");
            for (Binding b : s.bindings.values()) {
                if (b.status.equals(Binding.SENDING)) { b.status = Binding.UNCERTAIN; b.note = "pk:note_uncertain_previous"; }
                if (b.uncertain()) s.automatic = false;
            }
            store.write(s);
            LocalScanner scanner = new LocalScanner(context, stopped);
            progress.accept(context.getString(R.string.progress_checking_local));
            LocalScanner.Snapshot local = scanner.scan(progress);
            boolean rebuilt = !s.mediaVersion.isEmpty() && !s.mediaVersion.equals(local.version());
            if (rebuilt) { s.resetMapping(); approved = Set.of(); s.log(context.getString(R.string.log_media_rebuilt)); }
            GraphApi graph = new GraphApi(context, s, stopped);
            progress.accept(context.getString(R.string.progress_listing_cloud));
            List<CloudItem> remote = graph.listSelected();
            scanner.assertUnchanged(local);
            Map<String, CloudItem> byId = new HashMap<>();
            for (CloudItem item : remote) byId.put(item.id(), item);
            DeletionPolicy policy = new DeletionPolicy();
            long now = System.currentTimeMillis();
            for (Binding b : s.bindings.values()) {
                if (b.terminal()) continue;
                CloudItem cloud = byId.get(b.cloudId);
                if (cloud == null || cloud.size() != b.size) {
                    b.status = Binding.HOLD; b.note = "pk:note_cloud_out_of_scope";
                    b.firstMissing = 0; b.observations = 0;
                    continue;
                }
                b.cloudPath = cloud.path();
                DeletionPolicy.Observation observation = policy.observe(b, local.media());
                String next = observation.kind().name();
                if (!next.equals(b.status)) { b.firstMissing = 0; b.observations = 0; b.lastObserved = 0; }
                b.status = next; b.note = observation.note();
                if (b.candidate()) {
                    if (b.firstMissing == 0) b.firstMissing = now;
                    if (b.lastObserved == 0 || now - b.lastObserved >= 60 * 60 * 1000L) {
                        b.observations++; b.lastObserved = now;
                    }
                } else { b.firstMissing = 0; b.observations = 0; b.lastObserved = now; }
            }
            for (Binding b : new Matcher().propose(local.media(), remote, s.folders, s.bindings.values())) s.bindings.put(b.cloudId, b);
            s.mediaVersion = local.version(); s.lastScan = now;
            s.localCount = (int) local.media().stream().filter(m -> !m.trashed() && s.folders.stream().anyMatch(f -> DeletionPolicy.isWithin(m.folder(), f))).count();
            s.cloudCount = remote.size();
            s.unresolvedCount = (int) remote.stream().filter(c -> !s.bindings.containsKey(c.id())).count();
            s.lastMessage = "pk:msg_scan_done";
            store.write(s);

            List<Binding> targets = new ArrayList<>();
            if (!approved.isEmpty()) {
                for (String id : approved) { Binding b = s.bindings.get(id); if (b != null && b.candidate()) targets.add(b); }
            } else if (background && s.automatic) {
                for (Binding b : s.bindings.values()) if (policy.canAutoDelete(b.status, b.observations, b.firstMissing, now, s.automatic)) targets.add(b);
                if (!targets.isEmpty() && !policy.withinAutomaticLimit(targets.size(), s.bindings.size())) {
                    targets.clear(); s.automatic = false;
                    s.lastMessage = "pk:msg_auto_limit_paused";
                }
            }
            int completed = 0;
            for (Binding b : targets) {
                if (stopped.getAsBoolean()) throw new AppFailure("CANCELLED");
                progress.accept(context.getString(R.string.progress_verifying_file, completed + 1, targets.size()));
                new RecycleGuard().execute(b, graph, () -> scanner.assertUnchanged(local), () -> store.write(s));
                completed++;
            }
            if (completed > 0) s.lastMessage = context.getString(R.string.msg_recycled_count, completed);
            s.log(AppText.resolve(context, s.lastMessage)); store.write(s);
        } catch (Exception e) {
            AppFailure error = e instanceof AppFailure ? (AppFailure) e : new AppFailure("CONTENT");
            if (state != null) {
                boolean uncertain = state.bindings.values().stream().anyMatch(Binding::uncertain);
                if (uncertain) error = new AppFailure("UNCERTAIN");
                String errorMessage = error.message(context);
                state.lastMessage = errorMessage; state.automatic = false;
                if (error.code.equals("CANCELLED")) state.scheduled = false;
                state.log(errorMessage); store.write(state);
            }
            throw error;
        } finally { Store.GATE.unlock(); }
    }
}
