package tv.bravia.applehome.input;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Routes the physical Home and Settings keys to the intended TV destinations. */
public class HomeKeyService extends AccessibilityService {
  @Override
  protected boolean onKeyEvent(KeyEvent event) {
    int code = event.getKeyCode();
    boolean home = code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_ALL_APPS;
    boolean settings =
        code == KeyEvent.KEYCODE_SETTINGS
            || code == KeyEvent.KEYCODE_MENU
            || code == KeyEvent.KEYCODE_NOTIFICATION;
    if (!home && !settings) return false;
    if (home) {
      HomeGestureController.onKey(this, event);
      return true;
    }
    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
      Intent target = new Intent(Settings.ACTION_SETTINGS);
      target.addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK
              | Intent.FLAG_ACTIVITY_CLEAR_TOP
              | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      startActivity(target);
    }
    return true;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {}

  @Override
  public void onInterrupt() {
    HomeGestureController.cancel();
  }

  @Override
  public void onDestroy() {
    HomeGestureController.cancel();
    super.onDestroy();
  }
}
