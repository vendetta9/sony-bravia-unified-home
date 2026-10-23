package tv.bravia.applehome.search;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.json.JSONArray;
import org.json.JSONObject;

/** Live movie lookup. Network work must run off the UI thread. */
final class MovieSearch {
  private static String omdbKey = "";
  private static File cacheDirectory;

  static void configure(Context context) {
    cacheDirectory = context.getCacheDir();
    Properties keys = new Properties();
    try (InputStream input = context.getAssets().open("movie-api.properties")) {
      keys.load(input);
      omdbKey = keys.getProperty("omdb", "");
    } catch (Exception ignored) {
    }
    MovieAvailability.configure(context, keys);
  }

  static final class Movie {
    final String id, title, year;

    Movie(String id, String title, String year) {
      this.id = id;
      this.title = title;
      this.year = year;
    }
  }

  static final class Provider {
    final String name, tvUrl, webUrl;
    String access;

    Provider(String name, String tvUrl, String webUrl, String access) {
      this.name = name;
      this.tvUrl = tvUrl;
      this.webUrl = webUrl;
      this.access = access;
    }

    String packageName() {
      String lower = name.toLowerCase(Locale.US);
      return lower.contains("netflix")
          ? "com.netflix.ninja"
          : lower.contains("prime") || lower.equals("amazon")
              ? "com.amazon.amazonvideo.livingroom"
              : lower.contains("hotstar")
                  ? "in.startv.hotstar"
                  : lower.contains("sony")
                      ? "com.sonyliv"
                      : lower.contains("zee5")
                          ? "com.graymatrix.did"
                          : lower.contains("apple")
                              ? "com.apple.atve.sony.appletv"
                              : lower.contains("google")
                                  ? "com.google.android.videos"
                                  : lower.contains("airtel") ? "tv.airtel.xstream.tvapp" : "";
    }
  }

  static final class Suggestions {
    final List<String> google = new ArrayList<>();
    final List<Movie> movies = new ArrayList<>();
  }

  static final class Lookup {
    final List<Movie> movies = new ArrayList<>();
    boolean completed;
  }

  static final class Feature {
    String id, title, year, imdbRating;
    String releaseDate = "";
    final List<String> cast = new ArrayList<>();
    String availabilityError = "", availabilitySource = "";
    Bitmap poster;
    final List<Provider> providers = new ArrayList<>();
  }

  static Suggestions suggest(String query) {
    Suggestions result = new Suggestions();
    String encoded;
    try {
      encoded = URLEncoder.encode(query, "UTF-8");
    } catch (Exception ignored) {
      return result;
    }
    String cachedSuggestions =
        readCache("google-suggest-v1-", query, 6 * 60 * 60 * 1000L, 10 * 60 * 1000L);
    try {
      String body = cachedSuggestions;
      if (body == null) {
        body =
            get(
                "https://suggestqueries.google.com/complete/search"
                    + "?client=firefox&hl=en&gl=in&q="
                    + encoded);
      }
      JSONArray response = new JSONArray(body);
      JSONArray options = response.optJSONArray(1);
      if (options != null)
        for (int i = 0; i < Math.min(3, options.length()); i++) {
          String suggestion = options.optString(i, "").trim();
          if (!suggestion.isEmpty()) result.google.add(suggestion);
        }
      if (cachedSuggestions == null && options != null)
        writeCache("google-suggest-v1-", query, body);
    } catch (Exception ignored) {
    }
    result.movies.addAll(findMovies(query));
    if (result.movies.isEmpty()) {
      // Google often corrects spelling that a strict movie-title API cannot match.
      for (String suggestion : result.google) {
        result.movies.addAll(findMovies(suggestion));
        if (!result.movies.isEmpty()) break;
      }
    }
    return result;
  }

  static String cleanTitle(String query) {
    String title =
        query
            .trim()
            .toLowerCase(Locale.US)
            .replaceAll(
                "(?i)\\b(cast|trailer|trailers|movie|movies|film|watch|online|ott|imdb|rating|full"
                    + " movie)\\b",
                " ")
            .replaceAll("(?i)\\brelease date\\b", " ")
            .replaceAll("[‐‑–—]", "-")
            .replaceAll("\\s+", " ")
            .trim();
    // First installments commonly omit '1' in their official title.
    return title.replaceAll("(?i)(?:\\s+(?:part\\s+)?1|(?<=[a-z])1)$", "").trim();
  }

  static List<Movie> findMovies(String query) {
    List<Movie> result = new ArrayList<>();
    String title = cleanTitle(query);
    String year = "";
    java.util.regex.Matcher date =
        java.util.regex.Pattern.compile("\\b((?:19|20)[0-9]{2})\\b").matcher(title);
    if (date.find()) {
      year = date.group(1);
      title = title.replace(year, "").trim();
    }
    if (title.length() < 2) return result;
    String cacheKey = title.replaceAll("[^a-z0-9]", "") + "|" + year;
    List<Movie> cached = readMovieCache(cacheKey);
    if (cached != null) return cached;
    boolean complete = true;
    try {
      String encoded = URLEncoder.encode(title, "UTF-8");
      String base =
          "https://www.omdbapi.com/?apikey="
              + URLEncoder.encode(omdbKey, "UTF-8")
              + "&type=movie"
              + (year.isEmpty() ? "" : "&y=" + year);
      if (!omdbKey.isEmpty()) {
        try {
          JSONObject exact = new JSONObject(get(base + "&t=" + encoded));
          if (!"True".equals(exact.optString("Response"))
              && !"Movie not found!".equals(exact.optString("Error"))) complete = false;
          String id = exact.optString("imdbID", "");
          if (id.matches("tt[0-9]+"))
            result.add(
                new Movie(id, exact.optString("Title", "Movie"), exact.optString("Year", "")));
        } catch (Exception ignored) {
          complete = false;
        }
        if (result.isEmpty())
          try {
            JSONObject response = new JSONObject(get(base + "&s=" + encoded));
            JSONArray movies = response.optJSONArray("Search");
            boolean foundMovies = "True".equals(response.optString("Response")) && movies != null;
            boolean validMiss =
                "False".equals(response.optString("Response"))
                    && "Movie not found!".equals(response.optString("Error"));
            if (!foundMovies && !validMiss) complete = false;
            if (movies != null)
              for (int i = 0; i < Math.min(8, movies.length()); i++) {
                JSONObject movie = movies.getJSONObject(i);
                String id = movie.optString("imdbID", "");
                if (id.matches("tt[0-9]+"))
                  result.add(
                      new Movie(
                          id, movie.optString("Title", "Movie"), movie.optString("Year", "")));
              }
          } catch (Exception ignored) {
            complete = false;
          }
      }
      if (result.isEmpty()) {
        Lookup motn = MovieAvailability.searchMovies(title);
        result.addAll(motn.movies);
        if (!motn.completed) complete = false;
      }
      if (result.isEmpty())
        try {
          JSONObject response =
              new JSONObject(
                  get(
                      "https://v3-cinemeta.strem.io/catalog/movie/top/search="
                          + encoded
                          + ".json"));
          JSONArray movies = response.optJSONArray("metas");
          if (movies == null) complete = false;
          if (movies != null)
            for (int i = 0; i < Math.min(8, movies.length()); i++) {
              JSONObject movie = movies.getJSONObject(i);
              String id = movie.optString("id", "");
              if (id.matches("tt[0-9]+"))
                result.add(
                    new Movie(
                        id, movie.optString("name", "Movie"), movie.optString("releaseInfo", "")));
            }
        } catch (Exception ignored) {
          complete = false;
        }
    } catch (Exception ignored) {
      complete = false;
    }
    if (!year.isEmpty()) {
      final String requestedYear = year;
      result.removeIf(movie -> !movie.year.startsWith(requestedYear));
    }
    // Cache positives even if a fallback source failed. Cache a miss only when
    // every configured/attempted title source returned a valid response.
    if (!result.isEmpty() || complete) writeMovieCache(cacheKey, result);
    return result;
  }

  private static List<Movie> readMovieCache(String key) {
    String body = readCache("movie-search-v1-", key, 6 * 60 * 60 * 1000L, 10 * 60 * 1000L);
    if (body == null) return null;
    try {
      JSONArray array = new JSONArray(body);
      List<Movie> result = new ArrayList<>();
      for (int i = 0; i < array.length(); i++) {
        JSONObject item = array.getJSONObject(i);
        String id = item.optString("id", "");
        if (id.matches("tt[0-9]+"))
          result.add(new Movie(id, item.optString("title", "Movie"), item.optString("year", "")));
      }
      return result;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void writeMovieCache(String key, List<Movie> movies) {
    JSONArray array = new JSONArray();
    for (Movie movie : movies) {
      JSONObject item = new JSONObject();
      try {
        item.put("id", movie.id);
        item.put("title", movie.title);
        item.put("year", movie.year);
        array.put(item);
      } catch (Exception ignored) {
      }
    }
    writeCache("movie-search-v1-", key, array.toString());
  }

  private static String readCache(String prefix, String key, long positiveTtl, long emptyTtl) {
    if (cacheDirectory == null) return null;
    File cache = new File(cacheDirectory, prefix + cacheKey(key) + ".json");
    if (!cache.isFile()) return null;
    long age = System.currentTimeMillis() - cache.lastModified();
    try {
      String body = new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8);
      JSONArray data = new JSONArray(body);
      JSONArray entries = data.length() > 1 ? data.optJSONArray(1) : null;
      int count = entries == null ? data.length() : entries.length();
      long ttl = count == 0 ? emptyTtl : positiveTtl;
      return age >= 0 && age < ttl ? body : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void writeCache(String prefix, String key, String body) {
    if (cacheDirectory == null) return;
    try {
      Files.write(
          new File(cacheDirectory, prefix + cacheKey(key) + ".json").toPath(),
          body.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
    }
  }

  private static String cacheKey(String key) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest(key.toLowerCase(Locale.US).getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte value : hash) result.append(String.format(Locale.US, "%02x", value & 0xff));
      return result.toString();
    } catch (Exception ignored) {
      return Integer.toHexString(key.toLowerCase(Locale.US).hashCode());
    }
  }

  static Movie matchingMovie(String query, List<Movie> movies) {
    String key =
        cleanTitle(query).replaceAll("\\b(?:19|20)[0-9]{2}\\b", "").replaceAll("[^a-z0-9]", "");
    Movie match = null;
    for (Movie movie : movies) {
      String title = movie.title.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
      if (key.equals(title)) {
        if (match != null) return null;
        match = movie;
      }
    }
    return match;
  }

  static Feature feature(String imdbId) throws Exception {
    if (imdbId == null || !imdbId.matches("tt[0-9]+"))
      throw new IllegalArgumentException("Invalid movie ID");
    Feature result = new Feature();
    result.id = imdbId;
    String posterUrl = "";
    if (!omdbKey.isEmpty())
      try {
        JSONObject omdb =
            new JSONObject(
                get(
                    "https://www.omdbapi.com/?apikey="
                        + URLEncoder.encode(omdbKey, "UTF-8")
                        + "&i="
                        + imdbId));
        if ("True".equals(omdb.optString("Response"))) {
          result.title = omdb.optString("Title", "Movie");
          result.year = omdb.optString("Year", "");
          result.imdbRating = omdb.optString("imdbRating", "");
          result.releaseDate = formatReleaseDate(omdb.optString("Released", ""));
          for (String actor : omdb.optString("Actors", "").split(",")) addCast(result, actor);
          posterUrl = omdb.optString("Poster", "");
        }
      } catch (Exception ignored) {
      }
    if (result.title == null) {
      JSONObject meta =
          new JSONObject(get("https://v3-cinemeta.strem.io/meta/movie/" + imdbId + ".json"))
              .getJSONObject("meta");
      result.title = meta.optString("name", "Movie");
      result.year = meta.optString("releaseInfo", "");
      result.imdbRating = meta.optString("imdbRating", "");
      result.releaseDate = formatReleaseDate(meta.optString("released", ""));
      JSONArray cast = meta.optJSONArray("cast");
      if (cast != null) for (int i = 0; i < cast.length(); i++) addCast(result, cast.optString(i));
      posterUrl = meta.optString("poster", "");
    }
    if (!result.imdbRating.matches("[0-9]+(\\.[0-9]+)?")) result.imdbRating = "";
    result.poster = poster(posterUrl);

    MovieAvailability.load(result);
    return result;
  }

  static void addCast(Feature movie, String actor) {
    actor = actor.trim();
    if (movie.cast.size() < 4
        && !actor.isEmpty()
        && !actor.equals("N/A")
        && !movie.cast.contains(actor)) movie.cast.add(actor);
  }

  private static String formatReleaseDate(String value) {
    for (String pattern :
        new String[] {"dd MMM yyyy", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd"}) {
      try {
        java.text.SimpleDateFormat input = new java.text.SimpleDateFormat(pattern, Locale.US);
        input.setLenient(false);
        java.util.Date date = input.parse(value);
        if (date != null)
          return new java.text.SimpleDateFormat("dd MMM yyyy", Locale.US).format(date);
      } catch (Exception ignored) {
      }
    }
    return "";
  }

  private static Bitmap poster(String url) {
    if (!url.startsWith("https://")) return null;
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(url).openConnection();
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      try (InputStream input = connection.getInputStream()) {
        return BitmapFactory.decodeStream(input);
      }
    } catch (Exception ignored) {
      return null;
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  private static String get(String url) throws Exception {
    return get(url, "");
  }

  static String get(String url, String apiKey) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    try {
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; Bravia Home)");
      if (!apiKey.isEmpty()) connection.setRequestProperty("X-API-Key", apiKey);
      try (InputStream input = connection.getInputStream();
          ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[4096];
        int n;
        while ((n = input.read(buffer)) != -1) {
          if (output.size() + n > 1024 * 1024)
            throw new IllegalStateException("Response too large");
          output.write(buffer, 0, n);
        }
        return output.toString("UTF-8");
      }
    } finally {
      connection.disconnect();
    }
  }

  private MovieSearch() {}
}
