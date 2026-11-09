package cn.lisiyi.photokeep.core;

public final class Binding {
    /** Written to state.json verbatim; renaming one would strand records saved by an older build. */
    public static final String PRESENT = "PRESENT", TRASHED = "TRASHED", MISSING = "MISSING", HOLD = "HOLD",
            SENDING = "SENDING", DONE = "DONE", UNCERTAIN = "UNCERTAIN", IGNORED = "IGNORED";
    public final String cloudId, localKey, sha256, name, originalFolder;
    public final long size;
    public String cloudPath, status = PRESENT, note = "";
    public long firstMissing = 0, lastObserved = 0;
    public int observations = 0;
    public Binding(String cloudId, String localKey, String sha256, long size, String name, String originalFolder, String cloudPath) {
        this.cloudId = cloudId; this.localKey = localKey; this.sha256 = sha256;
        this.size = size; this.name = name; this.originalFolder = originalFolder; this.cloudPath = cloudPath;
    }
    /** The phone copy is gone or trashed; still only a proposal until the cloud bytes are verified. */
    public boolean candidate() { return status.equals(TRASHED) || status.equals(MISSING); }
    /** The scan loop stops here: this record is no longer re-derived from the phone library. */
    public boolean terminal() { return status.equals(DONE) || status.equals(UNCERTAIN) || status.equals(SENDING) || status.equals(IGNORED); }
    /** A DELETE whose outcome was never confirmed; only a human checking OneDrive can settle it. */
    public boolean uncertain() { return status.equals(SENDING) || status.equals(UNCERTAIN); }
    /** Records the preview reports as needing a human check before they are trusted again. */
    public boolean needsReview() { return status.equals(HOLD) || uncertain(); }
}
