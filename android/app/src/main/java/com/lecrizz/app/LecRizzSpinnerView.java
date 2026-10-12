package com.lecrizz.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.graphics.Color;
import android.view.animation.LinearInterpolator;

/** Lightweight LecRizz spinner: three bird-like marks orbit a soft ring. */
public final class LecRizzSpinnerView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final ValueAnimator animator;
  private float angle;
  private int accentA = Color.rgb(82, 211, 215);
  private int accentB = Color.rgb(67, 145, 207);
  private int ringColor = 0x334F7A99;

  public LecRizzSpinnerView(Context context) {
    super(context);
    setContentDescription("LecRizz loading");
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    setThemeColors(LecRizzPrefs.isSunset(context));
    animator = ValueAnimator.ofFloat(0f, 360f);
    animator.setDuration(1800L);
    animator.setRepeatCount(ValueAnimator.INFINITE);
    animator.setInterpolator(new LinearInterpolator());
    animator.addUpdateListener(a -> { angle = (float) a.getAnimatedValue(); invalidate(); });
  }

  public void setThemeColors(boolean sunset) {
    accentA = sunset ? Color.rgb(255, 151, 99) : Color.rgb(82, 211, 215);
    accentB = sunset ? Color.rgb(255, 100, 115) : Color.rgb(67, 145, 207);
    ringColor = sunset ? 0x55FF9763 : 0x5552D3D7;
    invalidate();
  }

  @Override protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (ValueAnimator.areAnimatorsEnabled()) animator.start();
  }

  @Override protected void onDetachedFromWindow() {
    animator.cancel();
    super.onDetachedFromWindow();
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float cx = getWidth() / 2f, cy = getHeight() / 2f;
    float radius = Math.min(getWidth(), getHeight()) * .31f;
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(Math.max(2f, radius * .10f));
    paint.setColor(ringColor);
    canvas.drawCircle(cx, cy, radius, paint);
    paint.setColor(accentA);
    paint.setStrokeCap(Paint.Cap.ROUND);
    canvas.drawArc(new RectF(cx-radius, cy-radius, cx+radius, cy+radius), angle, 88f, false, paint);

    paint.setStyle(Paint.Style.FILL);
    float birdRadius = Math.max(3.5f, radius * .20f);
    for (int i = 0; i < 3; i++) {
      float a = (float) Math.toRadians(angle + i * 120f);
      float x = cx + (float)Math.cos(a) * radius;
      float y = cy + (float)Math.sin(a) * radius;
      paint.setColor(i == 1 ? accentB : accentA);
      canvas.drawCircle(x, y, birdRadius, paint);
      // Tiny paired wings make each orbiting dot read as a simple bird mark.
      paint.setStrokeWidth(Math.max(1.5f, birdRadius * .42f));
      paint.setStrokeCap(Paint.Cap.ROUND);
      canvas.drawLine(x-birdRadius*.75f, y-birdRadius*.2f, x-birdRadius*.1f, y-birdRadius*.65f, paint);
      canvas.drawLine(x-birdRadius*.1f, y-birdRadius*.65f, x+birdRadius*.55f, y-birdRadius*.15f, paint);
    }
  }
}
