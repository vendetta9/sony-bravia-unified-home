package tv.bravia.applehome.ui.glass;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.animation.DecelerateInterpolator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tv.bravia.applehome.R;
import tv.bravia.applehome.core.model.AppTile;
import tv.bravia.applehome.device.*;
import tv.bravia.applehome.search.*;
import tv.bravia.applehome.sports.*;
import tv.bravia.applehome.ui.common.LauncherSurface;

/** Glass presentation, focus and app-order policy. Shared features live outside this view. */
public final class GlassHomeView extends LauncherSurface implements SportsHost {

  final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final android.graphics.Typeface headerRegular =
      android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL);
  final android.graphics.Typeface headerMedium =
      android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL);
  final android.graphics.Typeface headerClock =
      android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL);
  final SimpleDateFormat headerClockFormat = new SimpleDateFormat("h:mm", Locale.ENGLISH);
  final SimpleDateFormat headerPeriodFormat = new SimpleDateFormat("a", Locale.ENGLISH);
  final SimpleDateFormat headerDateFormat = new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH);
  final PackageManager pm;
  final MovieSearchController movies;
  final ProviderPreviewController previews;
  final SportsController sports;
  final List<AppTile> dock = new ArrayList<>();
  final List<AppTile> grid = new ArrayList<>();
  final List<List<Integer>> appRows = new ArrayList<>();
  final List<String> appRowSections = new ArrayList<>();
  final Set<String> configuredKeys = new HashSet<>();
  final Map<String, Integer> ambientColors = new HashMap<>();
  final Map<Integer, Drawable> brandMarks = new HashMap<>();
  final Bitmap[] wallpapers;
  final Bitmap categoryWallpaper;
  final Bitmap[] glassWallpapers;
  int wallpaperIndex = 0;
  int previousWallpaperIndex = 0;
  float wallpaperBlend = 1f;
  ValueAnimator wallpaperAnimator;
  int area = 0, index = 0, columns = 8, gridColumns = 6;
  int homeCategory = 0;
  int lastDockIndex = 0, lastGridIndex = 0;
  float gridScrollY = 0f, gridScrollTargetY = 0f;
  ValueAnimator gridScrollAnimator;
  float focusReveal = 1f;
  int lastFocusArea = -1, lastFocusIndex = -1;
  ValueAnimator focusRevealAnimator;
  boolean inputs = false, panel = false, rearrangeMode = false;
  float panelProgress = 0f;
  ValueAnimator panelAnimator;
  int inputIndex = 0;
  final List<TvInputInfo> tvInputs = new ArrayList<>();
  boolean contextMenu = false, moveMode = false;
  boolean systemInfo = false, updatedSystemApp = false;
  String systemPackage = "", systemLabel = "";
  boolean menuHoldActive = false;
  int contextIndex = 0;
  boolean centerHeld = false, longPressTriggered = false;
  final Handler keyHandler = new Handler(Looper.getMainLooper());
  final VolumeController volume;
  final AppLauncher appLauncher;
  final DeviceStatusController deviceStatus;
  final int white = Color.rgb(246, 247, 251);
  Integer temperatureC;
  String wifiName = "Wi-Fi";
  Integer wifiRssi;

  public GlassHomeView(Context c) {
    super(c);
    setFocusable(true);
    pm = c.getPackageManager();
    volume = new VolumeController(c);
    appLauncher = new AppLauncher(c);
    deviceStatus = new DeviceStatusController(c, this);
    movies = new MovieSearchController(this);
    previews = new ProviderPreviewController(this);
    sports = new SportsController(this);
    categoryWallpaper =
        android.graphics.BitmapFactory.decodeResource(
            getResources(), R.drawable.glass_category_coast);
    refreshInputs();
    wallpapers =
        new Bitmap[] {
          android.graphics.BitmapFactory.decodeResource(
              getResources(), R.drawable.scene_dusk_peaks),
          android.graphics.BitmapFactory.decodeResource(
              getResources(), R.drawable.scene_golden_coast),
          android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.scene_moon_lake),
          android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.scene_aurora),
          android.graphics.BitmapFactory.decodeResource(
              getResources(), R.drawable.scene_city_lights),
          android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.scene_monsoon)
        };
    glassWallpapers = new Bitmap[wallpapers.length];
    for (int i = 0; i < wallpapers.length; i++) {
      Bitmap source = wallpapers[i];
      if (source != null) {
        // A small copy gives the lower glass shelf a soft, live backdrop
        // without changing or blurring the full-screen wallpaper.
        int bw = 240, bh = Math.max(1, Math.round(240f * source.getHeight() / source.getWidth()));
        glassWallpapers[i] = Bitmap.createScaledBitmap(source, bw, bh, true);
      }
    }
    wallpaperIndex = initialWallpaperIndex();
    previousWallpaperIndex = wallpaperIndex;

    refreshWifi();

    load();
  }

  public void refreshWifi() {
    deviceStatus.refreshWifi();
  }

  @Override
  public void onWifi(String name, Integer rssi) {
    wifiName = name;
    wifiRssi = rssi;
    invalidate();
  }

  @Override
  public void onWeather(Integer celsius) {
    temperatureC = celsius;
    invalidate();
  }

  final Runnable clockTick =
      new Runnable() {
        @Override
        public void run() {
          invalidate();
          postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50);
        }
      };

  public void returnToHome() {
    if (panelAnimator != null) panelAnimator.cancel();
    panel = false;
    panelProgress = 0f;
    inputs = false;
    area = 0;
    homeCategory = 0;
    index = 0;
    lastDockIndex = 0;
    lastGridIndex = 0;
    rearrangeMode = false;
    contextMenu = false;
    systemInfo = false;
    moveMode = false;
    menuHoldActive = false;
    contextIndex = 0;
    centerHeld = false;
    longPressTriggered = false;
    if (gridScrollAnimator != null) gridScrollAnimator.cancel();
    if (focusRevealAnimator != null) focusRevealAnimator.cancel();
    gridScrollY = gridScrollTargetY = 0f;
    lastFocusArea = lastFocusIndex = -1;
    focusReveal = 1f;
    requestFocus();
    invalidate();
  }

  int panelCategory = 1;
  int transitionDockCategory = 0;
  List<AppTile> transitionDock = new ArrayList<>();
  final Map<Integer, List<AppTile>> categoryCache = new HashMap<>();

  void previewCategory(int category) {
    if (homeCategory != 1 && homeCategory != 5) {
      transitionDockCategory = homeCategory;
      transitionDock = new ArrayList<>(homeCategoryApps());
    }
    homeCategory = category;
    showAppsPanel(category == 1 || category == 5);
  }

  void showAppsPanel(boolean show) {
    if (panel == show && panelProgress == (show ? 1f : 0f)) return;
    if (show) {
      panelCategory = homeCategory;
      lastGridIndex = 0;
      buildAppRows();
    } else if (area == 1 && !grid.isEmpty()) lastGridIndex = index;
    panel = show;
    if (panelAnimator != null) panelAnimator.cancel();
    panelAnimator = ValueAnimator.ofFloat(panelProgress, show ? 1f : 0f);
    panelAnimator.setDuration(220);
    panelAnimator.setInterpolator(new DecelerateInterpolator(1.3f));
    panelAnimator.addUpdateListener(
        a -> {
          panelProgress = (Float) a.getAnimatedValue();
          invalidate();
        });
    panelAnimator.start();
  }

  final Runnable wallpaperRotation =
      new Runnable() {
        @Override
        public void run() {
          previousWallpaperIndex = wallpaperIndex;
          wallpaperIndex = (wallpaperIndex + 1) % wallpapers.length;
          wallpaperBlend = 0f;
          if (wallpaperAnimator != null) wallpaperAnimator.cancel();
          wallpaperAnimator = ValueAnimator.ofFloat(0f, 1f);
          wallpaperAnimator.setDuration(1400);
          wallpaperAnimator.setInterpolator(new DecelerateInterpolator(1.15f));
          wallpaperAnimator.addUpdateListener(
              a -> {
                wallpaperBlend = (Float) a.getAnimatedValue();
                invalidate();
              });
          wallpaperAnimator.start();
          postDelayed(this, 120000);
        }
      };

  int initialWallpaperIndex() {
    int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    if (hour >= 6 && hour < 11) return 0;
    if (hour >= 11 && hour < 17) return 1;
    if (hour >= 17 && hour < 21) return 2;
    return 3;
  }

  void load() {
    categoryCache.clear();
    dock.clear();
    grid.clear();
    configuredKeys.clear();
    ambientColors.clear();
    String[] keys = {
      "LIVE_TV",
      "com.netflix.ninja",
      "in.startv.hotstar",
      "com.amazon.amazonvideo.livingroom",
      "tv.airtel.xstream.tvapp",
      "com.apple.atve.sony.appletv",
      "com.formulaone.production",
      "com.sonyliv"
    };
    String[] labels = {
      "Live TV", "Netflix", "Hotstar", "Prime Video", "Xstream", "Apple TV", "F1 TV", "Sony LIV"
    };
    List<AppTile> configured = new ArrayList<>();
    Set<String> dockKeys = new HashSet<>();
    Collections.addAll(
        dockKeys,
        "LIVE_TV",
        "com.netflix.ninja",
        "in.startv.hotstar",
        "com.amazon.amazonvideo.livingroom",
        "tv.airtel.xstream.tvapp",
        "com.sonyliv");
    for (int i = 0; i < keys.length; i++) {
      if (!installed(keys[i])) continue;
      configuredKeys.add(keys[i]);
      ResolveInfo resolved = resolve(keys[i]);
      configured.add(
          new AppTile(
              keys[i],
              labels[i],
              loadBanner(keys[i]),
              loadIcon(keys[i]),
              resolved == null ? null : resolved.activityInfo.name));
    }

    // The app panel is the complete library, including dock favourites.
    Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
    List<AppTile> all = new ArrayList<>();
    Set<String> seenPackages = new HashSet<>();
    for (AppTile tile : configured)
      seenPackages.add("LIVE_TV".equals(tile.key) ? "com.sony.dtv.tvx" : tile.key);
    for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
      if (r.activityInfo == null) continue;
      String k = r.activityInfo.packageName;
      if (k.equals(getContext().getPackageName()) || !seenPackages.add(k)) continue;
      all.add(
          new AppTile(
              k,
              k.contains("tvmusic") ? "YouTube Music" : String.valueOf(r.loadLabel(pm)),
              loadBanner(r),
              r.loadIcon(pm),
              r.activityInfo.name));
    }
    grid.addAll(configured);
    grid.addAll(all);
    android.content.SharedPreferences prefs =
        getContext().getSharedPreferences("glass_ui", Context.MODE_PRIVATE);
    String saved = prefs.getString("dock_order", "");
    boolean customizedDock = prefs.getBoolean("dock_customized", false);
    Set<String> added = new HashSet<>();
    if (!saved.isEmpty()) {
      for (String key : saved.split(",")) {
        if (!customizedDock && !dockKeys.contains(key)) continue;
        for (AppTile tile : grid) {
          if (dock.size() < columns && tile.key.equals(key) && added.add(key)) {
            dock.add(tile);
            break;
          }
        }
      }
    }
    if (!customizedDock) {
      for (AppTile tile : configured)
        if (dockKeys.contains(tile.key) && added.add(tile.key)) dock.add(tile);
    }
    Collections.sort(
        grid,
        Comparator.comparingInt((AppTile a) -> initialAppRank(a))
            .thenComparing(a -> a.label.toLowerCase(Locale.US)));
    String appOrder =
        getContext()
            .getSharedPreferences("glass_ui", Context.MODE_PRIVATE)
            .getString("apps_order", "");
    int appOrderVersion =
        getContext()
            .getSharedPreferences("glass_ui", Context.MODE_PRIVATE)
            .getInt("apps_order_version", 0);
    if (appOrderVersion == 3 && !appOrder.isEmpty()) {
      List<AppTile> ordered = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (String key : appOrder.split(",")) {
        for (AppTile tile : grid) if (tile.key.equals(key) && seen.add(key)) ordered.add(tile);
      }
      for (AppTile tile : grid) if (seen.add(tile.key)) ordered.add(tile);
      grid.clear();
      grid.addAll(ordered);
    }
    buildAppRows();
  }

  int initialAppRank(AppTile tile) {
    String[] first = {
      "LIVE_TV",
      "com.netflix.ninja",
      "in.startv.hotstar",
      "com.amazon.amazonvideo.livingroom",
      "com.sonyliv",
      "com.graymatrix.did",
      "tv.airtel.xstream.tvapp",
      "com.formulaone.production",
      "com.apple.atve.sony.appletv",
      "com.spotify.tv.android",
      "com.google.android.youtube.tv",
      "com.google.android.youtube.tvmusic"
    };
    for (int i = 0; i < first.length; i++) if (tile.key.equals(first[i])) return i;
    String label = tile.label.toLowerCase(Locale.US);
    if (label.equals("spotify")) return 9;
    if (label.equals("youtube music")) return 11;
    if (label.equals("youtube")) return 10;
    return first.length;
  }

  int appSectionOrder(AppTile tile) {
    String label = tile.label.toLowerCase(Locale.US);
    String pkg = tile.key.toLowerCase(Locale.US);
    if (label.contains("youtube music") || pkg.contains("tvmusic")) return 1;
    if (label.contains("news")
        || pkg.contains("news")
        || label.contains("ndtv")
        || label.contains("inshort")
        || label.contains("times of india")) return 3;
    if (pkg.contains("formulaone")
        || label.contains("sport")
        || label.contains("f1")
        || label.contains("espn")
        || label.contains("cricket")
        || pkg.contains("sport")) return 2;
    if (label.contains("netflix")
        || label.contains("hotstar")
        || label.contains("zee")
        || label.contains("prime")
        || label.contains("sony liv")
        || label.contains("xstream")
        || label.contains("apple tv")
        || label.contains("live tv")
        || label.contains("youtube")) return 0;
    if (label.contains("spotify")
        || label.contains("music")
        || label.contains("tunein")
        || pkg.contains("music")
        || pkg.contains("spotify")) return 1;
    return 4;
  }

  String appSectionTitle(AppTile tile) {
    int section = appSectionOrder(tile);
    return section == 0
        ? "MOVIES & TV"
        : section == 1 ? "MUSIC" : section == 2 ? "SPORTS" : section == 3 ? "NEWS" : "TOOLS";
  }

  public List<AppTile> homeCategoryApps() {
    List<AppTile> cached = categoryCache.get(homeCategory);
    if (cached != null) return cached;
    List<AppTile> result = computeCategoryApps();
    categoryCache.put(homeCategory, result);
    return result;
  }

  List<AppTile> computeCategoryApps() {
    if (homeCategory == 0) return movieDockApps();
    List<AppTile> result = new ArrayList<>();
    int wanted =
        homeCategory == 0
            ? 0
            : homeCategory == 2 ? 1 : homeCategory == 3 ? 2 : homeCategory == 4 ? 3 : -1;
    if (homeCategory == 1 || homeCategory == 5) return result;
    for (AppTile tile : grid) {
      String key = tile.key.toLowerCase(Locale.US);
      String label = tile.label.toLowerCase(Locale.US);
      boolean youtube = label.contains("youtube") && !label.contains("music");
      boolean sharedSports =
          key.contains("hotstar") || key.contains("sonyliv") || key.contains("formulaone");
      if (appSectionOrder(tile) == wanted
          || homeCategory == 3 && sharedSports
          || homeCategory == 4 && youtube) result.add(tile);
    }
    if (homeCategory == 0) {
      // YouTube remains installed and available in Apps, Sports and News.
      result.removeIf(t -> t.label.toLowerCase(Locale.US).contains("youtube"));
      Collections.sort(result, Comparator.comparingInt(t -> "LIVE_TV".equals(t.key) ? 0 : 1));
    }
    if (homeCategory == 2)
      Collections.sort(
          result,
          Comparator.comparingInt(
              t -> t.key.contains("spotify") ? 0 : t.key.contains("tvmusic") ? 1 : 2));
    String order =
        getContext()
            .getSharedPreferences("glass_ui", Context.MODE_PRIVATE)
            .getString("category_order_" + homeCategory, "");
    if (!order.isEmpty()) {
      List<String> keys = java.util.Arrays.asList(order.split(","));
      Collections.sort(
          result,
          Comparator.comparingInt(
              t -> {
                int rank = keys.indexOf(t.key);
                return rank < 0 ? keys.size() : rank;
              }));
    }
    return result;
  }

  List<AppTile> movieDockApps() {
    List<AppTile> result = new ArrayList<>();
    android.content.SharedPreferences prefs =
        getContext().getSharedPreferences("glass_ui", Context.MODE_PRIVATE);
    if (prefs.contains("glass_dock_order")) {
      for (String key : prefs.getString("glass_dock_order", "").split(",")) {
        for (AppTile tile : grid)
          if (tile.key.equals(key)
              && !"LIVE_TV".equals(tile.key)
              && !tile.key.toLowerCase(Locale.US).contains("xstream")
              && !result.contains(tile)
              && result.size() < columns) {
            result.add(tile);
            break;
          }
      }
    } else {
      for (AppTile tile : grid)
        if (!"LIVE_TV".equals(tile.key)
            && !tile.key.toLowerCase(Locale.US).contains("xstream")
            && appSectionOrder(tile) == 0
            && !tile.label.toLowerCase(Locale.US).contains("youtube")) result.add(tile);
      Collections.sort(result, Comparator.comparingInt(t -> "LIVE_TV".equals(t.key) ? 0 : 1));
      List<String> order =
          java.util.Arrays.asList(prefs.getString("category_order_0", "").split(","));
      if (!order.get(0).isEmpty())
        Collections.sort(
            result,
            Comparator.comparingInt(
                t -> {
                  int rank = order.indexOf(t.key);
                  return rank < 0 ? order.size() : rank;
                }));
      if (result.size() > columns) return new ArrayList<>(result.subList(0, columns));
    }
    return result;
  }

  void saveMovieDock(List<AppTile> apps) {
    categoryCache.clear();
    StringBuilder order = new StringBuilder();
    for (AppTile tile : apps) {
      if (order.length() > 0) order.append(',');
      order.append(tile.key);
    }
    getContext()
        .getSharedPreferences("glass_ui", Context.MODE_PRIVATE)
        .edit()
        .putString("glass_dock_order", order.toString())
        .apply();
  }

  void buildAppRows() {
    appRows.clear();
    appRowSections.clear();
    if (homeCategory == 1) {
      for (int start = 0; start < grid.size(); start += gridColumns) {
        List<Integer> row = new ArrayList<>();
        for (int i = start; i < Math.min(start + gridColumns, grid.size()); i++) row.add(i);
        appRows.add(row);
        appRowSections.add("");
      }
      return;
    }
    // Group the + pane independently; never re-sort the saved flat Apps order.
    for (int group = 0; group <= 4; group++) {
      List<Integer> values = new ArrayList<>();
      for (int i = 0; i < grid.size(); i++)
        if (appSectionOrder(grid.get(i)) == group) values.add(i);
      for (int start = 0; start < values.size(); start += gridColumns) {
        List<Integer> row =
            new ArrayList<>(values.subList(start, Math.min(start + gridColumns, values.size())));
        appRows.add(row);
        appRowSections.add(appSectionTitle(grid.get(row.get(0))));
      }
    }
  }

  int appRowForIndex(int appIndex) {
    for (int r = 0; r < appRows.size(); r++) if (appRows.get(r).contains(appIndex)) return r;
    return -1;
  }

  void moveAppVertically(int direction) {
    int rowIndex = appRowForIndex(index);
    if (rowIndex < 0) return;
    if (direction < 0 && rowIndex == 0) {
      showAppsPanel(false);
      area = 4;
      index = homeCategory;
      return;
    }
    int next = rowIndex + direction;
    if (next < 0 || next >= appRows.size()) return;
    List<Integer> currentRow = appRows.get(rowIndex), nextRow = appRows.get(next);
    int col = currentRow.indexOf(index);
    index = nextRow.get(Math.min(Math.max(0, col), nextRow.size() - 1));
  }

  boolean installed(String key) {
    try {
      pm.getPackageInfo(packageFor(key), 0);
      return true;
    } catch (PackageManager.NameNotFoundException ignored) {
      return false;
    }
  }

  public void refreshApps() {
    String selectedKey = null;
    if (area == 1 && index >= 0 && index < grid.size()) selectedKey = grid.get(index).key;
    else if (area == 0 && index >= 0 && index < homeCategoryApps().size())
      selectedKey = homeCategoryApps().get(index).key;
    int previousIndex = index;
    load();
    if (area == 1) {
      if (grid.isEmpty()) {
        showAppsPanel(false);
        area = 0;
        index = 0;
      } else index = restoredIndex(grid, selectedKey, previousIndex);
    } else if (area == 0) {
      List<AppTile> visible = homeCategoryApps();
      index = visible.isEmpty() ? 0 : restoredIndex(visible, selectedKey, previousIndex);
    }
    invalidate();
  }

  int restoredIndex(List<AppTile> tiles, String key, int previousIndex) {
    if (key != null)
      for (int i = 0; i < tiles.size(); i++) if (tiles.get(i).key.equals(key)) return i;
    return Math.min(previousIndex, tiles.size() - 1);
  }

  ResolveInfo resolve(String key) {
    if ("LIVE_TV".equals(key)) key = "com.sony.dtv.tvx";
    Intent q =
        new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setPackage(key);
    List<ResolveInfo> found = pm.queryIntentActivities(q, PackageManager.MATCH_ALL);
    return found.isEmpty() ? null : found.get(0);
  }

  Drawable loadIcon(String key) {
    String pkg = "LIVE_TV".equals(key) ? "com.sony.dtv.tvx" : key;
    try {
      return pm.getApplicationIcon(pkg);
    } catch (Exception e) {
      return null;
    }
  }

  Drawable loadBanner(String key) {
    ResolveInfo r = resolve(key);
    return r == null
        ? loadApplicationBanner("LIVE_TV".equals(key) ? "com.sony.dtv.tvx" : key)
        : loadBanner(r);
  }

  Drawable loadApplicationBanner(String pkg) {
    try {
      ApplicationInfo ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_ALL);
      return bannerFor(ai.banner, ai);
    } catch (Exception e) {
      return null;
    }
  }

  Drawable loadBanner(ResolveInfo r) {
    ActivityInfo ai = r.activityInfo;
    Drawable d = bannerFor(ai.banner, ai.applicationInfo);
    if (d == null) d = bannerFor(ai.applicationInfo.banner, ai.applicationInfo);
    return d;
  }

  Drawable bannerFor(int id, ApplicationInfo owner) {
    if (id == 0) return null;
    try {
      return pm.getResourcesForApplication(owner)
          .getDrawableForDensity(
              id, Math.max(320, getResources().getDisplayMetrics().densityDpi), null);
    } catch (Exception e) {
      return null;
    }
  }

  public void text(Canvas c, String s, float x, float y, float size, int color) {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setColor(color);
    p.setTextSize(size);
    p.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    c.drawText(s, x, y, p);
  }

  public void rounded(Canvas c, float l, float t, float r, float b, float rad, int color) {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setColor(color);
    c.drawRoundRect(new RectF(l, t, r, b), rad, rad, p);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    if (!panel && panelProgress == 0f && (gridScrollY != 0f || gridScrollTargetY != 0f)) {
      if (gridScrollAnimator != null) gridScrollAnimator.cancel();
      gridScrollY = gridScrollTargetY = 0f;
    }
    float w = getWidth(), h = getHeight();
    drawWallpaper(c, w, h);

    List<AppTile> categoryItems = homeCategoryApps();
    AppTile focused =
        (area == 0 || area == 4) && !categoryItems.isEmpty()
            ? categoryItems.get(
                Math.min(area == 0 ? index : lastDockIndex, categoryItems.size() - 1))
            : area == 1 && !grid.isEmpty() ? grid.get(Math.min(index, grid.size() - 1)) : null;
    animateFocusReveal();
    // The reference keeps the wallpaper artwork clean; per-app color washes
    // obscure it and made the former dock look like a boxed launcher panel.
    if (isForeground()
        && homeCategory == 0
        && !movies.hasFeaturedMovie()
        && (area == 0 || area == 4)
        && !panel
        && focused != null) previews.select(focused.key);
    else previews.select(null);
    previews.draw(c, w, h);
    sports.setActive(isForeground() && homeCategory == 3 && !panel);
    if (homeCategory == 3 && !panel) {
      if (area == 0 && focused != null) sports.selectProvider(focused.key);
      else if (area == 4) sports.selectProvider("");
      sports.draw(c, w, h);
    }
    drawGlassDockBackdrop(c, w, h);
    drawHeader(c, w, h);
    if (homeCategory == 0 && !movies.isSearchOpen() && panelProgress == 0f)
      movies.drawFeaturedMovie(c, w);
    if (homeCategory == 2 && (area == 0 || area == 4) && !panel && !volume.isMusicActive())
      drawMusicEmptyState(c, w, h);

    if (panelProgress < 1f) {
      int save = c.save();
      c.translate(0, 12f * panelProgress);
      float dockScale = 1f;
      c.scale(dockScale, dockScale, w / 2f, appRowCenter(h));
      c.saveLayerAlpha(0, h * 0.66f, w, h, Math.round(255f * (1f - panelProgress)));
      List<AppTile> currentApps = homeCategoryApps();
      AppTile dockFocused =
          currentApps.isEmpty()
              ? null
              : currentApps.get(
                  Math.min(area == 0 ? index : lastDockIndex, currentApps.size() - 1));
      drawDock(c, w, h, dockFocused);
      c.restoreToCount(save);
    }
    if (panelProgress > 0f) {
      int save = c.save();
      c.translate(0, 24f * (1f - panelProgress));
      float panelScale = 1f;
      c.scale(panelScale, panelScale, w / 2f, h * 0.58f);
      c.saveLayerAlpha(40, 148, w - 40, h, Math.round(255f * panelProgress));
      drawAppsPanel(c, w, h);
      c.restoreToCount(save);
    }
    if (inputs) drawInputs(c, w);
    if (movies.isSearchOpen()) movies.drawSearchPanel(c, w, h);
    if (panelProgress > 0f) drawHomeCategories(c, w, h);
    if (contextMenu) drawContextMenu(c, w, h);
    if (systemInfo) drawSystemInfo(c, w, h);
  }

  void drawWallpaper(Canvas c, float w, float h) {
    c.drawColor(0xFF89B9D1);
    if (categoryWallpaper != null) drawWallpaperBitmap(c, categoryWallpaper, w, h, 255);
    p.setColor(Color.WHITE);
    p.setShader(new LinearGradient(0, 0, 0, h, 0x00000000, 0x18030A14, Shader.TileMode.CLAMP));
    c.drawRect(0, 0, w, h, p);
    p.setShader(null);
  }

  void drawGlassDockBackdrop(Canvas c, float w, float h) {
    float top = h * 0.68f;
    p.setStyle(Paint.Style.FILL);
    p.setColor(Color.WHITE);
    p.setShader(new LinearGradient(0, top, 0, h, 0x00040A12, 0x25040A12, Shader.TileMode.CLAMP));
    c.drawRect(0, top, w, h, p);
    p.setShader(null);
    if (panelProgress < 0.15f || homeCategory == 1) drawHomeCategories(c, w, h);
  }

  void drawMusicEmptyState(Canvas c, float w, float h) {
    float x = 110f, y = h * 0.25f, size = h * .415f;
    rounded(c, x, y, x + size, y + size, 24f, 0x4EDAE5EF);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.3f);
    p.setColor(0x72FFFFFF);
    c.drawRoundRect(new RectF(x + .5f, y + .5f, x + size - .5f, y + size - .5f), 24f, 24f, p);
    p.setStyle(Paint.Style.FILL);
    p.setTextSize(88f);
    p.setColor(0xEAF4F7FA);
    p.setTypeface(android.graphics.Typeface.create("sans-serif", 0));
    String icon = "♫";
    c.drawText(icon, x + (size - p.measureText(icon)) / 2f, y + size * .54f, p);
    float textX = x + size + 42f, cy = y + size * .16f;
    p.setColor(0xF4F7FAFC);
    c.drawCircle(textX + 40f, cy + 15f, 40f, p);
    p.setColor(0xFF526477);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(2.5f);
    p.setStrokeJoin(Paint.Join.ROUND);
    Path play = new Path();
    play.moveTo(textX + 31f, cy + 1f);
    play.lineTo(textX + 31f, cy + 29f);
    play.lineTo(textX + 54f, cy + 15f);
    play.close();
    c.drawPath(play, p);
    p.setStyle(Paint.Style.FILL);
    text(c, "Nothing playing", textX + 106f, cy + 8f, 28f, 0xFFF5F7FA);
    text(c, "Pick something below to start listening", textX + 106f, cy + 39f, 18f, 0xE0E8EDF4);
  }

  int nextCategory(int category, int direction) {
    int[] order = {0, 3, 1, 2, 4, 5};
    for (int i = 0; i < order.length; i++)
      if (order[i] == category) return order[(i + direction + order.length) % order.length];
    return 0;
  }

  void drawHomeCategories(Canvas c, float w, float h) {
    if (panelCategory == 5 && panel && area != 4) return;
    String[] items = {"MOVIES", "SPORTS", "APPS", "MUSIC", "NEWS", "+"};
    int[] order = {0, 3, 1, 2, 4, 5};
    float font = 28f, gap = 24f, height = 54f, total = 0f;
    p.setTypeface(android.graphics.Typeface.create("sans-serif", 0));
    p.setTextSize(font);
    float[] widths = new float[6];
    for (int i = 0; i < 6; i++) {
      widths[i] = i == 5 ? 54f : p.measureText(items[i]) + 44f;
      total += widths[i] + (i == 5 ? 0f : gap);
    }
    float x = (w - total) / 2f, y = h * .925f;
    for (int i = 0; i < 6; i++) {
      boolean active = order[i] == homeCategory, focused = area == 4 && index == order[i];
      if (i == 5) {
        p.setColor(focused ? 0xFFF4F7F8 : 0x8EDCE8F4);
        c.drawCircle(x + widths[i] / 2f, y - 4f, 24f, p);
        text(c, "+", x + widths[i] / 2f - 8f, y + 5f, 30f, 0xFF24354A);
      } else {
        if (active || focused)
          rounded(
              c,
              x,
              y - height * .58f,
              x + widths[i],
              y + height * .42f,
              height / 2f,
              focused ? 0xFFF4F7F8 : 0xB5DCE7F1);
        text(c, items[i], x + 22f, y + 5f, font, active || focused ? 0xFF27384B : 0xFFF5F7FA);
      }
      x += widths[i] + gap;
    }
  }

  void drawWallpaperBitmap(Canvas c, Bitmap wallpaper, float w, float h, int alpha) {
    if (wallpaper == null || alpha <= 0) return;
    float scale = Math.max(w / wallpaper.getWidth(), h / wallpaper.getHeight());
    float dw = wallpaper.getWidth() * scale, dh = wallpaper.getHeight() * scale;
    p.setAlpha(alpha);
    c.drawBitmap(
        wallpaper, null, new RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), p);
  }

  void drawAmbient(Canvas c, float w, float h, AppTile tile) {
    drawAmbientColor(c, w, h, ambientColor(tile));
  }

  int ambientColor(AppTile tile) {
    if (tile == null) return 0;
    Integer cachedColor = ambientColors.get(tile.key);
    if (cachedColor != null) return cachedColor;
    int color = sampleColor(tile.banner != null ? tile.banner : tile.icon);
    ambientColors.put(tile.key, color);
    return color;
  }

  int blendColors(int from, int to, float progress) {
    if (from == 0) return to;
    if (to == 0) return from;
    return Color.rgb(
        Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * progress),
        Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * progress),
        Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * progress));
  }

  void drawAmbientColor(Canvas c, float w, float h, int color) {
    if (color == 0) return;
    p.setColor(Color.WHITE);
    p.setShader(
        new RadialGradient(
            w * 0.34f,
            h * 0.58f,
            w * 0.68f,
            (color & 0x00FFFFFF) | 0x65000000,
            (color & 0x00FFFFFF) | 0x00000000,
            Shader.TileMode.CLAMP));
    c.drawRect(0, 0, w, h, p);
    p.setShader(null);
  }

  int sampleColor(Drawable d) {
    if (d == null) return 0;
    int sw = 24, sh = 14, sumR = 0, sumG = 0, sumB = 0, count = 0;
    Bitmap b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
    Canvas cc = new Canvas(b);
    d.setBounds(0, 0, sw, sh);
    d.draw(cc);
    int[] px = new int[sw * sh];
    b.getPixels(px, 0, sw, 0, 0, sw, sh);
    for (int value : px)
      if (Color.alpha(value) > 20) {
        sumR += Color.red(value);
        sumG += Color.green(value);
        sumB += Color.blue(value);
        count++;
      }
    b.recycle();
    return count == 0 ? 0 : Color.rgb(sumR / count, sumG / count, sumB / count);
  }

  void drawHeader(Canvas c, float w, float h) {
    Calendar current = Calendar.getInstance();
    Date now = current.getTime();
    int hour = current.get(Calendar.HOUR_OF_DAY);
    String greeting =
        hour < 5
            ? "Good Night"
            : hour < 12
                ? "Good Morning"
                : hour < 17 ? "Good Afternoon" : hour < 21 ? "Good Evening" : "Good Night";
    String weather = temperatureC == null ? "--°C" : temperatureC + "°C";
    final float left = 64f, baseline = 124f;
    headerClockFormat.setTimeZone(current.getTimeZone());
    headerPeriodFormat.setTimeZone(current.getTimeZone());
    headerDateFormat.setTimeZone(current.getTimeZone());
    String clock = headerClockFormat.format(now), period = headerPeriodFormat.format(now);
    String date = headerDateFormat.format(now);

    // Shared small-text baseline, measured gaps and one visible left edge.
    headerPaint.setColor(white);
    headerPaint.setTypeface(headerMedium);
    headerPaint.setTextSize(22);
    c.drawText(greeting.toUpperCase(Locale.ENGLISH) + "  |  " + weather, left, 45, headerPaint);
    headerPaint.setTypeface(headerClock);
    headerPaint.setTextSize(80);
    Rect clockBounds = new Rect();
    headerPaint.getTextBounds(clock, 0, clock.length(), clockBounds);
    float clockX = left - clockBounds.left;
    c.drawText(clock, clockX, baseline, headerPaint);
    float periodX = clockX + headerPaint.measureText(clock) + 16f;
    headerPaint.setTypeface(headerMedium);
    headerPaint.setTextSize(22);
    c.drawText(period, periodX, baseline, headerPaint);
    float dividerX = periodX + headerPaint.measureText(period) + 20f;
    headerPaint.setColor(0x99FFFFFF);
    headerPaint.setStrokeWidth(1.5f);
    c.drawLine(dividerX, 94, dividerX, baseline + 1f, headerPaint);
    headerPaint.setColor(white);
    headerPaint.setTypeface(headerRegular);
    headerPaint.setTextSize(24);
    c.drawText(date, dividerX + 20f, baseline, headerPaint);

    float x = w - 678f;
    drawPill(c, x - 84, 52, 72, "", 3, area == 2 && index == 0);
    drawPill(c, x, 52, 180, "Inputs", 0, area == 2 && index == 1);
    drawPill(c, x + 192, 52, 230, wifiName, 1, area == 2 && index == 2);
    drawPill(c, x + 434, 52, 180, "Settings", 2, area == 2 && index == 3);
  }

  void drawPill(Canvas c, float x, float y, float width, String label, int icon, boolean selected) {
    float height = 72;
    p.setStyle(Paint.Style.FILL);
    p.setColor(Color.WHITE);
    p.setShader(
        new LinearGradient(
            x,
            y,
            x,
            y + height,
            selected ? 0xDDEDF2F8 : 0x70EFF5FC,
            selected ? 0xBBD5DFEA : 0x35333C4B,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(new RectF(x, y, x + width, y + height), 36, 36, p);
    p.setShader(null);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.2f);
    p.setColor(selected ? 0xF5FFFFFF : 0x78FFFFFF);
    c.drawRoundRect(new RectF(x + 0.6f, y + 0.6f, x + width - 0.6f, y + height - 0.6f), 36, 36, p);
    p.setStyle(Paint.Style.FILL);
    if (icon == 3) {
      drawSearchIcon(c, x + 35, y + 34, selected ? 0xFF18202D : white);
      return;
    }
    if (icon == 1) drawWifi(c, x + 36, y + 35, selected ? 0xFF18202D : white);
    else if (icon == 2) text(c, "⚙", x + 17, y + 50, 42, selected ? 0xFF18202D : white);
    else drawPlug(c, x + 36, y + 34, selected ? 0xFF18202D : white);
    int textColor = selected ? 0xFF18202D : white;
    if (icon == 1) {
      p.setTextSize(22);
      String shown = label;
      while (shown.length() > 1 && p.measureText(shown) > width - 86)
        shown = shown.substring(0, shown.length() - 2) + "…";
      text(c, shown, x + 70, y + 45, 22, textColor);
    } else text(c, label, x + 70, y + 45, 22, textColor);
  }

  void drawGlassButton(
      Canvas c, float x, float y, float size, String label, int icon, boolean selected) {
    float height = size;
    p.setStyle(Paint.Style.FILL);
    p.setColor(Color.WHITE);
    p.setShader(
        new LinearGradient(
            x,
            y,
            x,
            y + height,
            selected ? 0xEFF4F7FA : 0x42677586,
            selected ? 0xC9D6E1EC : 0x1F36465C,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(new RectF(x, y, x + size, y + height), height / 2f, height / 2f, p);
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    if (icon == 3) {
      drawSearchIcon(c, x + size / 2f - 1, y + height / 2f - 1, selected ? 0xFF18202D : white);
      return;
    }
    if (icon == 2) text(c, "⚙", x + 7, y + 39, 34, selected ? 0xFF18202D : white);
    else drawPlug(c, x + size / 2f, y + height / 2f, selected ? 0xFF18202D : white);
  }

  void drawGlassWifi(
      Canvas c, float x, float y, float width, float height, String label, boolean selected) {
    float radius = height / 2f;
    p.setStyle(Paint.Style.FILL);
    p.setColor(Color.WHITE);
    p.setShader(
        new LinearGradient(
            x,
            y,
            x,
            y + height,
            selected ? 0xEFF4F7FA : 0x42677586,
            selected ? 0xC9D6E1EC : 0x1F36465C,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(new RectF(x, y, x + width, y + height), radius, radius, p);
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    drawWifi(c, x + 27, y + height / 2f, selected ? 0xFF18202D : white);
    p.setTextSize(17);
    String shown = label;
    while (shown.length() > 1 && p.measureText(shown) > width - 60)
      shown = shown.substring(0, shown.length() - 2) + "…";
    text(c, shown, x + 52, y + height * 0.62f, 17, selected ? 0xFF18202D : white);
  }

  void drawSearchIcon(Canvas c, float x, float y, int color) {
    p.setShader(null);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(4.5f);
    p.setColor(color);
    c.drawCircle(x - 3, y - 4, 12, p);
    c.drawLine(x + 6, y + 5, x + 18, y + 17, p);
    p.setStyle(Paint.Style.FILL);
  }

  void drawPlug(Canvas c, float x, float y, int color) {
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(4f);
    p.setColor(color);
    c.drawRoundRect(new RectF(x - 12, y - 14, x + 12, y + 10), 4, 4, p);
    c.drawLine(x - 6, y + 10, x - 6, y + 17, p);
    c.drawLine(x + 6, y + 10, x + 6, y + 17, p);
    p.setStyle(Paint.Style.FILL);
  }

  void drawWifi(Canvas c, float x, float y, int color) {
    int level =
        wifiRssi == null ? 0 : wifiRssi >= -60 ? 3 : wifiRssi >= -70 ? 2 : wifiRssi >= -80 ? 1 : 0;
    int faded = (color & 0x00FFFFFF) | 0x55000000;
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(4f);
    p.setColor(level >= 3 ? color : faded);
    c.drawArc(new RectF(x - 20, y - 17, x + 20, y + 17), 220, 100, false, p);
    p.setColor(level >= 2 ? color : faded);
    c.drawArc(new RectF(x - 13, y - 10, x + 13, y + 13), 220, 100, false, p);
    p.setColor(level >= 1 ? color : faded);
    c.drawArc(new RectF(x - 7, y - 3, x + 7, y + 10), 220, 100, false, p);
    p.setColor(wifiRssi == null ? faded : color);
    p.setStyle(Paint.Style.FILL);
    c.drawCircle(x, y + 11, 4f, p);
  }

  float appRowCenter(float height) {
    float titleBottom = height * .715f;
    float categoryTop = height * .925f - 54f * .58f;
    return (titleBottom + categoryTop) / 2f;
  }

  void drawDock(Canvas c, float w, float h, AppTile focused) {
    boolean outgoing = homeCategory == 1 || homeCategory == 5;
    int category = outgoing ? transitionDockCategory : homeCategory;
    List<AppTile> visibleApps = outgoing ? transitionDock : homeCategoryApps();
    if (category == 2) {
      drawMusicDock(c, w, h, visibleApps);
      return;
    }
    int visibleCount = Math.min(columns, visibleApps.size());
    if (visibleCount == 0) return;
    int focusedIndex = Math.min(area == 0 ? index : lastDockIndex, visibleCount - 1);
    boolean highlightDock = area == 0 && !outgoing;
    float gap = Math.max(18f, Math.min(28f, w * 0.014f));
    float tileW = Math.min(260f, (w - 240f - gap * (visibleCount - 1)) / visibleCount);
    float start = (w - visibleCount * tileW - gap * (visibleCount - 1)) / 2f;
    if (category >= 2) start = 110f;
    float tileH = Math.min(h * 0.13f, tileW * 0.53f);
    if (category == 0) {
      gap = 24f;
      tileW = (w - 220f - gap * 5f) / 6f;
      tileH = Math.min(128f, tileW * .53f);
      start = (w - visibleCount * tileW - gap * (visibleCount - 1)) / 2f;
    }
    float y = appRowCenter(h) - tileH * 0.5f;
    float labelY = h * 0.76f;
    if (focused != null && rearrangeMode)
      text(c, "HOLD OK TO FINISH  ·  ◀ ▶  MOVE", w - 430, labelY + 2, 18, 0xE6F2F7FF);
    for (int i = 0; i < visibleCount; i++) {
      AppTile t = visibleApps.get(i);
      float x = start + i * (tileW + gap);
      if (!highlightDock || i != focusedIndex) {
        drawGlassAppPill(c, t, x, y, tileW, tileH, false);
      }
    }
    if (highlightDock) {
      float x = start + focusedIndex * (tileW + gap);
      drawGlassHoveredPill(c, visibleApps.get(focusedIndex), x, y, tileW, tileH);
    }
  }

  void drawMusicDock(Canvas c, float w, float h, List<AppTile> apps) {
    int n = Math.min(3, apps.size());
    if (n == 0) return;
    float gap = 24f, available = w - 220f - gap * (n - 1), x = 110f, height = h * .096f;
    float y = appRowCenter(h) - height / 2f;
    if (rearrangeMode) text(c, "◀ ▶  MOVE APP  ·  OK  DONE", 110f, y - 24f, 22f, Color.WHITE);
    for (int i = 0; i < n; i++) {
      float width = n == 1 ? available : i == 0 ? available * .5f : available * .5f / (n - 1);
      if (area == 0 && index == i) drawGlassHoveredPill(c, apps.get(i), x, y, width, height);
      else drawGlassAppPill(c, apps.get(i), x, y, width, height, false);
      x += width + gap;
    }
  }

  void drawGlassHoveredPill(
      Canvas c, AppTile tile, float baseX, float baseY, float baseW, float baseH) {
    float sixAppWidth = (getWidth() - 144f - 5f * 24f) / 6f;
    float scale = 1f + Math.min(0.14f, 0.10f * sixAppWidth / baseW) * focusReveal;
    float w = baseW * scale, h = baseH * scale;
    float x = baseX - (w - baseW) / 2f;
    float y = baseY - (h - baseH) / 2f - 3f * focusReveal;
    drawGlassAppPill(c, tile, x, y, w, h, true);
  }

  void drawGlassAppPill(
      Canvas c, AppTile tile, float x, float y, float w, float h, boolean selected) {
    float radius = h * 0.5f;
    RectF bounds = new RectF(x, y, x + w, y + h);
    p.setStyle(Paint.Style.FILL);
    p.setColor(Color.WHITE);
    p.setShader(
        new LinearGradient(
            x,
            y,
            x,
            y + h,
            selected ? 0xFFFFFFFF : 0xBA030912,
            selected ? 0xFFF4F6F9 : 0xB0030810,
            Shader.TileMode.CLAMP));
    c.drawRoundRect(bounds, radius, radius, p);
    p.setShader(null);
    if (selected) {
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1f);
      p.setColor(0xAFFFFFFF);
      c.drawRoundRect(bounds, radius, radius, p);
    }
    p.setStyle(Paint.Style.FILL);
    drawBrandMark(c, tile, x, y, w, h, selected ? 0xFF172434 : 0xFFF8FAFC, selected);
  }

  Bitmap hotstarMask;
  final android.graphics.ColorMatrixColorFilter nativeMonochrome =
      new android.graphics.ColorMatrixColorFilter(
          new float[] {
            .15975f, .53625f, .054f, 0f, 64f,
            .15975f, .53625f, .054f, 0f, 64f,
            .15975f, .53625f, .054f, 0f, 64f,
            0f, 0f, 0f, 1f, 0f
          });

  public void drawBrandMark(
      Canvas c, AppTile tile, float x, float y, float w, float h, int color, boolean selected) {
    String name = tile.label.toLowerCase(Locale.US);
    String key = tile.key.toLowerCase(Locale.US);
    if (key.contains("graymatrix") || name.contains("zee")) {
      drawZee5Logo(c, x, y, w, h, selected);
      return;
    }
    if (name.contains("youtube") && !key.contains("tvmusic") && !name.contains("youtube music")) {
      drawYouTubeLogo(c, x, y, w, h, selected);
      return;
    }
    if (key.contains("spotify")) {
      drawSpotifyLogo(c, x, y, w, h, selected);
      return;
    }
    if (key.contains("sonyliv")) {
      drawSonyLivLogo(c, x, y, w, h, selected);
      return;
    }
    if (key.contains("tvmusic") || name.contains("youtube music")) {
      drawYouTubeMusicLogo(c, x, y, w, h, selected);
      return;
    }
    int id = 0;
    if (name.contains("netflix")) id = R.drawable.brand_netflix;
    else if (name.contains("apple tv")) id = R.drawable.brand_apple_tv;
    else if (name.contains("prime")) id = R.drawable.brand_prime;
    else if (key.contains("formulaone") || name.equals("f1 tv")) id = R.drawable.brand_f1_tv;
    else if (key.contains("xstream")) id = R.drawable.brand_xstream;
    else if (key.contains("tvmusic") || name.contains("youtube music"))
      id = R.drawable.brand_youtube_music;
    else if (name.contains("youtube")) id = R.drawable.brand_youtube;
    if (id != 0) {
      Drawable mark = brandMarks.get(id);
      if (mark == null) {
        mark = getResources().getDrawable(id, null).mutate();
        brandMarks.put(id, mark);
      }
      int selectedColor =
          name.contains("netflix")
              ? 0xFFE50914
              : name.contains("prime")
                  ? 0xFF00A8E1
                  : key.contains("formulaone") || name.equals("f1 tv")
                      ? 0xFFE10600
                      : key.contains("xstream") ? 0xFFE31E24 : 0xFF111111;
      mark.setTint(selected ? selectedColor : Color.WHITE);
      float mw = w * .64f, mh = h * .42f;
      drawIconFit(c, mark, x + (w - mw) / 2f, y + (h - mh) / 2f, mw, mh);
      return;
    }
    if (name.contains("hotstar")) {
      drawHotstarLogo(c, x, y, w, h, selected);
      return;
    }
    drawNativeAppLabel(c, tile, x, y, w, h, color, selected);
  }

  void drawNativeAppLabel(
      Canvas c, AppTile tile, float x, float y, float w, float h, int color, boolean selected) {
    float font = h * (homeCategory == 2 && !panel ? .32f : .235f);
    float iconSize = h * .45f, gap = h * .094f;
    float iconWidth = tile.icon == null ? 0f : iconSize + gap;
    float available = w - h * .30f - iconWidth;
    p.setShader(null);
    p.setColorFilter(null);
    p.setStyle(Paint.Style.FILL);
    p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
    p.setTextSize(font);
    String label = tile.label.trim();
    String[] lines = {label};
    float measured = p.measureText(label);
    if (measured > available) {
      if (measured * .88f <= available) {
        font *= available / measured;
      } else {
        // Find the most balanced two-line split without dropping any words.
        int split = -1;
        float best = Float.MAX_VALUE;
        for (int i = 1; i < label.length() - 1; i++) {
          if (!Character.isWhitespace(label.charAt(i))) continue;
          float width =
              Math.max(
                  p.measureText(label.substring(0, i).trim()),
                  p.measureText(label.substring(i + 1).trim()));
          if (width < best) {
            best = width;
            split = i;
          }
        }
        if (split < 0) {
          for (int i = 1; i < label.length(); i++) {
            float width =
                Math.max(p.measureText(label.substring(0, i)), p.measureText(label.substring(i)));
            if (width < best) {
              best = width;
              split = i;
            }
          }
        }
        if (split > 0) {
          lines = new String[] {label.substring(0, split).trim(), label.substring(split).trim()};
          font *= Math.min(1f, available / best);
        }
      }
    }
    p.setTextSize(font);
    float textWidth = 0f;
    for (String line : lines) textWidth = Math.max(textWidth, p.measureText(line));
    float left = x + (w - iconWidth - textWidth) / 2f;
    if (tile.icon != null) {
      // Native icons retain their original colors on focus, matching brand wordmarks.
      tile.icon.setTintList(null);
      tile.icon.setColorFilter(selected ? null : nativeMonochrome);
      drawIconFit(c, tile.icon, left, y + (h - iconSize) / 2f, iconSize, iconSize);
      tile.icon.clearColorFilter();
    }
    p.setShader(null);
    p.setColorFilter(null);
    p.setColor(color);
    float lineHeight = (p.descent() - p.ascent()) * 1.02f;
    float baseline = y + (h - lines.length * lineHeight) / 2f - p.ascent();
    for (String line : lines) {
      c.drawText(line, left + iconWidth, baseline, p);
      baseline += lineHeight;
    }
  }

  void drawZee5Logo(Canvas c, float x, float y, float w, float h, boolean selected) {
    float height = h * .59f, width = height * (226.8f / 187.4f);
    drawBrandAsset(
        c,
        R.drawable.brand_zee5,
        x + (w - width) / 2f,
        y + (h - height) / 2f,
        width,
        height,
        selected ? null : Integer.valueOf(Color.WHITE));
  }

  void drawYouTubeLogo(Canvas c, float x, float y, float w, float h, boolean selected) {
    float mw = w * .70f, mh = h * .43f;
    drawBrandAsset(
        c,
        selected ? R.drawable.brand_youtube_color : R.drawable.brand_youtube,
        x + (w - mw) / 2f,
        y + (h - mh) / 2f,
        mw,
        mh,
        selected ? null : Integer.valueOf(Color.WHITE));
  }

  void drawBrandAsset(Canvas c, int id, float x, float y, float w, float h, Integer tint) {
    Drawable mark = brandMarks.get(id);
    if (mark == null) {
      mark = getResources().getDrawable(id, null).mutate();
      brandMarks.put(id, mark);
    }
    mark.setTintList(null);
    if (tint != null) mark.setTint(tint);
    drawIconFit(c, mark, x, y, w, h);
  }

  void drawBrandVector(Canvas c, int id, float x, float y, float w, float h, int color) {
    Drawable d = brandMarks.get(id);
    if (d == null) {
      d = getResources().getDrawable(id, null).mutate();
      brandMarks.put(id, d);
    }
    d.setTint(color);
    drawIconFit(c, d, x, y, w, h);
  }

  void drawSpotifyLogo(Canvas c, float x, float y, float w, float h, boolean selected) {
    float side = h * .40f, font = h * .30f;
    p.setTextSize(font);
    p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
    float total = side + 9f + p.measureText("Spotify"), left = x + (w - total) / 2f;
    drawBrandVector(
        c,
        R.drawable.brand_spotify,
        left,
        y + (h - side) / 2f,
        side,
        side,
        selected ? 0xFF1DB954 : Color.WHITE);
    p.setColor(selected ? 0xFF111111 : Color.WHITE);
    c.drawText("Spotify", left + side + 9f, y + (h - p.ascent() - p.descent()) / 2f, p);
  }

  void drawSonyLivLogo(Canvas c, float x, float y, float w, float h, boolean selected) {
    float total = w * .62f, left = x + (w - total) / 2f;
    int color = selected ? 0xFF111111 : Color.WHITE;
    drawBrandVector(c, R.drawable.brand_sony, left, y + h * .36f, total * .56f, h * .28f, color);
    drawBrandAsset(
        c,
        R.drawable.brand_liv,
        left + total * .60f,
        y + h * .27f,
        total * .40f,
        h * .46f,
        selected ? null : Integer.valueOf(Color.WHITE));
  }

  void drawYouTubeMusicLogo(Canvas c, float x, float y, float w, float h, boolean selected) {
    float textH = h * .32f, side = h * .40f, gap = h * .08f;
    float textW = textH * 5.4f, total = side + gap + textW;
    float fit = Math.min(1f, w * .78f / total);
    textH *= fit;
    side *= fit;
    gap *= fit;
    textW *= fit;
    total = side + gap + textW;
    float left = x + (w - total) / 2f;
    drawBrandVector(
        c,
        R.drawable.brand_youtube_music_icon,
        left,
        y + (h - side) / 2f,
        side,
        side,
        selected ? 0xFFFF0033 : Color.WHITE);
    drawBrandVector(
        c,
        R.drawable.brand_youtube_music_text,
        left + side + gap,
        y + (h - textH) / 2f,
        textW,
        textH,
        selected ? 0xFF111111 : Color.WHITE);
  }

  void drawHotstarLogo(Canvas c, float x, float y, float w, float h, boolean selected) {
    if (hotstarMask == null) {
      Bitmap source =
          android.graphics.BitmapFactory.decodeResource(
              getResources(), R.drawable.brand_hotstar_stacked);
      int sw = source.getWidth(), sh = source.getHeight();
      int[] pixels = new int[sw * sh];
      source.getPixels(pixels, 0, sw, 0, 0, sw, sh);
      for (int i = 0; i < pixels.length; i++) {
        int v = pixels[i];
        int luminance = (Color.red(v) + Color.green(v) + Color.blue(v)) / 3;
        int alpha = Color.alpha(v) * (255 - luminance) / 255;
        pixels[i] = (alpha << 24) | 0x00FFFFFF;
      }
      hotstarMask = Bitmap.createBitmap(pixels, sw, sh, Bitmap.Config.ARGB_8888);
    }
    float totalW = w * .72f, starW = totalW * .24f, wordW = totalW * .72f;
    float starH = starW * .95f, wordH = wordW / 5.5f, left = x + (w - totalW) / 2f;
    int sw = hotstarMask.getWidth(), sh = hotstarMask.getHeight();
    p.setShader(null);
    p.setColor(Color.WHITE);
    int color = selected ? 0xFF111111 : Color.WHITE;
    RectF starBounds = new RectF(left, y + (h - starH) / 2f, left + starW, y + (h + starH) / 2f);
    int starLayer = c.saveLayer(starBounds, null);
    p.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
    c.drawBitmap(
        hotstarMask,
        new android.graphics.Rect(
            (int) (sw * .30f), (int) (sh * .23f), (int) (sw * .70f), (int) (sh * .58f)),
        starBounds,
        p);
    p.setColorFilter(null);
    if (selected) {
      p.setColor(Color.WHITE);
      p.setShader(
          new LinearGradient(
              starBounds.left,
              starBounds.bottom,
              starBounds.right,
              starBounds.top,
              new int[] {0xFF0569FF, 0xFF8251E8, 0xFFFF59C8},
              null,
              Shader.TileMode.CLAMP));
      p.setXfermode(new android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
      c.drawRect(starBounds, p);
      p.setXfermode(null);
      p.setShader(null);
    }
    c.restoreToCount(starLayer);
    p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    c.drawBitmap(
        hotstarMask,
        new android.graphics.Rect(
            (int) (sw * .035f), (int) (sh * .59f), (int) (sw * .96f), (int) (sh * .755f)),
        new RectF(left + totalW - wordW, y + (h - wordH) / 2f, left + totalW, y + (h + wordH) / 2f),
        p);
    p.setColorFilter(null);
  }

  void drawAppsPanel(Canvas c, float w, float h) {
    if (panelCategory == 1) {
      drawAppsCategoryGrid(c, w, h);
      return;
    }
    p.setStyle(Paint.Style.FILL);
    p.setColor(Color.WHITE);
    p.setShader(new LinearGradient(0, 160, 0, h, 0x08030A14, 0x38030A14, Shader.TileMode.CLAMP));
    c.drawRect(0, 150, w, h, p);
    p.setShader(null);
    text(c, "All Apps", 64, 175, 31, white);
    text(c, grid.size() + " APPS  ·  HOLD OK FOR OPTIONS", 66, 202, 14, 0xD8E8EDF4);
    float gx = 64f, gap = 18f;
    float gw = (w - gx * 2f - gap * (gridColumns - 1)) / gridColumns;
    float gh = Math.min(112f, gw * 0.43f);
    float rowStride = gh + 22f;
    float sectionGap = 16f;
    float[] tops = new float[appRows.size()];
    float[] headings = new float[appRows.size()];
    float y = 244f;
    String previousSection = "";
    for (int r = 0; r < appRows.size(); r++) {
      String section = appRowSections.get(r);
      if (!section.equals(previousSection)) {
        y += sectionGap;
        headings[r] = y;
        y += 26f;
        previousSection = section;
      }
      tops[r] = y;
      y += rowStride;
    }
    float viewportTop = 230f, viewportBottom = area == 4 ? h * .925f - 70f : h - 54f;
    int selectedRow = area == 1 ? appRowForIndex(index) : -1;
    if (selectedRow >= 0) {
      float top = tops[selectedRow] - gridScrollY;
      float target = gridScrollY;
      if (top + gh > viewportBottom - 12f) target += top + gh - (viewportBottom - 12f);
      else if (top < viewportTop + 20f) target -= viewportTop + 20f - top;
      animateGridScrollTo(Math.max(0f, Math.min(Math.max(0f, y - viewportBottom), target)));
    }
    int clipSave = c.save();
    c.clipRect(40f, viewportTop, w - 40f, viewportBottom);
    previousSection = "";
    for (int r = 0; r < appRows.size(); r++) {
      String section = appRowSections.get(r);
      float rowY = tops[r] - gridScrollY;
      if (!section.equals(previousSection)) {
        float baseline = headings[r] - gridScrollY;
        text(c, section, gx, baseline, 17f, 0xE8F1F5F8);
        int count = 0;
        for (AppTile tile : grid) if (section.equals(appSectionTitle(tile))) count++;
        p.setTextSize(13f);
        float labelW = p.measureText(section);
        rounded(
            c,
            gx + labelW + 12f,
            baseline - 17f,
            gx + labelW + 43f,
            baseline + 4f,
            10f,
            0x663D526A);
        text(c, String.valueOf(count), gx + labelW + 21f, baseline - 2f, 12f, 0xEAF4F7FA);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0x557D91A8);
        c.drawLine(gx + labelW + 52f, baseline - 5f, w - gx, baseline - 5f, p);
        p.setStyle(Paint.Style.FILL);
        previousSection = section;
      }
      List<Integer> row = appRows.get(r);
      for (int col = 0; col < row.size(); col++) {
        int itemIndex = row.get(col);
        if (area == 1 && index == itemIndex) continue;
        float x = gx + col * (gw + gap);
        drawGlassAppPill(c, grid.get(itemIndex), x, rowY, gw, gh, false);
      }
      if (area == 1 && row.contains(index)) {
        int col = row.indexOf(index);
        drawGlassHoveredPill(c, grid.get(index), gx + col * (gw + gap), rowY, gw, gh);
      }
    }
    c.restoreToCount(clipSave);
    p.setColor(Color.WHITE);
    p.setShader(
        new LinearGradient(
            0, viewportTop, 0, viewportTop + 28f, 0x66030A14, 0x00030A14, Shader.TileMode.CLAMP));
    c.drawRect(40f, viewportTop, w - 40f, viewportTop + 28f, p);
    p.setShader(null);
    p.setColor(Color.WHITE);
    p.setShader(
        new LinearGradient(
            0,
            viewportBottom - 28f,
            0,
            viewportBottom,
            0x00030A14,
            0x76030A14,
            Shader.TileMode.CLAMP));
    c.drawRect(40f, viewportBottom - 28f, w - 40f, viewportBottom, p);
    p.setShader(null);
    if (area == 1 && !grid.isEmpty()) text(c, grid.get(index).label, gx, h - 20f, 20, white);
    if (moveMode) text(c, "◀ ▶  MOVE APP   ·   OK  DONE", w - 350, h - 20f, 16, 0xD8E6E8F0);
  }

  void drawAppsCategoryGrid(Canvas c, float w, float h) {
    float gx = 110f, gap = 24f;
    float gw = (w - 2f * gx - gap * 5f) / 6f;
    float gh = Math.min(128f, gw * .53f), stride = gh + 26f;
    float gy = appRowCenter(h) - gh / 2f - stride,
        top = gy - stride - 26f,
        bottom = gy + stride + gh + 20f;
    int selectedRow = Math.max(0, appRowForIndex(index));
    // Keep two rows in view; advancing beyond them shifts the entire grid by one row.
    if (panel) animateGridScrollTo(Math.max(0, selectedRow - 1) * stride);
    int save = c.saveLayer(60f, top, w - 60f, bottom, null);
    c.clipRect(60f, top, w - 60f, bottom);
    for (int r = 0; r < appRows.size(); r++) {
      float y = gy + r * stride - gridScrollY;
      if (y + gh < top || y > bottom) continue;
      List<Integer> row = appRows.get(r);
      for (int col = 0; col < row.size(); col++) {
        int item = row.get(col);
        float x = gx + col * (gw + gap);
        if (area == 1 && item == index) drawGlassHoveredPill(c, grid.get(item), x, y, gw, gh);
        else drawGlassAppPill(c, grid.get(item), x, y, gw, gh, false);
      }
    }
    // Broad feathering hides entering/leaving edges rather than cutting a visible line.
    p.setColor(Color.WHITE);
    p.setShader(
        new LinearGradient(
            0,
            top,
            0,
            gy - 12f,
            new int[] {0x00FFFFFF, 0x40FFFFFF, 0xA0FFFFFF, Color.WHITE},
            new float[] {0f, .32f, .70f, 1f},
            Shader.TileMode.CLAMP));
    p.setXfermode(new android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    c.drawRect(60f, top, w - 60f, bottom, p);
    p.setXfermode(null);
    p.setShader(null);
    p.setColor(Color.WHITE);
    p.setShader(
        new LinearGradient(
            0, bottom - 12f, 0, bottom, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP));
    p.setXfermode(new android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    c.drawRect(60f, top, w - 60f, bottom, p);
    p.setXfermode(null);
    p.setShader(null);
    p.setColor(Color.WHITE);
    c.restoreToCount(save);
  }

  void drawInputs(Canvas c, float w) {
    float l = w - 510, t = 99, r = w - 56, b = t + 22 + Math.max(1, tvInputs.size()) * 48;
    rounded(c, l, t, r, b, 26, 0xE3060912);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1);
    p.setColor(0x65FFFFFF);
    c.drawRoundRect(new RectF(l, t, r, b), 26, 26, p);
    if (tvInputs.isEmpty()) text(c, "No TV inputs found", l + 28, t + 51, 19, white);
    for (int i = 0; i < tvInputs.size(); i++) {
      float rowTop = t + 10 + i * 48;
      if (i == inputIndex) rounded(c, l + 8, rowTop, r - 8, rowTop + 44, 18, 0xE96C788A);
      text(c, inputLabel(tvInputs.get(i)), l + 28, rowTop + 29, 19, white);
    }
  }

  void refreshInputs() {
    tvInputs.clear();
    TvInputManager manager =
        (TvInputManager) getContext().getSystemService(Context.TV_INPUT_SERVICE);
    if (manager == null) return;
    boolean tunerAdded = false;
    Set<String> labels = new HashSet<>();
    for (TvInputInfo info : manager.getTvInputList()) {
      if (!info.getServiceInfo().packageName.startsWith("com.sony.dtv.tvinput")) continue;
      String label = String.valueOf(info.loadLabel(getContext()));
      if (label.toLowerCase(Locale.US).contains("xstream")) continue;
      if (info.getType() == TvInputInfo.TYPE_TUNER) {
        if (tunerAdded || !info.getId().contains("DvbTerrCable")) continue;
        tunerAdded = true;
      } else if (info.getType() != TvInputInfo.TYPE_HDMI
          && info.getType() != TvInputInfo.TYPE_COMPOSITE
          && info.getType() != TvInputInfo.TYPE_COMPONENT) continue;
      if (labels.add(inputLabel(info).toLowerCase(Locale.US))) tvInputs.add(info);
    }
    Collections.sort(tvInputs, Comparator.comparing(a -> inputLabel(a)));
    inputIndex = Math.min(inputIndex, Math.max(0, tvInputs.size() - 1));
  }

  String inputLabel(TvInputInfo info) {
    return info.getType() == TvInputInfo.TYPE_TUNER
        ? "Live TV"
        : String.valueOf(info.loadLabel(getContext()));
  }

  void openSelectedInput() {
    if (tvInputs.isEmpty()) return;
    TvInputInfo selected = tvInputs.get(inputIndex);
    if (selected.getType() == TvInputInfo.TYPE_TUNER) {
      Intent tv = pm.getLeanbackLaunchIntentForPackage("com.sony.dtv.tvx");
      if (tv != null) getContext().startActivity(tv);
      inputs = false;
      return;
    }
    Intent intent =
        new Intent(
            Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(selected.getId()));
    intent.setPackage("com.sony.dtv.tvx");
    try {
      getContext().startActivity(intent);
    } catch (Exception ignored) {
      Intent sony = pm.getLeanbackLaunchIntentForPackage("com.sony.dtv.tvx");
      if (sony != null) getContext().startActivity(sony);
    }
    inputs = false;
  }

  void drawContextMenu(Canvas c, float w, float h) {
    if (area != 1 || grid.isEmpty()) return;
    AppTile tile = grid.get(Math.min(index, grid.size() - 1));
    float l = w - 650, t = h - 460, r = w - 64, b = h - 54;
    rounded(c, l, t, r, b, 28, 0xF00A0E18);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.2f);
    p.setColor(0x66FFFFFF);
    c.drawRoundRect(new RectF(l, t, r, b), 28, 28, p);
    p.setStyle(Paint.Style.FILL);
    text(c, tile.label, l + 34, t + 48, 28, white);
    text(c, "APP OPTIONS", l + 34, t + 78, 13, 0xB8C9D0DE);
    String[] actions = {
      "Move",
      systemApp(tile.key)
          ? (updatedSystemApp(tile.key) ? "Uninstall updates" : "Disable in Settings")
          : "Uninstall",
      "App settings"
    };
    for (int i = 0; i < actions.length; i++) {
      float y = t + 132 + i * 60;
      if (i == contextIndex) rounded(c, l + 250, y - 32, r - 24, y + 18, 18, 0xEEF5F6FA);
      text(c, actions[i], l + 278, y, 20, i == contextIndex ? 0xFF18202D : white);
    }
    text(c, "▲ ▼  SELECT     OK  CONFIRM     BACK  CLOSE", l + 34, b - 24, 13, 0xB8C9D0DE);
  }

  void drawSystemInfo(Canvas c, float w, float h) {
    float l = w * 0.25f, r = w * 0.75f, t = h * 0.34f, b = h * 0.66f;
    rounded(c, l, t, r, b, 26, 0xF40A0E18);
    text(c, systemLabel, l + 32, t + 52, 28, white);
    text(c, "This app is part of the TV's system software.", l + 32, t + 106, 19, white);
    text(
        c,
        updatedSystemApp
            ? "Settings can remove its updates."
            : "Settings may allow you to disable it.",
        l + 32,
        t + 143,
        19,
        0xD8CFD5E2);
    text(c, "OK  OPEN APP SETTINGS       BACK  CANCEL", l + 32, b - 28, 16, 0xD8CFD5E2);
  }

  String packageFor(String key) {
    return "LIVE_TV".equals(key) ? "com.sony.dtv.tvx" : key;
  }

  int appFlags(String key) {
    try {
      return pm.getApplicationInfo(packageFor(key), PackageManager.MATCH_ALL).flags;
    } catch (Exception ignored) {
      return 0;
    }
  }

  boolean systemApp(String key) {
    return (appFlags(key) & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
        != 0;
  }

  boolean updatedSystemApp(String key) {
    return (appFlags(key) & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
  }

  void animateGridScrollTo(float target) {
    if (Math.abs(gridScrollTargetY - target) < 0.5f) return;
    gridScrollTargetY = target;
    if (gridScrollAnimator != null) gridScrollAnimator.cancel();
    float start = gridScrollY;
    gridScrollAnimator = ValueAnimator.ofFloat(start, target);
    gridScrollAnimator.setDuration(260);
    gridScrollAnimator.setInterpolator(new DecelerateInterpolator(1.2f));
    gridScrollAnimator.addUpdateListener(
        a -> {
          gridScrollY = (Float) a.getAnimatedValue();
          invalidate();
        });
    gridScrollAnimator.start();
  }

  void animateFocusReveal() {
    if ((area != 0 && area != 1)
        || (area == 0 && dock.isEmpty())
        || (area == 1 && grid.isEmpty())) {
      lastFocusArea = -1;
      return;
    }
    if (lastFocusArea == area && lastFocusIndex == index) return;
    lastFocusArea = area;
    lastFocusIndex = index;
    if (focusRevealAnimator != null) focusRevealAnimator.cancel();
    focusReveal = 0.35f;
    focusRevealAnimator = ValueAnimator.ofFloat(0.35f, 1f);
    focusRevealAnimator.setDuration(160);
    focusRevealAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
    focusRevealAnimator.addUpdateListener(
        a -> {
          focusReveal = (Float) a.getAnimatedValue();
          invalidate();
        });
    focusRevealAnimator.start();
  }

  void drawHoveredCard(Canvas c, AppTile tile, float baseX, float baseY, float baseW, float baseH) {
    // Match the approved six-app dock hover, keeping the same pixel growth
    // as tile width changes in the dock or Apps and Inputs.
    float sixAppWidth = (getWidth() - 128f - 5f * 24f) / 6f;
    float scale = 1f + Math.min(0.14f, 0.10f * sixAppWidth / baseW) * focusReveal;
    float lift = 6f * focusReveal;
    float w = baseW * scale, h = baseH * scale;
    float x = baseX - (w - baseW) / 2f, y = baseY - (h - baseH) / 2f - lift;
    drawCard(c, tile, x, y, w, h, true);
  }

  void dimCard(Canvas c, float x, float y, float w, float h) {
    float radius = Math.min(22, h * 0.18f);
    rounded(c, x, y, x + w, y + h, radius, 0x78000000);
  }

  void drawCard(Canvas c, AppTile t, float x, float y, float w, float h, boolean focused) {
    float radius = Math.min(22, h * 0.18f);
    Path clip = new Path();
    clip.addRoundRect(new RectF(x, y, x + w, y + h), radius, radius, Path.Direction.CW);
    c.save();
    c.clipPath(clip);
    if (t.banner != null) drawCrop(c, t.banner, x, y, w, h);
    else {
      rounded(c, x, y, x + w, y + h, radius, 0xB82A303A);
      if (t.icon != null) drawIconFit(c, t.icon, x + 16, y + 12, w - 32, h - 24);
    }
    c.restore();
  }

  void drawCrop(Canvas c, Drawable d, float x, float y, float w, float h) {
    int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
    if (iw <= 0 || ih <= 0) {
      d.setBounds((int) x, (int) y, (int) (x + w), (int) (y + h));
      d.draw(c);
      return;
    }
    float scale = Math.max(w / iw, h / ih), dw = iw * scale, dh = ih * scale;
    d.setBounds(
        (int) (x + (w - dw) / 2),
        (int) (y + (h - dh) / 2),
        (int) (x + (w + dw) / 2),
        (int) (y + (h + dh) / 2));
    d.draw(c);
  }

  public void drawIconFit(Canvas c, Drawable d, float x, float y, float w, float h) {
    int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
    if (iw <= 0 || ih <= 0) {
      d.setBounds((int) x, (int) y, (int) (x + w), (int) (y + h));
      d.draw(c);
      return;
    }
    float scale = Math.min(w / iw, h / ih), dw = iw * scale, dh = ih * scale;
    d.setBounds(
        (int) (x + (w - dw) / 2),
        (int) (y + (h - dh) / 2),
        (int) (x + (w + dw) / 2),
        (int) (y + (h + dh) / 2));
    d.draw(c);
  }

  final Runnable longPressAction =
      new Runnable() {
        @Override
        public void run() {
          if (centerHeld && area == 0) {
            rearrangeMode = !rearrangeMode;
            longPressTriggered = true;
            invalidate();
          } else if (centerHeld && area == 1 && !contextMenu && !moveMode && !grid.isEmpty()) {
            contextMenu = true;
            contextIndex = 0;
            menuHoldActive = true;
            longPressTriggered = true;
            invalidate();
          }
        }
      };

  @Override
  public boolean onKeyDown(int key, KeyEvent e) {
    if (homeCategory == 3 && sports.handle(key, e)) return true;
    if (movies.handleKey(key, e)) return true;
    if (systemInfo) {
      if (key == KeyEvent.KEYCODE_BACK) systemInfo = false;
      else if (key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DPAD_CENTER) {
        systemInfo = false;
        try {
          getContext()
              .startActivity(
                  new Intent(
                      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                      Uri.parse("package:" + systemPackage)));
        } catch (Exception ignored) {
        }
      }
      invalidate();
      return true;
    }
    if (inputs) {
      if (key == KeyEvent.KEYCODE_DPAD_UP && !tvInputs.isEmpty())
        inputIndex = (inputIndex + tvInputs.size() - 1) % tvInputs.size();
      else if (key == KeyEvent.KEYCODE_DPAD_DOWN && !tvInputs.isEmpty())
        inputIndex = (inputIndex + 1) % tvInputs.size();
      else if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)
        openSelectedInput();
      else if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_DPAD_LEFT) inputs = false;
      invalidate();
      return true;
    }
    if (contextMenu) {
      if (key == KeyEvent.KEYCODE_DPAD_UP) {
        contextIndex = (contextIndex + 2) % 3;
        menuHoldActive = false;
      } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
        contextIndex = (contextIndex + 1) % 3;
        menuHoldActive = false;
      } else if (key == KeyEvent.KEYCODE_BACK) {
        contextMenu = false;
        menuHoldActive = false;
      } else if (key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DPAD_CENTER) {
        // Ignore the repeat/release of the hold that opened the menu.
        if (!menuHoldActive && e.getRepeatCount() == 0) executeContextAction();
        longPressTriggered = true;
      }
      invalidate();
      return true;
    }
    if (moveMode) {
      if (key == KeyEvent.KEYCODE_DPAD_LEFT) moveGrid(-1);
      else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) moveGrid(1);
      else if (key == KeyEvent.KEYCODE_DPAD_UP) moveGrid(-gridColumns);
      else if (key == KeyEvent.KEYCODE_DPAD_DOWN) moveGrid(gridColumns);
      else if (key == KeyEvent.KEYCODE_BACK
          || key == KeyEvent.KEYCODE_ENTER
          || key == KeyEvent.KEYCODE_DPAD_CENTER) {
        moveMode = false;
        longPressTriggered = true;
      }
      invalidate();
      return true;
    }
    if (key == KeyEvent.KEYCODE_SETTINGS || key == KeyEvent.KEYCODE_MENU) {
      try {
        getContext().startActivity(new Intent(Settings.ACTION_SETTINGS));
      } catch (Exception ignored) {
      }
      return true;
    }
    if (key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DPAD_CENTER) {
      if (area == 0 || area == 1) {
        if (area == 1 && e.getRepeatCount() > 0 && !contextMenu && !moveMode && !grid.isEmpty()) {
          keyHandler.removeCallbacks(longPressAction);
          contextMenu = true;
          contextIndex = 0;
          menuHoldActive = true;
          longPressTriggered = true;
          invalidate();
          return true;
        }
        if (!centerHeld) {
          centerHeld = true;
          longPressTriggered = false;
          keyHandler.postDelayed(longPressAction, area == 1 ? 350 : 550);
        } else if (e.getRepeatCount() > 0 && !longPressTriggered) {
          keyHandler.removeCallbacks(longPressAction);
          rearrangeMode = !rearrangeMode;
          longPressTriggered = true;
          invalidate();
        }
        return true;
      }
      activate();
      return true;
    }
    int n =
        area == 0
            ? Math.min(homeCategory == 2 ? 3 : columns, homeCategoryApps().size())
            : area == 2 ? 4 : area == 3 ? movies.providerCount() : area == 4 ? 6 : grid.size();
    if (area == 0 && rearrangeMode && key == KeyEvent.KEYCODE_DPAD_LEFT) {
      moveDock(-1);
    } else if (area == 0 && rearrangeMode && key == KeyEvent.KEYCODE_DPAD_RIGHT) {
      moveDock(1);
    } else if (area == 0
        && rearrangeMode
        && (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN)) {
      return true;
    } else if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
      if ((area == 0 || area == 3) && n > 0) index = (index + n - 1) % n;
      else if (area == 4) {
        index = nextCategory(index, -1);
        previewCategory(index);
      } else if (area == 1 && n > 0) {
        int row = appRowForIndex(index);
        if (row >= 0) {
          List<Integer> values = appRows.get(row);
          int col = values.indexOf(index);
          index = values.get(col == 0 ? values.size() - 1 : col - 1);
        }
      } else index = Math.max(0, index - 1);
    } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
      if ((area == 0 || area == 3) && n > 0) index = (index + 1) % n;
      else if (area == 4) {
        index = nextCategory(index, 1);
        previewCategory(index);
      } else if (area == 1 && n > 0) {
        int row = appRowForIndex(index);
        if (row >= 0) {
          List<Integer> values = appRows.get(row);
          int col = values.indexOf(index);
          index = values.get(col == values.size() - 1 ? 0 : col + 1);
        }
      } else index = Math.min(Math.max(0, n - 1), index + 1);
    } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
      if (area == 1) {
        moveAppVertically(-1);
      } else if (area == 4) enterCategoryApps();
      else if (area == 0) {
        if (homeCategory == 3) {
          lastDockIndex = index;
          area = 5;
        } else {
          area = movies.providerCount() > 0 ? 3 : 2;
          index = 0;
        }
      } else if (area == 3) {
        if (index >= 6) index -= 6;
        else {
          area = 2;
          index = 0;
        }
      }
    } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
      if (area == 4) enterCategoryApps();
      else if (area == 2) {
        area = homeCategory == 3 ? 5 : movies.providerCount() > 0 ? 3 : 0;
        index = 0;
      } else if (area == 3) {
        if (index + 6 < n) index += 6;
        else {
          area = 0;
          index = 0;
        }
      } else if (area == 0 && !grid.isEmpty()) {
        lastDockIndex = index;
        area = 4;
        index = homeCategory;
      } else if (area == 1) moveAppVertically(1);
    } else if (key == KeyEvent.KEYCODE_BACK) {
      if (inputs) inputs = false;
      else if (rearrangeMode) rearrangeMode = false;
      else if (panel) {
        showAppsPanel(false);
        area = 4;
        index = homeCategory;
      } else movies.dismissFeature();
    }
    invalidate();
    return true;
  }

  @Override
  public boolean onKeyUp(int key, KeyEvent e) {
    if ((key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DPAD_CENTER) && contextMenu) {
      menuHoldActive = false;
    }
    if (key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_DPAD_CENTER) {
      if (centerHeld) {
        centerHeld = false;
        keyHandler.removeCallbacks(longPressAction);
        if (!longPressTriggered) {
          if (rearrangeMode) rearrangeMode = false;
          else activate();
        }
        invalidate();
      }
      return true;
    }
    return super.onKeyUp(key, e);
  }

  void moveDock(int direction) {
    List<AppTile> apps = homeCategoryApps();
    int count = Math.min(homeCategory == 2 ? 3 : columns, apps.size());
    if (count < 2 || index >= count) return;
    int target = (index + direction + count) % count;
    Collections.swap(apps, index, target);
    index = target;
    if (homeCategory == 0) {
      saveMovieDock(apps);
      return;
    }
    categoryCache.clear();
    StringBuilder order = new StringBuilder();
    for (AppTile tile : apps) {
      if (order.length() > 0) order.append(',');
      order.append(tile.key);
    }
    getContext()
        .getSharedPreferences("glass_ui", Context.MODE_PRIVATE)
        .edit()
        .putString("category_order_" + homeCategory, order.toString())
        .apply();
  }

  boolean dockContains(String key) {
    for (AppTile tile : movieDockApps()) if (tile.key.equals(key)) return true;
    return false;
  }

  void saveDockOrder() {
    StringBuilder order = new StringBuilder();
    for (AppTile tile : dock) {
      if (order.length() > 0) order.append(',');
      order.append(tile.key);
    }
    getContext()
        .getSharedPreferences("glass_ui", Context.MODE_PRIVATE)
        .edit()
        .putString("dock_order", order.toString())
        .putBoolean("dock_customized", true)
        .apply();
  }

  void moveGrid(int direction) {
    if (grid.isEmpty()) return;
    List<Integer> visual = new ArrayList<>();
    for (List<Integer> row : appRows) visual.addAll(row);
    int position = visual.indexOf(index), next = position + direction;
    if (position < 0 || next < 0 || next >= visual.size()) return;
    int target = visual.get(next);
    Collections.swap(grid, index, target);
    index = target;
    saveGridOrder();
    buildAppRows();
  }

  void saveGridOrder() {
    categoryCache.clear();
    StringBuilder order = new StringBuilder();
    for (AppTile tile : grid) {
      if (order.length() > 0) order.append(',');
      order.append(tile.key);
    }
    getContext()
        .getSharedPreferences("glass_ui", Context.MODE_PRIVATE)
        .edit()
        .putString("apps_order", order.toString())
        .putInt("apps_order_version", 3)
        .apply();
  }

  void executeContextAction() {
    if (grid.isEmpty()) {
      contextMenu = false;
      return;
    }
    AppTile tile = grid.get(Math.min(index, grid.size() - 1));
    if (contextIndex == 0) {
      contextMenu = false;
      moveMode = true;
      return;
    }
    contextMenu = false;
    String pkg = packageFor(tile.key);
    try {
      Intent target;
      if (contextIndex == 1) {
        if (systemApp(tile.key)) {
          systemInfo = true;
          updatedSystemApp = updatedSystemApp(tile.key);
          systemPackage = pkg;
          systemLabel = tile.label;
          invalidate();
          return;
        }
        target = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
      } else {
        target =
            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
      }
      getContext().startActivity(target);
    } catch (Exception ignored) {
    }
  }

  void enterCategoryApps() {
    if (homeCategory == 1 || homeCategory == 5) {
      showAppsPanel(true);
      area = 1;
      index = 0;
    } else {
      area = 0;
      index = Math.min(Math.max(0, lastDockIndex), Math.max(0, homeCategoryApps().size() - 1));
    }
  }

  void activate() {
    if (area == 4) {
      homeCategory = index;
      if (homeCategory == 1 || homeCategory == 5) {
        showAppsPanel(true);
        area = 1;
        index = 0;
      } else {
        area = 0;
        index = 0;
      }
      invalidate();
      return;
    }
    if (area == 2) {
      if (index == 0) movies.openSearch();
      else if (index == 1) {
        refreshInputs();
        inputs = !inputs;
      } else if (index == 2)
        try {
          getContext().startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception ignored) {
        }
      else if (index == 3)
        try {
          getContext().startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception ignored) {
        }
      invalidate();
      return;
    }
    if (area == 3) {
      if (index < movies.providerCount()) movies.activateProvider(index);
      return;
    }
    if (area == 0 && index < Math.min(columns, homeCategoryApps().size()))
      launch(homeCategoryApps().get(index));
    else if (area == 1 && index < grid.size()) launch(grid.get(index));
  }

  @Override
  public MovieSearchController movieSearch() {
    return movies;
  }

  @Override
  public android.view.View searchView() {
    return this;
  }

  @Override
  public void prepareForSearch() {
    if (panelAnimator != null) panelAnimator.cancel();
    panel = false;
    panelProgress = 0f;
    inputs = false;
  }

  @Override
  public void setSearchFocus(boolean home) {
    area = home ? 0 : 2;
    index = 0;
  }

  @Override
  public boolean isMovieProviderFocused() {
    return area == 3;
  }

  @Override
  public int movieProviderIndex() {
    return index;
  }

  @Override
  public void clearMovieProviderFocus() {
    if (area == 3) {
      area = 0;
      index = 0;
    }
  }

  @Override
  public boolean handleVolumeKeyDown(KeyEvent e) {
    return volume.handleVolumeKeyDown(e);
  }

  @Override
  public boolean handleVolumeKeyUp() {
    return volume.handleVolumeKeyUp();
  }

  @Override
  public void stopVolumeRepeating() {
    volume.stopVolumeRepeating();
  }

  public void launch(AppTile tile) {
    appLauncher.launch(tile);
  }

  @Override
  protected void resumeFeatures() {
    deviceStatus.start();
    removeCallbacks(clockTick);
    post(clockTick);
    removeCallbacks(wallpaperRotation);
    postDelayed(wallpaperRotation, 120000);
  }

  @Override
  protected void suspendFeatures() {
    deviceStatus.stop();
    stopVolumeRepeating();
    removeCallbacks(clockTick);
    removeCallbacks(wallpaperRotation);
    if (movies.isSearchOpen()) movies.hideSearch(true);
    previews.select("");
    sports.setActive(false);
  }

  @Override
  public void dispose() {
    onBackground();
    movies.dispose();
    deviceStatus.dispose();
    keyHandler.removeCallbacksAndMessages(null);
    if (panelAnimator != null) panelAnimator.cancel();
    if (gridScrollAnimator != null) gridScrollAnimator.cancel();
    if (focusRevealAnimator != null) focusRevealAnimator.cancel();
    if (wallpaperAnimator != null) wallpaperAnimator.cancel();
    previews.dispose();
    sports.dispose();
  }

  @Override
  public android.view.View sportsView() {
    return this;
  }

  @Override
  public int focusArea() {
    return area;
  }

  @Override
  public int focusIndex() {
    return index;
  }

  @Override
  public void setFocusArea(int value) {
    area = value;
  }

  @Override
  public void setFocusIndex(int value) {
    index = value;
  }

  @Override
  public int lastDockIndex() {
    return lastDockIndex;
  }
}
