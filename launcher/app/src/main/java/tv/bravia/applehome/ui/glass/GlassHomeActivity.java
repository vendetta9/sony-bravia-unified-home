package tv.bravia.applehome.ui.glass;

import tv.bravia.applehome.ui.common.*;

/** Glass activity factory; shared lifecycle belongs to BaseHomeActivity. */
public final class GlassHomeActivity extends BaseHomeActivity {
  @Override
  protected LauncherSurface createSurface() {
    return new GlassHomeView(this);
  }
}
