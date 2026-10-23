package tv.bravia.applehome.device;

import android.content.*;
import android.content.pm.*;

/** Resolves installed/enabled stock HOME activities; Sony Live TV is never a stock launcher. */
public final class AndroidHomeResolver {
  public Intent resolve(Context context) {
    Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    for (String name :
        new String[] {"com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher"})
      for (ResolveInfo result : context.getPackageManager().queryIntentActivities(query, 0))
        if (result.activityInfo != null && name.equals(result.activityInfo.packageName))
          return new Intent(query).setComponent(new ComponentName(name, result.activityInfo.name));
    return null;
  }
}
