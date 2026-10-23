package tv.bravia.applehome.search;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.json.JSONArray;
import org.json.JSONObject;

/** India streaming offers, kept separate from movie metadata and search UI. */
final class MovieAvailability {
  private static String motnKey = "", watchmodeKey = "";
  private static File cacheDirectory;

  static void configure(Context context, Properties keys) {
    cacheDirectory = context.getCacheDir();
    motnKey = keys.getProperty("movieofthenight", "").trim();
    watchmodeKey = keys.getProperty("watchmode", "").trim();
  }

  static void load(MovieSearch.Feature movie) {
    Map<String, MovieSearch.Provider> providers = new LinkedHashMap<>();
    boolean succeeded = false;
    if (!motnKey.isEmpty())
      try {
        JSONObject show =
            new JSONObject(
                cached(
                    "motn-v1-",
                    movie.id,
                    "https://api.movieofthenight.com/v4/shows/" + movie.id,
                    motnKey,
                    true));
        if (!movie.id.equals(show.optString("imdbId")))
          throw new IllegalStateException("Title mismatch");
        JSONArray cast = show.optJSONArray("cast");
        if (cast != null)
          for (int i = 0; i < cast.length(); i++) MovieSearch.addCast(movie, cast.optString(i));
        JSONObject options = show.optJSONObject("streamingOptions");
        JSONArray india = options == null ? null : options.optJSONArray("in");
        if (india != null)
          for (int i = 0; i < india.length(); i++) {
            JSONObject option = india.getJSONObject(i);
            // Add-on offers require another subscription; do not label as base membership.
            JSONObject addon = option.optJSONObject("addon");
            String name = option.getJSONObject("service").optString("name");
            if (addon != null) name += " · " + addon.optString("name", "Add-on");
            add(providers, name, "", option.optString("link"), access(option.optString("type")));
          }
        succeeded = true;
        movie.availabilitySource = "Movie of the Night";
      } catch (Exception ignored) {
      }

    if (!watchmodeKey.isEmpty())
      try {
        JSONArray sources =
            new JSONArray(
                cached(
                    "watchmode-v1-",
                    movie.id,
                    "https://api.watchmode.com/v1/title/" + movie.id + "/sources/?regions=IN",
                    watchmodeKey,
                    false));
        for (int i = 0; i < sources.length(); i++) {
          JSONObject option = sources.getJSONObject(i);
          if (!"IN".equals(option.optString("region"))) continue;
          String tv = option.optString("android_tv_url", "");
          if (!tv.startsWith("intent:") && !tv.startsWith("https://")) tv = "";
          add(
              providers,
              option.optString("name"),
              tv,
              option.optString("web_url"),
              access(option.optString("type")));
        }
        succeeded = true;
        movie.availabilitySource =
            movie.availabilitySource.isEmpty()
                ? "Watchmode"
                : movie.availabilitySource + " + Watchmode";
      } catch (Exception ignored) {
      }

    // An offer through an add-on is redundant when the base service already includes the title.
    java.util.Set<String> baseServices = new java.util.HashSet<>(providers.keySet());
    providers
        .entrySet()
        .removeIf(
            entry -> {
              int separator = entry.getKey().indexOf(" · ");
              return separator > 0 && baseServices.contains(entry.getKey().substring(0, separator));
            });
    List<MovieSearch.Provider> sorted = new ArrayList<>(providers.values());
    sorted.sort(Comparator.comparingInt(p -> rank(p.access)));
    for (MovieSearch.Provider provider : sorted) {
      movie.providers.add(provider);
    }
    if (!succeeded) movie.availabilityError = "Viewing options unavailable — try the search again";
  }

  static MovieSearch.Lookup searchMovies(String title) {
    MovieSearch.Lookup result = new MovieSearch.Lookup();
    if (motnKey.isEmpty()) {
      result.completed = true;
      return result;
    }
    try {
      JSONArray found =
          new JSONArray(
              MovieSearch.get(
                  "https://api.movieofthenight.com/v4/shows/search/title?country=in&title="
                      + java.net.URLEncoder.encode(title, "UTF-8"),
                  motnKey));
      result.completed = true;
      for (int i = 0; i < found.length() && result.movies.size() < 8; i++) {
        JSONObject movie = found.getJSONObject(i);
        if (!"movie".equals(movie.optString("showType"))) continue;
        String id = movie.optString("imdbId", "");
        if (id.matches("tt[0-9]+"))
          result.movies.add(
              new MovieSearch.Movie(
                  id, movie.optString("title", "Movie"), movie.optString("releaseYear", "")));
      }
    } catch (Exception ignored) {
    }
    return result;
  }

  private static String access(String type) {
    if (type.equals("subscription") || type.equals("sub")) return "Subscription";
    if (type.equals("rent")) return "Rent";
    if (type.equals("buy")) return "Buy";
    if (type.equals("free")) return "Free";
    if (type.equals("addon")) return "Add-on subscription";
    return "View options";
  }

  private static int rank(String access) {
    return access.equals("Subscription") ? 0 : access.equals("Free") ? 1 : 2;
  }

  private static void add(
      Map<String, MovieSearch.Provider> providers,
      String name,
      String tv,
      String web,
      String access) {
    if (name.equals("Amazon") || name.equals("Amazon Prime Video")) name = "Prime Video";
    if (name.equals("Hotstar")) name = "JioHotstar";
    if (name.equals("AppleTV") || name.equals("Apple TV Store")) name = "Apple TV";
    if (name.isEmpty() || !web.startsWith("https://")) return;
    MovieSearch.Provider prior = providers.get(name);
    if (prior == null || rank(access) < rank(prior.access)) {
      // Keep the subscription URL when a service also advertises a rental edition.
      providers.put(name, new MovieSearch.Provider(name, tv, web, access));
    } else if (rank(prior.access) == 2 && rank(access) == 2 && !prior.access.contains(access)) {
      prior.access += " · " + access;
    }
  }

  private static String cached(String prefix, String id, String url, String key, boolean show)
      throws Exception {
    File cache =
        cacheDirectory == null ? null : new File(cacheDirectory, prefix + "in-" + id + ".json");
    if (cache != null
        && cache.isFile()
        && System.currentTimeMillis() - cache.lastModified() < 21600000L) {
      try {
        return new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8);
      } catch (Exception ignored) {
      }
    }
    String response = MovieSearch.get(url, key);
    if (show) {
      JSONObject data = new JSONObject(response);
      if (!id.equals(data.optString("imdbId")) || !data.has("streamingOptions"))
        throw new IllegalStateException("Invalid availability response");
    } else new JSONArray(response);
    if (cache != null)
      try {
        Files.write(cache.toPath(), response.getBytes(StandardCharsets.UTF_8));
      } catch (Exception ignored) {
      }
    return response;
  }
}
