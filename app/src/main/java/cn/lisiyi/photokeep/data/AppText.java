package cn.lisiyi.photokeep.data;

import android.content.Context;
import cn.lisiyi.photokeep.R;

/** Resolves internal message/note keys into localized strings while passing legacy literals through unchanged. */
public final class AppText {
    private static final String PREFIX_ERROR = "pk:err:";
    private static final String PREFIX_RECYCLED_COUNT = "pk:msg_recycled_count:";

    private AppText() {}

    public static String errorMessage(String code) {
        return PREFIX_ERROR + (code == null ? "" : code);
    }

    public static String recycledMessage(int count) {
        return PREFIX_RECYCLED_COUNT + count;
    }

    public static String resolve(Context context, String value) {
        if (value == null) return "";
        if (value.startsWith(PREFIX_ERROR)) {
            return context.getString(AppFailure.messageRes(value.substring(PREFIX_ERROR.length())));
        }
        if (value.startsWith(PREFIX_RECYCLED_COUNT)) {
            try {
                int count = Integer.parseInt(value.substring(PREFIX_RECYCLED_COUNT.length()));
                return context.getString(R.string.msg_recycled_count, count);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
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
            case "pk:note_ignored_preview" -> context.getString(R.string.note_ignored_preview);
            case "pk:note_ignored_review" -> context.getString(R.string.note_ignored_review);
            case "pk:msg_initial" -> context.getString(R.string.msg_initial);
            case "pk:msg_connected" -> context.getString(R.string.msg_connected);
            case "pk:msg_mapping_reset" -> context.getString(R.string.msg_mapping_reset);
            case "pk:msg_scan_done" -> context.getString(R.string.msg_scan_done);
            case "pk:msg_auto_limit_paused" -> context.getString(R.string.msg_auto_limit_paused);
            case "pk:log_media_rebuilt" -> context.getString(R.string.log_media_rebuilt);
            default -> value;
        };
    }

    public static String resolveHistory(Context context, String entry) {
        if (entry == null) return "";
        int sep = entry.indexOf("  ");
        if (sep > 0 && entry.startsWith("pk:", sep + 2)) {
            return entry.substring(0, sep + 2) + resolve(context, entry.substring(sep + 2));
        }
        return resolve(context, entry);
    }
}
