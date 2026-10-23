package tv.bravia.applehome.sports;

import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Event-specific India listings discovered via web search; never use a global channel list. */
final class SportsBroadcastLookup {
  static void enrich(SportsFeeds.Event e) throws Exception {
    if (!e.sport.equals("Football") || !e.id.startsWith("espn:") || e.lookupSlug.isEmpty()) return;
    String id = e.id.substring(5);
    if (!id.matches("[0-9]+")) return;
    String url =
        "https://tvkickoff.com/en/match/" + e.footballLeague + "/" + id + "/" + e.lookupSlug;
    String html = SportsFeeds.get(url);
    Matcher schema =
        Pattern.compile(
                "<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>", Pattern.DOTALL)
            .matcher(html);
    boolean match = false;
    while (schema.find()) {
      JSONObject data = new JSONObject(schema.group(1));
      if (!data.optString("@type").equals("SportsEvent")) continue;
      String canonical = data.optString("url");
      long start = SportsFeeds.date(data.optString("startDate"));
      if (canonical.contains("/" + e.footballLeague + "/" + id + "/")
          && start > 0
          && Math.abs(start - e.start) < 60000) match = true;
    }
    if (!match) return;
    Matcher india =
        Pattern.compile(
                "<li>\\s*<b>India:</b>(.*?)</li>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE)
            .matcher(html);
    if (!india.find()) return;
    String channels = india.group(1).replaceAll("<[^>]+>", " ").toLowerCase(Locale.US);
    boolean found = false;
    if (channels.contains("jiohotstar") || channels.contains("disney+ hotstar")) {
      e.streams.putIfAbsent("in.startv.hotstar", "");
      found = true;
    }
    if (channels.contains("sony liv") || channels.contains("sonyliv")) {
      e.streams.putIfAbsent("com.sonyliv", "");
      found = true;
    }
    // A broadcaster listing proves coverage, but does not supply a playable event deep link.
    if (found) e.source += " + TV Kickoff";
  }
}
