package tv.bravia.applehome.core;

import android.content.*;
import android.widget.Toast;
import tv.bravia.applehome.device.AndroidHomeResolver;
import tv.bravia.applehome.ui.chooser.HomeChooserActivity;
import tv.bravia.applehome.ui.glass.GlassHomeActivity;
import tv.bravia.applehome.ui.minimalist.MinimalistHomeActivity;

/**
 * Resolves and opens destinations; gesture timing and drawing are not navigation responsibilities.
 */
public final class HomeNavigator {
  private final Context context;
  private final HomeSelectionRepository selection;
  private final AndroidHomeResolver androidHome = new AndroidHomeResolver();

  public HomeNavigator(Context c) {
    context = c;
    selection = new HomeSelectionRepository(c);
  }

  public HomeMode selected() {
    return selection.selected();
  }

  private Intent destination(HomeMode mode) {
    if (mode == HomeMode.ANDROID) return androidHome.resolve(context);
    return new Intent(
        context,
        mode == HomeMode.MINIMALIST ? MinimalistHomeActivity.class : GlassHomeActivity.class);
  }

  private void start(Intent intent) {
    context.startActivity(
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP));
  }

  public boolean choose(HomeMode mode) {
    Intent intent = destination(mode);
    if (intent == null) {
      Toast.makeText(
              context, "Enable Google TV Home in Settings to use Android UI", Toast.LENGTH_LONG)
          .show();
      return false;
    }
    HomeMode previous = selected();
    selection.save(mode);
    // Retire the previous custom surface when changing themes, but keep normal
    // Home presses cheap. Layout/order state survives in per-theme preferences.
    if (mode != previous && mode != HomeMode.ANDROID) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
    try {
      start(intent);
      return true;
    } catch (Exception e) {
      selection.save(previous);
      Toast.makeText(context, "Unable to open " + mode.label, Toast.LENGTH_LONG).show();
      return false;
    }
  }

  public void openSelected() {
    if (!choose(selected())) choose(HomeMode.GLASS);
  }

  public void openChooser() {
    start(new Intent(context, HomeChooserActivity.class));
  }
}
