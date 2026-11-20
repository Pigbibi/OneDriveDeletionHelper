package cn.lisiyi.photokeep.core;

import java.util.List;

/** Missing media alone is not evidence of an intentional delete. */
public final class DeletionPolicy {
    public static final long GRACE_MS = 24 * 60 * 60 * 1000L;
    /** One confirmation covers at most this many files; the engine rejects a larger approved batch. */
    public static final int MAX_MANUAL_BATCH = 10;
    /** Element names are written to state.json as binding statuses, so they keep their spelling. */
    public enum Kind { PRESENT, TRASHED, MISSING, HOLD }
    public record Observation(Kind kind, String note) {}

    public Observation observe(Binding binding, List<Media> allMedia) {
        for (Media media : allMedia) {
            if (!media.trashed() && !media.pending() && binding.sha256.equals(media.sha256()))
                return new Observation(Kind.PRESENT, "pk:note_present");
        }
        Media original = null;
        for (Media media : allMedia) {
            if (media.key().equals(binding.localKey)) original = media;
            if ((!media.readable() || media.pending()) && (media.size() == binding.size || media.size() <= 0))
                return new Observation(Kind.HOLD, "pk:note_pending_media");
        }
        if (original != null) {
            if (!original.sha256().equals(binding.sha256))
                return new Observation(Kind.HOLD, "pk:note_local_edited");
            if (original.trashed()) return new Observation(Kind.TRASHED, "pk:note_local_trashed");
            return new Observation(Kind.HOLD, "pk:note_local_hold");
        }
        return new Observation(Kind.MISSING, "pk:note_local_missing");
    }
    public boolean canAutoDelete(String status, int observations, long firstMissing, long now, boolean enabled) {
        return enabled && status.equals(Binding.TRASHED) && observations >= 2 && firstMissing > 0
                && now >= firstMissing && now - firstMissing >= GRACE_MS;
    }
    /** Caps a single automatic run so a bad match cannot sweep the library. */
    public boolean withinAutomaticLimit(int candidates, int tracked) {
        return candidates > 0 && candidates <= Math.min(10, Math.max(1, tracked / 5));
    }
    public static boolean isWithin(String folder, String root) {
        return !root.isEmpty() && root.endsWith("/") && folder.startsWith(root);
    }
}
