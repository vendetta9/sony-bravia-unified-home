package tv.bravia.applehome.device;

import android.content.Context;
import android.net.wifi.*;
import android.os.*;
import java.io.*;
import java.net.*;
import org.json.JSONObject;

/**
 * Read-only Wi-Fi/temperature polling. Activity lifecycle controls start/stop; never toggles Wi-Fi.
 */
public final class DeviceStatusController {
  public interface Listener {
    void onWifi(String name, Integer rssi);

    void onWeather(Integer celsius);
  }

  private final Context context;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private boolean active, disposed, fetching;
  private Integer temperature;
  private long weatherAt;
  private final Runnable wifi =
      new Runnable() {
        public void run() {
          if (active) {
            refreshWifi();
            main.postDelayed(this, 10000);
          }
        }
      };
  private final Runnable weather =
      new Runnable() {
        public void run() {
          if (active) {
            refreshWeather();
            main.postDelayed(this, 15 * 60000);
          }
        }
      };

  public DeviceStatusController(Context c, Listener listener) {
    context = c.getApplicationContext();
    this.listener = listener;
  }

  public void start() {
    if (active || disposed) return;
    active = true;
    main.post(wifi);
    if (temperature != null) listener.onWeather(temperature);
    main.post(weather);
  }

  public void stop() {
    active = false;
    main.removeCallbacks(wifi);
    main.removeCallbacks(weather);
  }

  public void dispose() {
    stop();
    disposed = true;
    main.removeCallbacksAndMessages(null);
  }

  public void refreshWifi() {
    String name = "Wi-Fi";
    Integer rssi = null;
    try {
      WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
      if (manager != null) {
        if (!manager.isWifiEnabled()) name = "Wi-Fi Off";
        else {
          WifiInfo info = manager.getConnectionInfo();
          if (info != null) {
            String ssid = info.getSSID();
            if (ssid != null && !WifiManager.UNKNOWN_SSID.equals(ssid)) {
              if (ssid.startsWith("\"") && ssid.endsWith("\""))
                ssid = ssid.substring(1, ssid.length() - 1);
              if (!ssid.isEmpty()) name = ssid;
            }
            int value = info.getRssi();
            if (value > -127 && value <= 0) rssi = value;
          }
        }
      }
    } catch (Exception ignored) {
    }
    if (!disposed) listener.onWifi(name, rssi);
  }

  private void refreshWeather() {
    if (fetching || System.currentTimeMillis() - weatherAt < 15 * 60000) return;
    fetching = true;
    new Thread(
            () -> {
              Integer result = null;
              HttpURLConnection connection = null;
              try {
                connection =
                    (HttpURLConnection)
                        new URL(
                                "https://api.open-meteo.com/v1/forecast?latitude=18.61867&longitude=73.80375&current=temperature_2m")
                            .openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                try (InputStream in = connection.getInputStream();
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                  byte[] buffer = new byte[2048];
                  int n;
                  while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
                  result =
                      (int)
                          Math.round(
                              new JSONObject(bytes.toString("UTF-8"))
                                  .getJSONObject("current")
                                  .getDouble("temperature_2m"));
                }
              } catch (Exception ignored) {
              } finally {
                if (connection != null) connection.disconnect();
              }
              final Integer ready = result;
              main.post(
                  () -> {
                    fetching = false;
                    if (disposed) return;
                    if (ready != null) {
                      temperature = ready;
                      weatherAt = System.currentTimeMillis();
                      if (active) listener.onWeather(ready);
                    }
                  });
            },
            "bravia-weather")
        .start();
  }
}
