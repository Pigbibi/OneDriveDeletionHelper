package cn.lisiyi.photokeep.core;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class RecycleGuardTest {
    private final String sha = "a".repeat(64);
    private Binding candidate() { Binding b = new Binding("cloud", "phone", sha, 100, "a.jpg", "DCIM/", "2026/09/a.jpg"); b.status = "MISSING"; return b; }
    private class FakeCloud implements RecycleGuard.Cloud {
        int reads, writes;
        boolean change, mismatch, fail;
        String deletedEtag;
        public CloudItem current(String id) { reads++; return new CloudItem(id, "renamed.jpg", "2026/09/renamed.jpg", 100, change && reads > 1 ? "v2" : "v1", false); }
        public String contentSha256(CloudItem item) { return mismatch ? "b".repeat(64) : sha; }
        public void recycle(CloudItem item) throws Exception { writes++; deletedEtag = item.etag(); if (fail) throw new IOException("Simulated interrupted request"); }
    }
    @Test public void persistsSendingBeforeWriteThenDoneWithVerifiedVersion() throws Exception {
        Binding b = candidate(); FakeCloud cloud = new FakeCloud(); List<String> journal = new ArrayList<>();
        new RecycleGuard().execute(b, cloud, () -> {}, () -> { journal.add(b.status); if (b.status.equals("SENDING")) assertEquals(0, cloud.writes); });
        assertEquals(List.of("SENDING", "DONE"), journal); assertEquals("v1", cloud.deletedEtag); assertEquals(1, cloud.writes);
    }
    @Test public void identicalFilenameAndSizeButDifferentBytesNeverDeletes() {
        Binding b = candidate(); FakeCloud cloud = new FakeCloud(); cloud.mismatch = true;
        assertThrows(Exception.class, () -> new RecycleGuard().execute(b, cloud, () -> {}, () -> {}));
        assertEquals(0, cloud.writes); assertEquals("HOLD", b.status);
    }
    @Test public void cloudChangeDuringDownloadNeverDeletes() {
        Binding b = candidate(); FakeCloud cloud = new FakeCloud(); cloud.change = true;
        assertThrows(Exception.class, () -> new RecycleGuard().execute(b, cloud, () -> {}, () -> {})); assertEquals(0, cloud.writes);
    }
    @Test public void permissionLossAfterDownloadNeverDeletes() {
        Binding b = candidate(); FakeCloud cloud = new FakeCloud(); int[] calls = {0};
        assertThrows(Exception.class, () -> new RecycleGuard().execute(b, cloud, () -> { if (++calls[0] == 2) throw new IOException(); }, () -> {}));
        assertEquals(0, cloud.writes);
    }
    @Test public void failedDurableSavePreventsNetworkWrite() {
        Binding b = candidate(); FakeCloud cloud = new FakeCloud();
        assertThrows(Exception.class, () -> new RecycleGuard().execute(b, cloud, () -> {}, () -> { throw new IOException(); })); assertEquals(0, cloud.writes);
    }
    @Test public void unknownOutcomeIsPersistedAndCannotBeRetried() {
        Binding b = candidate(); FakeCloud cloud = new FakeCloud(); cloud.fail = true; List<String> journal = new ArrayList<>();
        assertThrows(Exception.class, () -> new RecycleGuard().execute(b, cloud, () -> {}, () -> journal.add(b.status)));
        assertEquals(List.of("SENDING", "UNCERTAIN"), journal);
        assertThrows(Exception.class, () -> new RecycleGuard().execute(b, cloud, () -> {}, () -> {})); assertEquals(1, cloud.writes);
    }
    @Test public void restoredPhotoCancelsPreviouslyApprovedDeletion() {
        Binding b = candidate(); b.status = "PRESENT"; FakeCloud cloud = new FakeCloud();
        assertThrows(Exception.class, () -> new RecycleGuard().execute(b, cloud, () -> {}, () -> {})); assertEquals(0, cloud.reads); assertEquals(0, cloud.writes);
    }
}
