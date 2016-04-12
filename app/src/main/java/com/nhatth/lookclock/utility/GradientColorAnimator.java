package com.nhatth.lookclock.utility;

import android.animation.Animator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/**
 * Created by nhatth on 4/12/16.
 * A total impostor of ValueAnimator class (doesn't extend from ValueAnimator), with vaguely
 * similar methods (just a subset).
 * Handles animating colors in a GradientDrawable, shifting colors from one hue to another.
 */
public class GradientColorAnimator {
    ValueAnimator mGradientAnimator;
    GradientDrawable mDrawable;

    int mDuration, mStartColor, mEndColor, mHueShift;
    // For reset to the default state
    int mOriginalStart, mOriginalEnd;

    boolean mCanceled = false;

    public GradientColorAnimator(GradientDrawable gradientDrawable, int startColor, int endColor) {

        mDrawable = gradientDrawable;
        mStartColor = mOriginalStart = startColor;
        mEndColor = mOriginalEnd = endColor;
    }

    /**
     * Animate two-color gradient once, with specified hue shift
     */
    public void animateBoth(int hueShift, int duration) {
        mHueShift = hueShift;

        int newStartColor = hueChange(mStartColor, hueShift);
        int newEndColor = hueChange(mEndColor, hueShift);

        PropertyValuesHolder start = PropertyValuesHolder.ofInt("startColor", mStartColor, newStartColor);
        start.setEvaluator(ArgbEvaluator.getInstance());
        PropertyValuesHolder end = PropertyValuesHolder.ofInt("endColor", mEndColor, newEndColor);
        end.setEvaluator(ArgbEvaluator.getInstance());

        mGradientAnimator = ValueAnimator.ofPropertyValuesHolder(start, end);
        mGradientAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int currentStart = (int) animation.getAnimatedValue("startColor");
                int currentEnd = (int) animation.getAnimatedValue("endColor");
                mDrawable.setColors(new int[]{currentStart, currentEnd});
            }
        });

        setDuration(duration);
    }

    /**
     * Like {@link #animateBoth(int, int)}, but animate indefinitely
     */
    public void animateBothIndefinite(int hueShift, int duration) {
        mCanceled = false;
        animateBoth(hueShift, duration);
        mGradientAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mCanceled) {
                    int newStartColor = hueChange(mStartColor, mHueShift);
                    int newEndColor = hueChange(mEndColor, mHueShift);
                    mStartColor = newStartColor;
                    mEndColor = newEndColor;
                    animateBothIndefinite(mHueShift, mDuration);
                    start();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mCanceled = true;
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }

    public void setDuration(int duration) {
        mDuration = duration;
        mGradientAnimator.setDuration(duration);
    }

    /**
     * Start animation
     */
    public void start() {
        if (mGradientAnimator != null)
            mGradientAnimator.start();
        else
            throw new NullPointerException("Must call animateBoth() or animateBothIndefinite first!");
    }

    /**
     * Stop current animation and reset the GradientDrawable to original state
     */
    public void stopAndReset() {
        mGradientAnimator.cancel();
        mStartColor = mOriginalStart;
        mEndColor = mOriginalEnd;
        mGradientAnimator = null;
    }


    /**
     * By zed
     * <p>From http://stackoverflow.com/questions/18216285/android-animate-color-change-from-color-to-color</p>
     * <p/>
     * Shift the color's hue by an amount
     * @param c Original color
     * @param deg Amount to shift
     * @return New color
     */
    private static int hueChange(int c,int deg){
        float[] hsv = new float[3];       //array to store HSV values
        Color.colorToHSV(c,hsv); //get original HSV values of pixel
        hsv[0]=hsv[0]+deg;                //add the shift to the HUE of HSV array
        hsv[0]=hsv[0]%360;                //confines hue to values:[0,360]
        return Color.HSVToColor(Color.alpha(c),hsv);
    }
}
