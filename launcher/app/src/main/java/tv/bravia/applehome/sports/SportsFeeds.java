package tv.bravia.applehome.sports;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/**
 * Public publisher scoreboards and Sony LIV's own live-event catalog. No inferred streaming rights.
 */
final class SportsFeeds {
  static final class Event {
    String id = "",
        sport = "",
        title = "",
        competition = "",
        detail = "",
        score = "",
        source = "",
        page = "",
        pkg = "",
        play = "",
        footballLeague = "",
        lookupSlug = "";
    long start, end, updated = System.currentTimeMillis();
    boolean live;
    final Map<String, String> streams = new LinkedHashMap<>();
    final Set<String> direct = new HashSet<>();
  }

  static List<Event> fetch(int source) throws Exception {
    if (source == 4) return SportsAvailability.fetch();
    if (source == 0) return sony(get("https://www.sonyliv.com/custompage/sports-2245"));
    if (source == 1)
      return espn(get("https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard"), true);
    if (source == 2) return football();
    List<Event> scores = cricket(get("https://www.cricbuzz.com/cricket-match/live-scores"));
    try {
      SportsCricketAvailability.enrich(scores);
    } catch (Exception failure) {
      android.util.Log.w(
          "BraviaSports", "Cricket availability: " + failure.getClass().getSimpleName());
    }
    return scores;
  }

  static List<Event> football() throws Exception {
    List<Event> out = new ArrayList<>();
    Exception failure = null;
    int successful = 0;
    Calendar today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
    Calendar next = (Calendar) today.clone();
    next.add(Calendar.DAY_OF_MONTH, 7);
    SimpleDateFormat month = new SimpleDateFormat("yyyyMM", Locale.US);
    month.setTimeZone(today.getTimeZone());
    Set<String> months = new LinkedHashSet<>();
    months.add(month.format(today.getTime()));
    months.add(month.format(next.getTime()));
    for (String league : new String[] {"uefa.champions", "esp.1", "eng.1"})
      for (String dates : months) {
        try {
          List<Event> games =
              espn(
                  get(
                      "https://site.api.espn.com/apis/site/v2/sports/soccer/"
                          + league
                          + "/scoreboard?dates="
                          + dates
                          + "&limit=1000"),
                  false);
          for (Event e : games) {
            e.footballLeague = league;
            e.competition =
                league.equals("uefa.champions")
                    ? "UEFA Champions League"
                    : league.equals("esp.1") ? "LaLiga" : "Premier League";
            if (allowedFootball(e)) out.add(e);
          }
          successful++;
        } catch (Exception e) {
          failure = e;
        }
      }
    if (successful == 0 && failure != null) throw failure;
    Collections.sort(
        out, Comparator.comparingInt((Event e) -> e.live ? 0 : 1).thenComparingLong(e -> e.start));
    // Fetch only the nearest eligible matches, not every match in the monthly schedule.
    int checked = 0;
    long now = System.currentTimeMillis();
    for (Event e : out)
      if ((e.live || e.start >= now - 3600000L)
          && e.start <= now + 7L * 86400000
          && checked++ < 3) {
        try {
          SportsBroadcastLookup.enrich(e);
        } catch (Exception ignored) {
        }
      }
    return out;
  }

  /** Apply to every source, including publisher streams and India viewing offers. */
  static boolean allowedFootball(Event e) {
    if (!e.sport.equals("Football")) return true;
    String competition = (e.competition + " " + e.page).toLowerCase(Locale.US).replace('-', ' ');
    String title = e.title.toLowerCase(Locale.US);
    String league = e.footballLeague;
    if (league.isEmpty()) {
      if (competition.contains("champions league") && !competition.contains("women"))
        league = "uefa.champions";
      else if (competition.contains("laliga") || competition.contains("la liga")) league = "esp.1";
      else if (competition.contains("premier league") && !competition.contains("indian"))
        league = "eng.1";
    }
    return league.equals("uefa.champions")
        || league.equals("esp.1") && title.matches(".*\\b(?:fc )?barcelona\\b.*")
        || league.equals("eng.1") && title.matches(".*\\bmanchester united\\b.*");
  }

  static List<Event> espn(String body, boolean f1) throws Exception {
    List<Event> out = new ArrayList<>();
    JSONArray events = new JSONObject(body).optJSONArray("events");
    if (events == null) return out;
    for (int i = 0; i < events.length(); i++) {
      JSONObject e = events.getJSONObject(i);
      JSONArray cs = e.optJSONArray("competitions");
      if (cs == null) continue;
      for (int j = 0; j < cs.length(); j++) {
        JSONObject c = cs.getJSONObject(j), status = c.optJSONObject("status");
        if (status == null) status = e.optJSONObject("status");
        JSONObject type = status == null ? null : status.optJSONObject("type");
        if (type == null) continue;
        String state = type.optString("state");
        if (state.equals("post")) continue;
        Event item = new Event();
        item.id = "espn:" + c.optString("id", e.optString("id"));
        item.sport = f1 ? "F1" : "Football";
        item.title = e.optString("name");
        item.live = state.equals("in");
        item.start = date(c.optString("date", e.optString("date")));
        item.source = "ESPN";
        item.detail = type.optString("shortDetail");
        item.competition = c.optString("altGameNote", f1 ? "Formula 1" : "Football");
        if (f1) {
          JSONObject session = c.optJSONObject("type");
          item.detail = session == null ? "Race weekend" : session.optString("abbreviation");
          item.pkg = "com.formulaone.production";
          item.streams.put(item.pkg, "");
        } else {
          JSONArray teams = c.optJSONArray("competitors");
          List<String> scores = new ArrayList<>();
          if (teams != null)
            for (int k = 0; k < teams.length(); k++) {
              JSONObject team = teams.getJSONObject(k), name = team.optJSONObject("team");
              if (name != null)
                scores.add(name.optString("abbreviation") + " " + team.optString("score", "0"));
            }
          if (teams != null) {
            String home = "", away = "";
            for (int k = 0; k < teams.length(); k++) {
              JSONObject side = teams.getJSONObject(k), team = side.optJSONObject("team");
              if (team == null) continue;
              if (side.optString("homeAway").equals("home")) home = team.optString("displayName");
              else if (side.optString("homeAway").equals("away"))
                away = team.optString("displayName");
            }
            if (!home.isEmpty() && !away.isEmpty())
              item.lookupSlug =
                  (home + " vs " + away)
                      .toLowerCase(Locale.US)
                      .replaceAll("[^a-z0-9]+", "-")
                      .replaceAll("^-|-$", "");
          }
          if (item.live) item.score = join(scores, "  —  ");
        }
        JSONArray links = e.optJSONArray("links");
        if (links != null && links.length() > 0)
          item.page = links.getJSONObject(0).optString("href");
        if (item.live || item.start >= System.currentTimeMillis() - 3600000L) out.add(item);
      }
    }
    return out;
  }

  static List<Event> sony(String html) throws Exception {
    int marker = html.indexOf("window.INITIAL_STATE");
    if (marker < 0) throw new IOException("Sony page schema unavailable");
    int start = html.indexOf('{', marker);
    JSONObject root = (JSONObject) new JSONTokener(html.substring(start)).nextValue();
    Map<String, String> urls = new HashMap<>();
    Matcher links = Pattern.compile("href=\"([^\"]*(?:live-sport|sports)/[^\"]+)\"").matcher(html);
    while (links.find()) {
      String url = links.group(1).replace("&amp;", "&");
      Matcher id = Pattern.compile("-([0-9]{8,})(?:\\?|$)").matcher(url);
      if (id.find())
        urls.put(id.group(1), url.startsWith("https://") ? url : "https://www.sonyliv.com" + url);
    }
    List<Event> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    walkSony(root, urls, out, seen);
    return out;
  }

  static void walkSony(Object value, Map<String, String> urls, List<Event> out, Set<String> seen)
      throws Exception {
    if (value instanceof JSONObject) {
      JSONObject node = (JSONObject) value, m = node.optJSONObject("metadata");
      if (m != null
          && m.optBoolean("isLive")
          && m.optBoolean("isOnAir")
          && !m.optBoolean("isGeoBlocked")) {
        String id = m.optString("contentId", node.optString("id"));
        long now = System.currentTimeMillis();
        long start = m.optLong("contractStartDate"), end = m.optLong("contractEndDate");
        if (start <= now && (end == 0 || end > now) && seen.add(id) && urls.containsKey(id)) {
          Event e = new Event();
          e.id = "sony:" + id;
          e.title = m.optString("episodeTitle", m.optString("title"));
          e.competition = m.optString("title");
          e.start = m.optLong("originalAirDate", start);
          e.end = end;
          e.live = true;
          JSONArray genres = m.optJSONArray("genres");
          String genre = genres == null ? "" : genres.toString().toLowerCase(Locale.US);
          e.sport =
              genre.contains("cricket")
                  ? "Cricket"
                  : genre.contains("football") ? "Football" : "Sport";
          e.detail = "Live stream";
          e.pkg = "com.sonyliv";
          e.play = urls.get(id);
          e.page = e.play;
          e.source = "Sony LIV";
          e.streams.put(e.pkg, e.play);
          e.direct.add(e.pkg);
          out.add(e);
        }
      }
      Iterator<String> keys = node.keys();
      while (keys.hasNext()) walkSony(node.get(keys.next()), urls, out, seen);
    } else if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < array.length(); i++) walkSony(array.get(i), urls, out, seen);
    }
  }

  static List<Event> cricket(String html) throws Exception {
    List<Event> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Matcher flight =
        Pattern.compile("self\\.__next_f\\.push\\(\\[1,(\"(?:\\\\.|[^\"\\\\])*\")\\]\\)")
            .matcher(html);
    while (flight.find()) {
      String payload = new JSONArray("[" + flight.group(1) + "]").getString(0);
      int start = payload.indexOf('{');
      if (start >= 0)
        try {
          walkCricket(new JSONTokener(payload.substring(start)).nextValue(), out, seen);
        } catch (Exception ignored) {
        }
    }
    if (out.isEmpty()) throw new IOException("Cricket page schema unavailable");
    return out;
  }

  static void walkCricket(Object value, List<Event> out, Set<String> seen) throws Exception {
    if (value instanceof JSONObject) {
      JSONObject o = (JSONObject) value, m = o.optJSONObject("matchInfo");
      if (m != null) {
        String state = m.optString("state").toLowerCase(Locale.US), id = m.optString("matchId");
        if (!state.contains("complete")
            && !state.contains("result")
            && !state.contains("abandon")
            && seen.add(id)) {
          JSONObject a = m.optJSONObject("team1"), b = m.optJSONObject("team2");
          if (a != null && b != null) {
            Event e = new Event();
            e.id = "cricket:" + id;
            e.sport = "Cricket";
            e.title = a.optString("teamName") + " vs " + b.optString("teamName");
            e.competition = m.optString("seriesName");
            e.detail = m.optString("status");
            e.start = m.optLong("startDate");
            e.end = m.optLong("endDate");
            e.live =
                state.contains("progress")
                    || state.contains("live")
                    || state.contains("innings")
                    || state.contains("stump");
            if (state.contains("toss")) e.detail = "Toss · " + e.detail;
            JSONObject score = o.optJSONObject("matchScore");
            List<String> scores = new ArrayList<>();
            if (score != null)
              for (int i = 1; i <= 2; i++) {
                JSONObject team = score.optJSONObject("team" + i + "Score");
                if (team == null) continue;
                JSONObject innings = team.optJSONObject("inngs1");
                if (innings != null)
                  scores.add(
                      (i == 1 ? a : b).optString("teamSName")
                          + " "
                          + innings.optInt("runs")
                          + "/"
                          + innings.optInt("wickets")
                          + " ("
                          + innings.optString("overs")
                          + ")");
              }
            e.score = join(scores, "  ·  ");
            e.page = "https://www.cricbuzz.com/live-cricket-scores/" + id;
            e.source = "Cricbuzz";
            if (e.live || e.start > System.currentTimeMillis() - 3600000L) out.add(e);
          }
        }
      }
      Iterator<String> keys = o.keys();
      while (keys.hasNext()) walkCricket(o.get(keys.next()), out, seen);
    } else if (value instanceof JSONArray) {
      JSONArray a = (JSONArray) value;
      for (int i = 0; i < a.length(); i++) walkCricket(a.get(i), out, seen);
    }
  }

  static long date(String iso) {
    try {
      return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli();
    } catch (Exception ignored) {
      return 0;
    }
  }

  static String join(List<String> list, String separator) {
    StringBuilder s = new StringBuilder();
    for (String a : list) {
      if (s.length() > 0) s.append(separator);
      s.append(a);
    }
    return s.toString();
  }

  static String get(String address) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
    try {
      c.setConnectTimeout(10000);
      c.setReadTimeout(30000);
      c.setRequestProperty(
          "User-Agent",
          "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/121.0 Safari/537.36"
              + " BraviaHome/1.0");
      try (InputStream in = c.getInputStream()) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) != -1) {
          out.write(b, 0, n);
          if (out.size() > 6 * 1024 * 1024) throw new IOException("Response too large");
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
      }
    } finally {
      c.disconnect();
    }
  }
}
