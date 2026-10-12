package com.lecrizz.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;

/** Native LecRizz home, settings and story screens. Settings are saved locally on device. */
public class MainActivity extends Activity {
  private LinearLayout root;
  private boolean sunsetTheme;
  private boolean storyFromSettings = false;
  private int white, muted, accent, accent2, background, panel, panel2, onAccent;
  private static final String[] RIZZ_TONES = {"Smooth", "Flirty", "Funny", "Sweet", "Confident", "Mysterious"};
  private static final String[] SOURCE_LANGUAGES = {"Auto-detect", "English", "Tagalog", "Taglish", "Cebuano / Bisaya", "Spanish", "Indonesian", "Japanese", "Korean", "French", "Arabic"};
  private static final String[] TARGET_LANGUAGES = {"English", "Tagalog", "Taglish", "Cebuano / Bisaya", "Spanish", "Indonesian", "Japanese", "Korean", "French", "Arabic"};

  private void refreshTheme() {
    sunsetTheme = LecRizzPrefs.isSunset(this);
    white = LecRizzPrefs.color(this, "text");
    muted = LecRizzPrefs.color(this, "muted");
    accent = LecRizzPrefs.color(this, "accent");
    accent2 = LecRizzPrefs.color(this, "accent2");
    background = LecRizzPrefs.color(this, "background");
    panel = LecRizzPrefs.color(this, "panel");
    panel2 = LecRizzPrefs.color(this, "panel2");
    onAccent = LecRizzPrefs.color(this, "onAccent");
  }

  private TextView text(String s, int sp) {
    TextView t = new TextView(this);
    t.setText(s); t.setTextColor(white); t.setTextSize(sp); t.setPadding(0, 5, 0, 5);
    return t;
  }
  private GradientDrawable rounded(int color, float radius) {
    GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); return g;
  }
  private GradientDrawable gradient(float radius) {
    return gradient(accent2, accent, radius);
  }
  private GradientDrawable gradient(int first, int second, float radius) {
    GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{first, second});
    g.setCornerRadius(radius); return g;
  }
  private TextView pill(String s) {
    TextView t = text(s, 12); t.setGravity(Gravity.CENTER); t.setTextColor(white);
    t.setPadding(14, 7, 14, 7); t.setBackground(rounded(panel2, 40)); return t;
  }
  private Button btn(String s) {
    Button b = new Button(this); b.setText(s); b.setTextColor(onAccent); b.setTextSize(14);
    b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setAllCaps(false); b.setPadding(14, 4, 14, 4);
    b.setBackground(gradient(18)); b.setMinHeight(dp(46)); return b;
  }
  private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    refreshTheme();
    showSplash();
    // Warm the local preference store during the branded startup screen. No fake progress
    // percentage or artificial delay: the splash leaves as soon as initialization finishes.
    Thread initializer = new Thread(() -> {
      LecRizzPrefs.get(getApplicationContext()).getAll();
      runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) showWelcome(); });
    }, "LecRizz-init");
    initializer.start();
  }

  private void showSplash() {
    refreshTheme();
    LinearLayout splash = new LinearLayout(this);
    splash.setOrientation(LinearLayout.VERTICAL);
    splash.setGravity(Gravity.CENTER);
    splash.setPadding(dp(28), dp(28), dp(28), dp(28));
    splash.setBackgroundColor(background);
    ImageView logo = new ImageView(this);
    logo.setImageResource(R.drawable.lecrizz_icon);
    logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    splash.addView(logo, new LinearLayout.LayoutParams(dp(104), dp(104)));
    TextView name = text("LecRizz", 32);
    name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    name.setGravity(Gravity.CENTER);
    name.setPadding(0, dp(10), 0, dp(2));
    splash.addView(name);
    TextView tagline = text("Your words. Your vibe. Your style.", 14);
    tagline.setTextColor(muted);
    tagline.setGravity(Gravity.CENTER);
    splash.addView(tagline);
    LecRizzSpinnerView spinner = new LecRizzSpinnerView(this);
    spinner.setThemeColors(sunsetTheme);
    splash.addView(spinner, new LinearLayout.LayoutParams(dp(46), dp(46)));
    TextView status = text("Getting your space ready…", 12);
    status.setTextColor(muted);
    status.setGravity(Gravity.CENTER);
    splash.addView(status);
    setContentView(splash);
    getWindow().setStatusBarColor(background);
    getWindow().setNavigationBarColor(background);
  }
  @Override protected void onResume() { super.onResume(); refreshTheme(); }

  private void base() {
    refreshTheme();
    root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(20), dp(18), dp(20), dp(26)); root.setBackgroundColor(background);
    root.setOnApplyWindowInsetsListener((v, insets) -> {
      v.setPadding(dp(20), dp(18) + insets.getSystemWindowInsetTop(), dp(20), dp(26) + insets.getSystemWindowInsetBottom());
      return insets;
    });
    ScrollView sc = new ScrollView(this); sc.setFillViewport(true); sc.setClipToPadding(false); sc.addView(root); setContentView(sc);
    getWindow().setStatusBarColor(background); getWindow().setNavigationBarColor(background);
  }
  private LinearLayout cardContainer() {
    LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16), dp(14), dp(16), dp(14));
    c.setBackground(rounded(panel, dp(18))); return c;
  }
  private void addSectionTitle(String s) {
    TextView t = text(s, 22); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setPadding(0, dp(15), 0, dp(7)); root.addView(t);
  }
  private void addCard(String title, String detail, String action, Runnable run) {
    LinearLayout card = cardContainer();
    TextView h = text(title, 17); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); card.addView(h);
    TextView d = text(detail, 13); d.setTextColor(muted); d.setLineSpacing(dp(2), 1f); card.addView(d);
    Button b = btn(action); LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(46)); bp.topMargin = dp(10); card.addView(b, bp);
    b.setOnClickListener(v -> run.run());
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.topMargin = dp(10); root.addView(card, cp);
  }
  private void addSettingRow(String title, String detail, View action) {
    LinearLayout row = cardContainer();
    TextView h = text(title, 16); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); row.addView(h);
    TextView d = text(detail, 13); d.setTextColor(muted); d.setLineSpacing(dp(2), 1f); row.addView(d);
    if (action != null) { LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2); ap.topMargin = dp(8); row.addView(action, ap); }
    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = dp(9); root.addView(row, rp);
  }
  private Button inlineButton(String label, Runnable run) {
    Button b = btn(label); b.setOnClickListener(v -> run.run()); return b;
  }
  private void addBack(String label, Runnable run) {
    TextView back = text("‹  " + label, 15); back.setTextColor(accent); back.setOnClickListener(v -> run.run()); root.addView(back);
  }

  private void showWelcome() {
    base();
    LinearLayout hero = cardContainer(); hero.setGravity(Gravity.CENTER_HORIZONTAL); hero.setPadding(dp(18), dp(20), dp(18), dp(22)); hero.setBackground(gradient(dp(24)));
    ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.lecrizz_icon); icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    hero.addView(icon, new LinearLayout.LayoutParams(dp(104), dp(104)));
    TextView title = text("LecRizz", 32); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setGravity(Gravity.CENTER); title.setTextColor(onAccent); hero.addView(title);
    TextView tagline = text("Your words. Your vibe. Your style.", 14); tagline.setGravity(Gravity.CENTER); tagline.setTextColor(onAccent); hero.addView(tagline);
    TextView version = pill("v0.4.3 • development preview"); LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2); vp.topMargin = dp(12); hero.addView(version, vp);
    root.addView(hero, new LinearLayout.LayoutParams(-1, -2));
    addSectionTitle("Welcome to your space");
    TextView intro = text("A keyboard designed around expression, convenience, and your own style. AI-powered features are not connected in this development build yet.", 14);
    intro.setTextColor(muted); intro.setLineSpacing(dp(3), 1f); root.addView(intro);
    Space gap = new Space(this); root.addView(gap, new LinearLayout.LayoutParams(1, dp(8)));
    addCard("⌨  Set up your keyboard", "Enable LecRizz in Android settings, then select it when you type.", "Set up keyboard", () -> showGuide(1));
    addCard("Explore LecRizz", "Open your dashboard, themes, keyboard preferences, and story.", "Open dashboard", this::showDashboard);
  }

  private void showGuide(int step) {
    base(); addBack("Back to LecRizz", this::showWelcome);
    Space gap = new Space(this); root.addView(gap, new LinearLayout.LayoutParams(1, dp(20)));
    String title, body;
    if (step == 1) { title = "1. Enable LecRizz"; body = "Android needs you to enable LecRizz as an available keyboard. This opens the system keyboard settings."; }
    else if (step == 2) { title = "2. Switch to LecRizz"; body = "When you're typing in a text field, choose LecRizz from the keyboard picker."; }
    else { title = "3. You're ready"; body = "You can return here any time to review setup or change your keyboard preferences."; }
    TextView h = text(title, 27); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); root.addView(h);
    TextView d = text(body, 15); d.setTextColor(muted); d.setLineSpacing(dp(3), 1f); root.addView(d);
    Button action = btn(step < 3 ? "Open keyboard settings" : "Open keyboard picker");
    action.setOnClickListener(v -> { if (step < 3) startActivity(new Intent("android.settings.INPUT_METHOD_SETTINGS")); else ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker(); });
    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(50)); ap.topMargin = dp(20); root.addView(action, ap);
    Button next = btn(step < 3 ? "Next  →" : "Finish setup"); next.setOnClickListener(v -> { if (step < 3) showGuide(step + 1); else showDashboard(); });
    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, dp(50)); np.topMargin = dp(10); root.addView(next, np);
    addSettingRow("Need a reminder?", "After enabling it, open any chat app and choose LecRizz using Android's keyboard picker.", null);
  }

  private void showDashboard() {
    base();
    LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.HORIZONTAL); hero.setGravity(Gravity.CENTER_VERTICAL);
    hero.setPadding(dp(14), dp(14), dp(14), dp(14)); hero.setBackground(gradient(dp(20)));
    ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.lecrizz_icon); icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE); hero.addView(icon, new LinearLayout.LayoutParams(dp(60), dp(60)));
    LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setPadding(dp(12), 0, 0, 0); hero.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    TextView name = text("LecRizz", 24); name.setTypeface(Typeface.DEFAULT, Typeface.BOLD); name.setTextColor(onAccent); brand.addView(name);
    TextView tagline = text("Your words. Your vibe. Your style.", 12); tagline.setTextColor(onAccent); brand.addView(tagline);
    root.addView(hero, new LinearLayout.LayoutParams(-1, -2));
    addSectionTitle("Your space");
    addCard("⌨  Keyboard setup", "Enable LecRizz and make it your active keyboard.", "Set up keyboard", () -> showGuide(1));
    addCard("🎨  Appearance & themes", "Choose Ocean Breeze or Sunset Glow. Your choice is saved and shared with the keyboard.", "Customize appearance", this::showSettings);
    addCard("⚙  Keyboard preferences", "Adjust key height, borders, vibration, and double-space punctuation.", "Open preferences", () -> startActivity(new Intent(this, KeyboardSettingsActivity.class)));
    addCard("📖  My Story", "The idea and vision behind LecRizz.", "Read my story", () -> { storyFromSettings = false; showStory(); });
    TextView note = text("Development preview • AI replies, online translation, and GIF search are not connected yet.", 12); note.setTextColor(muted); note.setPadding(dp(2), dp(18), dp(2), dp(4)); root.addView(note);
  }

  private void showSettings() {
    base(); addBack("Back to dashboard", this::showDashboard); addSectionTitle("Settings");
    TextView intro = text("Make LecRizz feel like yours. Theme changes apply to both the app and keyboard.", 14); intro.setTextColor(muted); root.addView(intro);
    Button ocean = btn(LecRizzPrefs.OCEAN.equals(LecRizzPrefs.get(this).getString(LecRizzPrefs.THEME, LecRizzPrefs.OCEAN)) ? "Ocean Breeze  ✓" : "Ocean Breeze");
    Button sunset = btn(LecRizzPrefs.isSunset(this) ? "Sunset Glow  ✓" : "Sunset Glow");
    ocean.setBackground(gradient(0xFF4391CF, 0xFF52D3D7, dp(18))); ocean.setTextColor(0xFF062B32);
    sunset.setBackground(gradient(0xFFFF6473, 0xFFFFB86B, dp(18))); sunset.setTextColor(0xFF31181D);
    LinearLayout themes = new LinearLayout(this); themes.setOrientation(LinearLayout.HORIZONTAL);
    themes.addView(ocean, new LinearLayout.LayoutParams(0, dp(50), 1)); LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(50), 1); slp.leftMargin = dp(8); themes.addView(sunset, slp);
    ocean.setOnClickListener(v -> saveTheme(LecRizzPrefs.OCEAN)); sunset.setOnClickListener(v -> saveTheme(LecRizzPrefs.SUNSET));
    addSettingRow("Appearance & themes", "Ocean Breeze is cool and calm. Sunset Glow is warm and expressive. Both keep key labels readable.", themes);

    Switch sound = new Switch(this); sound.setText("Keyboard sound"); sound.setTextColor(white); sound.setTextSize(14);
    sound.setChecked(LecRizzPrefs.get(this).getBoolean(LecRizzPrefs.KEY_SOUND, false));
    sound.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
    sound.setTrackTintList(android.content.res.ColorStateList.valueOf(panel2));
    sound.setOnCheckedChangeListener((button, checked) -> LecRizzPrefs.get(this).edit().putBoolean(LecRizzPrefs.KEY_SOUND, checked).apply());
    addSettingRow("Sound", "Save your preference for keyboard key sounds.", sound);
    addSettingRow("Keyboard preferences", "Key height, borders, vibration, and double-space period.", inlineButton("Configure keyboard", () -> startActivity(new Intent(this, KeyboardSettingsActivity.class))));
    String currentTone = LecRizzPrefs.get(this).getString(LecRizzPrefs.RIZZ_TONE, "Smooth");
    if (!java.util.Arrays.asList(RIZZ_TONES).contains(currentTone)) currentTone = "Smooth";
    addSettingRow("Rizz tone", "Choose the style used by local demo ideas. These are examples, not AI-generated replies.",
        inlineButton("Tone: " + currentTone, () -> choosePreference("Rizz tone", RIZZ_TONES, LecRizzPrefs.RIZZ_TONE, "Smooth", "Tone saved on this device.")));

    String sourceLanguage = LecRizzPrefs.get(this).getString(LecRizzPrefs.SOURCE_LANGUAGE, "Auto-detect");
    String targetLanguage = LecRizzPrefs.get(this).getString(LecRizzPrefs.TARGET_LANGUAGE, "Tagalog");
    LinearLayout languageActions = new LinearLayout(this);
    languageActions.setOrientation(LinearLayout.VERTICAL);
    Button source = btn("Source language: " + sourceLanguage);
    Button target = btn("Target language: " + targetLanguage);
    source.setOnClickListener(v -> choosePreference("Source language", SOURCE_LANGUAGES, LecRizzPrefs.SOURCE_LANGUAGE, "Auto-detect", "Source language saved. Translation is not connected yet."));
    target.setOnClickListener(v -> choosePreference("Target language", TARGET_LANGUAGES, LecRizzPrefs.TARGET_LANGUAGE, "Tagalog", "Target language saved. Translation is not connected yet."));
    languageActions.addView(source, new LinearLayout.LayoutParams(-1, dp(44)));
    LinearLayout.LayoutParams targetLp = new LinearLayout.LayoutParams(-1, dp(44)); targetLp.topMargin = dp(7); languageActions.addView(target, targetLp);
    addSettingRow("Language preferences", "Choose and save your preferred source and target languages for a future translation service. These choices do not translate text in this build.", languageActions);

    addSettingRow("Privacy & permissions", "Rizz and translation controls are disabled in password fields and fields marked as private. This build does not connect AI services or send typed text to a service.", null);
    addSettingRow("My Story", "Read the vision behind LecRizz.", inlineButton("Read my story", () -> { storyFromSettings = true; showStory(); }));
    addSettingRow("Help & Support", "Need to enable or switch keyboards? Review the setup guide.", inlineButton("Open setup guide", () -> showGuide(1)));
    addSettingRow("About LecRizz", "Version 0.4.3 development preview. Not a production release; build and test on a device before relying on it.", null);
    addSettingRow("Credits & acknowledgments", "Built iteratively with AI-assisted development. Features should only be described as complete after implementation and testing.", null);
  }

  private void choosePreference(String title, String[] options, String prefKey, String fallback, String confirmation) {
    String current = LecRizzPrefs.get(this).getString(prefKey, fallback);
    int checked = 0;
    for (int i = 0; i < options.length; i++) if (options[i].equals(current)) { checked = i; break; }
    final int[] selected = {checked};
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setSingleChoiceItems(options, checked, (dialog, which) -> selected[0] = which)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Save", (dialog, which) -> {
          LecRizzPrefs.get(this).edit().putString(prefKey, options[selected[0]]).apply();
          showSettings();
          android.widget.Toast.makeText(this, confirmation, android.widget.Toast.LENGTH_SHORT).show();
        })
        .show();
  }

  private void saveTheme(String theme) {
    LecRizzPrefs.get(this).edit().putString(LecRizzPrefs.THEME, theme).apply();
    showSettings();
  }

  private void showStory() {
    base(); addBack("Back", () -> { if (storyFromSettings) showSettings(); else showDashboard(); });
    TextView heading = text("My Story", 30); heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD); heading.setPadding(0, dp(16), 0, dp(2)); root.addView(heading);
    TextView subtitle = text("The story behind LecRizz", 15); subtitle.setTextColor(muted); root.addView(subtitle);
    addStoryParagraph("Where It All Began", "LecRizz started with an idea: what if typing messages could be more fun, expressive, and personal? What if a keyboard could do more than simply put letters on a screen?");
    addStoryParagraph("More Than Just a Keyboard", "The goal is to bring creativity, personality, and convenience together in one place. Whether someone is chatting with friends, expressing feelings, or finding the right words, LecRizz aims to make messaging feel more personal.");
    addStoryParagraph("The Vision Behind the Design", "From readable keys to Ocean Breeze and Sunset Glow, every detail should help the keyboard feel polished and comfortable. LecRizz should be useful in everyday chats—not just look good in screenshots.");
    addStoryParagraph("The Journey Isn’t Always Easy", "Building an app takes patience, experimentation, and problem-solving. Rather than rushing to release something unfinished, the goal is to build carefully, test thoroughly, and improve the experience before introducing LecRizz to the world.");
    addStoryParagraph("A Vision Beyond Borders", "People communicate in different languages and express themselves in different ways. LecRizz is being planned with a global audience in mind, with the hope of making its experience welcoming and useful wherever people are.");
    addStoryParagraph("What’s Next?", "There is still more to build, more to learn, and more possibilities to explore. The long-term goal is to keep improving LecRizz, listen to its users, and turn the original idea into something people genuinely enjoy using.");
    TextView thanks = text("To everyone who becomes part of this journey: thank you.", 15); thanks.setTypeface(Typeface.DEFAULT, Typeface.BOLD); thanks.setGravity(Gravity.CENTER); thanks.setPadding(0, dp(18), 0, dp(12)); root.addView(thanks);
    TextView signature = text("LecRizz — Your words. Your vibe. Your style.", 13); signature.setTextColor(muted); signature.setGravity(Gravity.CENTER); root.addView(signature);
  }
  private void addStoryParagraph(String title, String body) {
    TextView h = text(title, 18); h.setTypeface(Typeface.DEFAULT, Typeface.BOLD); h.setPadding(0, dp(17), 0, dp(4)); root.addView(h);
    TextView b = text(body, 14); b.setTextColor(muted); b.setLineSpacing(dp(3), 1.03f); root.addView(b);
  }
}
