package com.lecrizz.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** Shared, device-local LecRizz preferences used by both the app and keyboard. */
public final class LecRizzPrefs {
  private LecRizzPrefs() {}
  public static final String FILE = "lecrizz_preferences";
  public static final String THEME = "theme";
  public static final String KEY_HEIGHT = "key_height_dp";
  public static final String KEY_BORDERS = "key_borders";
  public static final String VIBRATION = "vibration";
  public static final String KEY_SOUND = "key_sound";
  public static final String DOUBLE_SPACE = "double_space_period";
  public static final String RIZZ_TONE = "rizz_tone";
  public static final String SOURCE_LANGUAGE = "source_language";
  public static final String TARGET_LANGUAGE = "target_language";
  public static final String OCEAN = "ocean_breeze";
  public static final String SUNSET = "sunset_glow";

  public static SharedPreferences get(Context c) {
    return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
  }
  public static boolean isSunset(Context c) {
    return SUNSET.equals(get(c).getString(THEME, OCEAN));
  }
  public static int color(Context c, String role) {
    boolean sunset = isSunset(c);
    switch (role) {
      case "background": return sunset ? Color.rgb(27, 13, 22) : Color.rgb(7, 20, 29);
      case "panel": return sunset ? Color.rgb(48, 24, 34) : Color.rgb(15, 38, 49);
      case "panel2": return sunset ? Color.rgb(63, 31, 40) : Color.rgb(22, 51, 62);
      case "accent": return sunset ? Color.rgb(255, 151, 99) : Color.rgb(82, 211, 215);
      case "accent2": return sunset ? Color.rgb(255, 100, 115) : Color.rgb(67, 145, 207);
      case "text": return Color.rgb(250, 248, 250);
      case "muted": return sunset ? Color.rgb(220, 187, 185) : Color.rgb(174, 207, 215);
      case "onAccent": return sunset ? Color.rgb(46, 21, 27) : Color.rgb(5, 31, 38);
      default: return Color.MAGENTA;
    }
  }
}
