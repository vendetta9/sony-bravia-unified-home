package tv.bravia.applehome.search;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads provider-specific artwork and rotates the featured title while its dock app is focused. */
public final class ProviderPreviewController {
  private static final long ROTATE_MS = 5000L;
  private static final String TAG = "BraviaPreview";
  private final android.view.View view;
  private final Context context;
  private final ProviderPreviewCatalog catalog;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newFixedThreadPool(2);
  private final ExecutorService artworkWorker = Executors.newSingleThreadExecutor();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final List<Preview> items = new ArrayList<>();
  private volatile String selectedKey = "";
  private String selectedLabel = "";
  private String displayedLabel = "";
  private String displayedService = "";
  private final Map<String, List<Preview>> catalogs = new LinkedHashMap<>();
  private final Set<String> loading = new HashSet<>();
  private final Map<String, Long> catalogDays = new java.util.HashMap<>();
  private final Map<String, Long> refreshAttempts = new java.util.HashMap<>();
  private final Map<String, Long> frameAttempts = new java.util.HashMap<>();
  private long selectedAt;
  private final Runnable switchLoad =
      () -> {
        if (!this.disposed && !selectedKey.isEmpty()) load(selectedKey, selectedLabel);
      };

  private static long indiaDay(long millis) {
    return (millis + 19800000L) / 86400000L;
  }

  private boolean fresh(String service) {
    Long day = catalogDays.get(service);
    return day != null && day == indiaDay(System.currentTimeMillis());
  }

  private Bitmap previousFrame;
  private Preview previousItem;
  private String previousLabel = "";
  private boolean rotating;
  private volatile boolean disposed;
  private float blend = 1f;
  private ValueAnimator animator;
  private int index;
  private final Runnable rotate =
      new Runnable() {
        @Override
        public void run() {
          if (disposed || selectedKey.isEmpty()) return;
          // items still belongs to displayedService until the new provider is ready.
          // Never rotate that old catalog under the new provider's request key.
          if (selectedKey.equals(displayedService) && items.size() > 1 && !rotating) {
            final String service = selectedKey;
            final int next = (index + 1) % items.size();
            final Preview item = items.get(next);
            rotating = true;
            worker.execute(
                () -> {
                  loadPreview(item);
                  main.post(
                      () -> {
                        if (disposed) return;
                        rotating = false;
                        if (service.equals(selectedKey)
                            && service.equals(displayedService)
                            && next < items.size()
                            && items.get(next) == item
                            && item.bitmap != null) {
                          rememberFrame();
                          index = next;
                          crossfade();
                        } else if (!items.contains(item)) {
                          item.bitmap = null;
                          item.logo = null;
                        }
                      });
                });
          }
          view.invalidate();
          main.postDelayed(this, ROTATE_MS);
        }
      };

  public ProviderPreviewController(android.view.View host) {
    view = host;
    context = host.getContext().getApplicationContext();
    catalog = new ProviderPreviewCatalog(context);
  }

  public void select(String packageName) {
    String key = catalogFor(packageName);
    if (key.equals(selectedKey)) {
      if (!key.isEmpty()
          && android.os.SystemClock.uptimeMillis() - selectedAt >= 180
          && (!fresh(key) || !key.equals(displayedService))) load(key, selectedLabel);
      return;
    }
    main.removeCallbacks(rotate);
    main.removeCallbacks(switchLoad);
    selectedKey = key;
    selectedAt = android.os.SystemClock.uptimeMillis();
    selectedLabel = labelFor(packageName);
    view.invalidate();
    if (!key.isEmpty()) {
      if (fresh(key)
          && key.equals(displayedService)
          && !items.isEmpty()
          && items.get(Math.min(index, items.size() - 1)).bitmap != null) {
        main.postDelayed(rotate, ROTATE_MS);
        return;
      }
      List<Preview> cached = catalogs.get(key);
      if (fresh(key) && cached != null && !cached.isEmpty() && cached.get(0).bitmap != null)
        show(cached, selectedLabel);
      else main.postDelayed(switchLoad, 180);
      main.postDelayed(rotate, ROTATE_MS);
    }
  }

  public void draw(Canvas canvas, float width, float height) {
    if (selectedKey.isEmpty() || items.isEmpty()) return;
    Preview item = items.get(Math.min(index, items.size() - 1));
    if (item.bitmap != null) {
      if (previousFrame != null && blend < 1f) {
        paint.setShader(null);
        paint.setColor(0xFF050A12);
        paint.setAlpha(255);
        canvas.drawRect(0, 0, width, height, paint);
        if (blend < .5f)
          drawFrame(canvas, previousFrame, width, height, Math.round(255f * (1f - 2f * blend)));
        else drawFrame(canvas, item.bitmap, width, height, Math.round(255f * (2f * blend - 1f)));
      } else drawFrame(canvas, item.bitmap, width, height, 255);
      paint.setColor(Color.WHITE);
      paint.setShader(
          new LinearGradient(0, 0, 0, height, 0x08000A18, 0x24000A18, Shader.TileMode.CLAMP));
      canvas.drawRect(0, 0, width, height, paint);
      paint.setShader(null);
    }
    if (previousItem != null && blend < .5f)
      drawTitle(
          canvas, previousItem, previousLabel, width, height, Math.round(255 * (1f - 2f * blend)));
    else
      drawTitle(
          canvas,
          item,
          displayedLabel,
          width,
          height,
          previousItem == null ? 255 : Math.round(255 * Math.max(0f, 2f * blend - 1f)));
  }

  private void drawTitle(
      Canvas canvas, Preview item, String label, float width, float height, int alpha) {
    paint.setShader(null);
    paint.setColor(Color.WHITE);
    paint.setAlpha(alpha);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
    paint.setTextSize(30f);
    paint.setShadowLayer(8f, 0, 2f, 0xA0000000);
    String heading = item.trending ? "TRENDING ON " : "POPULAR ON ";
    canvas.drawText(heading + label.toUpperCase(Locale.US), width / 2f, height * .595f, paint);
    if (item.logo != null) {
      float scale =
          Math.min(width * .62f / item.logo.getWidth(), height * .10f / item.logo.getHeight());
      float dw = item.logo.getWidth() * scale, dh = item.logo.getHeight() * scale;
      canvas.drawBitmap(
          item.logo,
          null,
          new RectF(
              (width - dw) / 2f,
              height * .665f - dh / 2f,
              (width + dw) / 2f,
              height * .665f + dh / 2f),
          paint);
    } else {
      paint.setTypeface(
          android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
      paint.setTextSize(82f);
      while (paint.measureText(item.title) > width * .72f && paint.getTextSize() > 44f)
        paint.setTextSize(paint.getTextSize() - 1f);
      canvas.drawText(item.title, width / 2f, height * .70f, paint);
    }
    paint.clearShadowLayer();
    paint.setTextAlign(Paint.Align.LEFT);
    paint.setAlpha(255);
  }

  private void drawFrame(Canvas canvas, Bitmap bitmap, float width, float height, int alpha) {
    float scale = Math.max(width / bitmap.getWidth(), height / bitmap.getHeight());
    float dw = bitmap.getWidth() * scale, dh = bitmap.getHeight() * scale;
    paint.setShader(null);
    paint.setAlpha(alpha);
    canvas.drawBitmap(
        bitmap,
        null,
        new RectF((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f),
        paint);
    paint.setAlpha(255);
  }

  private void rememberFrame() {
    previousItem = items.isEmpty() ? null : items.get(Math.min(index, items.size() - 1));
    previousLabel = displayedLabel;
    previousFrame = previousItem == null ? null : previousItem.bitmap;
  }

  private void crossfade() {
    if (animator != null) animator.cancel();
    blend = previousFrame == null ? 1f : 0f;
    animator = ValueAnimator.ofFloat(blend, 1f);
    animator.setDuration(280);
    animator.addUpdateListener(
        a -> {
          blend = (Float) a.getAnimatedValue();
          view.invalidate();
        });
    animator.addListener(
        new android.animation.AnimatorListenerAdapter() {
          boolean cancelled;

          @Override
          public void onAnimationCancel(android.animation.Animator animation) {
            cancelled = true;
          }

          @Override
          public void onAnimationEnd(android.animation.Animator animation) {
            if (cancelled) return;
            previousFrame = null;
            previousItem = null;
            Preview current = items.isEmpty() ? null : items.get(Math.min(index, items.size() - 1));
            for (List<Preview> list : catalogs.values())
              for (Preview preview : list)
                if (preview != current) {
                  preview.bitmap = null;
                  preview.logo = null;
                }
            enhanceCurrent();
          }
        });
    animator.start();
  }

  private void show(List<Preview> loaded, String label) {
    rememberFrame();
    items.clear();
    items.addAll(loaded);
    index = 0;
    displayedLabel = label;
    displayedService = selectedKey;
    android.util.Log.i(TAG, "Displaying " + displayedService + ": " + items.get(0).title);
    crossfade();
  }

  void drawPillTexture(Canvas canvas, RectF bounds, float radius) {
    if (items.isEmpty()) return;
    Bitmap texture = items.get(Math.min(index, items.size() - 1)).texture;
    if (texture == null) return;
    int save = canvas.save();
    Path clip = new Path();
    clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
    canvas.clipPath(clip);
    paint.setAlpha(118);
    canvas.drawBitmap(texture, null, bounds, paint);
    paint.setAlpha(255);
    canvas.restoreToCount(save);
  }

  public void dispose() {
    disposed = true;
    selectedKey = "";
    main.removeCallbacksAndMessages(null);
    items.clear();
    catalogs.clear();
    previousFrame = null;
    previousItem = null;
    if (animator != null) animator.cancel();
    worker.shutdownNow();
    artworkWorker.shutdownNow();
    catalog.dispose();
  }

  private void load(String service, String label) {
    if (disposed || loading.contains(service)) return;
    long now = System.currentTimeMillis();
    List<Preview> saved = catalogs.get(service);
    boolean hasSaved = saved != null && !saved.isEmpty();
    Long attempted = refreshAttempts.get(service);
    // Catalog refresh cooldown must not block decoding an already cached provider.
    boolean refresh =
        !fresh(service)
            && (attempted == null || now - attempted >= (hasSaved ? 15L * 60000L : 10000L));
    if (!hasSaved && !refresh) return;
    Long frameAttempt = frameAttempts.get(service);
    if (!refresh && frameAttempt != null && now - frameAttempt < 1500L) return;
    loading.add(service);
    frameAttempts.put(service, now);
    if (refresh) refreshAttempts.put(service, now);
    final long day = indiaDay(now);
    final boolean wasFresh = fresh(service);
    worker.execute(
        () -> {
          if (!service.equals(selectedKey)) {
            main.post(() -> loading.remove(service));
            return;
          }
          List<Preview> loaded = saved;
          boolean complete = wasFresh;
          if (refresh) {
            ProviderPreviewCatalog.Result result = catalog.load(service);
            if (!result.titles.isEmpty()) {
              loaded = new ArrayList<>();
              for (ProviderPreviewCatalog.Title title : result.titles)
                loaded.add(new Preview(title.title, title.tmdbId, title.imageUrl, title.trending));
              complete = result.complete;
            }
          }
          if (loaded == null) loaded = new ArrayList<>();
          int readyIndex = -1;
          if (service.equals(selectedKey)) {
            // A missing image for the first chart entry must not block the whole OTT.
            for (int i = 0; i < Math.min(3, loaded.size()) && service.equals(selectedKey); i++) {
              loadPreview(loaded.get(i));
              if (loaded.get(i).bitmap != null) {
                readyIndex = i;
                break;
              }
            }
          }
          if (readyIndex > 0) java.util.Collections.rotate(loaded, -readyIndex);
          final List<Preview> ready = loaded;
          final boolean freshResult = complete;
          main.post(
              () -> {
                if (disposed) return;
                loading.remove(service);
                if (!ready.isEmpty()) {
                  // Retain metadata even when artwork fails, so a retry does not re-query APIs.
                  catalogs.put(service, ready);
                  catalogDays.put(service, freshResult ? day : day - 1);
                  if (service.equals(selectedKey) && ready.get(0).bitmap != null)
                    show(ready, label);
                  else if (service.equals(selectedKey))
                    android.util.Log.w(TAG, "Artwork pending for " + service);
                } else android.util.Log.w(TAG, "Catalog unavailable for " + service + "; retrying");
                view.invalidate();
              });
        });
  }

  private void loadPreview(Preview item) {
    if (item.bitmap != null) return;
    // Resolve the title treatment before publishing a new card. Late logo changes
    // would visibly replace plain text with a different font one second later.
    if (!item.resolved) {
      TitleArtwork.Images art = TitleArtwork.lookup(context, item.tmdbId);
      item.originalUrl = art.backdrop;
      item.logoUrl = art.logo;
      item.resolved = true;
    }
    item.logo = cachedBitmap(item.logoUrl);
    item.bitmap = cachedBitmap(item.originalUrl, false);
    item.enhanced = item.bitmap != null;
    if (item.bitmap == null) item.bitmap = cachedBitmap(item.fallbackUrl);
  }

  private void enhanceCurrent() {
    if (disposed || items.isEmpty() || !selectedKey.equals(displayedService)) return;
    Preview item = items.get(Math.min(index, items.size() - 1));
    if (item.enhancing || item.enhanced || item.originalUrl.isEmpty()) return;
    item.enhancing = true;
    String service = selectedKey;
    artworkWorker.execute(
        () -> {
          Bitmap original = null;
          if (!disposed && service.equals(selectedKey)) original = cachedBitmap(item.originalUrl);
          final Bitmap readyOriginal = original;
          main.post(
              () -> {
                item.enhancing = false;
                if (disposed) return;
                if (service.equals(selectedKey)
                    && service.equals(displayedService)
                    && !items.isEmpty()
                    && items.get(Math.min(index, items.size() - 1)) == item
                    && readyOriginal != null) {
                  // Upgrade only the backdrop. The title/logo committed with the card stays fixed.
                  item.bitmap = readyOriginal;
                  item.enhanced = true;
                  view.invalidate();
                }
              });
        });
  }

  private Bitmap cachedBitmap(String address) {
    return cachedBitmap(address, true);
  }

  private Bitmap cachedBitmap(String address, boolean download) {
    if (address == null || address.isEmpty()) return null;
    File directory = new File(context.getCacheDir(), "glass-artwork");
    directory.mkdirs();
    try {
      byte[] hash =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(address.getBytes(StandardCharsets.UTF_8));
      StringBuilder key = new StringBuilder();
      for (byte b : hash) key.append(String.format(Locale.US, "%02x", b));
      File file = new File(directory, key.toString() + ".image");
      if (file.isFile()) {
        file.setLastModified(System.currentTimeMillis());
        return decodeArtwork(file);
      }
      if (!download) return null;
      HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
      File pending = new File(directory, key.toString() + ".part");
      try {
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(15000);
        try (InputStream in = connection.getInputStream();
            FileOutputStream out = new FileOutputStream(pending)) {
          byte[] buffer = new byte[16384];
          int n;
          while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pending.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
          pending.delete();
          return null;
        }
        pending.renameTo(file);
      } finally {
        connection.disconnect();
        pending.delete();
      }
      File[] files = directory.listFiles();
      if (files != null) {
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        long total = 0;
        for (File f : files) total += f.length();
        for (File f : files)
          if (total > 256L * 1024 * 1024 && !f.equals(file)) {
            total -= f.length();
            f.delete();
          }
      }
      return decodeArtwork(file);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Bitmap decodeArtwork(File file) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getPath(), options);
    options.inSampleSize = 1;
    while (options.outWidth / options.inSampleSize > 4096
        || options.outHeight / options.inSampleSize > 2304) options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    return BitmapFactory.decodeFile(file.getPath(), options);
  }

  private static Bitmap bitmap(String address) {
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(address).openConnection();
      connection.setConnectTimeout(6000);
      connection.setReadTimeout(8000);
      connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; Bravia Home)");
      try (InputStream input = connection.getInputStream()) {
        return BitmapFactory.decodeStream(input);
      }
    } catch (Exception ignored) {
      return null;
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  private static String catalogFor(String pkg) {
    if (pkg == null) return "";
    if (pkg.equals("com.netflix.ninja")) return "netflix";
    if (pkg.equals("com.amazon.amazonvideo.livingroom")) return "prime";
    if (pkg.equals("com.apple.atve.sony.appletv")) return "apple";
    if (pkg.equals("in.startv.hotstar")) return "hotstar";
    if (pkg.equals("com.sonyliv")) return "sonyliv";
    if (pkg.equals("com.graymatrix.did")) return "zee5";
    if (pkg.equals("tv.airtel.xstream.tvapp")) return "airtelxstream";
    return "";
  }

  private static String labelFor(String pkg) {
    if (pkg == null) return "";
    if (pkg.equals("in.startv.hotstar")) return "JioHotstar";
    if (pkg.equals("com.sonyliv")) return "Sony LIV";
    if (pkg.equals("com.graymatrix.did")) return "Zee5";
    if (pkg.equals("com.amazon.amazonvideo.livingroom")) return "Prime Video";
    if (pkg.equals("tv.airtel.xstream.tvapp")) return "Xstream";
    if (pkg.equals("com.apple.atve.sony.appletv")) return "Apple TV";
    return "Netflix";
  }

  private static final class Preview {
    final String title, tmdbId, fallbackUrl;
    final boolean trending;
    volatile Bitmap bitmap, logo;
    String originalUrl = "", logoUrl = "";
    boolean resolved, enhancing, enhanced;
    final Bitmap texture = null;

    Preview(String value, String id, String fallback, boolean trending) {
      title = value;
      tmdbId = id;
      fallbackUrl = fallback;
      this.trending = trending;
    }
  }
}
