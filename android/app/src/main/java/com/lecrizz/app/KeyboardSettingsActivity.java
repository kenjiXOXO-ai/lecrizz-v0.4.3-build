package com.lecrizz.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Real, persistent keyboard preferences screen also exposed from Android's IME settings. */
public class KeyboardSettingsActivity extends Activity {
  private int white, muted, accent, background, panel, onAccent;
  private LinearLayout root;
  private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
  private void colors() {
    white = LecRizzPrefs.color(this, "text"); muted = LecRizzPrefs.color(this, "muted");
    accent = LecRizzPrefs.color(this, "accent"); background = LecRizzPrefs.color(this, "background");
    panel = LecRizzPrefs.color(this, "panel"); onAccent = LecRizzPrefs.color(this, "onAccent");
  }
  private GradientDrawable rounded(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
  private TextView text(String s, int size) { TextView t = new TextView(this); t.setText(s); t.setTextColor(white); t.setTextSize(size); t.setPadding(0, dp(4), 0, dp(4)); return t; }
  private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(onAccent); b.setTextSize(14); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(rounded(accent, 16)); return b; }

  @Override public void onCreate(Bundle b) {
    super.onCreate(b); colors(); getWindow().setStatusBarColor(background); getWindow().setNavigationBarColor(background);
    root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(18), dp(20), dp(24)); root.setBackgroundColor(background);
    root.setOnApplyWindowInsetsListener((v, insets) -> {
      v.setPadding(dp(20), dp(18) + insets.getSystemWindowInsetTop(), dp(20), dp(24) + insets.getSystemWindowInsetBottom());
      return insets;
    });
    ScrollView sc = new ScrollView(this); sc.setFillViewport(true); sc.addView(root); setContentView(sc);
    TextView back = text("‹  Back", 15); back.setTextColor(accent); back.setOnClickListener(v -> finish()); root.addView(back);
    TextView title = text("Keyboard preferences", 27); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setPadding(0, dp(15), 0, dp(3)); root.addView(title);
    TextView intro = text("These settings are saved on this device and used by the LecRizz keyboard.", 14); intro.setTextColor(muted); root.addView(intro);

    int currentHeight = LecRizzPrefs.get(this).getInt(LecRizzPrefs.KEY_HEIGHT, 46);
    LinearLayout heightCard = card();
    TextView heightLabel = text("Key height: " + currentHeight + " dp", 16); heightLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD); heightCard.addView(heightLabel);
    SeekBar seek = new SeekBar(this); seek.setMax(22); seek.setProgress(currentHeight - 38); seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
    heightCard.addView(seek, new LinearLayout.LayoutParams(-1, dp(44)));
    TextView range = text("Compact 38 dp  •  Comfortable 60 dp", 12); range.setTextColor(muted); heightCard.addView(range);
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        int h = progress + 38; heightLabel.setText("Key height: " + h + " dp");
        if (fromUser) LecRizzPrefs.get(KeyboardSettingsActivity.this).edit().putInt(LecRizzPrefs.KEY_HEIGHT, h).apply();
      }
      @Override public void onStartTrackingTouch(SeekBar bar) {}
      @Override public void onStopTrackingTouch(SeekBar bar) {}
    });

    addSwitch("Show key borders", "Add subtle outlines to individual keys.", LecRizzPrefs.KEY_BORDERS, true);
    addSwitch("Vibration on key press", "Use Android keyboard-tap haptics when supported.", LecRizzPrefs.VIBRATION, true);
    addSwitch("Keyboard sound", "Sound playback depends on device support and volume settings.", LecRizzPrefs.KEY_SOUND, false);
    addSwitch("Double-space inserts period", "Turn two spaces after a word into a period and a space.", LecRizzPrefs.DOUBLE_SPACE, true);
    Button done = button("Done"); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48)); p.topMargin = dp(14); root.addView(done, p); done.setOnClickListener(v -> finish());
  }
  private LinearLayout card() {
    LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16), dp(13), dp(16), dp(13)); c.setBackground(rounded(panel, 18));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(12); root.addView(c, p); return c;
  }
  private void addSwitch(String title, String detail, String key, boolean defaultValue) {
    LinearLayout c = card(); TextView h = text(title, 16); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); c.addView(h);
    TextView d = text(detail, 13); d.setTextColor(muted); c.addView(d);
    Switch s = new Switch(this); s.setText(title); s.setTextColor(white); s.setChecked(LecRizzPrefs.get(this).getBoolean(key, defaultValue));
    s.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
    s.setTrackTintList(android.content.res.ColorStateList.valueOf(muted)); c.addView(s);
    s.setOnCheckedChangeListener((button, checked) -> LecRizzPrefs.get(this).edit().putBoolean(key, checked).apply());
  }
}
