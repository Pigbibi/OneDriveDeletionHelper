package cn.lisiyi.photokeep.core;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class DeletionPolicyTest {
    private final DeletionPolicy policy = new DeletionPolicy();
    private final String sha = "a".repeat(64);
    private Media item(String key, String digest, boolean trash) {
        return new Media(key, "Pictures/", "photo.jpg", 100, 1, 1, digest, trash, false);
    }
    private Binding link() { return new Binding("cloud", "phone", sha, 100, "photo.jpg", "Pictures/", "cloud/photo.jpg"); }

    @Test public void existingPhotoIsNeverCandidate() {
        assertEquals(DeletionPolicy.Kind.PRESENT, policy.observe(link(), List.of(item("phone", sha, false))).kind());
    }
    @Test public void moveOrRenameKeepsCloudPhoto() {
        assertEquals(DeletionPolicy.Kind.PRESENT, policy.observe(link(), List.of(item("new-key", sha, false))).kind());
    }
    @Test public void survivingDuplicateBlocksDeletion() {
        assertEquals(DeletionPolicy.Kind.PRESENT, policy.observe(link(), List.of(item("phone", sha, true), item("copy", sha, false))).kind());
    }
    @Test public void missingFileRequiresManualReview() {
        assertEquals(DeletionPolicy.Kind.MISSING, policy.observe(link(), List.of()).kind());
        assertFalse(policy.canAutoDelete("MISSING", 2, 1, 100_000_000, true));
    }
    @Test public void explicitTrashCanBecomeAutomaticOnlyAfterGraceAndTwoScans() {
        assertEquals(DeletionPolicy.Kind.TRASHED, policy.observe(link(), List.of(item("phone", sha, true))).kind());
        assertFalse(policy.canAutoDelete("TRASHED", 1, 1, 100_000_000, true));
        assertFalse(policy.canAutoDelete("TRASHED", 2, 1, 1000, true));
        assertFalse(policy.canAutoDelete("TRASHED", 2, 1, 100_000_000, false));
        assertTrue(policy.canAutoDelete("TRASHED", 2, 1, 100_000_000, true));
    }
    @Test public void reusedMediaIdOrEditedPhotoCannotDeleteOldCloudFile() {
        assertEquals(DeletionPolicy.Kind.HOLD, policy.observe(link(), List.of(item("phone", "b".repeat(64), true))).kind());
    }
    @Test public void unreadablePotentialDuplicateBlocksDeletion() {
        assertEquals(DeletionPolicy.Kind.HOLD, policy.observe(link(), List.of(item("other", "", false))).kind());
    }
    @Test public void unknownOutcomeIsNeverRetriedAutomatically() {
        assertFalse(policy.canAutoDelete("UNCERTAIN", 9, 1, 100_000_000, true));
        assertFalse(policy.canAutoDelete("SENDING", 9, 1, 100_000_000, true));
    }
    @Test public void futureClockCannotExpireGrace() {
        assertFalse(policy.canAutoDelete("TRASHED", 3, 100_000_000, 1, true));
    }
    @Test public void excessiveBatchNeverRunsAutomatically() {
        assertTrue(policy.withinAutomaticLimit(1, 20));
        assertFalse(policy.withinAutomaticLimit(11, 1000));
        assertFalse(policy.withinAutomaticLimit(5, 10));
    }
}
