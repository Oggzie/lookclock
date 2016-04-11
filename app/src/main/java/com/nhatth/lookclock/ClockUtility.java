package com.nhatth.lookclock;

import android.content.Context;
import android.text.format.DateFormat;

import java.util.Calendar;

/**
 * Created by nhatth on 4/11/16.
 * A utility class for all thing clock related.
 */
public class ClockUtility {

    enum ClockState {NOT_LOOKING_AT, LOOKING_AT, CHANGING}

    /**
     * Add a leading zero, if the number is less than 10
     * @param number Number to format
     * @return String formatted number
     */
    public static String addLeadingZero (int number) {
        String formatted;
        if (number >= 10)
            formatted = String.valueOf(number);
        else
            formatted = "0" + number;

        return formatted;
    }

    /**
     * Return current time in the HH:MM style with matching device's time format (12 or 24)
     * @param context
     * @return Formatted time String
     */
    public static String getCurrentFormattedTime (Context context) {
        Calendar currentTime = Calendar.getInstance();
        int hour = currentTime.get(Calendar.HOUR);
        int min = currentTime.get(Calendar.MINUTE);
        if (DateFormat.is24HourFormat(context)) {
            if (currentTime.get(Calendar.AM_PM) == Calendar.PM) {
                hour += 12;
            }
        }
        else {
            if (hour == 0 && currentTime.get(Calendar.AM_PM) == Calendar.PM)
                hour += 12;
        }
        return addLeadingZero(hour) + ":" + addLeadingZero(min);
    }


}
