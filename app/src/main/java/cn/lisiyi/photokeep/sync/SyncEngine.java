package cn.lisiyi.photokeep.sync;

import android.content.Context;
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
            if (!approved.isEmpty() && (previewTime != s.lastScan || approved.size() > 10)) throw new AppFailure("CLOUD_CHANGED");
            for (String id : approved) if (!s.bindings.containsKey(id) || !s.bindings.get(id).candidate()) throw new AppFailure("CLOUD_CHANGED");
            for (Binding b : s.bindings.values()) {
                if (b.status.equals("SENDING")) { b.status = "UNCERTAIN"; b.note = "上次请求结果未确认，请到 OneDrive 核对"; }
                if (b.status.equals("UNCERTAIN")) s.automatic = false;
            }
            store.write(s);
            LocalScanner scanner = new LocalScanner(context, stopped);
            progress.accept("正在检查手机媒体库");
            LocalScanner.Snapshot local = scanner.scan(progress);
            boolean rebuilt = !s.mediaVersion.isEmpty() && !s.mediaVersion.equals(local.version());
            if (rebuilt) { s.resetMapping(); approved = Set.of(); s.log("手机媒体库已重建，关闭自动清理并重新建立对应关系"); }
            GraphApi graph = new GraphApi(context, s, stopped);
            progress.accept("正在读取 OneDrive 所选目录");
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
                    b.status = "HOLD"; b.note = "云端文件不在所选范围或已发生变化";
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
            s.lastMessage = "检查完成。未关联的云端照片会保留；清理前还会逐个核对文件内容。";
            store.write(s);

            List<Binding> targets = new ArrayList<>();
            if (!approved.isEmpty()) {
                for (String id : approved) { Binding b = s.bindings.get(id); if (b != null && b.candidate()) targets.add(b); }
            } else if (background && s.automatic) {
                for (Binding b : s.bindings.values()) if (policy.canAutoDelete(b.status, b.observations, b.firstMissing, now, s.automatic)) targets.add(b);
                if (!targets.isEmpty() && !policy.withinAutomaticLimit(targets.size(), s.bindings.size())) {
                    targets.clear(); s.automatic = false;
                    s.lastMessage = "待自动清理数量较多，已暂停自动清理，请先预览确认。";
                }
            }
            int completed = 0;
            for (Binding b : targets) {
                if (stopped.getAsBoolean()) throw new AppFailure("CANCELLED");
                progress.accept("正在核对清理文件 " + (completed + 1) + " / " + targets.size() + "（需要读取云端原文件）");
                new RecycleGuard().execute(b, graph, () -> scanner.assertUnchanged(local), () -> store.write(s));
                completed++;
            }
            if (completed > 0) s.lastMessage = "已核对内容并将 " + completed + " 个文件移入 OneDrive 回收站。";
            s.log(s.lastMessage); store.write(s);
        } catch (Exception e) {
            AppFailure error = e instanceof AppFailure ? (AppFailure) e : new AppFailure("CONTENT");
            if (state != null) {
                boolean uncertain = state.bindings.values().stream().anyMatch(b -> b.status.equals("SENDING") || b.status.equals("UNCERTAIN"));
                if (uncertain) error = new AppFailure("UNCERTAIN");
                state.lastMessage = error.getMessage(); state.automatic = false;
                if (error.code.equals("CANCELLED")) state.scheduled = false;
                state.log(error.getMessage()); store.write(state);
            }
            throw error;
        } finally { Store.GATE.unlock(); }
    }
}
