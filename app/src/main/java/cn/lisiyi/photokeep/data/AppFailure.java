package cn.lisiyi.photokeep.data;

import android.content.Context;
import cn.lisiyi.photokeep.R;

public final class AppFailure extends Exception {
    public final String code;
    public AppFailure(String code) { super(code); this.code = code; }
    public String message(Context context) { return context.getString(messageRes(code)); }
    public static int messageRes(String code) {
        return switch (code) {
            case "LOGIN" -> R.string.err_login;
            case "PERMISSION" -> R.string.err_permission;
            case "MEDIA_CHANGED" -> R.string.err_media_changed;
            case "NETWORK" -> R.string.err_network;
            case "CLOUD_CHANGED" -> R.string.err_cloud_changed;
            case "CONTENT" -> R.string.err_content;
            case "UNCERTAIN" -> R.string.err_uncertain;
            case "CONFIG" -> R.string.err_config;
            case "ACCOUNT" -> R.string.err_account;
            case "STATE" -> R.string.err_state;
            case "LIMIT" -> R.string.err_limit;
            case "BUSY" -> R.string.err_busy;
            case "CANCELLED" -> R.string.err_cancelled;
            default -> R.string.err_unknown;
        };
    }
}
