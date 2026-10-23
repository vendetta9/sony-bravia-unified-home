package tv.bravia.applehome.search;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Provider catalog retrieval, typed daily caches and a balanced movie/series rotation. */
final class ProviderPreviewCatalog {
  private static final int LIMIT = 8;
  private final Context context;

  static final class Title {
    final String title, tmdbId, imageUrl;
    final boolean trending;

    Title(String title, String tmdbId, String imageUrl, boolean trending) {
      this.title = title;
      this.tmdbId = tmdbId;
      this.imageUrl = imageUrl;
      this.trending = trending;
    }
  }

  static final class Result {
    final List<Title> titles;
    final boolean complete;

    Result(List<Title> titles, boolean complete) {
      this.titles = titles;
      this.complete = complete;
    }
  }

  private static final class Feed {
    final List<Title> titles;
    final boolean fresh;

    Feed(List<Title> titles, boolean fresh) {
      this.titles = titles;
      this.fresh = fresh;
    }
  }

  ProviderPreviewCatalog(Context context) {
    this.context = context.getApplicationContext();
  }

  Result load(String service) {
    String key = apiKey();
    if (key.isEmpty()) return new Result(Collections.emptyList(), false);
    Feed movies = loadType(service, "movie", key);
    Feed series = loadType(service, "series", key);
    List<Title> mixed = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    // Preserve each chart's ranking while preventing a movie-heavy combined
    // response from pushing every series beyond the eight visible previews.
    for (int i = 0;
        mixed.size() < LIMIT && (i < movies.titles.size() || i < series.titles.size());
        i++) {
      append(mixed, seen, movies.titles, i);
      append(mixed, seen, series.titles, i);
    }
    return new Result(mixed, movies.fresh && series.fresh);
  }

  private static void append(List<Title> mixed, Set<String> seen, List<Title> source, int index) {
    if (mixed.size() >= LIMIT || index >= source.size()) return;
    Title item = source.get(index);
    String identity = item.tmdbId.isEmpty() ? item.title.toLowerCase(Locale.ROOT) : item.tmdbId;
    if (seen.add(identity)) mixed.add(item);
  }

  private Feed loadType(String service, String type, String key) {
    boolean chart = service.equals("netflix") || service.equals("prime") || service.equals("apple");
    Feed primary = loadFeed(service, type, chart, key);
    if (!chart || !primary.titles.isEmpty()) return primary;
    // Some country/service charts have no entries of one type. Use popularity
    // for that type and label those individual cards accurately.
    Feed fallback = loadFeed(service, type, false, key);
    return new Feed(fallback.titles, primary.fresh && fallback.fresh);
  }

  private Feed loadFeed(String service, String type, boolean chart, String key) {
    // New typed cache names bypass the old combined, potentially movie-only cache.
    File cache =
        new File(
            context.getCacheDir(),
            "glass-catalog-v2-"
                + service
                + "-in-"
                + type
                + (chart ? "-top" : "-popular")
                + ".json");
    List<Title> stale = Collections.emptyList();
    if (cache.isFile()) {
      try (InputStream input = new FileInputStream(cache)) {
        stale = parse(new String(readAll(input), StandardCharsets.UTF_8), type, chart);
        if (indiaDay(cache.lastModified()) == indiaDay(System.currentTimeMillis()))
          return new Feed(stale, true);
      } catch (Exception ignored) {
      }
    }
    try {
      String path;
      if (chart) {
        path = "https://api.movieofthenight.com/v4/shows/top?country=in&service=" + service;
      } else {
        path =
            "https://api.movieofthenight.com/v4/shows/search/filters?country=in&catalogs="
                + URLEncoder.encode(service + ".subscription", "UTF-8")
                + "&series_granularity=show&order_by=popularity_1month&order_direction=desc";
      }
      String body = request(path + "&show_type=" + type, key);
      List<Title> titles = parse(body, type, chart);
      try (FileOutputStream output = new FileOutputStream(cache)) {
        output.write(body.getBytes(StandardCharsets.UTF_8));
      } catch (Exception ignored) {
      }
      return new Feed(titles, true);
    } catch (Exception ignored) {
      // Keep last successful titles on an outage; the controller retries after 15 minutes.
      return new Feed(stale, false);
    }
  }

  private static List<Title> parse(String body, String type, boolean trending) throws Exception {
    JSONArray shows;
    if (body.trim().startsWith("[")) shows = new JSONArray(body);
    else {
      shows = new JSONObject(body).optJSONArray("shows");
      if (shows == null) throw new IOException("Missing catalog shows array");
    }
    List<Title> result = new ArrayList<>();
    for (int i = 0; i < shows.length() && result.size() < LIMIT; i++) {
      JSONObject show = shows.optJSONObject(i);
      if (show == null || !type.equals(show.optString("showType", type))) continue;
      String title = show.optString("title", "").trim();
      String image = imageUrl(show.optJSONObject("imageSet"));
      if (title.isEmpty() || image.isEmpty()) continue;
      String id = show.optString("tmdbId", "");
      // Preserve tv/ IDs: artwork lookup must use the TV endpoint for series.
      if (id.matches("[0-9]+")) id = (type.equals("series") ? "tv/" : "movie/") + id;
      result.add(new Title(title, id, image, trending));
    }
    return result;
  }

  private String apiKey() {
    Properties properties = new Properties();
    try (InputStream input = context.getAssets().open("movie-api.properties")) {
      properties.load(input);
      return properties.getProperty("movieofthenight", "").trim();
    } catch (Exception ignored) {
      return "";
    }
  }

  private static long indiaDay(long millis) {
    return (millis + 19800000L) / 86400000L;
  }

  private static String imageUrl(JSONObject set) {
    if (set == null) return "";
    for (String kind :
        new String[] {
          "horizontalBackdrop", "horizontalPoster", "verticalBackdrop", "verticalPoster"
        }) {
      JSONObject variants = set.optJSONObject(kind);
      if (variants == null) continue;
      for (String size : new String[] {"w1440", "w1080", "w720", "w600", "w480", "w360", "w240"}) {
        String url = variants.optString(size, "");
        if (url.startsWith("https://")) return url;
      }
    }
    return "";
  }

  private static String request(String address, String key) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
    try {
      connection.setConnectTimeout(6000);
      connection.setReadTimeout(8000);
      connection.setRequestProperty("X-API-Key", key);
      connection.setRequestProperty("Accept", "application/json");
      try (InputStream input = connection.getInputStream()) {
        return new String(readAll(input), StandardCharsets.UTF_8);
      }
    } finally {
      connection.disconnect();
    }
  }

  private static byte[] readAll(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int count;
    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    return output.toByteArray();
  }
}
