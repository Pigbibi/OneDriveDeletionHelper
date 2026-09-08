package cn.lisiyi.photokeep;

import android.Manifest;
import android.content.Context;
import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import org.robolectric.shadows.ShadowAlertDialog;
import androidx.work.Configuration;
import androidx.work.WorkManager;
import cn.lisiyi.photokeep.core.Binding;
import cn.lisiyi.photokeep.data.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {30, 35})
public class AndroidIntegrationTest {
    private Context context;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        try { WorkManager.initialize(context, new Configuration.Builder().setMinimumLoggingLevel(android.util.Log.ERROR).build()); }
        catch (IllegalStateException ignored) { }
    }
    @Test public void freshInstallDefaultsToNoAutomaticDeletion() throws Exception {
        State s = new Store(context).read(); assertFalse(s.automatic); assertFalse(s.scheduled); assertEquals(0,s.lastScan);
    }
    @Test public void stateRoundTripPreservesUncertainWriteAndAccountBinding() throws Exception {
        State s = new State(); s.accountId="test-account"; s.driveId="test-drive"; s.folders.add("Pictures/"); s.cloudRoots.put("folder-id","Pictures");
        Binding b = new Binding("cloud", "phone", "a".repeat(64), 100,"image.jpg","Pictures/","2026/09/image.jpg"); b.status="UNCERTAIN"; s.bindings.put(b.cloudId,b);
        Store store = new Store(context); store.write(s); State restored = store.read();
        assertEquals("test-account",restored.accountId); assertEquals("test-drive",restored.driveId); assertEquals("UNCERTAIN",restored.bindings.get("cloud").status);
    }
    @Test public void corruptStateFailsClosedInsteadOfStartingFresh() throws Exception {
        Files.writeString(new File(context.getNoBackupFilesDir(),"state.json").toPath(),"{broken");
        assertThrows(AppFailure.class, () -> new Store(context).read());
    }
    @Test public void partialPhotoAccessNeverCountsAsCompleteLibrary() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.ACCESS_MEDIA_LOCATION);
        assertFalse(LocalScanner.permitted(context));
        if (android.os.Build.VERSION.SDK_INT >= 33) Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO);
        else Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
        assertTrue(LocalScanner.permitted(context));
        if (android.os.Build.VERSION.SDK_INT >= 33) Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_MEDIA_VIDEO);
        else Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
        assertFalse(LocalScanner.permitted(context));
    }
    @Test public void resetScopeTurnsOffAutomaticDeletion() {
        State s = new State(); s.automatic=true; s.lastScan=50; s.resetMapping(); assertFalse(s.automatic); assertEquals(0,s.lastScan);
    }
    @Test public void navigationRendersRealEmptyStatesAndSettings() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get(); View root=activity.getWindow().getDecorView();
            assertNotNull(find(root,"登录 OneDrive")); find(root,"预览").performClick();
            root=activity.getWindow().getDecorView(); assertNotNull(find(root,"先认识你的照片")); find(root,"设置").performClick();
            root=activity.getWindow().getDecorView(); assertNotNull(find(root,"连接与设置")); assertNotNull(find(root,"高级连接设置"));
            assertNull(find(root,"首次连接设置")); assertNull(find(root,"自定义 Client ID"));
        }
    }
    @Test public void legacyClientAndMappingSurviveUpgrade() throws Exception {
        State s = new State(); s.clientId = "11111111-1111-4111-8111-111111111111"; s.accountId = "old-account";
        s.automatic = true; s.scheduled = true;
        s.bindings.put("cloud", new Binding("cloud", "local", "a".repeat(64), 100, "test.jpg", "Pictures/", "Photos/test.jpg"));
        Store store = new Store(context); store.write(s); State restored = store.read();
        assertEquals(s.clientId, restored.clientId); assertEquals(s.accountId, restored.accountId);
        assertTrue(restored.automatic); assertTrue(restored.scheduled); assertEquals(1, restored.bindings.size());
    }
    @Test public void unconfiguredLegacyInstallUsesBundledRegistration() throws Exception {
        State s = new State(); s.clientId = "";
        Store store = new Store(context); store.write(s);
        assertEquals(BuildConfig.MICROSOFT_CLIENT_ID, store.read().clientId);
    }
    @Test public void unavailableLoginDoesNotAskOrdinaryUsersToRegisterApps() {
        Assume.assumeTrue(BuildConfig.MICROSOFT_CLIENT_ID.isEmpty());
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            find(controller.get().getWindow().getDecorView(), "登录 OneDrive").performClick();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog); assertTrue(dialog.isShowing());
            assertNotNull(find(dialog.getWindow().getDecorView(), "此安装包暂未开放登录"));
            assertNull(findEdit(dialog.getWindow().getDecorView()));
        }
    }
    @Test public void customClientIsAdvancedAndInvalidInputKeepsDialogOpen() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get(); find(activity.getWindow().getDecorView(), "设置").performClick();
            find(activity.getWindow().getDecorView(), "高级连接设置").performClick();
            AlertDialog advanced = ShadowAlertDialog.getLatestAlertDialog();
            find(advanced.getWindow().getDecorView(), "自定义 Client ID").performClick();
            AlertDialog custom = ShadowAlertDialog.getLatestAlertDialog();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            EditText input = findEdit(custom.getWindow().getDecorView()); assertNotNull(input);
            input.setText("invalid"); custom.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(custom.isShowing()); assertNotNull(input.getError());
        }
    }
    @Test public void downloadHostPolicyDoesNotAcceptLookalikeDomains() {
        assertTrue(GraphApi.downloadHostAllowed("public.bn.files.1drv.com"));
        assertTrue(GraphApi.downloadHostAllowed("example.sharepoint.com"));
        assertFalse(GraphApi.downloadHostAllowed("1drv.com.attacker.example"));
        assertFalse(GraphApi.downloadHostAllowed("127.0.0.1"));
    }
    private static View find(View view,String text) {
        if(view instanceof TextView t && t.getText().toString().equals(text))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private static EditText findEdit(View view) {
        if (view instanceof EditText edit) return edit;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = findEdit(group.getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
}
