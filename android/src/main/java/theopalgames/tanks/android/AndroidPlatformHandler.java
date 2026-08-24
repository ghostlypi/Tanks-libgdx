package theopalgames.tanks.android;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.DocumentsContract;

import basewindow.BasePlatformHandler;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;

public class AndroidPlatformHandler extends BasePlatformHandler
{
    private final AndroidLauncher launcher;

    public AndroidPlatformHandler(AndroidLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public void quit()
    {
        System.exit(0);
    }

    @Override
    public void openLink(String url)
    {
        Uri uri = Uri.parse(url);
        launcher.runOnUiThread(new Runnable() {
            @Override
            public void run () {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                // LiveWallpaper and Daydream applications need this flag
                if (!(launcher.getContext() instanceof Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                launcher.startActivity(intent);
            }
        });
    }

    @Override
    public void openFolder(String url) {
        launcher.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (AndroidSAFFile.root != null) {
                    launcher.openSAFDirectory();
                } else {
                    launcher.pendingOpenFolder = true;
                    launcher.startSAF();
                }
            }
        });
    }
}
