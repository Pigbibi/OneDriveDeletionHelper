package cn.lisiyi.photokeep.data;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.util.Base64;
import com.microsoft.identity.client.*;
import com.microsoft.identity.client.exception.MsalException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class Auth {
    public static final List<String> SCOPES = List.of("https://graph.microsoft.com/Files.ReadWrite");
    private static CompletableFuture<ISingleAccountPublicClientApplication> current;
    private static String currentId = "";
    public static String signature(Context context) throws AppFailure {
        try {
            byte[] cert = context.getPackageManager().getPackageInfo(context.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.getApkContentsSigners()[0].toByteArray();
            return Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest(cert), Base64.NO_WRAP);
        } catch (Exception e) { throw new AppFailure("CONFIG"); }
    }
    public static String redirect(Context context) throws AppFailure {
        return "msauth://" + context.getPackageName() + "/" + Uri.encode(signature(context));
    }
    public static synchronized CompletableFuture<ISingleAccountPublicClientApplication> get(Context context, String clientId) {
        if (current != null && currentId.equals(clientId) && !current.isCompletedExceptionally()) return current;
        CompletableFuture<ISingleAccountPublicClientApplication> result = new CompletableFuture<>();
        current = result;
        currentId = clientId;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (!State.validClientId(clientId)) throw new AppFailure("CONFIG");
                JSONObject config = new JSONObject().put("client_id", clientId).put("redirect_uri", redirect(context))
                        .put("account_mode", "SINGLE").put("authorization_user_agent", "BROWSER")
                        .put("broker_redirect_uri_registered", false)
                        .put("logging", new JSONObject().put("pii_enabled", false).put("logcat_enabled", false))
                        .put("authorities", new JSONArray().put(new JSONObject().put("type", "AAD").put("default", true)
                                .put("audience", new JSONObject().put("type", "AzureADandPersonalMicrosoftAccount"))));
                File file = new File(context.getNoBackupFilesDir(), "msal-config.json");
                Files.write(file.toPath(), config.toString().getBytes(StandardCharsets.UTF_8));
                PublicClientApplication.createSingleAccountPublicClientApplication(context.getApplicationContext(), file,
                        new IPublicClientApplication.ISingleAccountApplicationCreatedListener() {
                            @Override public void onCreated(ISingleAccountPublicClientApplication application) { result.complete(application); }
                            @Override public void onError(MsalException exception) { result.completeExceptionally(new AppFailure("LOGIN")); }
                        });
            } catch (Exception e) { result.completeExceptionally(new AppFailure("CONFIG")); }
        });
        return result;
    }
    public static Session session(Context context, String clientId) throws AppFailure {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new AppFailure("BUSY");
        try {
            ISingleAccountPublicClientApplication app = get(context, clientId).get(60, TimeUnit.SECONDS);
            IAccount account = app.getCurrentAccount().getCurrentAccount();
            if (account == null) throw new AppFailure("LOGIN");
            IAuthenticationResult result = app.acquireTokenSilent(new AcquireTokenSilentParameters.Builder()
                    .withScopes(SCOPES).forAccount(account).fromAuthority(account.getAuthority()).build());
            return new Session(result.getAccount().getId(), result.getAccessToken());
        } catch (Exception e) { throw new AppFailure("LOGIN"); }
    }
    public static final class Session {
        public final String accountId;
        public final String token;
        Session(String accountId, String token) { this.accountId = accountId; this.token = token; }
    }
}
