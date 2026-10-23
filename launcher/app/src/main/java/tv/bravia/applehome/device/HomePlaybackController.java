package tv.bravia.applehome.device;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

/** One playback policy for every app when the launcher becomes foreground. */
public final class HomePlaybackController {
  private final AudioManager audio;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private AudioFocusRequest focus;
  private boolean visible;
  private boolean ownsFocus;
  private final Runnable settle =
      () -> {
        if (visible) pause();
      };

  public HomePlaybackController(Context context) {
    audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    focus =
        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build())
            .setOnAudioFocusChangeListener(
                change -> ownsFocus = change == AudioManager.AUDIOFOCUS_GAIN)
            .build();
  }

  public void enterHome() {
    visible = true;
    handler.removeCallbacks(settle);
    pause();
    // Catch players completing their transition/buffering just after Home.
    handler.postDelayed(settle, 350);
    handler.postDelayed(settle, 1200);
  }

  private void pause() {
    if (audio == null) return;
    // PAUSE is idempotent, unlike PLAY_PAUSE. Send even for muted/buffering video.
    audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
    audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
    if (!ownsFocus)
      ownsFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  }

  public void leaveHome() {
    visible = false;
    handler.removeCallbacks(settle);
    if (audio != null) audio.abandonAudioFocusRequest(focus);
    ownsFocus = false;
  }
}
