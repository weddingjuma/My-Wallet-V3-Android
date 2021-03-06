package piuk.blockchain.android.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.support.v7.app.AlertDialog;
import android.view.MotionEvent;

import info.blockchain.wallet.payload.PayloadManager;

import java.io.File;
import java.security.Security;

import javax.inject.Inject;

import dagger.Lazy;
import piuk.blockchain.android.R;
import piuk.blockchain.android.data.access.AccessState;
import piuk.blockchain.android.injection.Injector;
import piuk.blockchain.android.ui.customviews.ToastCustom;
import piuk.blockchain.android.ui.launcher.LauncherActivity;

import static piuk.blockchain.android.util.PersistentPrefs.KEY_OVERLAY_TRUSTED;

@SuppressWarnings("WeakerAccess")
public class AppUtil {

    private static final String REGEX_UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @Inject PrefsUtil prefs;
    @Inject Lazy<PayloadManager> payloadManager;
    private Context context;
    private AlertDialog alertDialog;

    public AppUtil(Context context) {
        Injector.getInstance().getAppComponent().inject(this);
        this.context = context;
    }

    public void clearCredentials() {
        payloadManager.get().wipe();
        prefs.clear();
    }

    public void clearCredentialsAndRestart() {
        clearCredentials();
        restartApp();
    }

    public void restartApp() {
        Intent intent = new Intent(context, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public void restartAppWithVerifiedPin() {
        Intent intent = new Intent(context, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("verified", true);
        context.startActivity(intent);
        AccessState.getInstance().logIn();
    }

    public String getReceiveQRFilename() {
        // getExternalCacheDir can return null if permission for write storage not granted
        // or if running on an emulator
        return context.getExternalCacheDir() + File.separator + "qr.png";
    }

    public void deleteQR() {
        // getExternalCacheDir can return null if permission for write storage not granted
        // or if running on an emulator
        File file = new File(context.getExternalCacheDir() + File.separator + "qr.png");
        if (file.exists()) {
            file.delete();
        }
    }

    public boolean isNewlyCreated() {
        return prefs.getValue(PrefsUtil.KEY_NEWLY_CREATED_WALLET, false);
    }

    public void setNewlyCreated(boolean newlyCreated) {
        prefs.setValue(PrefsUtil.KEY_NEWLY_CREATED_WALLET, newlyCreated);
    }

    public boolean isSane() {
        String guid = prefs.getValue(PrefsUtil.KEY_GUID, "");

        if (!guid.matches(REGEX_UUID)) {
            return false;
        }

        String encryptedPassword = prefs.getValue(PrefsUtil.KEY_ENCRYPTED_PASSWORD, "");
        String pinID = prefs.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "");

        return !(encryptedPassword.isEmpty() || pinID.isEmpty());
    }

    public boolean isCameraOpen() {
        Camera camera = null;

        try {
            camera = Camera.open();
        } catch (RuntimeException e) {
            return true;
        } finally {
            if (camera != null) {
                camera.release();
            }
        }

        return false;
    }

    public String getSharedKey() {
        return prefs.getValue(PrefsUtil.KEY_SHARED_KEY, "");
    }

    public void setSharedKey(String sharedKey) {
        prefs.setValue(PrefsUtil.KEY_SHARED_KEY, sharedKey);
    }

    public void applyPRNGFixes() {
        try {
            PRNGFixes.apply();
        } catch (Exception e0) {
            //
            // some Android 4.0 devices throw an exception when PRNGFixes is re-applied
            // removing provider before apply() is a workaround
            //
            Security.removeProvider("LinuxPRNG");
            try {
                PRNGFixes.apply();
            } catch (Exception e1) {
                ToastCustom.makeText(context, context.getString(R.string.cannot_launch_app), ToastCustom.LENGTH_LONG, ToastCustom.TYPE_ERROR);
                AccessState.getInstance().logout(context);
            }
        }
    }

    public boolean detectObscuredWindow(Context context, MotionEvent event) {
        //Detect if touch events are being obscured by hidden overlays - These could be used for tapjacking
        if ((!prefs.getValue(KEY_OVERLAY_TRUSTED, false))
                && (event.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0) {

            //Prevent multiple popups
            if (alertDialog != null) {
                alertDialog.dismiss();
            }

            alertDialog = new AlertDialog.Builder(context, R.style.AlertDialogStyle)
                    .setTitle(R.string.screen_overlay_warning)
                    .setMessage(R.string.screen_overlay_note)
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_continue, (dialog, whichButton) ->
                            prefs.setValue(KEY_OVERLAY_TRUSTED, true))
                    .setNegativeButton(R.string.exit, (dialog, whichButton) ->
                            ((Activity) context).finish())
                    .show();
            return true;
        } else {
            return false;
        }
    }

    public String getPackageName() {
        return context.getPackageName();
    }

    public PackageManager getPackageManager() {
        return context.getPackageManager();
    }
}
