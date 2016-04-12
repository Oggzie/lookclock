package com.nhatth.lookclock.utility;

import android.view.animation.Interpolator;

/**
 * Created by nhatth on 4/6/16.
 * An Interpolator which interpolates values halfway, then reverse it.
 */
public class ReverseHalfwayInterpolator implements Interpolator {

    @Override
    public float getInterpolation(float input) {
        if (input <= 0.5)
            return input * 2;
        else
            return Math.abs((input - 1) * 2);
    }
}
