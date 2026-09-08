package cn.lisiyi.photokeep.core;

import java.util.List;

/** Missing media alone is not evidence of an intentional delete. */
public final class DeletionPolicy {
    public static final long GRACE_MS = 24 * 60 * 60 * 1000L;
    public enum Kind { PRESENT, TRASHED, MISSING, HOLD }
    public record Observation(Kind kind, String note) {}

    public Observation observe(Binding binding, List<Media> allMedia) {
        for (Media media : allMedia) {
            if (!media.trashed() && !media.pending() && binding.sha256.equals(media.sha256()))
                return new Observation(Kind.PRESENT, "手机上仍有相同内容，保留云端文件");
        }
        Media original = null;
        for (Media media : allMedia) {
            if (media.key().equals(binding.localKey)) original = media;
            if ((!media.readable() || media.pending()) && (media.size() == binding.size || media.size() <= 0))
                return new Observation(Kind.HOLD, "存在尚未读取完成的媒体，无法排除其他副本");
        }
        if (original != null) {
            if (!original.sha256().equals(binding.sha256))
                return new Observation(Kind.HOLD, "手机文件已经编辑或标识已变化");
            if (original.trashed()) return new Observation(Kind.TRASHED, "手机系统已明确标记为回收站文件");
            return new Observation(Kind.HOLD, "手机文件状态待确认");
        }
        return new Observation(Kind.MISSING, "手机中未找到，请确认是删除，而非移动、隐藏或释放空间");
    }
    public boolean canAutoDelete(String status, int observations, long firstMissing, long now, boolean enabled) {
        return enabled && status.equals("TRASHED") && observations >= 2 && firstMissing > 0
                && now >= firstMissing && now - firstMissing >= GRACE_MS;
    }
    public boolean withinAutomaticLimit(int candidates, int tracked) {
        return candidates > 0 && candidates <= Math.min(10, Math.max(1, tracked / 5));
    }
    public static boolean isWithin(String folder, String root) {
        return !root.isEmpty() && root.endsWith("/") && folder.startsWith(root);
    }
}
