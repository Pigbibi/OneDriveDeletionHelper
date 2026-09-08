package cn.lisiyi.photokeep.data;

import android.content.Context;
import android.net.Uri;
import cn.lisiyi.photokeep.core.*;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;

/** This client exposes reads and recycle only. It has no upload, overwrite, move, or permanent-delete operation. */
public final class GraphApi implements RecycleGuard.Cloud {
    private static final String BASE = "https://graph.microsoft.com/v1.0";
    private final Context context;
    private final State state;
    private final BooleanSupplier stopped;
    private String drive;
    public GraphApi(Context context, State state, BooleanSupplier stopped) {
        this.context = context; this.state = state; this.stopped = stopped; drive = state.driveId;
    }
    private String token() throws AppFailure {
        checkStop();
        Auth.Session session = Auth.session(context, state.clientId);
        if (!session.accountId.equals(state.accountId)) throw new AppFailure("ACCOUNT");
        return session.token;
    }
    private void checkStop() throws AppFailure {
        if (stopped.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new AppFailure("CANCELLED");
    }
    public String driveId() throws AppFailure {
        JSONObject data = get(BASE + "/me/drive?$select=id");
        String actual = data.optString("id");
        if (actual.isEmpty()) throw new AppFailure("ACCOUNT");
        if (!drive.isEmpty() && !drive.equals(actual)) throw new AppFailure("ACCOUNT");
        drive = actual;
        return actual;
    }
    private String itemUrl(String id) { return BASE + "/drives/" + Uri.encode(drive) + "/items/" + Uri.encode(id); }
    public List<CloudItem> children(String folderId, String path) throws AppFailure {
        if (drive.isEmpty()) driveId();
        String url = folderId.equals("root") ? BASE + "/drives/" + Uri.encode(drive) + "/root/children" : itemUrl(folderId) + "/children";
        url += "?$top=200&$select=id,name,size,eTag,folder,file,remoteItem,package,parentReference";
        List<CloudItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (!url.isEmpty()) {
            if (!seen.add(url)) throw new AppFailure("LIMIT");
            JSONObject page = get(url);
            JSONArray entries = page.optJSONArray("value");
            if (entries == null) throw new AppFailure("NETWORK");
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.optJSONObject(i);
                if (item == null || item.has("remoteItem") || item.has("package")) continue;
                boolean folder = item.has("folder");
                if (folder || isMedia(item)) result.add(parse(item, path));
                if (result.size() > 100_000) throw new AppFailure("LIMIT");
            }
            url = page.optString("@odata.nextLink", "");
        }
        return result;
    }
    public List<CloudItem> listSelected() throws AppFailure {
        driveId();
        record Folder(String id, String path) {}
        ArrayDeque<Folder> queue = new ArrayDeque<>();
        for (Map.Entry<String, String> root : state.cloudRoots.entrySet()) {
            JSONObject data = metadata(root.getKey());
            if (!data.has("folder") || data.has("remoteItem")) throw new AppFailure("CLOUD_CHANGED");
            queue.add(new Folder(root.getKey(), root.getValue()));
        }
        Set<String> visited = new HashSet<>(), files = new HashSet<>();
        List<CloudItem> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            checkStop();
            Folder folder = queue.remove();
            if (!visited.add(folder.id())) continue;
            for (CloudItem item : children(folder.id(), folder.path())) {
                if (item.folder()) queue.add(new Folder(item.id(), item.path()));
                else if (files.add(item.id())) result.add(item);
            }
            if (result.size() + visited.size() > 100_000) throw new AppFailure("LIMIT");
        }
        return result;
    }
    private JSONObject metadata(String id) throws AppFailure {
        return get(itemUrl(id) + "?$select=id,name,size,eTag,folder,file,remoteItem,package,parentReference");
    }
    @Override public CloudItem current(String id) throws AppFailure {
        JSONObject item = metadata(id);
        if (item.has("folder") || item.has("remoteItem") || item.has("package") || !isMedia(item)) throw new AppFailure("CLOUD_CHANGED");
        JSONObject parent = item.optJSONObject("parentReference");
        Set<String> visited = new HashSet<>();
        boolean allowed = false;
        for (int i = 0; i < 100 && parent != null; i++) {
            if (!drive.equals(parent.optString("driveId", drive))) throw new AppFailure("CLOUD_CHANGED");
            String parentId = parent.optString("id");
            if (state.cloudRoots.containsKey(parentId)) { allowed = true; break; }
            if (parentId.isEmpty() || !visited.add(parentId)) break;
            parent = metadata(parentId).optJSONObject("parentReference");
        }
        if (!allowed) throw new AppFailure("CLOUD_CHANGED");
        return parse(item, "");
    }
    @Override public String contentSha256(CloudItem item) throws AppFailure {
        HttpsURLConnection connection = null;
        try {
            connection = connectGraph(itemUrl(item.id()) + "/content", "GET");
            int status = connection.getResponseCode();
            if (status == 302 || status == 303 || status == 307) {
                String location = connection.getHeaderField("Location");
                connection.disconnect(); connection = null;
                // Pre-authenticated download URLs are kept in memory and never receive the Graph bearer token.
                for (int i = 0; i < 4; i++) {
                    URL url = new URL(location);
                    if (!url.getProtocol().equals("https") || url.getUserInfo() != null || !downloadHostAllowed(url.getHost())) throw new AppFailure("CONTENT");
                    connection = (HttpsURLConnection) url.openConnection();
                    configure(connection, "GET");
                    status = connection.getResponseCode();
                    if (status == 302 || status == 303 || status == 307) {
                        location = new URL(url, connection.getHeaderField("Location")).toString();
                        connection.disconnect(); connection = null;
                    } else break;
                }
            }
            if (connection == null || status != 200) throw new AppFailure("CONTENT");
            try (InputStream input = connection.getInputStream()) { return ContentHash.sha256(input, item.size(), stopped); }
        } catch (AppFailure e) { throw e; }
        catch (InterruptedException e) { throw new AppFailure("CANCELLED"); }
        catch (Exception e) { throw new AppFailure("CONTENT"); }
        finally { if (connection != null) connection.disconnect(); }
    }
    public static boolean downloadHostAllowed(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return List.of("1drv.com", "onedrive.com", "sharepoint.com", "storage.live.com", "onedrive.live.com")
                .stream().anyMatch(suffix -> h.equals(suffix) || h.endsWith("." + suffix));
    }
    @Override public void recycle(CloudItem item) throws AppFailure {
        if (item.folder() || item.etag().trim().isEmpty()) throw new AppFailure("CLOUD_CHANGED");
        try {
            okhttp3.Request request = new okhttp3.Request.Builder().url(itemUrl(item.id()))
                    .header("Authorization", "Bearer " + token()).header("If-Match", item.etag())
                    .header("Connection", "close").delete().build();
            try (okhttp3.Response response = DELETE_CLIENT.newCall(request).execute()) {
                if (response.code() != 204) throw new AppFailure("UNCERTAIN");
            }
        } catch (Exception e) { throw new AppFailure("UNCERTAIN"); }
    }
    private static final okhttp3.OkHttpClient DELETE_CLIENT = new okhttp3.OkHttpClient.Builder()
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS).build();
    private JSONObject get(String url) throws AppFailure {
        HttpsURLConnection connection = null;
        try {
            connection = connectGraph(url, "GET");
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new AppFailure("LOGIN");
            if (status == 404) throw new AppFailure("CLOUD_CHANGED");
            if (status != 200) throw new AppFailure("NETWORK");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkStop();
                    bytes.write(buffer, 0, count);
                    if (bytes.size() > 8 * 1024 * 1024) throw new AppFailure("LIMIT");
                }
                return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            }
        } catch (AppFailure e) { throw e; }
        catch (Exception e) { throw new AppFailure("NETWORK"); }
        finally { if (connection != null) connection.disconnect(); }
    }
    private HttpsURLConnection connectGraph(String url, String method) throws Exception {
        URL address = new URL(url);
        if (!address.getProtocol().equals("https") || !address.getHost().equals("graph.microsoft.com")
                || address.getUserInfo() != null || (address.getPort() != -1 && address.getPort() != 443)
                || !address.getPath().startsWith("/v1.0/")) throw new AppFailure("NETWORK");
        String authorization = token();
        HttpsURLConnection connection = (HttpsURLConnection) address.openConnection();
        configure(connection, method);
        connection.setRequestProperty("Authorization", "Bearer " + authorization);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }
    private static void configure(HttpsURLConnection connection, String method) throws Exception {
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15_000); connection.setReadTimeout(30_000);
        connection.setUseCaches(false); connection.setRequestMethod(method);
    }
    private static CloudItem parse(JSONObject j, String parentPath) throws AppFailure {
        String id = j.optString("id"), name = j.optString("name"), etag = j.optString("eTag");
        boolean folder = j.has("folder");
        long size = j.optLong("size", -1);
        if (id.isEmpty() || name.isEmpty() || (!folder && (size <= 0 || etag.isEmpty()))) throw new AppFailure("CLOUD_CHANGED");
        return new CloudItem(id, name, parentPath.isEmpty() ? name : parentPath + "/" + name, size, etag, folder);
    }
    private static boolean isMedia(JSONObject j) {
        JSONObject file = j.optJSONObject("file");
        if (file == null) return false;
        String mime = file.optString("mimeType", "");
        if (mime.startsWith("image/") || mime.startsWith("video/")) return true;
        String name = j.optString("name").toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(jpg|jpeg|png|heic|heif|webp|gif|dng|avif|mp4|mov|m4v|mkv|3gp|avi)$");
    }
}
