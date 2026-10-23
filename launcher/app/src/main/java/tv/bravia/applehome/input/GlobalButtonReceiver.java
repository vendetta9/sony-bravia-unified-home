package tv.bravia.applehome.input;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;

/** Receives Sony's GLOBAL_BUTTON broadcast for remote keys handled before app dispatch. */
public class GlobalButtonReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
    if (event == null) return;
    int code = event.getKeyCode();
    boolean home = code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_ALL_APPS;
    boolean settings =
        code == KeyEvent.KEYCODE_SETTINGS
            || code == KeyEvent.KEYCODE_MENU
            || code == KeyEvent.KEYCODE_NOTIFICATION;
    if (!home && !settings) return;
    if (home) {
      HomeGestureController.onKey(context, event);
      return;
    }
    if (event.getAction() != KeyEvent.ACTION_UP || event.isCanceled()) return;
    Intent target = new Intent(Settings.ACTION_SETTINGS);
    target.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    context.startActivity(target);
  }
}
