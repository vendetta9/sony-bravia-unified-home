package tv.bravia.applehome.core.model;

import android.graphics.drawable.Drawable;

/** Immutable installed-app identity and artwork, independent of either layout. */
public final class AppTile {
  public final String key, label, component;
  public final Drawable banner, icon;

  public AppTile(String key, String label, Drawable banner, Drawable icon, String component) {
    this.key = key;
    this.label = label;
    this.banner = banner;
    this.icon = icon;
    this.component = component;
  }
}
