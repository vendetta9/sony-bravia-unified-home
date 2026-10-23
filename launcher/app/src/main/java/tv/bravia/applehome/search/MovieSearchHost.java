package tv.bravia.applehome.search;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Theme adapter: search owns its state, keyboard, requests and paint, never theme fields. */
public interface MovieSearchHost {
  View searchView();

  void prepareForSearch();

  void setSearchFocus(boolean home);

  boolean isMovieProviderFocused();

  int movieProviderIndex();

  void clearMovieProviderFocus();

  void text(Canvas c, String value, float x, float y, float size, int color);

  void rounded(Canvas c, float l, float t, float r, float b, float radius, int color);

  void drawIconFit(Canvas c, Drawable d, float x, float y, float w, float h);
}
