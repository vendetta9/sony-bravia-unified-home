package tv.bravia.applehome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import tv.bravia.applehome.core.HomeNavigator;

/** Android HOME entry point. Dispatches the saved mode and never renders either theme. */
public final class MainActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    route();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    route();
  }

  private void route() {
    new HomeNavigator(this).openSelected();
    finish();
  }
}
