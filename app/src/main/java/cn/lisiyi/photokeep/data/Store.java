package cn.lisiyi.photokeep.data;

import android.content.Context;
import android.util.AtomicFile;
import cn.lisiyi.photokeep.core.Binding;
import org.json.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

/** All engine and settings mutations take GATE. AtomicFile preserves the previous valid state on interruption. */
public final class Store {
    public static final ReentrantLock GATE = new ReentrantLock();
    private final AtomicFile file;
    public Store(Context context) { file = new AtomicFile(new File(context.getNoBackupFilesDir(), "state.json")); }
    public synchronized State read() throws AppFailure {
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return new State();
        try {
            JSONObject j = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
            if (j.getInt("version") != 1) throw new AppFailure("STATE");
            State s = new State();
            s.accountId = j.getString("accountId");
            String savedClientId = j.getString("clientId");
            // Retain the exact registration used by existing accounts. Only unconfigured installs adopt the bundled one.
            if (!savedClientId.isEmpty() || !s.accountId.isEmpty()) s.clientId = savedClientId;
            s.accountLabel = j.getString("accountLabel"); s.driveId = j.getString("driveId");
            s.mediaVersion = j.getString("mediaVersion"); s.automatic = j.getBoolean("automatic");
            s.scheduled = j.getBoolean("scheduled"); s.intervalHours = j.getInt("intervalHours");
            s.lastScan = j.getLong("lastScan"); s.localCount = j.getInt("localCount");
            s.cloudCount = j.getInt("cloudCount"); s.unresolvedCount = j.getInt("unresolvedCount");
            s.lastMessage = j.getString("lastMessage");
            JSONArray folders = j.getJSONArray("folders"), history = j.getJSONArray("history"), bindings = j.getJSONArray("bindings");
            for (int i = 0; i < folders.length(); i++) s.folders.add(folders.getString(i));
            for (int i = 0; i < history.length(); i++) s.history.add(history.getString(i));
            JSONObject roots = j.getJSONObject("cloudRoots");
            for (Iterator<String> it = roots.keys(); it.hasNext();) { String key = it.next(); s.cloudRoots.put(key, roots.getString(key)); }
            for (int i = 0; i < bindings.length(); i++) {
                JSONObject b = bindings.getJSONObject(i);
                Binding binding = new Binding(b.getString("cloudId"), b.getString("localKey"), b.getString("sha256"),
                        b.getLong("size"), b.getString("name"), b.getString("originalFolder"), b.getString("cloudPath"));
                binding.status = b.getString("status"); binding.note = b.getString("note");
                binding.firstMissing = b.getLong("firstMissing"); binding.lastObserved = b.getLong("lastObserved");
                binding.observations = b.getInt("observations");
                if (!binding.sha256.matches("[a-f0-9]{64}") || binding.size <= 0 || s.bindings.containsKey(binding.cloudId)) throw new AppFailure("STATE");
                s.bindings.put(binding.cloudId, binding);
            }
            return s;
        } catch (Exception e) { throw new AppFailure("STATE"); }
    }
    public synchronized void write(State s) throws AppFailure {
        FileOutputStream stream = null;
        try {
            JSONObject j = new JSONObject().put("version", 1).put("clientId", s.clientId).put("accountId", s.accountId)
                    .put("accountLabel", s.accountLabel).put("driveId", s.driveId).put("mediaVersion", s.mediaVersion)
                    .put("automatic", s.automatic).put("scheduled", s.scheduled).put("intervalHours", s.intervalHours)
                    .put("lastScan", s.lastScan).put("localCount", s.localCount).put("cloudCount", s.cloudCount)
                    .put("unresolvedCount", s.unresolvedCount).put("lastMessage", s.lastMessage)
                    .put("folders", new JSONArray(s.folders)).put("cloudRoots", new JSONObject(s.cloudRoots))
                    .put("history", new JSONArray(s.history));
            JSONArray bindings = new JSONArray();
            for (Binding b : s.bindings.values()) bindings.put(new JSONObject().put("cloudId", b.cloudId).put("localKey", b.localKey)
                    .put("sha256", b.sha256).put("size", b.size).put("name", b.name).put("originalFolder", b.originalFolder)
                    .put("cloudPath", b.cloudPath).put("status", b.status).put("note", b.note).put("firstMissing", b.firstMissing)
                    .put("lastObserved", b.lastObserved).put("observations", b.observations));
            j.put("bindings", bindings);
            stream = file.startWrite();
            stream.write(j.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
        } catch (Exception e) { if (stream != null) file.failWrite(stream); throw new AppFailure("STATE"); }
    }
}
