package com.dotstaraj.glyphtorchtoy;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;

import com.nothing.ketchum.Common;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphException;
import com.nothing.ketchum.GlyphMatrixManager;

import java.util.Arrays;

public class GlyphTorchToyService extends Service {

    // The true per-pixel ceiling for the raw setMatrixFrame(int[]) path is
    // 4095, not 255. Confirmed by decompiling GlyphMatrixUtils.
    // convertToGlyphMatrix() (the bytecode every "normal" object-rendered
    // toy goes through): it averages a bitmap pixel's RGB to a 0-255
    // grayscale value, rescales that to 0-4095 (`gray * 4095 / 255`), then
    // multiplies by the object's brightness/255 fraction and clamps to
    // [0, 4095]. So GlyphMatrixObject's documented "0-255, default 255"
    // brightness is an input to that formula, not the final matrix value —
    // the array actually sent to hardware tops out at 4095. Filling the raw
    // array with 255 (as an earlier version of this file did) was only
    // ~6% of true max, which is why it looked dimmer than toys built the
    // normal way even with every LED "on".
    private static final int MAX_RAW_BRIGHTNESS = 4095;

    private GlyphMatrixManager mGM;
    private GlyphMatrixManager.Callback mCallback;

    // --- Service lifecycle -------------------------------------------------
    // Per Nothing's own documented pattern: "When your toy is selected and
    // shown on the Glyph Matrix, its functions start." So the torch turns on
    // as soon as the toy is bound (selected via the Glyph Button carousel),
    // and off as soon as it's unbound (you cycle away). No button handling
    // at all: onBind() just returns null, the documented minimal form for a
    // toy that doesn't react to long-press or touch events.

    @Override
    public IBinder onBind(Intent intent) {
        init();
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
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
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };
        mGM.init(mCallback);
    }

    // --- Matrix rendering ----------------------------------------------------

    /** Every addressable position in the matrix at true max brightness. */
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

    // Note on the system "Glyph Toys timeout" (Settings > Glyph Interface >
    // Glyph Toys, up to 10 minutes): an earlier version of this file tried
    // two things to get around it — calling the undocumented
    // setGlyphMatrixTimeout() method, and periodically re-sending the frame
    // as a keep-alive. Neither is here anymore. The first had no observed
    // effect. The second was disproven directly: the built-in clock toy
    // redraws its blinking colon every second and still gets dimmed by the
    // same system timeout, so redraw frequency clearly isn't what the
    // timer tracks. There's no confirmed way to disable this timeout from
    // inside a toy — the system-wide 10-minute max is the real ceiling,
    // and it applies to every toy, not just this one.
}
