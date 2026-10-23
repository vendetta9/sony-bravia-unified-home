package tv.bravia.applehome.sports;

import java.util.*;
import java.util.regex.*;
import org.json.*;

/**
 * India-specific event offers from JustWatch's public sports page. Never infer providers by sport.
 */
final class SportsAvailability {
  static List<SportsFeeds.Event> fetch() throws Exception {
    String html = SportsFeeds.get("https://sports.justwatch.com/in");
    Matcher m =
        Pattern.compile("<script[^>]*id=\"__NUXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL)
            .matcher(html);
    if (!m.find()) throw new java.io.IOException("India availability schema unavailable");
    JSONArray nodes = new JSONArray(m.group(1));
    Map<String, JSONObject> records = new HashMap<>();
    for (int i = 0; i < nodes.length(); i++) {
      JSONObject o = nodes.optJSONObject(i);
      if (o == null) continue;
      String type = str(nodes, o, "__typename"), id = str(nodes, o, "id");
      if (!id.isEmpty()) {
        String key = type + ":" + id;
        JSONObject old = records.get(key);
        if (old == null || old.length() < o.length()) records.put(key, o);
      }
    }
    List<SportsFeeds.Event> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < nodes.length(); i++) {
      JSONObject raw = nodes.optJSONObject(i);
      if (raw == null) continue;
      String sport = str(nodes, raw, "sport");
      if (!sport.equals("FOOTBALL") && !sport.equals("CRICKET") && !sport.equals("FORMULA_RACING"))
        continue;
      String offerKey = key(raw, "offers(");
      if (offerKey.isEmpty()) continue;
      JSONObject content = obj(nodes, raw, key(raw, "content("), records);
      if (content == null) continue;
      SportsFeeds.Event e = new SportsFeeds.Event();
      e.id = "jw:" + str(nodes, raw, "id");
      if (!seen.add(e.id)) continue;
      e.sport = sport.equals("FOOTBALL") ? "Football" : sport.equals("CRICKET") ? "Cricket" : "F1";
      e.start = SportsFeeds.date(str(nodes, content, "startAt"));
      String status = str(nodes, content, "status");
      if (status.equals("FINISHED") || status.equals("CANCELLED")) continue;
      e.live = status.equals("LIVE") || status.equals("IN_PROGRESS");
      if (!e.live && e.start < System.currentTimeMillis() - 24L * 3600000L) continue;
      e.title = str(nodes, content, "name");
      e.source = "JustWatch India";
      JSONObject competition = obj(nodes, raw, "competition", records);
      if (competition != null) {
        JSONObject cc = obj(nodes, competition, key(competition, "content("), records);
        if (cc != null) e.competition = str(nodes, cc, "name");
      }
      if (e.title.isEmpty()) {
        JSONObject competitors = obj(nodes, raw, "competitors", records);
        if (competitors != null)
          e.title =
              team(nodes, competitors, "homeTeam", records)
                  + " vs "
                  + team(nodes, competitors, "awayTeam", records);
      }
      if (e.sport.equals("F1")) {
        JSONObject parent = obj(nodes, raw, "parent", records);
        if (parent != null) {
          JSONObject pc = obj(nodes, parent, key(parent, "content("), records);
          if (pc != null) e.title = str(nodes, pc, "name") + " · " + e.title;
        }
        if (e.title.trim().length() < 4) e.title = "Formula 1 · " + e.competition;
      }
      String path = str(nodes, content, "fullPath");
      e.page =
          path.isEmpty()
              ? "https://sports.justwatch.com/in"
              : "https://sports.justwatch.com" + path;
      Object offers = ref(nodes, raw.opt(offerKey));
      if (offers instanceof JSONArray)
        for (int j = 0; j < ((JSONArray) offers).length(); j++) {
          Object offerValue = ref(nodes, ((JSONArray) offers).opt(j));
          if (!(offerValue instanceof JSONObject)) continue;
          JSONObject offer = (JSONObject) offerValue, pkg = obj(nodes, offer, "package", records);
          if (pkg == null) continue;
          String name = str(nodes, pkg, "clearName").toLowerCase(Locale.US),
              url = str(nodes, offer, "standardWebURL"),
              app = "";
          if (name.contains("hotstar")) app = "in.startv.hotstar";
          else if (name.contains("sony")) app = "com.sonyliv";
          else if (name.contains("f1 tv") && e.sport.equals("F1"))
            app = "com.formulaone.production";
          if (app.isEmpty()) continue;
          String host = "";
          try {
            host = new java.net.URI(url).getHost();
          } catch (Exception ignored) {
          }
          boolean safe =
              host != null
                  && (host.equals("hotstar.com")
                      || host.endsWith(".hotstar.com")
                      || host.equals("jiohotstar.com")
                      || host.endsWith(".jiohotstar.com")
                      || host.equals("sonyliv.com")
                      || host.endsWith(".sonyliv.com")
                      || host.equals("formula1.com")
                      || host.endsWith(".formula1.com"));
          e.streams.put(app, safe ? url : "");
          if (safe
              && ((app.contains("hotstar")
                      && url.contains("/sports/")
                      && url.matches(".*[0-9]{8,}.*"))
                  || (app.contains("sonyliv")
                      && url.contains("/live-sport/")
                      && url.contains("watch=true")))) e.direct.add(app);
        }
      if (!e.streams.isEmpty()) result.add(e);
    }
    return result;
  }

  static String team(JSONArray n, JSONObject c, String name, Map<String, JSONObject> records)
      throws Exception {
    JSONObject side = obj(n, c, name, records);
    if (side == null) return "";
    Object a = ref(n, side.opt("competitors"));
    if (a instanceof JSONArray && ((JSONArray) a).length() > 0) {
      JSONObject t = resolve(n, ref(n, ((JSONArray) a).opt(0)), records);
      if (t != null) {
        JSONObject content = obj(n, t, key(t, "content("), records);
        if (content != null) return str(n, content, "name");
      }
    }
    return "";
  }

  static String key(JSONObject o, String prefix) {
    Iterator<String> keys = o.keys();
    while (keys.hasNext()) {
      String k = keys.next();
      if (k.startsWith(prefix)) return k;
    }
    return "";
  }

  static Object ref(JSONArray n, Object value) {
    if (value instanceof Number) {
      int i = ((Number) value).intValue();
      return i >= 0 && i < n.length() ? n.opt(i) : JSONObject.NULL;
    }
    return value;
  }

  static String str(JSONArray n, JSONObject o, String key) {
    Object value = ref(n, o.opt(key));
    return value == null || value == JSONObject.NULL ? "" : String.valueOf(value);
  }

  static JSONObject resolve(JSONArray n, Object v, Map<String, JSONObject> r) {
    if (!(v instanceof JSONObject)) return null;
    JSONObject o = (JSONObject) v;
    return o.has("__ref") ? r.get(str(n, o, "__ref")) : o;
  }

  static JSONObject obj(JSONArray n, JSONObject o, String k, Map<String, JSONObject> r) {
    return resolve(n, ref(n, o.opt(k)), r);
  }
}
