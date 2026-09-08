package cn.lisiyi.photokeep.data;

public final class AppFailure extends Exception {
    public final String code;
    public AppFailure(String code) { super(messageFor(code)); this.code = code; }
    private static String messageFor(String code) {
        return switch (code) {
            case "LOGIN" -> "请在连接设置中重新登录 OneDrive。";
            case "PERMISSION" -> "需要允许访问全部照片和视频及原始媒体信息。权限不足，清理已暂停。";
            case "MEDIA_CHANGED" -> "检查时手机媒体库发生变化，本次未继续清理，请稍后重新检查。";
            case "NETWORK" -> "网络或 OneDrive 暂时不可用，请稍后重试。";
            case "CLOUD_CHANGED" -> "云端文件已变化或不在选定目录，已停止本次清理。";
            case "CONTENT" -> "文件内容无法确认一致，已保留云端文件。";
            case "UNCERTAIN" -> "删除结果尚未确认，已暂停自动清理。请打开 OneDrive 核对记录。";
            case "CONFIG" -> "请先完成应用标识、手机目录和 OneDrive 目录设置。";
            case "ACCOUNT" -> "连接的 OneDrive 已变化。请重新选择目录并建立对应关系。";
            case "STATE" -> "本地记录无法安全读取或保存，清理已暂停。请勿卸载，先联系开发者。";
            case "LIMIT" -> "本次数据量超出检查范围，未进行清理，请缩小所选云端目录。";
            case "BUSY" -> "另一项检查正在进行，请等待完成。";
            case "CANCELLED" -> "检查已停止，未继续清理。";
            default -> "本次检查未完成，清理已暂停。";
        };
    }
}
