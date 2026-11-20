package cn.lisiyi.photokeep.data;

import android.content.Context;
import cn.lisiyi.photokeep.R;

/** Resolves internal message/note keys produced by pure-Java core classes into localized strings. */
public final class AppText {
    private AppText() {}

    public static String resolve(Context context, String value) {
        if (value == null) return "";
        return switch (value) {
            case "pk:note_present" -> context.getString(R.string.note_present);
            case "pk:note_pending_media" -> context.getString(R.string.note_pending_media);
            case "pk:note_local_edited" -> context.getString(R.string.note_local_edited);
            case "pk:note_local_trashed" -> context.getString(R.string.note_local_trashed);
            case "pk:note_local_hold" -> context.getString(R.string.note_local_hold);
            case "pk:note_local_missing" -> context.getString(R.string.note_local_missing);
            case "pk:note_content_mismatch" -> context.getString(R.string.note_content_mismatch);
            case "pk:note_sending" -> context.getString(R.string.note_sending);
            case "pk:note_uncertain" -> context.getString(R.string.note_uncertain);
            case "pk:note_done" -> context.getString(R.string.note_done);
            case "pk:note_uncertain_previous" -> context.getString(R.string.note_uncertain_previous);
            case "pk:note_cloud_out_of_scope" -> context.getString(R.string.note_cloud_out_of_scope);
            case "pk:msg_initial" -> context.getString(R.string.msg_initial);
            case "pk:msg_connected" -> context.getString(R.string.msg_connected);
            case "pk:msg_mapping_reset" -> context.getString(R.string.msg_mapping_reset);
            case "pk:msg_scan_done" -> context.getString(R.string.msg_scan_done);
            case "pk:msg_auto_limit_paused" -> context.getString(R.string.msg_auto_limit_paused);
            default -> value;
        };
    }
}
