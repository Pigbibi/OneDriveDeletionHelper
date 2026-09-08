package cn.lisiyi.photokeep.data;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import cn.lisiyi.photokeep.core.*;
import java.io.InputStream;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class LocalScanner {
    private final Context context;
    private final BooleanSupplier stopped;
    public static final String VOLUME = MediaStore.VOLUME_EXTERNAL_PRIMARY;
    public record Snapshot(List<Media> media, String version, long generation) {}
    public LocalScanner(Context context, BooleanSupplier stopped) { this.context = context; this.stopped = stopped; }
    public static boolean permitted(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED
                    || context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) return false;
        } else if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return false;
        return context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void check() throws AppFailure {
        if (stopped.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new AppFailure("CANCELLED");
        if (!permitted(context)) throw new AppFailure("PERMISSION");
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) throw new AppFailure("PERMISSION");
    }
    public Map<String, Integer> directories() throws AppFailure {
        check();
        Map<String, Integer> result = new TreeMap<>();
        for (Media media : rows()) if (!media.trashed() && !media.pending()) result.merge(media.folder(), 1, Integer::sum);
        check();
        return result;
    }
    public Snapshot scan(Consumer<String> progress) throws AppFailure {
        check();
        ContentResolver resolver = context.getContentResolver();
        String version = MediaStore.getVersion(context, VOLUME);
        long generation = MediaStore.getGeneration(context, VOLUME);
        List<Media> rows = rows(), hashed = new ArrayList<>();
        try (HashCache cache = new HashCache(context)) {
            int index = 0;
            for (Media row : rows) {
                check();
                String key = version + ":" + row.key() + ":" + row.generation() + ":" + row.modified() + ":" + row.size();
                String digest = cache.get(key);
                if (row.pending() || row.size() <= 0) digest = "";
                else if (digest.isEmpty()) {
                    try (InputStream input = resolver.openInputStream(MediaStore.setRequireOriginal(Uri.parse(row.key())))) {
                        if (input != null) { digest = ContentHash.sha256(input, row.size(), stopped); cache.put(key, digest); }
                    } catch (InterruptedException e) { throw new AppFailure("CANCELLED"); }
                    catch (Exception e) { digest = ""; }
                }
                hashed.add(new Media(row.key(), row.folder(), row.name(), row.size(), row.modified(), row.generation(), digest, row.trashed(), row.pending()));
                if (++index % 20 == 0 || index == rows.size()) progress.accept("正在读取手机照片 " + index + " / " + rows.size());
            }
        } catch (AppFailure e) { throw e; }
        catch (Exception e) { throw new AppFailure("STATE"); }
        Snapshot snapshot = new Snapshot(hashed, version, generation);
        assertUnchanged(snapshot);
        return snapshot;
    }
    public void assertUnchanged(Snapshot snapshot) throws AppFailure {
        check();
        if (!snapshot.version().equals(MediaStore.getVersion(context, VOLUME))
                || snapshot.generation() != MediaStore.getGeneration(context, VOLUME)) throw new AppFailure("MEDIA_CHANGED");
    }
    private List<Media> rows() throws AppFailure {
        List<Media> result = new ArrayList<>();
        String[] projection = {"_id", "relative_path", "_display_name", "_size", "date_modified", "generation_modified", "is_trashed", "is_pending"};
        Bundle args = new Bundle();
        args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);
        args.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE);
        try {
            for (Uri collection : List.of(MediaStore.Images.Media.getContentUri(VOLUME), MediaStore.Video.Media.getContentUri(VOLUME))) {
                try (Cursor cursor = context.getContentResolver().query(collection, projection, args, null)) {
                    if (cursor == null) throw new AppFailure("PERMISSION");
                    while (cursor.moveToNext()) {
                        String folder = cursor.getString(1);
                        if (folder == null || folder.trim().isEmpty()) throw new AppFailure("PERMISSION");
                        result.add(new Media(ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(), folder,
                                cursor.getString(2), cursor.getLong(3), cursor.getLong(4), cursor.getLong(5), "",
                                cursor.getInt(6) != 0, cursor.getInt(7) != 0));
                        if (result.size() > 100_000) throw new AppFailure("LIMIT");
                    }
                }
            }
            return result;
        } catch (AppFailure e) { throw e; }
        catch (Exception e) { throw new AppFailure("PERMISSION"); }
    }
}
