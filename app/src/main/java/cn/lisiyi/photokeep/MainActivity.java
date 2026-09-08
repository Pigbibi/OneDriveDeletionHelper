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
import android.text.Html;
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
    private static final int BG = Color.rgb(244,245,240), INK = Color.rgb(32,56,45), MUTED = Color.rgb(86,102,92), GREEN = Color.rgb(23,107,85), PALE = Color.rgb(228,236,220);
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
        LinearLayout root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        body = column(); body.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        text(body, "PHOTO KEEP  /  PIGBIBI", 11, MUTED, false);
        gap(body, 8);
        text(body, page == 0 ? "拾光清理" : page == 1 ? "清理预览" : "连接与设置", 28, INK, true);
        gap(body, 22);
        if (working || loading) {
            LinearLayout banner = panel(body, PALE);
            text(banner, loading ? "正在读取，请稍候…" : workMessage, 14, INK, false);
            if (working) action(banner, "停止并暂停后台检查", false, this::stopWork);
            gap(body, 18);
        }
        if (page == 0) overview(); else if (page == 1) preview(); else settings();
        LinearLayout nav = new LinearLayout(this); nav.setPadding(dp(16), dp(8), dp(16), dp(8));
        String[] tabs = {"总览", "预览", "设置"};
        for (int i = 0; i < tabs.length; i++) {
            final int target = i;
            Button button = button(tabs[i], false, () -> { page = target; refresh(); });
            button.setTextColor(i == page ? GREEN : MUTED); button.setBackground(background(i == page ? PALE : BG, 16));
            button.setContentDescription(tabs[i] + (i == page ? "，当前页面" : ""));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(3), 0, dp(3), 0);
            nav.addView(button, p);
        }
        root.addView(nav); setContentView(root); root.requestApplyInsets();
    }
    private void overview() {
        LinearLayout hero = panel(body, PALE);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = column();
        text(words, "手机整理完，\n云端也清爽。", 25, INK, true);
        gap(words, 14); text(words, "沿用 OneDrive 备份，\n让清理跟上你的相册。", 14, MUTED, false);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        if (getResources().getConfiguration().screenWidthDp >= 380 && getResources().getConfiguration().fontScale <= 1.15f)
            row.addView(new PhotoArtwork(this), new LinearLayout.LayoutParams(dp(108), dp(155)));
        hero.addView(row);
        gap(body, 24);
        boolean connected = !state.accountId.isEmpty();
        String primary = !connected ? "连接 OneDrive" : state.folders.isEmpty() || state.cloudRoots.isEmpty() ? "选择管理目录" : state.lastScan == 0 ? "建立首次对应关系" : "立即检查照片";
        Button start = action(body, primary, true, () -> {
            if (!connected || state.folders.isEmpty() || state.cloudRoots.isEmpty()) { page = 2; refresh(); }
            else if (!LocalScanner.permitted(this)) permissions();
            else { selected.clear(); Scheduler.scan(this, Set.of(), 0); toast("已加入检查队列，连接 Wi-Fi 后运行"); }
        });
        start.setEnabled(!working && !loading);
        gap(body, 6); text(body, "仅通过 Wi-Fi 检查 · 首次检查不会删除文件", 12, MUTED, false);
        gap(body, 26);
        LinearLayout numbers = new LinearLayout(this);
        metric(numbers, String.valueOf(state.localCount), "手机照片");
        metric(numbers, String.valueOf(state.bindings.size()), "已建立关联");
        metric(numbers, String.valueOf(state.bindings.values().stream().filter(Binding::candidate).count()), "待清理核对");
        body.addView(numbers); gap(body, 26);
        text(body, "当前状态", 17, INK, true); gap(body, 12);
        statusLine("OneDrive", connected ? "已连接" : "尚未连接");
        statusLine("照片目录", state.folders.size() + " 个手机目录 · " + state.cloudRoots.size() + " 个云端目录");
        statusLine("后台检查", state.scheduled ? "约每 " + state.intervalHours + " 小时 · Wi-Fi" : "手动检查");
        statusLine("自动清理", state.automatic ? "已开启 · 系统回收站文件" : "关闭 · 先预览确认");
        gap(body, 20); text(body, state.lastMessage, 14, MUTED, false);
        if (state.lastScan > 0) { gap(body, 6); text(body, "上次检查 " + new java.text.SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(new Date(state.lastScan)), 12, MUTED, false); }
        action(body, "查看清理预览  →", false, () -> { page = 1; refresh(); });
    }
    private void preview() {
        text(body, "核对后，移入 OneDrive 回收站。", 16, INK, false);
        gap(body, 6); text(body, "清理前会读取云端原文件并核对内容，视频较大时需要等待。手机原文件和云端年月目录保持现状。", 14, MUTED, false);
        gap(body, 18);
        List<Binding> candidates = new ArrayList<>();
        for (Binding b : state.bindings.values()) if (b.candidate()) candidates.add(b);
        if (candidates.isEmpty()) {
            LinearLayout empty = panel(body, Color.WHITE);
            empty.addView(new PhotoArtwork(this), new LinearLayout.LayoutParams(-1, dp(145)));
            text(empty, state.lastScan == 0 ? "先认识你的照片" : "暂时没有待清理文件", 20, INK, true);
            gap(empty, 8); text(empty, state.lastScan == 0 ? "完成目录设置并检查一次，之后的变化会出现在这里。" : "已经关联且符合条件的文件才会出现在这里。历史多余照片会保留。", 14, MUTED, false);
        } else {
            text(body, candidates.size() + " 个文件需要核对", 17, INK, true);
            int count = 0;
            for (Binding b : candidates) {
                if (++count > 100) break;
                gap(body, 12);
                LinearLayout card = panel(body, Color.WHITE);
                CheckBox check = new CheckBox(this); check.setText(b.name); check.setTextSize(16); check.setTextColor(INK);
                check.setButtonTintList(ColorStateList.valueOf(GREEN)); check.setMinHeight(dp(48)); check.setChecked(selected.contains(b.cloudId));
                check.setEnabled(!working);
                check.setOnCheckedChangeListener((button, checked) -> {
                    if (checked && selected.size() >= 10) { check.setChecked(false); toast("每次最多确认 10 个文件"); return; }
                    if (checked) selected.add(b.cloudId); else selected.remove(b.cloudId);
                    if (recycleButton != null) recycleButton.setText("核对并清理所选（" + selected.size() + "）");
                });
                card.addView(check);
                text(card, b.status.equals("TRASHED") ? "手机系统回收站" : "手机中未找到 · 需要你确认", 13, GREEN, true);
                gap(card, 6); text(card, b.cloudPath, 12, MUTED, false);
                gap(card, 4); text(card, b.note + " · " + size(b.size), 12, MUTED, false);
            }
            if (candidates.size() > 100) text(body, "先显示 100 个，处理后继续显示其余文件。", 13, MUTED, false);
            recycleButton = action(body, "核对并清理所选（" + selected.size() + "）", true, this::confirmRecycle);
            recycleButton.setEnabled(!working);
            action(body, "保留所选文件", false, () -> mutate(s -> {
                for (String id : selected) { Binding b = s.bindings.get(id); if (b != null) { b.status = "IGNORED"; b.note = "用户选择保留，不再自动清理"; } }
                selected.clear();
            }));
        }
        gap(body, 22);
        long held = state.bindings.values().stream().filter(b -> b.status.equals("HOLD") || b.status.equals("UNCERTAIN") || b.status.equals("SENDING")).count();
        text(body, "保留与记录", 17, INK, true); gap(body, 8);
        text(body, state.unresolvedCount + " 个云端文件尚未关联，" + held + " 个关联需要进一步核对。它们不会自动删除。", 14, MUTED, false);
        action(body, "查看需要核对的文件", false, this::heldFiles);
        action(body, "查看最近记录", false, () -> new AlertDialog.Builder(this).setTitle("最近记录")
                .setMessage(state.history.isEmpty() ? "还没有检查记录。" : String.join("\n\n", state.history)).setPositiveButton("知道了", null).show());
        action(body, "打开 OneDrive", false, () -> open("https://onedrive.live.com/"));
    }
    private void settings() {
        text(body, "一次连接，日常轻松整理。", 16, MUTED, false); gap(body, 22);
        section("01", "连接 OneDrive");
        text(body, state.accountId.isEmpty() ? "通过微软官方登录页面授权。" : "已连接：" + state.accountLabel, 14, MUTED, false);
        action(body, state.accountId.isEmpty() ? "登录 OneDrive" : "重新授权", true, this::login);
        action(body, "首次连接设置", false, this::clientDialog);
        if (!state.accountId.isEmpty()) action(body, "断开本应用的连接", false, this::signOut);
        gap(body, 24); section("02", "选择照片目录");
        text(body, "支持手机内部存储的相机、Pictures、截图等目录。云端目录可以继续按年月存放。", 14, MUTED, false);
        action(body, LocalScanner.permitted(this) ? "照片访问权限已允许" : "允许照片和视频访问", false, this::permissions);
        action(body, "选择手机目录（" + state.folders.size() + "）", true, this::localFolders);
        if (!state.folders.isEmpty()) text(body, String.join("\n", state.folders), 13, MUTED, false);
        action(body, "添加 OneDrive 目录", false, () -> {
            if (state.accountId.isEmpty()) { toast("请先登录 OneDrive"); return; }
            cloudNavigation.clear(); cloudNavigation.add(new CloudItem("root", "OneDrive", "OneDrive", 0, "", true)); browseCloud();
        });
        for (Map.Entry<String,String> root : state.cloudRoots.entrySet()) action(body, root.getValue() + "  ·  移除", false, () -> new AlertDialog.Builder(this)
                .setTitle("移除管理目录？").setMessage("会重建本应用的对应关系并关闭自动清理，云端文件不会删除。")
                .setNegativeButton("取消", null).setPositiveButton("移除", (d,w) -> mutate(s -> { s.cloudRoots.remove(root.getKey()); s.resetMapping(); })).show());
        gap(body, 24); section("03", "检查与清理");
        action(body, state.scheduled ? "检查频率：每 " + state.intervalHours + " 小时" : "检查频率：手动", false, this::scheduleDialog);
        action(body, state.automatic ? "自动清理：已开启" : "自动清理：关闭", false, this::autoDialog);
        text(body, "自动清理仅处理系统明确标记为回收站、连续两次检查且满 24 小时的文件。只是不见了的照片仍需手动确认。", 13, MUTED, false);
        action(body, "手机后台运行设置", false, () -> {
            new AlertDialog.Builder(this).setTitle("手机后台运行设置")
                    .setMessage("在手机的应用设置中允许通知，并按需调整后台运行或电池优化限制。部分系统另有自启动选项，菜单名称因设备及系统版本而异。系统仍可能推迟检查，打开应用可手动检查。")
                    .setNegativeButton("知道了", null).setPositiveButton("打开应用设置", (d,w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))).show();
        });
        gap(body, 24); section("04", "关于拾光");
        action(body, "安装与连接教程", false, () -> guide("guide.html", "使用教程"));
        action(body, "隐私说明", false, () -> guide("privacy.html", "隐私说明"));
        action(body, "开源许可证", false, () -> guide("licenses.txt", "开源许可证"));
        action(body, "GitHub 开源项目  →", false, () -> open("https://github.com/Pigbibi/OneDriveDeletionHelper"));
        gap(body, 10); text(body, "PhotoKeep 0.1.0 · MIT License\nCopyright © 2026 Pigbibi\n独立开源项目，与 Microsoft、Google 无隶属关系。", 12, MUTED, false);
    }
    private void confirmRecycle() {
        if (selected.isEmpty()) { toast("先勾选需要清理的文件"); return; }
        Set<String> ids = new LinkedHashSet<>(selected);
        long previewTime = state.lastScan;
        new AlertDialog.Builder(this).setTitle("清理所选 " + ids.size() + " 个文件？")
                .setMessage("请确认这些照片是你主动删除的，而非移动、隐藏或释放手机空间。\n\n应用会重新检查手机，并读取云端原文件核对内容。确认一致后移入 OneDrive 回收站；不会清空回收站。")
                .setNegativeButton("再看看", null).setPositiveButton("确认并核对内容", (d,w) -> { Scheduler.scan(this, ids, previewTime); selected.clear(); }).show();
    }
    private void clientDialog() {
        LinearLayout layout = column(); layout.setPadding(dp(24), dp(8), dp(24), 0);
        text(layout, "第一次需要在微软注册自己的应用。将公开的 Application (client) ID 填在这里，不需要客户端密码。", 14, MUTED, false);
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("Application (client) ID"); input.setContentDescription("微软应用公开标识"); input.setText(state.clientId); layout.addView(input);
        try {
            text(layout, "注册 Android 平台时使用下面的信息：", 13, MUTED, false);
            TextView registration = text(layout, "包名：" + getPackageName() + "\n签名哈希：" + Auth.signature(this), 12, INK, false); registration.setTextIsSelectable(true);
            action(layout, "复制包名与签名哈希", false, () -> { try { copy(getPackageName() + "\n" + Auth.signature(this)); } catch (AppFailure e) { toast(e.getMessage()); } });
        } catch (AppFailure e) { text(layout, e.getMessage(), 13, MUTED, false); }
        new AlertDialog.Builder(this).setTitle("首次连接设置").setView(layout).setNeutralButton("查看教程", (d,w) -> guide("guide.html", "首次连接教程"))
                .setNegativeButton("取消", null).setPositiveButton("保存", (d,w) -> {
                    String id = input.getText().toString().trim();
                    if (!id.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) { toast("请输入有效的公开应用 ID"); return; }
                    mutate(s -> { if (!s.clientId.equals(id)) { s.clientId = id; s.accountId = ""; s.accountLabel = ""; s.driveId = ""; s.cloudRoots.clear(); s.resetMapping(); } });
                }).show();
    }
    private void login() {
        if (working || loading) { toast("请等待检查完成"); return; }
        if (state.clientId.isEmpty()) { clientDialog(); return; }
        loading = true; render();
        Auth.get(this, state.clientId).whenComplete((app, error) -> runOnUiThread(() -> {
            loading = false;
            if (isDestroyed()) return;
            if (error != null) { toast("请检查首次连接设置中的应用注册信息"); refresh(); return; }
            AuthenticationCallback callback = new AuthenticationCallback() {
                @Override public void onSuccess(IAuthenticationResult result) { runOnUiThread(() -> mutate(s -> {
                    String id = result.getAccount().getId();
                    if (!s.accountId.equals(id)) { s.cloudRoots.clear(); s.driveId = ""; s.resetMapping(); }
                    s.accountId = id; s.accountLabel = result.getAccount().getUsername(); s.lastMessage = "连接成功，请选择手机和 OneDrive 照片目录。";
                })); }
                @Override public void onError(MsalException exception) { runOnUiThread(() -> { toast("登录未完成，请检查注册信息或稍后重试"); refresh(); }); }
                @Override public void onCancel() { runOnUiThread(() -> { toast("已取消登录"); refresh(); }); }
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
        new AlertDialog.Builder(this).setTitle("断开连接？").setMessage("本应用会停止检查并清除对应关系，OneDrive 中的照片保持原样。")
                .setNegativeButton("取消", null).setPositiveButton("断开", (d,w) -> {
                    if (working) { toast("请先停止检查"); return; }
                    Auth.get(this, state.clientId).whenComplete((app, error) -> {
                        if (error != null) { runOnUiThread(() -> toast("断开未完成，请稍后重试")); return; }
                        app.signOut(new ISingleAccountPublicClientApplication.SignOutCallback() {
                            @Override public void onSignOut() { runOnUiThread(() -> mutate(s -> { s.accountId = ""; s.accountLabel = ""; s.driveId = ""; s.cloudRoots.clear(); s.scheduled = false; s.resetMapping(); })); }
                            @Override public void onError(MsalException exception) { runOnUiThread(() -> toast("断开未完成，请稍后重试")); }
                        });
                    });
                }).show();
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
        new AlertDialog.Builder(this).setTitle("开启自动清理？")
                .setMessage("仅处理手机系统明确标记为回收站、连续两次检查且满 24 小时的文件。\n\n每次最多 10 个，且大批量删除会暂停。执行前还会核对云端原文件内容。无法找到、隐藏或释放空间的照片仍需你手动确认。\n\n首次使用请先用测试照片验证。")
                .setNegativeButton("暂不开启", null).setPositiveButton("开启自动清理", (d,w) -> mutate(s -> s.automatic = true)).show();
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
            TextView text = new TextView(this); text.setTextColor(INK); text.setTextSize(15); text.setPadding(dp(24), dp(12), dp(24), dp(12));
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            String content = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            text.setText(asset.endsWith(".txt") ? content : Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY)); text.setMovementMethod(LinkMovementMethod.getInstance());
            ScrollView scroll = new ScrollView(this); scroll.addView(text);
            new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("知道了", null).show();
        } catch (Exception e) { toast("教程暂时无法打开"); }
    }
    private void copy(String value) { ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("应用注册信息", value)); toast("已复制"); }
    private void open(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (ActivityNotFoundException e) { toast("请先安装浏览器"); } }
    private void toast(String value) { if (!isDestroyed()) Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private GradientDrawable background(int color, int radius) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable; }
    private TextView text(LinearLayout parent, String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color); view.setLineSpacing(dp(3), 1f);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private LinearLayout panel(LinearLayout parent, int color) { LinearLayout panel = column(); panel.setBackground(background(color, 22)); panel.setPadding(dp(18), dp(18), dp(18), dp(18)); parent.addView(panel, new LinearLayout.LayoutParams(-1,-2)); return panel; }
    private Button button(String title, boolean primary, Runnable run) {
        Button button = new Button(this); button.setAllCaps(false); button.setText(title); button.setTextSize(15); button.setTextColor(primary ? Color.WHITE : GREEN);
        button.setStateListAnimator(null); button.setElevation(0);
        button.setMinHeight(dp(52)); button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(35,0,0,0)), background(primary ? GREEN : Color.TRANSPARENT, 15), background(Color.WHITE, 15)));
        button.setOnClickListener(v -> run.run()); return button;
    }
    private Button action(LinearLayout parent, String title, boolean primary, Runnable run) {
        Button button = button(title, primary, run); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(10); parent.addView(button, p); return button;
    }
    private void metric(LinearLayout parent, String value, String title) { LinearLayout col = column(); text(col, value, 28, INK, true); text(col, title, 11, MUTED, false); parent.addView(col, new LinearLayout.LayoutParams(0,-2,1)); }
    private void statusLine(String title, String value) {
        LinearLayout row = new LinearLayout(this); row.setPadding(0,dp(10),0,dp(10));
        TextView label = new TextView(this); label.setText(title); label.setTextColor(MUTED); label.setTextSize(13); row.addView(label, new LinearLayout.LayoutParams(dp(90),-2));
        TextView detail = new TextView(this); detail.setText(value); detail.setTextColor(INK); detail.setTextSize(13); row.addView(detail,new LinearLayout.LayoutParams(0,-2,1)); body.addView(row);
        View line = new View(this); line.setBackgroundColor(Color.rgb(221,227,217)); body.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private void section(String number, String title) { text(body, number + "  /  " + title, 18, INK, true); gap(body,10); }
    private static String size(long bytes) { return bytes >= 1024*1024 ? String.format(Locale.CHINA, "%.1f MB", bytes/(1024.0*1024)) : Math.max(1,bytes/1024) + " KB"; }
}
