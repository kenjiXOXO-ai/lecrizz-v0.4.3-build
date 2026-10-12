package com.lecrizz.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LecRizz keyboard: Gboard-style layout (number row, rounded keys, shift / caps lock,
 * ?123 symbols, key preview, hold-to-repeat backspace, smart enter key, auto-capitalise,
 * light + dark themes) with the Rizz / Translate action bar on top.
 *
 * Privacy rule: Rizz / Translate never read text in password or incognito fields.
 */
public class LecRizzImeService extends InputMethodService {

  private static final int LAYER_LETTERS = 0, LAYER_SYM1 = 1, LAYER_SYM2 = 2;
  private static final int SHIFT_OFF = 0, SHIFT_ON = 1, SHIFT_CAPS = 2;

  private static final String[] LETTER_ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
  private static final String DIGITS = "1234567890";
  private static final String SYM1_ROW2 = "@#\u20B1_&-+()/";   // ₱ for PH users
  private static final String SYM1_ROW3 = "*\"':;!?";
  private static final String SYM2_ROW2 = "~`|\u2022\u221A\u03C0\u00F7\u00D7\u00A3\u20AC";
  private static final String SYM2_ROW3 = "$\u00A5%[]{}";

  // state
  private int layer = LAYER_LETTERS;
  private int shift = SHIFT_OFF;
  private long lastShiftTap = 0, lastSpaceTap = 0;
  private boolean sensitiveField = false;   // password / incognito -> no AI features
  private boolean plainText = true;         // allow double-space -> ". "
  private EditorInfo editorInfo;

  // theme
  private float density = 3f;
  private int cBg, cKey, cFn, cPressed, cText, cMuted, cAccent, cOnAccent;

  // views
  private LinearLayout root, actionRow, suggestRow, keysBox;
  private HorizontalScrollView actionScroll;
  private TextView clipboardBtn, emojiBtn, gifsBtn, moreBtn;
  private ToneGenerator toneGenerator;
  private TextView rizzBtn, translateBtn, enterKey, shiftKey;
  private final TextView[] chips = new TextView[3];
  private final String[] suggestions = new String[3];
  private final List<TextView> letterKeys = new ArrayList<>();

  // key preview + repeat
  private PopupWindow preview;
  private PopupWindow activePopup;   // the one open Rizz / tool popup, so it can never be left behind
  private PopupWindow birdPopup;
  private boolean showingWordPredictions = false;
  private int predictionPrefixLength = 0;
  private TextView previewTv;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Runnable repeater;

  // ---------------------------------------------------------------- helpers

  private int dp(float v) { return (int) (v * density + 0.5f); }

  private boolean landscape() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  private int keyH() { int saved = LecRizzPrefs.get(this).getInt(LecRizzPrefs.KEY_HEIGHT, 46); return dp(landscape() ? Math.min(saved, 42) : saved); }

  private GradientDrawable rect(int color, float radius) {
    GradientDrawable g = new GradientDrawable();
    g.setColor(color);
    g.setCornerRadius(radius);
    return g;
  }

  private Drawable stateBg(int normal, int pressed, float radius) {
    StateListDrawable s = new StateListDrawable();
    s.addState(new int[]{android.R.attr.state_pressed}, rect(pressed, radius));
    s.addState(new int[]{}, rect(normal, radius));
    return s;
  }

  private Drawable stateBgBorder(int normal, int pressed, float radius) {
    StateListDrawable s = new StateListDrawable();
    GradientDrawable p = rect(pressed, radius); p.setStroke(dp(1), cAccent);
    GradientDrawable n = rect(normal, radius); n.setStroke(dp(1), shade(cMuted, 0.65f));
    s.addState(new int[]{android.R.attr.state_pressed}, p);
    s.addState(new int[]{}, n);
    return s;
  }

  private Drawable brandBg(float radius) {
    int secondary = LecRizzPrefs.color(this, "accent2");
    GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
        new int[]{secondary, cAccent});
    g.setCornerRadius(radius);
    StateListDrawable s = new StateListDrawable();
    s.addState(new int[]{android.R.attr.state_pressed}, rect(shade(cAccent, 0.86f), radius));
    s.addState(new int[]{}, g);
    return s;
  }

  private int shade(int c, float f) {
    return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
  }

  private LinearLayout.LayoutParams lp(float weight, int h, int l, int t, int r, int b) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, h, weight);
    p.setMargins(l, t, r, b);
    return p;
  }

  private LinearLayout.LayoutParams keyLp(float weight) {
    return lp(weight, keyH(), dp(2), dp(3), dp(2), dp(3));
  }

  private void pickPalette() {
    boolean sunset = LecRizzPrefs.isSunset(this);
    if (sunset) {
      cBg = 0xFF21131B; cKey = 0xFF3A2530; cFn = 0xFF51313A; cPressed = 0xFF75434A;
      cText = 0xFFFFF8F2; cMuted = 0xFFDDB8B6; cAccent = 0xFFFF9763; cOnAccent = 0xFF31181D;
    } else {
      cBg = 0xFF071C27; cKey = 0xFF103441; cFn = 0xFF164451; cPressed = 0xFF246574;
      cText = 0xFFF3FCFD; cMuted = 0xFF9CC8D2; cAccent = 0xFF52D3D7; cOnAccent = 0xFF062B32;
    }
  }

  // ---------------------------------------------------------------- lifecycle

  @Override public boolean onEvaluateFullscreenMode() { return false; }

  @Override public View onCreateInputView() {
    density = getResources().getDisplayMetrics().density;
    pickPalette();

    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(cBg);
    root.setPadding(dp(4), dp(6), dp(4), dp(6));
    // Android 15 draws IMEs edge-to-edge: keep keys above the navigation bar.
    root.setOnApplyWindowInsetsListener((v, ins) -> {
      v.setPadding(dp(4), dp(6), dp(4), dp(6) + ins.getSystemWindowInsetBottom());
      return ins;
    });

    buildActionRows();

    keysBox = new LinearLayout(this);
    keysBox.setOrientation(LinearLayout.VERTICAL);
    root.addView(keysBox, new LinearLayout.LayoutParams(-1, -2));

    buildKeys();
    return root;
  }

  @Override public void onStartInputView(EditorInfo info, boolean restarting) {
    super.onStartInputView(info, restarting);
    pickPalette();
    if (root != null) {
      root.setBackgroundColor(cBg);
      if (rizzBtn != null) { rizzBtn.setBackground(brandBg(dp(21))); rizzBtn.setTextColor(cOnAccent); }
      TextView[] tools = {translateBtn, clipboardBtn, emojiBtn, gifsBtn, moreBtn};
      for (TextView tool : tools) if (tool != null) { tool.setBackground(stateBg(cFn, cPressed, dp(21))); tool.setTextColor(cText); }
      for (TextView chip : chips) if (chip != null) { chip.setBackground(stateBg(cKey, cPressed, dp(14))); chip.setTextColor(cText); }
    }
    applyFieldState(info);
  }

  @Override public void onFinishInputView(boolean finishingInput) {
    super.onFinishInputView(finishingInput);
    hidePreview();
    dismissActivePopup();
    dismissBirdPopup();
    stopRepeat();
  }

  @Override public void onWindowHidden() {
    super.onWindowHidden();
    hidePreview();
    dismissActivePopup();
    dismissBirdPopup();
    stopRepeat();
  }

  private void applyFieldState(EditorInfo ei) {
    editorInfo = ei;
    int cls = ei.inputType & InputType.TYPE_MASK_CLASS;
    int var = ei.inputType & InputType.TYPE_MASK_VARIATION;

    boolean textPw = cls == InputType.TYPE_CLASS_TEXT
        && (var == InputType.TYPE_TEXT_VARIATION_PASSWORD
        || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
    boolean numPw = cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    boolean incognito = (ei.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
    sensitiveField = textPw || numPw || incognito;

    plainText = cls == InputType.TYPE_CLASS_TEXT && !textPw
        && var != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        && var != InputType.TYPE_TEXT_VARIATION_URI
        && var != InputType.TYPE_TEXT_VARIATION_FILTER;

    boolean numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
        || cls == InputType.TYPE_CLASS_DATETIME;
    layer = numeric ? LAYER_SYM1 : LAYER_LETTERS;
    shift = SHIFT_OFF;

    if (keysBox == null) return;
    hideSuggestions();
    float a = sensitiveField ? 0.4f : 1f;
    rizzBtn.setAlpha(a);
    translateBtn.setAlpha(a);
    buildKeys();
    updateAutoShift();
  }

  // ---------------------------------------------------------------- action bar

  private TextView pill(String label, boolean primary) {
    TextView t = new TextView(this);
    t.setText(label);
    t.setGravity(Gravity.CENTER);
    t.setTextSize(15);
    t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    t.setSingleLine(true);
    float r = dp(21);
    if (primary) {
      t.setTextColor(cOnAccent);
      t.setBackground(brandBg(r));
    } else {
      t.setTextColor(cText);
      t.setBackground(stateBg(cFn, cPressed, r));
    }
    return t;
  }

  private void buildActionRows() {
    actionScroll = new HorizontalScrollView(this);
    actionScroll.setHorizontalScrollBarEnabled(false);
    actionScroll.setFillViewport(false);
    actionScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    actionRow = new LinearLayout(this);
    actionRow.setOrientation(LinearLayout.HORIZONTAL);
    actionRow.setGravity(Gravity.CENTER_VERTICAL);
    rizzBtn = pill("Rizz", true);
    translateBtn = pill("Translate", false);
    clipboardBtn = pill("Clipboard", false);
    emojiBtn = pill("Emoji", false);
    gifsBtn = pill("GIFs", false);
    moreBtn = pill("More  ⋯", false);
    rizzBtn.setOnClickListener(v -> onRizz());
    rizzBtn.setOnLongClickListener(v -> { onChooseRizzTone(); return true; });
    translateBtn.setOnClickListener(v -> onTranslate());
    clipboardBtn.setOnClickListener(v -> onClipboard(clipboardBtn));
    emojiBtn.setOnClickListener(v -> onEmoji(emojiBtn));
    gifsBtn.setOnClickListener(v -> toast("GIF search is not connected in this development build."));
    moreBtn.setOnClickListener(v -> onMore(moreBtn));
    addTool(rizzBtn, dp(76)); addTool(translateBtn, dp(102)); addTool(clipboardBtn, dp(104));
    addTool(emojiBtn, dp(80)); addTool(gifsBtn, dp(70)); addTool(moreBtn, dp(78));
    actionScroll.addView(actionRow, new HorizontalScrollView.LayoutParams(-2, -1));
    root.addView(actionScroll, new LinearLayout.LayoutParams(-1, dp(48)));

    suggestRow = new LinearLayout(this);
    suggestRow.setOrientation(LinearLayout.HORIZONTAL);
    suggestRow.setGravity(Gravity.CENTER_VERTICAL);
    for (int i = 0; i < 3; i++) {
      final int idx = i;
      TextView c = new TextView(this);
      c.setTextColor(cText);
      c.setTextSize(12.5f);
      c.setMaxLines(2);
      c.setEllipsize(TextUtils.TruncateAt.END);
      c.setGravity(Gravity.CENTER);
      c.setPadding(dp(8), dp(4), dp(8), dp(4));
      c.setBackground(stateBg(cKey, cPressed, dp(14)));
      c.setOnClickListener(v -> applySuggestion(idx));
      chips[i] = c;
      suggestRow.addView(c, lp(1f, dp(44), dp(3), dp(2), dp(3), dp(2)));
    }
    TextView close = new TextView(this);
    close.setText("\u2715");
    close.setGravity(Gravity.CENTER);
    close.setTextColor(cMuted);
    close.setTextSize(16);
    close.setOnClickListener(v -> hideSuggestions());
    suggestRow.addView(close, new LinearLayout.LayoutParams(dp(40), dp(44)));
    suggestRow.setVisibility(View.GONE);
    root.addView(suggestRow, new LinearLayout.LayoutParams(-1, dp(48)));
  }

  private void addTool(TextView button, int width) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, dp(40));
    p.setMargins(dp(3), dp(2), dp(3), dp(2));
    actionRow.addView(button, p);
  }

  private void showSuggestions(String[] s) {
    showingWordPredictions = false;
    predictionPrefixLength = 0;
    for (int i = 0; i < 3; i++) {
      suggestions[i] = s[i];
      chips[i].setEnabled(true); chips[i].setAlpha(1f);
      chips[i].setText(s[i]);
    }
    actionScroll.setVisibility(View.GONE);
    suggestRow.setVisibility(View.VISIBLE);
  }

  private void hideSuggestions() {
    showingWordPredictions = false;
    predictionPrefixLength = 0;
    if (suggestRow == null) return;
    suggestRow.setVisibility(View.GONE);
    actionScroll.setVisibility(View.VISIBLE);
  }

  private void applySuggestion(int i) {
    if (suggestions[i] == null) return;
    if (showingWordPredictions) {
      applyWordPrediction(i);
      return;
    }
    final String chosen = suggestions[i];
    final String[] labels = {"Subtle", "Playful", "Bold"};
    LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(14), dp(12), dp(14), dp(12)); panel.setBackground(rect(cBg, dp(16)));
    TextView heading = new TextView(this); heading.setText(labels[i] + " · " + savedTone() + " · demo idea"); heading.setTextColor(cAccent); heading.setTextSize(13);
    heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD); panel.addView(heading);
    TextView previewText = new TextView(this); previewText.setText(chosen); previewText.setTextColor(cText); previewText.setTextSize(16);
    previewText.setPadding(0, dp(8), 0, dp(12)); panel.addView(previewText);
    LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); panel.addView(actions);
    final PopupWindow popup = new PopupWindow(panel, dp(280), -2, true);
    TextView copy = new TextView(this); copy.setText("Copy"); copy.setTextColor(cText); copy.setGravity(Gravity.CENTER);
    copy.setPadding(dp(10), dp(10), dp(10), dp(10)); copy.setBackground(stateBg(cFn, cPressed, dp(10)));
    TextView insert = new TextView(this); insert.setText("Insert at cursor"); insert.setTextColor(cOnAccent); insert.setGravity(Gravity.CENTER);
    insert.setPadding(dp(10), dp(10), dp(10), dp(10)); insert.setBackground(stateBg(cAccent, shade(cAccent, 0.86f), dp(10)));
    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(42), 1f); ap.rightMargin = dp(6); actions.addView(copy, ap);
    actions.addView(insert, new LinearLayout.LayoutParams(0, dp(42), 1f));
    copy.setOnClickListener(v -> {
      ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
      if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("LecRizz demo suggestion", chosen));
      popup.dismiss(); toast("Copied. Your draft was not changed.");
    });
    insert.setOnClickListener(v -> {
      InputConnection ic = getCurrentInputConnection();
      // Insert at the current cursor only; never delete or replace the existing draft.
      insertAtCursor(ic, chosen);
      popup.dismiss(); hideSuggestions();
    });
    popup.setBackgroundDrawable(rect(cBg, dp(16))); popup.setOutsideTouchable(true); popup.setElevation(dp(8));
    dismissActivePopup(); activePopup = popup;
    popup.showAtLocation(root, Gravity.CENTER, 0, 0);
  }

  // Small offline dictionary suggestions. No typed text is stored or sent to a service.
  private static final String[] LOCAL_WORDS = ("about above after again all also always am an and any are around as ask at away back be because been before best better between big bit both bring but by call came can cannot care change chat come could day did different do does doing done down each easy even ever every example feel few find first for friend from fun get give go good great had has have he hello help her here hey hi him his how i if in into is it its just keep kind know later learn let like little live look love make many may me message might more most much my need never new next nice no not now of off okay old on one only open or other our out over people place play please point pretty probably put really right said same say see she should since smile so some something soon still story style sure take talk tell than thank thanks that the their them then there these they thing think this those through time to today together too try turn two up us use very vibe want was way we well were what when where which who why will with without word work world would wow yes you your you're" ).split(" ");

  /** Hides the strip only if it is showing word predictions, so a Rizz suggestion strip is never dismissed by cursor movement. */
  private void hideWordPredictions() {
    if (showingWordPredictions) hideSuggestions();
  }

  private void updateWordPredictions() {
    if (root == null || actionScroll == null || suggestRow == null || sensitiveField || !plainText || layer != LAYER_LETTERS) return;
    if (editorInfo != null && (editorInfo.inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) { hideWordPredictions(); return; }
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) { hideWordPredictions(); return; }
    CharSequence selected = ic.getSelectedText(0);
    if (selected != null && selected.length() > 0) { hideWordPredictions(); return; }
    // Cursor in the middle of a word: a suggestion would duplicate the rest of the word, so show none.
    CharSequence after = ic.getTextAfterCursor(1, 0);
    if (after != null && after.length() > 0 && (Character.isLetter(after.charAt(0)) || after.charAt(0) == '\'')) { hideWordPredictions(); return; }
    CharSequence before = ic.getTextBeforeCursor(64, 0);
    if (before == null || before.length() == 0) { hideWordPredictions(); return; }
    int end = before.length();
    int start = end;
    while (start > 0) {
      char ch = before.charAt(start - 1);
      if (Character.isLetter(ch) || ch == '\'') start--; else break;
    }
    String prefix = before.subSequence(start, end).toString();
    if (prefix.length() < 1 || prefix.length() > 24) { hideWordPredictions(); return; }
    boolean initialUpper = Character.isUpperCase(prefix.charAt(0));
    String lower = prefix.toLowerCase(Locale.ROOT);
    ArrayList<String> found = new ArrayList<>();
    for (String word : LOCAL_WORDS) {
      if (word.startsWith(lower) && !word.equals(lower)) {
        String candidate = initialUpper ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word;
        if (!found.contains(candidate)) found.add(candidate);
        if (found.size() == 3) break;
      }
    }
    if (found.isEmpty()) { hideWordPredictions(); return; }
    predictionPrefixLength = prefix.length();
    showingWordPredictions = true;
    for (int i = 0; i < 3; i++) {
      suggestions[i] = i < found.size() ? found.get(i) : null;
      chips[i].setText(i < found.size() ? found.get(i) : "");
      chips[i].setEnabled(i < found.size());
      chips[i].setAlpha(i < found.size() ? 1f : 0.35f);
    }
    actionScroll.setVisibility(View.GONE);
    suggestRow.setVisibility(View.VISIBLE);
  }

  private void applyWordPrediction(int index) {
    if (index < 0 || index >= suggestions.length || suggestions[index] == null) return;
    InputConnection ic = getCurrentInputConnection();
    if (ic == null || predictionPrefixLength <= 0) { hideSuggestions(); return; }
    CharSequence selected = ic.getSelectedText(0);
    if (selected != null && selected.length() > 0) { hideSuggestions(); return; }
    String chosen = suggestions[index];
    ic.beginBatchEdit();
    ic.deleteSurroundingText(predictionPrefixLength, 0);
    ic.commitText(chosen, 1);
    ic.endBatchEdit();
    lastSpaceTap = 0;
    hideSuggestions();
    updateAutoShift();
  }

  private void dismissActivePopup() {
    try { if (activePopup != null && activePopup.isShowing()) activePopup.dismiss(); } catch (Exception ignored) { }
    activePopup = null;
  }

  /**
   * Inserts text at the cursor without ever deleting the draft. If the user has a
   * selection, the cursor is first collapsed to the end of it so commitText cannot replace it.
   */
  private void insertAtCursor(InputConnection ic, CharSequence text) {
    if (ic == null || text == null || text.length() == 0) return;
    ic.beginBatchEdit();
    if (!sensitiveField) {
      CharSequence selected = ic.getSelectedText(0);
      if (selected != null && selected.length() > 0) {
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (et != null) {
          int end = Math.max(et.selectionStart, et.selectionEnd);
          if (end >= 0) ic.setSelection(end, end);
        }
      }
    }
    ic.commitText(text, 1);
    ic.endBatchEdit();
  }

  private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

  /** Reads at most one character each side of the cursor; typed text is never stored or sent anywhere. */
  private boolean isFieldEmpty(InputConnection ic) {
    CharSequence b = ic.getTextBeforeCursor(1, 0);
    CharSequence a = ic.getTextAfterCursor(1, 0);
    return (b == null || b.length() == 0) && (a == null || a.length() == 0);
  }

  private static final String[] RIZZ_TONES = {"Smooth", "Flirty", "Funny", "Sweet", "Confident", "Mysterious"};

  /** Returns the saved tone, falling back to Smooth if the stored value is missing or unknown. */
  private String savedTone() {
    String t = LecRizzPrefs.get(this).getString(LecRizzPrefs.RIZZ_TONE, "Smooth");
    for (String known : RIZZ_TONES) if (known.equals(t)) return t;
    return "Smooth";
  }

  private void onRizz() {
    if (sensitiveField) { toast("Rizz is off in password and private fields"); return; }
    showRizzSuggestions(savedTone());
  }

  private void onChooseRizzTone() {
    if (sensitiveField) { toast("Rizz is off in password and private fields"); return; }
    final String[] tones = RIZZ_TONES;
    final String current = savedTone();
    String[] labels = new String[tones.length];
    Runnable[] actions = new Runnable[tones.length];
    for (int i = 0; i < tones.length; i++) {
      final String tone = tones[i];
      labels[i] = tone.equals(current) ? "\u2713  " + tone : tone;
      actions[i] = () -> {
        LecRizzPrefs.get(this).edit().putString(LecRizzPrefs.RIZZ_TONE, tone).apply();
        showRizzSuggestions(tone);
      };
    }
    showToolPopup(rizzBtn, "Rizz tone (saved on this device)", labels, actions);
  }

  private void showRizzSuggestions(String tone) {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null || sensitiveField) return;
    boolean empty = isFieldEmpty(ic);
    String[] demo;
    switch (tone) {
      case "Flirty": demo = empty
          ? new String[]{"So, when do I get to see that smile? 😏", "Not gonna lie, you caught my attention ✨", "Should I admit you crossed my mind? 👀"}
          : new String[]{"Careful, you're making me smile 😏", "And that's why you're interesting ✨", "Now you've got my attention 👀"}; break;
      case "Funny": demo = empty
          ? new String[]{"Important question: fries or pizza? 😂", "I came here with zero plans and one joke 😭", "Rate my conversation skills from 1 to legendary 💀"}
          : new String[]{"Your honor, I plead hilarious 😂", "That plot twist needs a soundtrack 😭", "I'm choosing to be dramatic about this 💀"}; break;
      case "Sweet": demo = empty
          ? new String[]{"Hope something makes you smile today 💛", "Just wanted to send you a little good energy ✨", "You deserve a really good day 🫶"}
          : new String[]{"I'm glad you told me that 💛", "That honestly made me smile 🫶", "I hope things get easier for you ✨"}; break;
      case "Confident": demo = empty
          ? new String[]{"Let's skip the awkward part and say hi 😎", "I had a feeling we'd get along.", "Good energy recognizes good energy."}
          : new String[]{"I know what I bring to the conversation 😎", "Fair point. Here's my take:", "I'm listening—keep going."}; break;
      case "Mysterious": demo = empty
          ? new String[]{"I could tell you, but where's the fun in that? 👀", "Some stories start with one message.", "Maybe you'll figure me out."}
          : new String[]{"There's a little more to that story.", "Interesting. I had a feeling you'd say that 👀", "Not every clue needs an explanation."}; break;
      default: demo = empty
          ? new String[]{"Hey, what are you up to? 😏", "Random, but you crossed my mind ✨", "Tell me the best part of your day 👀"}
          : new String[]{"Tell me more 👀", "Wait, really? 😭", "I like where this is going ✨"}; break;
    }
    showSuggestions(demo);
    toast(tone + " tone · demo ideas only, not AI-generated");
  }

  private void onTranslate() {
    if (sensitiveField) { toast("Translate is off in password and private fields"); return; }
    // Do not fabricate a translation. The toolbar action remains honest until a service is configured.
    toast("Translation service isn't connected yet. Your text was not changed.");
  }

  private void onClipboard(View anchor) {
    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) {
      toast("Clipboard is empty"); return;
    }
    CharSequence clip = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
    if (clip == null || clip.length() == 0) { toast("No text in the current clipboard item"); return; }
    String shown = sensitiveField ? "Insert clipboard text (hidden in private fields)"
        : (clip.length() > 120 ? clip.subSequence(0, 120) + "…" : clip.toString());
    showToolPopup(anchor, "Clipboard", new String[]{shown}, new Runnable[]{() -> insertAtCursor(getCurrentInputConnection(), clip)});
  }

  private void onEmoji(View anchor) {
    String[] emojis = {"😀", "😂", "🥹", "😍", "😏", "😭", "👀", "✨", "❤️", "🔥", "🙏", "🤝", "💀", "🥰", "😎", "🫶"};
    Runnable[] actions = new Runnable[emojis.length];
    for (int i = 0; i < emojis.length; i++) { final String emoji = emojis[i]; actions[i] = () -> {
      insertAtCursor(getCurrentInputConnection(), emoji);
    }; }
    showEmojiPopup(anchor, emojis, actions);
  }

  private void onMore(View anchor) {
    String[] items = {"Keyboard preferences", "Open LecRizz app", "Switch keyboard"};
    Runnable[] actions = {
      () -> { Intent i = new Intent(this, KeyboardSettingsActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); },
      () -> { Intent i = new Intent(this, MainActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); },
      this::switchKeyboard
    };
    showToolPopup(anchor, "More LecRizz tools", items, actions);
  }

  private PopupWindow newPanelPopup(LinearLayout panel, int widthDp) {
    final PopupWindow popup = new PopupWindow(panel, dp(widthDp), -2, true);
    popup.setBackgroundDrawable(rect(cBg, dp(14)));
    popup.setOutsideTouchable(true);
    popup.setElevation(dp(8));
    return popup;
  }

  /** Shows the popup just above the toolbar so it never covers the keys or runs off the screen. */
  private void showAboveToolbar(PopupWindow popup, LinearLayout panel, int widthDp) {
    dismissActivePopup();
    activePopup = popup;
    try {
      panel.measure(View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      int[] loc = new int[2];
      root.getLocationOnScreen(loc);
      int y = Math.max(0, loc[1] - panel.getMeasuredHeight() - dp(4));
      popup.showAtLocation(root, Gravity.NO_GRAVITY, loc[0] + dp(8), y);
    } catch (RuntimeException e) {
      activePopup = null;   // a popup failure must never break typing
    }
  }

  private void showToolPopup(View anchor, String title, String[] items, Runnable[] actions) {
    LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(8), dp(8), dp(8), dp(8)); panel.setBackground(rect(cBg, dp(14)));
    TextView heading = new TextView(this); heading.setText(title); heading.setTextColor(cMuted); heading.setTextSize(12);
    heading.setPadding(dp(8), dp(6), dp(8), dp(6)); panel.addView(heading);
    final PopupWindow popup = newPanelPopup(panel, 250);
    for (int i = 0; i < items.length; i++) {
      final int index = i;
      TextView item = new TextView(this); item.setText(items[i]); item.setTextColor(cText); item.setTextSize(15);
      item.setMaxLines(3); item.setEllipsize(TextUtils.TruncateAt.END);
      item.setGravity(Gravity.CENTER_VERTICAL); item.setPadding(dp(12), dp(10), dp(12), dp(10));
      item.setBackground(stateBg(cFn, cPressed, dp(10)));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(3); panel.addView(item, p);
      item.setOnClickListener(v -> { popup.dismiss(); actions[index].run(); });
    }
    showAboveToolbar(popup, panel, 250);
  }

  /** Emoji grid: 8 per row, so the picker stays a few rows tall instead of a screen-high list. */
  private void showEmojiPopup(View anchor, String[] emojis, Runnable[] actions) {
    final int perRow = 8, width = 8 * 38 + 16;
    LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(8), dp(8), dp(8), dp(8)); panel.setBackground(rect(cBg, dp(14)));
    TextView heading = new TextView(this); heading.setText("Emoji"); heading.setTextColor(cMuted); heading.setTextSize(12);
    heading.setPadding(dp(4), dp(2), dp(4), dp(6)); panel.addView(heading);
    final PopupWindow popup = newPanelPopup(panel, width);
    LinearLayout row = null;
    for (int i = 0; i < emojis.length; i++) {
      if (i % perRow == 0) { row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); panel.addView(row); }
      final int index = i;
      TextView e = new TextView(this); e.setText(emojis[i]); e.setTextSize(22); e.setGravity(Gravity.CENTER);
      e.setBackground(stateBg(cBg, cPressed, dp(8)));
      row.addView(e, new LinearLayout.LayoutParams(dp(38), dp(42)));
      e.setOnClickListener(v -> { popup.dismiss(); actions[index].run(); });
    }
    showAboveToolbar(popup, panel, width);
  }

  // ---------------------------------------------------------------- keys

  private TextView baseKey(String label, int bg, int fg, float sp, float radius) {
    TextView k = new TextView(this);
    k.setText(label);
    k.setTextColor(fg);
    k.setTextSize(sp);
    k.setGravity(Gravity.CENTER);
    k.setIncludeFontPadding(false);
    k.setSingleLine(true);
    int pressed = (bg == cKey || bg == cFn) ? cPressed : shade(bg, 0.85f);
    boolean borders = LecRizzPrefs.get(this).getBoolean(LecRizzPrefs.KEY_BORDERS, true);
    k.setBackground(borders ? stateBgBorder(bg, pressed, radius) : stateBg(bg, pressed, radius));
    return k;
  }

  private void spacer(LinearLayout row, float w) {
    row.addView(new View(this), new LinearLayout.LayoutParams(0, keyH(), w));
  }

  private LinearLayout newRow() {
    LinearLayout r = new LinearLayout(this);
    r.setOrientation(LinearLayout.HORIZONTAL);
    keysBox.addView(r, new LinearLayout.LayoutParams(-1, -2));
    return r;
  }

  private String label(String ch) {
    return shift == SHIFT_OFF ? ch.toLowerCase(Locale.ROOT) : ch.toUpperCase(Locale.ROOT);
  }

  private TextView charKey(LinearLayout row, String ch, boolean letter, float sp) {
    TextView k = baseKey(letter ? label(ch) : ch, cKey, cText, sp, dp(10));
    row.addView(k, keyLp(1f));
    if (letter) { k.setTag(ch); letterKeys.add(k); }
    attach(k, true, () -> type(letter ? label(ch) : ch), false);
    return k;
  }

  private TextView fnKey(LinearLayout row, String text, float weight, float sp, Runnable action, boolean repeat) {
    TextView k = baseKey(text, cFn, cText, sp, dp(10));
    row.addView(k, keyLp(weight));
    attach(k, false, action, repeat);
    return k;
  }

  @SuppressLint("ClickableViewAccessibility")
  private void attach(TextView k, boolean showPreview, Runnable action, boolean repeat) {
    k.setOnTouchListener((v, e) -> {
      switch (e.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          v.setPressed(true);
          if (LecRizzPrefs.get(this).getBoolean(LecRizzPrefs.VIBRATION, true))
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
          if (LecRizzPrefs.get(this).getBoolean(LecRizzPrefs.KEY_SOUND, false)) playKeySound();
          if (showPreview) showPreview(v, ((TextView) v).getText().toString());
          action.run();
          if (repeat) startRepeat(action);
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          v.setPressed(false);
          hidePreview();
          stopRepeat();
          return true;
        default:
          return true;
      }
    });
  }

  private void buildKeys() {
    if (keysBox == null) return;
    if (layer != LAYER_LETTERS) hideSuggestions();
    hidePreview();
    stopRepeat();
    keysBox.removeAllViews();
    letterKeys.clear();
    shiftKey = null;

    // number row (always visible, like Gboard)
    LinearLayout r1 = newRow();
    for (char c : DIGITS.toCharArray()) charKey(r1, String.valueOf(c), false, 20);

    if (layer == LAYER_LETTERS) {
      LinearLayout r2 = newRow();
      for (char c : LETTER_ROWS[0].toCharArray()) charKey(r2, String.valueOf(c), true, 22);
      LinearLayout r3 = newRow();
      spacer(r3, 0.5f);
      for (char c : LETTER_ROWS[1].toCharArray()) charKey(r3, String.valueOf(c), true, 22);
      spacer(r3, 0.5f);
      LinearLayout r4 = newRow();
      shiftKey = fnKey(r4, "\u21E7", 1.5f, 20, this::onShift, false);
      for (char c : LETTER_ROWS[2].toCharArray()) charKey(r4, String.valueOf(c), true, 22);
      fnKey(r4, "\u232B", 1.5f, 20, this::onBackspace, true);
    } else {
      boolean s2 = layer == LAYER_SYM2;
      LinearLayout r2 = newRow();
      for (char c : (s2 ? SYM2_ROW2 : SYM1_ROW2).toCharArray()) charKey(r2, String.valueOf(c), false, 20);
      LinearLayout r3 = newRow();
      fnKey(r3, s2 ? "?123" : "=\\<", 1.5f, 15, () -> { layer = s2 ? LAYER_SYM1 : LAYER_SYM2; buildKeys(); }, false);
      for (char c : (s2 ? SYM2_ROW3 : SYM1_ROW3).toCharArray()) charKey(r3, String.valueOf(c), false, 20);
      fnKey(r3, "\u232B", 1.5f, 20, this::onBackspace, true);
    }

    LinearLayout r5 = newRow();
    fnKey(r5, layer == LAYER_LETTERS ? "?123" : "ABC", 1.5f, 15,
        () -> { layer = layer == LAYER_LETTERS ? LAYER_SYM1 : LAYER_LETTERS; buildKeys(); }, false);
    charKey(r5, ",", false, 20);
    fnKey(r5, "\uD83C\uDF10", 1f, 18, this::switchKeyboard, false);

    TextView space = baseKey("English (US)  •  LecRizz", cKey, cMuted, 10, dp(10));
    r5.addView(space, keyLp(4f));
    attach(space, false, this::onSpace, false);

    charKey(r5, ".", false, 20);
    enterKey = baseKey("\u21B5", cAccent, cOnAccent, 18, dp(23));
    enterKey.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    r5.addView(enterKey, keyLp(1.5f));
    attach(enterKey, false, this::onEnter, false);

    refreshShiftKey();
    refreshEnterKey();
  }

  // ---------------------------------------------------------------- key actions

  private void type(String s) {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    ic.commitText(s, 1);
    lastSpaceTap = 0;
    if (shift == SHIFT_ON) setShift(SHIFT_OFF);
    updateAutoShift();
    updateWordPredictions();
  }

  private void playKeySound() {
    try {
      if (toneGenerator == null) toneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, 35);
      toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 25);
    } catch (RuntimeException ignored) { }
  }

  private void onSpace() {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    long now = SystemClock.uptimeMillis();
    if (plainText && LecRizzPrefs.get(this).getBoolean(LecRizzPrefs.DOUBLE_SPACE, true) && now - lastSpaceTap < 400) {
      CharSequence b = ic.getTextBeforeCursor(2, 0);
      if (b != null && b.length() == 2 && b.charAt(1) == ' ' && Character.isLetterOrDigit(b.charAt(0))) {
        ic.beginBatchEdit();
        ic.deleteSurroundingText(1, 0);
        ic.commitText(". ", 1);
        ic.endBatchEdit();
        lastSpaceTap = 0;
        updateAutoShift();
        updateWordPredictions();
        return;
      }
    }
    ic.commitText(" ", 1);
    lastSpaceTap = now;
    updateAutoShift();
    updateWordPredictions();
  }

  private void onBackspace() {
    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);   // handles selections and emoji correctly
    lastSpaceTap = 0;
    updateAutoShift();
    updateWordPredictions();
  }

  private void onEnter() {
    showEnterBirdAnimation();
    hideSuggestions();
    InputConnection ic = getCurrentInputConnection();
    if (ic == null || editorInfo == null) { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER); return; }
    int action = editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
    if (isNewline()) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    else ic.performEditorAction(action);
    updateAutoShift();
  }

  private void showEnterBirdAnimation() {
    if (enterKey == null || root == null) return;
    if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;   // respect reduced-motion / animations off
    dismissBirdPopup();
    try {
      TextView bird = new TextView(this);
      bird.setText("🐦");
      bird.setTextSize(22);
      bird.setGravity(Gravity.CENTER);
      bird.setBackground(rect(cBg, dp(16)));
      bird.setElevation(dp(5));
      birdPopup = new PopupWindow(bird, dp(44), dp(38), false);
      birdPopup.setBackgroundDrawable(rect(cBg, dp(16)));
      birdPopup.setTouchable(false);
      int[] loc = new int[2];
      enterKey.getLocationOnScreen(loc);
      int x = loc[0] + enterKey.getWidth() / 2 - dp(22);
      int y = Math.max(dp(8), loc[1] - dp(42));
      birdPopup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
      bird.setAlpha(1f);
      bird.setTranslationY(dp(4));
      bird.animate().translationY(-dp(18)).alpha(0f).setDuration(430L).withEndAction(this::dismissBirdPopup).start();
    } catch (Exception ignored) { dismissBirdPopup(); }
  }

  private void dismissBirdPopup() {
    try { if (birdPopup != null && birdPopup.isShowing()) birdPopup.dismiss(); } catch (Exception ignored) { }
    birdPopup = null;
  }

  private boolean isNewline() {
    if (editorInfo == null) return true;
    int action = editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
    boolean multi = (editorInfo.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
    boolean noAction = (editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
    return multi || noAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED;
  }

  private void refreshEnterKey() {
    if (enterKey == null) return;
    String l = "\u21B5";
    if (!isNewline()) {
      switch (editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION) {
        case EditorInfo.IME_ACTION_GO: l = "Go"; break;
        case EditorInfo.IME_ACTION_SEARCH: l = "\uD83D\uDD0D"; break;
        case EditorInfo.IME_ACTION_SEND: l = "\u27A4"; break;
        case EditorInfo.IME_ACTION_NEXT: l = "\u2192"; break;
        case EditorInfo.IME_ACTION_PREVIOUS: l = "\u2190"; break;
        case EditorInfo.IME_ACTION_DONE: l = "\u2713"; break;
        default: break;
      }
    }
    enterKey.setText(l);
  }

  private void switchKeyboard() {
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) imm.showInputMethodPicker();
  }

  // ---------------------------------------------------------------- shift

  private void onShift() {
    long now = SystemClock.uptimeMillis();
    if (shift == SHIFT_CAPS) setShift(SHIFT_OFF);
    else if (now - lastShiftTap < 350) setShift(SHIFT_CAPS);
    else setShift(shift == SHIFT_ON ? SHIFT_OFF : SHIFT_ON);
    lastShiftTap = now;
  }

  private void setShift(int s) {
    shift = s;
    for (TextView k : letterKeys) k.setText(label((String) k.getTag()));
    refreshShiftKey();
  }

  private void refreshShiftKey() {
    if (shiftKey == null) return;
    boolean active = shift != SHIFT_OFF;
    shiftKey.setText(shift == SHIFT_CAPS ? "\u21EA" : "\u21E7");
    shiftKey.setTextColor(active ? cOnAccent : cText);
    shiftKey.setBackground(stateBg(active ? cAccent : cFn, active ? shade(cAccent, 0.85f) : cPressed, dp(10)));
  }

  private void updateAutoShift() {
    if (layer != LAYER_LETTERS || shift == SHIFT_CAPS || editorInfo == null) return;
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    int caps = ic.getCursorCapsMode(editorInfo.inputType);
    setShift(caps != 0 ? SHIFT_ON : SHIFT_OFF);
  }

  // ---------------------------------------------------------------- preview + repeat

  private void showPreview(View anchor, String s) {
    try {
      if (preview == null) {
        previewTv = new TextView(this);
        previewTv.setGravity(Gravity.CENTER);
        previewTv.setTextSize(28);
        previewTv.setTextColor(cText);
        previewTv.setBackground(rect(cFn, dp(12)));
        preview = new PopupWindow(previewTv, dp(54), dp(64));
        preview.setTouchable(false);
        preview.setClippingEnabled(false);
        preview.setElevation(dp(6));
      }
      previewTv.setText(s);
      int[] loc = new int[2];
      anchor.getLocationOnScreen(loc);
      int x = loc[0] + anchor.getWidth() / 2 - dp(27);
      int y = loc[1] - dp(70);
      if (preview.isShowing()) preview.update(x, y, -1, -1);
      else preview.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    } catch (Exception ignored) {
      // preview is cosmetic; never crash typing because of it
    }
  }

  private void hidePreview() {
    try {
      if (preview != null && preview.isShowing()) preview.dismiss();
    } catch (Exception ignored) { }
  }

  private void startRepeat(final Runnable action) {
    stopRepeat();
    repeater = new Runnable() {
      @Override public void run() {
        action.run();
        handler.postDelayed(this, 45);
      }
    };
    handler.postDelayed(repeater, 380);
  }

  private void stopRepeat() {
    if (repeater != null) handler.removeCallbacks(repeater);
    repeater = null;
  }

  @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
    super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
    if (newSelStart != oldSelStart || newSelEnd != oldSelEnd) updateWordPredictions();
  }

  @Override public void onDestroy() {
    stopRepeat(); hidePreview(); dismissActivePopup(); dismissBirdPopup();
    if (toneGenerator != null) { toneGenerator.release(); toneGenerator = null; }
    super.onDestroy();
  }
}
