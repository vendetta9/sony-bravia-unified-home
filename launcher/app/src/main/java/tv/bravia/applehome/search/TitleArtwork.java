package tv.bravia.applehome.search;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.json.JSONArray;
import org.json.JSONObject;

/** Original-resolution TMDB backdrops and title logos, matched by provider TMDB ID. */
final class TitleArtwork {
  static final class Images {
    String backdrop = "", logo = "";
    int width, height;
  }

  static Images lookup(Context context, String id) {
    Images images = new Images();
    if (!id.matches("(movie|tv)/[0-9]+")) return images;
    try {
      Properties keys = new Properties();
      try (InputStream in = context.getAssets().open("movie-api.properties")) {
        keys.load(in);
      }
      String token = keys.getProperty("tmdb", "").trim();
      if (token.isEmpty()) return images;
      File cache = new File(context.getCacheDir(), "tmdb-art-" + id.replace('/', '-') + ".json");
      String body;
      if (cache.isFile() && System.currentTimeMillis() - cache.lastModified() < 7L * 86400000L) {
        try (InputStream in = new FileInputStream(cache)) {
          body = new String(read(in), StandardCharsets.UTF_8);
        }
      } else {
        HttpURLConnection c =
            (HttpURLConnection)
                new URL("https://api.tmdb.org/3/" + id + "/images?include_image_language=en,null")
                    .openConnection();
        try {
          c.setConnectTimeout(6000);
          c.setReadTimeout(10000);
          c.setRequestProperty("Authorization", "Bearer " + token);
          c.setRequestProperty("Accept", "application/json");
          try (InputStream in = c.getInputStream()) {
            body = new String(read(in), StandardCharsets.UTF_8);
          }
        } finally {
          c.disconnect();
        }
        new JSONObject(body);
        try (FileOutputStream out = new FileOutputStream(cache)) {
          out.write(body.getBytes(StandardCharsets.UTF_8));
        }
      }
      JSONObject data = new JSONObject(body);
      JSONArray backdrops = data.optJSONArray("backdrops");
      long best = -1;
      if (backdrops != null)
        for (int i = 0; i < backdrops.length(); i++) {
          JSONObject a = backdrops.getJSONObject(i);
          int w = a.optInt("width"), h = a.optInt("height");
          if (h <= 0 || w / (float) h < 1.4f || w / (float) h > 2.2f) continue;
          long score = (long) w * h;
          if (score > best) {
            best = score;
            images.backdrop = "https://image.tmdb.org/t/p/original" + a.getString("file_path");
            images.width = w;
            images.height = h;
          }
        }
      JSONArray logos = data.optJSONArray("logos");
      double logoScore = -1;
      if (logos != null)
        for (int i = 0; i < logos.length(); i++) {
          JSONObject a = logos.getJSONObject(i);
          String path = a.optString("file_path");
          if (!path.endsWith(".png")) continue;
          double score =
              (a.optString("iso_639_1").equals("en") ? 100000000 : 0)
                  + a.optDouble("vote_average") * 100000
                  + a.optInt("width");
          if (score > logoScore) {
            logoScore = score;
            images.logo = "https://image.tmdb.org/t/p/original" + path;
          }
        }
    } catch (Exception ignored) {
    }
    return images;
  }

  private static byte[] read(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] b = new byte[8192];
    int n;
    while ((n = in.read(b)) != -1) out.write(b, 0, n);
    return out.toByteArray();
  }
}
