package com.dotstaraj.glyphtorchtoy;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.nothing.ketchum.Common;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphException;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphToy;

import java.util.Arrays;

public class GlyphTorchToyService extends Service {

    // Confirmed by decompiling GlyphMatrixUtils.convertToGlyphMatrix(): the
    // raw setMatrixFrame(int[]) scale tops out at 4095, not 255 -- see the
    // README for the full explanation.
    private static final int MAX_RAW_BRIGHTNESS = 4095;

    // Extra-long-press haptic: no initial delay, a 200ms "long" pulse, a
    // 100ms gap, a 5ms "short" pulse, another 100ms gap, another 5ms
    // "short" pulse -- distinguishable from whatever (if anything) Nothing
    // OS itself does for the regular long press.
    private static final long[] EXTRA_LONG_PRESS_HAPTIC = {0, 200, 100, 5, 100, 5};

    private GlyphMatrixManager mGM;
    private GlyphMatrixManager.Callback mCallback;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean changeReceived = false;
    private boolean pressResolved = false;
    private final Runnable extraLongPressRunnable = this::onExtraLongPressTimerFired;

    // --- Service lifecycle -------------------------------------------------

    @Override
    public IBinder onBind(Intent intent) {
        init();
        return serviceMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mainHandler.removeCallbacks(extraLongPressRunnable);
        changeReceived = false;
        pressResolved = false;

        fireAction(GlyphActionsConfig.KEY_DEACTIVATED);

        if (mGM != null) {
            mGM.turnOff();
            mGM.unInit();
        }
        mGM = null;
        mCallback = null;
        return false;
    }

    private void init() {
        mGM = GlyphMatrixManager.getInstance(getApplicationContext());
        mCallback = new GlyphMatrixManager.Callback() {
            @Override
            public void onServiceConnected(ComponentName componentName) {
                mGM.register(Glyph.DEVICE_23112);
                drawFullBrightness();
                fireAction(GlyphActionsConfig.KEY_ACTIVATED);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };
        mGM.init(mCallback);
    }

    // --- Glyph Button event handling ---------------------------------------
    //
    // Regular long press: EVENT_CHANGE. Extra long press: a configurable
    // timer (default 1000ms, see GlyphActionsConfig) started on
    // EVENT_ACTION_DOWN. On EVENT_CHANGE we don't fire anything yet -- just
    // remember it happened. If EVENT_ACTION_UP arrives before the timer
    // fires, that resolves the press: fire the long-press action if
    // EVENT_CHANGE was seen, otherwise it was too short a tap to mean
    // anything. If the timer fires first, that's the extra long press:
    // fire that action instead, play its haptic, and the eventual
    // EVENT_ACTION_UP is a no-op. The two are mutually exclusive by
    // design -- reaching extra-long-press suppresses the regular
    // long-press action rather than firing both.

    private final Handler serviceHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                String event = msg.getData().getString(GlyphToy.MSG_GLYPH_TOY_DATA);
                if (GlyphToy.EVENT_ACTION_DOWN.equals(event)) {
                    onActionDown();
                } else if (GlyphToy.EVENT_CHANGE.equals(event)) {
                    changeReceived = true;
                } else if (GlyphToy.EVENT_ACTION_UP.equals(event)) {
                    onActionUp();
                }
            } else {
                super.handleMessage(msg);
            }
        }
    };

    private final Messenger serviceMessenger = new Messenger(serviceHandler);

    private void onActionDown() {
        mainHandler.removeCallbacks(extraLongPressRunnable);
        changeReceived = false;
        pressResolved = false;
        int timerMs = GlyphActionsConfig.getExtraLongPressTimerMs(this);
        mainHandler.postDelayed(extraLongPressRunnable, timerMs);
    }

    private void onActionUp() {
        mainHandler.removeCallbacks(extraLongPressRunnable);
        if (pressResolved) {
            // Extra long press already fired for this press cycle -- ignore.
            return;
        }
        pressResolved = true;
        if (changeReceived) {
            fireAction(GlyphActionsConfig.KEY_LONGPRESS);
        }
        changeReceived = false;
    }

    private void onExtraLongPressTimerFired() {
        if (pressResolved) {
            return;
        }
        pressResolved = true;
        changeReceived = false;
        vibrateExtraLongPress();
        fireAction(GlyphActionsConfig.KEY_EXTRA_LONGPRESS);
    }

    // --- Configured action firing -------------------------------------------

    private void fireAction(String prefKey) {
        ActionTarget target = GlyphActionsConfig.get(this, prefKey);
        if (target == null) return;
        try {
            Intent intent;
            if (ActionTarget.TYPE_APP.equals(target.type)) {
                intent = getPackageManager().getLaunchIntentForPackage(target.packageName);
            } else {
                intent = Intent.parseUri(target.intentUri, Intent.URI_INTENT_SCHEME);
            }
            if (intent == null) return;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            // No UI to report failures to from here. A launch that's
            // blocked by background-activity-start restrictions, or a
            // shortcut pointing at a non-exported activity, just silently
            // doesn't happen -- see the README for both known risks.
            e.printStackTrace();
        }
    }

    private void vibrateExtraLongPress() {
        try {
            VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
            if (vm == null) return;
            Vibrator vibrator = vm.getDefaultVibrator();
            vibrator.vibrate(VibrationEffect.createWaveform(EXTRA_LONG_PRESS_HAPTIC, -1));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Matrix rendering ----------------------------------------------------

    private void drawFullBrightness() {
        if (mGM == null) return;
        try {
            int length = Common.getDeviceMatrixLength(); // 25 on Phone (3)
            int[] frame = new int[length * length];
            Arrays.fill(frame, MAX_RAW_BRIGHTNESS);
            mGM.setMatrixFrame(frame);
        } catch (GlyphException e) {
            e.printStackTrace();
        }
    }
}
