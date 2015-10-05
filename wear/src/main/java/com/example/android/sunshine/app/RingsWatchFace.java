/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class RingsWatchFace extends CanvasWatchFaceService implements DataApi.DataListener,
     GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener  {

    public static final String TAG = RingsWatchFace.class.getSimpleName();
    private static final String SUNSHINE_HIGH_TEMP = "weather-high";
    private static final String SUNSHINE_LOW_TEMP = "weather-low";
    private static final String SUNSHINE_ICON = "weather-icon";
    private static final String SUNSHINE_DATA_PATH = "/sunshine-weather-data";

    GoogleApiClient mGoogleApiClient;

    private String mHighTemp = "";
    private String mLowTemp = "";
    private Bitmap mWeatherIcon;


    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        mGoogleApiClient = new GoogleApiClient.Builder(RingsWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();

        Log.d(TAG, "mGoogleApiClient.connect() called");

        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        Paint mBackgroundPaint;
        Paint mInactivePaint;
        Paint mActivePaint;
        Paint mAmbientPaint;

        boolean mAmbient;
        Calendar mCal;

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
//                mTime.clear(intent.getStringExtra("time-zone"));
//                mTime.setToNow();
                mCal.setTimeZone(TimeZone.getTimeZone(intent.getStringExtra("time-zone")));

            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(RingsWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = RingsWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.analog_background));

            mInactivePaint = new Paint();
            mInactivePaint.setColor(resources.getColor(R.color.inactive_text));
//            mInactivePaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mInactivePaint.setAntiAlias(true);
            mInactivePaint.setStrokeCap(Paint.Cap.ROUND);
            mInactivePaint.setTextSize(resources.getDimension(R.dimen.inactive_text_size));

            mActivePaint = new Paint();
            mActivePaint.setColor(resources.getColor(R.color.active_text));
//            mActivePaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mActivePaint.setFakeBoldText(true);
            mActivePaint.setAntiAlias(true);
            mActivePaint.setStrokeCap(Paint.Cap.ROUND);
            mActivePaint.setTextSize(resources.getDimension(R.dimen.active_text_size));

            mAmbientPaint = new Paint();
            mAmbientPaint.setColor(resources.getColor(R.color.active_text));
//            mAmbientPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mAmbientPaint.setFakeBoldText(false);
            mAmbientPaint.setAntiAlias(true);
            mAmbientPaint.setStrokeCap(Paint.Cap.ROUND);
            mAmbientPaint.setTextSize(resources.getDimension(R.dimen.active_text_size));

//            mTime = new Time();
            mCal = new GregorianCalendar();

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mInactivePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private final String[] MONTH_NAMES = {"JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"};
        private final String[] DAY_NAMES = {"SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};

        // Shorter names for Ambient mode
        private final String[] MONTH_NAMES_AMBIENT = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        private final String[] DAY_NAMES_AMBIENT = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};

        private final String[] MINUTES_OR_SECONDS = new String[60];
        private final String[] HOURS = new String[12];

        {
           for (int i = 0; i < MINUTES_OR_SECONDS.length; i++)  {
               MINUTES_OR_SECONDS[i] = "" + i;
           }

            HOURS[0] = "12";
            for (int i = 1; i < HOURS.length; i++)  {
                HOURS[i] = "" + i;
            }
        }


        private final int VERTICAL_SPACING = 30;
        private final int HORIZONTAL_SPACING = 5;
        private final int VERTICAL_BASE = 225;

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mCal.setTime(new Date());

            int width = bounds.width();
            int height = bounds.height();

            // Draw the background.
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = width / 2f;
            float centerY = height / 2f;

            // Draw Center line
//            canvas.drawLine(centerX, 0, centerX, height, mInactivePaint);


            // Draw Day of Month band
            int y = VERTICAL_BASE;
            int numDaysInMonth = mCal.getActualMaximum(Calendar.DAY_OF_MONTH);
            String[] dayInMonthBand = new String[numDaysInMonth];
            for (int i = 0; i < numDaysInMonth; i++)  {
                dayInMonthBand[i] = "" + (i + 1);
            }
            drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas,
                    "" + mCal.get(Calendar.DAY_OF_MONTH), dayInMonthBand);

            // Draw Month band
            y -= VERTICAL_SPACING;
            String[] names = (mAmbient) ? MONTH_NAMES_AMBIENT : MONTH_NAMES;
            drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas,
                    names[mCal.get(Calendar.MONTH)], names);

            // Draw Day of Week band
              y -= VERTICAL_SPACING;
            names = (mAmbient) ? DAY_NAMES_AMBIENT : DAY_NAMES;
            drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas,
                    names[mCal.get(Calendar.DAY_OF_WEEK) - 1], names);

            // Draw Second band
            y -= VERTICAL_SPACING;

            if (!mAmbient) {
                drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas,
                        MINUTES_OR_SECONDS[mCal.get(Calendar.SECOND)], MINUTES_OR_SECONDS);
            }

            // Draw Minute band
            y -= VERTICAL_SPACING;
            drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas,
                    MINUTES_OR_SECONDS[mCal.get(Calendar.MINUTE)], MINUTES_OR_SECONDS);

            // Draw Hour Band
            y -= VERTICAL_SPACING;
            drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas,
                    HOURS[mCal.get(Calendar.HOUR)], HOURS);

            // Do not draw the weather data if in ambient mode.
            if (!mAmbient)  {

                Log.d(TAG, "mLowTemp=" + mLowTemp);
                Log.d(TAG, "mHighTemp=" + mHighTemp);
                Log.d(TAG, "mWeatherIcon is " + ((mWeatherIcon == null) ? "" : "not ") + " null");


                canvas.drawText(mLowTemp, 50, height - 50, mActivePaint);

                canvas.drawText(mHighTemp,
                        (width - 50 - mActivePaint.measureText(mHighTemp)),
                        height - 50, mActivePaint);

                if (mWeatherIcon != null) {
                    Rect rect = new Rect(
                            (int)(centerX - 20),
                            (int)(height - 80),
                            (int)(centerX + 20),
                            (int)(height - 40));

                    canvas.drawBitmap(mWeatherIcon, null, rect, mActivePaint);
                }

            }

        }

        private void drawBand(int y, float x, int width, int spacing, Canvas canvas,
                              String word, String[] words)  {

            int baseWordIndex = -1;

            for (int i = 0; i < words.length; i++)  {
              if (words[i].equals(word))  {
                  baseWordIndex = i;
                  break;
              }
            }

            if (baseWordIndex < 0) {
                throw new IllegalArgumentException("Cannot find word:" + word + " in word list");
            }


            float baseWordWidth = (mAmbient) ? mAmbientPaint.measureText(words[baseWordIndex]) :
                    mActivePaint.measureText(words[baseWordIndex]);

            float textStart = x - (baseWordWidth / 2f);

            canvas.drawText(word, textStart, y, (mAmbient) ? mAmbientPaint : mActivePaint);

            /*
            * If in ambient mode, we'll only draw the main word.
            * Below here are the strings that appear to the left and the right.
            */
            if (mAmbient) return;

            float textEnd = textStart - spacing;

            // Draw to the left.
            int currentWordIndex = baseWordIndex - 1;
            if (currentWordIndex < 0) currentWordIndex = words.length - 1;

            while (textEnd > 0)  {
                float textWidth = mInactivePaint.measureText(words[currentWordIndex]);
                textStart = textEnd - textWidth;
                canvas.drawText(words[currentWordIndex], textStart, y, mInactivePaint);
                textEnd = textStart - spacing;
                currentWordIndex -= 1;
                if (currentWordIndex < 0) currentWordIndex = words.length - 1;
            }

            // Draw to the right
            currentWordIndex = baseWordIndex + 1;
            if (currentWordIndex >= words.length) currentWordIndex = 0;

            textStart = x + (baseWordWidth / 2f) + spacing;

            while (textStart < width)  {
                float textWidth = mInactivePaint.measureText(words[currentWordIndex]);
                canvas.drawText(words[currentWordIndex], textStart, y, mInactivePaint);
                textStart += textWidth + spacing;
                currentWordIndex += 1;
                if (currentWordIndex >= words.length) currentWordIndex = 0;
            }



        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
//                mTime.clear(TimeZone.getDefault().getID());
//                mTime.setToNow();
                mCal.setTimeZone(TimeZone.getDefault());
                mCal.setTime(new Date());

            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            RingsWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            RingsWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<RingsWatchFace.Engine> mWeakReference;

        public EngineHandler(RingsWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            RingsWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "onConnected() called");
        Wearable.DataApi.addListener(mGoogleApiClient, this);

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "onConnectionSuspended() called, i=" + i);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

        Log.i(TAG, "onDataChanged() called");

        for (DataEvent dataEvent : dataEventBuffer) {
            if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                continue;
            }

            DataItem dataItem = dataEvent.getDataItem();
            if (!dataItem.getUri().getPath().equals(SUNSHINE_DATA_PATH)) {
                continue;
            }

            DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
            DataMap dataMap = dataMapItem.getDataMap();
            mHighTemp = dataMap.getString(SUNSHINE_HIGH_TEMP);
            mLowTemp = dataMap.getString(SUNSHINE_LOW_TEMP);
            Asset iconAsset = dataMap.getAsset(SUNSHINE_ICON);

            if (iconAsset != null) {

                ConnectionResult connectionResult =
                        mGoogleApiClient.blockingConnect(500, TimeUnit.MILLISECONDS);

                if (connectionResult.isSuccess()) {

                    InputStream assetInputStream =
                            Wearable.DataApi.getFdForAsset(mGoogleApiClient, iconAsset)
                                    .await().getInputStream();
                    
                    if (assetInputStream != null) {
                        mWeatherIcon = BitmapFactory.decodeStream(assetInputStream);
                    }
                }
            }
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "onConnectionFailed() called");

    }
}
