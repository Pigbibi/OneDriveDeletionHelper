package cn.lisiyi.photokeep.core;

public final class Binding {
    public final String cloudId, localKey, sha256, name, originalFolder;
    public final long size;
    public String cloudPath, status = "PRESENT", note = "";
    public long firstMissing = 0, lastObserved = 0;
    public int observations = 0;
    public Binding(String cloudId, String localKey, String sha256, long size, String name, String originalFolder, String cloudPath) {
        this.cloudId = cloudId; this.localKey = localKey; this.sha256 = sha256;
        this.size = size; this.name = name; this.originalFolder = originalFolder; this.cloudPath = cloudPath;
    }
    public boolean terminal() { return status.equals("DONE") || status.equals("UNCERTAIN") || status.equals("SENDING") || status.equals("IGNORED"); }
    public boolean candidate() { return status.equals("TRASHED") || status.equals("MISSING"); }
}
