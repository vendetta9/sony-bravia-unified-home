package tv.bravia.applehome.sports;

import java.util.*;
import java.util.regex.*;
import org.json.*;

/**
 * Dated cricket BroadcastEvent metadata; separate from scores and never inferred from team
 * nationality.
 */
final class SportsCricketAvailability {
  static long fetched;
  static List<SportsFeeds.Event> cached = new ArrayList<>();

  static void enrich(List<SportsFeeds.Event> scores) throws Exception {
    long now = System.currentTimeMillis();
    if (now - fetched > 300000) {
      List<SportsFeeds.Event> ready = new ArrayList<>();
      String schedule = SportsFeeds.get("https://tryball.in/women/schedule");
      Set<String> links = new LinkedHashSet<>();
      Matcher paths =
          Pattern.compile("https://tryball\\.in/women/match/[^\\\"<> ]+").matcher(schedule);
      while (paths.find()) links.add(paths.group());
      int checked = 0;
      for (String link : links) {
        Matcher day = Pattern.compile("(20[0-9]{2}-[0-9]{2}-[0-9]{2})$").matcher(link);
        if (!day.find()) continue;
        long date = SportsFeeds.date(day.group(1) + "T00:00:00Z");
        if (date < now - 86400000L || date > now + 7L * 86400000 || checked++ >= 5) continue;
        try {
          ready.addAll(parse(SportsFeeds.get(link)));
        } catch (Exception ignored) {
        }
      }
      cached = ready;
      fetched = now;
    }
    for (SportsFeeds.Event e : scores)
      for (SportsFeeds.Event offer : cached) {
        if (!e.sport.equals("Cricket") || Math.abs(e.start - offer.start) > 300000L) continue;
        boolean women = e.title.toLowerCase(Locale.US).contains("women");
        if (women != offer.title.toLowerCase(Locale.US).contains("women")) continue;
        if (identity(e.title).equals(identity(offer.title))) {
          e.streams.putAll(offer.streams);
          if (!offer.streams.isEmpty()) e.source += " + TryBall";
        }
      }
  }

  static List<SportsFeeds.Event> parse(String html) throws Exception {
    List<SportsFeeds.Event> out = new ArrayList<>();
    Matcher scripts =
        Pattern.compile(
                "<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>", Pattern.DOTALL)
            .matcher(html);
    while (scripts.find()) {
      JSONObject root = new JSONObject(scripts.group(1));
      JSONArray graph = root.optJSONArray("@graph");
      if (graph == null) continue;
      for (int i = 0; i < graph.length(); i++) {
        JSONObject event = graph.optJSONObject(i);
        if (event == null
            || !event.optString("@type").equals("SportsEvent")
            || !event.optString("sport").equals("Cricket")) continue;
        JSONObject home = event.optJSONObject("homeTeam"), away = event.optJSONObject("awayTeam");
        if (home == null || away == null) continue;
        SportsFeeds.Event e = new SportsFeeds.Event();
        e.sport = "Cricket";
        e.title = home.optString("name") + " vs " + away.optString("name");
        e.start = SportsFeeds.date(event.optString("startDate"));
        String id = event.optString("@id");
        if (e.start == 0 || id.isEmpty()) continue;
        for (int j = 0; j < graph.length(); j++) {
          JSONObject broadcast = graph.optJSONObject(j);
          if (broadcast == null || !broadcast.optString("@type").equals("BroadcastEvent")) continue;
          JSONObject target = broadcast.optJSONObject("broadcastOfEvent"),
              service = broadcast.optJSONObject("publishedOn");
          if (target == null || service == null || !id.equals(target.optString("@id"))) continue;
          JSONObject country = service.optJSONObject("areaServed");
          if (country == null || !country.optString("name").equals("India")) continue;
          String name = service.optString("name").toLowerCase(Locale.US);
          if (name.equals("jiohotstar")) e.streams.put("in.startv.hotstar", "");
          else if (name.equals("sonyliv") || name.equals("sony liv"))
            e.streams.put("com.sonyliv", "");
        }
        if (!e.streams.isEmpty()) out.add(e);
      }
    }
    return out;
  }

  static String identity(String title) {
    List<String> tokens = new ArrayList<>();
    for (String word : title.toLowerCase(Locale.US).split("[^a-z]+"))
      if (!word.isEmpty() && !word.equals("women") && !word.equals("vs") && !word.equals("at"))
        tokens.add(word);
    Collections.sort(tokens);
    return tokens.toString();
  }
}
