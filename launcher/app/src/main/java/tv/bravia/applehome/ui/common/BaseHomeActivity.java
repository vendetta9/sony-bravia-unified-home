package tv.bravia.applehome.ui.common;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.*;
import android.widget.FrameLayout;
import tv.bravia.applehome.device.HomePlaybackController;

/** Shared Android lifecycle, package-change refresh, immersive mode and playback integration. */
public abstract class BaseHomeActivity extends Activity {
  private LauncherSurface surface;
  private HomePlaybackController playback;
  private boolean receiverRegistered;
  private final BroadcastReceiver packages =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
          if (Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction())
              && i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
          if (surface != null) surface.refreshApps();
        }
      };

  protected abstract LauncherSurface createSurface();

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    tv.bravia.applehome.core.LegacyPreferencesMigrator.migrate(this);
    playback = new HomePlaybackController(this);
    getWindow().setFlags(1024, 1024);
    hideBars();
    surface = createSurface();
    FrameLayout root = new FrameLayout(this);
    root.addView(surface);
    surface.movieSearch().attach(root);
    setContentView(root);
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_PACKAGE_ADDED);
    filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
    filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
    filter.addDataScheme("package");
    registerReceiver(packages, filter);
    receiverRegistered = true;
    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 1);
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    hideBars();
    if (surface.movieSearch().isSearchOpen()) surface.movieSearch().hideSearch(true);
    surface.returnToHome();
    surface.refreshApps();
  }

  @Override
  protected void onResume() {
    super.onResume();
    playback.enterHome();
    surface.refreshApps();
    surface.movieSearch().refreshSavedFeature();
    surface.onForeground();
  }

  @Override
  protected void onPause() {
    surface.onBackground();
    playback.leaveHome();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (surface != null) surface.dispose();
    if (playback != null) playback.leaveHome();
    if (receiverRegistered) unregisterReceiver(packages);
    super.onDestroy();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent e) {
    int key = e.getKeyCode();
    if (surface != null
        && (key == KeyEvent.KEYCODE_VOLUME_UP || key == KeyEvent.KEYCODE_VOLUME_DOWN)) {
      if (e.getAction() == KeyEvent.ACTION_DOWN) return surface.handleVolumeKeyDown(e);
      if (e.getAction() == KeyEvent.ACTION_UP) return surface.handleVolumeKeyUp();
    }
    return super.dispatchKeyEvent(e);
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] names, int[] results) {
    super.onRequestPermissionsResult(code, names, results);
    if (code == 1 && surface != null) surface.refreshWifi();
  }

  @Override
  public void onBackPressed() {
    if (surface != null && surface.movieSearch().isSearchOpen())
      surface.movieSearch().hideSearch(false);
    else super.onBackPressed();
  }

  private void hideBars() {
    getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }

  @Override
  public void onWindowFocusChanged(boolean focus) {
    super.onWindowFocusChanged(focus);
    if (focus) hideBars();
  }
}
