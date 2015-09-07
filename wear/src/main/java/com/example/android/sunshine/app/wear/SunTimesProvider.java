package com.example.android.sunshine.app.wear;


import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.luckycatlabs.sunrisesunset.dto.Location;

import java.util.Calendar;
import java.util.TimeZone;


public class SunTimesProvider {

    private static Location location = new Location("39.55416666666667", "-77.995");

//    private static Location location = new Location("83.5", "-77.995");
    private static Long calculatorCreateTime = 0L;
    private static Long maxCalculatorAge = (long) 1000 * 60 * 60; // One hour


    private static Calendar sunrise;
    private static Calendar sunset;

    public static Calendar getSunrise()  {
        updateValues();
        return sunrise;

    }

    public static Calendar getSunset()  {
        updateValues();
        return sunset;
    }

    private static void updateValues() {

        long currTime = System.currentTimeMillis();
        if ((currTime - calculatorCreateTime) < maxCalculatorAge) return;

        Calendar cal = Calendar.getInstance(TimeZone.getDefault());

        SunriseSunsetCalculator calculator =
                new SunriseSunsetCalculator(location, cal.getTimeZone());

        calculatorCreateTime = currTime;

        sunrise = calculator.getOfficialSunriseCalendarForDate(cal);
        sunset = calculator.getOfficialSunsetCalendarForDate(cal);

        // Adjust times
//        sunrise.add(Calendar.HOUR, -3);
//        sunset.add(Calendar.HOUR, +6);

    }

}
