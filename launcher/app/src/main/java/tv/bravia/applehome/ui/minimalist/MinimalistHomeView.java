package tv.bravia.applehome.ui.minimalist;

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
import android.graphics.RadialGradient;
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

/** Minimalist presentation, focus and app-order policy. Shared features live outside this view. */
public final class MinimalistHomeView extends LauncherSurface {

  final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  final PackageManager pm;
  final MovieSearchController movies;
  final List<AppTile> dock = new ArrayList<>();
  final List<AppTile> grid = new ArrayList<>();
  final Set<String> configuredKeys = new HashSet<>();
  final Map<String, Integer> ambientColors = new HashMap<>();
  final Bitmap[] wallpapers;
  int wallpaperIndex = 0;
  int previousWallpaperIndex = 0;
  float wallpaperBlend = 1f;
  ValueAnimator wallpaperAnimator;
  int area = 0, index = 0, columns = 8, gridColumns = 6;
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

  public MinimalistHomeView(Context c) {
    super(c);
    setFocusable(true);
    pm = c.getPackageManager();
    volume = new VolumeController(c);
    appLauncher = new AppLauncher(c);
    deviceStatus = new DeviceStatusController(c, this);
    movies = new MovieSearchController(this);
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

  void showAppsPanel(boolean show) {
    if (panel == show && panelProgress == (show ? 1f : 0f)) return;
    if (show) lastGridIndex = 0;
    else if (area == 1 && !grid.isEmpty()) lastGridIndex = index;
    panel = show;
    if (panelAnimator != null) panelAnimator.cancel();
    panelAnimator = ValueAnimator.ofFloat(panelProgress, show ? 1f : 0f);
    panelAnimator.setDuration(280);
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
              String.valueOf(r.loadLabel(pm)),
              loadBanner(r),
              r.loadIcon(pm),
              r.activityInfo.name));
    }
    grid.addAll(configured);
    grid.addAll(all);
    android.content.SharedPreferences prefs =
        getContext().getSharedPreferences("minimalist_ui", Context.MODE_PRIVATE);
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
    Collections.sort(grid, Comparator.comparing(a -> a.label.toLowerCase(Locale.US)));
    String appOrder =
        getContext()
            .getSharedPreferences("minimalist_ui", Context.MODE_PRIVATE)
            .getString("apps_order", "");
    int appOrderVersion =
        getContext()
            .getSharedPreferences("minimalist_ui", Context.MODE_PRIVATE)
            .getInt("apps_order_version", 0);
    if (appOrderVersion == 2 && !appOrder.isEmpty()) {
      List<AppTile> ordered = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (String key : appOrder.split(",")) {
        for (AppTile tile : grid) if (tile.key.equals(key) && seen.add(key)) ordered.add(tile);
      }
      for (AppTile tile : grid) if (seen.add(tile.key)) ordered.add(tile);
      grid.clear();
      grid.addAll(ordered);
    }
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
    else if (area == 0 && index >= 0 && index < dock.size()) selectedKey = dock.get(index).key;
    int previousIndex = index;
    load();
    if (area == 1) {
      if (grid.isEmpty()) {
        showAppsPanel(false);
        area = 0;
        index = 0;
      } else index = restoredIndex(grid, selectedKey, previousIndex);
    } else if (area == 0) {
      index = dock.isEmpty() ? 0 : restoredIndex(dock, selectedKey, previousIndex);
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

    AppTile focused =
        area == 0 && !dock.isEmpty()
            ? dock.get(Math.min(index, dock.size() - 1))
            : area == 1 && !grid.isEmpty() ? grid.get(Math.min(index, grid.size() - 1)) : null;
    animateFocusReveal();
    if (panelAnimator != null && panelAnimator.isRunning() && !dock.isEmpty() && !grid.isEmpty()) {
      AppTile dockTile = dock.get(Math.min(area == 0 ? index : lastDockIndex, dock.size() - 1));
      AppTile gridTile = grid.get(Math.min(area == 1 ? index : lastGridIndex, grid.size() - 1));
      drawAmbientColor(
          c, w, h, blendColors(ambientColor(dockTile), ambientColor(gridTile), panelProgress));
    } else drawAmbient(c, w, h, focused);
    drawHeader(c, w, h);
    if (!movies.isSearchOpen() && panelProgress == 0f) movies.drawFeaturedMovie(c, w);

    if (panelProgress < 1f) {
      int save = c.save();
      c.translate(0, 48f * panelProgress);
      float dockScale = 1f - 0.025f * panelProgress;
      c.scale(dockScale, dockScale, w / 2f, h * 0.82f);
      c.saveLayerAlpha(0, h * 0.66f, w, h, Math.round(255f * (1f - panelProgress)));
      AppTile dockFocused =
          dock.isEmpty()
              ? null
              : dock.get(Math.min(area == 0 ? index : lastDockIndex, dock.size() - 1));
      drawDock(c, w, h, dockFocused);
      c.restoreToCount(save);
    }
    if (panelProgress > 0f) {
      int save = c.save();
      c.translate(0, 96f * (1f - panelProgress));
      float panelScale = 0.97f + 0.03f * panelProgress;
      c.scale(panelScale, panelScale, w / 2f, h * 0.58f);
      c.saveLayerAlpha(40, 148, w - 40, h, Math.round(255f * panelProgress));
      drawAppsPanel(c, w, h);
      c.restoreToCount(save);
    }
    if (inputs) drawInputs(c, w);
    if (movies.isSearchOpen()) movies.drawSearchPanel(c, w, h);
    if (contextMenu) drawContextMenu(c, w, h);
    if (systemInfo) drawSystemInfo(c, w, h);
  }

  void drawWallpaper(Canvas c, float w, float h) {
    c.drawColor(Color.rgb(6, 9, 18));
    drawWallpaperBitmap(c, wallpapers[previousWallpaperIndex], w, h, 255);
    if (wallpaperBlend < 1f || previousWallpaperIndex != wallpaperIndex) {
      drawWallpaperBitmap(
          c,
          wallpapers[wallpaperIndex],
          w,
          h,
          Math.max(0, Math.min(255, (int) (255f * wallpaperBlend))));
    }
    p.setAlpha(255);
    p.setShader(new LinearGradient(0, 0, 0, h, 0x08000000, 0x65000000, Shader.TileMode.CLAMP));
    p.setColor(Color.WHITE);
    c.drawRect(0, 0, w, h, p);
    p.setShader(null);
  }

  void drawWallpaperBitmap(Canvas c, Bitmap wallpaper, float w, float h, int alpha) {
    if (wallpaper == null || alpha <= 0) return;
    float scale = Math.max(w / wallpaper.getWidth(), h / wallpaper.getHeight());
    float dw = wallpaper.getWidth() * scale, dh = wallpaper.getHeight() * scale;
    p.setAlpha(alpha);
    c.drawBitmap(wallpaper, (w - dw) / 2f, (h - dh) / 2f, p);
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
    Date now = new Date();
    int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    String greeting =
        hour < 5
            ? "Good Night"
            : hour < 12
                ? "Good Morning"
                : hour < 17 ? "Good Afternoon" : hour < 21 ? "Good Evening" : "Good Night";
    String weather = temperatureC == null ? "--°C" : temperatureC + "°C";
    text(c, greeting.toUpperCase(Locale.getDefault()) + "  |  " + weather, 64, 45, 18, 0xDBE8EAF0);
    String clock = new SimpleDateFormat("h:mm", Locale.getDefault()).format(now);
    text(c, clock, 60, 119, 68, white);
    float clockWidth = p.measureText(clock);
    text(
        c,
        new SimpleDateFormat("a", Locale.getDefault()).format(now).toUpperCase(Locale.getDefault()),
        69 + clockWidth,
        116,
        18,
        0xE3E6EAF2);
    float dividerX = 113 + clockWidth;
    p.setShader(null);
    p.setColor(0x58E6EAF1);
    p.setStrokeWidth(1.5f);
    c.drawLine(dividerX, 84, dividerX, 121, p);
    String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now);
    text(c, date, dividerX + 22, 111, 18, 0xE4E6EAF2);

    float x = w - 678;
    drawPill(c, x - 84, 52, 72, "", 3, area == 2 && index == 0);
    drawPill(c, x, 52, 180, "Inputs", 0, area == 2 && index == 1);
    drawPill(c, x + 192, 52, 230, wifiName, 1, area == 2 && index == 2);
    drawPill(c, x + 434, 52, 180, "Settings", 2, area == 2 && index == 3);
  }

  void drawPill(Canvas c, float x, float y, float width, String label, int icon, boolean selected) {
    float height = 72;
    p.setStyle(Paint.Style.FILL);
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

  void drawDock(Canvas c, float w, float h, AppTile focused) {
    int visibleCount = Math.min(columns, dock.size());
    if (visibleCount == 0) return;
    int focusedIndex = Math.min(area == 0 ? index : lastDockIndex, visibleCount - 1);
    boolean highlightDock = area == 0 || panel;
    float gap = 24;
    float tileW = Math.min(320f, (w - 128f - gap * (visibleCount - 1)) / visibleCount);
    float start = (w - visibleCount * tileW - gap * (visibleCount - 1)) / 2f;
    float dockTop = h * 0.70f;
    float dockHeight = h - dockTop;
    float tileH = Math.min(tileW * 0.56f, dockHeight * 0.54f);
    float labelY = dockTop + dockHeight * 0.23f;
    float y = Math.min(dockTop + dockHeight * 0.38f, h - dockHeight * 0.10f - tileH);
    if (focused != null) text(c, focused.label, 76, labelY, 42, white);
    if (rearrangeMode)
      text(c, "HOLD OK TO FINISH  ·  ◀ ▶  MOVE", w - 430, labelY + 2, 18, 0xD8E6E8F0);
    for (int i = 0; i < visibleCount; i++) {
      AppTile t = dock.get(i);
      float x = start + i * (tileW + gap);
      if (!highlightDock || i != focusedIndex) {
        drawCard(c, t, x, y, tileW, tileH, false);
        dimCard(c, x, y, tileW, tileH);
      }
    }
    if (highlightDock) {
      float x = start + focusedIndex * (tileW + gap);
      drawHoveredCard(c, dock.get(focusedIndex), x, y, tileW, tileH);
    }
  }

  void drawAppsPanel(Canvas c, float w, float h) {
    rounded(c, 48, 155, w - 48, h - 54, 34, 0xE3060912);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(1.2f);
    p.setColor(0x65FFFFFF);
    c.drawRoundRect(new RectF(48, 155, w - 48, h - 54), 34, 34, p);
    text(c, "Apps and Inputs", 78, 202, 28, white);
    text(c, "DOWN TO BROWSE  ·  OK TO OPEN", w - 392, 201, 15, 0xC8E0E5EF);
    float gx = 78, gy = 247, gap = 20;
    float gw = (w - 156 - gap * (gridColumns - 1)) / gridColumns, gh = 130;
    float rowStride = gh + 20;
    int rowCount = (grid.size() + gridColumns - 1) / gridColumns;
    int firstRow = Math.min(Math.max(0, rowCount - 5), Math.max(0, index / gridColumns - 3));
    animateGridScrollTo(firstRow * rowStride);
    c.save();
    c.clipRect(55, 232, w - 55, h - 90);
    for (int i = 0; i < grid.size(); i++) {
      int row = i / gridColumns;
      int col = i % gridColumns;
      float x = gx + col * (gw + gap), y = gy + row * rowStride - gridScrollY;
      if (y + gh < 232 || y > h - 90) continue;
      if (!(area == 1 && i == Math.min(index, grid.size() - 1))) {
        drawCard(c, grid.get(i), x, y, gw, gh, false);
        dimCard(c, x, y, gw, gh);
      }
    }
    if (area == 1 && !grid.isEmpty()) {
      int selected = Math.min(index, grid.size() - 1);
      float x = gx + (selected % gridColumns) * (gw + gap);
      float y = gy + (selected / gridColumns) * rowStride - gridScrollY;
      drawHoveredCard(c, grid.get(selected), x, y, gw, gh);
    }
    c.restore();
    if (area == 1 && !grid.isEmpty()) text(c, grid.get(index).label, 78, h - 82, 24, white);
    if (moveMode) text(c, "◀ ▶  MOVE APP   ·   OK  DONE", w - 360, h - 82, 17, 0xD8E6E8F0);
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
      "App settings",
      dockContains(tile.key)
          ? "Remove from Dock"
          : dock.size() >= columns ? "Dock full (8)" : "Add to Dock"
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
        contextIndex = (contextIndex + 3) % 4;
        menuHoldActive = false;
      } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
        contextIndex = (contextIndex + 1) % 4;
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
            ? Math.min(columns, dock.size())
            : area == 2 ? 4 : area == 3 ? movies.providerCount() : grid.size();
    if (area == 0 && rearrangeMode && key == KeyEvent.KEYCODE_DPAD_LEFT) {
      moveDock(-1);
    } else if (area == 0 && rearrangeMode && key == KeyEvent.KEYCODE_DPAD_RIGHT) {
      moveDock(1);
    } else if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
      if ((area == 0 || area == 3) && n > 0) index = (index + n - 1) % n;
      else if (area == 1 && n > 0) {
        int rowStart = (index / gridColumns) * gridColumns;
        int rowEnd = Math.min(rowStart + gridColumns, n) - 1;
        index = index == rowStart ? rowEnd : index - 1;
      } else index = Math.max(0, index - 1);
    } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
      if ((area == 0 || area == 3) && n > 0) index = (index + 1) % n;
      else if (area == 1 && n > 0) {
        int rowStart = (index / gridColumns) * gridColumns;
        int rowEnd = Math.min(rowStart + gridColumns, n) - 1;
        index = index == rowEnd ? rowStart : index + 1;
      } else index = Math.min(Math.max(0, n - 1), index + 1);
    } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
      if (area == 1) {
        int row = index / gridColumns;
        if (row == 0) {
          showAppsPanel(false);
          area = 0;
          index = 0;
        } else {
          index = Math.max(0, index - gridColumns);
        }
      } else if (area == 0) {
        area = movies.providerCount() > 0 ? 3 : 2;
        index = 0;
      } else if (area == 3) {
        if (index >= 6) index -= 6;
        else {
          area = 2;
          index = 0;
        }
      }
    } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
      if (area == 2) {
        area = movies.providerCount() > 0 ? 3 : 0;
        index = 0;
      } else if (area == 3) {
        if (index + 6 < n) index += 6;
        else {
          area = 0;
          index = 0;
        }
      } else if (area == 0 && !grid.isEmpty()) {
        lastDockIndex = index;
        showAppsPanel(true);
        area = 1;
        index = 0;
      } else if (area == 1 && index + gridColumns < grid.size()) index += gridColumns;
    } else if (key == KeyEvent.KEYCODE_BACK) {
      if (inputs) inputs = false;
      else if (rearrangeMode) rearrangeMode = false;
      else if (panel) {
        showAppsPanel(false);
        area = 0;
        index = Math.max(0, Math.min(index, Math.min(columns, dock.size()) - 1));
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
    int target = index + direction;
    int count = Math.min(columns, dock.size());
    if (count == 0) return;
    target = (target + count) % count;
    AppTile current = dock.get(index);
    dock.set(index, dock.get(target));
    dock.set(target, current);
    index = target;
    saveDockOrder();
  }

  boolean dockContains(String key) {
    for (AppTile tile : dock) if (tile.key.equals(key)) return true;
    return false;
  }

  void saveDockOrder() {
    StringBuilder order = new StringBuilder();
    for (AppTile tile : dock) {
      if (order.length() > 0) order.append(',');
      order.append(tile.key);
    }
    getContext()
        .getSharedPreferences("minimalist_ui", Context.MODE_PRIVATE)
        .edit()
        .putString("dock_order", order.toString())
        .putBoolean("dock_customized", true)
        .apply();
  }

  void moveGrid(int direction) {
    if (grid.isEmpty()) return;
    int target = index + direction;
    if (target < 0 || target >= grid.size()) return;
    AppTile current = grid.get(index);
    grid.set(index, grid.get(target));
    grid.set(target, current);
    index = target;
    saveGridOrder();
  }

  void saveGridOrder() {
    StringBuilder order = new StringBuilder();
    for (AppTile tile : grid) {
      if (order.length() > 0) order.append(',');
      order.append(tile.key);
    }
    getContext()
        .getSharedPreferences("minimalist_ui", Context.MODE_PRIVATE)
        .edit()
        .putString("apps_order", order.toString())
        .putInt("apps_order_version", 2)
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
    if (contextIndex == 3) {
      if (dockContains(tile.key)) {
        for (int i = dock.size() - 1; i >= 0; i--)
          if (dock.get(i).key.equals(tile.key)) dock.remove(i);
      } else {
        if (dock.size() >= columns) return;
        dock.add(tile);
      }
      saveDockOrder();
      contextMenu = false;
      invalidate();
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

  void activate() {
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
    if (area == 0 && index < Math.min(columns, dock.size())) launch(dock.get(index));
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
  }
}
