package theopalgames.tanks.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.documentfile.provider.DocumentFile;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidAudio;
import com.badlogic.gdx.backends.android.DefaultAndroidFiles;
import com.badlogic.gdx.files.FileHandle;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.io.OutputStream;

import libgdxwindow.LibGDXAsyncMiniAudioSoundPlayer;
import libgdxwindow.LibGDXFileManager;
import tanks.Game;
import theopalgames.tanks.*;
import theopalgames.tanks.R;

public class AndroidLauncher extends AndroidApplication {
    public static final int SAF_REQUEST_CODE = 42;
    public boolean pendingOpenFolder = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Gdx.files = new DefaultAndroidFiles(this.getAssets(), this, true);
        Tanks.appType = ApplicationType.Android;
        AndroidSAFFile.application = this;

        SharedPreferences prefs = getSharedPreferences("tanks", MODE_PRIVATE);
        String safUri = prefs.getString("saf_uri", null);
        if (safUri != null) {
            AndroidSAFFile.root = DocumentFile.fromTreeUri(this, Uri.parse(safUri));
            LibGDXFileManager.fileCreator = AndroidSAFFile::new;
        }

        Tanks.initialize();

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.depth = 24;
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;
        config.maxSimultaneousSounds = 64;
        if (Game.antialiasing) {
            Tanks.window.antialiasingEnabled = true;
            config.numSamples = 4;
        }

        Tanks.keyboardHeightListener = new AndroidKeyboardHeightListener(this);
        Tanks.vibrationPlayer = new AndroidVibrationPlayer();

        DisplayMetrics displayMetrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        if (Build.VERSION.SDK_INT >= 30)
            this.getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

        Tanks.pointWidth = displayMetrics.widthPixels / displayMetrics.density;
        Tanks.pointHeight = displayMetrics.heightPixels / displayMetrics.density;

        Tanks.platformHandler = new AndroidPlatformHandler(this);

        initialize(new Tanks(), config);
        file_migration();
    }

    @Override
    public AndroidAudio createAudio(Context context, AndroidApplicationConfiguration config) {
        LibGDXAsyncMiniAudioSoundPlayer.miniAudio.setupAndroid(context.getAssets());
        return super.createAudio(context, config);
    }

    public void file_migration() {
        if (AndroidSAFFile.root != null) return;

        FileHandle externalDir = Gdx.files.external("/.tanks");
        if (externalDir.exists()) {
            Log.i("Tanks", "Migrating from external storage to internal storage...");
            migrateDir(externalDir, Gdx.files.local(""));
            externalDir.deleteDirectory();
        }
    }

    private void migrateDir(FileHandle src, FileHandle dest) {
        for (FileHandle file : src.list()) {
            FileHandle d = dest.child(file.name());
            if (file.isDirectory()) {
                d.mkdirs();
                migrateDir(file, d);
                file.deleteDirectory();
            } else {
                d.writeBytes(file.readBytes(), false);
                file.delete();
            }
        }
    }

    public void startSAF() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.saf_folder_picker_title)
                .setMessage(R.string.saf_folder_picker_message)
                .setPositiveButton(R.string.saf_folder_picker_button_proceed, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(intent, SAF_REQUEST_CODE);
                })
                .setNegativeButton(R.string.saf_folder_picker_button_back, (dialog, which) -> {
                    pendingOpenFolder = false;
                })
                .setCancelable(false)
                .show();
    }

    public void openSAFDirectory() {
        if (AndroidSAFFile.root != null) {
            Uri uri = AndroidSAFFile.root.getUri();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SAF_REQUEST_CODE && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                getSharedPreferences("tanks", MODE_PRIVATE).edit().putString("saf_uri", uri.toString()).apply();

                AndroidSAFFile.root = DocumentFile.fromTreeUri(this, uri);
                LibGDXFileManager.fileCreator = AndroidSAFFile::new;

                migrateLocalToSAF(AndroidSAFFile.root);

                if (pendingOpenFolder) {
                    pendingOpenFolder = false;
                    openSAFDirectory();
                }
            }
        }
    }

    private void migrateLocalToSAF(DocumentFile safRoot) {
        FileHandle localDir = Gdx.files.local("");
        Log.i("Tanks", "Migrating from internal storage to SAF...");
        migrateLocalToSAFDir(localDir, safRoot);
    }

    private void migrateLocalToSAFDir(FileHandle src, DocumentFile destDir) {
        for (FileHandle file : src.list()) {
            if (file.name().equals("lib") || file.name().equals("tanks")) continue; // Skip system/app dirs if they exist

            Log.i("Tanks", "Migrating: " + file.path());
            if (file.isDirectory()) {
                DocumentFile d = destDir.findFile(file.name());
                if (d == null)
                    d = destDir.createDirectory(file.name());

                if (d != null)
                    migrateLocalToSAFDir(file, d);

                file.deleteDirectory();
            } else {
                DocumentFile d = destDir.findFile(file.name());
                if (d == null)
                    d = destDir.createFile("application/octet-stream", file.name());

                if (d != null) {
                    try {
                        OutputStream out = getContentResolver().openOutputStream(d.getUri());
                        out.write(file.readBytes());
                        out.close();
                    } catch (IOException e) {
                        Log.e("Tanks", "Failed to migrate file: " + file.path(), e);
                    }
                }
                file.delete();
            }
        }
    }
}
