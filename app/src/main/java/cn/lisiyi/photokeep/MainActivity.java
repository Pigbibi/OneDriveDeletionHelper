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

    @Override protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppPrefs.wrap(newBase));
    }

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
                workMessage = text != null ? AppText.resolve(this, text) : getString(info.getState() == WorkInfo.State.ENQUEUED ? R.string.work_queued : R.string.work_preparing);
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
            catch (AppFailure e) { workMessage = e.message(this); }
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
        TextView heading = new TextView(this); heading.setText(page == 0 ? R.string.app_name : page == 1 ? R.string.title_preview : R.string.title_settings);
        heading.setTextSize(30); heading.setTextColor(ink); heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setAccessibilityHeading(true);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        if (page == 0) {
            boolean connected = !state.accountId.isEmpty();
            TextView pill = pill(getString(connected ? R.string.status_connected : R.string.status_disconnected), connected ? primaryContainer : surfaceHigh, connected ? onPrimaryContainer : muted);
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
            text(banner, loading ? getString(R.string.loading_wait) : workMessage, 15, onSecondaryContainer, false);
            banner.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            if (working) action(banner, getString(R.string.action_stop_work), DANGER_OUTLINED, this::stopWork);
            gap(body, 16);
        }
        if (page == 0) overview(); else if (page == 1) preview(); else settings();
        String[] tabs = {getString(R.string.nav_overview), getString(R.string.nav_preview), getString(R.string.nav_settings)};
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
        String headline = getString(!connected ? R.string.hero_not_connected : state.folders.isEmpty() || state.cloudRoots.isEmpty() ? R.string.hero_no_folders : state.lastScan == 0 ? R.string.hero_no_mapping : R.string.hero_connected);
        text(words, headline, 24, onPrimaryContainer, true);
        gap(words, 10); text(words, state.lastScan > 0 ? getString(R.string.last_scan_at, formatScanTime(state.lastScan)) : getString(R.string.last_scan_never), 15, onPrimaryContainer, false);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        if (getResources().getConfiguration().screenWidthDp >= 380 && getResources().getConfiguration().fontScale <= 1.15f)
            row.addView(new PhotoArtwork(this), new LinearLayout.LayoutParams(dp(108), dp(128)));
        hero.addView(row);
        String primaryLabel = getString(!connected ? R.string.action_sign_in : state.folders.isEmpty() || state.cloudRoots.isEmpty() ? R.string.action_select_folders : state.lastScan == 0 ? R.string.action_first_scan : R.string.action_scan_now);
        Button start = action(hero, primaryLabel, FILLED, () -> {
            if (!connected) login();
            else if (state.folders.isEmpty() || state.cloudRoots.isEmpty()) { page = 2; refresh(); }
            else if (!LocalScanner.permitted(this)) permissions();
            else { selected.clear(); Scheduler.scan(this, Set.of(), 0); toast(getString(R.string.toast_scan_queued)); }
        });
        ((LinearLayout.LayoutParams) start.getLayoutParams()).topMargin = dp(20);
        start.setEnabled(!working && !loading);
        gap(hero, 10); text(hero, getString(R.string.hero_hint), 13, onPrimaryContainer, false);
        gap(body, 16);
        long pending = state.bindings.values().stream().filter(Binding::candidate).count();
        LinearLayout numbers = new LinearLayout(this); numbers.setGravity(Gravity.CENTER_VERTICAL);
        numbers.setBackground(shape(surface, 0, R_CARD)); numbers.setPadding(dp(20), dp(18), dp(16), dp(18));
        LinearLayout lead = column();
        TextView big = text(lead, String.valueOf(pending), 44, pending > 0 ? primary : ink, true); big.setIncludeFontPadding(false);
        gap(lead, 4); text(lead, getString(R.string.metric_pending), 14, muted, false);
        numbers.addView(lead, new LinearLayout.LayoutParams(0, -2, 1));
        View split = new View(this); split.setBackgroundColor(divider);
        LinearLayout.LayoutParams splitParams = new LinearLayout.LayoutParams(dp(1), dp(56)); splitParams.setMargins(dp(12), 0, dp(16), 0);
        numbers.addView(split, splitParams);
        LinearLayout side = column();
        figure(side, getString(R.string.metric_local_photos), String.valueOf(state.localCount)); gap(side, 10);
        figure(side, getString(R.string.metric_linked), String.valueOf(state.bindings.size()));
        numbers.addView(side, new LinearLayout.LayoutParams(0, -2, 1.2f));
        body.addView(numbers, new LinearLayout.LayoutParams(-1, -2));
        gap(body, 28);
        section(getString(R.string.section_status));
        LinearLayout status = panel(body, surface, 0, R_CARD); status.setPadding(dp(16), dp(4), dp(16), dp(4));
        statusLine(status, getString(R.string.label_onedrive), getString(connected ? R.string.status_connected : R.string.status_not_connected), connected ? ink : muted, true);
        statusLine(status, getString(R.string.label_folders), getString(R.string.status_folders_value, state.folders.size(), state.cloudRoots.size()), ink, true);
        statusLine(status, getString(R.string.label_background_check), state.scheduled ? getString(R.string.status_schedule_periodic, state.intervalHours) : getString(R.string.status_schedule_manual), ink, true);
        statusLine(status, getString(R.string.label_auto_cleanup), getString(state.automatic ? R.string.status_auto_on : R.string.status_auto_off), state.automatic ? error : ink, false);
        gap(body, 16);
        LinearLayout log = column(); log.setPadding(dp(4), 0, dp(4), 0); body.addView(log);
        text(log, AppText.resolve(this, state.lastMessage), 14, muted, false);
        if (state.lastScan > 0) { gap(log, 4); text(log, getString(R.string.last_scan_at, formatScanTime(state.lastScan)), 13, muted, false); }
        action(body, getString(R.string.action_view_preview), TEXT, () -> { page = 1; refresh(); });
    }
    private void preview() {
        LinearLayout intro = column(); intro.setPadding(dp(4), 0, dp(4), 0); body.addView(intro);
        text(intro, getString(R.string.preview_heading), 17, ink, true);
        gap(intro, 6); text(intro, getString(R.string.preview_subtitle), 14, muted, false);
        gap(body, 20);
        List<Binding> candidates = new ArrayList<>();
        for (Binding b : state.bindings.values()) if (b.candidate()) candidates.add(b);
        if (candidates.isEmpty()) {
            LinearLayout empty = panel(body, surface, 0, R_HERO); empty.setPadding(dp(24), dp(28), dp(24), dp(28));
            empty.addView(new PhotoArtwork(this), new LinearLayout.LayoutParams(-1, dp(128)));
            gap(empty, 16);
            text(empty, getString(state.lastScan == 0 ? R.string.empty_first_scan_title : R.string.empty_clean_title), 20, ink, true).setGravity(Gravity.CENTER_HORIZONTAL);
            gap(empty, 8); text(empty, getString(state.lastScan == 0 ? R.string.empty_first_scan_desc : R.string.empty_clean_desc), 14, muted, false).setGravity(Gravity.CENTER_HORIZONTAL);
        } else {
            section(getString(R.string.section_candidates_count, candidates.size()));
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
                    if (checked && selected.size() >= DeletionPolicy.MAX_MANUAL_BATCH) { check.setChecked(false); toast(getString(R.string.toast_max_batch, DeletionPolicy.MAX_MANUAL_BATCH)); return; }
                    if (checked) selected.add(b.cloudId); else selected.remove(b.cloudId);
                    card.setBackground(shape(checked ? errorContainer : surface, 0, R_CARD));
                    check.setTextColor(checked ? onErrorContainer : ink);
                    if (recycleButton != null) recycleButton.setText(getString(R.string.action_verify_clean_selected, selected.size()));
                });
                card.addView(check);
                card.addView(details);
                boolean trashed = b.status.equals(Binding.TRASHED);
                chip(details, getString(trashed ? R.string.chip_trashed : R.string.chip_missing), trashed ? primaryContainer : errorContainer, trashed ? onPrimaryContainer : onErrorContainer);
                gap(details, 8); text(details, b.cloudPath, 13, muted, false);
                gap(details, 2); text(details, getString(R.string.candidate_note_size, AppText.resolve(this, b.note), size(b.size)), 13, muted, false);
            }
            if (candidates.size() > 100) { gap(body, 10); text(body, getString(R.string.preview_limit_hint), 13, muted, false); }
            gap(body, 20);
            LinearLayout confirm = panel(body, errorContainer, 0, R_CARD); confirm.setPadding(dp(20), dp(18), dp(20), dp(20));
            text(confirm, getString(R.string.preview_confirm_hint), 14, onErrorContainer, false);
            recycleButton = action(confirm, getString(R.string.action_verify_clean_selected, selected.size()), DANGER, this::confirmRecycle);
            recycleButton.setEnabled(!working);
            action(body, getString(R.string.action_keep_selected), OUTLINED, () -> mutate(s -> {
                for (String id : selected) { Binding b = s.bindings.get(id); if (b != null) { b.status = Binding.IGNORED; b.note = "pk:note_ignored_preview"; } }
                selected.clear();
            }));
        }
        gap(body, 28);
        long held = state.bindings.values().stream().filter(Binding::needsReview).count();
        section(getString(R.string.section_records));
        LinearLayout records = group();
        LinearLayout note = column(); note.setPadding(0, dp(12), 0, dp(8)); records.addView(note);
        text(note, getString(R.string.records_summary, state.unresolvedCount, held), 14, muted, false);
        action(records, getString(R.string.action_view_held), ROW, this::heldFiles);
        action(records, getString(R.string.action_view_history), ROW, () -> new AlertDialog.Builder(this).setTitle(R.string.dialog_history_title)
                .setMessage(state.history.isEmpty() ? getString(R.string.dialog_history_empty) : state.history.stream().map(entry -> AppText.resolveHistory(this, entry)).collect(java.util.stream.Collectors.joining("\n\n"))).setPositiveButton(R.string.action_got_it, null).show());
        external(action(records, getString(R.string.action_open_onedrive), ROW, () -> open("https://onedrive.live.com/")));
    }
    private void settings() {
        section(getString(R.string.section_connection));
        LinearLayout connection = panel(body, surface, 0, R_CARD); connection.setPadding(dp(16), dp(16), dp(16), dp(8));
        text(connection, state.accountId.isEmpty() ? getString(R.string.connection_prompt) : getString(R.string.connection_signed_in, state.accountLabel), 15, state.accountId.isEmpty() ? muted : ink, !state.accountId.isEmpty());
        action(connection, getString(state.accountId.isEmpty() ? R.string.action_sign_in : R.string.action_reauthorize), FILLED, this::login);
        gap(connection, 8);
        action(connection, getString(R.string.action_advanced_connection), ROW, this::advancedConnection);
        if (!state.accountId.isEmpty()) action(connection, getString(R.string.action_disconnect), DANGER_ROW, this::signOut);
        gap(body, 28); section(getString(R.string.section_folders));
        LinearLayout folders = panel(body, surface, 0, R_CARD); folders.setPadding(dp(16), dp(16), dp(16), dp(8));
        text(folders, getString(R.string.folders_hint), 14, muted, false);
        boolean permitted = LocalScanner.permitted(this);
        action(folders, getString(permitted ? R.string.action_photos_permitted : R.string.action_photos_allow), ROW, this::permissions);
        action(folders, getString(R.string.action_choose_local_folders, state.folders.size()), FILLED, this::localFolders);
        if (!state.folders.isEmpty()) {
            gap(folders, 12);
            LinearLayout paths = panel(folders, surfaceHigh, 0, R_CHIP); paths.setPadding(dp(12), dp(10), dp(12), dp(10));
            text(paths, String.join("\n", state.folders), 13, muted, false);
        }
        action(folders, getString(R.string.action_add_onedrive_folder), ROW, () -> {
            if (state.accountId.isEmpty()) { toast(getString(R.string.toast_sign_in_first)); return; }
            cloudNavigation.clear(); cloudNavigation.add(new CloudItem("root", getString(R.string.label_onedrive), getString(R.string.label_onedrive), 0, "", true)); browseCloud();
        });
        gap(folders, 4);
        for (Map.Entry<String,String> root : state.cloudRoots.entrySet()) action(folders, getString(R.string.action_remove_cloud_root, root.getValue()), DANGER_ROW, () -> danger(new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_remove_folder_title).setMessage(R.string.dialog_remove_folder_message)
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_remove, (d,w) -> mutate(s -> { s.cloudRoots.remove(root.getKey()); s.resetMapping(); })).show()));
        gap(body, 28); section(getString(R.string.section_cleanup));
        LinearLayout cleanup = group();
        action(cleanup, state.scheduled ? getString(R.string.action_schedule_periodic, state.intervalHours) : getString(R.string.action_schedule_manual), ROW, this::scheduleDialog);
        action(cleanup, getString(state.automatic ? R.string.action_auto_on : R.string.action_auto_off), state.automatic ? DANGER_ROW : ROW, this::autoDialog);
        action(cleanup, getString(R.string.action_background_settings), ROW, () -> {
            new AlertDialog.Builder(this).setTitle(R.string.action_background_settings)
                    .setMessage(R.string.dialog_background_message)
                    .setNegativeButton(R.string.action_got_it, null).setPositiveButton(R.string.action_open_app_settings, (d,w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))).show();
        });
        LinearLayout hint = column(); hint.setPadding(0, dp(4), 0, dp(14)); cleanup.addView(hint);
        text(hint, getString(R.string.cleanup_auto_hint), 13, muted, false);
        gap(body, 28); section(getString(R.string.section_appearance_language));
        LinearLayout appearance = group();
        String lang = AppPrefs.language(this);
        int langLabel = AppPrefs.LANG_ZH.equals(lang) ? R.string.lang_zh : AppPrefs.LANG_EN.equals(lang) ? R.string.lang_en : R.string.lang_system;
        action(appearance, getString(R.string.action_language, getString(langLabel)), ROW, this::languageDialog);
        String theme = AppPrefs.theme(this);
        int themeLabel = AppPrefs.THEME_LIGHT.equals(theme) ? R.string.theme_light : AppPrefs.THEME_DARK.equals(theme) ? R.string.theme_dark : R.string.theme_system;
        action(appearance, getString(R.string.action_theme, getString(themeLabel)), ROW, this::themeDialog);
        gap(body, 28); section(getString(R.string.section_about));
        LinearLayout about = group();
        action(about, getString(R.string.action_setup_guide), ROW, () -> guide(getString(R.string.asset_guide), getString(R.string.title_guide)));
        action(about, getString(R.string.title_privacy), ROW, () -> guide(getString(R.string.asset_privacy), getString(R.string.title_privacy)));
        action(about, getString(R.string.title_licenses), ROW, () -> guide("licenses.txt", getString(R.string.title_licenses)));
        external(action(about, getString(R.string.action_github), ROW, () -> open("https://github.com/Pigbibi/OneDriveDeletionHelper")));
        gap(body, 20);
        LinearLayout footer = column(); footer.setPadding(dp(4), 0, dp(4), 0); body.addView(footer);
        text(footer, getString(R.string.about_footer, BuildConfig.VERSION_NAME), 12, muted, false);
    }
    private void confirmRecycle() {
        if (selected.isEmpty()) { toast(getString(R.string.toast_select_files_first)); return; }
        Set<String> ids = new LinkedHashSet<>(selected);
        long previewTime = state.lastScan;
        danger(new AlertDialog.Builder(this).setTitle(getString(R.string.dialog_confirm_recycle_title, ids.size()))
                .setMessage(R.string.dialog_confirm_recycle_message)
                .setNegativeButton(R.string.action_review_again, null).setPositiveButton(R.string.action_confirm_verify, (d,w) -> { Scheduler.scan(this, ids, previewTime); selected.clear(); }).show());
    }
    private void advancedConnection() {
        if (working || loading) { toast(getString(R.string.toast_wait_operation)); return; }
        LinearLayout layout = column(); layout.setPadding(dp(24), dp(8), dp(24), dp(8));
        boolean bundled = state.clientId.equalsIgnoreCase(BuildConfig.MICROSOFT_CLIENT_ID);
        text(layout, getString(bundled ? R.string.connection_mode_bundled : R.string.connection_mode_custom), 14, muted, false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.action_advanced_connection).setView(layout)
                .setNegativeButton(R.string.action_close, null).create();
        action(layout, getString(R.string.action_custom_client_id), OUTLINED, () -> { dialog.dismiss(); clientDialog(); });
        Button restore = action(layout, getString(R.string.action_restore_default_connection), OUTLINED, () -> {
            dialog.dismiss(); changeConnection(BuildConfig.MICROSOFT_CLIENT_ID);
        });
        restore.setEnabled(!bundled && State.validClientId(BuildConfig.MICROSOFT_CLIENT_ID));
        dialog.show();
    }
    private void changeConnection(String id) {
        if (working || loading) { toast(getString(R.string.toast_wait_operation)); return; }
        if (state.clientId.equalsIgnoreCase(id)) return;
        danger(new AlertDialog.Builder(this).setTitle(R.string.dialog_switch_connection_title)
                .setMessage(R.string.dialog_switch_connection_message)
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_switch, (d,w) -> {
                    if (working || loading) { toast(getString(R.string.toast_wait_operation)); return; }
                    mutate(s -> s.changeClientId(id));
                }).show());
    }
    private void clientDialog() {
        LinearLayout layout = column(); layout.setPadding(dp(24), dp(8), dp(24), 0);
        text(layout, getString(R.string.dialog_client_id_warning), 14, muted, false);
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint(R.string.hint_client_id); input.setContentDescription(getString(R.string.cd_client_id));
        if (!state.clientId.equalsIgnoreCase(BuildConfig.MICROSOFT_CLIENT_ID)) input.setText(state.clientId);
        layout.addView(input);
        try {
            gap(layout, 8); text(layout, getString(R.string.dialog_client_id_platform_hint), 13, muted, false);
            TextView registration = text(layout, getString(R.string.dialog_client_id_registration_info, getPackageName(), Auth.signature(this)), 13, ink, false); registration.setTextIsSelectable(true);
            registration.setTypeface(Typeface.MONOSPACE);
            action(layout, getString(R.string.action_copy_registration_info), OUTLINED, () -> { try { copy(getPackageName() + "\n" + Auth.signature(this)); } catch (AppFailure e) { toast(e.message(this)); } });
        } catch (AppFailure e) { text(layout, e.message(this), 13, muted, false); }
        ScrollView scroll = new ScrollView(this); scroll.addView(layout);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.action_custom_client_id).setView(scroll)
                .setNeutralButton(R.string.action_developer_guide, (d,w) -> open("https://github.com/Pigbibi/OneDriveDeletionHelper/blob/main/docs/microsoft-app.md"))
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_save, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String id = input.getText().toString().trim();
            if (!State.validClientId(id)) { input.setError(getString(R.string.error_invalid_client_id)); return; }
            dialog.dismiss(); changeConnection(id);
        }));
        dialog.show();
    }
    private void login() {
        if (working || loading) { toast(getString(R.string.toast_wait_check)); return; }
        if (!State.validClientId(state.clientId)) {
            new AlertDialog.Builder(this).setTitle(R.string.dialog_login_unavailable_title)
                    .setMessage(R.string.dialog_login_unavailable_message)
                    .setNegativeButton(R.string.action_got_it, null).setPositiveButton(R.string.action_go_download, (d,w) -> open("https://github.com/Pigbibi/OneDriveDeletionHelper/releases")).show();
            return;
        }
        final String loginClientId = state.clientId;
        loading = true; render();
        Auth.get(this, loginClientId).whenComplete((app, error) -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            if (error != null) { loading = false; toast(getString(R.string.toast_connection_unavailable)); refresh(); return; }
            AuthenticationCallback callback = new AuthenticationCallback() {
                @Override public void onSuccess(IAuthenticationResult result) { runOnUiThread(() -> {
                    loading = false;
                    mutate(s -> s.completeLogin(loginClientId, result.getAccount().getId(), result.getAccount().getUsername()));
                }); }
                @Override public void onError(MsalException exception) { runOnUiThread(() -> { loading = false; toast(getString(R.string.toast_login_incomplete)); refresh(); }); }
                @Override public void onCancel() { runOnUiThread(() -> { loading = false; toast(getString(R.string.toast_login_cancelled)); refresh(); }); }
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
        if (working || loading) { toast(getString(R.string.toast_wait_operation)); return; }
        danger(new AlertDialog.Builder(this).setTitle(R.string.dialog_disconnect_title).setMessage(R.string.dialog_disconnect_message)
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_disconnect_confirm, (d,w) -> {
                    if (working || loading) { toast(getString(R.string.toast_wait_operation)); return; }
                    Auth.get(this, state.clientId).whenComplete((app, error) -> {
                        if (error != null) { runOnUiThread(() -> toast(getString(R.string.toast_disconnect_incomplete))); return; }
                        app.signOut(new ISingleAccountPublicClientApplication.SignOutCallback() {
                            @Override public void onSignOut() { runOnUiThread(() -> mutate(s -> { s.accountId = ""; s.accountLabel = ""; s.driveId = ""; s.cloudRoots.clear(); s.scheduled = false; s.resetMapping(); })); }
                            @Override public void onError(MsalException exception) { runOnUiThread(() -> toast(getString(R.string.toast_disconnect_incomplete))); }
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
            if (paths.isEmpty()) { toast(getString(R.string.toast_no_readable_folders)); return; }
            String[] labels = paths.stream().map(p -> getString(R.string.local_folder_item, p, folders.getOrDefault(p, 0))).toArray(String[]::new);
            boolean[] checked = new boolean[paths.size()]; Set<String> picked = new TreeSet<>(state.folders);
            for (int i=0;i<paths.size();i++) checked[i] = picked.contains(paths.get(i));
            new AlertDialog.Builder(this).setTitle(R.string.dialog_local_folders_title).setMultiChoiceItems(labels, checked, (d,i,value) -> {
                if (value) picked.add(paths.get(i)); else picked.remove(paths.get(i));
            }).setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_save_folders, (d,w) -> mutate(s -> {
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
                    .setNegativeButton(R.string.action_cancel, null).setNeutralButton(cloudNavigation.size() > 1 ? R.string.action_parent_folder : R.string.action_close, (d,w) -> { if (cloudNavigation.size() > 1) { cloudNavigation.removeLast(); browseCloud(); } })
                    .setPositiveButton(R.string.action_use_this_folder, (d,w) -> mutate(s -> { s.driveId = result.drive; s.cloudRoots.put(location.id(), location.path()); s.resetMapping(); })).create();
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!location.id().equals("root")));
            dialog.show();
        });
    }
    private record FolderResult(String drive, List<CloudItem> folders) {}
    private void scheduleDialog() {
        String[] choices = {getString(R.string.schedule_manual), getString(R.string.schedule_6h), getString(R.string.schedule_12h), getString(R.string.schedule_24h)};
        new AlertDialog.Builder(this).setTitle(R.string.dialog_schedule_title).setItems(choices, (d,i) -> mutate(s -> {
            s.scheduled = i != 0; s.intervalHours = i == 1 ? 6 : i == 2 ? 12 : 24;
            if (!s.scheduled) s.automatic = false;
        })).show();
    }
    private void languageDialog() {
        String[] values = {AppPrefs.LANG_SYSTEM, AppPrefs.LANG_ZH, AppPrefs.LANG_EN};
        String[] labels = {getString(R.string.lang_system), getString(R.string.lang_zh), getString(R.string.lang_en)};
        String current = AppPrefs.language(this);
        int checked = AppPrefs.LANG_ZH.equals(current) ? 1 : AppPrefs.LANG_EN.equals(current) ? 2 : 0;
        new AlertDialog.Builder(this).setTitle(R.string.dialog_language_title)
                .setSingleChoiceItems(labels, checked, (d, i) -> {
                    d.dismiss();
                    if (!values[i].equals(current)) {
                        AppPrefs.setLanguage(this, values[i]);
                        recreate();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null).show();
    }
    private void themeDialog() {
        String[] values = {AppPrefs.THEME_SYSTEM, AppPrefs.THEME_LIGHT, AppPrefs.THEME_DARK};
        String[] labels = {getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)};
        String current = AppPrefs.theme(this);
        int checked = AppPrefs.THEME_LIGHT.equals(current) ? 1 : AppPrefs.THEME_DARK.equals(current) ? 2 : 0;
        new AlertDialog.Builder(this).setTitle(R.string.dialog_theme_title)
                .setSingleChoiceItems(labels, checked, (d, i) -> {
                    d.dismiss();
                    if (!values[i].equals(current)) {
                        AppPrefs.setTheme(this, values[i]);
                        recreate();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null).show();
    }
    private void autoDialog() {
        if (state.automatic) { mutate(s -> s.automatic = false); return; }
        if (state.lastScan == 0 || !state.scheduled || !LocalScanner.permitted(this)) { toast(getString(R.string.toast_auto_prereq)); return; }
        if (state.bindings.values().stream().anyMatch(Binding::uncertain)) { toast(getString(R.string.toast_auto_uncertain)); return; }
        danger(new AlertDialog.Builder(this).setTitle(R.string.dialog_auto_title)
                .setMessage(R.string.dialog_auto_message)
                .setNegativeButton(R.string.action_not_now, null).setPositiveButton(R.string.action_enable_auto, (d,w) -> mutate(s -> s.automatic = true)).show());
    }
    private void heldFiles() {
        List<Binding> held = new ArrayList<>();
        for (Binding b : state.bindings.values()) if (b.needsReview()) held.add(b);
        if (held.isEmpty()) { toast(getString(R.string.toast_no_held_files)); return; }
        String[] items = held.stream().map(b -> b.name + "\n" + AppText.resolve(this, b.note)).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(R.string.dialog_held_files_title).setItems(items, (d,i) -> {
            Binding b = held.get(i);
            new AlertDialog.Builder(this).setTitle(b.name).setMessage(getString(R.string.dialog_held_item_message, b.cloudPath, AppText.resolve(this, b.note)))
                    .setNegativeButton(R.string.action_back, null).setNeutralButton(R.string.action_open_onedrive, (a,w) -> open("https://onedrive.live.com/"))
                    .setPositiveButton(R.string.action_keep_stop_tracking, (a,w) -> mutate(s -> { Binding current = s.bindings.get(b.cloudId); if (current != null) { current.status = Binding.IGNORED; current.note = "pk:note_ignored_review"; } })).show();
        }).setPositiveButton(R.string.action_close, null).show();
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
        toast(getString(R.string.toast_stop_requested));
    }
    private interface Mutation { void run(State s) throws Exception; }
    private void mutate(Mutation action) {
        if (isDestroyed()) return;
        if (!Store.GATE.tryLock()) { toast(getString(R.string.toast_scan_in_progress)); return; }
        try {
            Store store = new Store(this); State fresh = store.read(); action.run(fresh); store.write(fresh);
            state = fresh; Scheduler.configure(this, fresh);
        } catch (Exception e) { toast(e instanceof AppFailure af ? af.message(this) : getString(R.string.toast_settings_save_failed)); }
        finally { Store.GATE.unlock(); }
        refresh();
    }
    private <T> void async(Callable<T> task, java.util.function.Consumer<T> onSuccess) {
        if (loading || working) { toast(getString(R.string.toast_wait_current_check)); return; }
        loading = true; render();
        io.execute(() -> {
            try { T result = task.call(); runOnUiThread(() -> { if (isDestroyed()) return; loading = false; refresh(); onSuccess.accept(result); }); }
            catch (Exception e) { runOnUiThread(() -> { if (isDestroyed()) return; loading = false; toast(e instanceof AppFailure af ? af.message(this) : getString(R.string.toast_read_incomplete)); refresh(); }); }
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
            new AlertDialog.Builder(this).setTitle(title).setView(view).setPositiveButton(R.string.action_got_it, null).show();
        } catch (Exception e) { toast(getString(R.string.toast_guide_unavailable)); }
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
        web.loadDataWithBaseURL(null, applyDocumentTheme(html), "text/html", "utf-8", null);
        return web;
    }
    private String applyDocumentTheme(String html) {
        String theme = AppPrefs.theme(this);
        if (AppPrefs.THEME_SYSTEM.equals(theme)) return html;
        String override = AppPrefs.THEME_DARK.equals(theme)
                ? "<style>:root{color-scheme:dark;--bg:#242932;--text:#E2E6EC;--muted:#B9C1CD;--accent:#A8C7FA;--rule:#434B57;--node:#242932;}</style>"
                : "<style>:root{color-scheme:light;--bg:#E7EBF1;--text:#171C23;--muted:#454E5B;--accent:#2A5B9C;--rule:#C7CFDA;--node:#E7EBF1;}@media (prefers-color-scheme:dark){:root{color-scheme:light;--bg:#E7EBF1;--text:#171C23;--muted:#454E5B;--accent:#2A5B9C;--rule:#C7CFDA;--node:#E7EBF1;}}</style>";
        return html.replace("</head>", override + "</head>");
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
    private void copy(String value) { ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(getString(R.string.clip_label_registration), value)); toast(getString(R.string.toast_copied)); }
    private void open(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (ActivityNotFoundException e) { toast(getString(R.string.toast_install_browser)); } }
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
        item.setContentDescription(current ? getString(R.string.cd_nav_current, title) : title); item.setSelected(current);
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
        LinearLayout row = new LinearLayout(this); row.setPadding(0,dp(10),0,dp(10));
        TextView label = new TextView(this); label.setText(title); label.setTextColor(muted); label.setTextSize(14); row.addView(label, new LinearLayout.LayoutParams(dp(84),-2));
        TextView detail = new TextView(this); detail.setText(value); detail.setTextColor(valueColor); detail.setTextSize(15); row.addView(detail,new LinearLayout.LayoutParams(0,-2,1)); parent.addView(row);
        if (separated) { View line = new View(this); line.setBackgroundColor(divider); LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1,dp(1)); lineParams.setMargins(dp(84),0,0,0); parent.addView(line,lineParams); }
    }
    private void section(String title) {
        TextView label = new TextView(this); label.setText(title); label.setTextSize(14); label.setTextColor(primary);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); label.setAccessibilityHeading(true);
        label.setPadding(dp(4), 0, dp(4), 0); body.addView(label, new LinearLayout.LayoutParams(-1, -2)); gap(body, 10);
    }
    private String formatScanTime(long timeMs) {
        return new java.text.SimpleDateFormat(getString(R.string.date_format_scan), getResources().getConfiguration().getLocales().get(0)).format(new Date(timeMs));
    }
    private String size(long bytes) {
        if (bytes >= 1024 * 1024) {
            String mb = String.format(getResources().getConfiguration().getLocales().get(0), "%.1f", bytes / (1024.0 * 1024));
            return getString(R.string.size_mb, mb);
        }
        return getString(R.string.size_kb, Math.max(1L, bytes / 1024));
    }
}
