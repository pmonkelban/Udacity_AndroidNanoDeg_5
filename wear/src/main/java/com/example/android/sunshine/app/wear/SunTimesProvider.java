package com.example.android.sunshine.app.wear;


import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import java.util.Calendar;
import java.util.TimeZone;


public class SunTimesProvider
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = SunTimesProvider.class.getSimpleName();

    private static final String DEFAULT_LAT = "39.55416666666667";
    private static final String DEFAULT_LON = "-77.995";

    private String lat = DEFAULT_LAT;
    private String lon = DEFAULT_LON;

    protected Location mLastLocation;

    private Long calculatorCreateTime = 0L;
    private static Long maxCalculatorAge = (long) 1000 * 60 * 60; // One hour

    private Calendar sunrise;
    private Calendar sunset;


    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;

    public SunTimesProvider(Context context) {

        /*
        * Create a connection to Google Play Services.
        * We'll be using the location services API to get the user's
        * last know location.  This in turn will be used to provide location
        * specific sunrise and sunset times.
        */
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        Log.d(TAG, "Calling mGoogleApiClient.connect()");
        mGoogleApiClient.connect();

    }

    public Calendar getSunrise() {
        updateValues();
        return sunrise;
    }

    public Calendar getSunset() {
        updateValues();
        return sunset;
    }

    private void updateValues() {

        long currTime = System.currentTimeMillis();
        if ((currTime - calculatorCreateTime) < maxCalculatorAge) return;

        Calendar cal = Calendar.getInstance(TimeZone.getDefault());

        SunriseSunsetCalculator calculator =
                new SunriseSunsetCalculator(
                        new com.luckycatlabs.sunrisesunset.dto.Location(lat, lon),
                        cal.getTimeZone());

        calculatorCreateTime = currTime;

        sunrise = calculator.getOfficialSunriseCalendarForDate(cal);
        sunset = calculator.getOfficialSunsetCalendarForDate(cal);

    }

    @Override
    public void onConnected(Bundle bundle) {

        Log.d(TAG, "In onConnected()");

//        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
//        if (mLastLocation != null)  {
//            lat = String.valueOf(mLastLocation.getLatitude());
//            lon = String.valueOf(mLastLocation.getLongitude());
//            Log.d(TAG, "Location updated. Lat: " + lat + " Lon: " + lon);
//        } else  {
//            Log.d(TAG, "Location data is null");
//        }

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setInterval(10_000l)
                .setFastestInterval(1_000l);

        LocationServices.FusedLocationApi
                .requestLocationUpdates(mGoogleApiClient, locationRequest, this)
                .setResultCallback(new ResultCallback() {

                    @Override
                    public void onResult(Result result) {

                        if (result.getStatus().isSuccess()) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Successfully requested location updates");
                            }
                        } else {
                            Log.e(TAG,
                                    "Failed in requesting location updates, "
                                            + "status code: "
                                            + result.getStatus().getStatusCode()
                                            + ", message: "
                                            + result.getStatus().getStatusMessage());
                        }
                    }
                });

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "Connection failed: ConnectionResult.getErrorCode() = " +
                connectionResult.getErrorCode());

    }

    @Override
    public void onLocationChanged(Location location) {

        Log.d(TAG, "In onLocationChanged()");
        lat = String.valueOf(location.getLatitude());
        lon = String.valueOf(location.getLongitude());
        Log.d(TAG, "Location updated. Lat: " + lat + " Lon: " + lon);

    }
}
