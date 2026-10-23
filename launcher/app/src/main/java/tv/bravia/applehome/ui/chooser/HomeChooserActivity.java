package tv.bravia.applehome.ui.chooser;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import tv.bravia.applehome.core.*;

/** Available above either launcher or any app through the three-second Home gesture. */
public class HomeChooserActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    getWindow().setDimAmount(.55f);
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(36, 28, 36, 28);
    GradientDrawable background = new GradientDrawable();
    background.setColor(0xF01B2835);
    background.setCornerRadius(30);
    panel.setBackground(background);
    TextView title = new TextView(this);
    title.setText("Choose your home screen");
    title.setTextColor(Color.WHITE);
    title.setTextSize(26);
    title.setTypeface(null, Typeface.BOLD);
    panel.addView(title);
    TextView hint = new TextView(this);
    hint.setText("Home opens your selection. Hold Home for 3 seconds to switch anytime.");
    hint.setTextColor(0xFFD1DDE8);
    hint.setTextSize(16);
    hint.setPadding(0, 12, 0, 20);
    panel.addView(hint);
    HomeNavigator navigator = new HomeNavigator(this);
    HomeMode selected = navigator.selected();
    Button initial = null;
    for (HomeMode mode : HomeMode.values()) {
      Button button =
          choice(
              panel,
              mode.label + (mode == selected ? "  ·  Current" : ""),
              () -> {
                if (navigator.choose(mode)) finish();
              });
      if (mode == selected) initial = button;
    }
    choice(panel, "Cancel", this::finish);
    setContentView(panel);
    WindowManager.LayoutParams size = getWindow().getAttributes();
    size.width = 760;
    size.height = WindowManager.LayoutParams.WRAP_CONTENT;
    size.gravity = Gravity.CENTER;
    getWindow().setAttributes(size);
    if (initial != null) initial.requestFocus();
  }

  Button choice(LinearLayout panel, String title, Runnable action) {
    Button button = new Button(this);
    button.setText(title);
    button.setAllCaps(false);
    button.setTextSize(21);
    button.setGravity(Gravity.CENTER_VERTICAL);
    button.setPadding(24, 0, 24, 0);
    button.setFocusable(true);
    button.setOnFocusChangeListener(
        (v, focused) -> {
          GradientDrawable bg = new GradientDrawable();
          bg.setCornerRadius(30);
          bg.setColor(focused ? 0xFFF3F7FA : 0xB30B1623);
          v.setBackground(bg);
          button.setTextColor(focused ? 0xFF142838 : Color.WHITE);
        });
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(30);
    bg.setColor(0xB30B1623);
    button.setBackground(bg);
    button.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, 68);
    layout.topMargin = 10;
    panel.addView(button, layout);
    button.setOnClickListener(v -> action.run());
    return button;
  }
}
