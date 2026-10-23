package tv.bravia.applehome.search;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.HashMap;
import java.util.Map;

/** Search UI and movie actions, called by the launcher through this controller. */
public final class MovieSearchController {
  private final MovieSearchHost host;
  private final Paint p;
  private final int white = Color.rgb(246, 247, 251);
  private EditText searchInput;
  private final Map<String, Drawable> providerIcons = new HashMap<>();
  private boolean keyboardFocused;
  private String loadedFeatureId = "";
  private Runnable pendingKeyboard;
  boolean searchOpen = false, searchLoading = false, featureLoading = false;
  String searchQuery = "", featureError = "";
  MovieSearch.Suggestions suggestions = new MovieSearch.Suggestions();
  MovieSearch.Feature feature;
  int searchSelection = 0, searchSerial = 0, featureSerial = 0;
  Runnable pendingSearch;

  public MovieSearchController(MovieSearchHost host) {
    this.host = host;
    this.p = new Paint(Paint.ANTI_ALIAS_FLAG);
    MovieSearch.configure(getContext());
    String saved =
        getContext()
            .getSharedPreferences("movie_search", Context.MODE_PRIVATE)
            .getString("featured_movie", "");
    if (!saved.isEmpty()) loadFeature(saved);
  }

  public boolean isSearchOpen() {
    return searchOpen;
  }

  /** The featured search result owns the home content area until explicitly dismissed. */
  public boolean hasFeaturedMovie() {
    return feature != null || featureLoading || !featureError.isEmpty();
  }

  /** Refresh shared selection after returning from the other presentation. */
  public void refreshSavedFeature() {
    String saved =
        getContext()
            .getSharedPreferences("movie_search", Context.MODE_PRIVATE)
            .getString("featured_movie", "");
    if (saved.equals(loadedFeatureId)) return;
    if (saved.isEmpty()) dismissFeature();
    else loadFeature(saved);
  }

  private Context getContext() {
    return host.searchView().getContext();
  }

  private void invalidate() {
    host.searchView().invalidate();
  }

  private void post(Runnable action) {
    host.searchView().post(action);
  }

  private void text(Canvas c, String s, float x, float y, float size, int color) {
    host.text(c, s, x, y, size, color);
  }

  private void rounded(Canvas c, float l, float t, float r, float b, float radius, int color) {
    host.rounded(c, l, t, r, b, radius, color);
  }

  private void drawIconFit(Canvas c, Drawable d, float x, float y, float w, float h) {
    host.drawIconFit(c, d, x, y, w, h);
  }

  public void activateProvider(int index) {
    if (index >= 0 && index < providerCount()) launchProvider(feature.providers.get(index));
  }

  public void attach(FrameLayout root) {
    searchInput =
        new EditText(getContext()) {
          @Override
          public boolean onKeyPreIme(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
              if (event.getAction() == KeyEvent.ACTION_UP) focusSearchResults();
              return true;
            }
            return super.onKeyPreIme(keyCode, event);
          }
        };
    searchInput.setShowSoftInputOnFocus(false);
    searchInput.setFocusable(false);
    searchInput.setSingleLine(true);
    searchInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 30);
    searchInput.setPadding(0, 0, 0, 0);
    searchInput.setGravity(Gravity.CENTER_VERTICAL);
    searchInput.setTextColor(Color.WHITE);
    searchInput.setHintTextColor(0xA8DDE4EF);
    searchInput.setHint("Search movies");
    searchInput.setBackgroundColor(Color.TRANSPARENT);
    searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    FrameLayout.LayoutParams searchLayout =
        new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 76, Gravity.TOP);
    searchLayout.topMargin = 213;
    searchLayout.leftMargin = 96;
    searchLayout.rightMargin = 96;
    root.addView(searchInput, searchLayout);
    searchInput.setVisibility(View.GONE);
    searchInput.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateSearchQuery(s.toString());
          }

          @Override
          public void afterTextChanged(Editable s) {}
        });
    searchInput.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == EditorInfo.IME_ACTION_SEARCH) {
            focusSearchResults();
            return true;
          }
          return false;
        });
    searchInput.setOnKeyListener(
        (v, key, event) -> {
          if (key == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
            focusSearchResults();
            return true;
          }
          return false;
        });
  }

  public void openSearch() {
    beginSearch();
    searchInput.setVisibility(View.VISIBLE);
    searchInput.setText("");
    showNativeKeyboard();
  }

  private void focusHost() {
    if (pendingKeyboard != null) searchInput.removeCallbacks(pendingKeyboard);
    InputMethodManager ime =
        (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (ime != null) ime.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    searchInput.clearFocus();
    searchInput.setShowSoftInputOnFocus(false);
    searchInput.setFocusable(false);
    host.searchView().setFocusableInTouchMode(true);
    host.searchView().requestFocus();
    invalidate();
  }

  void focusSearchResults() {
    keyboardFocused = false;
    searchSelection = suggestions.movies.isEmpty() ? 0 : suggestions.google.size();
    focusHost();
  }

  void editQuery(String suggestion) {
    if (!searchInput.getText().toString().equals(suggestion)) searchInput.setText(suggestion);
    searchInput.setSelection(searchInput.length());
    showNativeKeyboard();
  }

  private void showNativeKeyboard() {
    keyboardFocused = true;
    searchInput.setShowSoftInputOnFocus(true);
    searchInput.setFocusableInTouchMode(true);
    searchInput.requestFocus();
    searchInput.setSelection(searchInput.length());
    if (pendingKeyboard != null) searchInput.removeCallbacks(pendingKeyboard);
    pendingKeyboard =
        () -> {
          if (!searchOpen || !searchInput.hasFocus()) return;
          InputMethodManager ime =
              (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
          if (ime != null) ime.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        };
    searchInput.postDelayed(pendingKeyboard, 120);
    invalidate();
  }

  private void deleteLetter() {
    android.text.Editable value = searchInput.getText();
    if (value.length() > 0)
      value.delete(value.toString().offsetByCodePoints(value.length(), -1), value.length());
  }

  public void hideSearch(boolean goHome) {
    InputMethodManager keyboard =
        (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (keyboard != null) keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    if (pendingKeyboard != null) searchInput.removeCallbacks(pendingKeyboard);
    searchInput.clearFocus();
    searchInput.setVisibility(View.GONE);
    endSearch(goHome);
    host.searchView().requestFocus();
  }

  void beginSearch() {
    host.prepareForSearch();
    searchOpen = true;
    searchSelection = 0;
    suggestions = new MovieSearch.Suggestions();
    searchQuery = "";
    searchLoading = false;
    host.setSearchFocus(false);
    invalidate();
  }

  void endSearch(boolean goHome) {
    searchOpen = false;
    searchSerial++;
    if (pendingSearch != null) host.searchView().removeCallbacks(pendingSearch);
    searchLoading = false;
    host.setSearchFocus(goHome);
    invalidate();
  }

  void updateSearchQuery(String value) {
    if (!searchOpen) return;
    searchQuery = value.trim();
    searchSelection = 0;
    suggestions = new MovieSearch.Suggestions();
    int serial = ++searchSerial;
    if (pendingSearch != null) host.searchView().removeCallbacks(pendingSearch);
    searchLoading = searchQuery.length() >= 2;
    if (searchLoading) {
      String query = searchQuery;
      pendingSearch =
          () ->
              new Thread(
                      () -> {
                        MovieSearch.Suggestions found = MovieSearch.suggest(query);
                        post(
                            () -> {
                              if (serial != searchSerial || !searchOpen) return;
                              suggestions = found;
                              searchSelection =
                                  !keyboardFocused && !found.movies.isEmpty()
                                      ? found.google.size()
                                      : 0;
                              searchLoading = false;
                              invalidate();
                            });
                      },
                      "bravia-movie-search")
                  .start();
      host.searchView().postDelayed(pendingSearch, 450);
    }
    invalidate();
  }

  int searchResultCount() {
    return suggestions.google.size() + suggestions.movies.size();
  }

  void activateSearchResult() {
    if (searchSelection < suggestions.google.size()) {
      String query = suggestions.google.get(searchSelection);
      searchInput.setText(query);
      if (pendingSearch != null) host.searchView().removeCallbacks(pendingSearch);
      int serial = ++searchSerial;
      keyboardFocused = false;
      searchLoading = true;
      new Thread(
              () -> {
                java.util.List<MovieSearch.Movie> matches = MovieSearch.findMovies(query);
                MovieSearch.Movie match = MovieSearch.matchingMovie(query, matches);
                post(
                    () -> {
                      if (serial != searchSerial || !searchOpen) return;
                      searchLoading = false;
                      if (match != null) selectMovie(match.id);
                      else {
                        suggestions = new MovieSearch.Suggestions();
                        suggestions.movies.addAll(matches);
                        searchSelection = 0;
                        invalidate();
                      }
                    });
              },
              "bravia-resolve-suggestion")
          .start();
      return;
    }
    int movieIndex = searchSelection - suggestions.google.size();
    if (movieIndex >= 0 && movieIndex < suggestions.movies.size()) {
      selectMovie(suggestions.movies.get(movieIndex).id);
    }
  }

  private void selectMovie(String id) {
    getContext()
        .getSharedPreferences("movie_search", Context.MODE_PRIVATE)
        .edit()
        .putString("featured_movie", id)
        .apply();
    hideSearch(true);
    loadFeature(id);
  }

  public void dismissFeature() {
    // Invalidate pending details so a late response cannot restore the card.
    featureSerial++;
    loadedFeatureId = "";
    feature = null;
    featureLoading = false;
    featureError = "";
    providerIcons.clear();
    getContext()
        .getSharedPreferences("movie_search", Context.MODE_PRIVATE)
        .edit()
        .remove("featured_movie")
        .apply();
    host.clearMovieProviderFocus();
    invalidate();
  }

  void loadFeature(String imdbId) {
    loadedFeatureId = imdbId;
    int serial = ++featureSerial;
    featureLoading = true;
    featureError = "";
    invalidate();
    new Thread(
            () -> {
              MovieSearch.Feature found = null;
              try {
                found = MovieSearch.feature(imdbId);
              } catch (Exception ignored) {
              }
              MovieSearch.Feature result = found;
              post(
                  () -> {
                    if (serial != featureSerial) return;
                    feature = result;
                    providerIcons.clear();
                    featureLoading = false;
                    featureError = result == null ? "Movie details unavailable" : "";
                    invalidate();
                  });
            },
            "bravia-movie-details")
        .start();
  }

  public void drawFeaturedMovie(Canvas c, float w) {
    if (featureLoading) {
      text(c, "Finding movie and streaming options…", 84, 330, 31, white);
      return;
    }
    if (feature == null) {
      if (!featureError.isEmpty()) text(c, featureError, 84, 330, 28, white);
      return;
    }
    float posterX = 82, posterY = 220, posterW = 220, posterH = 330;
    if (feature.poster != null) {
      int save = c.save();
      Path clip = new Path();
      clip.addRoundRect(
          new RectF(posterX, posterY, posterX + posterW, posterY + posterH),
          20,
          20,
          Path.Direction.CW);
      c.clipPath(clip);
      p.setShader(null);
      p.setAlpha(255);
      p.setColor(Color.WHITE);
      c.drawBitmap(
          feature.poster,
          null,
          new RectF(posterX, posterY, posterX + posterW, posterY + posterH),
          p);
      c.restoreToCount(save);
    }
    float titleX = 340;
    String title = fitText(feature.title, 50, w - titleX - 80);
    text(c, title, titleX, 302, 50, white);
    String details =
        feature.releaseDate.isEmpty()
            ? "Release unavailable · " + feature.year
            : "Release: " + feature.releaseDate;
    if (!feature.imdbRating.isEmpty()) details += "   ·   IMDb " + feature.imdbRating + "/10";
    text(c, details, titleX, 350, 25, 0xE2E7EBF3);
    if (!feature.cast.isEmpty()) {
      text(c, "CAST", titleX, 399, 17, 0xBBDCE4F0);
      text(
          c,
          fitText(android.text.TextUtils.join("   ·   ", feature.cast), 22, w - titleX - 80),
          titleX,
          435,
          22,
          0xE2E7EBF3);
    }
    if (feature.providers.isEmpty()) {
      text(
          c,
          feature.availabilityError.isEmpty()
              ? "No streaming options found for this title in India"
              : feature.availabilityError,
          titleX,
          548,
          22,
          0xD3DBE2EE);
      return;
    }
    text(c, "WATCH IN INDIA", titleX, 504, 18, 0xD8E7EDF4);
    int firstProviderRow =
        host.isMovieProviderFocused() ? Math.max(0, host.movieProviderIndex() / 6 - 1) : 0;
    for (int i = firstProviderRow * 6;
        i < Math.min(feature.providers.size(), (firstProviderRow + 2) * 6);
        i++) {
      float x = titleX + (i % 6) * 226f;
      float y = 526 + (i / 6 - firstProviderRow) * 78f;
      drawProviderPill(
          c,
          feature.providers.get(i),
          x,
          y,
          210,
          66,
          host.isMovieProviderFocused() && host.movieProviderIndex() == i);
    }
    text(
        c,
        "Availability: "
            + feature.availabilitySource
            + "   ·   INFO  Data sources   ·   BACK  Dismiss",
        titleX,
        713,
        15,
        0xBBDCE4F0);
    if (feature.providers.size() > 12) text(c, "↑ ↓  More services", titleX, 741, 15, 0xBBDCE4F0);
  }

  String fitText(String value, float size, float maxWidth) {
    p.setTextSize(size);
    if (p.measureText(value) <= maxWidth) return value;
    String result = value;
    while (result.length() > 1 && p.measureText(result + "…") > maxWidth)
      result = result.substring(0, result.length() - 1);
    return result + "…";
  }

  void drawProviderPill(
      Canvas c,
      MovieSearch.Provider source,
      float x,
      float y,
      float width,
      float height,
      boolean selected) {
    p.setStyle(Paint.Style.FILL);
    p.setShader(
        new LinearGradient(
            x,
            y,
            x,
            y + height,
            selected ? 0xEAF4F7FC : 0x85EAF1F8,
            selected ? 0xC6DCE5F0 : 0x45343E4E,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(new RectF(x, y, x + width, y + height), 30, 30, p);
    p.setShader(null);
    int color = selected ? 0xFF18202D : white;
    Drawable icon = providerIcon(source);
    if (icon != null) drawIconFit(c, icon, x + 17, y + 13, 38, 38);
    else text(c, "▶", x + 24, y + 43, 28, color);
    text(c, fitText(source.name, 19, width - 77), x + 67, y + 29, 19, color);
    text(c, fitText(source.access, 13, width - 77), x + 67, y + 49, 13, color);
  }

  Drawable providerIcon(MovieSearch.Provider source) {
    String key = source.packageName();
    if (key.isEmpty()) return null;
    if (!providerIcons.containsKey(key)) {
      Drawable icon = null;
      try {
        icon = getContext().getPackageManager().getApplicationIcon(key);
      } catch (Exception ignored) {
      }
      providerIcons.put(key, icon);
    }
    return providerIcons.get(key);
  }

  public void drawSearchPanel(Canvas c, float w, float h) {
    rounded(c, 48, 155, w - 48, h - 54, 34, 0xF00B101B);
    p.setShader(null);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.2f);
    p.setColor(0x70FFFFFF);
    c.drawRoundRect(new RectF(48, 155, w - 48, h - 54), 34, 34, p);
    p.setStyle(Paint.Style.FILL);
    text(c, "Search movies", 94, 199, 28, white);
    p.setColor(0x60FFFFFF);
    p.setStrokeWidth(1);
    c.drawLine(94, 306, w - 94, 306, p);
    float resultX = 94;
    if (searchQuery.isEmpty()) {
      text(c, "Type a movie name or hold the remote microphone", resultX, 377, 26, 0xD4DCE5F0);
      return;
    }
    if (searchLoading && searchResultCount() == 0) {
      text(c, "Searching…", resultX, 377, 24, 0xD4DCE5F0);
      return;
    }
    if (searchResultCount() == 0) {
      text(c, "No matches yet", resultX, 377, 24, 0xD4DCE5F0);
      return;
    }
    float selectedTop =
        suggestions.google.isEmpty()
            ? 374 + searchSelection * 58f
            : 370 + searchSelection * 58f + (searchSelection >= suggestions.google.size() ? 37 : 0);
    float scroll = Math.max(0, selectedTop + 52 - (h - 125));
    float y = 354 - scroll;
    int position = 0;
    c.save();
    c.clipRect(resultX - 8, 325, w - 70, h - 118);
    if (!suggestions.google.isEmpty()) {
      text(c, "GOOGLE SUGGESTIONS", resultX, y, 17, 0xBAC7D2E0);
      y += 16;
      for (String suggestion : suggestions.google) {
        drawSearchRow(
            c,
            suggestion,
            "",
            resultX - 8,
            y,
            w - resultX - 80,
            !keyboardFocused && position == searchSelection);
        y += 58;
        position++;
      }
      y += 17;
    }
    if (!suggestions.movies.isEmpty()) {
      text(c, "MOVIES", resultX, y + 4, 17, 0xBAC7D2E0);
      y += 20;
      for (MovieSearch.Movie movie : suggestions.movies) {
        drawSearchRow(
            c,
            movie.title,
            movie.year,
            resultX - 8,
            y,
            w - resultX - 80,
            !keyboardFocused && position == searchSelection);
        y += 58;
        position++;
      }
    }
    c.restore();
    text(
        c,
        keyboardFocused
            ? "HOLD THE REMOTE MICROPHONE TO SPEAK     ·     BACK  RESULTS"
            : "↑ ↓  SELECT     ·     OK  OPEN     ·     LEFT  KEYBOARD     ·     BACK  CLOSE",
        94,
        h - 81,
        17,
        0xC2CDD7E4);
  }

  void drawSearchRow(
      Canvas c, String title, String detail, float x, float top, float width, boolean selected) {
    if (selected) rounded(c, x, top, x + width, top + 52, 18, 0xD5E9F0F8);
    int color = selected ? 0xFF17212F : white;
    text(c, fitText(title, 25, width - 190), x + 18, top + 36, 25, color);
    if (!detail.isEmpty()) text(c, detail, x + width - 145, top + 35, 21, color);
  }

  public int providerCount() {
    return feature == null || featureLoading ? 0 : feature.providers.size();
  }

  void launchProvider(MovieSearch.Provider source) {
    String app = source.packageName();
    for (String link : new String[] {source.tvUrl, source.webUrl}) {
      if (link == null || link.isEmpty()) continue;
      try {
        Intent parsed =
            link.startsWith("intent:")
                ? Intent.parseUri(link, Intent.URI_INTENT_SCHEME)
                : new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        Uri uri = parsed.getData();
        String scheme = uri == null ? "" : uri.getScheme();
        if (scheme == null
            || !(scheme.equals("https")
                || scheme.equals("http")
                || scheme.equals("nflx")
                || scheme.equals("hotstar")
                || scheme.equals("aiv"))) continue;
        // Use only a viewing URI and our known package, never executable feed extras.
        Intent target = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!app.isEmpty()) {
          try {
            getContext().startActivity(new Intent(target).setPackage(app));
            return;
          } catch (android.content.ActivityNotFoundException ignored) {
          }
        }
        if (scheme.equals("https") || scheme.equals("http")) {
          getContext().startActivity(target);
          return;
        }
      } catch (Exception ignored) {
      }
    }
    Toast.makeText(getContext(), "No installed app can open this title link", Toast.LENGTH_LONG)
        .show();
  }

  public void dispose() {
    searchSerial++;
    featureSerial++;
    if (pendingSearch != null) host.searchView().removeCallbacks(pendingSearch);
    if (pendingKeyboard != null) searchInput.removeCallbacks(pendingKeyboard);
  }

  private void appendHardwareKey(KeyEvent event) {
    int codePoint = event.getUnicodeChar();
    if (codePoint != 0 && !Character.isISOControl(codePoint))
      searchInput.append(new String(Character.toChars(codePoint)));
  }

  public boolean handleKey(int key, KeyEvent e) {
    if (key == KeyEvent.KEYCODE_INFO) {
      if (e.getRepeatCount() == 0) {
        TextView details = new TextView(getContext());
        details.setText(
            android.text.Html.fromHtml(
                "Streaming availability information provided by <a"
                    + " href=\"https://www.movieofthenight.com/about/api\">Streaming Availability"
                    + " API by Movie of the Night</a>.<br><br><a"
                    + " href=\"https://api.watchmode.com/\">Watchmode</a> provides additional"
                    + " availability.<br><br>Movie details: <a"
                    + " href=\"https://www.omdbapi.com/\">OMDb</a>, with Cinemeta"
                    + " fallback.<br><br>Availability is for India and may depend on your account"
                    + " and subscription.",
                android.text.Html.FROM_HTML_MODE_LEGACY));
        details.setPadding(40, 28, 40, 28);
        details.setTextSize(18);
        details.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        new android.app.AlertDialog.Builder(getContext())
            .setTitle("Movie search data sources")
            .setView(details)
            .setPositiveButton("Close", null)
            .show();
      }
      return true;
    }
    if (searchOpen) {
      if (key == KeyEvent.KEYCODE_DEL) deleteLetter();
      else if (key == KeyEvent.KEYCODE_BACK) {
        if (keyboardFocused) focusSearchResults();
        else hideSearch(false);
      } else if (keyboardFocused) {
        if (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_ENTER)
          focusSearchResults();
        else appendHardwareKey(e);
      } else if (key == KeyEvent.KEYCODE_DPAD_LEFT) editQuery(searchInput.getText().toString());
      else if (key == KeyEvent.KEYCODE_DPAD_DOWN)
        searchSelection = Math.min(Math.max(0, searchResultCount() - 1), searchSelection + 1);
      else if (key == KeyEvent.KEYCODE_DPAD_UP) {
        if (searchSelection == 0) editQuery(searchInput.getText().toString());
        else searchSelection--;
      } else if ((key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DPAD_CENTER)
          && e.getRepeatCount() == 0) activateSearchResult();
      else appendHardwareKey(e);
      invalidate();
      return true;
    }
    return false;
  }
}
