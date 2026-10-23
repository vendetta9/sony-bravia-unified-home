package tv.bravia.applehome.input;

import android.content.Context;
import android.os.*;
import android.view.KeyEvent;
import tv.bravia.applehome.core.HomeNavigator;

/** One shared 3-second press state machine for accessibility and Sony global-button delivery. */
public final class HomeGestureController {
  static final long HOLD_MS = 3000;
  static final Handler handler = new Handler(Looper.getMainLooper());
  static boolean held, shown;
  static long downTime, lastFinished = -1;
  static Context owner;
  static final Runnable hold =
      () -> {
        if (held && !shown) {
          shown = true;
          new HomeNavigator(owner).openChooser();
        }
      };

  public static void onKey(Context c, KeyEvent event) {
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      if (event.getDownTime() == lastFinished) return;
      if (!held || event.getDownTime() != downTime) {
        handler.removeCallbacks(hold);
        held = true;
        shown = false;
        downTime = event.getDownTime();
        owner = c.getApplicationContext();
        handler.postDelayed(hold, Math.max(0, HOLD_MS - (SystemClock.uptimeMillis() - downTime)));
      }
    } else if (event.getAction() == KeyEvent.ACTION_UP) {
      if (event.getDownTime() == lastFinished) return;
      handler.removeCallbacks(hold);
      boolean alreadyShown = held && shown && downTime == event.getDownTime();
      boolean longPress = alreadyShown || event.getEventTime() - event.getDownTime() >= HOLD_MS;
      held = false;
      shown = false;
      lastFinished = event.getDownTime();
      owner = null;
      if (event.isCanceled()) return;
      if (longPress) {
        if (!alreadyShown) new HomeNavigator(c).openChooser();
      } else new HomeNavigator(c).openSelected();
    }
  }

  public static void cancel() {
    handler.removeCallbacks(hold);
    held = false;
    shown = false;
    owner = null;
  }
}
