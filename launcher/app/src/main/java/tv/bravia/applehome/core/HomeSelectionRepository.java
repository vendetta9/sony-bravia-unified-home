package tv.bravia.applehome.core;

import android.content.*;

/** Owns the one saved Home choice. Theme UI preferences and movie search are separate stores. */
public final class HomeSelectionRepository {
  private final SharedPreferences preferences;

  public HomeSelectionRepository(Context c) {
    LegacyPreferencesMigrator.migrate(c);
    preferences = c.getSharedPreferences("home_selection", Context.MODE_PRIVATE);
    if (!preferences.contains("mode")) {
      boolean androidHome =
          c.getSharedPreferences("launcher", Context.MODE_PRIVATE)
              .getBoolean("stock_home_selected", false);
      preferences
          .edit()
          .putString("mode", androidHome ? HomeMode.ANDROID.id : HomeMode.GLASS.id)
          .apply();
    } else {
      String stored = preferences.getString("mode", HomeMode.GLASS.id);
      HomeMode canonical = HomeMode.fromId(stored);
      if (!canonical.id.equals(stored)) preferences.edit().putString("mode", canonical.id).apply();
    }
  }

  public HomeMode selected() {
    return HomeMode.fromId(preferences.getString("mode", HomeMode.GLASS.id));
  }

  public void save(HomeMode mode) {
    preferences.edit().putString("mode", mode.id).apply();
  }
}
