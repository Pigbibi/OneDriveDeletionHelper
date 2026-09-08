package cn.lisiyi.photokeep.data;

import cn.lisiyi.photokeep.core.Binding;
import java.util.*;

public final class State {
    public String clientId = "", accountId = "", accountLabel = "", driveId = "", mediaVersion = "";
    public boolean automatic = false, scheduled = false;
    public int intervalHours = 24;
    public long lastScan = 0;
    public int localCount = 0, cloudCount = 0, unresolvedCount = 0;
    public String lastMessage = "先连接 OneDrive，再选择需要管理的照片目录。";
    public final Set<String> folders = new TreeSet<>();
    public final Map<String, String> cloudRoots = new LinkedHashMap<>();
    public final Map<String, Binding> bindings = new LinkedHashMap<>();
    public final List<String> history = new ArrayList<>();

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
