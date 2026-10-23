package tv.bravia.applehome.sports;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.KeyEvent;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import tv.bravia.applehome.core.model.AppTile;

/** Remote-navigable live event widgets and honest, source-backed viewing options. */
public final class SportsController {
  final SportsHost view;
  final Handler main = new Handler(Looper.getMainLooper());
  final ExecutorService workers = Executors.newFixedThreadPool(4);
  final Map<Integer, List<SportsFeeds.Event>> feeds = new HashMap<>();
  final Map<Integer, Long> fetched = new HashMap<>();
  final Set<Integer> loading = new HashSet<>();
  final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
  boolean active;
  private volatile boolean disposed;
  String provider = "";
  int selected, option;
  SportsFeeds.Event opened;
  final Runnable refresh =
      new Runnable() {
        public void run() {
          if (active) {
            load();
            main.postDelayed(this, 60000);
          }
        }
      };

  public SportsController(SportsHost v) {
    view = v;
  }

  public void setActive(boolean value) {
    if (disposed || active == value) return;
    active = value;
    if (value) {
      load();
      main.postDelayed(refresh, 60000);
    } else {
      main.removeCallbacks(refresh);
      opened = null;
    }
  }

  public void selectProvider(String key) {
    if (!provider.equals(key)) {
      provider = key;
      selected = 0;
    }
  }

  void load() {
    if (disposed) return;
    for (int source = 0; source < 5; source++) {
      if (loading.contains(source)
          || System.currentTimeMillis() - fetched.getOrDefault(source, 0L)
              < (source == 4 || source == 2 ? 300000 : 55000)) continue;
      loading.add(source);
      final int id = source;
      workers.execute(
          () -> {
            List<SportsFeeds.Event> events = null;
            try {
              events = SportsFeeds.fetch(id);
            } catch (Exception failure) {
              android.util.Log.w(
                  "BraviaSports",
                  "Source "
                      + id
                      + ": "
                      + failure.getClass().getSimpleName()
                      + ": "
                      + failure.getMessage());
            }
            final List<SportsFeeds.Event> ready = events;
            main.post(
                () -> {
                  if (disposed) return;
                  loading.remove(id);
                  fetched.put(id, System.currentTimeMillis());
                  if (ready != null) feeds.put(id, ready);
                  view.sportsView().invalidate();
                });
          });
    }
  }

  List<SportsFeeds.Event> events() {
    List<SportsFeeds.Event> all = new ArrayList<>();
    long now = System.currentTimeMillis();
    for (List<SportsFeeds.Event> list : feeds.values())
      for (SportsFeeds.Event e : list) {
        if (!SportsFeeds.allowedFootball(e)) continue;
        if (e.end > 0 && e.end < now) continue;
        if (!e.live && e.start < now - 3600000L) continue;
        if (!e.live && e.start > now + 7L * 86400000) continue;
        all.add(e);
      }
    // Merge availability only when the sport, event identity and start time agree.
    for (SportsFeeds.Event e : all)
      for (SportsFeeds.Event other : all) {
        if (e == other || !e.sport.equals(other.sport) || other.streams.isEmpty()) continue;
        if (Math.abs(e.start - other.start) > 4L * 3600000) continue;
        if (e.sport.equals("F1") && Math.abs(e.start - other.start) <= 1800000L
            || identity(e.title).equals(identity(other.title))) {
          e.streams.putAll(other.streams);
          e.direct.addAll(other.direct);
        }
      }
    Collections.sort(
        all,
        Comparator.comparingInt((SportsFeeds.Event e) -> e.live ? 0 : 1)
            .thenComparingInt(e -> e.streams.isEmpty() ? 1 : 0)
            .thenComparingLong(e -> e.start));
    SportsFeeds.Event football = null, cricket1 = null, cricket2 = null, f1 = null;
    for (SportsFeeds.Event e : all) {
      if (e.sport.equals("Football") && football == null) football = e;
      if (e.sport.equals("F1") && f1 == null && raceWeek(e.start)) f1 = e;
      if (e.sport.equals("Cricket")) {
        if (cricket1 == null) cricket1 = e;
        else if (cricket2 == null && !identity(cricket1.title).equals(identity(e.title)))
          cricket2 = e;
      }
    }
    List<SportsFeeds.Event> result = new ArrayList<>();
    result.add(football == null ? placeholder("Football") : football);
    result.add(cricket1 == null ? placeholder("Cricket") : cricket1);
    result.add(f1 != null ? f1 : cricket2 == null ? placeholder("Cricket") : cricket2);
    return result;
  }

  SportsFeeds.Event placeholder(String sport) {
    SportsFeeds.Event e = new SportsFeeds.Event();
    e.sport = sport;
    e.title =
        sport.equals("Football")
            ? "No UCL, Barcelona or Man United match in the next 7 days"
            : "No current " + sport.toLowerCase(Locale.US) + " event reported";
    e.detail = loading.isEmpty() ? "Check again shortly" : "Checking sources…";
    e.source = "Schedule";
    return e;
  }

  boolean raceWeek(long time) {
    Calendar start = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
    int day = (start.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    start.add(Calendar.DAY_OF_MONTH, -day);
    start.set(Calendar.HOUR_OF_DAY, 0);
    start.set(Calendar.MINUTE, 0);
    start.set(Calendar.SECOND, 0);
    start.set(Calendar.MILLISECOND, 0);
    return time >= start.getTimeInMillis() && time < start.getTimeInMillis() + 7L * 86400000;
  }

  String identity(String name) {
    String plain =
        java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.US);
    List<String> words = new ArrayList<>();
    for (String word : plain.split("[^a-z0-9]+"))
      if (!word.isEmpty()
          && !word.matches("[0-9]+|vs|at|fc|oct|nov|dec|jan|feb|mar|apr|may|jun|jul|aug|sep"))
        words.add(word);
    Collections.sort(words);
    return words.toString();
  }

  public void draw(Canvas c, float w, float h) {
    List<SportsFeeds.Event> events = events();
    view.text(c, "Live & upcoming", 110, 218, 34, Color.WHITE);
    view.text(c, "INDIA · FOOTBALL · CRICKET · RACE WEEKEND", 110, 249, 20, 0xFFF2F6FA);
    int count = events.size();
    if (count == 0) {
      view.rounded(c, 110, 285, w - 110, 650, 28, 0x991B3043);
      view.text(
          c,
          loading.isEmpty()
              ? "No current events reported for this selection"
              : "Checking live events…",
          145,
          365,
          30,
          Color.WHITE);
      view.text(
          c, "Select another sports app, or check its sports section.", 145, 410, 23, 0xFFE0EAF2);
      return;
    }
    selected = Math.max(0, Math.min(selected, count - 1));
    int first = (selected / 3) * 3;
    float gap = 24f, cw = (w - 220f - 2 * gap) / 3f, top = 284f, ch = 400f;
    for (int i = first; i < Math.min(first + 3, count); i++) {
      SportsFeeds.Event e = events.get(i);
      float x = 110 + (i - first) * (cw + gap);
      boolean focus = view.focusArea() == 5 && i == selected;
      view.rounded(c, x, top, x + cw, top + ch, 28, focus ? 0xEDF2F7FA : 0xD21A2B3C);
      int ink = focus ? 0xFF152B3B : Color.WHITE;
      boolean stale = System.currentTimeMillis() - e.updated > 120000;
      String badge =
          (stale ? "CACHED · " : e.live ? "LIVE · " : "UPCOMING · ")
              + e.sport.toUpperCase(Locale.US);
      view.text(c, badge, x + 26, top + 39, 20, focus ? 0xFF526B7B : 0xFFC7E3EF);
      lines(c, e.title, x + 26, top + 92, cw - 52, 31, ink, 2);
      view.text(
          c,
          fit(e.score.isEmpty() ? e.competition : e.score, cw - 52, 26),
          x + 26,
          top + 204,
          26,
          ink);
      view.text(c, fit(e.detail, cw - 52, 21), x + 26, top + 246, 21, ink);
      view.text(c, startLabel(e), x + 26, top + 285, 21, ink);
      String action =
          e.streams.isEmpty() ? "Viewing link unavailable" : "OK · Verified viewing options";
      view.text(c, action, x + 26, top + 340, 23, ink);
      view.text(
          c,
          e.source + (stale ? " · refresh unavailable" : " · updated " + clock(e.updated)),
          x + 26,
          top + 375,
          17,
          focus ? 0xFF526B7B : 0xFFBDD0DE);
    }
    view.text(c, "← → SELECT EVENT  ·  OK VIEWING OPTIONS  ·  ↓ APPS", 110, 727, 18, 0xFFF5F8FA);
    if (view.focusArea() == 6 && opened != null) drawOptions(c, w, h);
  }

  void lines(Canvas c, String text, float x, float y, float width, float size, int color, int max) {
    p.setTextSize(size);
    StringBuilder line = new StringBuilder();
    int row = 0;
    for (String word : text.split(" ")) {
      String test = line.length() == 0 ? word : line + " " + word;
      if (p.measureText(test) > width && line.length() > 0) {
        view.text(c, line.toString(), x, y + row * 39, size, color);
        row++;
        line.setLength(0);
        if (row >= max) return;
      }
      if (line.length() > 0) line.append(' ');
      line.append(word);
    }
    if (row < max) view.text(c, fit(line.toString(), width, size), x, y + row * 39, size, color);
  }

  String fit(String value, float width, float size) {
    p.setTextSize(size);
    if (p.measureText(value) <= width) return value;
    while (value.length() > 1 && p.measureText(value + "…") > width)
      value = value.substring(0, value.length() - 1);
    return value + "…";
  }

  static String clock(long time) {
    SimpleDateFormat f = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
    f.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
    return f.format(new Date(time));
  }

  String startLabel(SportsFeeds.Event e) {
    if (e.live) return "In progress";
    SimpleDateFormat f = new SimpleDateFormat("EEE, d MMM · h:mm a 'IST'", Locale.ENGLISH);
    f.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
    return e.start > 0 ? f.format(new Date(e.start)) : "Schedule not supplied";
  }

  String providerName(String key) {
    return key.contains("formulaone")
        ? "F1 TV"
        : key.contains("sonyliv") ? "Sony LIV" : key.contains("hotstar") ? "JioHotstar" : "Sports";
  }

  List<AppTile> options() {
    List<AppTile> list = new ArrayList<>();
    for (AppTile tile : view.homeCategoryApps()) {
      if (opened == null || !opened.streams.containsKey(tile.key)) continue;
      list.add(tile);
    }
    return list;
  }

  void drawOptions(Canvas c, float w, float h) {
    float l = w * .20f, r = w * .80f, t = 230f;
    List<AppTile> choices = options();
    view.rounded(c, l, t, r, 720f, 30, 0xF3132435);
    view.text(c, "Viewing options", l + 32, t + 48, 32, Color.WHITE);
    view.text(c, fit(opened.title, r - l - 64, 25), l + 32, t + 91, 25, Color.WHITE);
    for (int i = 0; i < choices.size() + 1; i++) {
      float y = t + 142 + i * 76;
      boolean focus = option == i;
      view.rounded(c, l + 24, y, r - 24, y + 64, 28, focus ? 0xFFF4F7FA : 0xB5081523);
      int ink = focus ? 0xFF13283A : Color.WHITE;
      if (i < choices.size()) {
        AppTile tile = choices.get(i);
        view.drawBrandMark(c, tile, l + 36, y + 3, 210, 58, ink, focus);
        boolean direct = opened.direct.contains(tile.key);
        view.text(
            c,
            direct ? "Watch this event" : "Verified broadcaster · open app",
            l + 265,
            y + 40,
            23,
            ink);
      } else view.text(c, "Event details · " + opened.source, l + 45, y + 40, 23, ink);
    }
    view.text(
        c,
        choices.isEmpty()
            ? "No broadcaster has been verified for this event."
            : "Only broadcasters verified for this event are listed.",
        l + 32,
        690,
        18,
        0xFFD0DFEB);
  }

  public boolean handle(int key, KeyEvent event) {
    if (view.focusArea() != 5 && view.focusArea() != 6) return false;
    if (event.getRepeatCount() > 0 && (key == 23 || key == 66)) return true;
    List<SportsFeeds.Event> list = events();
    if (view.focusArea() == 5) {
      if (key == 21 && !list.isEmpty()) selected = (selected + list.size() - 1) % list.size();
      else if (key == 22 && !list.isEmpty()) selected = (selected + 1) % list.size();
      else if (key == 20 || key == 4) {
        view.setFocusArea(0);
        view.setFocusIndex(
            Math.min(view.lastDockIndex(), Math.max(0, view.homeCategoryApps().size() - 1)));
      } else if (key == 19) {
        view.setFocusArea(2);
        view.setFocusIndex(0);
      } else if ((key == 23 || key == 66) && !list.isEmpty()) {
        opened = list.get(Math.min(selected, list.size() - 1));
        option = 0;
        view.setFocusArea(6);
      }
    } else {
      int count = options().size() + 1;
      if (key == 19) option = (option + count - 1) % count;
      else if (key == 20) option = (option + 1) % count;
      else if (key == 4) {
        view.setFocusArea(5);
        opened = null;
      } else if (key == 23 || key == 66) openOption();
    }
    view.sportsView().invalidate();
    return true;
  }

  void openOption() {
    List<AppTile> choices = options();
    if (option == choices.size()) {
      try {
        view.sportsView()
            .getContext()
            .startActivity(
                new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        opened.page.isEmpty() ? "https://sports.justwatch.com/in" : opened.page)));
      } catch (Exception e) {
        Toast.makeText(
                view.sportsView().getContext(), "No app can open event details", Toast.LENGTH_SHORT)
            .show();
      }
      return;
    }
    if (option < 0 || option >= choices.size()) return;
    AppTile tile = choices.get(option);
    if (opened.direct.contains(tile.key) && !opened.streams.getOrDefault(tile.key, "").isEmpty()) {
      Intent direct =
          new Intent(Intent.ACTION_VIEW, Uri.parse(opened.streams.get(tile.key)))
              .setPackage(tile.key);
      if (direct.resolveActivity(view.sportsView().getContext().getPackageManager()) != null) {
        try {
          view.sportsView().getContext().startActivity(direct);
          return;
        } catch (Exception ignored) {
        }
      }
      Toast.makeText(
              view.sportsView().getContext(),
              "This app cannot open the event link; choose it in the app",
              Toast.LENGTH_LONG)
          .show();
    }
    view.launch(tile);
  }

  public void dispose() {
    disposed = true;
    active = false;
    main.removeCallbacksAndMessages(null);
    workers.shutdownNow();
  }
}
