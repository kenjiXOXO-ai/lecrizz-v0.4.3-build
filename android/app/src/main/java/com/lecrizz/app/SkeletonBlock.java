package com.lecrizz.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Reusable skeleton placeholder for genuinely asynchronous dashboard/settings content. */
public final class SkeletonBlock extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final ValueAnimator animator;
  private float shimmer = -1f;
  private final float radius;
  private final int baseColor, shineColor;

  public SkeletonBlock(Context context, float cornerRadiusPx) {
    super(context);
    radius = cornerRadiusPx;
    boolean sunset = LecRizzPrefs.isSunset(context);
    baseColor = sunset ? 0xFF38232D : 0xFF12313D;
    shineColor = sunset ? 0xFF59313B : 0xFF24515D;
    setContentDescription("Content loading");
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    animator = ValueAnimator.ofFloat(-1f, 2f);
    animator.setDuration(1400L);
    animator.setRepeatCount(ValueAnimator.INFINITE);
    animator.setInterpolator(new LinearInterpolator());
    animator.addUpdateListener(a -> { shimmer = (float)a.getAnimatedValue(); invalidate(); });
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
    float w = getWidth(), h = getHeight();
    if (w <= 0 || h <= 0) return;
    float shift = shimmer * w;
    paint.setShader(new LinearGradient(shift-w*.45f, 0, shift+w*.45f, h,
        new int[]{baseColor, shineColor, baseColor}, null, Shader.TileMode.CLAMP));
    canvas.drawRoundRect(new RectF(0, 0, w, h), radius, radius, paint);
    paint.setShader(null);
  }
}
