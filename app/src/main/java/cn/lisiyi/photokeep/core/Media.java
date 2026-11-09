package cn.lisiyi.photokeep.core;

public record Media(String key, String folder, String name, long size, long modified,
                    long generation, String sha256, boolean trashed, boolean pending) {
    public boolean readable() { return ContentHash.isValidDigest(sha256); }
}
