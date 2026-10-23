package tv.bravia.applehome.core;

/** Canonical stored IDs with compatibility for previous Unified Home selections. */
public enum HomeMode {
  GLASS("glass", "Glass UI"),
  MINIMALIST("minimalist", "Minimalist UI"),
  ANDROID("android", "Android UI");
  public final String id, label;

  HomeMode(String id, String label) {
    this.id = id;
    this.label = label;
  }

  public static HomeMode fromId(String id) {
    // Read old persisted IDs only; all new writes use the canonical names above.
    if ("debloat".equals(id)) return MINIMALIST;
    if ("stock".equals(id)) return ANDROID;
    for (HomeMode m : values()) if (m.id.equals(id)) return m;
    return GLASS;
  }
}
