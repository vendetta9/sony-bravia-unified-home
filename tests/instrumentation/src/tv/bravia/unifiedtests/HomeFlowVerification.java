package tv.bravia.unifiedtests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.util.*;
import tv.bravia.applehome.core.*;
import tv.bravia.applehome.input.HomeGestureController;

/** On-device mode/gesture integration checks. Restores the original saved Home choice. */
public final class HomeFlowVerification extends Instrumentation {
  private volatile String resumed = "";
  private volatile int chooserCount;
  private volatile Activity foreground;
  private int passed;
  private Application application;
  private final Application.ActivityLifecycleCallbacks lifecycle =
      new Application.ActivityLifecycleCallbacks() {
        public void onActivityResumed(Activity a) {
          resumed = a.getClass().getSimpleName();
          foreground = a;
          if (resumed.equals("HomeChooserActivity")) chooserCount++;
        }

        public void onActivityCreated(Activity a, Bundle b) {}

        public void onActivityStarted(Activity a) {}

        public void onActivityPaused(Activity a) {}

        public void onActivityStopped(Activity a) {}

        public void onActivitySaveInstanceState(Activity a, Bundle b) {}

        public void onActivityDestroyed(Activity a) {}
      };

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  private void check(boolean okay, String name) {
    if (!okay) throw new AssertionError(name + "; resumed=" + resumed);
    passed++;
    Bundle b = new Bundle();
    b.putString("check", name);
    b.putString("result", "PASS");
    sendStatus(0, b);
  }

  private void await(String activity) throws Exception {
    long end = SystemClock.uptimeMillis() + 8000;
    while (SystemClock.uptimeMillis() < end && !activity.equals(resumed)) Thread.sleep(100);
    check(activity.equals(resumed), "Foreground: " + activity);
  }

  private void choose(HomeMode mode) {
    runOnMainSync(
        () -> {
          if (!new HomeNavigator(getTargetContext()).choose(mode))
            throw new AssertionError("Cannot choose " + mode);
        });
  }

  private void home(long duration) throws Exception {
    long down = SystemClock.uptimeMillis();
    runOnMainSync(
        () ->
            HomeGestureController.onKey(
                getTargetContext(),
                new KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, 0)));
    Thread.sleep(duration);
    runOnMainSync(
        () ->
            HomeGestureController.onKey(
                getTargetContext(),
                new KeyEvent(
                    down,
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_HOME,
                    0)));
  }

  private boolean containsLabel(View view, String label) {
    if (view instanceof TextView && ((TextView) view).getText().toString().startsWith(label))
      return true;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (containsLabel(group.getChildAt(i), label)) return true;
    }
    return false;
  }

  private void verifyRenamingMigration() {
    Context c = getTargetContext();
    check(
        HomeMode.MINIMALIST.label.equals("Minimalist UI")
            && HomeMode.GLASS.label.equals("Glass UI")
            && HomeMode.ANDROID.label.equals("Android UI"),
        "Exact UI labels");
    check(HomeMode.fromId("debloat") == HomeMode.MINIMALIST, "Legacy minimalist ID resolves");
    check(HomeMode.fromId("stock") == HomeMode.ANDROID, "Legacy Android ID resolves");
    check(HomeMode.fromId("unknown") == HomeMode.GLASS, "Unknown mode falls back to Glass UI");
    SharedPreferences selection = c.getSharedPreferences("home_selection", Context.MODE_PRIVATE);
    selection.edit().putString("mode", "debloat").commit();
    check(
        new HomeSelectionRepository(c).selected() == HomeMode.MINIMALIST
            && selection.getString("mode", "").equals("minimalist"),
        "Saved minimalist ID canonicalized");
    selection.edit().putString("mode", "stock").commit();
    check(
        new HomeSelectionRepository(c).selected() == HomeMode.ANDROID
            && selection.getString("mode", "").equals("android"),
        "Saved Android ID canonicalized");
    SharedPreferences old = c.getSharedPreferences("debloat_ui", Context.MODE_PRIVATE);
    SharedPreferences target = c.getSharedPreferences("minimalist_ui", Context.MODE_PRIVATE);
    SharedPreferences markers = c.getSharedPreferences("unified_migration", Context.MODE_PRIVATE);
    boolean hadMarker = markers.contains("v2_minimalist");
    boolean previousMarker = markers.getBoolean("v2_minimalist", false);
    String key = "verification_" + SystemClock.uptimeMillis();
    try {
      old.edit()
          .putString(key, "saved-order")
          .putInt(key + "_count", 6)
          .putStringSet(key + "_set", new HashSet<>(Arrays.asList("a", "b")))
          .putString(key + "_existing", "old")
          .commit();
      target.edit().putString(key + "_existing", "keep-new").commit();
      markers.edit().putBoolean("v2_minimalist", false).commit();
      check(markers.getBoolean("v1", false), "Prior migration already completed");
      LegacyPreferencesMigrator.migrate(c);
      check(
          target.getString(key, "").equals("saved-order") && target.getInt(key + "_count", 0) == 6,
          "Minimalist preferences migrate after v1");
      check(
          target.getStringSet(key + "_set", Collections.emptySet()).size() == 2,
          "Preference sets retained");
      check(
          target.getString(key + "_existing", "").equals("keep-new"),
          "Existing minimalist preferences preserved");
      check(markers.getBoolean("v2_minimalist", false), "Rename migration recorded");
    } finally {
      for (SharedPreferences prefs : new SharedPreferences[] {old, target})
        prefs
            .edit()
            .remove(key)
            .remove(key + "_count")
            .remove(key + "_set")
            .remove(key + "_existing")
            .commit();
      SharedPreferences.Editor marker = markers.edit();
      if (hadMarker) marker.putBoolean("v2_minimalist", previousMarker);
      else marker.remove("v2_minimalist");
      marker.commit();
    }
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    HomeMode original = new HomeSelectionRepository(getTargetContext()).selected();
    result.putString("original_mode", original.id);
    try {
      application = (Application) getTargetContext().getApplicationContext();
      runOnMainSync(() -> application.registerActivityLifecycleCallbacks(lifecycle));
      check(HomeMode.values().length == 3, "Exactly three home modes");
      verifyRenamingMigration();
      choose(HomeMode.MINIMALIST);
      await("MinimalistHomeActivity");
      check(
          new HomeSelectionRepository(getTargetContext()).selected() == HomeMode.MINIMALIST,
          "Minimalist saved choice");
      // Timer must remain quiet before three seconds and open once after it.
      int before = chooserCount;
      long down = SystemClock.uptimeMillis();
      runOnMainSync(
          () ->
              HomeGestureController.onKey(
                  getTargetContext(), new KeyEvent(down, down, KeyEvent.ACTION_DOWN, 3, 0)));
      Thread.sleep(2700);
      check(chooserCount == before, "No chooser before 3 seconds");
      Thread.sleep(650);
      await("HomeChooserActivity");
      check(chooserCount == before + 1, "Chooser opens once after 3 seconds");
      runOnMainSync(
          () -> {
            View view = foreground.getWindow().getDecorView();
            check(
                containsLabel(view, "Minimalist UI")
                    && containsLabel(view, "Glass UI")
                    && containsLabel(view, "Android UI"),
                "Chooser displays renamed UI labels");
          });
      runOnMainSync(
          () ->
              HomeGestureController.onKey(
                  getTargetContext(),
                  new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, 3, 0)));
      Thread.sleep(250);
      check(
          resumed.equals("HomeChooserActivity") && chooserCount == before + 1,
          "Long-press release keeps chooser open");
      sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
      sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
      await("GlassHomeActivity");
      check(
          new HomeSelectionRepository(getTargetContext()).selected() == HomeMode.GLASS,
          "Glass saved choice");
      home(3100);
      await("HomeChooserActivity");
      sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
      sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
      sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
      Thread.sleep(800);
      check(
          new HomeSelectionRepository(getTargetContext()).selected() == HomeMode.ANDROID,
          "Android UI saved choice");
      // Verify the chooser is globally launchable even while Android UI is the selected foreground
      // home.
      home(3100);
      await("HomeChooserActivity");
      sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
      sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
      await("MinimalistHomeActivity");
      // A direct developer launch must not alter the saved Home destination.
      runOnMainSync(
          () ->
              getTargetContext()
                  .startActivity(
                      new Intent()
                          .setClassName(
                              "tv.bravia.applehome",
                              "tv.bravia.applehome.ui.glass.GlassHomeActivity")
                          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
      await("GlassHomeActivity");
      check(
          new HomeSelectionRepository(getTargetContext()).selected() == HomeMode.MINIMALIST,
          "Direct launch preserves saved Home choice");
      home(100);
      await("MinimalistHomeActivity");
      check(
          new HomeSelectionRepository(getTargetContext()).selected() == HomeMode.MINIMALIST,
          "Short Home follows saved Minimalist choice");
      int count = chooserCount;
      home(500);
      Thread.sleep(250);
      check(chooserCount == count, "Sub-3-second Home does not show chooser");
      result.putString("result", "PASS");
      result.putInt("checks", passed);
    } catch (Throwable failure) {
      result.putString("result", "FAIL");
      result.putString("failure", failure.toString());
    } finally {
      try {
        runOnMainSync(
            () -> {
              HomeGestureController.cancel();
              if (application != null) application.unregisterActivityLifecycleCallbacks(lifecycle);
              new HomeNavigator(getTargetContext()).choose(original);
              // Instrumentation completion terminates the target process immediately.
              // Commit restoration synchronously instead of relying on a queued apply.
              getTargetContext()
                  .getSharedPreferences("home_selection", Context.MODE_PRIVATE)
                  .edit()
                  .putString("mode", original.id)
                  .commit();
            });
      } catch (Throwable ignored) {
      }
      finish(
          result.getString("result").equals("PASS") ? Activity.RESULT_OK : Activity.RESULT_CANCELED,
          result);
    }
  }
}
