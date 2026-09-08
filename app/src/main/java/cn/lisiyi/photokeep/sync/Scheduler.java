package cn.lisiyi.photokeep.sync;

import android.content.Context;
import androidx.work.*;
import cn.lisiyi.photokeep.data.State;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class Scheduler {
    public static final String PERIODIC = "photokeep-periodic", MANUAL = "photokeep-manual";
    public static void configure(Context context, State state) {
        WorkManager manager = WorkManager.getInstance(context);
        if (!state.scheduled) { manager.cancelUniqueWork(PERIODIC); return; }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ScanWorker.class, Math.max(6, state.intervalHours), TimeUnit.HOURS)
                .setInputData(new Data.Builder().putBoolean("background", true).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
                .build();
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request);
    }
    public static void scan(Context context, Set<String> approved, long previewTime) {
        Data data = new Data.Builder().putStringArray("approved", approved.toArray(new String[0])).putLong("previewTime", previewTime).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ScanWorker.class).setInputData(data)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()).build();
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, request);
    }
    public static void stop(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(MANUAL);
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC);
    }
}
