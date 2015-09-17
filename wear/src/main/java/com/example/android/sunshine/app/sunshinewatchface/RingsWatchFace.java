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

package com.example.android.sunshine.app.sunshinewatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import com.example.android.sunshine.app.sunshinewatchface.R;import java.lang.IllegalArgumentException;import java.lang.Override;import java.lang.String;import java.lang.System;import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class RingsWatchFace extends CanvasWatchFaceService {
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
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        Paint mBackgroundPaint;
        Paint mInactivePaint;
        Paint mActivePaint;
        boolean mAmbient;
        Time mTime;

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
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
            mInactivePaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mInactivePaint.setAntiAlias(true);
            mInactivePaint.setStrokeCap(Paint.Cap.ROUND);
            mInactivePaint.setTextSize(resources.getDimension(R.dimen.inactive_text_size));

            mActivePaint = new Paint();
            mActivePaint.setColor(resources.getColor(R.color.active_text));
            mActivePaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mActivePaint.setFakeBoldText(true);
            mActivePaint.setAntiAlias(true);
            mActivePaint.setStrokeCap(Paint.Cap.ROUND);
            mActivePaint.setTextSize(resources.getDimension(R.dimen.active_text_size));


            mTime = new Time();
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
            Calendar calendar = new GregorianCalendar();

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
            int numDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
            String[] dayInMonthBand = new String[numDaysInMonth];
            for (int i = 0; i < numDaysInMonth; i++)  {
                dayInMonthBand[i] = "" + (i + 1);
            }
            drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas, mAmbient,
                    "" + calendar.get(Calendar.DAY_OF_MONTH), dayInMonthBand);

            // Draw Month band
            y -= VERTICAL_SPACING;
            String[] names = (mAmbient) ? MONTH_NAMES_AMBIENT : MONTH_NAMES;
            drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas, mAmbient,
                    names[calendar.get(Calendar.MONTH)], names);

            // Draw Day of Week band
            y -= VERTICAL_SPACING;
            names = (mAmbient) ? DAY_NAMES_AMBIENT : DAY_NAMES;
            drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas, mAmbient,
                    names[calendar.get(Calendar.DAY_OF_WEEK)], names);

            // Draw Second band
            y -= VERTICAL_SPACING;

            if (!mAmbient) {
                drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas, mAmbient,
                        MINUTES_OR_SECONDS[calendar.get(Calendar.SECOND)], MINUTES_OR_SECONDS);
            }

            // Draw Minute band
            y -= VERTICAL_SPACING;
            drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas, mAmbient,
                    MINUTES_OR_SECONDS[calendar.get(Calendar.MINUTE)], MINUTES_OR_SECONDS);

            // Draw Hour Band
            y -= VERTICAL_SPACING;
            drawBand(y, centerX, width, HORIZONTAL_SPACING, canvas, mAmbient,
                    HOURS[calendar.get(Calendar.HOUR)], MINUTES_OR_SECONDS);



//            float secRot = mTime.second / 30f * (float) Math.PI;
//            int minutes = mTime.minute;
//            float minRot = minutes / 30f * (float) Math.PI;
//            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;
//
//            float secLength = centerX - 20;
//            float minLength = centerX - 40;
//            float hrLength = centerX - 80;
//
//            if (!mAmbient) {
//                float secX = (float) Math.sin(secRot) * secLength;
//                float secY = (float) -Math.cos(secRot) * secLength;
//                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mInactivePaint);
//            }
//
//            float minX = (float) Math.sin(minRot) * minLength;
//            float minY = (float) -Math.cos(minRot) * minLength;
//            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mInactivePaint);
//
//            float hrX = (float) Math.sin(hrRot) * hrLength;
//            float hrY = (float) -Math.cos(hrRot) * hrLength;
//            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mInactivePaint);
        }

        private void drawBand(int y, float x, int width, int spacing, Canvas canvas,
                              boolean isAmbient, String word, String[] words)  {

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

            float baseWordWidth = mActivePaint.measureText(words[baseWordIndex]);

            float textStart = x - (baseWordWidth / 2f);

            canvas.drawText(word, textStart, y, mActivePaint);

            if (isAmbient) return;

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
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
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
}
