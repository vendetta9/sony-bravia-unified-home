package tv.bravia.applehome.device;

import android.content.Context;
import android.media.AudioManager;
import android.os.*;
import android.view.KeyEvent;

/** Tap/native-repeat/timer-repeat volume handling shared by both home screens. */
public final class VolumeController {
  private final AudioManager audioManager;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private int volumeDirection;
  private boolean volumeSystemRepeating;
  private final Runnable volumeRepeatAction =
      new Runnable() {
        public void run() {
          if (volumeDirection == 0 || volumeSystemRepeating) return;
          adjustVolume(volumeDirection);
          handler.postDelayed(this, 120);
        }
      };

  public VolumeController(Context c) {
    audioManager = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
  }

  public boolean isMusicActive() {
    return audioManager != null && audioManager.isMusicActive();
  }

  void adjustVolume(int direction) {
    if (audioManager != null) {
      audioManager.adjustStreamVolume(
          AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
    }
  }

  public void stopVolumeRepeating() {
    handler.removeCallbacks(volumeRepeatAction);
    volumeDirection = 0;
    volumeSystemRepeating = false;
  }

  public boolean handleVolumeKeyDown(KeyEvent e) {
    int direction =
        e.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
            ? AudioManager.ADJUST_RAISE
            : AudioManager.ADJUST_LOWER;
    if (e.getRepeatCount() == 0) {
      stopVolumeRepeating();
      volumeDirection = direction;
      adjustVolume(direction);
      handler.postDelayed(volumeRepeatAction, 500);
    } else {
      if (volumeDirection != direction) {
        stopVolumeRepeating();
        volumeDirection = direction;
      }
      // Prefer native key repeats when the remote supplies them; the
      // timed fallback covers remotes that send only one key-down.
      volumeSystemRepeating = true;
      handler.removeCallbacks(volumeRepeatAction);
      adjustVolume(direction);
    }
    return true;
  }

  public boolean handleVolumeKeyUp() {
    stopVolumeRepeating();
    return true;
  }
}
