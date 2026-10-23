package tv.bravia.applehome.ui.minimalist;

import tv.bravia.applehome.ui.common.*;

/** Minimalist activity factory; shared lifecycle belongs to BaseHomeActivity. */
public final class MinimalistHomeActivity extends BaseHomeActivity {
  @Override
  protected LauncherSurface createSurface() {
    return new MinimalistHomeView(this);
  }
}
