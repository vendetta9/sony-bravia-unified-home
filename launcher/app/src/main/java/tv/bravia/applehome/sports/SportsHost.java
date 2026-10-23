package tv.bravia.applehome.sports;

import android.graphics.Canvas;
import android.view.View;
import java.util.List;
import tv.bravia.applehome.core.model.AppTile;

/** Only navigation/drawing capabilities needed by Sports; no Glass implementation dependency. */
public interface SportsHost {
  View sportsView();

  int focusArea();

  int focusIndex();

  int lastDockIndex();

  void setFocusArea(int value);

  void setFocusIndex(int value);

  List<AppTile> homeCategoryApps();

  void launch(AppTile tile);

  void text(Canvas c, String value, float x, float y, float size, int color);

  void rounded(Canvas c, float l, float t, float r, float b, float radius, int color);

  void drawBrandMark(
      Canvas c, AppTile tile, float x, float y, float w, float h, int color, boolean selected);
}
