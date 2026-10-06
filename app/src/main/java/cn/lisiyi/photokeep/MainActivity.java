package cn.lisiyi.photokeep;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.view.*;
import android.widget.*;
import androidx.work.*;
import cn.lisiyi.photokeep.core.*;
import cn.lisiyi.photokeep.data.*;
import cn.lisiyi.photokeep.sync.*;
import com.microsoft.identity.client.*;
import com.microsoft.identity.client.exception.MsalException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int R_CHIP = 8, R_CARD = 16, R_HERO = 28;
    private static final int FILLED = 0, OUTLINED = 1, TEXT = 2, DANGER = 3, DANGER_OUTLINED = 4, ROW = 5, DANGER_ROW = 6;
    private int bg, surface, surfaceHigh, ink, muted, outline, divider, primary, onPrimary, primaryContainer, onPrimaryContainer, secondaryContainer, onSecondaryContainer, error, onError, errorContainer, onErrorContainer, ripple;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Set<String> selected = new LinkedHashSet<>();
    private final ArrayDeque<CloudItem> cloudNavigation = new ArrayDeque<>();
    private State state = new State();
    private int page = 0;
    private boolean working = false, loading = false;
    private String workMessage = "";
    private LinearLayout body;
    private Button recycleButton;
    private androidx.lifecycle.LiveData<List<WorkInfo>> manualData, periodicData;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) page = savedInstanceState.getInt("page", 0);
        loadColors();
        refresh();
        WorkManager manager = WorkManager.getInstance(this);
        manualData = manager.getWorkInfosForUniqueWorkLiveData(Scheduler.MANUAL);
        periodicData = manager.getWorkInfosForUniqueWorkLiveData(Scheduler.PERIODIC);
        manualData.observeForever(manualObserver);
        periodicData.observeForever(periodicObserver);
    }
    private final androidx.lifecycle.Observer<List<WorkInfo>> manualObserver = infos -> updateWork(infos, false);
    private final androidx.lifecycle.Observer<List<WorkInfo>> periodicObserver = infos -> updateWork(infos, true);
    private boolean manualWorking, periodicWorking;
    private void updateWork(List<WorkInfo> infos, boolean periodic) {
        if (isDestroyed()) return;
        boolean active = false;
        if (infos != null) for (WorkInfo info : infos) {
            if (info.getState() == WorkInfo.State.RUNNING || (!periodic && info.getState() == WorkInfo.State.ENQUEUED)) {
                active = true;
                String text = info.getProgress().getString("message");
                workMessage = text != null ? text : (info.getState() == WorkInfo.State.ENQUEUED ? "已排队，连接 Wi-Fi 后开始检查" : "正在准备检查");
            }
        }
        if (periodic) periodicWorking = active; else manualWorking = active;
        working = manualWorking || periodicWorking;
        refresh();
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("page", page); }
    @Override protected void onDestroy() {
        if (manualData != null) manualData.removeObserver(manualObserver);
        if (periodicData != null) periodicData.removeObserver(periodicObserver);
        io.shutdownNow(); super.onDestroy();
    }
    private void refresh() {
        if (isDestroyed()) return;
        if (Store.GATE.tryLock()) {
            try { state = new Store(this).read(); }
            catch (AppFailure e) { workMessage = e.getMessage(); }
            finally { Store.GATE.unlock(); }
        }
        selected.removeIf(id -> !state.bindings.containsKey(id) || !state.bindings.get(id).candidate());
        render();
    }
    private void render() {
        LinearLayout root = column(); root.setBackgroundColor(bg);
        LinearLayout nav = new LinearLayout(this); nav.setBackgroundColor(surface); nav.setGravity(Gravity.CENTER_VERTICAL);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            nav.setPadding(dp(8), dp(12), dp(8), dp(16) + bars.bottom); return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        body = column(); body.setPadding(dp(16), dp(20), dp(16), dp(32));
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(4), 0, 0, 0);
        TextView heading = new TextView(this); heading.setText(page == 0 ? "拾光清理" : page == 1 ? "清理预览" : "连接与设置");
        heading.setTextSize(30); heading.setTextColor(ink); heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setAccessibilityHeading(true);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        if (page == 0) {
            boolean connected = !state.accountId.isEmpty();
            TextView pill = pill(connected ? "已连接" : "未连接", connected ? primaryContainer : surfaceHigh, connected ? onPrimaryContainer : muted);
            header.addView(pill, new LinearLayout.LayoutParams(-2, -2));
        }
        body.addView(header, new LinearLayout.LayoutParams(-1, -2));
        gap(body, 20);
        if (working || loading) {
            LinearLayout banner = panel(body, secondaryContainer, 0, R_CARD);
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setIndeterminate(true); bar.setIndeterminateTintList(ColorStateList.valueOf(primary));
            bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            banner.addView(bar, new LinearLayout.LayoutParams(-1, dp(4)));
            gap(banner, 12);
            text(banner, loading ? "正在读取，请稍候…" : workMessage, 15, onSecondaryContainer, false);
            banner.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            if (working) action(banner, "停止并暂停后台检查", DANGER_OUTLINED, this::stopWork);
            gap(body, 16);
        }
        if (page == 0) overview(); else if (page == 1) preview(); else settings();
        String[] tabs = {"总览", "预览", "设置"};
        int[] icons = {R.drawable.ic_nav_overview, R.drawable.ic_nav_preview, R.drawable.ic_nav_settings};
        for (int i = 0; i < tabs.length; i++) {
            final int target = i;
            nav.addView(navItem(tabs[i], icons[i], i == page, () -> { page = target; refresh(); }), new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(nav); setContentView(root); root.requestApplyInsets();
    }
    private void overview() {
        boolean connected = !state.accountId.isEmpty();
        LinearLayout hero = panel(body, primaryContainer, 0, R_HERO);
        hero.setPadding(dp(20), dp(24), dp(20), dp(20));
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = column();
        text(words, "手机整理完，\n云端也清爽。", 24, onPrimaryContainer, true);
        gap(words, 10); text(words, "沿用 OneDrive 备份，\n让清理跟上你的相册。", 15, onPrimaryContainer, false);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        if (getResources().getConfiguration().screenWidthDp >= 380 && getResources().getConfiguration().fontScale <= 1.15f)
            row.addView(new PhotoArtwork(this), new LinearLayout.LayoutParams(dp(108), dp(128)));
        hero.addView(row);
        String primaryLabel = !connected ? "登录 OneDrive" : state.folders.isEmpty() || state.cloudRoots.isEmpty() ? "选择管理目录" : state.lastScan == 0 ? "建立首次对应关系" : "立即检查照片";
        Button start = action(hero, primaryLabel, FILLED, () -> {
            if (!connected) login();
            else if (state.folders.isEmpty() || state.cloudRoots.isEmpty()) { page = 2; refresh(); }
            else if (!LocalScanner.permitted(this)) permissions();
            else { selected.clear(); Scheduler.scan(this, Set.of(), 0); toast("已加入检查队列，连接 Wi-Fi 后运行"); }
        });
        ((LinearLayout.LayoutParams) start.getLayoutParams()).topMargin = dp(20);
        start.setEnabled(!working && !loading);
        gap(hero, 10); text(hero, "仅通过 Wi-Fi 检查，首次检查不会删除文件", 13, onPrimaryContainer, false);
        gap(body, 16);
        long pending = state.bindings.values().stream().filter(Binding::candidate).count();
        LinearLayout numbers = new LinearLayout(this); numbers.setGravity(Gravity.CENTER_VERTICAL);
        numbers.setBackground(shape(surface, 0, R_CARD)); numbers.setPadding(dp(20), dp(18), dp(16), dp(18));
        LinearLayout lead = column();
        TextView big = text(lead, String.valueOf(pending), 44, pending > 0 ? primary : ink, true); big.setIncludeFontPadding(false);
        gap(lead, 4); text(lead, "待清理核对", 14, muted, false);
        numbers.addView(lead, new LinearLayout.LayoutParams(0, -2, 1));
        View split = new View(this); split.setBackgroundColor(divider);
        LinearLayout.LayoutParams splitParams = new LinearLayout.LayoutParams(dp(1), dp(56)); splitParams.setMargins(dp(12), 0, dp(16), 0);
        numbers.addView(split, splitParams);
        LinearLayout side = column();
        figure(side, "手机照片", String.valueOf(state.localCount)); gap(side, 10);
        figure(side, "已建立关联", String.valueOf(state.bindings.size()));
        numbers.addView(side, new LinearLayout.LayoutParams(0, -2, 1.2f));
        body.addView(numbers, new LinearLayout.LayoutParams(-1, -2));
        gap(body, 28);
        section("当前状态");
        LinearLayout status = panel(body, surface, 0, R_CARD); status.setPadding(dp(16), dp(4), dp(16), dp(4));
        statusLine(status, "OneDrive", connected ? "已连接" : "尚未连接", connected ? ink : muted, true);
        statusLine(status, "照片目录", state.folders.size() + " 个手机目录，" + state.cloudRoots.size() + " 个云端目录", ink, true);
        statusLine(status, "后台检查", state.scheduled ? "约每 " + state.intervalHours + " 小时，仅 Wi-Fi" : "手动检查", ink, true);
        statusLine(status, "自动清理", state.automatic ? "已开启，处理系统回收站文件" : "关闭，先预览确认", state.automatic ? error : ink, false);
        gap(body, 16);
        LinearLayout log = column(); log.setPadding(dp(4), 0, dp(4), 0); body.addView(log);
        text(log, state.lastMessage, 14, muted, false);
        if (state.lastScan > 0) { gap(log, 4); text(log, "上次检查 " + new java.text.SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(new Date(state.lastScan)), 13, muted, false); }
        action(body, "查看清理预览", TEXT, () -> { page = 1; refresh(); });
    }
    private void preview() {
        LinearLayout intro = column(); intro.setPadding(dp(4), 0, dp(4), 0); body.addView(intro);
        text(intro, "核对后，移入 OneDrive 回收站。", 17, ink, true);
        gap(intro, 6); text(intro, "清理前会读取云端原文件并核对内容，视频较大时需要等待。手机原文件和云端年月目录保持现状。", 14, muted, false);
        gap(body, 20);
        List<Binding> candidates = new ArrayList<>();
        for (Binding b : state.bindings.values()) if (b.candidate()) candidates.add(b);
        if (candidates.isEmpty()) {
            LinearLayout empty = panel(body, surface, 0, R_HERO); empty.setPadding(dp(24), dp(28), dp(24), dp(28));
            empty.addView(new PhotoArtwork(this), new LinearLayout.LayoutParams(-1, dp(128)));
            gap(empty, 16);
            text(empty, state.lastScan == 0 ? "先认识你的照片" : "暂时没有待清理文件", 20, ink, true).setGravity(Gravity.CENTER_HORIZONTAL);
            gap(empty, 8); text(empty, state.lastScan == 0 ? "完成目录设置并检查一次，之后的变化会出现在这里。" : "已经关联且符合条件的文件才会出现在这里。历史多余照片会保留。", 14, muted, false).setGravity(Gravity.CENTER_HORIZONTAL);
        } else {
            section(candidates.size() + " 个文件需要核对");
            int count = 0;
            for (Binding b : candidates) {
                if (++count > 100) break;
                if (count > 1) gap(body, 8);
                boolean picked = selected.contains(b.cloudId);
                LinearLayout card = panel(body, picked ? errorContainer : surface, 0, R_CARD);
                card.setPadding(dp(8), dp(4), dp(16), dp(16));
                CheckBox check = new CheckBox(this); check.setText(b.name); check.setTextSize(16); check.setTextColor(picked ? onErrorContainer : ink);
                check.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{error, outline}));
                check.setMinHeight(dp(48)); check.setChecked(picked);
                check.setEnabled(!working);
                LinearLayout details = column(); details.setPadding(dp(40), 0, 0, 0);
                check.setOnCheckedChangeListener((button, checked) -> {
                    if (checked && selected.size() >= 10) { check.setChecked(false); toast("每次最多确认 10 个文件"); return; }
                    if (checked) selected.add(b.cloudId); else selected.remove(b.cloudId);
                    card.setBackground(shape(checked ? errorContainer : surface, 0, R_CARD));
                    check.setTextColor(checked ? onErrorContainer : ink);
                    if (recycleButton != null) recycleButton.setText("核对并清理所选（" + selected.size() + "）");
                });
                card.addView(check);
                card.addView(details);
                boolean trashed = b.status.equals("TRASHED");
                chip(details, trashed ? "手机系统回收站" : "手机中未找到，需要你确认", trashed ? primaryContainer : errorContainer, trashed ? onPrimaryContainer : onErrorContainer);
                gap(details, 8); text(details, b.cloudPath, 13, muted, false);
                gap(details, 2); text(details, b.note + "，" + size(b.size), 13, muted, false);
            }
            if (candidates.size() > 100) { gap(body, 10); text(body, "先显示 100 个，处理后继续显示其余文件。", 13, muted, false); }
            gap(body, 20);
            LinearLayout confirm = panel(body, errorContainer, 0, R_CARD); confirm.setPadding(dp(20), dp(18), dp(20), dp(20));
            text(confirm, "移入 OneDrive 回收站前，会再次确认并核对内容。", 14, onErrorContainer, false);
            recycleButton = action(confirm, "核对并清理所选（" + selected.size() + "）", DANGER, this::confirmRecycle);
            recycleButton.setEnabled(!working);
            action(body, "保留所选文件", OUTLINED, () -> mutate(s -> {
                for (String id : selected) { Binding b = s.bindings.get(id); if (b != null) { b.status = "IGNORED"; b.note = "用户选择保留，不再自动清理"; } }
                selected.clear();
            }));
        }
        gap(body, 28);
        long held = state.bindings.values().stream().filter(b -> b.status.equals("HOLD") || b.status.equals("UNCERTAIN") || b.status.equals("SENDING")).count();
        section("保留与记录");
        LinearLayout records = group();
        LinearLayout note = column(); note.setPadding(0, dp(12), 0, dp(8)); records.addView(note);
        text(note, state.unresolvedCount + " 个云端文件尚未关联，" + held + " 个关联需要进一步核对。它们不会自动删除。", 14, muted, false);
        action(records, "查看需要核对的文件", ROW, this::heldFiles);
        action(records, "查看最近记录", ROW, () -> new AlertDialog.Builder(this).setTitle("最近记录")
                .setMessage(state.history.isEmpty() ? "还没有检查记录。" : String.join("\n\n", state.history)).setPositiveButton("知道了", null).show());
        external(action(records, "打开 OneDrive", ROW, () -> open("https://onedrive.live.com/")));
    }
    private void settings() {
        LinearLayout intro = column(); intro.setPadding(dp(4), 0, dp(4), 0); body.addView(intro);
        text(intro, "一次连接，日常轻松整理。", 15, muted, false); gap(body, 24);
        section("连接 OneDrive");
        LinearLayout connection = panel(body, surface, 0, R_CARD); connection.setPadding(dp(16), dp(16), dp(16), dp(8));
        text(connection, state.accountId.isEmpty() ? "通过微软官方登录页面授权。" : "已连接：" + state.accountLabel, 15, state.accountId.isEmpty() ? muted : ink, !state.accountId.isEmpty());
        action(connection, state.accountId.isEmpty() ? "登录 OneDrive" : "重新授权", FILLED, this::login);
        gap(connection, 8);
        action(connection, "高级连接设置", ROW, this::advancedConnection);
        if (!state.accountId.isEmpty()) action(connection, "断开本应用的连接", DANGER_ROW, this::signOut);
        gap(body, 28); section("选择照片目录");
        LinearLayout folders = panel(body, surface, 0, R_CARD); folders.setPadding(dp(16), dp(16), dp(16), dp(8));
        text(folders, "支持手机内部存储的相机、Pictures、截图等目录。云端目录可以继续按年月存放。", 14, muted, false);
        boolean permitted = LocalScanner.permitted(this);
        action(folders, permitted ? "照片访问权限已允许" : "允许照片和视频访问", permitted ? ROW : OUTLINED, this::permissions);
        action(folders, "选择手机目录（" + state.folders.size() + "）", FILLED, this::localFolders);
        if (!state.folders.isEmpty()) {
            gap(folders, 12);
            LinearLayout paths = panel(folders, surfaceHigh, 0, R_CHIP); paths.setPadding(dp(12), dp(10), dp(12), dp(10));
            text(paths, String.join("\n", state.folders), 13, muted, false);
        }
        action(folders, "添加 OneDrive 目录", OUTLINED, () -> {
            if (state.accountId.isEmpty()) { toast("请先登录 OneDrive"); return; }
            cloudNavigation.clear(); cloudNavigation.add(new CloudItem("root", "OneDrive", "OneDrive", 0, "", true)); browseCloud();
        });
        gap(folders, 4);
        for (Map.Entry<String,String> root : state.cloudRoots.entrySet()) action(folders, root.getValue() + "  ·  移除", DANGER_ROW, () -> danger(new AlertDialog.Builder(this)
                .setTitle("移除管理目录？").setMessage("会重建本应用的对应关系并关闭自动清理，云端文件不会删除。")
                .setNegativeButton("取消", null).setPositiveButton("移除", (d,w) -> mutate(s -> { s.cloudRoots.remove(root.getKey()); s.resetMapping(); })).show()));
        gap(body, 28); section("检查与清理");
        LinearLayout cleanup = group();
        action(cleanup, state.scheduled ? "检查频率：每 " + state.intervalHours + " 小时" : "检查频率：手动", ROW, this::scheduleDialog);
        action(cleanup, state.automatic ? "自动清理：已开启" : "自动清理：关闭", state.automatic ? DANGER_ROW : ROW, this::autoDialog);
        action(cleanup, "手机后台运行设置", ROW, () -> {
            new AlertDialog.Builder(this).setTitle("手机后台运行设置")
                    .setMessage("在手机的应用设置中允许通知，并按需调整后台运行或电池优化限制。部分系统另有自启动选项，菜单名称因设备及系统版本而异。系统仍可能推迟检查，打开应用可手动检查。")
                    .setNegativeButton("知道了", null).setPositiveButton("打开应用设置", (d,w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))).show();
        });
        LinearLayout hint = column(); hint.setPadding(0, dp(4), 0, dp(14)); cleanup.addView(hint);
        text(hint, "自动清理仅处理系统明确标记为回收站、连续两次检查且满 24 小时的文件。只是不见了的照片仍需手动确认。", 13, muted, false);
        gap(body, 28); section("关于拾光");
        LinearLayout about = group();
        action(about, "安装与连接教程", ROW, () -> guide("guide.html", "使用教程"));
        action(about, "隐私说明", ROW, () -> guide("privacy.html", "隐私说明"));
        action(about, "开源许可证", ROW, () -> guide("licenses.txt", "开源许可证"));
        external(action(about, "GitHub 开源项目", ROW, () -> open("https://github.com/Pigbibi/OneDriveDeletionHelper")));
        gap(body, 20);
        LinearLayout footer = column(); footer.setPadding(dp(4), 0, dp(4), 0); body.addView(footer);
        text(footer, "PhotoKeep " + BuildConfig.VERSION_NAME + "，MIT License\nCopyright © 2026 Pigbibi\n独立开源项目，与 Microsoft、Google 无隶属关系。", 12, muted, false);
    }
    private void confirmRecycle() {
        if (selected.isEmpty()) { toast("先勾选需要清理的文件"); return; }
        Set<String> ids = new LinkedHashSet<>(selected);
        long previewTime = state.lastScan;
        danger(new AlertDialog.Builder(this).setTitle("清理所选 " + ids.size() + " 个文件？")
                .setMessage("请确认这些照片是你主动删除的，而非移动、隐藏或释放手机空间。\n\n应用会重新检查手机，并读取云端原文件核对内容。确认一致后移入 OneDrive 回收站；不会清空回收站。")
                .setNegativeButton("再看看", null).setPositiveButton("确认并核对内容", (d,w) -> { Scheduler.scan(this, ids, previewTime); selected.clear(); }).show());
    }
    private void advancedConnection() {
        if (working || loading) { toast("请等待当前操作完成"); return; }
        LinearLayout layout = column(); layout.setPadding(dp(24), dp(8), dp(24), dp(8));
        boolean bundled = state.clientId.equalsIgnoreCase(BuildConfig.MICROSOFT_CLIENT_ID);
        text(layout, bundled ? "当前使用应用内置连接。通常无需修改。" : "当前使用自定义连接。升级时会保留原有配置。", 14, muted, false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("高级连接设置").setView(layout)
                .setNegativeButton("关闭", null).create();
        action(layout, "自定义 Client ID", OUTLINED, () -> { dialog.dismiss(); clientDialog(); });
        Button restore = action(layout, "恢复默认连接", OUTLINED, () -> {
            dialog.dismiss(); changeConnection(BuildConfig.MICROSOFT_CLIENT_ID);
        });
        restore.setEnabled(!bundled && State.validClientId(BuildConfig.MICROSOFT_CLIENT_ID));
        dialog.show();
    }
    private void changeConnection(String id) {
        if (working || loading) { toast("请等待当前操作完成"); return; }
        if (state.clientId.equalsIgnoreCase(id)) return;
        danger(new AlertDialog.Builder(this).setTitle("切换连接配置？")
                .setMessage("切换后需要重新登录并选择 OneDrive 目录，后台检查和自动清理会关闭，照片对应关系会重建。手机和云端照片不会删除。")
                .setNegativeButton("取消", null).setPositiveButton("切换", (d,w) -> {
                    if (working || loading) { toast("请等待当前操作完成"); return; }
                    mutate(s -> s.changeClientId(id));
                }).show());
    }
    private void clientDialog() {
        LinearLayout layout = column(); layout.setPadding(dp(24), dp(8), dp(24), 0);
        text(layout, "仅供自行注册微软应用或编译的用户使用。普通用户无需填写。这里只接受公开应用 ID，不接受客户端密码。", 14, muted, false);
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("Application (client) ID"); input.setContentDescription("微软应用公开标识");
        if (!state.clientId.equalsIgnoreCase(BuildConfig.MICROSOFT_CLIENT_ID)) input.setText(state.clientId);
        layout.addView(input);
        try {
            gap(layout, 8); text(layout, "注册 Android 平台时使用下面的信息：", 13, muted, false);
            TextView registration = text(layout, "包名：" + getPackageName() + "\n签名哈希：" + Auth.signature(this), 13, ink, false); registration.setTextIsSelectable(true);
            registration.setTypeface(Typeface.MONOSPACE);
            action(layout, "复制包名与签名哈希", OUTLINED, () -> { try { copy(getPackageName() + "\n" + Auth.signature(this)); } catch (AppFailure e) { toast(e.getMessage()); } });
        } catch (AppFailure e) { text(layout, e.getMessage(), 13, muted, false); }
        ScrollView scroll = new ScrollView(this); scroll.addView(layout);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("自定义 Client ID").setView(scroll)
                .setNeutralButton("开发者教程", (d,w) -> open("https://github.com/Pigbibi/OneDriveDeletionHelper/blob/main/docs/MICROSOFT-APP.md"))
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String id = input.getText().toString().trim();
            if (!State.validClientId(id)) { input.setError("请输入有效的公开应用 ID"); return; }
            dialog.dismiss(); changeConnection(id);
        }));
        dialog.show();
    }
    private void login() {
        if (working || loading) { toast("请等待检查完成"); return; }
        if (!State.validClientId(state.clientId)) {
            new AlertDialog.Builder(this).setTitle("此安装包暂未开放登录")
                    .setMessage("请从项目发布页下载已配置登录的版本。普通用户无需注册微软应用。")
                    .setNegativeButton("知道了", null).setPositiveButton("前往下载", (d,w) -> open("https://github.com/Pigbibi/OneDriveDeletionHelper/releases")).show();
            return;
        }
        final String loginClientId = state.clientId;
        loading = true; render();
        Auth.get(this, loginClientId).whenComplete((app, error) -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            if (error != null) { loading = false; toast("连接暂时不可用，请稍后重试或更新应用"); refresh(); return; }
            AuthenticationCallback callback = new AuthenticationCallback() {
                @Override public void onSuccess(IAuthenticationResult result) { runOnUiThread(() -> {
                    loading = false;
                    mutate(s -> s.completeLogin(loginClientId, result.getAccount().getId(), result.getAccount().getUsername()));
                }); }
                @Override public void onError(MsalException exception) { runOnUiThread(() -> { loading = false; toast("登录未完成，请检查网络及账号授权，或稍后重试"); refresh(); }); }
                @Override public void onCancel() { runOnUiThread(() -> { loading = false; toast("已取消登录"); refresh(); }); }
            };
            app.getCurrentAccountAsync(new ISingleAccountPublicClientApplication.CurrentAccountCallback() {
                @Override public void onAccountLoaded(IAccount account) {
                    SignInParameters parameters = SignInParameters.builder().withActivity(MainActivity.this).withScopes(Auth.SCOPES).withCallback(callback).build();
                    if (account == null) app.signIn(parameters); else app.signInAgain(parameters);
                }
                @Override public void onAccountChanged(IAccount priorAccount, IAccount currentAccount) { }
                @Override public void onError(MsalException exception) { callback.onError(exception); }
            });
        }));
    }
    private void signOut() {
        if (working || loading) { toast("请等待当前操作完成"); return; }
        danger(new AlertDialog.Builder(this).setTitle("断开连接？").setMessage("本应用会停止检查并清除对应关系，OneDrive 中的照片保持原样。")
                .setNegativeButton("取消", null).setPositiveButton("断开", (d,w) -> {
                    if (working || loading) { toast("请等待当前操作完成"); return; }
                    Auth.get(this, state.clientId).whenComplete((app, error) -> {
                        if (error != null) { runOnUiThread(() -> toast("断开未完成，请稍后重试")); return; }
                        app.signOut(new ISingleAccountPublicClientApplication.SignOutCallback() {
                            @Override public void onSignOut() { runOnUiThread(() -> mutate(s -> { s.accountId = ""; s.accountLabel = ""; s.driveId = ""; s.cloudRoots.clear(); s.scheduled = false; s.resetMapping(); })); }
                            @Override public void onError(MsalException exception) { runOnUiThread(() -> toast("断开未完成，请稍后重试")); }
                        });
                    });
                }).show());
    }
    private void permissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) { permissions.add(Manifest.permission.READ_MEDIA_IMAGES); permissions.add(Manifest.permission.READ_MEDIA_VIDEO); permissions.add(Manifest.permission.POST_NOTIFICATIONS); }
        else permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= 34) permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
        requestPermissions(permissions.toArray(new String[0]), 10);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) { super.onRequestPermissionsResult(code, permissions, results); refresh(); }
    private void localFolders() {
        if (!LocalScanner.permitted(this)) { permissions(); return; }
        async(() -> new LocalScanner(this, () -> false).directories(), folders -> {
            Set<String> allPaths = new TreeSet<>(folders.keySet()); allPaths.addAll(state.folders);
            List<String> paths = new ArrayList<>(allPaths);
            if (paths.isEmpty()) { toast("没有可读取的照片目录"); return; }
            String[] labels = paths.stream().map(p -> p + "  ·  " + folders.getOrDefault(p, 0)).toArray(String[]::new);
            boolean[] checked = new boolean[paths.size()]; Set<String> picked = new TreeSet<>(state.folders);
            for (int i=0;i<paths.size();i++) checked[i] = picked.contains(paths.get(i));
            new AlertDialog.Builder(this).setTitle("选择手机目录（包含子目录）").setMultiChoiceItems(labels, checked, (d,i,value) -> {
                if (value) picked.add(paths.get(i)); else picked.remove(paths.get(i));
            }).setNegativeButton("取消", null).setPositiveButton("保存目录", (d,w) -> mutate(s -> {
                if (!s.folders.equals(picked)) { s.folders.clear(); s.folders.addAll(picked); s.resetMapping(); }
            })).show();
        });
    }
    private void browseCloud() {
        CloudItem location = cloudNavigation.getLast();
        final State snapshot = state;
        async(() -> {
            GraphApi graph = new GraphApi(getApplicationContext(), snapshot, () -> false);
            String drive = graph.driveId();
            List<CloudItem> folders = new ArrayList<>();
            for (CloudItem item : graph.children(location.id(), location.path())) if (item.folder()) folders.add(item);
            return new FolderResult(drive, folders);
        }, result -> {
            String[] names = result.folders.stream().map(CloudItem::name).toArray(String[]::new);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle(location.path()).setItems(names, (d,i) -> { cloudNavigation.add(result.folders.get(i)); browseCloud(); })
                    .setNegativeButton("取消", null).setNeutralButton(cloudNavigation.size() > 1 ? "上一级" : "关闭", (d,w) -> { if (cloudNavigation.size() > 1) { cloudNavigation.removeLast(); browseCloud(); } })
                    .setPositiveButton("选用此目录", (d,w) -> mutate(s -> { s.driveId = result.drive; s.cloudRoots.put(location.id(), location.path()); s.resetMapping(); })).create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!location.id().equals("root")));
            dialog.show();
        });
    }
    private record FolderResult(String drive, List<CloudItem> folders) {}
    private void scheduleDialog() {
        String[] choices = {"仅手动检查", "每 6 小时", "每 12 小时", "每天"};
        new AlertDialog.Builder(this).setTitle("检查频率 · 仅 Wi-Fi").setItems(choices, (d,i) -> mutate(s -> {
            s.scheduled = i != 0; s.intervalHours = i == 1 ? 6 : i == 2 ? 12 : 24;
            if (!s.scheduled) s.automatic = false;
        })).show();
    }
    private void autoDialog() {
        if (state.automatic) { mutate(s -> s.automatic = false); return; }
        if (state.lastScan == 0 || !state.scheduled || !LocalScanner.permitted(this)) { toast("请先建立对应关系、允许全部照片访问，并开启定期检查"); return; }
        if (state.bindings.values().stream().anyMatch(b -> b.status.equals("UNCERTAIN") || b.status.equals("SENDING"))) { toast("请先在预览中核对上次未确认的清理结果"); return; }
        danger(new AlertDialog.Builder(this).setTitle("开启自动清理？")
                .setMessage("仅处理手机系统明确标记为回收站、连续两次检查且满 24 小时的文件。\n\n每次最多 10 个，且大批量删除会暂停。执行前还会核对云端原文件内容。无法找到、隐藏或释放空间的照片仍需你手动确认。\n\n首次使用请先用测试照片验证。")
                .setNegativeButton("暂不开启", null).setPositiveButton("开启自动清理", (d,w) -> mutate(s -> s.automatic = true)).show());
    }
    private void heldFiles() {
        List<Binding> held = new ArrayList<>();
        for (Binding b : state.bindings.values()) if (b.status.equals("HOLD") || b.status.equals("UNCERTAIN") || b.status.equals("SENDING")) held.add(b);
        if (held.isEmpty()) { toast("没有异常关联；未关联的历史文件将原样保留"); return; }
        String[] items = held.stream().map(b -> b.name + "\n" + b.note).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("需要核对的文件").setItems(items, (d,i) -> {
            Binding b = held.get(i);
            new AlertDialog.Builder(this).setTitle(b.name).setMessage(b.cloudPath + "\n\n" + b.note + "\n\n请先到 OneDrive 核对。选择保留后，本应用不再处理这个关联。")
                    .setNegativeButton("返回", null).setNeutralButton("打开 OneDrive", (a,w) -> open("https://onedrive.live.com/"))
                    .setPositiveButton("保留并停止跟踪", (a,w) -> mutate(s -> { Binding current = s.bindings.get(b.cloudId); if (current != null) { current.status = "IGNORED"; current.note = "用户已核对并停止跟踪"; } })).show();
        }).setPositiveButton("关闭", null).show();
    }
    private void stopWork() {
        Scheduler.stop(this);
        io.execute(() -> {
            Store.GATE.lock();
            try { Store store = new Store(this); State s = store.read(); s.scheduled = false; s.automatic = false; store.write(s); }
            catch (AppFailure ignored) { }
            finally { Store.GATE.unlock(); }
            runOnUiThread(this::refresh);
        });
        toast("已请求停止并暂停后台检查");
    }
    private interface Mutation { void run(State s) throws Exception; }
    private void mutate(Mutation action) {
        if (isDestroyed()) return;
        if (!Store.GATE.tryLock()) { toast("检查正在进行，请完成或停止后再修改设置"); return; }
        try {
            Store store = new Store(this); State fresh = store.read(); action.run(fresh); store.write(fresh);
            state = fresh; Scheduler.configure(this, fresh);
        } catch (Exception e) { toast(e instanceof AppFailure ? e.getMessage() : "设置未保存，请重试"); }
        finally { Store.GATE.unlock(); }
        refresh();
    }
    private <T> void async(Callable<T> task, java.util.function.Consumer<T> onSuccess) {
        if (loading || working) { toast("请等待当前检查完成"); return; }
        loading = true; render();
        io.execute(() -> {
            try { T result = task.call(); runOnUiThread(() -> { if (isDestroyed()) return; loading = false; refresh(); onSuccess.accept(result); }); }
            catch (Exception e) { runOnUiThread(() -> { if (isDestroyed()) return; loading = false; toast(e instanceof AppFailure ? e.getMessage() : "读取未完成，请稍后重试"); refresh(); }); }
        });
    }
    private void guide(String asset, String title) {
        try (java.io.InputStream input = getAssets().open(asset)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            String content = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            View view;
            if (asset.endsWith(".txt")) {
                TextView text = new TextView(this); text.setTextColor(ink); text.setTextSize(13); text.setTypeface(Typeface.MONOSPACE); text.setLineSpacing(dp(2), 1f); text.setPadding(dp(24), dp(12), dp(24), dp(12));
                text.setText(content); text.setMovementMethod(LinkMovementMethod.getInstance());
                ScrollView scroll = new ScrollView(this); scroll.addView(text); view = scroll;
            } else view = document(content);
            new AlertDialog.Builder(this).setTitle(title).setView(view).setPositiveButton("知道了", null).show();
        } catch (Exception e) { toast("教程暂时无法打开"); }
    }
    /** Bundled pages only: no JavaScript, file or network access; links open in the user's browser. */
    private View document(String html) {
        android.webkit.WebView web = new android.webkit.WebView(this);
        android.webkit.WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(false); settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setBlockNetworkLoads(true);
        web.setBackgroundColor(getColor(R.color.pk_surface_container_high));
        web.setWebViewClient(new android.webkit.WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView view, android.webkit.WebResourceRequest request) {
                open(request.getUrl().toString()); return true;
            }
        });
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        return web;
    }
    private void danger(AlertDialog dialog) {
        Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (confirm != null) { confirm.setTextColor(error); confirm.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); }
    }
    private void loadColors() {
        bg = getColor(R.color.pk_background); surface = getColor(R.color.pk_surface_container); surfaceHigh = getColor(R.color.pk_surface_container_high);
        ink = getColor(R.color.pk_on_surface); muted = getColor(R.color.pk_on_surface_variant); outline = getColor(R.color.pk_outline); divider = getColor(R.color.pk_outline_variant);
        primary = getColor(R.color.pk_primary); onPrimary = getColor(R.color.pk_on_primary);
        primaryContainer = getColor(R.color.pk_primary_container); onPrimaryContainer = getColor(R.color.pk_on_primary_container);
        secondaryContainer = getColor(R.color.pk_secondary_container); onSecondaryContainer = getColor(R.color.pk_on_secondary_container);
        error = getColor(R.color.pk_error); onError = getColor(R.color.pk_on_error);
        errorContainer = getColor(R.color.pk_error_container); onErrorContainer = getColor(R.color.pk_on_error_container);
        ripple = getColor(R.color.pk_ripple);
    }
    private void copy(String value) { ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("应用注册信息", value)); toast("已复制"); }
    private void open(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (ActivityNotFoundException e) { toast("请先安装浏览器"); } }
    private void toast(String value) { if (!isDestroyed()) Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    /** Shape scale: chips 8, cards 16, hero and empty states 28, buttons fully rounded. */
    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }
    private TextView text(LinearLayout parent, String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color); view.setLineSpacing(dp(3), 1f);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private LinearLayout panel(LinearLayout parent, int fill, int stroke, int radius) {
        LinearLayout panel = column(); panel.setBackground(shape(fill, stroke, radius)); panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        parent.addView(panel, new LinearLayout.LayoutParams(-1,-2)); return panel;
    }
    /** Grouped list container for settings-style rows. */
    private LinearLayout group() { LinearLayout group = panel(body, surface, 0, R_CARD); group.setPadding(dp(16), dp(4), dp(16), dp(4)); return group; }
    private TextView pill(String value, int fill, int color) {
        TextView pill = new TextView(this); pill.setText(value); pill.setTextSize(13); pill.setTextColor(color);
        pill.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pill.setBackground(shape(fill, 0, 999)); pill.setPadding(dp(12), dp(6), dp(12), dp(6)); return pill;
    }
    private void chip(LinearLayout parent, String value, int fill, int color) {
        TextView chip = new TextView(this); chip.setText(value); chip.setTextSize(12); chip.setTextColor(color);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setBackground(shape(fill, 0, R_CHIP)); chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        parent.addView(chip, new LinearLayout.LayoutParams(-2, -2));
    }
    /** Material 3 navigation bar item: one clickable label carrying the indicator pill and icon. */
    private TextView navItem(String title, int icon, boolean current, Runnable run) {
        TextView item = new TextView(this); item.setText(title); item.setTextSize(12); item.setGravity(Gravity.CENTER);
        item.setTextColor(current ? ink : muted);
        item.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Drawable glyph = getDrawable(icon).mutate(); glyph.setTint(current ? onSecondaryContainer : muted);
        LayerDrawable indicator = new LayerDrawable(new Drawable[]{shape(current ? secondaryContainer : Color.TRANSPARENT, 0, 16), glyph});
        indicator.setLayerSize(0, dp(64), dp(32)); indicator.setLayerSize(1, dp(24), dp(24)); indicator.setLayerGravity(1, Gravity.CENTER);
        item.setCompoundDrawablesWithIntrinsicBounds(null, indicator, null, null); item.setCompoundDrawablePadding(dp(4));
        item.setMinHeight(dp(56)); item.setClickable(true); item.setFocusable(true);
        item.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), null, shape(Color.WHITE, 0, 16)));
        item.setContentDescription(title + (current ? "，当前页面" : "")); item.setSelected(current);
        item.setOnClickListener(v -> run.run()); return item;
    }
    private Button button(String title, int kind, Runnable run) {
        boolean row = kind == ROW || kind == DANGER_ROW;
        int fill = kind == FILLED ? primary : kind == DANGER ? error : Color.TRANSPARENT;
        int stroke = kind == OUTLINED ? outline : kind == DANGER_OUTLINED ? error : 0;
        int label = kind == FILLED ? onPrimary : kind == DANGER ? onError : kind == DANGER_OUTLINED || kind == DANGER_ROW ? error : kind == ROW ? ink : primary;
        int radius = row ? R_CHIP : 999;
        Button button = new Button(this); button.setAllCaps(false); button.setText(title);
        button.setTextSize(row ? 16 : 15); button.setLetterSpacing(0);
        button.setTypeface(row ? Typeface.DEFAULT : Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{(ink & 0x00FFFFFF) | 0x61000000, label}));
        button.setStateListAnimator(null); button.setElevation(0);
        button.setMinHeight(dp(row ? 56 : 48)); button.setMinimumHeight(dp(row ? 56 : 48));
        int side = row ? 0 : kind == TEXT ? 4 : 24;
        button.setPadding(dp(side), dp(10), dp(side), dp(10));
        if (row) {
            button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            Drawable chevron = getDrawable(R.drawable.ic_chevron).mutate(); chevron.setTint(kind == DANGER_ROW ? error : muted);
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, chevron, null);
        }
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, shape(fill == Color.TRANSPARENT ? Color.TRANSPARENT : (ink & 0x00FFFFFF) | 0x1F000000, stroke == 0 ? 0 : (ink & 0x00FFFFFF) | 0x1F000000, radius));
        states.addState(new int[]{}, shape(fill, stroke, radius));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), states, shape(Color.WHITE, 0, radius)));
        button.setOnClickListener(v -> run.run()); return button;
    }
    private Button action(LinearLayout parent, String title, int kind, Runnable run) {
        Button button = button(title, kind, run);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(kind == TEXT ? -2 : -1, -2);
        p.topMargin = dp(kind == ROW || kind == DANGER_ROW ? 0 : 12); parent.addView(button, p); return button;
    }
    private void external(Button row) {
        Drawable icon = getDrawable(R.drawable.ic_open_external).mutate(); icon.setTint(muted);
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null);
    }
    private void figure(LinearLayout parent, String title, String value) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(this); label.setText(title); label.setTextColor(muted); label.setTextSize(13);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView number = new TextView(this); number.setText(value); number.setTextColor(ink); number.setTextSize(18);
        number.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); row.addView(number);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
    private void statusLine(LinearLayout parent, String title, String value, int valueColor, boolean separated) {
        LinearLayout row = new LinearLayout(this); row.setPadding(0,dp(14),0,dp(14));
        TextView label = new TextView(this); label.setText(title); label.setTextColor(muted); label.setTextSize(14); row.addView(label, new LinearLayout.LayoutParams(dp(84),-2));
        TextView detail = new TextView(this); detail.setText(value); detail.setTextColor(valueColor); detail.setTextSize(15); row.addView(detail,new LinearLayout.LayoutParams(0,-2,1)); parent.addView(row);
        if (separated) { View line = new View(this); line.setBackgroundColor(divider); parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1))); }
    }
    private void section(String title) {
        TextView label = new TextView(this); label.setText(title); label.setTextSize(14); label.setTextColor(primary);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); label.setAccessibilityHeading(true);
        label.setPadding(dp(4), 0, dp(4), 0); body.addView(label, new LinearLayout.LayoutParams(-1, -2)); gap(body, 10);
    }
    private static String size(long bytes) { return bytes >= 1024*1024 ? String.format(Locale.CHINA, "%.1f MB", bytes/(1024.0*1024)) : Math.max(1,bytes/1024) + " KB"; }
}
