package tv.bravia.applehome.core;

import android.content.*;
import java.util.Map;
import java.util.Set;

/** Versioned, non-destructive migration into isolated UI and search stores. */
public final class LegacyPreferencesMigrator {
  private LegacyPreferencesMigrator() {}

  public static synchronized void migrate(Context c) {
    SharedPreferences migration = c.getSharedPreferences("unified_migration", Context.MODE_PRIVATE);
    if (!migration.getBoolean("v1", false)) {
      SharedPreferences old = c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
      if (!copyMissing(old, c.getSharedPreferences("glass_ui", Context.MODE_PRIVATE), true)) return;
      SharedPreferences search = c.getSharedPreferences("movie_search", Context.MODE_PRIVATE);
      if (!search.contains("featured_movie") && old.contains("featured_movie"))
        search.edit().putString("featured_movie", old.getString("featured_movie", "")).commit();
      migration.edit().putBoolean("v1", true).commit();
    }
    // Independent of v1: devices already running Unified Home must migrate too.
    if (!migration.getBoolean("v2_minimalist", false)) {
      if (!copyMissing(
          c.getSharedPreferences("debloat_ui", Context.MODE_PRIVATE),
          c.getSharedPreferences("minimalist_ui", Context.MODE_PRIVATE),
          false)) return;
      migration.edit().putBoolean("v2_minimalist", true).commit();
    }
  }

  @SuppressWarnings("unchecked")
  private static boolean copyMissing(
      SharedPreferences old, SharedPreferences target, boolean legacy) {
    SharedPreferences.Editor editor = target.edit();
    for (Map.Entry<String, ?> item : old.getAll().entrySet()) {
      String key = item.getKey();
      Object value = item.getValue();
      if (target.contains(key)
          || (legacy && (key.equals("stock_home_selected") || key.equals("featured_movie"))))
        continue;
      if (value instanceof String) editor.putString(key, (String) value);
      else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
      else if (value instanceof Integer) editor.putInt(key, (Integer) value);
      else if (value instanceof Long) editor.putLong(key, (Long) value);
      else if (value instanceof Float) editor.putFloat(key, (Float) value);
      else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
    }
    // Complete each copy before recording its migration marker.
    return editor.commit();
  }
}
