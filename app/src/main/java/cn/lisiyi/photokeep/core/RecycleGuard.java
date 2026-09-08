package cn.lisiyi.photokeep.core;

/** Explicit steps make the write boundary testable without a phone or a real Microsoft account. */
public final class RecycleGuard {
    public interface Cloud {
        CloudItem current(String id) throws Exception;
        String contentSha256(CloudItem item) throws Exception;
        void recycle(CloudItem item) throws Exception;
    }
    public interface Journal { void save() throws Exception; }
    public interface LocalCheck { void check() throws Exception; }

    public void execute(Binding b, Cloud cloud, LocalCheck localCheck, Journal journal) throws Exception {
        if (!b.candidate()) throw new IllegalStateException("Item is not a candidate");
        localCheck.check();
        CloudItem before = cloud.current(b.cloudId);
        if (before.folder() || before.size() != b.size || before.etag().trim().isEmpty()
                || !before.id().equals(b.cloudId)) throw new IllegalStateException("Cloud item changed");
        if (!b.sha256.equals(cloud.contentSha256(before))) {
            b.status = "HOLD"; b.note = "云端内容与手机原记录不一致，保留文件"; journal.save();
            throw new IllegalStateException("Content mismatch");
        }
        CloudItem after = cloud.current(b.cloudId);
        if (after.folder() || after.size() != b.size || !after.id().equals(before.id()) || !after.etag().equals(before.etag()))
            throw new IllegalStateException("Cloud item changed during verification");
        localCheck.check();
        b.status = "SENDING"; b.note = "正在请求移入 OneDrive 回收站";
        journal.save(); // Persist before sending, so process death cannot silently repeat an uncertain DELETE.
        try {
            cloud.recycle(after);
        } catch (Exception e) {
            b.status = "UNCERTAIN"; b.note = "结果待人工核对，不会自动重试";
            journal.save();
            throw e;
        }
        b.status = "DONE"; b.note = "已移入 OneDrive 回收站";
        journal.save();
    }
}
