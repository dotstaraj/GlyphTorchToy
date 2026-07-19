package com.dotstaraj.glyphtorchtoy;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import com.nothing.ketchum.Common;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphException;
import com.nothing.ketchum.GlyphMatrixFrame;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphMatrixObject;
import com.nothing.ketchum.GlyphMatrixUtils;
import com.nothing.ketchum.GlyphToy;

import java.util.Arrays;

public class GlyphTorchToyService extends Service {

    private GlyphMatrixManager mGM;
    private GlyphMatrixManager.Callback mCallback;
    private boolean torchOn = false;

    // --- Service lifecycle -------------------------------------------------
    // The system binds this service when the user cycles the Glyph Button to
    // this toy, and unbinds it when they cycle away. This is the same pattern
    // as Nothing's own README example.

    @Override
    public IBinder onBind(Intent intent) {
        init();
        return serviceMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (torchOn) {
            torchOn = false;
            restoreDefaultTimeout();
        }
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
                drawIdle();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };
        mGM.init(mCallback);
    }

    // --- Glyph Button event handling ---------------------------------------
    // Long-press sends EVENT_CHANGE. We use that as a simple on/off toggle
    // rather than touch-down/touch-up, so you don't have to physically hold
    // the button down to keep the torch lit.

    private final Handler serviceHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                Bundle bundle = msg.getData();
                String event = bundle.getString(GlyphToy.MSG_GLYPH_TOY_DATA);
                if (GlyphToy.EVENT_CHANGE.equals(event)) {
                    toggleTorch();
                }
            } else {
                super.handleMessage(msg);
            }
        }
    };

    private final Messenger serviceMessenger = new Messenger(serviceHandler);

    private void toggleTorch() {
        torchOn = !torchOn;
        if (torchOn) {
            drawFullBrightness();
            preventTimeout();
        } else {
            drawIdle();
            restoreDefaultTimeout();
        }
    }

    // --- Matrix rendering ----------------------------------------------------

    /** Torch state: every addressable position in the matrix at max brightness. */
    private void drawFullBrightness() {
        if (mGM == null) return;
        try {
            int length = Common.getDeviceMatrixLength(); // 25 on Phone (3)
            int[] frame = new int[length * length];
            Arrays.fill(frame, 255);
            mGM.setMatrixFrame(frame);
        } catch (GlyphException e) {
            e.printStackTrace();
        }
    }

    /** Idle state: dim outline icon so you can tell the toy is selected but off. */
    private void drawIdle() {
        if (mGM == null) return;
        try {
            GlyphMatrixObject.Builder iconBuilder = new GlyphMatrixObject.Builder();
            GlyphMatrixObject icon = iconBuilder
                    .setImageSource(GlyphMatrixUtils.drawableToBitmap(
                            getDrawable(R.drawable.glyph_toy_preview)))
                    .setScale(100)
                    .setBrightness(60)
                    .setPosition(0, 0)
                    .setReverse(false)
                    .build();

            GlyphMatrixFrame.Builder frameBuilder = new GlyphMatrixFrame.Builder();
            GlyphMatrixFrame frame = frameBuilder.addTop(icon).build(this);
            mGM.setMatrixFrame(frame.render());
        } catch (GlyphException e) {
            e.printStackTrace();
        }
    }

    // --- Timeout handling ------------------------------------------------
    // setGlyphMatrixTimeout() is not documented in the GDK README, but exists
    // in the SDK (confirmed by decompiling the aar). Its name strongly implies
    // it controls the same "Glyph Toys timeout" setting shown in
    // Settings > Glyph Interface > Glyph Toys. Without disabling it, the torch
    // may auto-dim/turn off after the system's configured toy timeout even
    // while toggled "on". Untested — verify on your device and drop this call
    // if it doesn't behave as expected.

    private void preventTimeout() {
        if (mGM == null) return;
        try {
            mGM.setGlyphMatrixTimeout(false);
        } catch (GlyphException e) {
            e.printStackTrace();
        }
    }

    private void restoreDefaultTimeout() {
        if (mGM == null) return;
        try {
            mGM.setGlyphMatrixTimeout(true);
        } catch (GlyphException e) {
            e.printStackTrace();
        }
    }
}
