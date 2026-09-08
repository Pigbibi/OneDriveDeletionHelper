package cn.lisiyi.photokeep.sync;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import androidx.annotation.NonNull;
import androidx.work.*;
import cn.lisiyi.photokeep.MainActivity;
import cn.lisiyi.photokeep.R;
import cn.lisiyi.photokeep.data.AppFailure;
import java.util.*;

public final class ScanWorker extends Worker {
    public static final String CHANNEL = "photokeep-checks";
    private long lastProgress = 0;
    public ScanWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    private ForegroundInfo foreground(String text) {
        Context context = getApplicationContext();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "照片检查", NotificationManager.IMPORTANCE_LOW));
        PendingIntent intent = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_app)
                .setContentTitle("拾光清理").setContentText(text).setContentIntent(intent).setOngoing(true).build();
        return new ForegroundInfo(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }
    @NonNull @Override public Result doWork() {
        try {
            setForegroundAsync(foreground("正在准备检查")).get();
            String[] approved = getInputData().getStringArray("approved");
            new SyncEngine(getApplicationContext(), this::isStopped, text -> {
                if (System.currentTimeMillis() - lastProgress < 1500) return;
                lastProgress = System.currentTimeMillis();
                setProgressAsync(new Data.Builder().putString("message", text).build());
                setForegroundAsync(foreground(text));
            }).run(approved == null ? Set.of() : new HashSet<>(Arrays.asList(approved)), getInputData().getLong("previewTime", 0), getInputData().getBoolean("background", false));
            return Result.success();
        } catch (AppFailure e) {
            // No automatic WorkManager retry, especially after a write with an unknown outcome.
            return Result.failure(new Data.Builder().putString("message", e.getMessage()).build());
        } catch (Exception e) {
            return Result.failure(new Data.Builder().putString("message", "后台检查未完成，请打开应用重新检查。").build());
        }
    }
}
