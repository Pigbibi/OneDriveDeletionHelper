package cn.lisiyi.photokeep.data;

import cn.lisiyi.photokeep.core.Binding;
import cn.lisiyi.photokeep.BuildConfig;
import java.util.*;

public final class State {
    public String clientId = BuildConfig.MICROSOFT_CLIENT_ID, accountId = "", accountLabel = "", driveId = "", mediaVersion = "";
    public boolean automatic = false, scheduled = false;
    public int intervalHours = 24;
    public long lastScan = 0;
    public int localCount = 0, cloudCount = 0, unresolvedCount = 0;
    public String lastMessage = "先连接 OneDrive，再选择需要管理的照片目录。";
    public final Set<String> folders = new TreeSet<>();
    public final Map<String, String> cloudRoots = new LinkedHashMap<>();
    public final Map<String, Binding> bindings = new LinkedHashMap<>();
    public final List<String> history = new ArrayList<>();

    public static boolean validClientId(String id) {
        return id != null && id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                && !id.equals("00000000-0000-0000-0000-000000000000");
    }
    public void changeClientId(String value) {
        String id = value.trim().toLowerCase(Locale.ROOT);
        if (!validClientId(id)) throw new IllegalArgumentException("Invalid public client ID");
        if (clientId.equalsIgnoreCase(id)) return;
        clientId = id; accountId = ""; accountLabel = ""; driveId = "";
        cloudRoots.clear(); scheduled = false; resetMapping();
    }
    public boolean completeLogin(String registrationId, String id, String label) {
        // A delayed browser callback must never attach an old registration to new settings.
        if (!clientId.equalsIgnoreCase(registrationId) || id == null || id.isEmpty()) return false;
        if (!accountId.equals(id)) {
            cloudRoots.clear(); driveId = ""; scheduled = false; resetMapping();
        }
        accountId = id; accountLabel = label == null ? "" : label;
        lastMessage = "连接成功，请选择手机和 OneDrive 照片目录。";
        return true;
    }
    public void resetMapping() {
        automatic = false; lastScan = 0; mediaVersion = "";
        bindings.clear(); localCount = cloudCount = unresolvedCount = 0;
        lastMessage = "目录或连接已更新，请重新建立对应关系。";
    }
    public void log(String message) {
        history.add(0, new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date()) + "  " + message);
        while (history.size() > 60) history.remove(history.size() - 1);
    }
}
