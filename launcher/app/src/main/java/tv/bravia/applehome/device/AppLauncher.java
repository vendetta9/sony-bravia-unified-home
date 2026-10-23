package tv.bravia.applehome.device;

import android.content.*;
import android.content.pm.PackageManager;
import tv.bravia.applehome.core.model.AppTile;

/** Sony Live TV and installed-app launch targets, independent of drawing/navigation. */
public final class AppLauncher {
  private final Context context;
  private final PackageManager pm;

  public AppLauncher(Context c) {
    context = c;
    pm = c.getPackageManager();
  }

  public void launch(AppTile tile) {
    try {
      String key = tile.key;
      if ("LIVE_TV".equals(key)) {
        // Exact Sony VH22 Live TV package. Resolve its own leanback entry point first;
        // this avoids routing the tile through Settings or a generic input picker.
        Intent sony = pm.getLeanbackLaunchIntentForPackage("com.sony.dtv.tvx");
        if (sony == null) {
          sony = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
          sony.setComponent(new ComponentName("com.sony.dtv.tvx", "com.sony.dtv.tvx.MainActivity"));
        }
        context.startActivity(sony);
        return;
      }
      if ("com.apple.atve.sony.appletv".equals(key)) {
        Intent a = new Intent();
        a.setComponent(
            new ComponentName(
                "com.apple.atve.sony.appletv", "com.apple.atve.androidtv.appletv.MainActivity"));
        context.startActivity(a);
        return;
      }
      if (tile.component != null) {
        Intent leanback =
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        leanback.setComponent(new ComponentName(key, tile.component));
        context.startActivity(leanback);
        return;
      }
      Intent i = pm.getLaunchIntentForPackage(key);
      if (i != null) context.startActivity(i);
    } catch (Exception ignored) {
    }
  }
}
