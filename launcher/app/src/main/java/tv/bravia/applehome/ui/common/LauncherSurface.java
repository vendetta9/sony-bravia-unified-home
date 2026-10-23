package tv.bravia.applehome.ui.common;

import android.content.Context;
import android.view.*;
import tv.bravia.applehome.device.DeviceStatusController;
import tv.bravia.applehome.search.*;

/** Small lifecycle/search contract implemented independently by both presentations. */
public abstract class LauncherSurface extends View
    implements MovieSearchHost, DeviceStatusController.Listener {
  private boolean foreground;

  protected LauncherSurface(Context c) {
    super(c);
  }

  public final boolean isForeground() {
    return foreground;
  }

  public final void onForeground() {
    foreground = true;
    resumeFeatures();
  }

  public final void onBackground() {
    foreground = false;
    suspendFeatures();
  }

  protected abstract void resumeFeatures();

  protected abstract void suspendFeatures();

  public abstract MovieSearchController movieSearch();

  public abstract void refreshApps();

  public abstract void refreshWifi();

  public abstract void returnToHome();

  public abstract boolean handleVolumeKeyDown(KeyEvent e);

  public abstract boolean handleVolumeKeyUp();

  public abstract void stopVolumeRepeating();

  public abstract void dispose();
}
